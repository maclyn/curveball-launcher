package com.inipage.homelylauncher.drawer;

import android.app.Activity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Preconditions;
import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.model.GridItem;
import com.inipage.homelylauncher.state.LayoutEditingSingleton;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.utils.Prewarmer;
import com.inipage.homelylauncher.views.AppPopupMenu;
import com.inipage.homelylauncher.views.DecorViewDragger;
import com.inipage.homelylauncher.views.DecorViewManager;

import org.apache.commons.collections4.trie.PatriciaTrie;
import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Renders application icons and performs searches for the app list.
 */
public class ApplicationIconAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static class AdapterElement {

        @Nullable
        private final ApplicationIconHideable mUnderlyingApp;
        private final char mUnderlyingIndex;
        private final int mElementType;

        private AdapterElement(@Nullable ApplicationIconHideable icon, char underlyingIndex, int elementType) {
            mUnderlyingApp = icon;
            mUnderlyingIndex = underlyingIndex;
            mElementType = elementType;
        }

        static AdapterElement createTopElement() {
            return new AdapterElement(null, '?', ITEM_VIEW_TYPE_TOP_HEADER);
        }

        static AdapterElement createHeaderElement(char index) {
            return new AdapterElement(null, index, ITEM_VIEW_TYPE_LETTER_HEADER);
        }

        static AdapterElement createAppElement(ApplicationIconHideable icon) {
            return new AdapterElement(icon, '?', ITEM_VIEW_TYPE_APP);
        }

        public ApplicationIconHideable getUnderlyingApp() {
            Preconditions.checkState(mElementType == ITEM_VIEW_TYPE_APP);
            return Preconditions.checkNotNull(mUnderlyingApp);
        }

        public char getUnderlyingIndex() {
            Preconditions.checkState(mElementType == ITEM_VIEW_TYPE_LETTER_HEADER);
            return mUnderlyingIndex;
        }

