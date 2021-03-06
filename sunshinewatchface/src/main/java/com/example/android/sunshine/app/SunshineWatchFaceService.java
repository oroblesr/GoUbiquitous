/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.SunshineWearCommon;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.graphics.Bitmap.createScaledBitmap;

/*
 * Created by Oscar.
 * Credit to https://github.com/Electryc/SimpleAndroidWatchface/
 * for general guidance
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        private Bitmap mWeatherBitmap;
        Paint mDatePaint;
        Paint mTextPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        Paint mLinePaint;
        Paint mBitmapPaint;
        boolean mAmbient;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initDateFormats();
            }
        };
        int mTapCount;

        float mLineHeight;
        float mLineThickness;
        float mBitmapSize;
        float mXOffset;
        float mYOffset;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;

        private GoogleApiClient mGoogleApiClient;

        int weatherId;
        String lowTemp;
        String highTemp;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            Context context = SunshineWatchFaceService.this;

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mLineThickness = resources.getDimension(R.dimen.line_thickness);
            mBitmapSize = resources.getDimension(R.dimen.bitmap_size);


            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(context,R.color.background));

            mWeatherBitmap = BitmapFactory.decodeResource(getResources(),
                    SunshineWatchFaceUtil.getResourceForWeatherId(weatherId));


            mTextPaint = createTextPaint(ContextCompat.getColor(context,R.color.digital_text),
                    BOLD_TYPEFACE);
            mDatePaint = createTextPaint(ContextCompat.getColor(context,R.color.date_text),
                    NORMAL_TYPEFACE);
            mHighTempPaint = createTextPaint(ContextCompat.getColor(context,R.color.high_temp_text),
                    BOLD_TYPEFACE);
            mLowTempPaint = createTextPaint(ContextCompat.getColor(context,R.color.low_temp_text),
                    NORMAL_TYPEFACE);

            mBitmapPaint = new Paint();
            mBitmapPaint.setAntiAlias(true);

            mLinePaint = new Paint();
            mLinePaint.setColor(ContextCompat.getColor(context,R.color.line_color));

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initDateFormats();

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        @Override
        public void onDestroy() {
            releaseApi();
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                mCalendar.setTimeZone(TimeZone.getDefault());
                initDateFormats();
                mGoogleApiClient.connect();
            } else {
                unregisterReceiver();
                releaseApi();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void releaseApi() {
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                //Wearable.DataApi.removeListener(mGoogleApiClient, onDataChangedListener);
                mGoogleApiClient.disconnect();
            }
        }
        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            float highTempTextSize = resources.getDimension(isRound
                    ? R.dimen.high_temp_text_size_round : R.dimen.high_temp_text_size);
            float lowTempTextSize = resources.getDimension(isRound
                    ? R.dimen.low_temp_text_size_round : R.dimen.low_temp_text_size);

            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(dateTextSize);
            mHighTempPaint.setTextSize(highTempTextSize);
            mLowTempPaint.setTextSize(lowTempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
                    mLinePaint.setAntiAlias(!inAmbientMode);
                    mBitmapPaint.setAntiAlias(!inAmbientMode);

                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFaceService.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            float widthCenter = bounds.width() / 2f;
            float heightCenter = bounds.height() / 2f;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss",Locale.getDefault());
            SimpleDateFormat sdfAmbient = new SimpleDateFormat("HH:mm",Locale.getDefault());

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            String text = mAmbient
                    ? sdfAmbient.format(mCalendar.getTime())
                    : sdf.format(mCalendar.getTime());

            canvas.drawText(text, widthCenter - mTextPaint.measureText(text)/2,
                    heightCenter - mLineHeight, mTextPaint);

            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {

                String high = highTemp == null ? "" : highTemp;
                String low = lowTemp == null ? "" : lowTemp;

                String date = mDayOfWeekFormat.format(mDate) + ", " + mDateFormat.format(mDate);
                // Date
                canvas.drawText(date, widthCenter - mDatePaint.measureText(date)/2,
                        heightCenter, mDatePaint);

                canvas.drawRect(mXOffset, heightCenter + mLineHeight/2 , bounds.width() - mXOffset,
                        heightCenter + mLineHeight/2 + mLineThickness, mLinePaint);


                int bitmapSize = (int) mBitmapSize;
                float bitmapXPosition = (widthCenter + mXOffset - mBitmapSize)/ 2 ;
                float bitmapYPosition = (heightCenter + bounds.height() - mBitmapSize )/ 2 ;
                float tempYPosition = (heightCenter + bounds.height())/ 2 ;
                // Weather Art
                canvas.drawBitmap(createScaledBitmap(mWeatherBitmap, bitmapSize, bitmapSize, true),
                        bitmapXPosition, bitmapYPosition, mBitmapPaint);

                // High Temp
                canvas.drawText(high, widthCenter, tempYPosition, mHighTempPaint);

                // Low Temp
                canvas.drawText(low, widthCenter + mHighTempPaint.measureText(high)*4/3,
                        tempYPosition, mLowTempPaint);

            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void initDateFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = new SimpleDateFormat("MMM dd yy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(onConnectedResultCallback);

        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }


        private final ResultCallback<DataItemBuffer> onConnectedResultCallback = new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(@NonNull DataItemBuffer dataItems) {
                for (DataItem item : dataItems) {
                    processConfigurationFor(item);
                }

                dataItems.release();
                invalidateIfNecessary();
            }
        };

        private void processConfigurationFor(DataItem item) {
            if (SunshineWearCommon.PATH.equals(item.getUri().getPath())) {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                if (dataMap.containsKey(SunshineWearCommon.KEY_ID)) {
                    weatherId = dataMap.getInt(SunshineWearCommon.KEY_ID);
                    lowTemp = dataMap.getString(SunshineWearCommon.KEY_LOW);
                    highTemp = dataMap.getString(SunshineWearCommon.KEY_HIGH);

                    mWeatherBitmap = BitmapFactory.decodeResource(getResources(),
                            SunshineWatchFaceUtil.getResourceForWeatherId(weatherId));
                }

            }
        }

        private void invalidateIfNecessary() {
            if (isVisible() && !isInAmbientMode()) {
                invalidate();
            }
        }


        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {

            for (DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    processConfigurationFor(item);
                }
            }
            dataEventBuffer.release();
            invalidateIfNecessary();

        }
    }
}
