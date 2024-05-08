package com.inipage.homelylauncher.caches;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.inipage.homelylauncher.drawer.FastScrollable;
import com.inipage.homelylauncher.model.ApplicationIconCheckable;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.LifecycleLogUtils;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import static android.os.Process.myUserHandle;

/**
 * Keeps a cache of sorted installed activities and shortcuts from those activities. Keeping this
 * close is much faster than regular hitting system services (potential IPC) to refetch.
 */
public class AppInfoCache {

    private static final int APP_HOST_ID = 42 << 10;
    @SuppressLint("StaticFieldLeak")
    private static AppInfoCache s_INSTANCE;
    private final Context mContext;
    private final AppWidgetHost mAppWidgetHost;
    private final Map<String, List<ShortcutWrapper>> mPackageToShortcutInfos;
    private final Map<String, List<ApplicationIconHideable>> mPackageToApps;
    private Map<String, Map<String, AppWidgetProviderInfo>> mPackageToClassToAppWidgetProvider;
    private List<ApplicationIconHideable> mInstalledApps;
    private final LauncherApps.Callback mCallback = new LauncherApps.Callback() {

        // Called when an app is uninstalled
        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
            mInstalledApps = mInstalledApps.stream()
                .filter(applicationIconHideable ->
                            !applicationIconHideable.getPackageName()
                                .equals(packageName))
                .collect(Collectors.toList());
            mPackageToApps.remove(packageName);
            mPackageToShortcutInfos.remove(packageName);
            mPackageToClassToAppWidgetProvider.remove(packageName);
            IconCacheSync.getInstance(mContext).clearCacheForPackage(packageName);
            AppLabelCache.getInstance(mContext).clearCacheForPackage(packageName);
            publishEvent(packageName, PackageModifiedEvent.Modification.REMOVED);
        }

