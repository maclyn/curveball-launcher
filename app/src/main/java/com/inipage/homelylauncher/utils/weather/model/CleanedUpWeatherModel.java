package com.inipage.homelylauncher.utils.weather.model;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.Constants;

import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * It's a pain to get displayable data from the {@linkplain LTSForecastModel}; this class takes one
 * of those and cleans it up to give you easily displayable data.
 */
public class CleanedUpWeatherModel {
    public static final String TAG = "CleanedUpWeatherModel";
    private final String condition;
    private final String temp;
    private final String high;
    private final String low;
    int resourceId;

    public CleanedUpWeatherModel(
        int resourceId, String temp, String condition, String high, String low) {
        this.resourceId = resourceId;
        this.condition = condition;
        this.temp = temp;
        this.high = high;
        this.low = low;
    }

    public static CleanedUpWeatherModel parseFromLTSForecastModel(
        LTSForecastModel model, Context context) {
        Pair<Date, LocationModel> conditionEntry = null;
        Pair<Date, LocationModel> temperatureEntry = null;
        Pair<Date, LocationModel> rangeEntry = null;

        GregorianCalendar cal = new GregorianCalendar();
        cal.set(Calendar.YEAR, 2100);
        Date earliestTime = cal.getTime();

        Collections.sort(
            model.getProduct().getTimeEntries(), (t1, t2) -> t1.getFrom().compareTo(t2.getFrom()));
        for (TimeModel forecast : model.getProduct().getTimeEntries()) {
            LocationModel l = forecast.getLocation();
            if (l != null) {
                if (l.getMaxTemperature() != null && l.getMinTemperature() != null) {
                    if (rangeEntry == null ||
                        rangeEntry.first.getTime() > forecast.getFrom().getTime()) {
                        rangeEntry = new Pair<>(forecast.getFrom(), l);
                    }
                } else if (l.getTemperature() != null) {
                    if (temperatureEntry == null ||
                        temperatureEntry.first.getTime() > forecast.getFrom().getTime()) {
                        temperatureEntry = new Pair<>(forecast.getFrom(), l);
                    }
                } else if (l.getSymbol() != null) {
                    if (conditionEntry == null ||
                        conditionEntry.first.getTime() > forecast.getFrom().getTime()) {
                        conditionEntry = new Pair<>(forecast.getFrom(), l);
                    }
                }
            }
        }

        // logic should be
        // if date (day) is the same
        //  if high is higher, that's the max
        //  if low is lower, that's the min
        // find closest time; use that for condition & temp

        String condition = "";
        int conditionId = -1;
        if (conditionEntry != null) {
            conditionId = convertConditionToId(conditionEntry.second.getSymbol().getId());
            condition = convertConditionToString(conditionEntry.second.getSymbol().getId());
        }
        String temperatureValue = null;
        if (temperatureEntry != null) {
            temperatureValue = getTempFromValue(
                temperatureEntry.second.getTemperature().getValue(),
                context);
        }
        String highValue = null;
        if (rangeEntry != null) {
            highValue = getTempFromValue(rangeEntry.second.getMaxTemperature().getValue(), context);
        }
        String lowValue = null;
        if (rangeEntry != null) {
            lowValue = getTempFromValue(rangeEntry.second.getMinTemperature().getValue(), context);
        }

        return new CleanedUpWeatherModel(
            conditionId, temperatureValue, condition, highValue, lowValue);
    }

    private static int convertConditionToId(String condition) {
        if (condition == null) {
            return -1;
        }

        condition = condition.replace("Dark_", "");
        switch (condition) {
            case "Sun":
                return R.drawable.clima_sun;
            case "PartlyCloud":
            case "LightCloud":
                return R.drawable.clima_cloud_sun;
            case "Cloud":
                return R.drawable.clima_cloud;
            case "Drizzle":
            case "LightRain":
                return R.drawable.clima_cloud_drizzle;
            case "LightRainSun":
            case "RainSun":
            case "DrizzleSun":
                return R.drawable.clima_cloud_rain_sun;
            case "Rain":
                return R.drawable.clima_cloud_rain;
            case "LightRainThunder":
            case "RainThunder":
            case "RainThunderSun":
            case "DrizzleThunder":
            case "LightRainThunderSun":
            case "DrizzleThunderSun":
                return R.drawable.clima_cloud_lightning;
            case "Fog":
                return R.drawable.clima_cloud_fog;
            case "Sleet":
            case "SleetThunder":
            case "SleetSun":
            case "SleetSunThunder":
            case "SnowSun":
            case "LightSleetThunderSun":
            case "LightSleetSun":
            case "HeavySleetSun":
            case "HeavySleetThunderSun":
            case "LightSleetThunder":
            case "HeavySleetThunder":
            case "Snow":
            case "LightSleet":
            case "HeavySleet":
            case "SnowThunder":
            case "SnowSunThunder":
            case "LightSnowSun":
            case "LightSnowThunder":
            case "HeavySnowThunder":
                return R.drawable.clima_cloud_snow;
            case "HeavySnow":
            case "HeavysnowSun":
            case "LightSnowThunderSun":
            case "HeavySnowThunderSun":
                return R.drawable.clima_cloud_snow_alt;
        }
        return R.drawable.clima_umbrella;
    }

    private static String convertConditionToString(@Nullable String condition) {
        if (condition == null) {
            return "Unknown";
        }

        condition = condition.replace("Dark_", "");
        switch (condition) {
            case "Sun":
                return "sunny";
            case "PartlyCloud":
                return "partly cloudy";
            case "LightCloud":
            case "Cloud":
                return "cloudy";
            case "Drizzle":
            case "LightRain":
                return "drizzle";
            case "LightRainSun":
            case "RainSun":
            case "DrizzleSun":
                return "sun showers";
            case "Rain":
                return "rainy";
            case "LightRainThunder":
            case "RainThunder":
            case "RainThunderSun":
            case "DrizzleThunder":
            case "LightRainThunderSun":
            case "DrizzleThunderSun":
                return "thunderstorms";
            case "Fog":
                return "foggy";
            case "Sleet":
            case "SleetThunder":
            case "SleetSun":
            case "SleetSunThunder":
            case "LightSleetThunderSun":
            case "LightSleetSun":
            case "HeavySleetSun":
            case "HeavySleetThunderSun":
            case "LightSleetThunder":
            case "HeavySleetThunder":
            case "LightSleet":
            case "HeavySleet":
                return "sleet";
            case "LightSnowThunderSun":
            case "HeavySnowThunderSun":
            case "LightSnowSun":
            case "LightSnowThunder":
            case "HeavySnowThunder":
            case "SnowSun":
            case "Snow":
            case "SnowThunder":
            case "SnowSunThunder":
                return "snowy";
            case "HeavySnow":
            case "HeavysnowSun":
                return "blizzard";
        }
        return "Rainy";
    }

    private static String getTempFromValue(float temp, Context context) {
        if (isUsingCelsius(context)) {
            return Math.round(temp) + "°";
        } else {
            temp = (temp * 9 / 5) + 32;
            return Math.round(temp) + "°";
        }
    }

    private static boolean isUsingCelsius(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(Constants.WEATHER_USE_CELCIUS_PREF, false);
    }

    public int getResourceId() {
        return resourceId;
    }

    public String getTemp() {
        return temp;
    }

    public String getHigh() {
        return high;
    }

    public String getLow() {
        return low;
    }

    public String getCondition() {
        return condition;
    }
}
