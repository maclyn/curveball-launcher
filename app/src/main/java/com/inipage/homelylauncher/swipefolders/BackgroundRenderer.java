package com.inipage.homelylauncher.swipefolders;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.AttributeApplier;

public class BackgroundRenderer {

    private final Paint mPaint = new Paint();

    public BackgroundRenderer(Context context) {
        AttributeApplier.applyDensity(this, context);
        mPaint.setAntiAlias(true);
        mPaint.setColor(context.getResources().getColor(R.color.transparent));
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    public void render(Canvas canvas, View view, SwipeAttributes sharedAttrs, float progression) {
        final float totalHeight = view.getHeight();
        canvas.drawRect(
            0,
            0,
            view.getWidth(),
            totalHeight,
            mPaint);
    }
}
