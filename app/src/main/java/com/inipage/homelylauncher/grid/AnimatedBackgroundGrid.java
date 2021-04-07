package com.inipage.homelylauncher.grid;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.BuildConfig;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.SizeValAttribute;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

@SuppressLint("ViewConstructor")
public class AnimatedBackgroundGrid
    extends View
    implements ValueAnimator.AnimatorUpdateListener, ValueAnimator.AnimatorListener {

    private static final boolean RENDER_DEBUG_ATTRS = BuildConfig.DEBUG && false;

    private final AccelerateDecelerateInterpolator INTERPOLATOR =
        new AccelerateDecelerateInterpolator();
    private final Paint mPaint;
    private final GridMetrics mGridMetrics;
    private final GridViewHolderMap mItemMap;
    @SizeValAttribute(2)
    private final int mLineWidth = intValue();
    private float mAnimatedPercent;
    private boolean mHiding;
    private boolean mHighlighting;
    private int mHighlightColumn, mHighlightRow, mHighlightWidth, mHighlightHeight;
    @Nullable
    private ValueAnimator mAnimator;

    public AnimatedBackgroundGrid(
        Context context,
        GridMetrics gridMetrics,
        GridViewHolderMap itemMap) {
        super(context, null);
        AttributeApplier.applyDensity(this, context);
        mAnimatedPercent = 0F;
        mGridMetrics = gridMetrics;
        mItemMap = itemMap;
        mHiding = true;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(mLineWidth);
        mPaint.setTextSize(20);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (Float.compare(mAnimatedPercent, 0F) == 0 || mItemMap == null) {
            return;
        }

        final float cellWidth = mGridMetrics.getCellWidthPx();
        final float cellHeight = mGridMetrics.getCellHeightPx();
        final int columnCount = mGridMetrics.getColumnCount();
        final int rowCount = mGridMetrics.getRowCount();
        final int totalWidth = (int) (cellWidth * columnCount);
        final int totalHeight = (int) (cellHeight * rowCount);
        mPaint.setAlpha((int) (mAnimatedPercent * 255 * 0.8));

        // Vertical lines
        for (int i = 0; i < mGridMetrics.getColumnCount() - 1; i++) {
            final float x = cellWidth * (i + 1);
            canvas.drawLine(x, 0, x, mAnimatedPercent * totalHeight, mPaint);
        }

        // Horizontal lines and pluses
        final float innerPaddingDelta = cellWidth * 0.2F;
        for (int i = 0; i < rowCount; i++) {
            final float yEnd = cellHeight * (i + 1);
            final float yStart = (int) (yEnd - cellHeight);
            final float yCenter = yEnd - (cellHeight / 2);

            // Horizontal lines
            if (i < rowCount - 1) {
                canvas.drawLine(0, yEnd, mAnimatedPercent * totalWidth, yEnd, mPaint);
            }

            // Pluses/circle symbols
            for (int j = 0; j < mGridMetrics.getColumnCount(); j++) {
                final int xStart = (int) (j * cellWidth);
                final int xEnd = (int) (xStart + cellWidth);
                if (RENDER_DEBUG_ATTRS) {
                    canvas.drawText(j + " " + i, xStart, yStart + 20, mPaint);
                }

                final boolean slotFilled = mItemMap.hasItemAtIdx(i, j);
                if (mHighlighting &&
                    i >= mHighlightRow && i < mHighlightRow + mHighlightHeight &&
                    j >= mHighlightColumn && j < mHighlightColumn + mHighlightWidth &&
                    !slotFilled) {
                    mPaint.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(
                        xStart + (cellWidth / 2),
                        yCenter,
                        (cellWidth / 2) - innerPaddingDelta,
                        mPaint);
                } else if (!slotFilled) {
                    // Cross
                    // horizontal
                    canvas.drawLine(
                        xStart + innerPaddingDelta,
                        yCenter,
                        xEnd - innerPaddingDelta,
                        yCenter,
                        mPaint);

                    // vertical
                    canvas.drawLine(
                        xEnd - (cellWidth / 2),
                        yCenter - (cellWidth / 2) + innerPaddingDelta,
                        xEnd - (cellWidth / 2),
                        yCenter + (cellWidth / 2) - innerPaddingDelta,
                        mPaint);
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
            mGridMetrics.getWidthOfColumnSpanPx(mGridMetrics.getColumnCount()),
            mGridMetrics.getHeightOfRowSpanPx(mGridMetrics.getRowCount()));
    }

    public void animateIn() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mHiding = false;
        mAnimator = ValueAnimator.ofFloat(0, 1F);
        mAnimator.addUpdateListener(this);
        mAnimator.addListener(this);
        mAnimator.setInterpolator(INTERPOLATOR);
        mAnimator.start();
    }

    public void animateOut() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mHiding = true;
        mAnimator = ValueAnimator.ofFloat(1F, 0F);
        mAnimator.addUpdateListener(this);
        mAnimator.addListener(this);
        mAnimator.setInterpolator(INTERPOLATOR);
        mAnimator.start();
    }

    public void highlightDragPosition(int col, int row, int width, int height) {
        mHighlighting = true;
        mHighlightRow = row;
        mHighlightColumn = col;
        mHighlightWidth = width;
        mHighlightHeight = height;
        invalidate();
    }

    public void quitDragMode() {
        mHighlighting = false;
        invalidate();
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mAnimatedPercent = mHiding ?
                           1 - animation.getAnimatedFraction() :
                           animation.getAnimatedFraction();
        invalidate();
    }

    @Override
    public void onAnimationStart(Animator animation) {
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        if (mHiding) {
            mAnimatedPercent = 0F;
        } else {
            mAnimatedPercent = 1F;
        }
        invalidate();
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        onAnimationEnd(animation);
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }
}
