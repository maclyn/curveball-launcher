package com.inipage.homelylauncher.dock.items

import android.content.Context
import com.inipage.homelylauncher.utils.weather.WeatherController
import com.inipage.homelylauncher.utils.weather.model.CleanedUpWeatherModel
import com.inipage.homelylauncher.utils.weather.model.LTSForecastModel

class WeatherBottomSheet(val context: Context) : WeatherController.WeatherPresenter {

    fun show() {
        WeatherController.requestWeather(context, this)
    }
    override fun onWeatherFound(weather: LTSForecastModel?) {
        val parsedModel = CleanedUpWeatherModel.parseFromLTSForecastModel(weather, context)
        // TODO
    }

    override fun requestLocationPermission() = Unit
    override fun onFetchFailure() = Unit
}