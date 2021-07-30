package com.inipage.homelylauncher.model;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    @Override
    public int hashCode() {
        return super.hashCode() * (mIsHidden ? 1 : -1);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof ApplicationIconHideable)) {
            return false;
        }
        return obj.hashCode() == this.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + (mIsHidden ? " (hidden)" : "");
    }
}
