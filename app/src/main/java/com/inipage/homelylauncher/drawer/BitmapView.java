package com.inipage.homelylauncher.drawer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/**
 * An 'ImageView' _without_ many of the optimizations of Android's ImageView. To be honest, I don't
 * know why this works better than ImageView, which I guess caused issues at some point? I don't
 * really remember.
 */
public class BitmapView extends View {

    final Paint mPaint = new Paint();
    final private Rect mSrc = new Rect();
    final private Rect mDst = new Rect();
    private Bitmap mBitmap;

    public BitmapView(Context context) {
        super(context);
    }

    public BitmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BitmapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public synchronized void setBitmap(Bitmap bitmap) {
        this.mBitmap = bitmap;
        if (bitmap != null) {
            this.mSrc.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            this.mDst.set(0, 0, getWidth(), getHeight());
            this.mPaint.setAntiAlias(true);
        }
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        if (mBitmap == null) {
            return;
        }
        canvas.drawBitmap(mBitmap, mSrc, mDst, mPaint);
    }

    @Override
    protected synchronized void onLayout(
        boolean changed, int left, int top, int right, int bottom) {
        if (!changed) {
            return;
        }
        this.mDst.set(0, 0, getWidth(), getHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
    }
}
