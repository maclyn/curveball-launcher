package com.inipage.homelylauncher.dock.items

import android.Manifest
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

class WeatherDockItem : DockControllerItem(), WeatherPresenter {

    private var mForecast: CleanedUpWeatherModel? = null

    override fun onAttach() {
        val context = context ?: return
        WeatherController.requestWeather(context, true,this)
    }

    override fun requestLocationPermission() {
        context ?: return
        Toast.makeText(
            context,
            R.string.grant_location_permission_for_weather,
            Toast.LENGTH_LONG
        ).show()
        val activity = ViewUtils.requireActivityOf(context) ?: return
        activity.requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            HomeActivity.REQUEST_LOCATION_PERMISSION
        )
    }
    
    override fun onWeatherFoundFast(model: CleanedUpWeatherModel?) {
        model ?: return
        mForecast = model
        showSelf()
    }

    override fun getDrawable(): Drawable? {
        val assetId = mForecast?.assetId ?: return null
        val context = context ?: return null
        return ViewUtils.getDrawableFromAssetPNG(context, assetId)
    }

    override fun getLabel(): String? {
        return if (mForecast == null) null else mForecast?.currentTemp
    }

    override fun getSecondaryLabel(): String? {
        val context = context ?: return null
        return if (mForecast == null) null else
            context.getString(R.string.temp_format_string, mForecast?.low, mForecast?.highTemp)
    }

    override fun getTint(): Int {
        val context = context ?: return super.getTint()
        return context.getColor(R.color.dock_item_weather_color)
    }

    override fun getAction(view: View): Runnable {
        val context = context ?: return Runnable {}
        return Runnable {
            val bottomSheet = WeatherBottomSheet(context)
            bottomSheet.show()
        }
    }

    override fun getSecondaryAction(
        view: View?
    ): Runnable {
        val context = context ?: return Runnable {}
        return Runnable {
            WeatherController.invalidateCache(context);
            WeatherController.requestWeather(context, true,this);
        }
    }

    override fun getBasePriority(): Long = DockItemPriorities.PRIORITY_WEATHER.priority.toLong()
}