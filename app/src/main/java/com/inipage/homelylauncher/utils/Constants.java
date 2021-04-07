package com.inipage.homelylauncher.utils;

import com.google.gson.Gson;

public class Constants {

    public static final String CACHED_WEATHER_RESPONSE_JSON_PREFERENCE = "cached_weather_response_json_pref";
    public static final String CACHED_WEATHER_RESPONSE_TIME_PREFERENCE = "cached_weather_response_time_pref";
    public static final String WEATHER_USE_CELCIUS_PREF = "celsius_pref";

    public static final String DISABLED_CALENDARS_PREF = "disabled_calendars_pref";

    public static final String PACKAGE = "com.inipage.homelylauncher";
    public static final String DEFAULT_FOLDER_ICON = "ic_folder_white_48dp";

    public static final Gson DEFAULT_GSON = new Gson();

    public static final boolean DEBUG_RENDER = false;

    // More than this, and widget start to freak out
    public static final int DEFAULT_COLUMN_COUNT = 5;
    public static final int DEFAULT_MAX_ROW_COUNT = 6;
    // Each cell of the homescreen isn't actually square -- we have to apply this factor,
    // or widgets aren't going to fit right...
    public static final float WIDTH_TO_HEIGHT_SCALAR = 1.5F;
}
