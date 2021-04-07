package com.inipage.homelylauncher.caches;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.util.Pair;

import com.inipage.homelylauncher.model.ApplicationIcon;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Label cache for applications.
 */
public class AppLabelCache {
    private static AppLabelCache s_INSTANCE;

    private final Map<Pair<String, String>, String> mLabelMap;
    private final Map<String, Set<Pair<String, String>>> mPackageNameToLabelKeyMap;
    private final WeakReference<PackageManager> mPackageManagerRef;

    private AppLabelCache(Context c) {
        mLabelMap = new HashMap<>();
        mPackageNameToLabelKeyMap = new HashMap<>();
        mPackageManagerRef = new WeakReference<>(c.getPackageManager());
    }

    public static AppLabelCache getInstance(Context c) {
        if (s_INSTANCE == null || s_INSTANCE.mPackageManagerRef.get() == null) {
            s_INSTANCE = new AppLabelCache(c);
        }
        return s_INSTANCE;
    }

    public String getLabel(ApplicationIcon applicationIcon) {
        return getLabel(applicationIcon.getPackageName(), applicationIcon.getActivityName());
    }

    public String getLabel(String packageName, String activityName) {
        return getLabel(new Pair<>(packageName, activityName));
    }

    public String getLabel(Pair<String, String> component) {
        if (mLabelMap.containsKey(component)) {
            return mLabelMap.get(component);
        }

        @Nullable PackageManager pm = mPackageManagerRef.get();
        if (pm == null) {
            return component.first;
        }
        final ComponentName cm = new ComponentName(component.first, component.second);
        try {
            ActivityInfo info = pm.getActivityInfo(cm, 0);
            mLabelMap.put(component, info.loadLabel(pm).toString());
        } catch (PackageManager.NameNotFoundException e) {
            mLabelMap.put(component, component.first);
        }
        if (mPackageNameToLabelKeyMap.containsKey(component.first)) {
            mPackageNameToLabelKeyMap.get(component.first).add(component);
        } else {
            Set<Pair<String, String>> componentSet = new HashSet<>();
            componentSet.add(component);
            mPackageNameToLabelKeyMap.put(component.first, componentSet);
        }
        return mLabelMap.get(component);
    }

    public void clearCache() {
        mLabelMap.clear();
        mPackageNameToLabelKeyMap.clear();
    }

    public void clearCacheForPackage(String changedPackage) {
        if (!mPackageNameToLabelKeyMap.containsKey(changedPackage)) {
            return;
        }
        for (Pair<String, String> key : mPackageNameToLabelKeyMap.get(changedPackage)) {
            mLabelMap.remove(key);
        }
    }
}
