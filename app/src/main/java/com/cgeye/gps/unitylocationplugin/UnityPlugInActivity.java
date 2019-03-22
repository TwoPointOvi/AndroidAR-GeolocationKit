package com.cgeye.gps.unitylocationplugin;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.cgeye.gps.unitylocationplugin.commons.Utils;
import com.cgeye.gps.unitylocationplugin.interfaces.LocationServiceInterface;
import com.cgeye.gps.unitylocationplugin.interfaces.SimpleTempCallback;
import com.cgeye.gps.unitylocationplugin.loggers.GeohashRTFilter;
import com.cgeye.gps.unitylocationplugin.services.KalmanLocationService;
import com.cgeye.gps.unitylocationplugin.services.ServicesHelper;
import com.unity3d.player.UnityPlayerActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by CGEye.
 */

public class UnityPlugInActivity extends UnityPlayerActivity implements LocationServiceInterface {
    private static final String TAG = "AndroidLocationPlugIn";
    List<Location> kalmanLocations = new ArrayList<>();
    Location firstKalmanLocation;
    Location lastKalmanLocation;
    GeohashRTFilter geohashRTFilter;

    KalmanLocationService.Settings defaultSettings =
            new KalmanLocationService.Settings(Utils.ACCELEROMETER_DEFAULT_DEVIATION,
                    Utils.GPS_MIN_DISTANCE,
                    Utils.GPS_MIN_TIME,
                    Utils.GEOHASH_DEFAULT_PREC,
                    Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT,
                    Utils.SENSOR_DEFAULT_FREQ_HZ,
                    null, false);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Log.d(TAG, "UnityPlayerActivity:onCreate() called.");
        ServicesHelper.addLocationServiceInterface(this);
        Log.d(TAG, "UnityPlayerActivity:onCreate() Location service started.");

        /*Start kalman service location
        ServicesHelper.getLocationService(this, value -> {
            if (value.IsRunning()) {
                return;
            }
            value.stop();
            KalmanLocationService.Settings settings =
                    new KalmanLocationService.Settings(Utils.ACCELEROMETER_DEFAULT_DEVIATION,
                            Utils.GPS_MIN_DISTANCE,
                            Utils.GPS_MIN_TIME,
                            Utils.GEOHASH_DEFAULT_PREC,
                            Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT,
                            Utils.SENSOR_DEFAULT_FREQ_HZ, null, false);
            value.reset(settings); //here you can adjust your filter behavior
            value.start();
        }); */
        //Add the filter for filter
        geohashRTFilter = new GeohashRTFilter(Utils.GEOHASH_DEFAULT_PREC,
                Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT);

        ServicesHelper.getLocationService(this, new SimpleTempCallback<KalmanLocationService>() {
            @Override
            public void onCall(KalmanLocationService value) {
                if (value.IsRunning()) {
                    return;
                }
                value.stop();
                value.reset(defaultSettings); //here you can adjust your filter behavior
                value.start();
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "UnityPlayerActivity:onDestroy().");
        ServicesHelper.removeLocationServiceInterface(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "UnityPlayerActivity:onPause() Location service stopped.");
        /*stop kalman location service
        ServicesHelper.getLocationService(this, value -> {
            value.stop();
        });*/

        ServicesHelper.getLocationService(this, new SimpleTempCallback<KalmanLocationService>() {
            @Override
            public void onCall(KalmanLocationService value) {
                value.stop();
            }
        });
        geohashRTFilter.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "UnityPlayerActivity:onResume() Location service started.");
        /*Start kalman service location
        ServicesHelper.getLocationService(this, value -> {
            if (value.IsRunning()) {
                return;
            }
            value.stop();
            KalmanLocationService.Settings settings =
                    new KalmanLocationService.Settings(Utils.ACCELEROMETER_DEFAULT_DEVIATION,
                            Utils.GPS_MIN_DISTANCE,
                            Utils.GPS_MIN_TIME,
                            Utils.GEOHASH_DEFAULT_PREC,
                            Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT,
                            Utils.SENSOR_DEFAULT_FREQ_HZ, null, false);
            value.reset(settings); //here you can adjust your filter behavior
            value.start();
        }); */

        ServicesHelper.getLocationService(this, new SimpleTempCallback<KalmanLocationService>() {
            @Override
            public void onCall(KalmanLocationService value) {
                if (value.IsRunning()) {
                    return;
                }
                value.stop();
                value.reset(defaultSettings); //here you can adjust your filter behavior
                value.start();
            }
        });
        geohashRTFilter.reset(null);
    }

    @Override
    public void locationChanged(Location location) {
        if (firstKalmanLocation == null) {
            firstKalmanLocation = location;
        }
        lastKalmanLocation = location;
        Log.d(TAG, "UnityPlayerActivity: Location updated: " +
                location.getLatitude() + ", " + location.getLongitude());

        kalmanLocations.add(lastKalmanLocation);
        geohashRTFilter.filter(lastKalmanLocation);
    }

