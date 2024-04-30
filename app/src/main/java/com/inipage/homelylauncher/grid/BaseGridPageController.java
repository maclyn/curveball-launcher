package com.inipage.homelylauncher.grid;

import static android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL;
import static android.appwidget.AppWidgetProviderInfo.RESIZE_NONE;
import static android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL;
import static android.view.DragEvent.ACTION_DRAG_ENDED;
import static android.view.DragEvent.ACTION_DRAG_ENTERED;
import static android.view.DragEvent.ACTION_DRAG_EXITED;
import static android.view.DragEvent.ACTION_DRAG_LOCATION;
import static android.view.DragEvent.ACTION_DRAG_STARTED;
import static android.view.DragEvent.ACTION_DROP;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static com.inipage.homelylauncher.caches.PackageModifiedEvent.Modification.ADDED;
import static com.inipage.homelylauncher.caches.PackageModifiedEvent.Modification.REMOVED;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_DRAG_OFFSET;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_ICON_CASCADE;
import static com.inipage.homelylauncher.utils.ViewUtils.exceedsSlopInActionMove;
import static com.inipage.homelylauncher.utils.ViewUtils.getRawXWithPointerId;
import static com.inipage.homelylauncher.utils.ViewUtils.getRawYWithPointerId;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.Pair;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.AppInfoCache;
import com.inipage.homelylauncher.caches.PackageModifiedEvent;
import com.inipage.homelylauncher.model.ApplicationIconHideable;
import com.inipage.homelylauncher.model.GridItem;
import com.inipage.homelylauncher.model.GridPage;
import com.inipage.homelylauncher.pager.BasePageController;
import com.inipage.homelylauncher.pager.GridPageLayout;
import com.inipage.homelylauncher.state.EditingEvent;
import com.inipage.homelylauncher.state.GestureNavContractSingleton;
import com.inipage.homelylauncher.state.GridDropFailedEvent;
import com.inipage.homelylauncher.state.LayoutEditingSingleton;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.Constants;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.StatusBarUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.AppPopupMenu;
import com.inipage.homelylauncher.views.DecorViewDragger;
import com.inipage.homelylauncher.views.DecorViewDragger.DragEvent;
import com.inipage.homelylauncher.views.DecorViewManager;
import com.inipage.homelylauncher.widgets.WidgetAddBottomSheet;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The glue between the Views in the grid (GridViewHolder) and the underlying data
 * (GridViewHolders).
 */
public abstract class BaseGridPageController implements BasePageController {

    private final Host mHost;
    private final GridPage<GridItem> mPage;
    private final GridViewHolderHost mGridItemHost;
    private final boolean mStartInEditing;
    private final DragListener mDragListener;
    private GridMetrics mMetrics;
    private GridViewHolderMap mHolderMap;
    private GridPageLayout mRootContainer;
    private RelativeLayout mContainer;
    private AnimatedBackgroundGrid mAnimatedBackgroundGrid;

    // Widget addition
    private int mPendingAppWidgetId;
    private AppWidgetProviderInfo mPendingAwpi;
    private int mAdditionX;
    private int mAdditionY;

    public BaseGridPageController(Host host, GridPage<GridItem> page, boolean startInEditing) {
        mHost = host;
        mPage = page;
        mDragListener = new DragListener(host.getContext());
        mStartInEditing = startInEditing;
        mGridItemHost = new GridViewHolderHost();
        AttributeApplier.applyDensity(this, host.getContext());
    }

    public void bind(View rootView) {
        mRootContainer = rootView.findViewById(R.id.grid_container);
        mRootContainer.setListener(new GesturePageLayoutListener());
        // This might be re-used if we bind it again, so...
        mRootContainer.removeAllViews();
        mRootContainer.post(this::onRootContainerLayout);
    }

    /**
     * Given that we'll know the actual height/width of our view, layout the page.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void onRootContainerLayout() {
        final int height = mRootContainer.getHeight();
        final int width = mRootContainer.getWidth();

        /*
         * TODO: The following logic works if the screen doesn't change size or rotate
         * We have to re-layout if we want this work nicely on tablets
         */

