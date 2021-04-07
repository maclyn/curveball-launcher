package com.inipage.homelylauncher.model;

import android.content.Context;

import com.inipage.homelylauncher.caches.AppLabelCache;
import com.inipage.homelylauncher.drawer.FastScrollable;

public class ApplicationIcon extends FastScrollable {
    private final String label;
    private final String packageName;
    private final String activityName;
    private final int hashCode;

    public ApplicationIcon(String packageName, String activityName, Context labelContext) {
        this(
            packageName,
            activityName,
            AppLabelCache.getInstance(labelContext).getLabel(packageName, activityName));
    }

    public ApplicationIcon(String packageName, String activityName, String label) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
        this.hashCode = (packageName.hashCode() * 37) + activityName.hashCode();
    }

    public ApplicationIcon(String name) {
        this(name, name, name);
    }

    @Override
    public String getName() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getActivityName() {
        return activityName;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
