package com.inipage.homelylauncher.utils.weather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.inipage.homelylauncher.utils.Constants;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.weather.model.CleanedUpWeatherModel;
import com.inipage.homelylauncher.utils.weather.model.LTSForecastModel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_WEATHER_LOADING;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A wrapper for fetching weather from Met.no, handling some necessary caching logic.
 */
public class WeatherController {

    public interface WeatherPresenter {
        default void requestLocationPermission() {}

        default void onWeatherFound(LTSForecastModel weather) {}

        default void onWeatherFoundFast(CleanedUpWeatherModel model) {}

        default void onFetchFailure() {}
    }

    private static final String TAG = "WeatherController";
    private static final boolean DEBUG_WEATHER = false;
    private static final SimpleDateFormat RFC_1123_PARSER =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

    public static boolean requestWeather(
            Context context, boolean preferFastModel, WeatherPresenter presenter) {
        if (DEBUG_WEATHER) {
            refreshWeather(context, preferFastModel, presenter);
            return false;
        }

        final SharedPreferences reader = PreferenceManager.getDefaultSharedPreferences(context);
        @Nullable String fullJSON =
            reader.getString(Constants.CACHED_WEATHER_RESPONSE_JSON_PREFERENCE, null);
        @Nullable String liteJSON =
            reader.getString(Constants.CACHED_WEATHER_RESPONSE_LITE_JSON_PREFERENCE, null);
        @Nullable String cachedExpiration =
            reader.getString(Constants.CACHED_WEATHER_RESPONSE_EXPIRY_PREFERENCE, null);
        if (cachedExpiration == null || liteJSON == null || fullJSON == null) {
            DebugLogUtils.needle(TAG_WEATHER_LOADING, "No cached weather data; refreshing");
            refreshWeather(context, preferFastModel, presenter);
            return false;
        }
        try {
            @Nullable final Date expiryDate = RFC_1123_PARSER.parse(cachedExpiration);
            if (expiryDate != null && expiryDate.getTime() > System.currentTimeMillis()) {
                DebugLogUtils.needle(TAG_WEATHER_LOADING, "Displaying cached weather...");
                if (preferFastModel) {
                    final CleanedUpWeatherModel cleanedUpWeatherModel =
                        CleanedUpWeatherModel.deserialize(liteJSON);
                    presenter.onWeatherFoundFast(cleanedUpWeatherModel);
                } else {
                    final LTSForecastModel weatherResponse = LTSForecastModel.deserialize(fullJSON);
                    presenter.onWeatherFound(weatherResponse);
                }
                return true;
            }
        } catch (Exception ignored) {}

        DebugLogUtils.needle(TAG_WEATHER_LOADING, "Weather data is stale or wrong; refreshing");
        refreshWeather(context, preferFastModel, presenter);
        return false;
    }

    public static void invalidateCache(@Nullable Context context) {
        if (context == null) return;
        final SharedPreferences.Editor writer =
            PreferenceManager.getDefaultSharedPreferences(context).edit();
        writer.remove(Constants.CACHED_WEATHER_RESPONSE_JSON_PREFERENCE);
        writer.remove(Constants.CACHED_WEATHER_RESPONSE_LITE_JSON_PREFERENCE);
        writer.remove(Constants.CACHED_WEATHER_RESPONSE_EXPIRY_PREFERENCE);
        writer.apply();
    }

    private static void refreshWeather(
            final Context context,
            final boolean preferFastWeather,
            final WeatherPresenter presenter) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            presenter.requestLocationPermission();
            presenter.onFetchFailure();
            return;
        }

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setAltitudeRequired(false);
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        criteria.setAccuracy(Criteria.NO_REQUIREMENT);
        @Nullable String provider = lm.getBestProvider(criteria, true);
        if (provider == null) {
            presenter.onFetchFailure();
            return;
        }

        Location lastLocation = lm.getLastKnownLocation(provider);
        if (lastLocation != null) {
            fetchWeatherForLocation(context, preferFastWeather, presenter, lastLocation);
        } else {
            lm.requestSingleUpdate(criteria, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    fetchWeatherForLocation(context, preferFastWeather, presenter, location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            }, Looper.getMainLooper());
        }
    }

    private static void fetchWeatherForLocation(
        final Context context,
        final boolean preferFastWeather,
        final WeatherPresenter presenter,
        Location location)
    {
        if (location != null) {
            DebugLogUtils.needle(
                TAG_WEATHER_LOADING,
                location.getLatitude() + "; " + location.getLongitude());
        } else {
            return;
        }

        // Round to 4 places to avoid wasteful API requests
        double lat = BigDecimal.valueOf(location.getLatitude()).setScale(4, RoundingMode.DOWN).doubleValue();
        double lon = BigDecimal.valueOf(location.getLongitude()).setScale(4, RoundingMode.DOWN).doubleValue();
        WeatherApiFactory
            .getInstance()
            .getWeatherLTS(lat, lon)
            .enqueue(new Callback<LTSForecastModel>() {
            @Override
            public void onResponse(
                @NotNull Call<LTSForecastModel> call,
                @NotNull Response<LTSForecastModel> response) {
                DebugLogUtils.needle(TAG_WEATHER_LOADING, "Got weather response!");
                try {
                    @Nullable final LTSForecastModel model = response.body();
                    if (model == null) {
                        DebugLogUtils.needle(TAG_WEATHER_LOADING, "Null response");
                        presenter.onFetchFailure();
                        return;
                    }
                    @Nullable final CleanedUpWeatherModel cleanedUpWeatherModel =
                        CleanedUpWeatherModel.parseFromLTSForecastModel(model, context);
                    if (preferFastWeather) {
                        presenter.onWeatherFoundFast(cleanedUpWeatherModel);
                    } else {
                        presenter.onWeatherFound(model);
                    }

                    // Try and cache the response for later use
                    final List<String> expiryHeaders = response.headers().values("Expires");
                    if (expiryHeaders.size() > 0) {
                        final SharedPreferences.Editor writer =
                            PreferenceManager.getDefaultSharedPreferences(context).edit();
                        writer.putString(
                            Constants.CACHED_WEATHER_RESPONSE_JSON_PREFERENCE,
                            model.serialize());
                        writer.putString(
                            Constants.CACHED_WEATHER_RESPONSE_LITE_JSON_PREFERENCE,
                            cleanedUpWeatherModel.serialize());
                        writer.putString(
                            Constants.CACHED_WEATHER_RESPONSE_EXPIRY_PREFERENCE,
                            expiryHeaders.get(0));
                        writer.apply();
                    }
                } catch (Exception ignored) {
                    DebugLogUtils.needle(TAG_WEATHER_LOADING, response.raw().toString());
                    presenter.onFetchFailure();
                }
            }

            @Override
            public void onFailure(@NotNull Call<LTSForecastModel> call, @NotNull Throwable t) {
                Log.e(TAG, "Failure fetching weather", t);
                presenter.onFetchFailure();
            }
        });
    }
}
