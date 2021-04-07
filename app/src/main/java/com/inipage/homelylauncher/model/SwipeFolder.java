package com.inipage.homelylauncher.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.swipefolders.RowContent;

import java.util.List;

public class SwipeFolder extends RowContent {

    private String mDrawablePackage;
    private String mDrawableName;
    private String mTitle;
    private ImmutableList<SwipeApp> mApps;

    public SwipeFolder(
        String title,
        String drawablePackage,
        String drawableName,
        ImmutableList<SwipeApp> apps) {
        this.mTitle = title;
        this.mDrawablePackage = drawablePackage;
        this.mDrawableName = drawableName;
        mApps = apps;
    }

    public String getDrawableName() {
        return mDrawableName;
    }

    public String getDrawablePackage() {
        return mDrawablePackage;
    }

    public void setDrawable(String dRes, String dPkg) {
        this.mDrawableName = dRes;
        this.mDrawablePackage = dPkg;
    }

    public ImmutableList<SwipeApp> getShortcutApps() {
        return mApps;
    }

    public void addApp(Pair<String, String> app) {
        ImmutableList.Builder<SwipeApp> replacement = new ImmutableList.Builder<>();
        replacement.addAll(mApps);
        replacement.add(new SwipeApp(app));
        mApps = replacement.build();
    }

    public void replaceApps(List<SwipeApp> replacement) {
        this.mApps = ImmutableList.copyOf(replacement);
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    @Override
    public String getLabel(Context unused) {
        return mTitle;
    }

    @Override
    public Bitmap getIcon(Context context) {
        return IconCacheSync.getInstance(context).getNamedResource(mDrawablePackage, mDrawableName);
    }
}
