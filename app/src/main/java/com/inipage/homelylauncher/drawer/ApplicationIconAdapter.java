package com.inipage.homelylauncher.drawer;

import android.app.Activity;
import android.util.Pair;
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
import com.inipage.homelylauncher.utils.LifecycleLogUtils;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * Renders application icons and performs searches for the app list.
 */
public class ApplicationIconAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Delegate {

        void enterSearchMode(View v);

        void showOptionsMenu(View v);
    }

    private static class AdapterElement {

        @Nullable
        private final ApplicationIconHideable mUnderlyingApp;
        private final char mUnderlyingHeaderChar;
        private final int mElementType;

        private AdapterElement(@Nullable ApplicationIconHideable icon, char underlyingHeaderChar, int elementType) {
            mUnderlyingApp = icon;
            mUnderlyingHeaderChar = underlyingHeaderChar;
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

        public char getUnderlyingHeaderChar() {
            Preconditions.checkState(mElementType == ITEM_VIEW_TYPE_LETTER_HEADER);
            return mUnderlyingHeaderChar;
        }

        public int getElementType() {
            return mElementType;
        }

        @NonNull
        @Override
        public String toString() {
            return mElementType == ITEM_VIEW_TYPE_TOP_HEADER ?
                   "Topmost header" :
                   (mElementType == ITEM_VIEW_TYPE_LETTER_HEADER ?
                        "Letter header for " + getUnderlyingHeaderChar() :
                            "App element for " + getUnderlyingApp().toString());
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof AdapterElement)) {
                return false;
            }
            final AdapterElement other = (AdapterElement) obj;
            if (getElementType() != other.getElementType()) {
                return false;
            }
            switch (mElementType) {
                case ITEM_VIEW_TYPE_APP:
                    return getUnderlyingApp().equals(other.getUnderlyingApp());
                case ITEM_VIEW_TYPE_LETTER_HEADER:
                    return getUnderlyingHeaderChar() == other.getUnderlyingHeaderChar();
                case ITEM_VIEW_TYPE_TOP_HEADER:
                default:
                    return true;
            }
        }
    }

    private static final Comparator<AdapterElement> ELEMENT_COMPARATOR = new Comparator<AdapterElement>() {
        @Override
        public int compare(AdapterElement o1, AdapterElement o2) {
            if (o1.getElementType() == ITEM_VIEW_TYPE_TOP_HEADER && o2.getElementType() != ITEM_VIEW_TYPE_TOP_HEADER) {
                return -1;
            }
            if (o1.getElementType() != ITEM_VIEW_TYPE_TOP_HEADER && o2.getElementType() == ITEM_VIEW_TYPE_TOP_HEADER) {
                return 1;
            }
            // Since max(count(elements w/ TYPE_TOP)) == 1), we don't have to consider it from here
            // on out; just focus on LETTER_HEADERS and APPS

            // Two apps -- easy, compare app names
            if (o1.getElementType() == ITEM_VIEW_TYPE_APP && o2.getElementType() == ITEM_VIEW_TYPE_APP) {
                return FastScrollable.getComparator().compare(o1.getUnderlyingApp(), o2.getUnderlyingApp());
            }

            // Two headers -- easy, compare character
            if (o1.getElementType() == ITEM_VIEW_TYPE_LETTER_HEADER && o2.getElementType() == ITEM_VIEW_TYPE_LETTER_HEADER) {
                return FastScrollable.getCharComparator().compare(o1.getUnderlyingHeaderChar(), o2.getUnderlyingHeaderChar());
            }

            // lhs = header, rhs = app
            if (o1.getElementType() == ITEM_VIEW_TYPE_LETTER_HEADER && o2.getElementType() == ITEM_VIEW_TYPE_APP) {
                char header = o1.getUnderlyingHeaderChar();
                char appHeader = o2.getUnderlyingApp().getScrollableField();
                if (header == FastScrollable.NUMERIC) {
                    return -1; // header goes first -> top of list
                }
                if (header == appHeader) {
                    return -1; // header goes first -> correct header for this particular app
                }
                return FastScrollable.getCharComparator().compare(header, appHeader);
            }

            // lhs = app, rhs = header
            if (o1.getElementType() == ITEM_VIEW_TYPE_APP && o2.getElementType() == ITEM_VIEW_TYPE_LETTER_HEADER) {
                char appHeader = o1.getUnderlyingApp().getScrollableField();
                char header = o2.getUnderlyingHeaderChar();
                if (header == FastScrollable.NUMERIC) {
                    return 1;
                }
                if (header == appHeader) {
                    return -1;
                }
                return FastScrollable.getCharComparator().compare(appHeader, header);
            }
            return 0;
        }
    };

    private enum Mode {
        SHOWING_ALL,
        SEARCH_RESULTS
    }

    private static final int ITEM_VIEW_TYPE_APP = 1;
    private static final int ITEM_VIEW_TYPE_TOP_HEADER = 2;
    private static final int ITEM_VIEW_TYPE_LETTER_HEADER = 3;

    private static final int HEADER_ITEM_ID = 0;

    private final Delegate mDelegate;
    private final List<ApplicationIconHideable> mApps;
    private final PatriciaTrie<ApplicationIconHideable> mAppsTree = new PatriciaTrie<>();
    private final Activity mActivity;

    private List<AdapterElement> mElements;
    @Nullable
    private List<ApplicationIconHideable> mLastSearchResult;
    private Mode mMode;

    public ApplicationIconAdapter(Delegate delegate, Activity activity) {
        this.mApps = AppInfoCache.get().getAppDrawerActivities();
        for (ApplicationIconHideable icon : mApps) {
            mAppsTree.put(icon.getName().toLowerCase(Locale.getDefault()), icon);
            Prewarmer.getInstance().prewarm(() ->
                IconCacheSync.getInstance(activity).getActivityIcon(
                    icon.getPackageName(), icon.getActivityName()));
        }
        this.mDelegate = delegate;
        this.mActivity = activity;
        this.mMode = Mode.SHOWING_ALL;
        rebuild(null);
    }

    /**
     * Filter the adapter to only show relevant search results.
     * @param query The search.
     * @return Whether the search returns results.
     */
    public synchronized boolean performSearch(String query) {
        mMode = Mode.SEARCH_RESULTS;
        if (rebuild(query)) {
            notifyDataSetChanged();
        }
        return !mElements.isEmpty();
    }

    public synchronized void leaveSearch() {
        if (mMode != Mode.SEARCH_RESULTS) {
            return;
        }
        mMode = Mode.SHOWING_ALL;
        mLastSearchResult = null;
        rebuild(null);
        notifyDataSetChanged();
    }

    public synchronized void spliceInPackageChanges(
        String changedPackage,
        List<ApplicationIconHideable> activities) {
        // This only functions when we're showing all apps
        Preconditions.checkState(mMode == Mode.SHOWING_ALL);

        // This can't leverage rebuild() as we do for searching/init(), since we want the removed
        // elements to cleanly animate away

        // Kill the existing items in mApps and mElements with the package name
        List<Integer> toRemoveFromApps = new ArrayList<>();
        for (int i = 0; i < mApps.size(); i++) {
            if (mApps.get(i).getPackageName().equals(changedPackage)) {
                toRemoveFromApps.add(i);
            }
        }
        List<Integer> toRemoveFromElements = new ArrayList<>();
        for (int i = 0; i < mElements.size(); i++) {
            final AdapterElement element = mElements.get(i);
            if (element.getElementType() != ITEM_VIEW_TYPE_APP) {
                continue;
            }
            if (element.getUnderlyingApp().getPackageName().equals(changedPackage)) {
                toRemoveFromElements.add(i);
            }
        }
        int removedCount = 0;
        for (Integer idx : toRemoveFromApps) {
            final int realIdx = idx - removedCount;
            mAppsTree.remove(mApps.get(realIdx).getName().toLowerCase(Locale.getDefault()));
            mApps.remove(realIdx);
            removedCount++;
        }
        int removedElementCount = 0;
        for (Integer idx : toRemoveFromElements) {
            final int realIdx = idx - removedElementCount;
            AdapterElement removedElement = mElements.remove(realIdx);
            LifecycleLogUtils.logEvent(LifecycleLogUtils.LogType.LOG, "Spliced out " + removedElement.toString() + " at " + realIdx);
            notifyItemRemoved(realIdx);
            removedElementCount++;
        }

        // Splice in the new activities in-place to mApps and mElements
        for (ApplicationIconHideable activity : activities) {
            final int appsInsertionIdx =
                Math.abs(Collections.binarySearch(
                    mApps, activity, FastScrollable.getComparator()) + 1);
            if (appsInsertionIdx >= mApps.size()) {
                mApps.add(activity);
            } else {
                mApps.add(appsInsertionIdx, activity);
            }
            mAppsTree.put(activity.getName().toLowerCase(Locale.getDefault()), activity);

            final AdapterElement newAppElement = AdapterElement.createAppElement(activity);
            final int elementsInsertionIdx = Math.abs(Collections.binarySearch(mElements, newAppElement, ELEMENT_COMPARATOR) + 1);
            if (elementsInsertionIdx >= mElements.size()) {
                mElements.add(newAppElement);
                notifyItemInserted(mElements.size() - 1);
            } else {
                mElements.add(elementsInsertionIdx, newAppElement);
                notifyItemInserted(elementsInsertionIdx);
            }
            LifecycleLogUtils.logEvent(
                LifecycleLogUtils.LogType.LOG,
                "Spliced in " + newAppElement.toString() + " at " + elementsInsertionIdx);
        }

        // Iterates through mElements, add any headers we need, and remove any we don't
        final Set<Pair<Integer, Character>> changeIndices = new HashSet<>();
        // When iterating through changeIndices, hitting removeCharacter = remove this index
        final char removeCharacter = '0';
        char characterOfLastHeader = '0';
        int netOffsetFromAddRemove = 0;
        for (int i = 1; i < mElements.size(); i++) {
            AdapterElement element = mElements.get(i);
            if (element.getElementType() == ITEM_VIEW_TYPE_LETTER_HEADER) {
                if (mElements.get(i - 1).getElementType() == ITEM_VIEW_TYPE_LETTER_HEADER) {
                    // We've seen two headers in a row; trim the one right before
                    changeIndices.add(new Pair<>(i - 1 + netOffsetFromAddRemove, removeCharacter));
                    netOffsetFromAddRemove--;
                }
                characterOfLastHeader = element.getUnderlyingHeaderChar();
            } else if (element.getElementType() == ITEM_VIEW_TYPE_APP) {
                char appScrollable = element.getUnderlyingApp().getScrollableField();
                if (appScrollable != characterOfLastHeader) {
                    // Drop a new header here
                    changeIndices.add(new Pair<>(i + netOffsetFromAddRemove, appScrollable));
                    netOffsetFromAddRemove++;
                }
            }
        }
        for (Pair<Integer, Character> change : changeIndices) {
            if (change.second == removeCharacter) {
                AdapterElement removedElement = mElements.remove((int) change.first);
                LifecycleLogUtils.logEvent(LifecycleLogUtils.LogType.LOG, "Spliced out " + removedElement.toString() + " at " + change.first);
                notifyItemRemoved(change.second);
            } else {
                final AdapterElement newElement = AdapterElement.createHeaderElement(change.second);
                mElements.add(change.first, newElement);
                LifecycleLogUtils.logEvent(LifecycleLogUtils.LogType.LOG, "Spliced in " + newElement.toString() + " at " + change.first);
                notifyItemInserted(change.first);
            }
        }

        // Refresh the top-most header
        notifyItemChanged(0);
    }

    public synchronized void hideApp(ApplicationIcon ai) {
        // We both remove the relevant app from mElements and mApps
        int position = -1;
        ApplicationIconHideable searchKey = new ApplicationIconHideable(mActivity, ai.getPackageName(), ai.getActivityName(), false);
        for (int i = 0; i < mElements.size(); i++) {
            if (mElements.get(i).getElementType() == ITEM_VIEW_TYPE_APP && mElements.get(i).getUnderlyingApp().equals(searchKey)) {
                position = i;
                break;
            }
        }
        if (position == -1) {
            return;
        }
        final boolean hasHeaderAbove = mElements.get(position - 1).getElementType() == ITEM_VIEW_TYPE_LETTER_HEADER;
        final boolean lastItemInSection = position == mElements.size() - 1 ?
          hasHeaderAbove :
          mElements.get(position + 1).getElementType() == ITEM_VIEW_TYPE_LETTER_HEADER;
        mElements.remove(position);
        mAppsTree.remove(searchKey.getName().toLowerCase(Locale.getDefault()));
        notifyItemRemoved(position);
        // Maybe remove the header too?
        if (lastItemInSection) {
            mElements.remove(position - 1);
            notifyItemRemoved(position - 1);
        }
        final int appsIndex = mApps.indexOf(searchKey);
        if (appsIndex != -1) {
            mApps.remove(appsIndex);
        }
    }

    public ApplicationIcon getFirstApp() {
        Preconditions.checkState(mMode == Mode.SEARCH_RESULTS);
        return mElements.get(0).getUnderlyingApp();
    }

    /**
     * Destructive test of adapter internal consistency.
     * @return Checks if a fresh rebuild (which will be "correct") matches the old state. After
     * this is called, the adapter *will* be consistent.
     */
    public boolean isConsistent_USE_FOR_DEBUGGING_ONLY() {
        final List<AdapterElement> old = new ArrayList<>(mElements);
        rebuild(null);
        notifyDataSetChanged();
        return old.equals(mElements);
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
    public synchronized void onBindViewHolder(final RecyclerView.ViewHolder holder, int i) {
        final int itemViewType = getItemViewType(i);
        if (itemViewType == ITEM_VIEW_TYPE_TOP_HEADER) {
            final TopHeaderHolder headerHolder = (TopHeaderHolder) holder;
            headerHolder.installCount.setText(String.valueOf(mApps.size()));
            headerHolder.enterSearchButton.setOnClickListener(mDelegate::enterSearchMode);
            headerHolder.showOptionsMenu.setOnClickListener(mDelegate::showOptionsMenu);
            return;
        }
        if (itemViewType == ITEM_VIEW_TYPE_LETTER_HEADER) {
            final LetterHolder letterHolder = (LetterHolder) holder;
            letterHolder.title.setText(String.valueOf(mElements.get(i).getUnderlyingHeaderChar()));
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
    public synchronized long getItemId(int position) {
        switch (getItemViewType(position)) {
            case ITEM_VIEW_TYPE_TOP_HEADER:
                return HEADER_ITEM_ID;
            case ITEM_VIEW_TYPE_LETTER_HEADER:
                return mElements.get(position).getUnderlyingHeaderChar();
            default:
            case ITEM_VIEW_TYPE_APP:
                return mElements.get(position).getUnderlyingApp().hashCode();
        }
    }

    @Override
    public synchronized int getItemCount() {
        return mElements.size();
    }

    @Override
    public synchronized int getItemViewType(int position) {
        return mElements.get(position).getElementType();
    }

    /**
     * Use mode and search query to rebuild the elements that'll be rendered.
     * @param query The search query. Pass {@code null} to reload the list.
     * @return True if mElements has been updated.
     */
    private boolean rebuild(@Nullable String query) {
        if (query == null) {
            mLastSearchResult = null;
            mElements = new ArrayList<>();
            mElements.add(AdapterElement.createTopElement());
            char currentScrollableField = '@'; // never a scrollable field, i guess
            for (ApplicationIconHideable app : mApps) {
                if (app.getScrollableField() != currentScrollableField) {
                    mElements.add(AdapterElement.createHeaderElement(app.getScrollableField()));
                    currentScrollableField = app.getScrollableField();
                }
                mElements.add(AdapterElement.createAppElement(app));
            }
            return true;
        }

        final String netQuery = query.toLowerCase(Locale.getDefault());
        // 1 - Get the matching apps by first few characters
        final SortedMap<String, ApplicationIconHideable> searchMap = mAppsTree.prefixMap(netQuery);
        final List<ApplicationIconHideable> result = new ArrayList<>(searchMap.values());
        // 2 - Search through everything else by checking contains()
        for (ApplicationIconHideable icon : mApps) {
            if (icon.isHidden() || searchMap.containsValue(icon)) {
                continue;
            }
            if (icon.getName().toLowerCase(Locale.getDefault()).contains(netQuery)) {
                result.add(icon);
            }
        }
        if (mLastSearchResult != null && mLastSearchResult.equals(result)) {
            return false;
        }
        mLastSearchResult = result;
        // 3 - Map these all to elements
        mElements = mLastSearchResult
            .parallelStream()
            .map(AdapterElement::createAppElement)
            .collect(Collectors.toList());
        return true;
    }

    public static class TopHeaderHolder extends AlphaAwareViewHolder {
        TextView installCount;
        View enterSearchButton;
        View showOptionsMenu;

        public TopHeaderHolder(View view) {
            super(view);
            this.installCount = ViewCompat.requireViewById(view, R.id.installed_apps_count);
            this.enterSearchButton = ViewCompat.requireViewById(view, R.id.search_box_button);
            this.showOptionsMenu = ViewCompat.requireViewById(view, R.id.bottom_sheet_settings_button);
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
