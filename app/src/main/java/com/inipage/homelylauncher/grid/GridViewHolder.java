package com.inipage.homelylauncher.grid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.google.common.base.Preconditions;
import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.model.ClassicGridItem;
import com.inipage.homelylauncher.model.GridItem;
import com.inipage.homelylauncher.state.LayoutEditingSingleton;

import java.util.Objects;

import javax.annotation.Nullable;

import static com.inipage.homelylauncher.grid.GridItemHandleView.Direction.LEFT_UP;
import static com.inipage.homelylauncher.grid.GridViewHolder.ResizeDirection.DOWN;
import static com.inipage.homelylauncher.grid.GridViewHolder.ResizeDirection.DOWN_IN;
import static com.inipage.homelylauncher.grid.GridViewHolder.ResizeDirection.LEFT;
import static com.inipage.homelylauncher.grid.GridViewHolder.ResizeDirection.LEFT_IN;
import static com.inipage.homelylauncher.grid.GridViewHolder.ResizeDirection.RIGHT;
import static com.inipage.homelylauncher.grid.GridViewHolder.ResizeDirection.RIGHT_IN;
import static com.inipage.homelylauncher.grid.GridViewHolder.ResizeDirection.UP;
import static com.inipage.homelylauncher.grid.GridViewHolder.ResizeDirection.UP_IN;

/** Base class for anything represented in the grid. */
public abstract class GridViewHolder {

    public interface Host {
        String getItemDescription(GridViewHolder viewHolder);

        GridMetrics getGridMetrics();

        RelativeLayout getGridContainer();

        boolean canResizeGridViewHolderInDirection(
            GridViewHolder gridViewHolder, ResizeDirection direction);

        void onRemove(GridViewHolder viewHolder);

        void onResize(GridViewHolder viewHolder);
    }

    public enum ResizeDirection {
        UP, DOWN, LEFT, RIGHT, UP_IN, DOWN_IN, LEFT_IN, RIGHT_IN;

        public boolean isHorizontal() {
            return !isVertical();
        }

        public boolean isVertical() {
            return this.equals(UP) || this.equals(DOWN) || this.equals(UP_IN) ||
                this.equals(DOWN_IN);
        }

        public boolean isShrink() {
            return this.equals(UP_IN) || this.equals(DOWN_IN) || this.equals(LEFT_IN) ||
                this.equals(RIGHT_IN);
        }
    }

    private static final float SCALE_AMOUNT = 0.925F;
    private static final Interpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator();
    private static final Interpolator ACC_DEC_INTERPOLATOR = new AccelerateDecelerateInterpolator();

    // Subclasses need access to these fields to fill and populate their content
    protected final GridItem mItem;
    protected final FrameLayout mRootView;

    // These are purely imp. details; listener results are sent down to the subclasses
    private final ImageView mRemovalView;
    private final GridItemHandleView mLeftHandle, mRightHandle, mUpHandle, mDownHandle;

    @Nullable
    private Host mHost;
    private Point mQueuedPoint, mQueuedPointPx;

    @SuppressLint("InflateParams")
    public GridViewHolder(Context context, GridItem item) {
        mItem = item;
        mRootView =
            (FrameLayout) LayoutInflater.from(context).inflate(
                R.layout.grid_item_view, null);
        mRootView.setTag(this); // Used for validation later on
        mRemovalView = mRootView.findViewById(R.id.widget_remove_button);
        mLeftHandle = mRootView.findViewById(R.id.left_handle);
        mRightHandle = mRootView.findViewById(R.id.right_handle);
        mUpHandle = mRootView.findViewById(R.id.up_handle);
        mDownHandle = mRootView.findViewById(R.id.down_handle);
        final boolean isEditing = LayoutEditingSingleton.getInstance().isEditing();
        mRootView.setScaleY(isEditing ? SCALE_AMOUNT : 1F);
        mRootView.setScaleX(isEditing ? SCALE_AMOUNT : 1F);
        invalidateEditControls();
    }

    public void invalidateEditControls() {
        final boolean isEditing = LayoutEditingSingleton.getInstance().isEditing();
        mRemovalView.setScaleY(isEditing ? SCALE_AMOUNT : 0);
        mRemovalView.setScaleX(isEditing ? SCALE_AMOUNT : 0);
        mRemovalView.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        if (!isEditing) {
            hideViews(mLeftHandle, mRightHandle, mUpHandle, mDownHandle);
        }
        if (mHost == null) {
            mLeftHandle.setArrowsEnabled(false, false);
            mRightHandle.setArrowsEnabled(false, false);
            mUpHandle.setArrowsEnabled(false, false);
            mDownHandle.setArrowsEnabled(false, false);
            return;
        }

        final boolean left = mHost.canResizeGridViewHolderInDirection(this, LEFT);
        final boolean leftIn = mHost.canResizeGridViewHolderInDirection(this, LEFT_IN);
        final boolean rightIn = mHost.canResizeGridViewHolderInDirection(this, RIGHT_IN);
        final boolean right = mHost.canResizeGridViewHolderInDirection(this, RIGHT);
        final boolean up = mHost.canResizeGridViewHolderInDirection(this, UP);
        final boolean upIn = mHost.canResizeGridViewHolderInDirection(this, UP_IN);
        final boolean downIn = mHost.canResizeGridViewHolderInDirection(this, DOWN_IN);
        final boolean down = mHost.canResizeGridViewHolderInDirection(this, DOWN);

        Log.d(
            "GridViewHolder",
            mHost.getItemDescription(this) +
                " [left=" + left + ",right=" + right + ",up=" + up + ",down=" + down +
                ",leftIn=" + leftIn + ",rightIn=" + rightIn + ",upIn=" + upIn + ",downIn=" +
                downIn + "]");

        mLeftHandle.setArrowsEnabled(left, leftIn);
        mRightHandle.setArrowsEnabled(rightIn, right);
        mUpHandle.setArrowsEnabled(up, upIn);
        mDownHandle.setArrowsEnabled(downIn, down);
    }

