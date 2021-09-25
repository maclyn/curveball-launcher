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
        var event = CalendarUtils.findRelevantEvent(context)
        if (event != null && event.allDay && event.start - event.end > ONE_DAY_MS) {
            event = null
        }
        this.event = event
    }

    override fun getIcon(): Int {
        return R.drawable.dock_icon_event
    }

    override fun getLabel(): String? {
        return if (event == null) null else event!!.title
    }

    override fun getSecondaryLabel(): String? {
        val event = event ?: return null
        val context = context ?: return null
        return if (event.allDay)
            context.getString(R.string.all_day)
        else
            eventDateFormatter.format(Date(event.start))
    }

    override fun getTint(): Int {
        val context = context ?: return super.getTint()
        return context.getColor(R.color.dock_item_calendar_color)
    }

    override fun getAction(view: View): Runnable {
        val host = mHost ?: return Runnable {}
        val context = host.context ?: return Runnable {}
        return Runnable {
            CalendarUtils.launchEvent(
                context,
                if (event == null) -1 else event!!.id
            )
        }
    }

    override fun getSecondaryAction(view: View): Runnable {
        val host = mHost ?: return Runnable {}
        val context = context ?: return Runnable {}
        return Runnable {
            HiddenCalendarsPickerBottomSheet.show(context) {
                if (
                    event != null &&
                    PrefsHelper.getDisabledCalendars(context).containsKey(event!!.calendarId)
                ) {
                    host.hideHostedItem()
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

    companion object {
        private const val ONE_DAY_MS = (1000 * 60 * 60 * 24).toLong()
    }
}