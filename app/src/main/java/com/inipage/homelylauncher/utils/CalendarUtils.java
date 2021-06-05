package com.inipage.homelylauncher.utils;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.persistence.PrefsHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarUtils {

    private static final SimpleDateFormat EVENT_FORMATTER =
        new SimpleDateFormat("h:mm aa", Locale.getDefault());
    private static final long HALF_DAY_IN_MILLIS = 1000L * 60L * 60L * 12L;
    private final static String[] QUERY_COLUMNS = new String[]{
        CalendarContract.Instances.DTSTART,
        CalendarContract.Instances.DTEND,
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.CALENDAR_ID,
        };

    @Nullable
    public static Event findRelevantEvent(Context context) {
        final boolean hasCalendarPermission =
            PermissionUtils.checkPermission(Manifest.permission.READ_CALENDAR, context);
        if (!hasCalendarPermission) {
            return null;
        }

        final long now = System.currentTimeMillis();
        final ContentResolver resolver = context.getContentResolver();
        final Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now - HALF_DAY_IN_MILLIS);
        ContentUris.appendId(builder, now + HALF_DAY_IN_MILLIS);
        final Cursor cursor = resolver.query(builder.build(), QUERY_COLUMNS, null, null,
                                             CalendarContract.Instances.DTSTART + " asc");

        if (cursor == null || !cursor.moveToFirst()) {
            return null;
        }
        final int allDayCol = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY);
        final int startCol = cursor.getColumnIndex(CalendarContract.Instances.DTSTART);
        final int endCol = cursor.getColumnIndex(CalendarContract.Instances.DTEND);
        final int idCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID);
        final int titleCol = cursor.getColumnIndex(CalendarContract.Instances.TITLE);
        final int locationCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION);
        final int calendarIdCol = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID);

        if (startCol == -1 ||
            endCol == -1 ||
            titleCol == -1 ||
            allDayCol == -1 ||
            locationCol == -1 ||
            idCol == -1 ||
            calendarIdCol == -1 ||
            cursor.getCount() == 0) {
            cursor.close();
            return null;
        }

        Map<Integer, Boolean> disabledCalendars = PrefsHelper.getDisabledCalendars(context);
        @Nullable Event fallbackEvent = null;
        while (!cursor.isAfterLast()) {
            final int calendarId = cursor.getInt(calendarIdCol);
            if (disabledCalendars.containsKey(calendarId)) {
                cursor.moveToNext();
                continue;
            }

            final String title = cursor.getString(titleCol);
            final int id = cursor.getInt(idCol);
            @Nullable final String location = cursor.getString(locationCol);
            final boolean allDay = cursor.getInt(allDayCol) == 1;
            final long startTime = cursor.getLong(startCol);
            final long endTime = cursor.getLong(endCol);
            if (allDay && fallbackEvent == null) {
                fallbackEvent = new Event(startTime, endTime, allDay, id, calendarId, title, location);
                cursor.moveToNext();
                continue;
            }
            // Ongoing events: best case
            if (startTime < now && endTime > now) {
                cursor.close();
                return new Event(startTime, endTime, allDay, id, calendarId, title, location);
            }
            // Otherwise, future events, second best case, we'll hit after ongoing events due to
            // sorting
            if (startTime > now) {
                cursor.close();
                return new Event(startTime, endTime, allDay, id, calendarId, title, location);
            }

            // Otherwise, ignore and move one
            cursor.moveToNext();
        }
        cursor.close();
        return fallbackEvent;
    }

    public static void launchEvent(Context context, int eventId) {
        final Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        Intent intent = new Intent(Intent.ACTION_VIEW).setData(uri);
        context.startActivity(intent);
    }

    public static List<Calendar> getCalendars(Context context) {
        final List<Calendar> result = new ArrayList<>();
        final boolean hasCalendarPermission =
            PermissionUtils.checkPermission(Manifest.permission.READ_CALENDAR, context);
        if (!hasCalendarPermission) {
            return result;
        }

        final ContentResolver resolver = context.getContentResolver();
        final Uri.Builder builder = CalendarContract.Calendars.CONTENT_URI.buildUpon();
        final Cursor cursor = resolver.query(
            builder.build(),
            null,
            null,
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " desc");

        if (cursor == null || !cursor.moveToFirst()) {
            return null;
        }
        final int calendarIdIntCol = cursor.getColumnIndex(CalendarContract.Calendars._ID);
        final int displayNameStrCol = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);

        if (calendarIdIntCol == -1 ||
            displayNameStrCol == -1 ||
            cursor.getCount() == 0) {
            cursor.close();
            return result;
        }

        Map<Integer, Boolean> disabledCalendars = PrefsHelper.getDisabledCalendars(context);
        while (!cursor.isAfterLast()) {
            final String name = cursor.getString(displayNameStrCol);
            final int id = cursor.getInt(calendarIdIntCol);
            result.add(new Calendar(id, name, !disabledCalendars.containsKey(id)));
            cursor.moveToNext();
        }
        cursor.close();
        return result;
    }

    public static void launchEventLocation(Context context, String location) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://maps.google.com/maps?q=" + location));
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        }
    }

    public static class Event {
        private final long mStart;
        private final long mEnd;
        private final boolean mAllDay;
        private final int mId;
        private final int mCalendarId;
        private final String mTitle;
        @Nullable
        private final String mLocation;

        private Event(
            long start,
            long end,
            boolean allDay,
            int id,
            int calendarId,
            String title,
            @Nullable String location) {
            this.mStart = start;
            this.mEnd = end;
            this.mAllDay = allDay;
            this.mId = id;
            this.mCalendarId = calendarId;
            this.mTitle = title;
            this.mLocation = location;
        }

        public long getStart() {
            return mStart;
        }

        public long getEnd() {
            return mEnd;
        }

        public boolean getAllDay() {
            return mAllDay;
        }

        public String getTitle() {
            return mTitle;
        }

        public int getID() {
            return mId;
        }

        public int getCalendarId() {
            return mCalendarId;
        }

        public String getLongDescription(Context context) {
            return mAllDay ? getShortDescription(context) :
                   context.getString(
                       R.string.dock_icon_calendar_time,
                       EVENT_FORMATTER.format(new Date(mStart)),
                       EVENT_FORMATTER.format(new Date(mEnd)));
        }

        public String getShortDescription(Context context) {
            return mAllDay ?
                   context.getString(R.string.dock_icon_calendar_all_day) :
                   EVENT_FORMATTER.format(new Date(mStart));
        }

        @Nullable
        public String getLocation() {
            return mLocation;
        }
    }

    public static class Calendar {

        private final int mCalendarId;
        private final String mDisplayName;
        private boolean mEnabled;

        public Calendar(int calendarId, String displayName, boolean enabled) {
            mCalendarId = calendarId;
            mDisplayName = displayName;
            mEnabled = enabled;
        }

        public int getCalendarId() {
            return mCalendarId;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public boolean isEnabled() {
            return mEnabled;
        }

        public void setEnabled(boolean enabled) {
            mEnabled = enabled;
        }
    }
}
