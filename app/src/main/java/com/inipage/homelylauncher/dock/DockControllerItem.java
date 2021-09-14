package com.inipage.homelylauncher.dock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

public abstract class DockControllerItem {

    public interface ItemCallback {
        void hideMe();
    }

    public interface TintCallback {
        void onTintLoaded(int tintColor);
    }

    public interface LoadingCallback {
        void onLoaded();
    }

    /**
     * @return True if loading has completed, false if loading is ongoing.
     */
    public boolean startLoading(Context context, LoadingCallback controllerHandle) {
        controllerHandle.onLoaded();
        return true;
    }

    public abstract boolean isActive(Context context);

    public int getIcon() {
        return 0;
    }

    @Nullable
    public Bitmap getBitmap(Context context) {
        return null;
    }

    @Nullable
    public Drawable getDrawable(Context context) {
        return null;
    }

    @Nullable
    public String getLabel(Context context) {
        return null;
    }

    @Nullable
    public String getSecondaryLabel(Context context) {
        return null;
    }

    @ColorInt
    public int getTint(Context context, TintCallback tintCallback) {
        return Color.WHITE;
    }

    public abstract Runnable getAction(View view, Context context);

    @Nullable
    public Runnable getSecondaryAction(View view, Context context, ItemCallback handle) {
        return null;
    }

    public abstract long getBasePriority();

    public long getSubPriority() {
        return 0L;
    }
}
