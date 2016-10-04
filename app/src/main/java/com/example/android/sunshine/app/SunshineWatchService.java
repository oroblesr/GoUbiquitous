package com.example.android.sunshine.app;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.SunshineWearCommon;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;


/**
 * Created by Oscar on 03/10/2016.
 * Credit to https://github.com/Electryc/SimpleAndroidWatchface/
 * for general guidance
 */

public class SunshineWatchService extends IntentService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String SERVICE = "SunshineWatchService";
    public static final String UPDATE = "update_watch";

    private GoogleApiClient mGoogleApiClient;

    public SunshineWatchService() {
        super(SERVICE);
    }

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    @Override
    protected void onHandleIntent(Intent intent) {

        if (intent != null && intent.getAction() != null && intent.getAction().equals(UPDATE)) {

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();


            mGoogleApiClient.connect();
        }

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(SunshineWearCommon.PATH);

        String locationQuery = Utility.getPreferredLocation(this);
        Uri weatherUri = WeatherContract.WeatherEntry
                .buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());


        Cursor data = getContentResolver().query(
                weatherUri,
                FORECAST_COLUMNS,
                null,
                null,
                null);

        if (data != null && data.moveToFirst()) {

            int weatherId = data.getInt(
                    data.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID));

            double high = data.getDouble(data.getColumnIndex(
                    WeatherContract.WeatherEntry.COLUMN_MAX_TEMP));

            double low = data.getDouble(data.getColumnIndex(
                    WeatherContract.WeatherEntry.COLUMN_MIN_TEMP));

            String highString = Utility.formatTemperature(this, high);
            String lowString = Utility.formatTemperature(this, low);

            putDataMapRequest.getDataMap()
                    .putInt(SunshineWearCommon.KEY_ID, weatherId);
            putDataMapRequest.getDataMap()
                    .putString(SunshineWearCommon.KEY_LOW, highString);
            putDataMapRequest.getDataMap()
                    .putString(SunshineWearCommon.KEY_HIGH, lowString);

            putDataMapRequest.getDataMap()
                    .putInt("value", (int) System.currentTimeMillis());

            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest.setUrgent());


            data.close();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }


    @Override
    public void onDestroy() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onDestroy();

    }

}
