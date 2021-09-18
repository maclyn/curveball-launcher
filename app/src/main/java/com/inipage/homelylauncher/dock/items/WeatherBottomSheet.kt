package com.inipage.homelylauncher.dock.items

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.utils.ViewUtils
import com.inipage.homelylauncher.utils.weather.WeatherController
import com.inipage.homelylauncher.utils.weather.model.CleanedUpWeatherModel
import com.inipage.homelylauncher.utils.weather.model.LTSForecastModel
import com.inipage.homelylauncher.views.BottomSheetHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class WeatherBottomSheet(val context: Context) : WeatherController.WeatherPresenter {

    data class Entry(val date: Date, val rawConditions: Pair<String, String>, val temp: Float, val high: Float, val low: Float)

    private val hourFormat = SimpleDateFormat("h:mm aa", Locale.getDefault())
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.getDefault())

    private val conditionMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Pair<String, String>>>>()
    private val temperatureMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Float>>>()
    private val highMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Float>>>()
    private val lowMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Float>>>()

    fun show() {
        WeatherController.requestWeather(context, this)
    }

    override fun onWeatherFound(weather: LTSForecastModel?) {
        val entries = weather?.product?.timeEntries?.sortedWith(compareBy { it.from }) ?: return
        if (entries.isEmpty()) {
            return
        }
        entries
            .filter { it.location?.symbol?.code != null }
            .forEach {
                putFieldInMap(conditionMap, it.from, it.to, Pair(it.location.symbol.id, it.location.symbol.code))
            }
        entries
            .filter { it.from == it.to && it.location.temperature != null }
            .forEach {
                putFieldInMap(temperatureMap, it.from, it.to, it.location.temperature.value)
            }
        entries
            .filter { it.location?.minTemperature != null && it.location?.maxTemperature != null }
            .forEach {
                putFieldInMap(highMap, it.from, it.to, it.location.maxTemperature.value)
                putFieldInMap(lowMap, it.from, it.to, it.location.minTemperature.value)
            }

        // Nearest hourly forecast, and hourly values for the next 12 hours
        val now = Date()
        val workingHourlyCalendar = GregorianCalendar()
        workingHourlyCalendar.time = now
        workingHourlyCalendar.set(Calendar.MILLISECOND, 0)
        workingHourlyCalendar.set(Calendar.SECOND, 0)
        workingHourlyCalendar.set(Calendar.MINUTE, 0)
        var hourOffset = 0
        val hourlyEntries = LinkedList<Entry>()
        var headerEntry: Entry? = null
        while (hourOffset < 13) {
            val temp = getFieldFromMap(temperatureMap, workingHourlyCalendar.time)
            val high = getFieldFromMap(highMap, workingHourlyCalendar.time)
            val low = getFieldFromMap(lowMap, workingHourlyCalendar.time)
            val condition = getFieldFromMap(conditionMap, workingHourlyCalendar.time)
            if (condition != null && temp != null && high != null && low != null) {
                val entry = Entry(workingHourlyCalendar.time, condition, temp, high, low)
                if (workingHourlyCalendar.time <= now) {
                    headerEntry = entry
                } else {
                    hourlyEntries.add(entry)
                }
            }
            hourOffset += 1
            workingHourlyCalendar.add(Calendar.HOUR, 1)
        }

        // Daily values for as many days as we can get at noon
        var dayOffset = 1
        val dailyWorkingDate = GregorianCalendar()
        val dailyEntries = LinkedList<Entry>()
        dailyWorkingDate.time = entries.get(0).from
        dailyWorkingDate.set(Calendar.HOUR_OF_DAY, 12) // Noon
        while (dayOffset < 7) {
            dailyWorkingDate.add(Calendar.DAY_OF_MONTH, 1)
            dayOffset += 1
            val temp = getFieldFromMap(temperatureMap, dailyWorkingDate.time) ?: continue
            val high = getFieldFromMap(highMap, dailyWorkingDate.time) ?: continue
            val low = getFieldFromMap(lowMap, dailyWorkingDate.time) ?: continue
            val condition = getFieldFromMap(conditionMap, dailyWorkingDate.time) ?: continue
            dailyEntries.add(Entry(dailyWorkingDate.time, condition, temp, high, low))
        }

        val bottomSheetRootView =
            LayoutInflater.from(context).inflate(R.layout.weather_bottom_sheet_root_view, null);
        val itemContainer =
            ViewCompat.requireViewById<LinearLayout>(bottomSheetRootView, R.id.weather_item_container);
        if (headerEntry != null) {
            itemContainer.addView(mapEntryToView(headerEntry, itemContainer, "Now", hourFormat))
        }
        hourlyEntries.forEach {
            itemContainer.addView(mapEntryToView(it, itemContainer, null, hourFormat))
        }
        dailyEntries.forEach {
            itemContainer.addView(mapEntryToView(it, itemContainer, null, dayOfWeekFormat))
        }
        val bottomSheetHelper = BottomSheetHelper()
            .setIsFixedHeight()
            .setContentView(bottomSheetRootView)
        bottomSheetHelper.show(context, context.getString(R.string.weather_title))
    }

    override fun requestLocationPermission() = Unit
    override fun onFetchFailure() = Unit

    private fun mapEntryToView(entry: Entry, root: ViewGroup, timeOverride: String?, formatter: SimpleDateFormat): View {
        val itemView = LayoutInflater.from(context).inflate(R.layout.weather_entry_item, root, false)

        // Icon
        ViewCompat.requireViewById<ImageView>(itemView, R.id.weather_item_icon).setImageDrawable(
            ViewUtils.getDrawableFromAssetPNG(context, entry.rawConditions.second))
        // Time
        ViewCompat.requireViewById<TextView>(itemView, R.id.weather_item_time).text =
            timeOverride ?: formatter.format(entry.date)
        // Condition/temp
        ViewCompat.requireViewById<TextView>(itemView, R.id.weather_item_condition_and_temp).text =
            context.getString(
                R.string.weather_format_string,
                CleanedUpWeatherModel.getTempFromValue(entry.temp, context),
                CleanedUpWeatherModel.convertConditionToString(entry.rawConditions.first))
        // High/low
        ViewCompat.requireViewById<TextView>(itemView, R.id.weather_item_high_low).text =
            context.getString(
                R.string.temp_format_string,
                CleanedUpWeatherModel.getTempFromValue(entry.low, context),
                CleanedUpWeatherModel.getTempFromValue(entry.high, context))

        return itemView
    }

    private fun <T: Any> putFieldInMap(
        map: HashMap<Date, LinkedList<Pair<Pair<Date, Date>, T>>>,
        from: Date,
        to: Date,
        value: T) {
        // Working objects
        val toCalendar = GregorianCalendar()
        toCalendar.time = to
        val fromCalendar = GregorianCalendar()
        fromCalendar.time = from

        // Fill in the map from [start to end)
        var hasDoneOneIteration = false
        while (fromCalendar < toCalendar || !hasDoneOneIteration) {
            hasDoneOneIteration = true
            val key = fromCalendar.time
            val mapValue = map[key]
            if (mapValue == null) {
                // First entry for this time window
                val list = LinkedList<Pair<Pair<Date, Date>, T>>()
                val rangePair = Pair(from, to)
                list.add(Pair(rangePair, value))
                map[key] = list
            } else {
                // There's already a value in this time window, so add it to the list
                mapValue.add(Pair(Pair(from, to), value))
                // Sort by duration of the span; we want the shortest possible span first
                mapValue.sortWith(compareBy {
                    it.first.second.time - it.first.first.time
                })
            }
            fromCalendar.add(Calendar.HOUR_OF_DAY, 1)
        }
    }

    private fun <T: Any> getFieldFromMap(
        map: HashMap<Date, LinkedList<Pair<Pair<Date, Date>, T>>>, key: Date): T? {
        val mapValue = map[key] ?: return null
        if (mapValue.isEmpty()) {
            return null
        }
        return mapValue[0].second
    }

    private fun <T: Any> getDateForFieldFromMap(
        map: HashMap<Date, LinkedList<Pair<Pair<Date, Date>, T>>>, key: Date): Date? {
        val mapValue = map[key] ?: return null
        if (mapValue.isEmpty()) {
            return null
        }
        return mapValue[0].first.first
    }
}