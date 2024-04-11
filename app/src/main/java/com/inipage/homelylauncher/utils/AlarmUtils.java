package com.inipage.homelylauncher.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;

import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static android.content.Context.ALARM_SERVICE;

public class AlarmUtils {

    private static final SimpleDateFormat ALARM_TIME_FORMAT =
        new SimpleDateFormat("h:mm aa", Locale.US);
    private static final SimpleDateFormat ALARM_TIME_AMPM_FORMAT =
        new SimpleDateFormat("aa", Locale.US);
    private static final long HALF_DAY_MILLIS = 1000 * 60 * 60 * 12;
    private static final String[] BLOCKED_ALARM_SENDERS = new String[]{
        "com.google,android.calendar",
        "com.android.calendar",
        "com.samsung.android.calendar",
        "com.android.providers.calendar"
    };

    public static class AlarmHandle {

        private final boolean mHasAlarm;
        @Nullable private String mNextAlarmTime;
        @Nullable private String mNextAlarmTimeAmPm;
        @Nullable private PendingIntent mNextAlarmIntent;
        private long mNextAlarmTimeMs;

        private AlarmHandle(Context context) {
            final AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
            if (am == null) {
                mHasAlarm = false;
                return;
            }

            // Get next alarm clock really isn't perfect -- we have to filter out all calendar entries,
            // and then choose a reasonable window during which we might expect to see an alarm, since
            // the "before bedtime reminder" is also an alarm...
            @Nullable final AlarmManager.AlarmClockInfo clockInfo = am.getNextAlarmClock();
            if (clockInfo == null || clockInfo.getShowIntent() == null) {
                mHasAlarm = false;
                return;
            }
            final String sender = clockInfo.getShowIntent().getIntentSender().getCreatorPackage();
            if (Arrays.asList(BLOCKED_ALARM_SENDERS).contains(sender)) {
                mHasAlarm = false;
                return;
            }
            final long triggerTime = clockInfo.getTriggerTime();
            final long now = System.currentTimeMillis();
            // If the alarm isn't within 12 hours, ignore it
            if (triggerTime - now > HALF_DAY_MILLIS) {
                mHasAlarm = false;
                return;
            }
            // If the alarm isn't between 2:00am and 11:59am, it probably isn't a wakeup alarm
            // I don't love this approximation, but getNextAlarmClock() returns other scheduled alarms
            // that aren't "alarm clock" alarms
            final Calendar triggerCalendar = Calendar.getInstance();
            triggerCalendar.setTimeInMillis(triggerTime);
            mHasAlarm = triggerCalendar.get(Calendar.HOUR_OF_DAY) >= 2 &&
                triggerCalendar.get(Calendar.HOUR_OF_DAY) <= 12;
            if (!mHasAlarm) {
                return;
            }
            final Date triggerDate = new Date(triggerTime);
            mNextAlarmTime = ALARM_TIME_FORMAT.format(triggerDate);
            mNextAlarmTimeAmPm = ALARM_TIME_AMPM_FORMAT.format(triggerDate);
            mNextAlarmTimeMs = triggerTime;
            mNextAlarmIntent = clockInfo.getShowIntent();
        }

        public boolean hasAlarm() {
            return mHasAlarm;
        }

        @Nullable
        public String getNextAlarmTime() {
            return mNextAlarmTime;
        }

        @Nullable
        public String getNextAlarmTimeAmPm() {
            return mNextAlarmTimeAmPm;
        }

        @Nullable
        public PendingIntent getNextAlarmIntent() {
            return mNextAlarmIntent;
        }

        public long getNextAlarmTimeMs() {
            return mNextAlarmTimeMs;
        }
    }

    public static AlarmHandle getAlarmHandle(Context context) {
        return new AlarmHandle(context);
    }
}
