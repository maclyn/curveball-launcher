package com.inipage.homelylauncher.dock.items

import android.Manifest
import android.content.Context
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

    private var mContext: Context? = null
    private var mCallback: LoadingCallback? = null
    private var mForecast: CleanedUpWeatherModel? = null

    override fun requestLocationPermission() {
        if (mContext == null) {
            return
        }
        Toast.makeText(
            mContext,
            R.string.grant_location_permission_for_weather,
            Toast.LENGTH_LONG
        ).show()
        val activity = ViewUtils.activityOf(mContext) ?: return
        activity.requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            HomeActivity.REQUEST_LOCATION_PERMISSION
        )
    }

    override fun onWeatherFound(weather: LTSForecastModel) {
        mForecast = CleanedUpWeatherModel.parseFromLTSForecastModel(weather, mContext)
        mCallback?.onLoaded()
        mContext = null
        mCallback = null
    }

    override fun onFetchFailure() {
        mContext = null
        mCallback = null
    }

    override fun startLoading(context: Context, controllerHandle: LoadingCallback): Boolean {
        mContext = context
        mCallback = controllerHandle
        return WeatherController.requestWeather(context, this)
    }

    override fun isActive(context: Context): Boolean {
        return true
    }

    override fun getDrawable(context: Context?): Drawable? {
        val assetId = mForecast?.assetId
        if (context == null || assetId == null) {
            return null;
        }
        return ViewUtils.getDrawableFromAssetPNG(context, assetId)
    }

    override fun getLabel(context: Context): String? {
        return if (mForecast == null) null else mForecast?.currentTemp
    }

    override fun getSecondaryLabel(context: Context): String? {
        return if (mForecast == null) null else
            context.getString(R.string.temp_format_string, mForecast?.low, mForecast?.highTemp)
    }

    override fun getTint(context: Context, tintCallback: TintCallback): Int {
        return context.getColor(R.color.dock_item_weather_color)
    }

    override fun getAction(view: View, context: Context): Runnable {
        return Runnable {
            val bottomSheet = WeatherBottomSheet(context)
            bottomSheet.show()
        }
    }

    override fun getSecondaryAction(
        view: View?,
        context: Context?,
        handle: ItemCallback?
    ): Runnable {
        return Runnable {
            WeatherController.invalidateCache(context);
            WeatherController.requestWeather(context, this);
        }
    }

    override fun getBasePriority(): Long {
        return DockItemPriorities.PRIORITY_WEATHER.priority.toLong()
    }
}