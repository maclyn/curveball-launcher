package com.inipage.homelylauncher.drawer;

import android.app.Activity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.model.GridItem;
import com.inipage.homelylauncher.state.LayoutEditingSingleton;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.views.AppPopupMenu;
import com.inipage.homelylauncher.views.DecorViewDragger;
import com.inipage.homelylauncher.views.DecorViewManager;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApplicationIconAdapter extends RecyclerView.Adapter<ApplicationIconAdapter.AppIconHolder> {

    private final List<ApplicationIconHideable> mApps;
    private final Activity mContext;

    public ApplicationIconAdapter(List<ApplicationIconHideable> apps, Activity activity) {
        this.mApps = apps;
        this.mContext = activity;
    }

    public List<ApplicationIconHideable> getApps() {
        return mApps;
    }

    @NotNull
    @Override
    public AppIconHolder onCreateViewHolder(ViewGroup viewGroup, int index) {
        final ApplicationIconLayout rootView =
            (ApplicationIconLayout)
                LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.application_icon, viewGroup, false);
        return new AppIconHolder(rootView);
    }

    // Set up specific customIcon with data
    @Override
    public void onBindViewHolder(final AppIconHolder viewHolder, int i) {
        final ApplicationIcon ai = mApps.get(i);
        final View mainView = viewHolder.mainView;
        viewHolder.title.setText(ai.getName());
        viewHolder.icon.setBitmap(
            IconCacheSync.getInstance(mContext).getActivityIcon(
                ai.getPackageName(), ai.getActivityName()));
        viewHolder.mainView.setClickable(true);
        viewHolder.mainView.setAlpha(1F);
        viewHolder.mainView.setOnClickListener(
            v -> InstalledAppUtils.launchApp(v, ai.getPackageName(), ai.getActivityName()));
        viewHolder.mainView.attachListener(new ApplicationIconLayout.Listener() {

            @Override
            public void onLongPress(final int startX, final int startY) {
                if (mainView.getParent() == null) {
                    return;
                }
                mainView.getParent().requestDisallowInterceptTouchEvent(true);
                mainView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                new AppPopupMenu().show(
                    startX,
                    startY,
                    false,
                    mainView.getContext(),
                    ai,
                    new AppPopupMenu.Listener() {
                        @Override
                        public void onRemove() {
                            EventBus.getDefault().post(new HideAppEvent(ai));
                        }

                        @Override
                        public void onDismiss() {}
                    });
            }

            @Override
            public void onDragStarted(final int rawX, final int rawY) {
                DebugLogUtils.needle(
                    DebugLogUtils.TAG_DRAG_OFFSET,
                    "LongPressAndDragHandler rawX=" + rawX + "; rawY=" + rawY);

                DecorViewManager.get(mContext).detachAllViews();
                LayoutEditingSingleton.getInstance().setEditing(true);
                final AppViewHolder appViewHolder =
                    new AppViewHolder(
                        mContext,
                        GridItem.getNewAppItem(ai));
                final View draggingView = viewHolder.icon;
                DecorViewDragger.get(mContext).startDrag(
                    draggingView, appViewHolder, true, rawX, rawY);
            }

            @Override
            public void onDragEvent(MotionEvent event) {
                DecorViewDragger.get(mContext).forwardTouchEvent(event);
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return mApps.get(position).hashCode();
    }

    @Override
    public int getItemCount() {
        return mApps.size();
    }

    public void spliceInPackageChanges(
        String changedPackage,
        List<ApplicationIconHideable> activities) {
        // Kill the existing items with the package name
        Set<Integer> toRemove = new HashSet<>();
        for (int i = 0; i < mApps.size(); i++) {
            if (mApps.get(i).getPackageName().equals(changedPackage)) {
                toRemove.add(i);
            }
        }
        int removedCount = 0;
        for (Integer idx : toRemove) {
            final int realIdx = idx - removedCount;
            mApps.remove(realIdx);
            notifyItemRemoved(realIdx);
            removedCount++;
        }

        // Splice in the new activities in-place
        for (ApplicationIconHideable activity : activities) {
            final int insertionIdx =
                Math.abs(Collections.binarySearch(
                    mApps, activity, FastScrollable.getComparator()));
            if (insertionIdx >= mApps.size()) {
                mApps.add(activity);
            } else {
                mApps.add(insertionIdx, activity);
            }
            notifyItemInserted(insertionIdx);
        }
    }

    public void hideApp(ApplicationIcon ai) {
        final int index = mApps.indexOf(new ApplicationIconHideable(mContext, ai.getPackageName(), ai.getActivityName(), false));
        if (index != -1) {
            mApps.remove(index);
            notifyItemRemoved(index);
        }
    }

    public static class AppIconHolder extends RecyclerView.ViewHolder {
        final int[] tmp = new int[2];
        ApplicationIconLayout mainView;
        BitmapView icon;
        TextView title;

        public AppIconHolder(ApplicationIconLayout mainView) {
            super(mainView);
            this.mainView = mainView;
            this.title = mainView.findViewById(R.id.app_icon_label);
            this.icon = mainView.findViewById(R.id.app_icon_image);
        }

        public void applyAlpha(RecyclerView container) {
            container.getLocationOnScreen(tmp);
            final float containerTop = tmp[1];
            mainView.getLocationOnScreen(tmp);
            final float itemTop = tmp[1];
            final float itemHeight = mainView.getHeight();
            if (itemTop < containerTop) { // itemTop < containerTop --> falling off top
                final float amountOffScreen = containerTop - itemTop;
                mainView.setAlpha(1 - (amountOffScreen / itemHeight));
            } else { // itemTop > containerTop --> falling off bottom
                final float bottomOfContainer = containerTop + container.getHeight();
                mainView.setAlpha((bottomOfContainer - itemTop) / itemHeight);
            }
        }
    }
}
