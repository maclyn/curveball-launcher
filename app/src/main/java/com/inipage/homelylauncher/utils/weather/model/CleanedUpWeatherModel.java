package com.inipage.homelylauncher.utils.weather.model;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.inipage.homelylauncher.utils.Constants;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * It's a pain to get displayable data from the {@linkplain LTSForecastModel}; this class takes one
 * of those and cleans it up to give you easily displayable data.
 */
public class CleanedUpWeatherModel {

    public class HourlyModel {

    }

    public class DailyModel {

    }

    // Values representing the current state
    private final String mCondition;
    private final String mCurrentTemp;
    private final String mHighTemp;
    private final String mLowTemp;
    private final String mConditionAssetId;

    // Values representing an hourly forecast
    private final List<HourlyModel> mHourlyModels;

    // Values representing a daily forecast
    private final List<DailyModel> mDailyModels;

    public CleanedUpWeatherModel(
        String resourceId,
        String currentTemp,
        String condition,
        String highTemp,
        String lowTemp,
        List<HourlyModel> hourlyModels,
        List<DailyModel> dailyModels) {
        this.mConditionAssetId = resourceId;
        this.mCondition = condition;
        this.mCurrentTemp = currentTemp;
        this.mHighTemp = highTemp;
        this.mLowTemp = lowTemp;
        mHourlyModels = hourlyModels;
        mDailyModels = dailyModels;
    }

    public static CleanedUpWeatherModel parseFromLTSForecastModel(
        LTSForecastModel model, Context context) {
        Pair<Date, LocationModel> conditionEntry = null;
        Pair<Date, LocationModel> temperatureEntry = null;
        Pair<Date, LocationModel> rangeEntry = null;

        model.getProduct().getTimeEntries().sort(Comparator.comparing(TimeModel::getFrom));
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
        String conditionId = "";
        if (conditionEntry != null) {
            conditionId = conditionEntry.second.getSymbol().getCode();
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
            conditionId, temperatureValue, condition, highValue, lowValue, new ArrayList<>(),
            new ArrayList<>());
    }

    private static String convertConditionToString(@Nullable String condition) {
        if (condition == null) {
            return "Unknown";
        }

        condition = condition.replace("Dark_", "");
        switch (condition) {
            case "Sun":
                return "Sunny";
            case "PartlyCloud":
                return "Partly Cloudy";
            case "LightCloud":
            case "Cloud":
                return "Cloudy";
            case "Drizzle":
            case "LightRain":
                return "Drizzle";
            case "LightRainSun":
            case "RainSun":
            case "DrizzleSun":
                return "Sun Showers";
            case "Rain":
                return "Rainy";
            case "LightRainThunder":
            case "RainThunder":
            case "RainThunderSun":
            case "DrizzleThunder":
            case "LightRainThunderSun":
            case "DrizzleThunderSun":
                return "Thunderstorms";
            case "Fog":
                return "Foggy";
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
                return "Sleet";
            case "LightSnowThunderSun":
            case "HeavySnowThunderSun":
            case "LightSnowSun":
            case "LightSnowThunder":
            case "HeavySnowThunder":
            case "SnowSun":
            case "Snow":
            case "SnowThunder":
            case "SnowSunThunder":
                return "Snowy";
            case "HeavySnow":
            case "HeavysnowSun":
                return "Blizzard";
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

    public String getAssetId() {
        return mConditionAssetId;
    }

    public String getCurrentTemp() {
        return mCurrentTemp;
    }

    public String getHighTemp() {
        return mHighTemp;
    }

    public String getLow() {
        return mLowTemp;
    }

    public String getCondition() {
        return mCondition;
    }
}
