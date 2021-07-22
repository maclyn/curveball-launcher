package com.inipage.homelylauncher.drawer;

import android.app.Activity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
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

public class ApplicationIconAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private class GroupedApps {

        private final List<ApplicationIconHideable> mGroupedApps;
        private final char mGroupingCharacter;

        private GroupedApps() {
            mGroupedApps = null;
            mGroupingCharacter = 'a';
        }
    }

    private static final int ITEM_VIEW_TYPE_APP = 1;
    private static final int ITEM_VIEW_TYPE_TOP_HEADER = 2;
    private static final int ITEM_VIEW_TYPE_LETTER_HEADER = 3;

    private static final int HEADER_ITEM_ID = 0;

    private final List<ApplicationIconHideable> mApps;
    private final Activity mActivity;
    private final boolean mRenderHeaders;

    public ApplicationIconAdapter(
            List<ApplicationIconHideable> apps,
            Activity activity,
            boolean renderHeaders) {
        this.mApps = apps;
        this.mActivity = activity;
        this.mRenderHeaders = renderHeaders;
    }

    public List<ApplicationIconHideable> getApps() {
        return mApps;
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int type) {
        switch (type) {
            case ITEM_VIEW_TYPE_APP: {
                final ApplicationIconLayout rootView =
                    (ApplicationIconLayout)
                        LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.application_icon, viewGroup, false);
                return new AppIconHolder(rootView);
            }
            case ITEM_VIEW_TYPE_LETTER_HEADER: {
                return null;
            }
            case ITEM_VIEW_TYPE_TOP_HEADER:
            default: {
                final View rootView =
                        LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.application_header_row, viewGroup, false);
                return new TopHeaderHolder(rootView);
            }
        }
    }

    // Set up specific customIcon with data
    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int i) {
        final int itemViewType = getItemViewType(i);
        if (itemViewType == ITEM_VIEW_TYPE_TOP_HEADER) {
            final TopHeaderHolder headerHolder = (TopHeaderHolder) holder;
            headerHolder.installCount.setText(String.valueOf(mApps.size()));
            return;
        }
        if (itemViewType == ITEM_VIEW_TYPE_LETTER_HEADER) {
            return;
        }

        final AppIconHolder viewHolder = (AppIconHolder) holder;
        final ApplicationIcon ai = mApps.get(i - (mRenderHeaders ? 1 : 0));
        final View mainView = viewHolder.mainView;
        viewHolder.title.setText(ai.getName());
        viewHolder.icon.setBitmap(
            IconCacheSync.getInstance(mActivity).getActivityIcon(
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

                DecorViewManager.get(mActivity).detachAllViews();
                LayoutEditingSingleton.getInstance().setEditing(true);
                final AppViewHolder appViewHolder =
                    new AppViewHolder(
                        mActivity,
                        GridItem.getNewAppItem(ai));
                final View draggingView = viewHolder.icon;
                DecorViewDragger.get(mActivity).startDrag(
                    draggingView, appViewHolder, true, rawX, rawY);
            }

            @Override
            public void onDragEvent(MotionEvent event) {
                DecorViewDragger.get(mActivity).forwardTouchEvent(event);
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return position == 0 && mRenderHeaders ?
               HEADER_ITEM_ID :
               mApps.get(position - (mRenderHeaders ? 1 : 0)).hashCode();
    }

    @Override
    public int getItemCount() {
        return mApps.size() + (mRenderHeaders ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 && mRenderHeaders ? ITEM_VIEW_TYPE_TOP_HEADER : ITEM_VIEW_TYPE_APP;
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
            notifyItemRemoved(realIdx + (mRenderHeaders ? 1 : 0));
            removedCount++;
        }

        // Splice in the new activities in-place
        int addedCount = 0;
        for (ApplicationIconHideable activity : activities) {
            final int insertionIdx =
                Math.abs(Collections.binarySearch(
                    mApps, activity, FastScrollable.getComparator()));
            if (insertionIdx >= mApps.size()) {
                mApps.add(activity);
            } else {
                mApps.add(insertionIdx, activity);
            }
            notifyItemInserted(insertionIdx + (mRenderHeaders ? 1 : 0));
            addedCount++;
        }
        if (removedCount != addedCount && mRenderHeaders) {
            notifyItemChanged(0);
        }
    }

    public void hideApp(ApplicationIcon ai) {
        final int index = mApps.indexOf(new ApplicationIconHideable(mActivity, ai.getPackageName(), ai.getActivityName(), false));
        if (index != -1) {
            mApps.remove(index);
            notifyItemRemoved(index + (mRenderHeaders ? 1 : 0));
        }
        if (mRenderHeaders) {
            notifyItemChanged(0);
        }
    }

    public static class TopHeaderHolder extends AlphaAwareViewHolder {
        TextView installCount;

        public TopHeaderHolder(View view) {
            super(view);
            this.installCount = ViewCompat.requireViewById(view, R.id.installed_apps_count);
        }
    }

    public static class LetterHolder extends AlphaAwareViewHolder {

        public LetterHolder(View view) {
            super(view);
        }
    }

    public static class AppIconHolder extends AlphaAwareViewHolder {
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

    public static class AlphaAwareViewHolder extends RecyclerView.ViewHolder {

        private final int[] tmp = new int[2];
        View view;

        public AlphaAwareViewHolder(View view) {
            super(view);
            this.view = view;
        }

        public void applyAlpha(RecyclerView container) {
            container.getLocationOnScreen(tmp);
            final float containerTop = tmp[1];
            view.getLocationOnScreen(tmp);
            final float itemTop = tmp[1];
            final float itemHeight = view.getHeight();
            if (itemTop < containerTop) { // itemTop < containerTop --> falling off top
                final float amountOffScreen = containerTop - itemTop;
                view.setAlpha(1 - (amountOffScreen / itemHeight));
            } else { // itemTop > containerTop --> falling off bottom
                final float bottomOfContainer = containerTop + container.getHeight();
                view.setAlpha((bottomOfContainer - itemTop) / itemHeight);
            }
        }
    }
}
