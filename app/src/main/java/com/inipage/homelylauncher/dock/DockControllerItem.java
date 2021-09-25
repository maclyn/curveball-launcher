package com.inipage.homelylauncher.dock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

public abstract class DockControllerItem {

    protected interface Host {

        Context getContext();

        void showHostedItem();

        void hideHostedItem();

        void tintLoaded(int color);
    }

    protected Host mHost;

    public void attach(Host host) {
        mHost = host;
        this.onAttach();
    }

    public void onAttach() {}

    public void detach() {
        this.onDetach();
        mHost = null;
    }

    /**
     * Use to cleanup any resources. Useful for items that are listening for hide/show and
     * need to unregister receivers, listeners, etc.
     */
    public void onDetach() {}

    public int getIcon() {
        return 0;
    }

    @Nullable
    public Bitmap getBitmap() {
        return null;
    }

    @Nullable
    public Drawable getDrawable() {
        return null;
    }

    @Nullable
    public String getLabel() {
        return null;
    }

    @Nullable
    public String getSecondaryLabel() {
        return null;
    }

    @ColorInt
    public int getTint() {
        return Color.WHITE;
    }

    public abstract Runnable getAction(View dockItemView);

    @Nullable
    public Runnable getSecondaryAction(View dockItemView) {
        return null;
    }

    public abstract long getBasePriority();

    public long getSubPriority() {
        return 0L;
    }

    @Nullable
    protected Context getContext() {
        if (mHost == null) {
            return null;
        }
        return mHost.getContext();
    }
}