        if (mPage.areDimensionsUnset()) {
            mMetrics = new GridMetrics(height, width, Constants.DEFAULT_COLUMN_COUNT);
            mPage.sizeFromContainer(mMetrics);
            commitPage();
        } else {
            mMetrics = new GridMetrics(
                mPage.getHeight(), mPage.getWidth(), height, width);
            if (mPage.getHeight() != mMetrics.getRowCount() ||
                mPage.getWidth() != mMetrics.getColumnCount()) {
                mPage.sizeFromContainer(mMetrics);
                commitPage();
            }
        }
        mHolderMap = new GridViewHolderMap(mMetrics);
        mAnimatedBackgroundGrid = new AnimatedBackgroundGrid(
            mHost.getContext(),
            mMetrics,
            mHolderMap);
        mContainer = new RelativeLayout(mHost.getContext());
        mContainer.setOnTouchListener(new TouchListener());
        final FrameLayout.LayoutParams backgroundParams =
            new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        final FrameLayout.LayoutParams containerParams =
            new FrameLayout.LayoutParams(
                mMetrics.getWidthOfColumnSpanPx(mMetrics.getColumnCount()),
                mMetrics.getHeightOfRowSpanPx(mMetrics.getRowCount()),
                Gravity.CENTER);
        mRootContainer.addView(mAnimatedBackgroundGrid, backgroundParams);
        mRootContainer.addView(mContainer, containerParams);
        mContainer.setClickable(true);
        mContainer.setLongClickable(true);
        mContainer.post(mAnimatedBackgroundGrid::requestLayout);
        for (GridItem item : mPage.getItems()) {
            addItem(item);
        }
        validateGrid("Initial grid load");
        mAnimatedBackgroundGrid.invalidate();

