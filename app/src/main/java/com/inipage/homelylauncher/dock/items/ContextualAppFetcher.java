package com.inipage.homelylauncher.dock.items;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.dock.DockControllerItem;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.model.ClassicGridItem;
import com.inipage.homelylauncher.model.ClassicGridPage;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.Constants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fetches apps from the UsageStatsService.
 */
public class ContextualAppFetcher {

    private Map<String, Boolean> mHiddenApps;

    public ContextualAppFetcher() {
        reloadPrefs();
    }

    public void reloadPrefs() {
        final List<DockItem> dockItems = DatabaseEditor.get().getDockPreferences();

        Stream<String> hiddenBySuggestion =
            dockItems.stream()
                .filter(DockItem::isHidden)
                .map(item -> lookupKey(item.getPackageName(), item.getActivityName()));

        // This mostly works
        Stream<String> hiddenByDrawer =
            DatabaseEditor.get().getHiddenAppsAsMap(true).keySet()
                .stream()
                .map(key -> lookupKey(key.first, key.second));

        // Yeesh.
        Stream<String> hiddenFromPages = DatabaseEditor.get()
            .getGridPages()
            .parallelStream()
            .flatMap(
                (Function<ClassicGridPage, Stream<ClassicGridItem>>) gridPage ->
                    gridPage.getItems().parallelStream())
            .filter(gridItem -> gridItem.getType() ==
                ClassicGridItem.GRID_TYPE_APP)
            .map(gridItem -> lookupKey(gridItem.getPackageName(), gridItem.getActivityName()));

        mHiddenApps =
            Stream.concat(Stream.concat(
                hiddenBySuggestion, hiddenByDrawer), hiddenFromPages)
                    .collect(
                        Collectors.toMap(
                            item -> item,
                            item -> true,
                            (keyA, keyB) -> keyA // Same item can be hidden in multiple places
                    ));
    }

    public List<DockControllerItem> getRecentApps(Context context) {
        final List<DockControllerItem> suggestions = new ArrayList<>();
        final Map<String, Boolean> packagesSeen = new HashMap<>();

        @SuppressWarnings("ResourceType")
        final UsageStatsManager usm =
            (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return ImmutableList.of();
        }

        final List<SuggestionApp> recentApps = new ArrayList<>();
        final Map<String, Long> useTimeMap = new HashMap<>();

        // Look at the past 4 hours
        final Calendar workingCalendar = new GregorianCalendar();
        workingCalendar.roll(Calendar.HOUR_OF_DAY, -4);
        long end = System.currentTimeMillis();
        long start = workingCalendar.getTimeInMillis();
        List<UsageStats> granularOptions =
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end);
        for (UsageStats entry : granularOptions) {
            // Weighed quite heavily; 2x past day and 3x past week
            long timeUsed = entry.getTotalTimeInForeground() * 6 * 3;
            if (useTimeMap.containsKey(entry.getPackageName())) {
                useTimeMap.put(
                    entry.getPackageName(),
                    useTimeMap.get(entry.getPackageName()) + timeUsed);
            } else {
                useTimeMap.put(entry.getPackageName(), timeUsed);
            }
        }

        // Look at past day
        long previousEndPoint = workingCalendar.getTimeInMillis();
        workingCalendar.roll(Calendar.DAY_OF_WEEK, -1);
        start = workingCalendar.getTimeInMillis();
        end = previousEndPoint;
        granularOptions =
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end);
        for (UsageStats entry : granularOptions) {
            // Weighed a little more heavily than week
            long timeUsed = (long) (entry.getTotalTimeInForeground() * 1.5);
            if (useTimeMap.containsKey(entry.getPackageName())) {
                useTimeMap.put(
                    entry.getPackageName(),
                    useTimeMap.get(entry.getPackageName()) + timeUsed);
            } else {
                useTimeMap.put(entry.getPackageName(), timeUsed);
            }
        }

        //  Look at past week
        previousEndPoint = workingCalendar.getTimeInMillis();
        workingCalendar.roll(Calendar.WEEK_OF_YEAR, -1);
        start = workingCalendar.getTimeInMillis();
        end = previousEndPoint;
        List<UsageStats> leastGranularOptions = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            start,
            end);
        for (UsageStats entry : leastGranularOptions) {
            // Unweighted hours/day
            long timeUsed = entry.getTotalTimeInForeground() / 7;
            if (useTimeMap.containsKey(entry.getPackageName())) {
                useTimeMap.put(
                    entry.getPackageName(),
                    useTimeMap.get(entry.getPackageName()) + timeUsed);
            } else {
                useTimeMap.put(entry.getPackageName(), timeUsed);
            }
        }

        for (Map.Entry<String, Long> entry : useTimeMap.entrySet()) {
            if (packagesSeen.containsKey(entry.getKey())) {
                continue;
            }

            final List<ApplicationIconHideable> apps =
                AppInfoCache.get().getActivitiesForPackageFast(entry.getKey());
            if (apps.isEmpty()) {
                continue;
            }

            final ApplicationIconHideable targetApp = apps.get(0);
            if (mHiddenApps.containsKey(targetApp.getPackageName() + "|" + targetApp.getActivityName())) {
                continue;
            }
            recentApps.add(
                new SuggestionApp(targetApp.getPackageName(), targetApp.getActivityName(), entry.getValue()));
        }
        recentApps.sort((lhs, rhs) -> (int) (rhs.getDurationUsed() - lhs.getDurationUsed()));
        recentApps.forEach(suggestionApp -> updateWorkingSet(
            suggestions,
            packagesSeen,
            suggestionApp));
        return suggestions.subList(0, suggestions.size() > 15 ? 14 : suggestions.size());
    }

    private void updateWorkingSet(
        List<DockControllerItem> suggestions,
        Map<String, Boolean> packagesSeen,
        SuggestionApp recentApp) {
        if (recentApp == null) {
            return;
        }
        String lookupKey = recentApp.getPackageName() + "|" + recentApp.getActivityName();
        if (packagesSeen.containsKey(lookupKey) ||
            mHiddenApps.containsKey(lookupKey) ||
            recentApp.getPackageName().equals(Constants.PACKAGE)) {
            return;
        }
        packagesSeen.put(lookupKey, true);
        suggestions.add(new RecentAppDockItem(recentApp));
    }

    private String lookupKey(String packageName, String activityName) {
        return packageName + "|" + activityName;
    }

    static class SuggestionApp {

        private final String mPackageName;
        private final String mActivityName;
        private final long mDurationUsed;

        SuggestionApp(String packageName, String activityName, long mDurationUsed) {
            this.mPackageName = packageName;
            this.mActivityName = activityName;
            this.mDurationUsed = mDurationUsed;
        }

        String getPackageName() {
            return mPackageName;
        }

        String getActivityName() {
            return mActivityName;
        }

        long getDurationUsed() {
            return mDurationUsed;
        }
    }
}

