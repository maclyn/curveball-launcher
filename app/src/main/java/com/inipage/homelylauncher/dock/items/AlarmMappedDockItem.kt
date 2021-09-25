package com.inipage.homelylauncher.dock.items

import com.inipage.homelylauncher.dock.DockControllerItem
import android.app.PendingIntent
import com.inipage.homelylauncher.utils.AlarmUtils
import com.inipage.homelylauncher.R
import android.app.PendingIntent.CanceledException
import android.view.View
import com.inipage.homelylauncher.dock.DockItemPriorities

/**
 * Renders an alarm in the dock.
 */
class AlarmMappedDockItem : DockControllerItem() {

    private var mHasAlarm = false
    private var mNextAlarmTimeMs: Long = 0
    private var mNextAlarmIntent: PendingIntent? = null
    private var mNextAlarmTime: String? = null
    private var mNextAlarmTimeAMPM: String? = null

    override fun onAttach() {
        val host = mHost ?: return
        val context = host.context ?: return
        mHasAlarm = AlarmUtils.hasAlarm(context)
        mNextAlarmTimeMs = AlarmUtils.getNextAlarmMs(context)
        mNextAlarmTime = AlarmUtils.getNextAlarmTime(context)
        mNextAlarmTimeAMPM = AlarmUtils.getNextAlarmTimeAMPM(context)
        mNextAlarmIntent = AlarmUtils.getAlarmIntent(context)
        if (mHasAlarm) {
            host.showHostedItem()
        }
    }

    override fun getIcon(): Int {
        return R.drawable.dock_icon_alarm
    }

    override fun getLabel(): String? {
        return mNextAlarmTime
    }

    override fun getSecondaryLabel(): String? {
        return mNextAlarmTimeAMPM
    }

    override fun getTint(): Int {
        val context = context ?: return super.getTint()
        return context.getColor(R.color.dock_item_alarm_color)
    }

    override fun getAction(view: View): Runnable {
        return Runnable {
            try {
                mNextAlarmIntent?.send()
            } catch (ignored: CanceledException) {}
        }
    }

    override fun getBasePriority(): Long {
        return DockItemPriorities.PRIORITY_ALARM.priority.toLong()
    }

    override fun getSubPriority(): Long {
        return Math.abs(System.currentTimeMillis() - mNextAlarmTimeMs)
    }
}