        if (mStartInEditing) {
            enterEditMode();
        }
    }

    abstract void commitPage();

    abstract boolean isItemOnPage(GridItem item);

    @Nullable
    abstract String getPageId();

    abstract GridItem buildWidgetItem(int x, int y, int width, int height, int widgetId);

    abstract void updateItemToPage(GridItem item);

    private void addItem(GridItem item) {
        switch (item.getType()) {
            case GridItem.GRID_TYPE_APP:
                addAppItem(item);
                break;
            case GridItem.GRID_TYPE_WIDGET:
                addWidgetItem(item);
                break;
        }
    }

    /**
     * Compare the underlying GridPage, ViewHolderMap, and container, to make sure the screen,
     * database, and memory representations of this page are accurate.
     * <p>
     * This is a dirty hack, but should air out any weird issues.
     */
    private void validateGrid(String event) {
        mContainer.post(() -> validateInternal(event));
    }

    private void enterEditMode() {
        for (GridViewHolder viewHolder : mHolderMap.getHolders()) {
            viewHolder.enterEditMode();
        }
        Objects.requireNonNull(mAnimatedBackgroundGrid).animateIn();
    }

    //region GridItem specific code
    private void addAppItem(GridItem gridItem) {
        final GridViewHolder gridViewHolder =
            new AppViewHolder(mRootContainer.getContext(), gridItem);
        addViewHolder(gridViewHolder);
    }

    private void addWidgetItem(GridItem gridItem) {
        final int appWidgetId = gridItem.getDI();
        @Nullable final AppWidgetProviderInfo awpi =
            getAppWidgetManager().getAppWidgetInfo(appWidgetId);
        final int spanWidthPx = mMetrics.getWidthOfColumnSpanPx(gridItem.getWidth());
        final int spanHeightPx = mMetrics.getHeightOfRowSpanPx(gridItem.getHeight());
        @Nullable GridViewHolder gridViewHolder;
        if (awpi != null) {
            // getApplicationContext is important -- we want a Context that is not themed
            // otherwise the hosted widgets will look really bad...
            final AppWidgetHostView hostView =
                AppInfoCache.get()
                    .getAppWidgetHost()
                    .createView(
                        mHost.getContext().getApplicationContext(),
                        appWidgetId,
                        awpi);
            hostView.updateAppWidgetSize(
                null, spanWidthPx, spanHeightPx, spanWidthPx, spanHeightPx);
            gridViewHolder = new WidgetViewHolder(
                hostView,
                awpi,
                gridItem);
        } else {
            gridViewHolder = new MissingViewHolder(mContainer.getContext(), gridItem);
        }
        addViewHolder(gridViewHolder);
    }

    private void validateInternal(String event) {
        List<String> violations = new ArrayList<>();

        // Build map of ID -> item
        final Map<String, GridItem> dbIdToItem = new HashMap<>();
        for (GridItem item : mPage.getItems()) {
            dbIdToItem.put(item.getID(), item);
        }

        // Check ViewHolderMap
        final Set<GridViewHolder> holders = mHolderMap.getHolders();
        for (GridViewHolder holder : holders) {
            String holderId = holder.getItem().getID();
            if (!dbIdToItem.containsKey(holderId)) {
                violations.add("Database missing holderMap item = " + holder.getItem().toString());
                try {
                    holder.detachHost();
                    mHolderMap.removeHolder(holder);
                } catch (Exception ignored) {
                }
            } else if (!dbIdToItem.get(holderId).equalish(holder.getItem())) {
                violations.add("Database has different metrics for holderMap item =" +
                                   holder.getItem() + " and dbItem=" + dbIdToItem.get(holderId));
                try {
                    mPage.getItems().remove(dbIdToItem.get(holderId));
                    holder.detachHost();
                } catch (Exception ignored) {
                }
            }
        }

        // Check ViewHolders attached to the container
        for (int i = 0; i < mContainer.getChildCount(); i++) {
            @Nullable Object childTag = mContainer.getChildAt(i).getTag();
            if (!(childTag instanceof GridViewHolder)) {
                continue;
            }

            final GridViewHolder containerHolder = (GridViewHolder) childTag;
            final String holderId = containerHolder.getItem().getID();
            if (!dbIdToItem.containsKey(containerHolder.getItem().getID())) {
                violations.add("Database missing onscreen holder = " + containerHolder.getItem());
                try {
                    containerHolder.detachHost();
                } catch (Exception ignored) {
                }
            } else if (!dbIdToItem.get(holderId).equalish(containerHolder.getItem())) {
                violations.add("Database has different metrics for onscreen item =" +
                                   containerHolder.getItem() + " and dbItem=" +
                                   dbIdToItem.get(holderId));
                try {
                    mPage.getItems().remove(dbIdToItem.get(holderId));
                    containerHolder.detachHost();
                    mHolderMap.removeHolder(containerHolder);
                } catch (Exception ignored) {
                }
            }
        }

        if (!violations.isEmpty()) {
            final StringBuilder warning =
                new StringBuilder("After event ")
                    .append(event)
                    .append("these violations exist:\n\n");
            for (String violation : violations) {
                warning.append(violation).append("\n");
            }
            warning.append(
                "Remediation was attempted. If this didn't help, clear app data (sorry!).");

            ScrollView scrollView = new ScrollView(mContainer.getContext());
            TextView view = new TextView(mContainer.getContext());
            scrollView.addView(view);
            view.setText(warning.toString());
            new AlertDialog.Builder(mContainer.getContext())
                .setTitle("View <-> memory <-> DB corrupt")
                .setView(scrollView)
                .show();

            onGridMakeupChanged();
            commitPage();
        }
    }

    private void addViewHolder(GridViewHolder gridViewHolder) {
        mHolderMap.addHolder(gridViewHolder);
        gridViewHolder.attachHost(mGridItemHost);
    }

    private AppWidgetManager getAppWidgetManager() {
        return (AppWidgetManager) mHost.getContext().getSystemService(Context.APPWIDGET_SERVICE);
    }

    private void onGridMakeupChanged() {
        for (GridViewHolder viewHolder : mHolderMap.getHolders()) {
            viewHolder.invalidateEditControls();
        }
        mAnimatedBackgroundGrid.invalidate();
    }

    public boolean isEmptyPage() {
        return mPage.getItems().isEmpty();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEditingEvent(EditingEvent event) {
        if (event.isEditing()) {
            enterEditMode();
        } else {
            leaveEditMode();
        }
    }

    private void leaveEditMode() {
        for (GridViewHolder viewHolder : mHolderMap.getHolders()) {
            viewHolder.exitEditMode();
        }
        Objects.requireNonNull(mAnimatedBackgroundGrid).animateOut();
    }
    //endregion

    /**
     * Handles when you drag an item to a new location, but that location doesn't have space
     * to fit the item.
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGridDropFailed(GridDropFailedEvent event) {
        final GridViewHolder holder = event.getGridViewHolder();
        final GridItem item = holder.getItem();
        if (!isItemOnPage(item)) {
            return;
        }
        mPage.getItems().add(item);
        addItem(item);
        commitPage();
        onGridMakeupChanged();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPackageModifiedEvent(PackageModifiedEvent event) {
        if (event.getModification() == ADDED) {
            return;
        }

        final String changedPackage = event.getPackageName();
        final boolean isRemoval = REMOVED == event.getModification();
        Set<GridViewHolder> itemsToDrop = new HashSet<>();
        for (GridViewHolder holder : mHolderMap.getHolders()) {
            if (holder instanceof MissingViewHolder) {
                return;
            }

            if (holder instanceof WidgetViewHolder) {
                final String previousPackage =
                    ((WidgetViewHolder) holder).getProviderInfo().provider.getPackageName();
                if (!changedPackage.equals(previousPackage)) {
                    continue;
                }
                if (isRemoval) {
                    itemsToDrop.add(holder);
                } else { // Modification
                    // Check if the provider is still valid
                    // TODO
                }
            } else if (holder instanceof AppViewHolder) {
                final String previousPackage =
                    ((AppViewHolder) holder).getAppIcon().getPackageName();
                final String previousActivity =
                    ((AppViewHolder) holder).getAppIcon().getActivityName();
                if (!changedPackage.equals(previousPackage)) {
                    continue;
                }
                if (isRemoval) {
                    itemsToDrop.add(holder);
                } else {
                    // Check if app is still installed
                    List<ApplicationIconHideable> activities =
                        AppInfoCache
                            .get()
                            .getActivitiesForPackage(previousPackage);
                    boolean foundActivity = false;
                    for (ApplicationIconHideable app : activities) {
                        if (app.getActivityName().equals(previousActivity)) {
                            foundActivity = true;
                            break;
                        }
                    }
                    if (!foundActivity) {
                        itemsToDrop.add(holder);
                    }
                }
            }
        }
        if (itemsToDrop.isEmpty()) {
            return;
        }

        for (GridViewHolder item : itemsToDrop) {
            removeViewHolder(item);
        }
        onGridMakeupChanged();
        commitPage();
    }

    private void removeViewHolder(GridViewHolder gridViewHolder) {
        mPage.getItems().remove(gridViewHolder.getItem());
        mHolderMap.removeHolder(gridViewHolder);
        mAnimatedBackgroundGrid.invalidate();
    }

    @Override
    public DecorViewDragger.TargetedDragAwareComponent getDragAwareComponent() {
        return mDragListener;
    }

    @Override
    public void onPause() {
        clearDragTarget();
    }

    public void clearDragTarget() {
        mDragListener.clearDragTarget();
    }

    private void showWidgetAdditionMenu(int row, int column) {
        final Map<Pair<Integer, Integer>, Boolean> spaces =
            mHolderMap.getRightAndDownItemFits(row, column);
        WidgetAddBottomSheet.show(
            mRootContainer.getContext(),
            column,
            row,
            mMetrics,
            spaces,
            (targetX, targetY, awpi) -> {
                mPendingAppWidgetId = AppInfoCache.get().getAppWidgetHost().allocateAppWidgetId();
                mPendingAwpi = awpi;
                mAdditionX = targetX;
                mAdditionY = targetY;
                final boolean bindTest = getAppWidgetManager().bindAppWidgetIdIfAllowed(
                    mPendingAppWidgetId, awpi.provider);
                if (!bindTest) {
                    mHost.requestBindWidget(getPageId(), mPendingAppWidgetId, awpi);
                    return;
                }
                onBindWidgetSucceeded();
            });
    }
    //endregion

    public void onBindWidgetSucceeded() {
        if (mPendingAwpi.configure == null) {
            commitPendingWidgetAddition();
            return;
        }
        mHost.requestConfigureWidget(getPageId(), mPendingAppWidgetId, mPendingAwpi);
    }

    public void commitPendingWidgetAddition() {
        final int appWidgetId = mPendingAppWidgetId;
        final AppWidgetProviderInfo awpi =
            getAppWidgetManager().getAppWidgetInfo(appWidgetId);

        final int x = mAdditionX;
        final int y = mAdditionY;
        final int gridWidth = mMetrics.getMinColumnCountForWidget(awpi);
        final int gridHeight = mMetrics.getMinRowCountForWidget(awpi);
        final GridItem widgetItem =
            buildWidgetItem(
                x,
                y,
                gridWidth,
                gridHeight,
                mPendingAppWidgetId);
        mPage.getItems().add(widgetItem);
        addWidgetItem(widgetItem);
        mPendingAppWidgetId = mAdditionX = mAdditionY = -1;
        mPendingAwpi = null;
        onGridMakeupChanged();
        commitPage();
    }

    private void log(String tag, String contents) {
        DebugLogUtils.needle(tag, "page=" + getPageId(), contents);
    }

    public void getLastLaunchedItem() {
        // TODO
    }

    public interface Host {
        Activity getContext();

        void requestBindWidget(@Nullable String pageId, int appWidgetId, AppWidgetProviderInfo awpi);

        void requestConfigureWidget(@Nullable String pageId, int appWidgetId, AppWidgetProviderInfo awpi);
    }

    private class TouchListener implements View.OnTouchListener {

        private boolean mGotTouchDown;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case ACTION_DOWN:
                    mGotTouchDown = true;
                    return true;
                case ACTION_MOVE:
                    return mGotTouchDown;
                case ACTION_UP:
                    if (!mGotTouchDown) {
                        return false;
                    }
                    mGotTouchDown = false;
                    final int colIdx = (int) e.getX() / mMetrics.getCellWidthPx();
                    final int rowIdx = (int) e.getY() / mMetrics.getCellHeightPx();
                    if (mHolderMap.hasItemAtIdx(rowIdx, colIdx)) {
                        return true;
                    }

                    if (LayoutEditingSingleton.getInstance().isEditing()) {
                        showWidgetAdditionMenu(rowIdx, colIdx);
                    } else {
                        WallpaperManager wm = WallpaperManager.getInstance(v.getContext());
                        wm.sendWallpaperCommand(
                            v.getWindowToken(),
                            WallpaperManager.COMMAND_TAP,
                            (int) e.getRawX(),
                            (int) e.getRawY(),
                            0,
                            null);
                    }
                    return true;
                case ACTION_CANCEL:
                    mGotTouchDown = false;
                    return false;
            }
            return true;
        }
    }

    private class DragListener implements DecorViewDragger.TargetedDragAwareComponent {

        private final GridChoreographer mChoreographer;
        @Nullable
        private Point mLastCellCommitted;
        @Nullable
        private Point mLastCellDraggedOver;
        private final int mDistFromEdgeForDrags;

        public DragListener(Context context) {
            mLastCellDraggedOver = mLastCellCommitted = null;
            mChoreographer = new GridChoreographer(context, cellCommitted -> {
                mLastCellCommitted = cellCommitted;
                commitPage();
                mHolderMap.invalidate();
                onGridMakeupChanged();
            });
            mDistFromEdgeForDrags =
                context.getResources().getDimensionPixelSize(R.dimen.dist_from_edge_to_switch);
        }

        @Override
        public View getDragAwareTargetView() {
            return mContainer;
        }

        @Override
        public void onDrag(View v, DragEvent event) {
            if (!(event.getLocalState() instanceof GridViewHolder)) {
                return;
            }

            final GridViewHolder viewHolder = (GridViewHolder) event.getLocalState();
            final GridItem gridItem = viewHolder.getItem();
            switch (event.getAction()) {
                case ACTION_DRAG_STARTED:
                    log(TAG_ICON_CASCADE, "Drag started received");
                    break;
                case ACTION_DRAG_ENTERED:
                case ACTION_DRAG_LOCATION:
                    // If we're over the very edge of the screen, we're queueing a move left/right,
                    // so we don't do a displacement action
                    if (event.getRawX() < mDistFromEdgeForDrags ||
                        event.getRawX() > (mRootContainer.getWidth() - mDistFromEdgeForDrags))
                    {
                        mChoreographer.clear();
                        return;
                    }

                    // Set last cell dragged over & last cell committed if not already set
                    if (!viewHolder.getItem().isSizeUnset() &&
                        mLastCellDraggedOver == null &&
                        mLastCellCommitted == null) {
                        mLastCellDraggedOver =
                            new Point(viewHolder.getItem().getX(), viewHolder.getItem().getY());
                        mLastCellCommitted = new Point(mLastCellDraggedOver);
                    }

                    // Figure out if the drag event is over a new point
                    final Point locationPoint = findPointFromDragEvent(event);
                    final int columnCell = locationPoint.x;
                    final int rowCell = locationPoint.y;
                    mAnimatedBackgroundGrid.highlightDragPosition(
                        columnCell, rowCell, gridItem.getWidth(), gridItem.getHeight());
                    final boolean hasNewPositionData =
                        mLastCellDraggedOver == null ||
                            columnCell != mLastCellDraggedOver.x ||
                            rowCell != mLastCellDraggedOver.y;
                    if (!hasNewPositionData) {
                        return;
                    }

                    // Try and free up the space
                    log(TAG_ICON_CASCADE, "New position data found: " + columnCell + "x" + rowCell);
                    final boolean areaOccupied = mHolderMap.isAreaOccupied(
                        columnCell, rowCell, gridItem.getWidth(), gridItem.getHeight());
                    if (areaOccupied) {
                        final Point targetCell = new Point(columnCell, rowCell);
                        @Nullable Set<GridViewHolder> naiveSolution = null;
                        try {
                            naiveSolution =
                                mHolderMap.solveForTranslationsToFitMovement(
                                    targetCell,
                                    mLastCellCommitted,
                                    mLastCellDraggedOver,
                                    gridItem);
                        } catch (Exception solvedFailure) {
                            log(TAG_DRAG_OFFSET, "Failed to solve for movement: " + solvedFailure);
                        }
                        if (naiveSolution != null) {
                            mChoreographer.queueSolve(naiveSolution, targetCell);
                        }
                    } else {
                        mChoreographer.clear();
                        mLastCellCommitted = new Point(columnCell, rowCell);
                    }
                    mLastCellDraggedOver = new Point(columnCell, rowCell);
                    break;
                case ACTION_DROP:
                    final Point dropDragPoint = findPointFromDragEvent(event);
                    log(TAG_ICON_CASCADE, "Drag drop point=" + dropDragPoint);
                    maybeCommitDragChanges(dropDragPoint.x, dropDragPoint.y, viewHolder);
                    mChoreographer.clear();
                    mAnimatedBackgroundGrid.quitDragMode();
                    mLastCellDraggedOver = mLastCellCommitted = null;
                    break;
                case ACTION_DRAG_EXITED:
                    log(TAG_ICON_CASCADE, "Drag exited");
                    // We don't wipe out the mLastCell here, because there's situations where
                    // we'd want to restore to that (e.g. dragging up a little too far); when
                    // we expect to remove it
                    break;
                case ACTION_DRAG_ENDED:
                    // We ALWAYS get this, even if we're not privy to the DROP event, so this is
                    // a safe place to do cleanup
                    mLastCellDraggedOver = mLastCellCommitted = null;
                    mChoreographer.clear();
                    mAnimatedBackgroundGrid.quitDragMode();
                    break;
            }
        }

        @Override
        public int getPriority() {
            return DecorViewDragger.DRAG_PRIORITY_HIGHEST;
        }

        private Point findPointFromDragEvent(DragEvent event) {
            // DragEvent.getRawXOffsetByView() and DragEvent.getRawYOffsetByView() are already relative to the receiving view --
            // in this case, mContainer
            final GridViewHolder viewHolder = (GridViewHolder) event.getLocalState();
            final GridItem gridItem = viewHolder.getItem();
            final int cellWidth = mMetrics.getCellWidthPx();
            final int cellHeight = mMetrics.getCellHeightPx();

            log(TAG_DRAG_OFFSET, "event.getRawXOffsetByView()/Y():" + event.getRawX() + ", " + event.getRawY());

            float topLeftCellCenterX =
                event.getRawXOffsetByView(mContainer) + event.getOffsetX() + (cellWidth / 2F);
            float topLeftCellCenterY =
                event.getRawYOffsetByView(mContainer) + event.getOffsetY() + (cellHeight / 2F);
            final float columnValue = topLeftCellCenterX / cellWidth;
            final float rowValue = topLeftCellCenterY / cellHeight;
            int columnCell =
                (int) Math.min(Math.max(0, Math.floor(columnValue)), mMetrics.getColumnCount() - 1);
            int rowCell =
                (int) Math.min(Math.max(0, Math.floor(rowValue)), mMetrics.getRowCount() - 1);
            // columnCell and rowCell are the right fit for the center of top-left cell, but for
            // >1x1 items we need to do more fitting to make sure it doesn't flow off the screen
            if (columnCell + gridItem.getWidth() > mMetrics.getColumnCount()) {
                columnCell = mMetrics.getColumnCount() - gridItem.getWidth();
            } else if (columnCell < 0) {
                columnCell = 0;
            }
            if (rowCell + gridItem.getHeight() > mMetrics.getRowCount()) {
                rowCell = mMetrics.getRowCount() - gridItem.getHeight();
            } else if (rowCell < 0) {
                rowCell = 0;
            }
            return new Point(columnCell, rowCell);
        }

        private void maybeCommitDragChanges(int x, int y, GridViewHolder holder) {
            final GridItem gridItem = holder.getItem();
            if (mHolderMap.isAreaOccupied(x, y, gridItem.getWidth(), gridItem.getHeight())) {
                Toast.makeText(
                    mContainer.getContext(),
                    R.string.item_doesnt_fit_here,
                    Toast.LENGTH_SHORT).show();
                EventBus.getDefault().post(new GridDropFailedEvent(holder));
            } else {
                // Fix up item; x/y/pageId could all be wrong
                gridItem.update(x, y);
                updateItemToPage(gridItem);
                mPage.getItems().add(gridItem);
                addItem(gridItem);
                commitPage();
                onGridMakeupChanged();
                validateGrid("Dropping item=" + gridItem);
            }
        }

        public void clearDragTarget() {
            log(TAG_ICON_CASCADE, "clearDragTarget");
            mLastCellDraggedOver = mLastCellCommitted = null;
            mChoreographer.halt();
        }
    }

    private class GesturePageLayoutListener implements GridPageLayout.Listener {

        @Nullable
        private GridViewHolder mActionTargetGridHolder;

        @Override
        public boolean onLongPress(final int rawX, final int rawY) {
            mRootContainer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

            final boolean isEditing = LayoutEditingSingleton.getInstance().isEditing();
            @Nullable final GridViewHolder gridViewHolder = getItemAtPosition(rawX, rawY);

            // Nothing there? Flip editing mode
            if (gridViewHolder == null) {
                LayoutEditingSingleton.getInstance().setEditing(!isEditing);
                // No drag will happen after this, but we still want to steal the following
                // events so we don't show the widget add menu
                return true;
            }

            if (gridViewHolder instanceof AppViewHolder) {
                // AppViewHolders have a menu that we should show before breaking off into a drag
                // if we're not in edit mode
                if (isEditing) {
                    // Start drag immediately (don't show menu in edit mode)
                    beginDragOnHolder(gridViewHolder, rawX, rawY);
                    return true;
                }

                // Show app menu
                AppViewHolder appViewHolder = (AppViewHolder) gridViewHolder;
                mRootContainer.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                // Center on the menu on center of the app icon
                final int[] out = new int[2];
                appViewHolder.getDragView().getLocationOnScreen(out);
                int viewWidth = appViewHolder.getDragView().getMeasuredWidth();
                int viewHeight = appViewHolder.getDragView().getMeasuredHeight();

                new AppPopupMenu().show(
                    out[0] + (viewWidth / 2),
                    out[1] + (viewHeight / 2),
                    true,
                    mRootContainer.getContext(),
                    appViewHolder.getAppIcon(),
                    new AppPopupMenu.Listener() {
                        @Override
                        public void onRemove() {
                            removeViewHolder(appViewHolder);
                        }

                        @Override
                        public void onDismiss() {}
                    });
                mActionTargetGridHolder = gridViewHolder;
                return true;
            } else if (gridViewHolder instanceof WidgetViewHolder) {
                // If it's a widget, either enter edit mode, or start moving it
                if (isEditing) {
                    beginDragOnHolder(gridViewHolder, rawX, rawY);
                } else {
                    LayoutEditingSingleton.getInstance().setEditing(true);
                }
                // We have to steal to stop the widget action from firing
                mActionTargetGridHolder = gridViewHolder;
                return true;
            } else {
                // Empty space and we're already editing
                return false;
            }
        }

        @Override
        public void onEventAfterLongPress(
            MotionEvent event,
            int action,
            int firstPointerId,
            float startRawX,
            float startRawY
        ) {
            // If we're in an ongoing drag -> forward to drag manager to move icon/widget
            // around the screen
            DecorViewDragger dragger = DecorViewDragger.get(mHost.getContext());
            if (dragger.isDragActive()) {
                switch (action) {
                    case ACTION_MOVE:
                        dragger.onDragMoveEvent(
                            getRawXWithPointerId(mRootContainer, event, firstPointerId),
                            getRawYWithPointerId(mRootContainer, event, firstPointerId));
                        break;
                    case ACTION_UP:
                        dragger.onDragEndEvent(
                            getRawXWithPointerId(mRootContainer, event, firstPointerId),
                            getRawYWithPointerId(mRootContainer, event, firstPointerId));
                        mActionTargetGridHolder = null;
                        break;
                    case ACTION_CANCEL:
                        dragger.onDragCancelEvent();
                        mActionTargetGridHolder = null;
                        break;
                }
                return;
            }

            if (mActionTargetGridHolder == null) {
                return;
            }

            if (action == ACTION_UP || action == ACTION_CANCEL) {
                mActionTargetGridHolder = null;
                return;
            }

            if (action == ACTION_MOVE &&
                exceedsSlopInActionMove(event, firstPointerId, startRawX, startRawY, mRootContainer)
            ) {
                // Collapse the menu, if it's showing
                DecorViewManager.get(mRootContainer.getContext()).detachAllViews();
                LayoutEditingSingleton.getInstance().setEditing(true);
                beginDragOnHolder(
                    Objects.requireNonNull(mActionTargetGridHolder),
                    (int) startRawX,
                    (int) startRawY);
            }
        }

        @Override
        public void onSwipeUpStarted(MotionEvent motionEvent, int firstPointerIdx, float startRawY) {
            if (LayoutEditingSingleton.getInstance().isEditing()) {
                return;
            }
            // TODO: This will be updated to handle folders at some point
        }

        @Override
        public void onEventAfterSwipeUp(
            MotionEvent event,
            int action,
            int firstPointerId,
            float startRawY
        ) {
            // TODO: Implement
        }

        @Override
        public void onSwipeDown() {
            mRootContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            StatusBarUtils.expandStatusBar(mHost.getContext());
        }

        @Override
        public void onUnhandledTouchUpInGridLayoutBounds(MotionEvent event) {
            @Nullable final GridViewHolder gridViewHolder =
                getItemAtPosition((int) event.getRawX(), (int) event.getRawY());
            if (!(gridViewHolder instanceof WidgetViewHolder)) {
                return;
            }
            final WidgetViewHolder widgetViewHolder = (WidgetViewHolder) gridViewHolder;
            final View view = gridViewHolder.getDragView();
            final int[] out = new int[2];
            view.getLocationOnScreen(out);
            GestureNavContractSingleton.INSTANCE.onWidgetLaunchRequest(
                widgetViewHolder.getProviderInfo().provider.getPackageName(),
                new RectF(out[0], out[1], out[1] + view.getWidth(), out[1] + view.getHeight()));
        }

        @Nullable
        private GridViewHolder getItemAtPosition(int rawX, int rawY) {
            // x/y are rawX/rawY the gesture page layout; translate to container-relative
            // coordinates
            final int[] out = new int[2];
            mContainer.getLocationOnScreen(out);
            int x = rawX - out[0];
            int y = rawY - out[1];
            return mHolderMap.getItemAtPositionPx(x, y, mMetrics);
        }

        private void beginDragOnHolder(GridViewHolder gridViewHolder, int startX, int startY) {
            gridViewHolder.getRootContainer().requestDisallowInterceptTouchEvent(true);
            DecorViewDragger.get(mHost.getContext())
                .startDrag(
                    gridViewHolder.getDragView(),
                    gridViewHolder,
                    false,
                    startX,
                    startY);
            gridViewHolder.detachHost();
            mPage.getItems().remove(gridViewHolder.getItem());
            mHolderMap.removeHolder(gridViewHolder);
            onGridMakeupChanged();
            commitPage();
        }
    }

    private class GridViewHolderHost implements GridViewHolder.Host {

        @Override
        public String getItemDescription(GridViewHolder viewHolder) {
            GridItem item = viewHolder.getItem();
            if (item.getType() == GridItem.GRID_TYPE_APP) {
                return "App=" + item.getPackageName() + "/" + item.getActivityName();
            } else if (item.getType() == GridItem.GRID_TYPE_WIDGET) {
                @Nullable final AppWidgetProviderInfo awpi =
                    getAppWidgetManager().getAppWidgetInfo(item.getWidgetID());
                if (awpi != null) {
                    return "Widget=" + awpi.provider.flattenToShortString();
                }
                return "Widget=(dead)";
            }
            return "?";
        }

        @Override
        public GridMetrics getGridMetrics() {
            return mMetrics;
        }

        @Override
        public RelativeLayout getGridContainer() {
            return mContainer;
        }

        @Override
        public boolean canResizeGridViewHolderInDirection(
            GridViewHolder gridViewHolder,
            GridViewHolder.ResizeDirection direction) {
            if (!(gridViewHolder instanceof WidgetViewHolder)) {
                return false;
            }

            final WidgetViewHolder widgetViewHolder = (WidgetViewHolder) gridViewHolder;
            final int resizeMode = widgetViewHolder.getProviderInfo().resizeMode;
            final int minColCount =
                mMetrics.getMinColumnCountForWidget(widgetViewHolder.getProviderInfo());
            final int minRowCount =
                mMetrics.getMinRowCountForWidget(widgetViewHolder.getProviderInfo());
            if (resizeMode == RESIZE_NONE) {
                return false;
            }
            if (resizeMode == RESIZE_HORIZONTAL && direction.isVertical()) {
                return false;
            }
            if (resizeMode == RESIZE_VERTICAL && direction.isHorizontal()) {
                return false;
            }
            if (direction.isShrink()) {
                if (direction.isHorizontal()) {
                    return gridViewHolder.getItem().getWidth() > minColCount;
                } else {
                    return gridViewHolder.getItem().getHeight() > minRowCount;
                }
            } else {
                return mHolderMap.canItemExpandOutInDirection(gridViewHolder, direction);
            }
        }

        @Override
        public void onRemove(GridViewHolder viewHolder) {
            removeViewHolder(viewHolder);
            onGridMakeupChanged();
            commitPage();
            validateGrid("Removing item: " + viewHolder.getItem().toString());
        }

        @Override
        public void onResize(GridViewHolder viewHolder) {
            mHolderMap.invalidate();
            onGridMakeupChanged();
            commitPage();
            validateGrid("Resizing item: " + viewHolder.getItem());
        }
    }
}
