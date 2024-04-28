package com.inipage.homelylauncher.drawer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.util.Pair;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.base.Preconditions;
import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.FontCacheSync;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.model.ClassicGridItem;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.state.LayoutEditingSingleton;
import com.inipage.homelylauncher.utils.Constants;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.utils.InstalledAppUtils.AppLaunchSource;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * Renders application icons and performs searches for the app list.
 */
public class AppDrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Delegate {

        void enterFastScrollMode(View v);

        void scrollToIndex(int idx);

        int getFirstIndexOnScreen();

        int getLastIndexOnScreen();

        int getTotalCount();

        void setItemAnimator(RecyclerView.ItemAnimator animator);
    }

    private static class AdapterElement {

        @Nullable
        private final ApplicationIconHideable mUnderlyingApp;
        private final char mUnderlyingHeaderChar;
        private final int mSpacingIndex;
        private final int mElementType;

        private AdapterElement(@Nullable ApplicationIconHideable icon, char underlyingHeaderChar, int elementType, int spacingIndex) {
            mUnderlyingApp = icon;
            mUnderlyingHeaderChar = underlyingHeaderChar;
            mElementType = elementType;
            mSpacingIndex = spacingIndex;
        }

        static AdapterElement createTopElement() {
            return new AdapterElement(null, TOP_HEADER_BACKING_CHAR, ITEM_VIEW_TYPE_TOP, 0);
        }

        static AdapterElement createHeaderElement(char index) {
            return new AdapterElement(null, index, ITEM_VIEW_TYPE_HEADER, 0);
        }

        static AdapterElement createSpacerElement(char header, int index) {
            return new AdapterElement(null, header, ITEM_VIEW_TYPE_SPACER, index);
        }

        static AdapterElement createAppElement(ApplicationIconHideable icon) {
            return new AdapterElement(icon, '?', ITEM_VIEW_TYPE_APP, 0);
        }

        public ApplicationIconHideable getUnderlyingApp() {
            Preconditions.checkState(mElementType == ITEM_VIEW_TYPE_APP);
            return Preconditions.checkNotNull(mUnderlyingApp);
        }

        public char getUnderlyingHeaderChar() {
            Preconditions.checkState(
                mElementType == ITEM_VIEW_TYPE_HEADER ||
                mElementType == ITEM_VIEW_TYPE_SPACER);
            return mUnderlyingHeaderChar;
        }

        public int getSpacingIndex() {
            Preconditions.checkState(mElementType == ITEM_VIEW_TYPE_SPACER);
            return mSpacingIndex;
        }

        public int getElementType() {
            return mElementType;
        }

        @NonNull
        @Override
        public String toString() {
            switch (mElementType) {
                case ITEM_VIEW_TYPE_APP:
                    return "App element for " + getUnderlyingApp().toString();
                case ITEM_VIEW_TYPE_TOP:
                    return "Topmost header";
                case ITEM_VIEW_TYPE_HEADER:
                    return "Letter header for " + getUnderlyingHeaderChar();
                case ITEM_VIEW_TYPE_SPACER:
                    return "Spacer for " + getUnderlyingHeaderChar() + "/" + getSpacingIndex();
                default:
                    return "???";
            }
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
                case ITEM_VIEW_TYPE_HEADER:
                case ITEM_VIEW_TYPE_SPACER:
                    return getUnderlyingHeaderChar() == other.getUnderlyingHeaderChar() &&
                        getSpacingIndex() == other.getSpacingIndex();
                case ITEM_VIEW_TYPE_TOP:
                default:
                    return true;
            }
        }
    }

    private static final Comparator<AdapterElement> ELEMENT_COMPARATOR = new Comparator<AdapterElement>() {
        @Override
        public int compare(AdapterElement o1, AdapterElement o2) {
            // Top header and anything else -> easy
            if (o1.getElementType() == ITEM_VIEW_TYPE_TOP && o2.getElementType() !=
                ITEM_VIEW_TYPE_TOP) {
                return -1;
            }
            if (o1.getElementType() != ITEM_VIEW_TYPE_TOP && o2.getElementType() ==
                ITEM_VIEW_TYPE_TOP) {
                return 1;
            }

            // Two apps -- easy, compare app names
            if (o1.getElementType() == ITEM_VIEW_TYPE_APP && o2.getElementType() == ITEM_VIEW_TYPE_APP) {
                return FastScrollable.getComparator().compare(o1.getUnderlyingApp(), o2.getUnderlyingApp());
            }

            // Two headers -- easy, compare character
            if (o1.getElementType() == ITEM_VIEW_TYPE_HEADER && o2.getElementType() ==
                ITEM_VIEW_TYPE_HEADER) {
                return FastScrollable.getCharComparator().compare(o1.getUnderlyingHeaderChar(), o2.getUnderlyingHeaderChar());
            }

            // Two spacers -- easy, compare character, then spacing index
            if (o1.getElementType() == ITEM_VIEW_TYPE_SPACER && o2.getElementType() == ITEM_VIEW_TYPE_SPACER) {
                int comparison = FastScrollable.getCharComparator().compare(
                    o1.getUnderlyingHeaderChar(), o2.getUnderlyingHeaderChar());
                if (comparison != 0) {
                    return comparison;
                }
                return o1.getSpacingIndex() - o2.getSpacingIndex();
            }

            // lhs = header/spacer, rhs = app
            if ((o1.getElementType() == ITEM_VIEW_TYPE_HEADER ||
                o1.getElementType() == ITEM_VIEW_TYPE_SPACER) &&
                    o2.getElementType() == ITEM_VIEW_TYPE_APP) {
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

            // lhs = app, rhs = header/spacer
            if (o1.getElementType() == ITEM_VIEW_TYPE_APP &&
                    (o2.getElementType() == ITEM_VIEW_TYPE_HEADER ||
                        o2.getElementType() == ITEM_VIEW_TYPE_SPACER)) {
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

            // Fallback
            return 0;
        }
    };

    private enum Mode {
        SHOWING_ALL,
        SHOWING_GROUP,
        SEARCH_RESULTS
    }

    private static final int ITEM_VIEW_TYPE_APP = 1;
    private static final int ITEM_VIEW_TYPE_TOP = 2;
    private static final int ITEM_VIEW_TYPE_HEADER = 3;
    private static final int ITEM_VIEW_TYPE_SPACER = 4;
    private static final char TOP_HEADER_BACKING_CHAR = '?';

    private static final int HEADER_ITEM_ID = 0;

    private static final int HEADER_TOP_GROUP_DESELECTED_ALPHA = 180;
    private static final int HEADER_TOP_GROUP_SELECTED_ALPHA = 255;
    private static final int GROUP_ITEM_ROW_COUNT = 5;

    private final Delegate mDelegate;
    private final List<ApplicationIconHideable> mApps;
    private final PatriciaTrie<ApplicationIconHideable> mAppsTree = new PatriciaTrie<>();
    private final Map<String, Integer> mHeaderToCount = new HashMap<>();
    private final Activity mActivity;
    private final int mColumnCount;

    private List<AdapterElement> mElements;
    @Nullable private SwipeFolder mSelectedFolder;
    @Nullable
    private List<ApplicationIconHideable> mLastSearchResult;
    private Mode mMode;

    public AppDrawerAdapter(Delegate delegate, Activity activity, int columnCount) {
        this.mApps = AppInfoCache.get().getAppDrawerActivities();
        for (ApplicationIconHideable icon : mApps) {
            mAppsTree.put(icon.getName().toLowerCase(Locale.getDefault()), icon);
            Prewarmer.getInstance().prewarm(() ->
                IconCacheSync.getInstance(activity).getActivityIcon(
                    icon.getPackageName(), icon.getActivityName()));
        }
        this.mDelegate = delegate;
        this.mActivity = activity;
        this.mColumnCount = columnCount;
        this.mMode = Mode.SHOWING_ALL;
        rebuild(null);
    }

    /**
     * Filter the adapter to only show relevant search results.
     * @param query The search.
     * @return Whether the search returns results.
     */
    @SuppressLint("NotifyDataSetChanged")
    public synchronized boolean performSearch(String query) {
        mMode = Mode.SEARCH_RESULTS;
        mSelectedFolder = null;
        if (rebuild(query)) {
            notifyDataSetChanged();
        }
        return !mElements.isEmpty();
    }

    public synchronized void showGroup(SwipeFolder group) {
        assert(mMode != Mode.SEARCH_RESULTS);

        if (group == mSelectedFolder) {
            leaveGroupSelection();
            return;
        }

        Mode oldMode = mMode;
        mMode = Mode.SHOWING_GROUP;
        mSelectedFolder = group;
        mDelegate.setItemAnimator(null);
        notifyItemChanged(0);

        if (oldMode == Mode.SHOWING_GROUP) {
            rebuild(null);
        }
        for (int i = 1; i < mElements.size(); i++) {
            AdapterElement element = mElements.get(i);
            if (element.getElementType() != ITEM_VIEW_TYPE_APP ||
                !group.doesContainApp(element.getUnderlyingApp()))
            {
                mElements.remove(i);
                notifyItemRemoved(i);
                i--;
            }
        }
    }

    public synchronized void leaveGroupSelection() {
        mMode = Mode.SHOWING_ALL;
        mLastSearchResult = null;
        mSelectedFolder = null;
        rebuild(null);
        notifyDataSetChanged();
        mDelegate.setItemAnimator(new DefaultItemAnimator());
    }

    @SuppressLint("NotifyDataSetChanged")
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
            final ApplicationIconHideable removedApp = mApps.get(realIdx);
            final String headerMapKey = String.valueOf(removedApp.getScrollableField());
            mAppsTree.remove(removedApp.getName().toLowerCase(Locale.getDefault()));
            if (mHeaderToCount.containsKey(headerMapKey)) {
                int newCount = mHeaderToCount.get(headerMapKey) - 1;
                mHeaderToCount.put(headerMapKey, newCount);
            }
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
            final String headerMapKey = String.valueOf(activity.getScrollableField());
            if (mHeaderToCount.containsKey(headerMapKey)) {
                int newCount = mHeaderToCount.get(headerMapKey) + 1;
                mHeaderToCount.put(headerMapKey, newCount);
            } else {
                mHeaderToCount.put(headerMapKey, 1);
            }

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
        for (int i = mColumnCount; i < mElements.size(); i++) {
            AdapterElement element = mElements.get(i);
            if (element.getElementType() == ITEM_VIEW_TYPE_HEADER) {
                AdapterElement previousElement = mElements.get(i - 1);
                if (usingGridLayout()) {
                    if (previousElement.getElementType() == ITEM_VIEW_TYPE_SPACER &&
                        previousElement.getUnderlyingHeaderChar() != TOP_HEADER_BACKING_CHAR) {
                        // Walk back, and delete
                    }
                } else {
                    if (previousElement.getElementType() == ITEM_VIEW_TYPE_HEADER) {
                        // We've seen two headers in a row; trim the one right before
                        changeIndices.add(new Pair<>(
                            i - 1 + netOffsetFromAddRemove,
                            removeCharacter));
                        netOffsetFromAddRemove--;
                    }
                }
                characterOfLastHeader = element.getUnderlyingHeaderChar();
            } else if (element.getElementType() == ITEM_VIEW_TYPE_APP) {
                char appScrollable = element.getUnderlyingApp().getScrollableField();
                if (appScrollable != characterOfLastHeader) {
                    // Drop a new header here
                    changeIndices.add(new Pair<>(i + netOffsetFromAddRemove, appScrollable));
                    netOffsetFromAddRemove++;
                    if (usingGridLayout()) {

                    }
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
        final boolean hasHeaderAbove = mElements.get(position - 1).getElementType() ==
            ITEM_VIEW_TYPE_HEADER;
        final boolean lastItemInSection = position == mElements.size() - 1 ?
          hasHeaderAbove :
          mElements.get(position + 1).getElementType() == ITEM_VIEW_TYPE_HEADER;
        mElements.remove(position);
        mAppsTree.remove(searchKey.getName().toLowerCase(Locale.getDefault()));
        String headerMapKey = String.valueOf(searchKey.getScrollableField());
        if (mHeaderToCount.containsKey(headerMapKey)) {
            int newCount = mHeaderToCount.get(headerMapKey) - 1;
            mHeaderToCount.put(headerMapKey, newCount);
        }
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
                final AppDrawerIconViewGroup rootView =
                    (AppDrawerIconViewGroup)
                        LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.application_icon, viewGroup, false);
                final AppIconHolder holder = new AppIconHolder(rootView);
                FontCacheSync.Companion.get().applyTypefaceToTextView(
                    holder.title, Constants.LIST_FONT_PATH);
                return holder;
            }
            case ITEM_VIEW_TYPE_HEADER: {
                final View rootView =
                        LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.letter_header_row, viewGroup, false);
                final LetterHolder lh = new LetterHolder(rootView);
                return new LetterHolder(rootView);
            }
            case ITEM_VIEW_TYPE_SPACER: {
                final View view = new View(viewGroup.getContext());
                return new SpacerHolder(view);
            }
            case ITEM_VIEW_TYPE_TOP:
            default: {
                final View rootView =
                        LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.application_header_row, viewGroup, false);
                final TopHeaderHolder thh = new TopHeaderHolder(rootView);
                FontCacheSync.Companion.get().applyTypefaceToTextView(
                    thh.installCount, Constants.LIST_FONT_PATH);
                return thh;
            }
        }
    }

    // Set up specific customIcon with data
    @Override
    public synchronized void onBindViewHolder(final RecyclerView.ViewHolder holder, int i) {
        final int itemViewType = getItemViewType(i);

        if (itemViewType == ITEM_VIEW_TYPE_TOP) {
            final TopHeaderHolder headerHolder = (TopHeaderHolder) holder;
            final Context context = headerHolder.installCount.getContext();

            // Count of apps
            final SpannableString headerText =
                new SpannableString(
                    context.getResources().getString(R.string.header_app_count, mApps.size()));
            headerText.setSpan(
                new TextAppearanceSpan(context, R.style.BoldedText),
                0,
                String.valueOf(mApps.size()).length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            headerHolder.installCount.setText(headerText);
            return;
        }
        if (itemViewType == ITEM_VIEW_TYPE_HEADER) {
            final LetterHolder letterHolder = (LetterHolder) holder;
            String underlyingCharacter = String.valueOf(mElements.get(i).getUnderlyingHeaderChar());
            letterHolder.title.setHeaderChar(underlyingCharacter);
            if (mHeaderToCount.containsKey(underlyingCharacter)) {
                letterHolder.title.setHeaderCount(mHeaderToCount.get(underlyingCharacter));
            }
            letterHolder.title.setOnClickListener(mDelegate::enterFastScrollMode);
            return;
        }
        if (itemViewType == ITEM_VIEW_TYPE_SPACER) {
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
            v -> InstalledAppUtils.launchApp(
                viewHolder.icon, ai.getPackageName(), ai.getActivityName(), AppLaunchSource.APP_LIST));
        viewHolder.mainView.attachListener(new AppDrawerIconViewGroup.Listener() {

            @Override
            public void onLongPress(final int startX, final int startY) {
                if (mainView.getParent() == null) {
                    return;
                }
                mainView.getParent().requestDisallowInterceptTouchEvent(true);
                mainView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                final int[] out = new int[2];
                int viewWidth = mainView.getMeasuredWidth();
                int viewHeight = mainView.getMeasuredHeight();
                mainView.getLocationOnScreen(out);
                new AppPopupMenu().show(
                    out[0] + (viewWidth / 2),
                    out[1] + (viewHeight / 2),
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
                        ClassicGridItem.getNewAppItem(ai));
                final View draggingView = viewHolder.icon;
                DecorViewDragger.get(mActivity).startDrag(
                    draggingView, appViewHolder, true, rawX, rawY);
            }
        });
    }

    @Override
    public synchronized long getItemId(int position) {
        switch (getItemViewType(position)) {
            case ITEM_VIEW_TYPE_TOP:
                return HEADER_ITEM_ID;
            case ITEM_VIEW_TYPE_HEADER:
                return mElements.get(position).getUnderlyingHeaderChar();
            case ITEM_VIEW_TYPE_SPACER:
                return mElements.get(position).getUnderlyingHeaderChar() +
                    1 +
                    (100000L * (mElements.get(position).getSpacingIndex()));
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

    public synchronized void scrollToLetter(char letter) {
        letter = Character.toLowerCase(letter);
        for (int i = 0; i < mElements.size(); i++) {
            AdapterElement element = mElements.get(i);
            if (element.getElementType() != ITEM_VIEW_TYPE_HEADER) {
                continue;
            }
            if (Character.toLowerCase(element.getUnderlyingHeaderChar()) != letter) {
                continue;
            }

            int firstVisibleItem = mDelegate.getFirstIndexOnScreen();
            int lastVisibleItem = mDelegate.getLastIndexOnScreen();
            int visibleItemCount = lastVisibleItem - firstVisibleItem;
            int targetIdx = i;
            if (i < firstVisibleItem) {
                // Scrolling up, header will wind up at very top of screen
            } else if (i > lastVisibleItem) {
                // Scrolling down to the item, and then some
                targetIdx = (int) (i + (visibleItemCount * 0.6));
            } else {
                // Scroll position is somewhere on screen, so this operation is unneeded
                return;
            }
            mDelegate.scrollToIndex(Math.min(mDelegate.getTotalCount() - 1, targetIdx));
            break;
        }
    }

    public Map<String, Integer> getHeaderToCountMap() {
        return mHeaderToCount;
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
            if (usingGridLayout()) {
                for (int i = 0; i < mColumnCount - 1; i++) {
                    mElements.add(AdapterElement.createSpacerElement(TOP_HEADER_BACKING_CHAR, i));
                }
            }
            mHeaderToCount.clear();
            char currentScrollableField = '@'; // Never a scrollable field?
            for (ApplicationIconHideable app : mApps) {
                if (app.getScrollableField() != currentScrollableField) {
                    if (usingGridLayout() && mElements.size() % mColumnCount != 0) {
                        int startingIndex = mColumnCount;
                        while (mElements.size() % mColumnCount != 0) {
                            mElements.add(
                                AdapterElement.createSpacerElement(
                                    app.getScrollableField(), startingIndex));
                            startingIndex++;
                        }
                    }
                    mElements.add(AdapterElement.createHeaderElement(app.getScrollableField()));
                    if (usingGridLayout() && mElements.size() % mColumnCount != 0) {
                        int startingIndex = 0;
                        while (mElements.size() % mColumnCount != 0) {
                            mElements.add(
                                AdapterElement.createSpacerElement(
                                    app.getScrollableField(), startingIndex));
                            startingIndex++;
                        }
                    }
                    currentScrollableField = app.getScrollableField();
                }
                mElements.add(AdapterElement.createAppElement(app));
                final String headerKey = String.valueOf(currentScrollableField);
                if (mHeaderToCount.get(headerKey) == null) {
                    mHeaderToCount.put(headerKey, 0);

                }
                int newValue = mHeaderToCount.get(headerKey) + 1;
                mHeaderToCount.put(headerKey, newValue);
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
        mElements = result
            .parallelStream()
            .map(AdapterElement::createAppElement)
            .collect(Collectors.toList());
        return true;
    }

    private boolean usingGridLayout() {
        return mColumnCount > 1;
    }

    private View getGroupView(View parent, @Nullable SwipeFolder folder, boolean isSelected) {
        FrameLayout itemContainer = new FrameLayout(parent.getContext(), null);
        int appIconSize =
            parent
                .getContext()
                .getResources()
                .getDimensionPixelSize(R.dimen.app_drawer_group_icon_size);
        FrameLayout.LayoutParams groupFolderViewParams =
            new FrameLayout.LayoutParams(appIconSize, appIconSize);
        groupFolderViewParams.gravity = Gravity.CENTER;
        ImageView groupFolderView = new ImageView(itemContainer.getContext(), null);
        if (folder != null) {
            groupFolderView.setImageBitmap(folder.getIcon(parent.getContext()));
            groupFolderView.setImageAlpha(
                isSelected ?
                HEADER_TOP_GROUP_SELECTED_ALPHA :
                HEADER_TOP_GROUP_DESELECTED_ALPHA);
            groupFolderView.setOnClickListener(v -> showGroup(folder));
        }
        itemContainer.addView(groupFolderView, groupFolderViewParams);

        LinearLayout.LayoutParams itemContainerLayoutParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        itemContainerLayoutParams.weight = 1.0F;
        itemContainer.setLayoutParams(itemContainerLayoutParams);

        return itemContainer;
    }

    public static class TopHeaderHolder extends AnimatableViewHolder {
        TextView installCount;

        public TopHeaderHolder(View view) {
            super(view);
            this.installCount = ViewCompat.requireViewById(view, R.id.installed_apps_count);
        }
    }

    public static class SpacerHolder extends AnimatableViewHolder {

        public SpacerHolder(View view) {
            super(view);
        }
    }

    public static class LetterHolder extends AnimatableViewHolder {
        AppHeaderView title;

        public LetterHolder(View view) {
            super(view);
            this.title = ViewCompat.requireViewById(view, R.id.app_header_view);
        }
    }

    public static class AppIconHolder extends AnimatableViewHolder {
        AppDrawerIconViewGroup mainView;
        BitmapView icon;
        TextView title;

        public AppIconHolder(AppDrawerIconViewGroup mainView) {
            super(mainView);
            this.mainView = mainView;
            this.title = mainView.findViewById(R.id.app_icon_label);
            this.icon = mainView.findViewById(R.id.app_icon_image);
        }
    }

    public static class AnimatableViewHolder extends RecyclerView.ViewHolder {

        View view;

        public AnimatableViewHolder(View view) {
            super(view);
            this.view = view;
        }
    }
}
