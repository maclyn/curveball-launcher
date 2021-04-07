package com.inipage.homelylauncher.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;

import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import static android.content.Context.ALARM_SERVICE;

public class AlarmUtils {

    private static final SimpleDateFormat ALARM_TIME_FORMAT =
        new SimpleDateFormat("h:mm", Locale.US);
    private static final SimpleDateFormat ALARM_TIME_AMPM_FORMAT =
        new SimpleDateFormat("aa", Locale.US);
    private static final long HALF_DAY_MILLIS = 1000 * 60 * 60 * 12;

    private static final String[] BLOCKED_ALARM_SENDERS = new String[]{
        "com.google,android.calendar",
        "com.android.calendar",
        "com.samsung.android.calendar"
    };

    @Nullable
    public static String getNextAlarmTime(Context context) {
        if (!hasAlarm(context)) {
            return null;
        }

        return ALARM_TIME_FORMAT.format(new Date(getNextAlarmMs(context)));
    }

    public static boolean hasAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (am == null) {
            return false;
        }

        AlarmManager.AlarmClockInfo clockInfo = am.getNextAlarmClock();
        final String sender = clockInfo == null
                              ? ""
                              : clockInfo.getShowIntent().getIntentSender().getCreatorPackage();
        return clockInfo != null &&
            clockInfo.getTriggerTime() - System.currentTimeMillis() < HALF_DAY_MILLIS &&
            Arrays.stream(BLOCKED_ALARM_SENDERS).noneMatch(s -> s.equals(sender));
    }

    public static long getNextAlarmMs(Context context) {
        if (!hasAlarm(context)) {
            return -1L;
        }

        return ((AlarmManager) context.getSystemService(ALARM_SERVICE))
            .getNextAlarmClock()
            .getTriggerTime();
    }

    @Nullable
    public static String getNextAlarmTimeAMPM(Context context) {
        if (!hasAlarm(context)) {
            return null;
        }

        return ALARM_TIME_AMPM_FORMAT.format(new Date(getNextAlarmMs(context)));
    }

    @Nullable
    public static PendingIntent getAlarmIntent(Context context) {
        if (!hasAlarm(context)) {
            return null;
        }
        return ((AlarmManager) context.getSystemService(ALARM_SERVICE))
            .getNextAlarmClock()
            .getShowIntent();
    }
}
