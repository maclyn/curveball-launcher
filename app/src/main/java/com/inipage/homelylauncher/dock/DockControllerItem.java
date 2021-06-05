package com.inipage.homelylauncher.dock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

public interface DockControllerItem {

    interface ControllerHandle {

        void hideMe();
    }

    boolean isActive(Context context);

    default int getIcon() {
        return 0;
    }

    @Nullable
    default Bitmap getBitmap(Context context) {
        return null;
    }

    default @Nullable
    String getLabel(Context context) {
        return null;
    }

    default @Nullable
    String getSecondaryLabel(Context context) {
        return null;
    }

    default @ColorInt
    int getTint(Context context, Callback callback) {
        return Color.WHITE;
    }

    Runnable getAction(View view, Context context);

    default @Nullable
    Runnable getSecondaryAction(View view, Context context, ControllerHandle handle) {
        return null;
    }

    long getBasePriority();

    default long getSubPriority() {
        return 0L;
    }

    interface Callback {
        void onTintLoaded(int tintColor);
    }
}
