package com.inipage.homelylauncher.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.provider.Settings;

import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
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
        "com.samsung.android.calendar",
        "com.android.providers.calendar"
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

        // Get next alarm clock really isn't perfect -- we have to filter out all calendar entries,
        // and then choose a reasonable window during which we might expect to see an alarm, since
        // the "before bedtime reminder" is also an alarm...
        @Nullable final AlarmManager.AlarmClockInfo clockInfo = am.getNextAlarmClock();
        if (clockInfo == null || clockInfo.getShowIntent() == null) {
            return false;
        }
        final String sender = clockInfo.getShowIntent().getIntentSender().getCreatorPackage();
        if (Arrays.asList(BLOCKED_ALARM_SENDERS).contains(sender)) {
            return false;
        }
        final long triggerTime = clockInfo.getTriggerTime();
        final long now = System.currentTimeMillis();
        // If the alarm isn't within 12 hours, ignore it
        if (triggerTime - now < HALF_DAY_MILLIS) {
            return false;
        }
        // If the alarm isn't between 2:00am and 12:59pm, it probably isn't a wakeup alarm
        final Calendar triggerDate = Calendar.getInstance();
        triggerDate.setTimeInMillis(triggerTime);
        return triggerDate.get(Calendar.HOUR_OF_DAY) >= 2 &&
            triggerDate.get(Calendar.HOUR_OF_DAY) <= 12;
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