    public double[] getLastFilteredLocation() {
        double[] loc = new double[3];
        if (geohashRTFilter != null) {
            loc[0] = geohashRTFilter.getLastGeoFilteredLocation().getLatitude();
            loc[1] = geohashRTFilter.getLastGeoFilteredLocation().getLongitude();
            loc[2] = geohashRTFilter.getLastGeoFilteredLocation().getAltitude();
            Log.d("Filtered Loc", Double.toString(loc[0]) + ',' +
                    Double.toString(loc[1]) + ',' + Double.toString(loc[2]));
        }
        return loc;
    }

    public double[] getLastKalmanLocation() {
        double[] loc = new double[5];
        //Log.d(LOG_TAG, "Returning Kalman Location");
        if (lastKalmanLocation != null) {
            //Log.d(LOG_TAG, "Returning available location");
            loc[0] = lastKalmanLocation.getLatitude();
            loc[1] = lastKalmanLocation.getLongitude();
            loc[2] = lastKalmanLocation.getAltitude();
            loc[3] = (double)lastKalmanLocation.getBearing();
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc[4] = (double)lastKalmanLocation.getBearingAccuracyDegrees();
            } else {
                loc[4] = 0;
            }
        }
        return loc;
    }

    public float[] getMagnetometerData() {
        float[] magData = new float[2];
        magData[0] = ServicesHelper.getLocationService().magAccValue;
        magData[1] = ServicesHelper.getLocationService().magAccStatus;

        return magData;
    }

    public float[] distanceBetweenLocations(double lat, double lon, double alt) {
        float distance = 0;
        float angleBetweenPoints;
        float angleBetweenPointsA;
        // [0] -> distance        [1] -> angle
        float[] info = new float[3];

        Location loc = new Location("provider");
        loc.setLatitude(lat);
        loc.setLongitude(lon);
        loc.setAltitude(alt);

        if (lastKalmanLocation != null) {
            distance = lastKalmanLocation.distanceTo(loc);
        }

        angleBetweenPointsA = lastKalmanLocation.bearingTo(loc);
        angleBetweenPoints = bearingBetweenLocations(lastKalmanLocation, loc);
        Log.d(TAG, "UnityPlayerActivity:distanceBetweenLocations() bearingAndroid: " + angleBetweenPointsA
                        + " bearingCalc: " + angleBetweenPoints);

        info[0] = distance;
        info[1] = angleBetweenPoints;
        info[2] = angleBetweenPointsA;

        return info;
    }

    public float distanceBetweenCoordinates(double lat1, double lon1, double alt1, double lat2, double lon2, double alt2) {
        Location loc1 = new Location ("provider");
        loc1.setLatitude(lat1);
        loc1.setLongitude(lon1);
        loc1.setAltitude(alt1);
        Location loc2 = new Location ("provider");
        loc2.setLatitude(lat2);
        loc2.setLongitude(lon2);
        loc2.setAltitude(alt2);

        return loc1.distanceTo(loc2);
    }

    public double[] latlonToUTMCoordinates(double lat, double lon) {
        double[] info = new double[4];
        LatLonToUTM converterObj = new LatLonToUTM();
        LatLonToUTM converterUser = new LatLonToUTM();

        converterObj.convertLatLonToUTM(lat, lon);
        converterUser.convertLatLonToUTM(lastKalmanLocation.getLatitude(), lastKalmanLocation.getLongitude());

        info[0] = converterObj.returnEasting();
        info[1] = converterObj.returnNorthing();
        info[2] = converterUser.returnEasting();
        info[3] = converterUser.returnNorthing();

        return info;
    }

    public float bearingBetweenLocations(Location loc1, Location loc2) {
        float angleBetweenPoints;

        //Transform latitude and longitude to radians
        double loc1Lat, loc1Lon, loc2Lat, loc2Lon;
        loc1Lat = Math.toRadians(loc1.getLatitude());
        loc1Lon = Math.toRadians(loc1.getLongitude());
        loc2Lat = Math.toRadians(loc2.getLatitude());
        loc2Lon = Math.toRadians(loc2.getLongitude());

        double dLon = loc2Lon - loc1Lon;
        double y = Math.sin(dLon) * Math.cos(loc2Lon);
        double x = Math.cos(loc1Lat) * Math.sin(loc2Lat) - Math.sin(loc1Lat) * Math.cos(loc2Lat) * Math.cos(dLon);
        angleBetweenPoints = (float) Math.toDegrees((Math.atan2(y, x)));
        angleBetweenPoints = (angleBetweenPoints + 360) % 360;

        return angleBetweenPoints;
    }



    public void checkPermissions() {
        String[] interestedPermissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            interestedPermissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                    //Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    //Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            interestedPermissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
                    //Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }

        ArrayList<String> lstPermissions = new ArrayList<>(interestedPermissions.length);
        for (String perm : interestedPermissions) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                lstPermissions.add(perm);
            }
        }

        if (!lstPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, lstPermissions.toArray(new String[0]),
                    100);
        }
    }
}
