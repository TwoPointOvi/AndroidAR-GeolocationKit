package com.cgeye.gps.unitylocationplugin;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import java.util.List;

import java.io.IOException;
import java.util.Locale;

/**
 * Created by CGEye.
 */

public class LocationService extends Service {
    private static final String LOG_TAG = "AndroidLocationPlugIn";
    public static final String PENDING_INTENT = "pendingIntent";
    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 1f;

    private LocationManager locationManager;

    private LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(LOG_TAG, "LocationListener:onLocationChanged(): " + location);
            saveLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int i, Bundle bundle) {
            Log.d(LOG_TAG, "LocationListener: statusChanged(): " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(LOG_TAG, "LocationListener: providerEnabled(): " + provider);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(LOG_TAG, "LocationListener: providerDisabled(): " + provider);
        }
    };


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "LocationService:onCreate() called.");
        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "LocationService:onDestroy() called.");
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(gpsListener);
            } catch (SecurityException e) {
                Log.d(LOG_TAG, "fail to remove location listner, ignore", e);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOG_TAG, "LocationService:onStartCommand flags=" + flags + " startId=" + startId);
        if (startId == 1) {
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, gpsListener);
            } catch (SecurityException ex) {
                Log.d(LOG_TAG, "fail to request location update, ignore", ex);
            } catch (IllegalArgumentException ex) {
                Log.d(LOG_TAG, "provider does not exist, " + ex);
            }
        }

        return START_STICKY; //service will be restarted in case of failures
    }

    private void saveLocation(Location location) {
        if (location == null) return;

        //Address Information
        String addressLine = null;
        String streetName = "";
        String city = "";
        String state = "";
        String subAdminArea = "";
        String countryName = "";
        String countryCode = "";
        String postalCode = "";
        String knownName = "";
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            Address address = addresses.get(0);

            addressLine = address.getAddressLine(0);
            streetName = address.getThoroughfare();
            city = address.getLocality();
            state = address.getAdminArea();
            subAdminArea = address.getSubAdminArea();
            countryName = address.getCountryName();
            countryCode = address.getCountryCode();
            postalCode = address.getPostalCode();
            knownName = address.getFeatureName(); //only if known

        } catch (IOException ex) {
            Log.d(LOG_TAG, "failed to request address", ex);
        }

        ContentValues values = new ContentValues();
        values.put(LocationContentProvider.LOCATION_TIME, System.currentTimeMillis());
        values.put(LocationContentProvider.LOCATION_LATITUDE, location.getLatitude());
        values.put(LocationContentProvider.LOCATION_LONGITUDE, location.getLongitude());
        values.put(LocationContentProvider.LOCATION_ADDRESSLINE, addressLine);
        values.put(LocationContentProvider.LOCATION_STREETNAME, streetName);
        values.put(LocationContentProvider.LOCATION_CITY, city);
        values.put(LocationContentProvider.LOCATION_STATE, state);
        values.put(LocationContentProvider.LOCATION_SUBADMINAREA, subAdminArea);
        values.put(LocationContentProvider.LOCATION_COUNTRYCODE, countryCode);
        values.put(LocationContentProvider.LOCATION_COUNTRYNAME, countryName);
        values.put(LocationContentProvider.LOCATION_POSTALCODE, postalCode);
        values.put(LocationContentProvider.LOCATION_KNOWNNAME, knownName);

        Uri uri = getContentResolver().insert(LocationContentProvider.CONTENT_URI, values);
        Log.d(LOG_TAG, "data lan,lon: " + location.getLatitude() + ";" + location.getLongitude()
        + ";" + streetName + ";" + city + ";" + state + ";" + subAdminArea + ";" + countryCode
                + ";" + countryName + ";" + postalCode + ";" + knownName);
        Log.d(LOG_TAG, "inserted new location, uri: " + uri);
    }
}
