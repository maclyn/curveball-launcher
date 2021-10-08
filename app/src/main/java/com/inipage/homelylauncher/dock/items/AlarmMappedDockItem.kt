package com.inipage.homelylauncher.dock.items

import com.inipage.homelylauncher.dock.DockControllerItem
import android.app.PendingIntent
import com.inipage.homelylauncher.utils.AlarmUtils
import com.inipage.homelylauncher.R
import android.app.PendingIntent.CanceledException
import android.view.View
import com.inipage.homelylauncher.dock.DockItemPriorities
import kotlin.math.abs

/**
 * Renders an alarm in the dock.
 */
class AlarmMappedDockItem : DockControllerItem() {

    private var alarmHandle: AlarmUtils.AlarmHandle? = null

    override fun onAttach() {
        context ?: return
        alarmHandle = AlarmUtils.getAlarmHandle(context)
        if (alarmHandle?.hasAlarm() == true) {
            showSelf()
        }
    }

    override fun getIcon(): Int {
        return R.drawable.dock_icon_alarm
    }

    override fun getLabel(): String? {
        return alarmHandle?.nextAlarmTime
    }

    override fun getSecondaryLabel(): String? {
        return alarmHandle?.nextAlarmTimeAmPm
    }

    override fun getTint(): Int {
        val context = context ?: return super.getTint()
        return context.getColor(R.color.dock_item_alarm_color)
    }

    override fun getAction(view: View): Runnable {
        return Runnable {
            try {
                alarmHandle?.nextAlarmIntent?.send()
            } catch (ignored: CanceledException) {}
        }
    }

    override fun getBasePriority(): Long {
        return DockItemPriorities.PRIORITY_ALARM.priority.toLong()
    }

    override fun getSubPriority(): Long {
        return abs(System.currentTimeMillis() - (alarmHandle?.nextAlarmTimeMs ?: 0L))
    }
}