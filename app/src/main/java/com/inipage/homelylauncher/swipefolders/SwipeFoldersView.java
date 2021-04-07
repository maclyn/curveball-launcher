package com.inipage.homelylauncher.swipefolders;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.SwipeApp;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.state.StateChange;
import com.inipage.homelylauncher.state.SwipeFoldersStateMachine;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.utils.SizeValAttribute;
import com.inipage.homelylauncher.views.DecorViewDragger;

import java.util.List;
import java.util.Objects;

import static com.inipage.homelylauncher.state.SwipeFoldersStateMachine.CHOOSING_APP;
import static com.inipage.homelylauncher.state.SwipeFoldersStateMachine.CHOOSING_FOLDER;
import static com.inipage.homelylauncher.state.SwipeFoldersStateMachine.DROPPING_APP;
import static com.inipage.homelylauncher.state.SwipeFoldersStateMachine.IDLE;
import static com.inipage.homelylauncher.swipefolders.PerDragGestureValues.NO_ITEM_UNDER_DRAG;
import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

public class SwipeFoldersView extends View implements DecorViewDragger.DragAwareComponent {

    private final SwipeFoldersStateMachine mState;
    private final SwipeAttributes mSharedAttrs;
    private final BackgroundRenderer mBackgroundRenderer;
    private final AccordionRowRenderer mRowRenderer;
    private final IdleStateRenderer mIdleRenderer;
    private final DragStateRenderer mDragRenderer;
    private final Rect mExclusionRect = new Rect();
    /**
     * Extra padding distance applied to the first folder
     */
    @SizeValAttribute(64)
    private final int mFolderLeadInDistance = intValue();
    /**
     * If you start really close to the edge on the left or right, a distance that you're guaranteed
     * to have to select apps in.
     */
    @SizeValAttribute(120)
    private final int mMinAppSelectionDistance = intValue();
    private PerTouchGestureValues mTouchGestureValues;
    private PerDragGestureValues mDragGestureValues;
    private ImmutableList<SwipeFolder> mShortcuts;
    private Host mHost;
    private int mScreenWidth;

    public SwipeFoldersView(Context context) {
        this(context, null);
    }

