package com.inipage.homelylauncher.dock.items

import android.view.View
import com.inipage.homelylauncher.dock.DockControllerItem
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.persistence.PrefsHelper
import com.inipage.homelylauncher.dock.DockItemPriorities
import com.inipage.homelylauncher.utils.CalendarUtils
import java.text.SimpleDateFormat
import java.util.*

class CalendarMappedDockItem : DockControllerItem() {

    private val eventDateFormatter = SimpleDateFormat("h:mm aa", Locale.getDefault())

    private var event: CalendarUtils.Event? = null

    override fun onAttach() {
        val context = context
        val event = CalendarUtils.findRelevantEvent(context)
        if (event != null && event.allDay && event.start - event.end > ONE_DAY_MS) {
            return
        }
        this.event = event?.also { showSelf() }
    }

    override fun getIcon(): Int {
        return R.drawable.dock_icon_event
    }

    override fun getLabel(): String? = event?.title

    override fun getSecondaryLabel(): String? {
        val event = event ?: return null
        val context = context ?: return null
        return if (event.allDay)
            null
        else {
            val start = GregorianCalendar().also {
                it.time = Date(event.start)
            }
            val end = GregorianCalendar().also {
                it.time = Date(event.end)
            }
            val now = GregorianCalendar()
            if (now.before(start)) {
                return start.formatTime()
            }
            return context.getString(R.string.ends_at, end.formatTime())
        }
    }

    override fun getTint(): Int {
        val context = context ?: return super.getTint()
        return context.getColor(R.color.dock_item_calendar_color)
    }

    override fun getAction(view: View): Runnable {
        context ?: return Runnable {}
        return Runnable {
            CalendarUtils.launchEvent(
                context,
                if (event == null) -1 else event!!.id
            )
        }
    }

    override fun getSecondaryAction(view: View): Runnable {
        context ?: return Runnable {}
        return Runnable {
            HiddenCalendarsPickerBottomSheet.show(context) {
                val event = event ?: return@show
                if (PrefsHelper.get().disabledCalendars.containsKey(event.calendarId)) {
                    hideSelf()
                }
            }
        }
    }

    override fun getBasePriority(): Long {
        return if (event != null && event!!.allDay)
            DockItemPriorities.PRIORITY_EVENT_ALL_DAY.priority.toLong()
        else
            DockItemPriorities.PRIORITY_EVENT_RANGED.priority.toLong()
    }

    private fun Calendar.formatTime(): String = eventDateFormatter.format(this.time)

    companion object {
        private const val ONE_DAY_MS = (1000 * 60 * 60 * 24).toLong()
    }
}