    public GridItem getItem() {
        return mItem;
    }

    public int getWidth() {
        return mItem.getWidth();
    }

    public int getHeight() {
        return mItem.getHeight();
    }

    public void attachHost(final Host host) {
        mHost = host;
        mRemovalView.setOnClickListener(v -> {
            host.onRemove(this);
            detachHost();
        });
        mUpHandle.setListener(d -> {
            if (d == LEFT_UP) {
                mItem.resize(UP);
            } else {
                mItem.resize(UP_IN);
            }
            onResized();
        });
        mDownHandle.setListener(d -> {
            if (d == LEFT_UP) {
                mItem.resize(DOWN_IN);
            } else {
                mItem.resize(DOWN);
            }
            onResized();
        });
        mLeftHandle.setListener(d -> {
            if (d == LEFT_UP) {
                mItem.resize(LEFT);
            } else {
                mItem.resize(LEFT_IN);
            }
            onResized();
        });
        mRightHandle.setListener(d -> {
            if (d == LEFT_UP) {
                mItem.resize(RIGHT_IN);
            } else {
                mItem.resize(RIGHT);
            }
            onResized();
        });
        invalidateEditControls();
        final int spanWidthPx = host.getGridMetrics().getWidthOfColumnSpanPx(mItem.getWidth());
        final int spanHeightPx = host.getGridMetrics().getHeightOfRowSpanPx(mItem.getHeight());
        FrameLayout.LayoutParams params =
            new FrameLayout.LayoutParams(
                spanWidthPx,
                spanHeightPx,
                Gravity.CENTER);
        resetTranslation();
        host.getGridContainer().addView(mRootView, params);
    }

    public void resetTranslation() {
        mRootView.setTranslationX(
            Preconditions.checkNotNull(mHost).getGridMetrics().getWidthOfColumnSpanPx(mItem.getX()));
        mRootView.setTranslationY(
            Preconditions.checkNotNull(mHost).getGridMetrics().getHeightOfRowSpanPx(mItem.getY()));
    }

    public void detachHost() {
        if (mHost == null) {
            return;
        }
        mHost.getGridContainer().removeView(mRootView);
    }

    public FrameLayout getRootContainer() {
        return mRootView;
    }

    public void enterEditMode() {
        invalidateEditControls();
        mRootView.animate()
            .scaleX(SCALE_AMOUNT)
            .scaleY(SCALE_AMOUNT)
            .setInterpolator(OVERSHOOT_INTERPOLATOR)
            .start();
        animateAlphaIn(
            mRemovalView, mUpHandle, mDownHandle, mLeftHandle, mRightHandle);
    }

    public void exitEditMode() {
        invalidateEditControls();
        mRootView.animate()
            .scaleX(1F)
            .scaleY(1F)
            .setInterpolator(OVERSHOOT_INTERPOLATOR)
            .start();
        animateAlphaOut(
            mRemovalView, mUpHandle, mDownHandle, mLeftHandle, mRightHandle);
    }

    public void queueTranslation(int column, int row) {
        mQueuedPoint = new Point(column, row);
        final GridMetrics metrics = Objects.requireNonNull(mHost).getGridMetrics();
        mQueuedPointPx =
            new Point(metrics.getWidthOfColumnSpanPx(column), metrics.getHeightOfRowSpanPx(row));
    }

    public void commitTranslationChange() {
        mItem.update(mQueuedPoint.x, mQueuedPoint.y);
        clearQueuedTranslation();
    }

    public void clearQueuedTranslation() {
        mQueuedPoint = mQueuedPointPx = null;
    }

    public Point getQueuedTranslation() {
        return mQueuedPoint;
    }

    public Point getQueuedTranslationPx() {
        return mQueuedPointPx;
    }

    public Point getPositionPx() {
        final GridMetrics metrics = Preconditions.checkNotNull(mHost).getGridMetrics();
        return
            new Point(metrics.getWidthOfColumnSpanPx(mItem.getX()), metrics.getHeightOfRowSpanPx(mItem.getY()));
    }

    protected GridMetrics getGridMetrics() {
        return Preconditions.checkNotNull(mHost).getGridMetrics();
    }

    protected void onResized() {
        mRootView.requestDisallowInterceptTouchEvent(true);
        mRootView.post(() -> {
            mRootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            RelativeLayout.LayoutParams rootViewParams =
                (RelativeLayout.LayoutParams) mRootView.getLayoutParams();
            final GridMetrics metrics = Objects.requireNonNull(mHost).getGridMetrics();
            rootViewParams.height = metrics.getHeightOfRowSpanPx(mItem.getHeight());
            rootViewParams.width = metrics.getWidthOfColumnSpanPx(mItem.getWidth());
            mRootView.setLayoutParams(rootViewParams);

            mRootView.setTranslationX(metrics.getWidthOfColumnSpanPx(mItem.getX()));
            mRootView.setTranslationY(metrics.getHeightOfRowSpanPx(mItem.getY()));
            mHost.onResize(this);
            invalidateEditControls();
        });
    }

    protected abstract View getDragView();

    private void hideViews(View... views) {
        for (View v : views) {
            v.setAlpha(0F);
        }
    }

    private void animateAlphaOut(View... views) {
        for (View v : views) {
            v.animate().alpha(0).setInterpolator(ACC_DEC_INTERPOLATOR).start();
        }
    }

    private void animateAlphaIn(View... views) {
        for (View v : views) {
            v.animate().alpha(1).setInterpolator(ACC_DEC_INTERPOLATOR).start();
        }
    }
}
