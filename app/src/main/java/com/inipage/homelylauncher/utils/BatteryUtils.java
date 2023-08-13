package com.inipage.homelylauncher.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;

import androidx.annotation.Nullable;

public class BatteryUtils {

    public static boolean isLowCharge(Context context, int batteryLevel) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm.isPowerSaveMode() || batteryLevel < 20;
    }

    public static boolean isCharging(Context context) {
        IntentFilter batteryReading = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        @Nullable final Intent batteryStatus = context.registerReceiver(null, batteryReading);
        if (batteryStatus == null) {
            return false;
        }

        final int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static int getBatteryLevel(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent status = context.registerReceiver(null, filter);
        if (status != null) {
            return status.getIntExtra(BatteryManager.EXTRA_LEVEL, 50);
        }
        return 0;
    }
}