        public int getElementType() {
            return mElementType;
        }
    }

    private enum Mode {
        SHOWING_ALL,
        SEARCH_RESULTS
    }

    private static final int ITEM_VIEW_TYPE_APP = 1;
    private static final int ITEM_VIEW_TYPE_TOP_HEADER = 2;
    private static final int ITEM_VIEW_TYPE_LETTER_HEADER = 3;

    private static final int HEADER_ITEM_ID = 0;

    private final List<ApplicationIconHideable> mApps;
    private final PatriciaTrie<ApplicationIconHideable> mAppsTree = new PatriciaTrie<>();
    private final Activity mActivity;

    private List<AdapterElement> mElements;
    @Nullable
    private List<ApplicationIconHideable> mLastSearchResult;
    private Mode mMode;

    public ApplicationIconAdapter(Activity activity) {
        this.mApps = AppInfoCache.get().getAppDrawerActivities();
        for (ApplicationIconHideable icon : mApps) {
            mAppsTree.put(icon.getName().toLowerCase(Locale.getDefault()), icon);
            Prewarmer.getInstance().prewarm(() ->
                IconCacheSync.getInstance(activity).getActivityIcon(
                    icon.getPackageName(), icon.getActivityName()));
        }
        this.mActivity = activity;
        this.mMode = Mode.SHOWING_ALL;
        rebuild(null);
    }

    /**
     * Filter the adapter to only show relevant search results.
     * @param query The search.
     * @return Whether the search returns results.
     */
    public boolean performSearch(String query) {
        mMode = Mode.SEARCH_RESULTS;
        rebuild(query);
        notifyDataSetChanged();
        return !mElements.isEmpty();
    }

    public void leaveSearch() {
        if (mMode != Mode.SEARCH_RESULTS) {
            return;
        }
        mMode = Mode.SHOWING_ALL;
        mLastSearchResult = null;
        rebuild(null);
        notifyDataSetChanged();
    }

    public void spliceInPackageChanges(
        String changedPackage,
        List<ApplicationIconHideable> activities) {
        // TODO: THIS IS ALL WRONG NOW

        // This can't leverage rebuild() as we do for searching/init(), since we want the removed
        // elements to cleanly animate away

        /*
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

         */
    }

    public void hideApp(ApplicationIcon ai) {
        // TODO: THIS IS ALL WRONG NOW

        /*
        final int index = mApps.indexOf(new ApplicationIconHideable(mActivity, ai.getPackageName(), ai.getActivityName(), false));
        if (index != -1) {
            mApps.remove(index);
            notifyItemRemoved(index + (mRenderHeaders ? 1 : 0));
        }
        if (mRenderHeaders) {
            notifyItemChanged(0);
        }

         */
    }

    public ApplicationIcon getFirstApp() {
        Preconditions.checkState(mMode == Mode.SEARCH_RESULTS);
        return mElements.get(0).getUnderlyingApp();
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NotNull ViewGroup viewGroup, int type) {
        switch (type) {
            case ITEM_VIEW_TYPE_APP: {
                final ApplicationIconLayout rootView =
                    (ApplicationIconLayout)
                        LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.application_icon, viewGroup, false);
                return new AppIconHolder(rootView);
            }
            case ITEM_VIEW_TYPE_LETTER_HEADER: {
                final View rootView =
                        LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.letter_header_row, viewGroup, false);
                return new LetterHolder(rootView);
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
            final LetterHolder letterHolder = (LetterHolder) holder;
            letterHolder.title.setText(String.valueOf(mElements.get(i).getUnderlyingIndex()));
            return;
        }

        final AppIconHolder viewHolder = (AppIconHolder) holder;
        final ApplicationIcon ai = mElements.get(i).getUnderlyingApp();
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
        switch (getItemViewType(position)) {
            case ITEM_VIEW_TYPE_TOP_HEADER:
                return HEADER_ITEM_ID;
            case ITEM_VIEW_TYPE_LETTER_HEADER:
                return mElements.get(position).getUnderlyingIndex();
            default:
            case ITEM_VIEW_TYPE_APP:
                return mElements.get(position).getUnderlyingApp().hashCode();
        }
    }

    @Override
    public int getItemCount() {
        return mElements.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mElements.get(position).getElementType();
    }

    /**
     * Use mode and search query to rebuild the elements that'll be rendered.
     */
    private void rebuild(@Nullable String searchQuery) {
        mElements = new ArrayList<>();
        if (searchQuery == null) {
            mLastSearchResult = null;
            mElements.add(AdapterElement.createTopElement());
            char currentScrollableField = '@'; // never a scrollable field, i guess
            for (ApplicationIconHideable app : mApps) {
                if (app.getScrollableField() != currentScrollableField) {
                    mElements.add(AdapterElement.createHeaderElement(app.getScrollableField()));
                    currentScrollableField = app.getScrollableField();
                }
                mElements.add(AdapterElement.createAppElement(app));
            }
            return;
        }

        // TODO: Make searches work
        /*

        final String netQuery = query.toLowerCase(Locale.getDefault());
        // 1 - Get the matching apps by first few characters
        final SortedMap<String, ApplicationIconHideable> searchMap = mAppsTree.prefixMap(netQuery);
        final List<ApplicationIconHideable> result = new ArrayList<>(searchMap.values());
        // 2 - Search through everything else by checking contains()
        for (ApplicationIconHideable icon : mCachedApps) {
            if (icon.isHidden() || searchMap.containsValue(icon)) {
                continue;
            }
            if (icon.getName().toLowerCase(Locale.getDefault()).contains(netQuery)) {
                result.add(icon);
            }
        }
        if (mLastSearchResult != null && mLastSearchResult.equals(result)) {
            return;
        }
        mLastSearchResult = result;
        updateDrawerAdapter(result);
        searchApps(s.toString());

         */
    }

    public static class TopHeaderHolder extends AlphaAwareViewHolder {
        TextView installCount;

        public TopHeaderHolder(View view) {
            super(view);
            this.installCount = ViewCompat.requireViewById(view, R.id.installed_apps_count);
        }
    }

    public static class LetterHolder extends AlphaAwareViewHolder {
        TextView title;

        public LetterHolder(View view) {
            super(view);
            this.title = ViewCompat.requireViewById(view, R.id.letter_header_text);
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
