package com.inipage.homelylauncher.utils.weather;

import com.inipage.homelylauncher.utils.weather.model.LTSForecastModel;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

public interface WeatherApiInterface {
    @Headers({
        "User-Agent: Curveball-launcher 0.3 https://github.com/maclyn/curveball-launcher"
    })
    @GET("locationforecast/2.0/classic")
    Call<LTSForecastModel> getWeatherLTS(@Query("lat") double lat, @Query("lon") double lon);
}
