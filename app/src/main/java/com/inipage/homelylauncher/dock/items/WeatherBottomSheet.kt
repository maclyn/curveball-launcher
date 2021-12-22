package com.inipage.homelylauncher.dock.items

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import kotlin.math.roundToInt

class WeatherBottomSheet(val context: Context) : WeatherController.WeatherPresenter {

    data class Entry(
        val date: Date,
        val rawConditions: Pair<String, String>,
        val temp: Float?,
        val high: Float,
        val low: Float,
        val precipitation: Float?)

    // One week is the most we try to fill with putFieldInMap()
    private val maxDistanceBetweenDates = 1000 * 60 * 60 * 24 * 7

    private val hourFormat = SimpleDateFormat("h:mm aa", Locale.getDefault())
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.getDefault())

    private val conditionMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Pair<String, String>>>>()
    private val precipitationMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Float>>>()
    private val temperatureMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Float>>>()
    private val highMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Float>>>()
    private val lowMap = HashMap<Date, LinkedList<Pair<Pair<Date, Date>, Float>>>()

    fun show() {
        WeatherController.requestWeather(context, false,this)
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
            .filter { it.location?.temperature != null }
            .forEach {
                putFieldInMap(temperatureMap, it.from, it.to, it.location.temperature.value)
            }
        entries
            .filter { it.location?.precipitation != null }
            .forEach {
                putFieldInMap(precipitationMap, it.from, it.to, it.location.precipitation.value)
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
            val precipitation = getFieldFromMap(precipitationMap, workingHourlyCalendar.time)
            if (condition != null && temp != null && high != null && low != null) {
                val entry = Entry(workingHourlyCalendar.time, condition, temp, high, low, precipitation)
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
            val temp = getFieldFromMap(temperatureMap, dailyWorkingDate.time)
            val precipitation = getFieldFromMapMostGeneral(precipitationMap, dailyWorkingDate.time)
            val high = getFieldFromMap(highMap, dailyWorkingDate.time) ?: continue
            val low = getFieldFromMap(lowMap, dailyWorkingDate.time) ?: continue
            val condition = getFieldFromMap(conditionMap, dailyWorkingDate.time) ?: continue
            dailyEntries.add(Entry(dailyWorkingDate.time, condition, temp, high, low, precipitation))
        }

        val bottomSheetRootView =
            LayoutInflater.from(context).inflate(R.layout.weather_bottom_sheet_root_view, null);
        val nowContainer =
            ViewCompat.requireViewById<FrameLayout>(bottomSheetRootView, R.id.now_container);
        val hourlyContainer =
            ViewCompat.requireViewById<LinearLayout>(bottomSheetRootView, R.id.hourly_container);
        val dailyContainer =
            ViewCompat.requireViewById<LinearLayout>(bottomSheetRootView, R.id.days_container);
        if (headerEntry != null) {
            nowContainer.addView(mapEntryToView(headerEntry, nowContainer, "Now"))
        }
        val padding =
            context.resources.getDimension(R.dimen.weather_bottom_sheet_horizontal_padding) * 1.25
        hourlyContainer.addView(ViewUtils.createFillerWidthView(context, padding.toInt()))
        hourlyEntries.forEach {
            hourlyContainer.addView(mapHourlyEntryToView(it, hourlyContainer))
        }
        hourlyContainer.addView(ViewUtils.createFillerWidthView(context, padding.toInt()))
        dailyEntries.forEach {
            dailyContainer.addView(mapEntryToView(it, dailyContainer, null))
        }
        val bottomSheetHelper = BottomSheetHelper()
            .setIsFixedHeight()
            .setContentView(bottomSheetRootView)
        bottomSheetHelper.show(context, context.getString(R.string.weather_title))
    }

    private fun mapHourlyEntryToView(entry: Entry, root: ViewGroup): View {
        val showingDepth = shouldShowDepthValue(entry.precipitation)
        val itemView = LayoutInflater.from(context).inflate(
            if (showingDepth)
                R.layout.weather_entry_hourly_item
            else
                R.layout.weather_entry_hourly_item_condensed,
            root,
            false)

        // Icon
        ViewCompat.requireViewById<ImageView>(itemView, R.id.weather_hourly_item_icon).setImageDrawable(
            ViewUtils.getDrawableFromAssetPNG(context, entry.rawConditions.second))
        // Time
        ViewCompat.requireViewById<TextView>(itemView, R.id.weather_hourly_item_time).text = hourFormat.format(entry.date)
        // Temp.
        val tempTv = ViewCompat.requireViewById<TextView>(itemView, R.id.weather_hourly_item_temp)
        tempTv.text = CleanedUpWeatherModel.getTempFromValue(entry.temp ?: 0F, context)
        // Rain, maybe
        if (showingDepth) {
            val precipitationTv =
                ViewCompat.requireViewById<TextView>(itemView, R.id.weather_hourly_item_precip)
            precipitationTv.text =
                CleanedUpWeatherModel.getPrecipitationFromValue(entry.precipitation ?: 0F, context)
        }

        return itemView
    }

    private fun mapEntryToView(entry: Entry, root: ViewGroup, timeOverride: String?): View {
        val itemView = LayoutInflater.from(context).inflate(R.layout.weather_entry_item, root, false)

        // Icon
        ViewCompat.requireViewById<ImageView>(itemView, R.id.weather_item_icon).setImageDrawable(
            ViewUtils.getDrawableFromAssetPNG(context, entry.rawConditions.second))
        // Time
        ViewCompat.requireViewById<TextView>(itemView, R.id.weather_item_time).text =
            timeOverride ?: dayOfWeekFormat.format(entry.date)
        // Condition/temp
        val condition = entry.rawConditions.first
        val conditionTv = ViewCompat.requireViewById<TextView>(itemView, R.id.weather_item_condition_and_temp)
        conditionTv.text =
            context.getString(
                R.string.weather_format_string,
                CleanedUpWeatherModel.getTempFromValue(entry.temp ?: 0F, context),
                CleanedUpWeatherModel.convertConditionToString(entry.rawConditions.first))
        if (entry.temp == null) {
            conditionTv.text =
                CleanedUpWeatherModel.convertConditionToString(entry.rawConditions.first)
        }
        // High/low
        ViewCompat.requireViewById<TextView>(itemView, R.id.weather_item_high_low).text =
            context.getString(
                R.string.temp_format_string,
                CleanedUpWeatherModel.getTempFromValue(entry.low, context),
                CleanedUpWeatherModel.getTempFromValue(entry.high, context))
        // Rain, maybe
        val precipitationTv = ViewCompat.requireViewById<TextView>(itemView, R.id.weather_item_precip)
        val precipitationString =
            CleanedUpWeatherModel.getPrecipitationFromValue(entry.precipitation ?: 0F, context)
        val stringRes =
            when {
                condition.contains("Snow") -> R.string.amount_of_snow
                condition.contains("Sleet") -> R.string.amount_of_sleet
                else -> R.string.amount_of_rain
            }
        precipitationTv.text = context.getString(stringRes, precipitationString)
        precipitationTv.visibility =
            if (shouldShowDepthValue(entry.precipitation)) View.VISIBLE else View.GONE

        return itemView
    }

    private fun <T: Any> putFieldInMap(
        map: HashMap<Date, LinkedList<Pair<Pair<Date, Date>, T>>>,
        from: Date,
        to: Date,
        value: T) {
        if (from == to || from.after(to)) {
            return
        }
        if (to.time - from.time > maxDistanceBetweenDates) {
            return
        }


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

    private fun <T: Any> getFieldFromMapMostGeneral(
        map: HashMap<Date, LinkedList<Pair<Pair<Date, Date>, T>>>, key: Date): T? {
        val mapValue = map[key] ?: return null
        if (mapValue.isEmpty()) {
            return null
        }
        return mapValue[mapValue.size - 1].second
    }

    private fun shouldShowDepthValue(value: Float?): Boolean {
        if (value == null) return false
        val usingCelcius = CleanedUpWeatherModel.isUsingCelsius(context)
        if (usingCelcius) {
            return value >= 0.1F
        }
        return value * 0.03937F >= 0.01F
    }
}