        // Called when an app is installed
        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            List<ApplicationIconHideable> activitiesForPackage =
                getActivitiesForPackage(packageName);
            mInstalledApps.addAll(activitiesForPackage);
            mInstalledApps.sort(FastScrollable.getComparator());
            mPackageToApps.put(packageName, activitiesForPackage);
            final AppWidgetManager appWidgetManager = getAppWidgetManager();
            for (AppWidgetProviderInfo awpi : appWidgetManager.getInstalledProviders()) {
                final String providerPackageName = awpi.provider.getPackageName();
                if (!providerPackageName.equals(packageName)) {
                    continue;
                }
                insertWidgetProviderInfo(awpi);
            }
            publishEvent(packageName, PackageModifiedEvent.Modification.ADDED);
        }

        // Called when an app is upgraded or disabled/enabled
        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            List<ApplicationIconHideable> activitiesForPackage =
                getActivitiesForPackage(packageName);
            mInstalledApps = mInstalledApps.stream()
                .filter(applicationIconHideable ->
                            !applicationIconHideable.getPackageName()
                                .equals(packageName))
                .collect(Collectors.toList());
            mInstalledApps.addAll(activitiesForPackage);
            mInstalledApps.sort(FastScrollable.getComparator());
            mPackageToApps.put(packageName, activitiesForPackage);
            mPackageToClassToAppWidgetProvider.remove(packageName);
            final AppWidgetManager appWidgetManager = getAppWidgetManager();
            for (AppWidgetProviderInfo awpi : appWidgetManager.getInstalledProviders()) {
                final String providerPackageName = awpi.provider.getPackageName();
                if (!providerPackageName.equals(packageName)) {
                    continue;
                }
                insertWidgetProviderInfo(awpi);
            }
            IconCacheSync.getInstance(mContext).clearCacheForPackage(packageName);
            AppLabelCache.getInstance(mContext).clearCacheForPackage(packageName);
            publishEvent(packageName, PackageModifiedEvent.Modification.UPDATED);
        }

        // Called when a group of packages are made available again (e.g. SD card inserted)
        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            log("Packages available = " + Arrays.toString(packageNames));
            reloadAppsAndWidgets();
            EventBus.getDefault().post(new PackagesBulkModifiedEvent(
                packageNames, PackagesBulkModifiedEvent.Availability.AVAILABLE));
        }

        // Called when a group of packages are removed, but might return later
        // (e.g. SD card removed)
        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
            log("Packages unavailable = " + Arrays.toString(packageNames));
            reloadAppsAndWidgets();
            EventBus.getDefault().post(new PackagesBulkModifiedEvent(
                packageNames, PackagesBulkModifiedEvent.Availability.UNAVAILABLE));
        }

        // Called when packages are "suspended" and "unsuspended"; implementing a response to this
        // callback is actually a nice-to-have UI treatment, and not a "must have" since the system
        // will intercept app launch requests with a "App Paused" interstitial

        @Override
        public void onPackagesSuspended(String[] packageNames, UserHandle user) {
            log("Packages suspended = " + Arrays.toString(packageNames));
        }

        @Override
        public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
            log("Packages unsuspended = " + Arrays.toString(packageNames));
        }

        // Called when an app is installed, removed, or upgraded, and has shortcuts
        @Override
        public void onShortcutsChanged(
                @NonNull String packageName, @NonNull List<ShortcutInfo> shortcuts, @NonNull UserHandle user) {
            log("onShortcutsChanged for " + packageName);
            // We have to manually query here; shortcuts will only have "key information" if
            // we do otherwise
            final LauncherApps launcherApps =
                (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            final LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
            query.setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC |
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
            query.setPackage(packageName);
            mPackageToShortcutInfos.put(
                packageName, launcherApps.getShortcuts(query, myUserHandle()).stream()
                    .filter(ShortcutInfo::isEnabled)
                    .map(ShortcutWrapper::new)
                    .collect(Collectors.toList()));
        }
    };

    private AppInfoCache(Context context) {
        mContext = context;
        mAppWidgetHost = new AppWidgetHost(context, APP_HOST_ID);
        mPackageToShortcutInfos = new HashMap<>();
        mPackageToApps = new HashMap<>();

        final LauncherApps launcherApps =
            (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        launcherApps.registerCallback(mCallback);
        reloadAppsAndWidgets();
        // Shortcuts only need to be fetched here, since changes come through the
        // onShortcutInfo updated callbacks
        if (!launcherApps.hasShortcutHostPermission()) {
            return;
        }
        LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
        query.setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC |
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
        final List<ShortcutInfo> infos = launcherApps.getShortcuts(query, myUserHandle());
        for (ShortcutInfo info : infos) {
            if (!mPackageToShortcutInfos.containsKey(info.getPackage())) {
                mPackageToShortcutInfos.put(info.getPackage(), new ArrayList<>());
            }
            if (info.isEnabled()) {
                mPackageToShortcutInfos.get(info.getPackage()).add(new ShortcutWrapper(info));
            }
        }
    }

    private void reloadAppsAndWidgets() {
        final AppWidgetManager appWidgetManager = getAppWidgetManager();
        mInstalledApps = getInstalledAppsImpl(null);
        mPackageToClassToAppWidgetProvider = new HashMap<>();
        if (appWidgetManager == null) {
            return;
        }
        for (AppWidgetProviderInfo awpi : appWidgetManager.getInstalledProviders()) {
            insertWidgetProviderInfo(awpi);
        }
    }

    private AppWidgetManager getAppWidgetManager() {
        return (AppWidgetManager) mContext.getSystemService(Context.APPWIDGET_SERVICE);
    }

    private List<ApplicationIconHideable> getInstalledAppsImpl(@Nullable String packageName) {
        final List<LauncherActivityInfo> launcherApps = getAppsFromSystem(packageName);
        final Map<Pair<String, String>, Boolean> hiddenAppsMap =
            DatabaseEditor.get().getHiddenAppsAsMap(true);
        final List<ApplicationIconHideable> result = new ArrayList<>();
        for (LauncherActivityInfo app : launcherApps) {
            final String appPackageName = app.getComponentName().getPackageName();
            final ApplicationIconHideable applicationIconHideable = new ApplicationIconHideable(
                app,
                mContext,
                hiddenAppsMap.containsKey(
                    new Pair<>(
                        appPackageName,
                        app.getComponentName().getClassName())));
            result.add(applicationIconHideable);

            if (mPackageToApps.containsKey(appPackageName)) {
                mPackageToApps.get(appPackageName).add(applicationIconHideable);
            } else {
                List<ApplicationIconHideable> activities = new ArrayList<>();
                activities.add(applicationIconHideable);
                mPackageToApps.put(appPackageName, activities);
            }
        }
        result.sort((lhs, rhs) -> FastScrollable.getComparator().compare(lhs, rhs));
        return result;
    }

    private void insertWidgetProviderInfo(AppWidgetProviderInfo awpi) {
        final String packageName = awpi.provider.getPackageName();
        if (!mPackageToClassToAppWidgetProvider.containsKey(packageName)) {
            mPackageToClassToAppWidgetProvider.put(packageName, new HashMap<>());
        }
        mPackageToClassToAppWidgetProvider
            .get(packageName)
            .put(awpi.provider.getClassName(), awpi);
    }

    private List<LauncherActivityInfo> getAppsFromSystem(@Nullable String packageName) {
        final LauncherApps appService =
            (LauncherApps) mContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        return appService.getActivityList(packageName, myUserHandle());
    }

    public static void seed(Context context) {
        if (s_INSTANCE == null) {
            s_INSTANCE = new AppInfoCache(context);
        }
    }

    public static AppInfoCache get() {
        return s_INSTANCE;
    }

    public AppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    /**
     * Call when an app is hidden locally.
     */
    public void reloadVisibleActivities() {
        mInstalledApps = getInstalledAppsImpl(null);
    }

    public List<ApplicationIconHideable> getAllActivities() {
        return mInstalledApps;
    }

    public List<ApplicationIconHideable> getAppDrawerActivities() {
        return mInstalledApps.stream()
            .filter(applicationIconHideable -> !applicationIconHideable.isHidden())
            .collect(Collectors.toList());
    }

    public List<ApplicationIconCheckable> getCheckableActivities() {
        return mInstalledApps.stream()
            .map(ApplicationIconCheckable::new)
            .collect(Collectors.toList());
    }

    public List<ApplicationIconHideable> getActivitiesForPackage(String packageName) {
        return getInstalledAppsImpl(packageName);
    }

    public List<ApplicationIconHideable> getActivitiesForPackageFast(String packageName) {
        if (mPackageToApps.containsKey(packageName)) {
            return mPackageToApps.get(packageName);
        }
        return getInstalledAppsImpl(packageName);
    }

    public List<AppWidgetProviderInfo> getWidgets() {
        return mPackageToClassToAppWidgetProvider.values()
            .stream()
            .flatMap(
                (Function<Map<String, AppWidgetProviderInfo>, Stream<AppWidgetProviderInfo>>) map ->
                    map.values().stream())
            .collect(Collectors.toList());
    }

    public List<ShortcutWrapper> getPackageShortcuts(String packageName) {
        return
            mPackageToShortcutInfos.containsKey(packageName) ?
            mPackageToShortcutInfos.get(packageName) :
            new ArrayList<>();
    }

    private void publishEvent(String packageName, PackageModifiedEvent.Modification modification) {
        EventBus.getDefault().post(new PackageModifiedEvent(packageName, modification));
        log("Package " + packageName + " " + modification.name());
    }

    private void log(String message) {
        LifecycleLogUtils.logEvent(LifecycleLogUtils.LogType.LIFECYCLE_CHANGE, message);
    }
}
