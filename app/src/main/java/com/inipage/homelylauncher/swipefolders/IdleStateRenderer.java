package com.inipage.homelylauncher.swipefolders;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.SizeValAttribute;

import java.util.List;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

/**
 * Renderer for the SwipeFoldersView idle state.
 */
public class IdleStateRenderer {

    // Draw style attributes
    private static final float IDLE_ALPHA = 0.8F;
    private final Paint mPaint = new Paint();
    @SizeValAttribute(16)
    private final int mMarginSize = intValue();
    @SizeValAttribute(2)
    private final int mDotSize = intValue();
    private ImmutableList<SwipeFolder> mShortcuts;
    private float[] mDotRenderLocations;
    private float[] mIntermediateDotRenderLocations;

    public IdleStateRenderer(Context context) {
        AttributeApplier.applyDensity(this, context);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    public void updateShortcuts(List<SwipeFolder> newFavorites) {
        mDotRenderLocations = new float[newFavorites.size()];
        mIntermediateDotRenderLocations = new float[newFavorites.size()];
        mShortcuts = ImmutableList.copyOf(newFavorites);
    }

    public void renderTrueIdleState(
        Canvas c,
        View view,
        SwipeAttributes sharedAttrs) {
        renderIdleDots(c, view, sharedAttrs);
    }

    private void renderIdleDots(Canvas c, View view, SwipeAttributes sharedAttrs) {
        if (mShortcuts == null) {
            return;
        }
        final float leftBound = sharedAttrs.ACCORDION_IDLE_EDGE_PADDING_DP + mMarginSize;
        final float rightBound =
            view.getWidth() - sharedAttrs.ACCORDION_IDLE_EDGE_PADDING_DP - mMarginSize;
        final float width = rightBound - leftBound;
        final float elementWidth = width / mShortcuts.size();
        for (int i = 0; i < mShortcuts.size(); i++) {
            mDotRenderLocations[i] = leftBound + (elementWidth * i) + (elementWidth / 2F);
        }
        renderTopLevelDots(c, view, sharedAttrs, mDotRenderLocations, IDLE_ALPHA);
    }

    private void renderTopLevelDots(
        Canvas c,
        View view,
        SwipeAttributes sharedAttrs,
        float[] renderLocations,
        final float alpha) {
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAlpha(SwipeAttributes.getAlphaFromFloat(1, alpha));
        mPaint.setStrokeWidth(mDotSize);
        final float baselineY = sharedAttrs.getLineBaseline(view);
        for (float renderLocation : renderLocations) {
            c.drawCircle(
                renderLocation,
                baselineY,
                sharedAttrs.INDICATOR_HEIGHT_DP / 4F,
                mPaint);
        }
    }

    public void renderIntermediateState(
        boolean leadInActive,
        float leadInProgression,
        float leadInAmount,
        SwipeAttributes sharedAttrs,
        boolean isFolderLtr,
        ImmutableList<Float> centers,
        SwipeFoldersView.GestureDirection startDirection,
        View view,
        Canvas c) {
        if (!leadInActive) {
            return;
        }

        // Render the arrow moving with the lead in swipe
        final float newAlpha = IDLE_ALPHA - (IDLE_ALPHA * leadInProgression);
        for (int i = 0; i < mIntermediateDotRenderLocations.length; i++) {
            final float start = mDotRenderLocations[i];
            final int endLookupIdx =
                startDirection == SwipeFoldersView.GestureDirection.LEFT_TO_RIGHT ?
                i :
                mIntermediateDotRenderLocations.length - i - 1;
            final float end = centers.get(endLookupIdx);
            final float diff = (end - start) * leadInProgression;
            mIntermediateDotRenderLocations[i] = start + diff;
        }
        renderTopLevelDots(c, view, sharedAttrs, mIntermediateDotRenderLocations, newAlpha);
    }
}
