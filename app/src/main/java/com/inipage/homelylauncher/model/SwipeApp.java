package com.inipage.homelylauncher.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;

import com.inipage.homelylauncher.caches.AppLabelCache;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.pocket.RowContent;

public class SwipeApp extends RowContent {

    private final Pair<String, String> mPair;

    public SwipeApp(Pair<String, String> pair) {
        mPair = pair;
    }

    public SwipeApp(String packageName, String className) {
        mPair = new Pair<>(packageName, className);
    }

    public Pair<String, String> getComponent() {
        return mPair;
    }

    @Override
    public String getLabel(Context c) {
        return AppLabelCache.getInstance(c).getLabel(mPair);
    }

    @Override
    public Bitmap getIcon(Context context) {
        return IconCacheSync.getInstance(context).getActivityIcon(mPair.first, mPair.second);
    }
}
