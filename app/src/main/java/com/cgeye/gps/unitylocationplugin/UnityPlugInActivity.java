package com.cgeye.gps.unitylocationplugin;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;
import com.unity3d.player.UnityPlayerActivity;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by CGEye.
 */

public class UnityPlugInActivity extends UnityPlayerActivity {
    private static final String LOG_TAG = "AndroidLocationPlugIn";
    private static final int REQUEST_LOCATION = 1;
    private static final int LOCATION_REQUEST_CODE = 1010;
    private Intent locationIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "UnityPlayerActivity:onCreate() called.");
        locationIntent = new Intent(getApplicationContext(), LocationService.class);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "UnityPluginActivity:onDestroy() called.");
        stopLocationService();
    }


    public void startLocationService() {
        Log.d(LOG_TAG, "UnityPluginActivity:startLocationService() called.");
        PendingIntent pendingIntent = createPendingResult(REQUEST_LOCATION, new Intent(), PendingIntent.FLAG_UPDATE_CURRENT);
        locationIntent.putExtra(LocationService.PENDING_INTENT, pendingIntent);
        startService(locationIntent);
    }

    public void stopLocationService() {
        Log.d(LOG_TAG, "UnityPluginActivity:stopLocationService() called.");
        stopService(locationIntent);
    }

    public String getLocationsJson(long time) {
        Log.d(LOG_TAG, "UnityPluginActivity: getLocationsJson after " + time + " seconds.");
        //Cursor cursor = getContentResolver().query(LocationContentProvider.CONTENT_URI, null,
        //        LocationContentProvider.LOCATION_TIME + " >= " + time,
        //        null, null);
        Cursor cursor = getContentResolver().query(LocationContentProvider.CONTENT_URI, null,
                null, null, LocationContentProvider.LOCATION_TIME + " DESC");

        List<LocationData> locationUpdates = new ArrayList<>();
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                LocationData dto = new LocationData();
                dto.setTime(cursor.getLong(cursor.getColumnIndex(LocationContentProvider.LOCATION_TIME)));
                dto.setLongitude(cursor.getDouble(cursor.getColumnIndex(LocationContentProvider.LOCATION_LONGITUDE)));
                dto.setLatitude(cursor.getDouble(cursor.getColumnIndex(LocationContentProvider.LOCATION_LATITUDE)));
                dto.setAddressLine(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_ADDRESSLINE)));
                dto.setStreetName(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_STREETNAME)));
                dto.setCity(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_CITY)));
                dto.setState(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_STATE)));
                dto.setSubAdminArea(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_SUBADMINAREA)));
                dto.setCountryCode(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_COUNTRYCODE)));
                dto.setCountryName(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_COUNTRYNAME)));
                dto.setPostalCode(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_POSTALCODE)));
                dto.setKnownName(cursor.getString(cursor.getColumnIndex(LocationContentProvider.LOCATION_KNOWNNAME)));
                locationUpdates.add(dto);
            }
            cursor.close();
        }

        String json = new Gson().toJson(locationUpdates);

        Log.d(LOG_TAG, "Json: " + json);
        return json;
    }

    public void deleteLocationsBefore(long time) {
        Log.d(LOG_TAG, "UnityPluginActivity: deleteLocationsBefore " + time + " seconds.");
        int rowsDeleted = getContentResolver().delete(LocationContentProvider.CONTENT_URI,
                LocationContentProvider.LOCATION_TIME + " <= " + time,
                null);
        Log.d(LOG_TAG, "Deleted: " + rowsDeleted + "rows");
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean hasPermission() {
        //return PackageManager.PERMISSION_GRANTED == PermissionChecker.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION);
        return PackageManager.PERMISSION_GRANTED == checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermissions() {
        if (!hasPermission()) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
    }

}
