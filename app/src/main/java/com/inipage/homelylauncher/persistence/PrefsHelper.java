package com.inipage.homelylauncher.persistence;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.inipage.homelylauncher.utils.CalendarUtils;
import com.inipage.homelylauncher.utils.Constants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PrefsHelper {

    public static Map<Integer, Boolean> getDisabledCalendars(Context context) {
        final Map<Integer, Boolean> disabledCalendars = new HashMap<>();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> prefValue =
            prefs.getStringSet(Constants.DISABLED_CALENDARS_PREF, new HashSet<>());
        for (String cal : prefValue) {
            disabledCalendars.put(Integer.valueOf(cal), true);
        }
        return disabledCalendars;
    }

    public static void saveDisabledCalendars(
        Context context,
        List<CalendarUtils.Calendar> disabledCalendars) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final Set<String> prefValue =
            disabledCalendars.parallelStream()
                .filter(calendar -> !calendar.isEnabled())
                .map(calendar -> String.valueOf(calendar.getCalendarId()))
                .collect(Collectors.toSet());
        prefs.edit().putStringSet(Constants.DISABLED_CALENDARS_PREF, prefValue).apply();
    }
}
