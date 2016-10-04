package com.example.android.sunshine.app;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.SunshineWearCommon;
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

    @Override
    protected void onHandleIntent(Intent intent) {

        if (intent != null && intent.getAction() != null && intent.getAction().equals(UPDATE)) {

            Log.e("SunPhone", "-----On Intent-----");

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
        // TODO put relevant data
        Log.e("SunPhone", "-----Successfully connected to emulator-----");
        putDataMapRequest.getDataMap().putString(SunshineWearCommon.KEY_ID, "Message from Phone");
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e("SunPhone", "-----On onConnectionSuspended-----");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e("SunPhone", "-----On onConnectionFailed-----");
    }

/*
// TODO Fix onDestroy
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("SunPhone", "-----On onDestroy-----");
        mGoogleApiClient.disconnect();
    }
*/
}
