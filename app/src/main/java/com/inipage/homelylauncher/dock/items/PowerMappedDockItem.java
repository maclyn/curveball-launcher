package com.inipage.homelylauncher.dock.items;

import android.content.Context;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.dock.ConfigurableAppBackedDockItem;
import com.inipage.homelylauncher.dock.DockItemPriorities;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.utils.BatteryUtils;

import java.util.Map;

public class PowerMappedDockItem extends ConfigurableAppBackedDockItem {

    private final int mPowerLevel;
    private final boolean mIsLowPower;
    private final boolean mIsCharging;

    public PowerMappedDockItem(Context context, Map<Integer, DockItem> items) {
        super(items);
        mPowerLevel = BatteryUtils.getBatteryLevel(context);
        mIsLowPower = BatteryUtils.isLowCharge(context);
        mIsCharging = BatteryUtils.isCharging(context);
    }

    @Override
    protected int getDatabaseField() {
        return DockItem.DOCK_SHOW_IN_LOW_POWER;
    }

    @Override
    protected int getBottomSheetMessage() {
        return mIsLowPower ? R.string.dock_show_in_low_power : R.string.dock_show_in_charging;
    }

    @Override
    public boolean isActive(Context context) {
        return mIsLowPower || mIsCharging;
    }

    @Override
    public int getIcon() {
        return mIsLowPower ?
               R.drawable.dock_icon_battery_low :
               R.drawable.dock_icon_battery_charging;
    }

    @Nullable
    @Override
    public String getLabel(Context context) {
        return context.getString(R.string.dock_icon_power, mPowerLevel);
    }

    @Override
    public int getTint(Context context, Callback __) {
        return context.getColor(R.color.dock_item_power_color);
    }

    @Override
    public long getBasePriority() {
        return mIsLowPower ?
               DockItemPriorities.PRIORITY_POWER_EVENT_LOW :
               DockItemPriorities.PRIORITY_POWER_EVENT_CHARGING;
    }
}
