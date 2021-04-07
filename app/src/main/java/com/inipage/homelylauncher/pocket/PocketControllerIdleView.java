package com.inipage.homelylauncher.pocket;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RotateDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

/**
 * Render equidistant dots.
 */
public class PocketControllerIdleView extends View {

    private static final int ALPHA = 180;
    private final Paint mPaint;
    private final RotateDrawable mArrowDrawable;
    @SizeDimenAttribute(R.dimen.pocket_dot_view_height)
    int viewHeight = intValue();
    @SizeDimenAttribute(R.dimen.pocket_dot_view_dot_section_width)
    int dotSectionWidth;
    @SizeDimenAttribute(R.dimen.pocket_dot_arrow_height)
    int arrowHeight = intValue();
    @SizeDimenAttribute(R.dimen.pocket_dot_view_dot_radius)
    int dotRadius = intValue();
    private float mExpandedPercent;
    private int mDotCount;

    public PocketControllerIdleView(Context context) {
        this(context, null);
    }

    public PocketControllerIdleView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PocketControllerIdleView(
        Context context,
        @Nullable AttributeSet attrs,
        int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        AttributeApplier.applyDensity(this, context);
        if (isInEditMode()) {
            mDotCount = 5;
        }
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setStrokeWidth(dotRadius);
        mPaint.setAlpha(ALPHA);
        final Drawable arrowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.arrow_up);
        arrowDrawable.setAlpha(ALPHA);
        mArrowDrawable = new RotateDrawable();
        mArrowDrawable.setDrawable(arrowDrawable);
        mArrowDrawable.setFromDegrees(0);
        mArrowDrawable.setToDegrees(180);
        mArrowDrawable.setPivotXRelative(true);
        mArrowDrawable.setPivotYRelative(true);
        mArrowDrawable.setPivotY(0.5F);
        mArrowDrawable.setPivotX(0.5F);
        mExpandedPercent = 0;
    }

    public void setDotCount(int dotCount) {
        mDotCount = dotCount;
        invalidate();
    }

    public void setRotation(float rotation) {
        mExpandedPercent = rotation;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the arrow
        // Centered, to the height
        final int center = getWidth() / 2;
        mPaint.setAlpha(ALPHA);
        mArrowDrawable.setBounds(center - arrowHeight, 0, center + arrowHeight, arrowHeight);
        mArrowDrawable.setLevel((int) (mExpandedPercent * 10000));
        mArrowDrawable.draw(canvas);

        if (mDotCount == 0) {
            return;
        }
        mPaint.setAlpha((int) (ALPHA * (1 - mExpandedPercent)));
        final float totalDotsWidth = dotSectionWidth * mDotCount;
        final float startX = (getWidth() / 2F) - (totalDotsWidth / 2F) + (dotSectionWidth / 2F);
        final int yBaseline = (int) (arrowHeight + ((getHeight() - arrowHeight) / 2F)) - dotRadius;
        for (int i = 0; i < mDotCount; i++) {
            final float sectionStart = startX + (i * dotSectionWidth);
            canvas.drawCircle(
                sectionStart,
                yBaseline,
                dotRadius,
                mPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            measuredHeight > viewHeight ? viewHeight : measuredHeight);
    }
}
