package com.inipage.homelylauncher.dock.items;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.caches.IconColorCache;
import com.inipage.homelylauncher.dock.DockItemPriorities;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.DockItem;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.views.AppPopupMenu;

public class RecentAppDockItem extends SynchDockControllerItem {

    private final ContextualAppFetcher.SuggestionApp mSuggestionApp;

    public RecentAppDockItem(ContextualAppFetcher.SuggestionApp suggestionApp) {
        mSuggestionApp = suggestionApp;
    }

    @Override
    public boolean isActive(Context context) {
        return true;
    }

    @Nullable
    @Override
    public Bitmap getBitmap(Context context) {
        return IconCacheSync.getInstance(context).getActivityIcon(
            mSuggestionApp.getPackageName(), mSuggestionApp.getActivityName());
    }

    @Override
    public int getTint(Context context, TintCallback tintCallback) {
        return IconColorCache.getInstance().getColorForBitmap(
            context, getBitmap(context), tintCallback::onTintLoaded);
    }

    @Override
    public Runnable getAction(View view, Context context) {
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
    public Runnable getSecondaryAction(View view, Context context, ItemCallback itemCallback) {
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
                        itemCallback.hideMe();
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
