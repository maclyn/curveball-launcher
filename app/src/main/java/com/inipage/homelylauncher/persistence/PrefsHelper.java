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

    private static PrefsHelper s_INSTANCE;

    private final SharedPreferences mSharedPreferences;

    private PrefsHelper(Context context) {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static void seed(Context context) {
        if (s_INSTANCE == null) {
            s_INSTANCE = new PrefsHelper(context);
        }
    }

    public static PrefsHelper get() {
        return s_INSTANCE;
    }

    public Map<Integer, Boolean> getDisabledCalendars() {
        final Map<Integer, Boolean> disabledCalendars = new HashMap<>();
        Set<String> prefValue = mSharedPreferences.getStringSet(
            Constants.DISABLED_CALENDARS_PREF, new HashSet<>());
        for (String cal : prefValue) {
            disabledCalendars.put(Integer.valueOf(cal), true);
        }
        return disabledCalendars;
    }

    public void saveDisabledCalendars(List<CalendarUtils.Calendar> disabledCalendars) {
        final Set<String> prefValue =
            disabledCalendars.parallelStream()
                .filter(calendar -> !calendar.isEnabled())
                .map(calendar -> String.valueOf(calendar.getCalendarId()))
                .collect(Collectors.toSet());
        mSharedPreferences.edit().putStringSet(Constants.DISABLED_CALENDARS_PREF, prefValue).apply();
    }

    public boolean checkAndUpdateIsNewUser() {
        if (mSharedPreferences.getBoolean(Constants.HAS_SHOWN_NEW_USER_EXPERIENCE, false)) {
            return false;
        }
        mSharedPreferences.edit().putBoolean(Constants.HAS_SHOWN_NEW_USER_EXPERIENCE, true).apply();
        return true;
    }

    public static boolean usingVScroll() {
        return get().mSharedPreferences.getBoolean(Constants.VERTICAL_SCROLLER_PREF, false);
    }

    public static boolean useGWeather() {
        return get().mSharedPreferences.getBoolean(Constants.USE_G_WEATHER_PREF, false);
    }
}