    public SwipeFoldersView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeFoldersView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        AttributeApplier.applyDensity(this, context);
        final Handler h = new Handler(context.getMainLooper());
        mState = new SwipeFoldersStateMachine(context, h::post);
        mSharedAttrs = new SwipeAttributes(context);
        mBackgroundRenderer = new BackgroundRenderer(context);
        mRowRenderer = new AccordionRowRenderer(context);
        mIdleRenderer = new IdleStateRenderer(context);
        mDragRenderer = new DragStateRenderer(context);
        updateShortcuts(DatabaseEditor.get().getGestureFavorites());
    }

    public void updateShortcuts(List<SwipeFolder> newFavorites) {
        mShortcuts = ImmutableList.copyOf(newFavorites);
        mIdleRenderer.updateShortcuts(newFavorites);
    }

    public void attachHost(Host host, int screenWidth) {
        mHost = host;
        mScreenWidth = screenWidth;
        DecorViewDragger.get(mHost.getActivity()).registerDragAwareComponent(this);
    }

    public void handleForwardedTouchEvent(MotionEvent event, int totalXChange) {
        Log.d("MACLYN", "handleForwardedTouchEvent xchange = " + totalXChange);

        // This view actually drops touches directly; we rely on forwards from DragToOpenContainer
        // to handle touches (weird, but I wasn't about to try to learn touch dispatching)
        if (mShortcuts.isEmpty()) {
            return;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (mTouchGestureValues == null) {
                    Log.d("MACLYN", "creating touch gesture values");
                    mTouchGestureValues = new PerTouchGestureValues(event);
                    mState.request(StateChange.immediate(IDLE, CHOOSING_FOLDER));
                    mTouchGestureValues.update(event, totalXChange, mSharedAttrs);
                    mHost.requestStartFolderSwipe();
                    requestLayout();
                    return;
                }
                mTouchGestureValues.update(event, totalXChange, mSharedAttrs);

                // Handle fading out rest of the home screen with swipe in
                if (mTouchGestureValues.consumeNeedsCommit()) {
                    Log.d("MACLYN", "requesting commit folder swipe");
                    mHost.requestCommitFolderSwipe();
                } else {
                    if (!mTouchGestureValues.wasCommitConsumed()) {
                        Log.d("MACLYN", "requesting incremental folder swipe");
                        mHost.requestIncrementalFolderSwipe(mTouchGestureValues.commitIncrement());
                    }
                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                if (mTouchGestureValues.shouldEditTopLevelFolders()) {
                    mHost.requestFolderOrderEdit();
                } else if (mTouchGestureValues.getFolderToEdit() != -1) {
                    mHost.requestFolderEdit(mTouchGestureValues.getFolderToEdit());
                } else {
                    @Nullable final SwipeApp toLaunch = mTouchGestureValues.getLaunchItem();
                    if (toLaunch != null) {
                        // TODO: Appropriate animations
                        InstalledAppUtils.launchApp(
                            getContext(),
                            toLaunch.getComponent().first,
                            toLaunch.getComponent().second,
                            null);
                    }
                }
            case MotionEvent.ACTION_CANCEL:
                mHost.requestEndFolderSwipe();
                mState.request(StateChange.immediate(mState.getState(), IDLE));
                mTouchGestureValues = null;
                requestLayout();
                invalidate();
                break;
        }
    }

    @Override
    public View getDragAwareTargetView() {
        return this;
    }

    @Override
    public void onDrag(View v, DecorViewDragger.DragEvent event) {
        if (!(event.getLocalState() instanceof AppViewHolder)) {
            return;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                mDragGestureValues = new PerDragGestureValues(
                    this,
                    mShortcuts,
                    event,
                    mSharedAttrs);
                mState.request(StateChange.immediate(IDLE, DROPPING_APP));
                requestLayout();
                invalidate();
                break;
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
            case DragEvent.ACTION_DRAG_EXITED:
                if (mDragGestureValues.update(this, event, mSharedAttrs)) {
                    performSwitchBuzz();
                }
                invalidate();
                break;
            case DragEvent.ACTION_DROP:
                if (mDragGestureValues.getSelectedFolder() != NO_ITEM_UNDER_DRAG) {
                    if (mDragGestureValues.getShortcuts().size() ==
                        mDragGestureValues.getSelectedFolder()) {
                        mHost.requestCreateFolderDialog(
                            mDragGestureValues.getAppViewHolder().getAppIcon());
                    } else {
                        mHost.requestAddAppToFolder(
                            mDragGestureValues.getSelectedFolder(),
                            mDragGestureValues.getAppViewHolder().getAppIcon());
                    }
                }
                break;
            case DragEvent.ACTION_DRAG_ENDED:
                mDragGestureValues = null;
                mState.request(StateChange.immediate(mState.getState(), IDLE));
                requestLayout();
                invalidate();
                break;
        }
    }

    private void performSwitchBuzz() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mExclusionRect.set(0, 0, getWidth(), getHeight());
        ViewCompat.setSystemGestureExclusionRects(this, ImmutableList.of(mExclusionRect));

        switch (mState.getState()) {
            case IDLE:
                mBackgroundRenderer.render(canvas, this, mSharedAttrs, 0F);
                mIdleRenderer.renderTrueIdleState(canvas, this, mSharedAttrs);
                break;
            case DROPPING_APP:
                if (mDragGestureValues != null) {
                    mBackgroundRenderer.render(canvas, this, mSharedAttrs, 1F);
                    mDragRenderer.render(mDragGestureValues, mSharedAttrs, this, canvas);
                }
                break;
            case CHOOSING_APP:
            case CHOOSING_FOLDER:
                if (mTouchGestureValues != null) {
                    mTouchGestureValues.render(canvas);
                }
                break;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height;
        int width = MeasureSpec.getSize(widthMeasureSpec);
        switch (mState.getState()) {
            case CHOOSING_FOLDER:
                final float progression = mTouchGestureValues.mFolderRow == null ?
                                          1F :
                                          mTouchGestureValues.mFolderRow.isLeadInActive() ?
                                          mTouchGestureValues.mFolderRow.getLeadInProgression() :
                                          1F;
                Log.d("MACLYN", "progression=" + progression);
                final float diff =
                    mSharedAttrs.TOUCH_EXPANDED_HEIGHT_DP - mSharedAttrs.COLLAPSED_HEIGHT_DP;
                height = (int) (mSharedAttrs.COLLAPSED_HEIGHT_DP + (diff * progression));
                break;
            case CHOOSING_APP:
                height = mSharedAttrs.TOUCH_EXPANDED_HEIGHT_DP;
                break;
            case IDLE:
            case DROPPING_APP:
            default:
                height = mSharedAttrs.COLLAPSED_HEIGHT_DP;
                break;
        }
        setMeasuredDimension(width, height);
    }

    public enum GestureDirection {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT
    }

    public interface Host {
        void requestStartFolderSwipe();

        void requestIncrementalFolderSwipe(float percent);

        void requestCommitFolderSwipe();

        void requestEndFolderSwipe();

        void requestFolderEdit(int folder);

        void requestFolderOrderEdit();

        void requestCreateFolderDialog(ApplicationIcon app);

        void requestAddAppToFolder(int folder, ApplicationIcon app);

        Activity getActivity();
    }

    /**
     * To separate out the View-lifetime and gesture lifetime values, this class holds those values
     * that are only valid between ACTION_DOWN and ACTION_UP/CANCEL. These values are further broken
     * down into left and right swipe (with backtracking) by the {@linkplain AccordionRow}
     * abstraction. The state is then rendered out using the {@linkplain AccordionRowRenderer}.
     */
    private class PerTouchGestureValues {

        private float mCurrentX;
        @Nullable
        private GestureDirection mStartDirection;
        @Nullable
        private AccordionRow<SwipeFolder> mFolderRow;
        @Nullable
        private AccordionRow<SwipeApp> mAppRow;

        private int mSelectedFolder;
        private boolean mNeedsCommit;
        private boolean mNeedsCommitConsumed;

        private PerTouchGestureValues(MotionEvent initialEvent) {
            mCurrentX = initialEvent.getRawX();
            mNeedsCommit = false;
        }

        public void render(Canvas c) {
            if (mFolderRow != null) {
                mBackgroundRenderer.render(
                    c,
                    SwipeFoldersView.this,
                    mSharedAttrs,
                    mFolderRow.isLeadInActive() ?
                    mFolderRow.getLeadInProgression() :
                    1F);
                mRowRenderer.render(mFolderRow, mSharedAttrs, SwipeFoldersView.this, c);
                mIdleRenderer.renderIntermediateState(
                    mFolderRow.isLeadInActive(),
                    mFolderRow.getLeadInProgression(),
                    mFolderRow.getLeadInAmount(),
                    mSharedAttrs,
                    mFolderRow.isLtr(),
                    mFolderRow.getCenters(),
                    mStartDirection,
                    SwipeFoldersView.this,
                    c);
            }
            if (mAppRow != null) {
                if (mFolderRow == null) {
                    mBackgroundRenderer.render(c, SwipeFoldersView.this, mSharedAttrs, 1F);
                }
                mRowRenderer.render(mAppRow, mSharedAttrs, SwipeFoldersView.this, c);
            }
        }

        public void update(MotionEvent event, int totalXChange, SwipeAttributes sharedAttrs) {
            mCurrentX = event.getRawX();
            if (mStartDirection == null) {
                mStartDirection =
                    totalXChange > 0 ?
                    GestureDirection.RIGHT_TO_LEFT :
                    GestureDirection.LEFT_TO_RIGHT;
            }

            Log.d("MACLYN", "mStartDirection = " + mStartDirection.name());

            // Handle movement in rows
            switch (mState.getState()) {
                case CHOOSING_FOLDER:
                    if (mFolderRow == null) {
                        float folderLeadInDistance = mFolderLeadInDistance;
                        final float neededSpaceOppositeDirection =
                            mMinAppSelectionDistance +
                                mSharedAttrs.ACCORDION_IDLE_EDGE_PADDING_DP +
                                mSharedAttrs.TOUCH_BACKTRACKING_LEAD_IN_DISTANCE_DP;
                        final float actualExpectedSpace =
                            mFolderLeadInDistance +
                                (
                                    mStartDirection == GestureDirection.LEFT_TO_RIGHT ?
                                    mCurrentX :
                                    getWidth() - mCurrentX);
                        if (neededSpaceOppositeDirection > actualExpectedSpace) {
                            folderLeadInDistance +=
                                (neededSpaceOppositeDirection - actualExpectedSpace);
                        }
                        mFolderRow = new AccordionRow<>(
                            getContext(),
                            mSharedAttrs,
                            mShortcuts,
                            mStartDirection,
                            mCurrentX - 1, // When it first activates it should show progress
                            folderLeadInDistance,
                            mScreenWidth,
                            true);
                        if (mFolderRow.updateX(mCurrentX)) {
                            performSwitchBuzz();
                        }
                        requestLayout();
                        return;
                    }

                    if (mFolderRow.updateX(mCurrentX)) {
                        performSwitchBuzz();
                    }
                    if (mFolderRow.isLeadInActive()) {
                        requestLayout();
                    }
                    if (!mNeedsCommit && !mFolderRow.isLeadInActive()) {
                        mNeedsCommit = true;
                    }
                    if (mFolderRow.isBacktracking()) {
                        Log.d("MACLYN", "is backtracking folder!!");
                        if (mAppRow == null) {
                            mAppRow = new AccordionRow<>(
                                getContext(),
                                mSharedAttrs,
                                Objects.requireNonNull(
                                    mFolderRow.getSelectedItem()).getShortcutApps(),
                                mStartDirection == GestureDirection.LEFT_TO_RIGHT ?
                                GestureDirection.RIGHT_TO_LEFT :
                                GestureDirection.LEFT_TO_RIGHT,
                                mCurrentX,
                                mSharedAttrs.TOUCH_BACKTRACKING_LEAD_IN_DISTANCE_DP,
                                mScreenWidth,
                                false);
                        }
                        if (mAppRow.updateX(mCurrentX)) {
                            performSwitchBuzz();
                        }
                    }
                    if (mFolderRow.hasCompletedBacktrack(sharedAttrs)) {
                        mSelectedFolder = mFolderRow.getSelectedItemIdx();
                        mFolderRow = null;
                        mState.request(StateChange.immediate(CHOOSING_FOLDER, CHOOSING_APP));
                        update(event, totalXChange, mSharedAttrs);
                        return;
                    }
                    if (!mFolderRow.isBacktracking() && mAppRow != null) {
                        mAppRow = null;
                    }
                    break;
                case CHOOSING_APP:
                    if (Objects.requireNonNull(mAppRow).updateX(mCurrentX)) {
                        performSwitchBuzz();
                    }
                    break;
            }
        }

        private boolean consumeNeedsCommit() {
            if (mNeedsCommit && !mNeedsCommitConsumed) {
                mNeedsCommitConsumed = true;
                return true;
            }
            return false;
        }

        public boolean wasCommitConsumed() {
            return mNeedsCommitConsumed;
        }

        public float commitIncrement() {
            return mFolderRow == null ? 0 : mFolderRow.getLeadInProgression();
        }

        boolean shouldEditTopLevelFolders() {
            return mFolderRow != null && mFolderRow.isOverEdit();
        }

        int getFolderToEdit() {
            return mAppRow != null && mAppRow.isOverEdit() ? mSelectedFolder : -1;
        }

        @Nullable
        SwipeApp getLaunchItem() {
            return mAppRow == null ? null : mAppRow.getSelectedItem();
        }
    }
}