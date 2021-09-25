package com.inipage.homelylauncher.dock.items

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Toast
import com.inipage.homelylauncher.HomeActivity
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.dock.DockControllerItem
import com.inipage.homelylauncher.dock.DockItemPriorities
import com.inipage.homelylauncher.utils.ViewUtils
import com.inipage.homelylauncher.utils.weather.WeatherController
import com.inipage.homelylauncher.utils.weather.WeatherController.WeatherPresenter
import com.inipage.homelylauncher.utils.weather.model.CleanedUpWeatherModel
import com.inipage.homelylauncher.utils.weather.model.LTSForecastModel

class WeatherDockItem : DockControllerItem(), WeatherPresenter {

    private var mForecast: CleanedUpWeatherModel? = null

    override fun onAttach() {
        val context = context ?: return
        WeatherController.requestWeather(context, this)
    }

    override fun requestLocationPermission() {
        val context = mHost?.context ?: return
        Toast.makeText(
            context,
            R.string.grant_location_permission_for_weather,
            Toast.LENGTH_LONG
        ).show()
        val activity = ViewUtils.activityOf(context) ?: return
        activity.requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            HomeActivity.REQUEST_LOCATION_PERMISSION
        )
    }

    override fun onWeatherFound(weather: LTSForecastModel) {
        val context = mHost?.context ?: return
        mForecast = CleanedUpWeatherModel.parseFromLTSForecastModel(weather, context)
        mHost?.showHostedItem()
    }

    override fun onFetchFailure() = Unit

    override fun getDrawable(): Drawable? {
        val assetId = mForecast?.assetId ?: return null
        val context = mHost?.context ?: return null
        return ViewUtils.getDrawableFromAssetPNG(context, assetId)
    }

    override fun getLabel(): String? {
        return if (mForecast == null) null else mForecast?.currentTemp
    }

    override fun getSecondaryLabel(): String? {
        val context = mHost?.context ?: return null
        return if (mForecast == null) null else
            context.getString(R.string.temp_format_string, mForecast?.low, mForecast?.highTemp)
    }

    override fun getTint(): Int {
        val context = mHost?.context ?: return super.getTint()
        return context.getColor(R.color.dock_item_weather_color)
    }

    override fun getAction(view: View): Runnable {
        val context = mHost?.context ?: return Runnable {}
        return Runnable {
            val bottomSheet = WeatherBottomSheet(context)
            bottomSheet.show()
        }
    }

    override fun getSecondaryAction(
        view: View?
    ): Runnable {
        val context = mHost?.context ?: return Runnable {}
        return Runnable {
            WeatherController.invalidateCache(context);
            WeatherController.requestWeather(context, this);
        }
    }

    override fun getBasePriority(): Long {
        return DockItemPriorities.PRIORITY_WEATHER.priority.toLong()
    }
}