package com.oroblesr.sunshinewatchface;

import static android.graphics.Color.parseColor;

/**
 * Created by Oscar on 30/09/2016.
 * Based on WatchFaceSample
 */

public class SunshineWatchFaceUtil {

    public int getResourceForWeatherId(int weatherId) {
        final int CLEAR = 0;
        final int CLOUDS = 1;
        final int FOG = 2;
        final int LIGHT_CLOUDS = 3;
        final int LIGHT_RAIN = 4;
        final int RAIN = 5;
        final int SNOW = 6;
        final int STORM = 7;

        int resourceToGet;
        int resId;

        if (weatherId >= 200 && weatherId <= 232) resourceToGet = STORM;
        else if (weatherId >= 300 && weatherId <= 321) resourceToGet = LIGHT_RAIN;
        else if (weatherId >= 500 && weatherId <= 504) resourceToGet = RAIN;
        else if (weatherId == 511) resourceToGet = SNOW;
        else if (weatherId >= 520 && weatherId <= 531) resourceToGet = RAIN;
        else if (weatherId >= 600 && weatherId <= 622) resourceToGet = SNOW;
        else if (weatherId >= 701 && weatherId < 761) resourceToGet = FOG;
        else if (weatherId >= 761 || weatherId <= 781) resourceToGet = STORM;
        else if (weatherId == 800) resourceToGet = CLEAR;
        else if (weatherId == 801) resourceToGet = LIGHT_CLOUDS;
        else if (weatherId >= 802 && weatherId <= 804) resourceToGet = CLOUDS;
        else resourceToGet = -1;

        switch (resourceToGet) {
            case CLEAR: resId = R.drawable.art_clear; break;
            case CLOUDS: resId = R.drawable.art_clouds; break;
            case FOG: resId = R.drawable.art_fog; break;
            case LIGHT_CLOUDS: resId = R.drawable.art_light_clouds; break;
            case LIGHT_RAIN: resId = R.drawable.art_light_rain; break;
            case RAIN: resId = R.drawable.art_rain; break;
            case SNOW: resId = R.drawable.art_snow; break;
            case STORM: resId = R.drawable.art_storm; break;
            //TODO add error resource
            default: resId = -1;
        }

        return resId;
    }


}
