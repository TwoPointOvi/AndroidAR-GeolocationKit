package com.cgeye.gps.unitylocationplugin;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

/**
 * Created by CGEye.
 */

public class LocationContentProvider extends ContentProvider {
    private static final String LOG_TAG = "AndroidLocationPlugIn";

    private static final String DB_NAME = "cgeyeDB";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "location";
    public static final String LOCATION_TIME = "time";
    public static final String LOCATION_LATITUDE = "latitude";
    public static final String LOCATION_LONGITUDE = "longitude";
    public static final String LOCATION_ADDRESSLINE = "addressline";
    public static final String LOCATION_STREETNAME = "streetname";
    public static final String LOCATION_CITY = "city";
    public static final String LOCATION_STATE = "state";
    public static final String LOCATION_SUBADMINAREA = "subadminarea";
    public static final String LOCATION_COUNTRYCODE = "countrycode";
    public static final String LOCATION_COUNTRYNAME = "countryname";
    public static final String LOCATION_POSTALCODE = "postalcode";
    public static final String LOCATION_KNOWNNAME = "knownname";

    private static final String DB_CREATE = "CREATE TABLE " + TABLE + "(" +
            LOCATION_TIME + " INTEGER PRIMARY KEY, " +
            LOCATION_LATITUDE + " REAL, " +
            LOCATION_LONGITUDE + " REAL " +
            ");";

    private static final String AUTHORITY = "cgeye.plugin.provider.store";
    private static final String PATH = "location";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH);
    private static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd."
            + AUTHORITY + "." + PATH;

    private static final int URI_LOCATIONS = 1;

    private static final UriMatcher URI_MATCHER;
    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(AUTHORITY, PATH, URI_LOCATIONS);
    }

    private DBHelper dbHelper;

    @Override
    public boolean onCreate() {
        dbHelper = new DBHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (URI_MATCHER.match(uri) != URI_LOCATIONS) {
            throw new IllegalArgumentException("Wrong URI: " + uri);
        }

        Cursor cursor = dbHelper.getWritableDatabase().query(TABLE, projection, selection, selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), CONTENT_URI);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return (URI_MATCHER.match(uri) == URI_LOCATIONS) ? CONTENT_TYPE : null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        if (URI_MATCHER.match(uri) != URI_LOCATIONS) {
            throw new IllegalArgumentException("Wrong URI: " + uri);
        }

        long id = dbHelper.getWritableDatabase().insert(TABLE, null, contentValues);
        Uri resultUri = ContentUris.withAppendedId(CONTENT_URI, id);
        getContext().getContentResolver().notifyChange(resultUri, null);

        return resultUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (URI_MATCHER.match(uri) != URI_LOCATIONS) {
            throw new IllegalArgumentException("Wrong URI: " + uri);
        }

        int rowsAffected = dbHelper.getWritableDatabase().delete(TABLE, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);

        return rowsAffected;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;
    }

    private static class DBHelper extends SQLiteOpenHelper {
        public DBHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
            Log.d(LOG_TAG, "DBHelper init");
        }

        public void onCreate(SQLiteDatabase database) {
            Log.d(LOG_TAG, "Creating table: " + DB_CREATE);
            database.execSQL(DB_CREATE);
        }

        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        }
    }
}
