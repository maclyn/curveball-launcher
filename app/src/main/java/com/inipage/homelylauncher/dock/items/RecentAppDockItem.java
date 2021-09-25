package com.inipage.homelylauncher.dock.items;

import android.content.Context;
import android.graphics.Bitmap;
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

    public RecentAppDockItem(ContextualAppFetcher.SuggestionApp suggestionApp) {
        mSuggestionApp = suggestionApp;
    }

    @Override
    public void onAttach() {
        mHost.showHostedItem();
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
        @Nullable final Host host = mHost;
        if (host == null) {
            return super.getTint();
        }
        @Nullable final Context context = getContext();
        if (context == null) {
            return super.getTint();
        }
        return IconColorCache.getInstance().getColorForBitmap(
            context,
            getBitmap(),
            host::tintLoaded);
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
        @Nullable final Host host = mHost;
        if (host == null) {
            return null;
        }
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
                        host.hideHostedItem();
                    }

                    @Override
                    public void onDismiss() {}
                });
        };
    }

    @Override
    public long getBasePriority() {
        return DockItemPriorities.PRIORITY_RECENT_APP.getPriority();
    }

    @Override
    public long getSubPriority() {
        return mSuggestionApp.getDurationUsed();
    }
}
