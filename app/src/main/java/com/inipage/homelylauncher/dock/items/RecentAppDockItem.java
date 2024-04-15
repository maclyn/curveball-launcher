package com.inipage.homelylauncher.dock.items;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.caches.IconColorCache;
import com.inipage.homelylauncher.dock.DockControllerItem;
import com.inipage.homelylauncher.dock.DockItemPriorities;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.views.AppPopupMenu;

/**
 * Renders a recent app on the dock.
 */
public class RecentAppDockItem extends DockControllerItem {

    private final ContextualAppFetcher.SuggestionApp mSuggestionApp;
    private int mTintColor = Color.WHITE;

    public RecentAppDockItem(ContextualAppFetcher.SuggestionApp suggestionApp) {
        mSuggestionApp = suggestionApp;
    }

    @Override
    public void onAttach() {
        showSelf();
    }

    @Nullable
    @Override
    public Bitmap getBitmap() {
        @Nullable final Context context = getContext();
        if (context == null) {
            return null;
        }
        return IconCacheSync.getInstance(context).getActivityIcon(
            mSuggestionApp.getPackageName(), mSuggestionApp.getActivityName());
    }

    @Override
    public int getTint() {
        if (mTintColor != Color.WHITE) {
            return mTintColor;
        }
        @Nullable final Context context = getContext();
        if (context == null) {
            return mTintColor;
        }
        return IconColorCache.getInstance().getColorForBitmap(
            context,
            getBitmap(),
            color -> {
                mTintColor = color;
                tintLoaded(color);
            });
    }

    @Override
    public Runnable getAction(View view) {
        return () -> {
            final int[] out = new int[2];
            view.getLocationOnScreen(out);
            InstalledAppUtils.launchApp(
                out[0],
                out[1],
                view.getWidth(),
                view.getWidth(),
                view.getContext(),
                mSuggestionApp.getPackageName(),
                mSuggestionApp.getActivityName());
        };
    }

    @Nullable
    @Override
    public Runnable getSecondaryAction(View view) {
        @Nullable final Context context = getContext();
        if (context == null) {
            return null;
        }
        return () -> {
            ApplicationIcon applicationIcon = new ApplicationIcon(
                mSuggestionApp.getPackageName(), mSuggestionApp.getActivityName(), context);
            final int[] out = new int[2];
            view.getLocationOnScreen(out);
            final int x = out[0] + (view.getWidth() / 2);
            final int y = out[1] + (view.getHeight() / 2);
            new AppPopupMenu().show(
                x,
                y,
                true,
                context,
                applicationIcon,
                new AppPopupMenu.Listener() {
                    @Override
                    public void onRemove() {
                        DatabaseEditor.get().addDockPreference(
                            DockItem.createHiddenItem(
                                applicationIcon.getPackageName(), applicationIcon.getActivityName()));
                        hideSelf();
                    }

                    @Override
                    public void onDismiss() {}
                });
        };
    }

    @Override
    public int getBasePriority() {
        return DockItemPriorities.PRIORITY_RECENT_APP.getPriority();
    }

    @Override
    public int getSubPriority() {
        return (int) (mSuggestionApp.getDurationUsed() / 1000);
    }
}
