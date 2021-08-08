package com.inipage.homelylauncher.pager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;
import com.inipage.homelylauncher.utils.SizeValAttribute;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

public class PagerIndicatorView extends View {

    private final int INACTIVE_ALPHA = 80;
    private final int ACTIVE_ALPHA = 220;
    private final int INDICATOR_WIDTH_FACTOR = 8;
    private final Paint mPaint;
    @SizeDimenAttribute(R.dimen.indicator_height)
    int DESIRED_HEIGHT = intValue();
    @SizeValAttribute(2)
    int STROKE_WIDTH = intValue();
    @SizeValAttribute(4)
    int INDICATOR_SIZE = intValue();
    private int mGridPageCount;
    private int mActiveItem;
    private boolean mIsSetup = false;

    public PagerIndicatorView(Context context) {
        this(context, null);
    }

    public PagerIndicatorView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagerIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        AttributeApplier.applyDensity(this, getContext());
        // setBackground(context.getDrawable(R.drawable.indicator_view_background));
        mPaint = new Paint();
        mPaint.setStrokeWidth(STROKE_WIDTH);
    }

    public void setup(int gridPageCount) {
        mGridPageCount = gridPageCount;
        mActiveItem = 1;
        mIsSetup = true;
        requestLayout();
        invalidate();
    }

    public void updateActiveItem(int activeItem) {
        mActiveItem = activeItem;
        invalidate();
    }

    // Background is rendered as a View background;

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mIsSetup) {
            return;
        }

        // App drawer + other pages
        final int itemCount = mGridPageCount + 1;
        final int itemWidth = getWidth() / itemCount;
        final int xOffsetInRange = (itemWidth / 2) - (INDICATOR_SIZE / 2);
        final int yOffset = (getHeight() / 2) - (INDICATOR_SIZE / 2);

        mPaint.setColor(Color.WHITE);
        for (int i = 0; i < itemCount; i++) {
            final boolean active = i == mActiveItem;
            mPaint.setAlpha(active ? ACTIVE_ALPHA : INACTIVE_ALPHA);
            mPaint.setStyle(active ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
            final int xOffset = (i * itemWidth) + xOffsetInRange;
            switch (i) {
                case 0: // App drawer bubbles
                    float widthOverFour = INDICATOR_SIZE / 4F;
                    // Top left
                    canvas.drawCircle(
                        xOffset + widthOverFour,
                        yOffset + widthOverFour,
                        widthOverFour,
                        mPaint);
                    // Bottom right
                    canvas.drawCircle(
                        xOffset + (widthOverFour * 3),
                        yOffset + (widthOverFour * 3),
                        widthOverFour,
                        mPaint);
                    break;
                case 1: // Home square
                    canvas.drawRect(
                        xOffset,
                        yOffset,
                        xOffset + INDICATOR_SIZE,
                        yOffset + INDICATOR_SIZE,
                        mPaint);
                    break;
                default: // Circles
                    canvas.drawOval(
                        xOffset,
                        yOffset,
                        xOffset + INDICATOR_SIZE,
                        yOffset + INDICATOR_SIZE,
                        mPaint);
                    break;
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int verticalSpace = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(
            mGridPageCount * INDICATOR_SIZE * INDICATOR_WIDTH_FACTOR,
            verticalSpace < DESIRED_HEIGHT ? verticalSpace : DESIRED_HEIGHT);
    }
}
