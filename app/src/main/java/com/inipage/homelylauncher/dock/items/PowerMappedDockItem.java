package com.inipage.homelylauncher.dock.items;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.dock.ConfigurableAppBackedDockItem;
import com.inipage.homelylauncher.dock.DockItemPriorities;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.utils.BatteryUtils;

import java.util.Map;

public class PowerMappedDockItem extends ConfigurableAppBackedDockItem {

    @Nullable private BroadcastReceiver mBroadcastReceiver;

    private int mPowerLevel;
    private boolean mIsLowPower;
    private boolean mIsCharging;

    public PowerMappedDockItem(Map<Integer, DockItem> items) {
        super(items);
    }

    @Override
    public void onAttach() {
        @Nullable final Context context = getContext();
        if (context == null) {
            return;
        }
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                powerValuesChanged();
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);
        powerValuesChanged();
    }

    @Override
    public void onDetach() {
        @Nullable final Context context = getContext();
        if (context == null || mBroadcastReceiver == null) {
            return;
        }
        context.unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected int getDatabaseField() {
        return DockItem.DOCK_SHOW_IN_LOW_POWER;
    }

    @Override
    protected int getBottomSheetMessage() {
        return R.string.dock_show_power_state;
    }

    @Override
    public int getIcon() {
        return mIsCharging ?
               R.drawable.dock_icon_battery_charging :
               R.drawable.dock_icon_battery_low;
    }

    @Nullable
    @Override
    public String getLabel() {
        @Nullable final Context context = getContext();
        if (context == null) {
            return null;
        }
        return context.getString(R.string.dock_icon_power, mPowerLevel);
    }

    @Override
    public int getTint() {
        @Nullable final Context context = getContext();
        if (context == null) {
            return super.getTint();
        }
        return context.getColor(R.color.dock_item_power_color);
    }

    @Override
    public long getBasePriority() {
        return mIsLowPower ?
           DockItemPriorities.PRIORITY_POWER_EVENT_LOW.getPriority() :
           DockItemPriorities.PRIORITY_POWER_EVENT_CHARGING.getPriority();
    }

    private void powerValuesChanged() {
        @Nullable final Context context = getContext();
        if (context == null) {
            return;
        }
        mPowerLevel = BatteryUtils.getBatteryLevel(context);
        mIsLowPower = BatteryUtils.isLowCharge(context, mPowerLevel);
        mIsCharging = BatteryUtils.isCharging(context);
        if (mIsLowPower || mIsCharging) {
            showSelf();
        } else {
            hideSelf();
        }
    }
}
