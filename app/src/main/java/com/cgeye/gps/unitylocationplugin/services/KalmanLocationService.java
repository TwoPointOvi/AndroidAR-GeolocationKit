package com.cgeye.gps.unitylocationplugin.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.cgeye.gps.unitylocationplugin.commons.Coordinates;
import com.cgeye.gps.unitylocationplugin.commons.GeoPoint;
import com.cgeye.gps.unitylocationplugin.commons.RawSensorDataItem;
import com.cgeye.gps.unitylocationplugin.commons.SensorGpsDataItem;
import com.cgeye.gps.unitylocationplugin.commons.Utils;
import com.cgeye.gps.unitylocationplugin.filters.GPSAccKalmanFilter;
import com.cgeye.gps.unitylocationplugin.filters.LowPassFilter;
import com.cgeye.gps.unitylocationplugin.interfaces.ILogger;
import com.cgeye.gps.unitylocationplugin.interfaces.LocationServiceInterface;
import com.cgeye.gps.unitylocationplugin.interfaces.LocationServiceStatusInterface;
import com.cgeye.gps.unitylocationplugin.loggers.GeohashRTFilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Created by CGEye on 2/13/18.
 */

public class KalmanLocationService extends Service
        implements SensorEventListener, LocationListener, GpsStatus.Listener {

    public static class Settings {
        private double accelerationDeviation;
        private int gpsMinDistance;
        private int gpsMinTime;
        private boolean useGpsSpeed;
        private int geoHashPrecision;
        private int geoHashMinPointCount;
        private int sensorFfequencyHz;
        private ILogger logger;
        private boolean filterMockGpsCoordinates;


        public Settings(double accelerationDeviation,
                        int gpsMinDistance,
                        int gpsMinTime,
                        int geoHashPrecision,
                        int geoHashMinPointCount,
                        int sensorFfequencyHz,
                        ILogger logger,
                        boolean filterMockGpsCoordinates) {
            this.accelerationDeviation = accelerationDeviation;
            this.gpsMinDistance = gpsMinDistance;
            this.gpsMinTime = gpsMinTime;
            this.geoHashPrecision = geoHashPrecision;
            this.geoHashMinPointCount = geoHashMinPointCount;
            this.sensorFfequencyHz = sensorFfequencyHz;
            this.logger = logger;
            this.filterMockGpsCoordinates = filterMockGpsCoordinates;
        }

        public Settings(double accelerationDeviation,
                        int gpsMinDistance,
                        int gpsMinTime,
                        boolean useGpsSpeed,
                        int geoHashPrecision,
                        int geoHashMinPointCount,
                        int sensorFfequencyHz,
                        ILogger logger,
                        boolean filterMockGpsCoordinates) {
            this.accelerationDeviation = accelerationDeviation;
            this.gpsMinDistance = gpsMinDistance;
            this.gpsMinTime = gpsMinTime;
            this.useGpsSpeed = useGpsSpeed;
            this.geoHashPrecision = geoHashPrecision;
            this.geoHashMinPointCount = geoHashMinPointCount;
            this.sensorFfequencyHz = sensorFfequencyHz;
            this.logger = logger;
            this.filterMockGpsCoordinates = filterMockGpsCoordinates;
        }
    }

    public static final String TAG = "KalmanLocationService";

    //region Location service implementation. Maybe we need to move it to some abstract class?
    protected List<LocationServiceInterface> m_locationServiceInterfaces;
    protected List<LocationServiceStatusInterface> m_locationServiceStatusInterfaces;

    protected Location m_lastLocation;

    protected ServiceStatus m_serviceStatus = ServiceStatus.SERVICE_STOPPED;

    public enum ServiceStatus {
        PERMISSION_DENIED(0),
        SERVICE_STOPPED(1),
        SERVICE_STARTED(2),
        HAS_LOCATION(3),
        SERVICE_PAUSED(4);

        int value;

        ServiceStatus(int value) { this.value = value;}

        public int getValue() { return value; }
    }

    public boolean isSensorsEnabled() {
        return m_sensorsEnabled;
    }

    public boolean IsRunning() {
        return m_serviceStatus != ServiceStatus.SERVICE_STOPPED && m_serviceStatus != ServiceStatus.SERVICE_PAUSED && m_sensorsEnabled;
    }

    public void addInterface(LocationServiceInterface locationServiceInterface) {
        if (m_locationServiceInterfaces.add(locationServiceInterface) && m_lastLocation != null) {
            locationServiceInterface.locationChanged(m_lastLocation);
        }
    }

    public void addInterfaces(List<LocationServiceInterface> locationServiceInterfaces) {
        if (m_locationServiceInterfaces.addAll(locationServiceInterfaces) && m_lastLocation != null) {
            for (LocationServiceInterface locationServiceInterface : locationServiceInterfaces) {
                locationServiceInterface.locationChanged(m_lastLocation);
            }
        }
    }

    public void removeInterface(LocationServiceInterface locationServiceInterface) {
        m_locationServiceInterfaces.remove(locationServiceInterface);
    }

    public void removeStatusInterface(LocationServiceStatusInterface locationServiceStatusInterface) {
        m_locationServiceStatusInterfaces.remove(locationServiceStatusInterface);
    }

    public void addStatusInterface(LocationServiceStatusInterface locationServiceStatusInterface) {
        if (m_locationServiceStatusInterfaces.add(locationServiceStatusInterface)) {
            locationServiceStatusInterface.serviceStatusChanged(m_serviceStatus);
            locationServiceStatusInterface.GPSStatusChanged(m_activeSatellites);
            locationServiceStatusInterface.GPSEnabledChanged(m_gpsEnabled);
            locationServiceStatusInterface.lastLocationAccuracyChanged(m_lastLocationAccuracy);
        }
    }

    public void addStatusInterfaces(List<LocationServiceStatusInterface> locationServiceStatusInterfaces) {
        if (m_locationServiceStatusInterfaces.addAll(locationServiceStatusInterfaces)) {
            for (LocationServiceStatusInterface locationServiceStatusInterface : locationServiceStatusInterfaces) {
                locationServiceStatusInterface.serviceStatusChanged(m_serviceStatus);
                locationServiceStatusInterface.GPSStatusChanged(m_activeSatellites);
                locationServiceStatusInterface.GPSEnabledChanged(m_gpsEnabled);
                locationServiceStatusInterface.lastLocationAccuracyChanged(m_lastLocationAccuracy);
            }
        }
    }

    public Location getLastLocation() {
        return m_lastLocation;
    }

    /*Service implementation*/
    public class LocalBinder extends Binder {
        public KalmanLocationService getService() {
            return KalmanLocationService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stop();
        Log.d(TAG, "onTaskRemoved: " + rootIntent);
        m_locationServiceInterfaces.clear();
        m_locationServiceStatusInterfaces.clear();
        stopSelf();
    }
    //endregion

    private GeohashRTFilter m_geoHashRTFilter = null;
    public GeohashRTFilter getGeoHashRTFilter() {
        return m_geoHashRTFilter;
    }

    public static Settings defaultSettings =
            new Settings(Utils.ACCELEROMETER_DEFAULT_DEVIATION,
                    Utils.GPS_MIN_DISTANCE, Utils.GPS_MIN_TIME,
                    Utils.GEOHASH_DEFAULT_PREC, Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT,
                    Utils.SENSOR_DEFAULT_FREQ_HZ,
                    null, false);

    public static Settings defaultSett =
            new Settings(Utils.ACCELEROMETER_DEFAULT_DEVIATION,
                    Utils.GPS_MIN_DISTANCE, Utils.GPS_MIN_TIME, Utils.USE_GPS_SPEED,
                    Utils.GEOHASH_DEFAULT_PREC, Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT,
                    Utils.SENSOR_DEFAULT_FREQ_HZ,
                    null, false);

    private Settings m_settings;
    private LocationManager m_locationManager;
    private PowerManager m_powerManager;
    private PowerManager.WakeLock m_wakeLock;

    private boolean m_gpsEnabled = false;
    private boolean m_sensorsEnabled = false;

    private int m_activeSatellites = 0;
    private float m_lastLocationAccuracy = 0;
    private GpsStatus m_gpsStatus;

    /**/
    private GPSAccKalmanFilter m_kalmanFilter;
    private SensorDataEventLoopTask m_eventLoopTask;
    private List<Sensor> m_lstSensors;
    private SensorManager m_sensorManager;
    private double m_magneticDeclination = 0.0;

    /*accelerometer + rotation vector*/
    private static int[] sensorTypes = {
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_GYROSCOPE,
    };

    private float[] rotationMatrix = new float[16];
    private float[] rotationMatrixInv = new float[16];
    private float[] absAcceleration = new float[4];
    private float[] linearAcceleration = new float[4];

    private float[] rotationVectorMatrix = new float[9];
    public float[] compassValues = new float[3];
    public float[] smoothedCompassValues = new float[3];
    public float[] compassDegreesValues = new float[3];
    private float[] rotationMatrixOrientation = new float[9];
    private float[] inclinationMatrixOrientation = new float[9];
    private float[] gravityVector = new float[3];
    private float[] geomagnetic = new float[3];
    private float[] smoothed = new float[3];
    public float[] orientationMagNoth = new float[3];
    public float[] lastOrientationMagNorth = new float[3];
    private float gyroThreshold = 0.2f;
    private int gyroNReadings = 5;
    private Queue lastNGyroReadings = new LinkedList();
    public float[] orientationResults = new float[3];
    public float[] accelerometerData = new float[3];
    public float[] gyroData = new float[3];
    //!

    //Magnetometer values
    public int magAccValue = 0;
    public int magAccStatus = 0;

    private Queue<SensorGpsDataItem> m_sensorDataQueue =
            new PriorityBlockingQueue<>();

    private void log2File(String format, Object... args) {
        if (m_settings.logger != null)
            m_settings.logger.log2file(format, args);
    }

    class SensorDataEventLoopTask extends AsyncTask {
        boolean needTerminate = false;
        long deltaTMs;
        KalmanLocationService owner;
        SensorDataEventLoopTask(long deltaTMs, KalmanLocationService owner) {
            this.deltaTMs = deltaTMs;
            this.owner = owner;
        }

        private void handlePredict(SensorGpsDataItem sdi) {
            log2File("%d%d KalmanPredict : accX=%f, accY=%f",
                    Utils.LogMessageType.KALMAN_PREDICT.ordinal(),
                    (long)sdi.getTimestamp(),
                    sdi.getAbsEastAcc(),
                    sdi.getAbsNorthAcc());
            m_kalmanFilter.predict(sdi.getTimestamp(), sdi.getAbsEastAcc(), sdi.getAbsNorthAcc());
        }

        private void handleUpdate(SensorGpsDataItem sdi) {
            double xVel = sdi.getSpeed() * Math.cos(sdi.getCourse());
            double yVel = sdi.getSpeed() * Math.sin(sdi.getCourse());
            log2File("%d%d KalmanUpdate : pos lon=%f, lat=%f, xVel=%f, yVel=%f, posErr=%f, velErr=%f",
                    Utils.LogMessageType.KALMAN_UPDATE.ordinal(),
                    (long)sdi.getTimestamp(),
                    sdi.getGpsLon(),
                    sdi.getGpsLat(),
                    xVel,
                    yVel,
                    sdi.getPosErr(),
                    sdi.getVelErr()
            );

            m_kalmanFilter.update(
                    sdi.getTimestamp(),
                    Coordinates.longitudeToMeters(sdi.getGpsLon()),
                    Coordinates.latitudeToMeters(sdi.getGpsLat()),
                    xVel,
                    yVel,
                    sdi.getPosErr(),
                    sdi.getVelErr()
            );
        }

        private Location locationAfterUpdateStep(SensorGpsDataItem sdi) {
            double xVel, yVel;
            Location loc = new Location(TAG);
            GeoPoint pp = Coordinates.metersToGeoPoint(m_kalmanFilter.getCurrentX(),
                    m_kalmanFilter.getCurrentY());
            loc.setLatitude(pp.Latitude);
            loc.setLongitude(pp.Longitude);
            loc.setAltitude(sdi.getGpsAlt());
            xVel = m_kalmanFilter.getCurrentXVel();
            yVel = m_kalmanFilter.getCurrentYVel();
            double speed = Math.sqrt(xVel*xVel + yVel*yVel); //scalar speed without bearing
            loc.setBearing((float)sdi.getCourse());
            loc.setSpeed((float) speed);
            loc.setTime(System.currentTimeMillis());
            loc.setElapsedRealtimeNanos(System.nanoTime());
            loc.setAccuracy((float) sdi.getPosErr());

            if (m_geoHashRTFilter != null) {
                m_geoHashRTFilter.filter(loc);
            }

            return loc;
        }

        @SuppressLint("DefaultLocale")
        @Override
        protected Object doInBackground(Object[] objects) {
            while (!needTerminate) {
                try {
                    Thread.sleep(deltaTMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue; //bad
                }

                SensorGpsDataItem sdi;
                double lastTimeStamp = 0.0;
                while ((sdi = m_sensorDataQueue.poll()) != null) {
                    if (sdi.getTimestamp() < lastTimeStamp) {
                        continue;
                    }
                    lastTimeStamp = sdi.getTimestamp();

                    //warning!!!
                    if (sdi.getGpsLat() == SensorGpsDataItem.NOT_INITIALIZED) {
                        handlePredict(sdi);
                    } else {
                        handleUpdate(sdi);
                        Location loc = locationAfterUpdateStep(sdi);
                        publishProgress(loc);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            onLocationChangedImp((Location) values[0]);
        }

        void onLocationChangedImp(Location location) {
            if (location == null || location.getLatitude() == 0 ||
                    location.getLongitude() == 0 ||
                    !location.getProvider().equals(TAG)) {
                return;
            }

            m_serviceStatus = ServiceStatus.HAS_LOCATION;
            m_lastLocation = location;
            m_lastLocationAccuracy = location.getAccuracy();

            if (ActivityCompat.checkSelfPermission(owner, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                m_gpsStatus = m_locationManager.getGpsStatus(m_gpsStatus);
            }

            int activeSatellites = 0;
            if (m_gpsStatus != null) {
                for (GpsSatellite satellite : m_gpsStatus.getSatellites()) {
                    activeSatellites += satellite.usedInFix() ? 1 : 0;
                }
                m_activeSatellites = activeSatellites;
            }

            for (LocationServiceInterface locationServiceInterface : m_locationServiceInterfaces) {
                locationServiceInterface.locationChanged(location);
            }
            for (LocationServiceStatusInterface locationServiceStatusInterface : m_locationServiceStatusInterfaces) {
                locationServiceStatusInterface.serviceStatusChanged(m_serviceStatus);
                locationServiceStatusInterface.lastLocationAccuracyChanged(m_lastLocationAccuracy);
                locationServiceStatusInterface.GPSStatusChanged(m_activeSatellites);
            }

        }
    }

    public KalmanLocationService() {
        m_locationServiceInterfaces = new ArrayList<>();
        m_locationServiceStatusInterfaces = new ArrayList<>();
        m_lstSensors = new ArrayList<Sensor>();
        m_eventLoopTask = null;
        reset(defaultSettings);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        m_locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        m_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        m_powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        m_wakeLock = m_powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        if (m_sensorManager == null) {
            m_sensorsEnabled = false;
            return; //todo handle somehow
        }

        for (Integer st : sensorTypes) {
            Sensor sensor = m_sensorManager.getDefaultSensor(st);
            if (sensor == null) {
                Log.d(TAG, String.format("Couldn't get sensor %d", st));
                continue;
            }
            m_lstSensors.add(sensor);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void start() {
        m_wakeLock.acquire();
        m_sensorDataQueue.clear();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            m_serviceStatus = ServiceStatus.PERMISSION_DENIED;
        } else {
            m_serviceStatus = ServiceStatus.SERVICE_STARTED;
            m_locationManager.removeGpsStatusListener(this);
            m_locationManager.addGpsStatusListener(this);
            m_locationManager.removeUpdates(this);
            m_locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    m_settings.gpsMinTime, m_settings.gpsMinDistance, this );
        }

        m_sensorsEnabled = true;
        for (Sensor sensor : m_lstSensors) {
            m_sensorManager.unregisterListener(this, sensor);
            m_sensorsEnabled &= !m_sensorManager.registerListener(this, sensor,
                    Utils.hertz2periodUs(m_settings.sensorFfequencyHz));
        }
        m_gpsEnabled = m_locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
            ilss.serviceStatusChanged(m_serviceStatus);
            ilss.GPSEnabledChanged(m_gpsEnabled);
        }

        m_eventLoopTask = new SensorDataEventLoopTask(500, this);
        m_eventLoopTask.needTerminate = false;
        m_eventLoopTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void stop() {
        if (m_wakeLock.isHeld())
            m_wakeLock.release();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            m_serviceStatus = ServiceStatus.SERVICE_STOPPED;
        } else {
            m_serviceStatus = ServiceStatus.SERVICE_PAUSED;
            m_locationManager.removeGpsStatusListener(this);
            m_locationManager.removeUpdates(this);
        }

        if (m_geoHashRTFilter != null) {
            m_geoHashRTFilter.stop();
        }

        m_sensorsEnabled = false;
        m_gpsEnabled = false;
        for (Sensor sensor : m_lstSensors)
            m_sensorManager.unregisterListener(this, sensor);

        for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
            ilss.serviceStatusChanged(m_serviceStatus);
            ilss.GPSEnabledChanged(m_gpsEnabled);
        }

        if (m_eventLoopTask != null) {
            m_eventLoopTask.needTerminate = true;
            m_eventLoopTask.cancel(true);
        }
        m_sensorDataQueue.clear();
    }

    public void reset(Settings settings) {
        m_settings = settings;
        m_kalmanFilter = null;

        if (m_settings.geoHashPrecision != 0 &&
                m_settings.geoHashMinPointCount != 0) {
            m_geoHashRTFilter = new GeohashRTFilter(m_settings.geoHashPrecision,
                    m_settings.geoHashMinPointCount);
        }
    }


    boolean avrg = false;
    private Queue<RawSensorDataItem> dataForAverageSensorData =
            new LinkedList<>();

    private SensorGpsDataItem averageData(int size) {
        double northAbsAcc = 0;
        double eastAbsAcc = 0;
        double upAbsAcc = 0;

        long now = android.os.SystemClock.elapsedRealtimeNanos();
        long nowMs = Utils.nano2milli(now);

        for (int i = 0; i < size; i++) {
            RawSensorDataItem rsdiTemp = dataForAverageSensorData.poll();
            if (rsdiTemp != null) {
                northAbsAcc += rsdiTemp.getNorth();
                eastAbsAcc += rsdiTemp.getEast();
                upAbsAcc += rsdiTemp.getUp();
            }
        }
        northAbsAcc /= size;
        eastAbsAcc /= size;
        upAbsAcc /= size;

        SensorGpsDataItem sdi = new SensorGpsDataItem(nowMs,
                SensorGpsDataItem.NOT_INITIALIZED,
                SensorGpsDataItem.NOT_INITIALIZED,
                SensorGpsDataItem.NOT_INITIALIZED,
                northAbsAcc,
                eastAbsAcc,
                upAbsAcc,
                SensorGpsDataItem.NOT_INITIALIZED,
                SensorGpsDataItem.NOT_INITIALIZED,
                SensorGpsDataItem.NOT_INITIALIZED,
                SensorGpsDataItem.NOT_INITIALIZED,
                m_magneticDeclination);

        return sdi;
    }

    /*SensorEventListener methods implementation*/
    @Override
    public void onSensorChanged(SensorEvent event) {
        final int east = 0;
        final int north = 1;
        final int up = 2;

        long now = android.os.SystemClock.elapsedRealtimeNanos();
        long nowMs = Utils.nano2milli(now);
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                System.arraycopy(event.values, 0, linearAcceleration, 0, event.values.length);
                android.opengl.Matrix.multiplyMV(absAcceleration, 0, rotationMatrixInv,
                        0, linearAcceleration, 0);

                String logStr = String.format("%d%d abs acc: %f %f %f",
                        Utils.LogMessageType.ABS_ACC_DATA.ordinal(),
                        nowMs, absAcceleration[east], absAcceleration[north], absAcceleration[up]);
                log2File(logStr);

                //Log.d("ACC_TAG", logStr);

                if (m_kalmanFilter == null) {
                    break;
                }


                SensorGpsDataItem sdi = new SensorGpsDataItem(nowMs,
                        SensorGpsDataItem.NOT_INITIALIZED,
                        SensorGpsDataItem.NOT_INITIALIZED,
                        SensorGpsDataItem.NOT_INITIALIZED,
                        absAcceleration[north],
                        absAcceleration[east],
                        absAcceleration[up],
                        SensorGpsDataItem.NOT_INITIALIZED,
                        SensorGpsDataItem.NOT_INITIALIZED,
                        SensorGpsDataItem.NOT_INITIALIZED,
                        SensorGpsDataItem.NOT_INITIALIZED,
                        m_magneticDeclination);

                /*Changes:
                RawSensorDataItem rsdi = new RawSensorDataItem(absAcceleration[north],
                        absAcceleration[east],
                        absAcceleration[up]);

                SensorGpsDataItem avrgDataItem = null;
                dataForAverageSensorData.add(rsdi);
                if (dataForAverageSensorData.size() >= Utils.IMU_DATA_WINDOW) {
                    avrgDataItem = averageData(dataForAverageSensorData.size());
                    avrg = true;
                }

                if (avrg && avrgDataItem != null) {
                    m_sensorDataQueue.add(avrgDataItem);
                    avrg = false;
                }

                */
                m_sensorDataQueue.add(sdi);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                android.opengl.Matrix.invertM(rotationMatrixInv, 0, rotationMatrix, 0);

                SensorManager.getRotationMatrixFromVector(rotationVectorMatrix, event.values);
                SensorManager.getOrientation(rotationVectorMatrix, compassValues);
                smoothedCompassValues = LowPassFilter.filter(compassValues, smoothedCompassValues);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                magAccValue = event.accuracy;
                float[] magneticData = new float[3];
                System.arraycopy(event.values, 0, magneticData, 0, 3);
                smoothed = LowPassFilter.filter(magneticData, geomagnetic);
                System.arraycopy(smoothed, 0, geomagnetic, 0, 3);
                break;

            case Sensor.TYPE_ACCELEROMETER:
                //float[] accelerometerData = new float[3];
                System.arraycopy(event.values, 0, accelerometerData, 0, 3);
                smoothed = LowPassFilter.filter(accelerometerData, gravityVector);
                System.arraycopy(smoothed, 0, gravityVector, 0, 3);
                break;

            case Sensor.TYPE_GRAVITY:
                // Use gravity to avoid movement of device
                /*
                System.arraycopy(event.values, 0, accelerometerData, 0, 3);
                smoothed = LowPassFilter.filter(accelerometerData, gravityVector);
                System.arraycopy(smoothed, 0, gravityVector, 0, 3);*/
                break;

            case Sensor.TYPE_GYROSCOPE:
                //gyroDataFunction(event);
                System.arraycopy(event.values, 0, gyroData, 0, 3);
                break;
        }

        if (SensorManager.getRotationMatrix(rotationMatrixOrientation, inclinationMatrixOrientation,
                gravityVector, geomagnetic)) {
            if (lastNGyroReadings.size() > gyroNReadings) {
                lastNGyroReadings.remove();
            }
            lastNGyroReadings.add(gyroData);
            float gyroMagnitude = calculateGyroAverageMagnitude();

            if (gyroMagnitude < gyroThreshold) {
                orientationResults = SensorManager.getOrientation(rotationMatrixOrientation, orientationMagNoth);
                lastOrientationMagNorth = orientationResults;
            }
        }
    }

    private float calculateGyroAverageMagnitude() {
        Iterator iterator = lastNGyroReadings.iterator();
        float gyroMagnitude = 0.0f;
        while (iterator.hasNext()) {
            float[] gyroInfo = (float[]) iterator.next();
            gyroMagnitude += (Math.pow(gyroInfo[0], 2) + Math.pow(gyroInfo[1], 2) + Math.pow(gyroInfo[2], 2));
        }
        gyroMagnitude = gyroMagnitude / lastNGyroReadings.size();
        return gyroMagnitude;
    }

    private static final float EPSILON = 0.000000001f;
    private boolean initState = true;
    private float timestamp;
    private static final float NS2S = 1.0f / 1000000000.0f;
    //angular speeds from gyro
    private float[] gyro = new float[3];
    //rotation matrix from gyro data
    private float[] gyroMatrix = new float[9];
    //orientation angles from gyro matrix
    private float[] gyroOrientation = new float[3];

    private float[] getRotationVectorFromGyro(float[] gyroValues, float timeFactor) {
        float[] normValues = new float[3];

        // Calculate the angular speed of the sample
        float omegaMagnitude = (float) Math.sqrt(gyroValues[0] * gyroValues[0] +
                        gyroValues[1] * gyroValues[1] +
                        gyroValues[2] * gyroValues[2]);

        // Normalize the rotation vector if it's big enough to get the axis
        if(omegaMagnitude > EPSILON) {
            normValues[0] = gyroValues[0] / omegaMagnitude;
            normValues[1] = gyroValues[1] / omegaMagnitude;
            normValues[2] = gyroValues[2] / omegaMagnitude;
        }

        // Integrate around this axis with the angular speed by the timestep
        // in order to get a delta rotation from this sample over the timestep
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        float thetaOverTwo = omegaMagnitude * timeFactor;
        float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
        float[] deltaRotationVector = new float[4];
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
        deltaRotationVector[3] = cosThetaOverTwo;

        return deltaRotationVector;
    }
    /* CONT
    private void gyroDataFunction(SensorEvent event) {
        if (orientationMagNoth == null) {
            return;
        }

        //initialisation of the gyroscope based rotation matrix
        if(initState) {
            float[] initMatrix = new float[9];
            initMatrix = getRotationMatrixFromOrientation(accMagOrientation);
            float[] test = new float[3];
            SensorManager.getOrientation(initMatrix, test);
            gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix);
            initState = false;
        }

        // copy the new gyro values into the gyro array
        // convert the raw gyro data into a rotation vector
        float[] deltaVector = new float[4];
        if(timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            System.arraycopy(event.values, 0, gyro, 0, 3);
            deltaVector = getRotationVectorFromGyro(gyro, dT / 2.0f);
        }

        // measurement done, save current time for next interval
        timestamp = event.timestamp;

        // convert rotation vector into rotation matrix
        float[] deltaMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);

        // apply the new rotation interval on the gyroscope based rotation matrix
        gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);

        // get the gyroscope based orientation from the rotation matrix
        SensorManager.getOrientation(gyroMatrix, gyroOrientation);
    }
    */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /*do nothing*/
        //Check for changes in magnetic fields
        switch (sensor.getType()) {
            case Sensor.TYPE_MAGNETIC_FIELD:
                magAccStatus = accuracy;
                break;
        }
    }

    /*LocationListener methods implementation*/
    @Override
    public void onLocationChanged(Location loc) {

        if (loc == null) return;
        //if (m_settings.filterMockGpsCoordinates && loc.isFromMockProvider()) return; MOCKUP

        double x, y, xVel, yVel, posDev, course, speed;
        long timeStamp;
        speed = loc.getSpeed();
        course = loc.getBearing();
        x = loc.getLongitude();
        y = loc.getLatitude();
        xVel = speed * Math.cos(course);
        yVel = speed * Math.sin(course);
        posDev = loc.getAccuracy();
        timeStamp = Utils.nano2milli(loc.getElapsedRealtimeNanos());

        //WARNING!!! here should be speed accuracy, but loc.hasSpeedAccuracy()
        // and loc.getSpeedAccuracyMetersPerSecond() requares API 26
        double velErr = loc.getAccuracy() * 0.1;

        String logStr = String.format("%d%d GPS : pos lat=%f, lon=%f, alt=%f, hdop=%f, speed=%f, bearing=%f, sa=%f",
                Utils.LogMessageType.GPS_DATA.ordinal(),
                timeStamp, loc.getLatitude(),
                loc.getLongitude(), loc.getAltitude(), loc.getAccuracy(),
                loc.getSpeed(), loc.getBearing(), velErr);
        log2File(logStr);

        GeomagneticField f = new GeomagneticField(
                (float)loc.getLatitude(),
                (float)loc.getLongitude(),
                (float)loc.getAltitude(),
                timeStamp);
        m_magneticDeclination = f.getDeclination();

        if (m_kalmanFilter == null) {
            log2File("%d%d KalmanAlloc : lon=%f, lat=%f, speed=%f, course=%f, m_accDev=%f, posDev=%f",
                    Utils.LogMessageType.KALMAN_ALLOC.ordinal(),
                    timeStamp, x, y, speed, course, m_settings.accelerationDeviation, posDev);
            m_kalmanFilter = new GPSAccKalmanFilter(
                    Utils.USE_GPS_SPEED,
                    Coordinates.longitudeToMeters(x),
                    Coordinates.latitudeToMeters(y),
                    xVel,
                    yVel,
                    m_settings.accelerationDeviation,
                    posDev,
                    timeStamp);
            /*
            m_kalmanFilter = new GPSAccKalmanFilter(
                    Utils.USE_GPS_SPEED,
                    Coordinates.longitudeToMeters(x),
                    Coordinates.latitudeToMeters(y),
                    xVel,
                    yVel,
                    m_settings.accelerationDeviation,
                    posDev,
                    timeStamp);*/
            return;
        }

        SensorGpsDataItem sdi = new SensorGpsDataItem(
                timeStamp, loc.getLatitude(), loc.getLongitude(), loc.getAltitude(),
                SensorGpsDataItem.NOT_INITIALIZED,
                SensorGpsDataItem.NOT_INITIALIZED,
                SensorGpsDataItem.NOT_INITIALIZED,
                loc.getSpeed(),
                loc.getBearing(),
                loc.getAccuracy(),
                velErr,
                m_magneticDeclination);
        m_sensorDataQueue.add(sdi);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        /*do nothing*/
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "onProviderEnabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            m_gpsEnabled = true;
            for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
                ilss.GPSEnabledChanged(m_gpsEnabled);
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "onProviderDisabled: " + provider);
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            m_gpsEnabled = false;
            for (LocationServiceStatusInterface ilss : m_locationServiceStatusInterfaces) {
                ilss.GPSEnabledChanged(m_gpsEnabled);
            }
        }
    }

    /*GpsStatus.Listener implementation. do we really need this? */
    @Override
    public void onGpsStatusChanged(int event) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            m_gpsStatus = m_locationManager.getGpsStatus(m_gpsStatus);
        }

        int activeSatellites = 0;
        if (m_gpsStatus != null) {
            for (GpsSatellite satellite : m_gpsStatus.getSatellites()) {
                activeSatellites += satellite.usedInFix() ? 1 : 0;
            }

            if (activeSatellites != 0) {
                this.m_activeSatellites = activeSatellites;
                for (LocationServiceStatusInterface locationServiceStatusInterface : m_locationServiceStatusInterfaces) {
                    locationServiceStatusInterface.GPSStatusChanged(this.m_activeSatellites);
                }
            }
        }
    }
}
