package com.inipage.homelylauncher.model;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;

public class ApplicationIconHideable extends ApplicationIcon {

    private boolean mIsHidden;

    public ApplicationIconHideable(
        LauncherActivityInfo app,
        Context context,
        boolean hiddenLocally) {
        this(
            context,
            app.getComponentName().getPackageName(),
            app.getComponentName().getClassName(),
            hiddenLocally);
    }

    public ApplicationIconHideable(
        Context context, String packageName, String className, boolean hiddenLocally) {
        super(packageName, className, context);
        this.mIsHidden = hiddenLocally;
    }

    public boolean isHidden() {
        return mIsHidden;
    }

    public void setHidden(boolean isHidden) {
        this.mIsHidden = isHidden;
    }
}
