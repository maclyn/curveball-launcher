package com.inipage.homelylauncher.persistence;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.ApplicationClass;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.utils.CalendarUtils;
import com.inipage.homelylauncher.utils.Constants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

public class PrefsHelper {

    private static PrefsHelper s_INSTANCE;

    private final SharedPreferences mSharedPreferences;

    private static final String STAND_IN_PREFIX = "icon_stand_in/";

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

    public static boolean isUsingIconPack() {
        return get().mSharedPreferences.getBoolean(Constants.HAS_ICON_PACK_SET_PREF, false);
    }

    @Nullable
    public static String getIconPack() {
        return get().mSharedPreferences.getString(Constants.SELECTED_ICON_PACK_PACKAGE_PREF, null);
    }

    public static void setIconPack(@Nullable String packageName) {
        if (packageName == null) {
            get().mSharedPreferences.edit()
                .remove(Constants.SELECTED_ICON_PACK_PACKAGE_PREF)
                .putBoolean(Constants.HAS_ICON_PACK_SET_PREF, false)
                .commit();
        } else {
            get().mSharedPreferences.edit()
                .putBoolean(Constants.HAS_ICON_PACK_SET_PREF, true)
                .putString(Constants.SELECTED_ICON_PACK_PACKAGE_PREF, packageName)
                .commit();
        }
    }

    public static Map<Pair<String, String>, String> loadStandIns(@Nullable String iconPackPackage) {
        Set<String> standInsPref =
            get().mSharedPreferences.getStringSet(getStandInKey(iconPackPackage), new HashSet<>());
        Map<Pair<String, String>, String> standIns = new HashMap<>();
        for (String standIn : standInsPref) {
            String[] sides = standIn.split("\\|");
            if (sides.length != 2) {
                continue;
            }
            String[] components = sides[0].split("/");
            if (components.length != 2) {
                continue;
            }
            standIns.put(Pair.create(components[0], components[1]), sides[1]);
        }
        return standIns;
    }

    public static void setStandIn(
        String iconPackPackage,
        Pair<String, String> replacedComponent,
        @Nullable String replacementDrawable)
    {
        final String key = getStandInKey(iconPackPackage);
        Set<String> standInsPref =
            new HashSet<>(
                get().mSharedPreferences.getStringSet(getStandInKey(iconPackPackage),
                                                      new HashSet<>()));
        if (replacementDrawable == null) {
            @Nullable String removalStr = null;
            for (String standIn : standInsPref) {
                if (!standIn.startsWith(replacedComponent.first)) {
                    continue;
                }
                if (!standIn.substring(standIn.indexOf(replacedComponent.first) + 1).startsWith(replacedComponent.second)) {
                    continue;
                }
                removalStr = standIn;
                break;
            }
            if (removalStr != null) {
                standInsPref.remove(removalStr);
            }
        } else {
            standInsPref.add(
                replacedComponent.first + "/" + replacedComponent.second + "|" + replacementDrawable);
        }
        get().mSharedPreferences.edit().putStringSet(key, standInsPref).commit();
    }

    public static void clearStandIns() {
        @Nullable String iconPack = getIconPack();
        if (iconPack == null) {
            return;
        }
        final String key = getStandInKey(iconPack);
        get().mSharedPreferences.edit().putStringSet(key, new HashSet<>()).commit();
    }

    private static String getStandInKey(String iconPackPackage) {
        return STAND_IN_PREFIX + iconPackPackage;
    }
}
