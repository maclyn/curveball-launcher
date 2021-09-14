package com.inipage.homelylauncher.dock.items;

import android.app.PendingIntent;
import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.dock.DockItemPriorities;
import com.inipage.homelylauncher.utils.AlarmUtils;

public class AlarmMappedDockItem extends SynchDockControllerItem {

    private final boolean mHasAlarm;
    private final long mNextAlarmTimeMs;
    @Nullable
    private final PendingIntent mNextAlarmIntent;
    @Nullable
    private final String mNextAlarmTime;
    @Nullable
    private final String mNextAlarmTimeAMPM;

    public AlarmMappedDockItem(Context context) {
        mHasAlarm = AlarmUtils.hasAlarm(context);
        mNextAlarmTimeMs = AlarmUtils.getNextAlarmMs(context);
        mNextAlarmTime = AlarmUtils.getNextAlarmTime(context);
        mNextAlarmTimeAMPM = AlarmUtils.getNextAlarmTimeAMPM(context);
        mNextAlarmIntent = AlarmUtils.getAlarmIntent(context);
    }

    @Override
    public boolean isActive(Context context) {
        return mHasAlarm;
    }

    @Override
    public int getIcon() {
        return R.drawable.dock_icon_alarm;
    }

    @Nullable
    @Override
    public String getLabel(Context context) {
        return mNextAlarmTime;
    }

    @Nullable
    @Override
    public String getSecondaryLabel(Context context) {
        return mNextAlarmTimeAMPM;
    }

    @Override
    public int getTint(Context context, TintCallback __) {
        return context.getColor(R.color.dock_item_alarm_color);
    }

    @Override
    public Runnable getAction(View view, Context context) {
        return () -> {
            if (mNextAlarmIntent == null) {
                return;
            }
            try {
                mNextAlarmIntent.send();
            } catch (PendingIntent.CanceledException ignored) {
            }
        };
    }

    @Override
    public long getBasePriority() {
        return DockItemPriorities.PRIORITY_ALARM.getPriority();
    }

    @Override
    public long getSubPriority() {
        return Math.abs(System.currentTimeMillis() - mNextAlarmTimeMs);
    }
}
