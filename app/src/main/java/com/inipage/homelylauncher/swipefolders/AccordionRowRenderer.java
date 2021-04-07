package com.inipage.homelylauncher.swipefolders;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.View;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.Constants;
import com.inipage.homelylauncher.utils.SizeValAttribute;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

/**
 * Renderer for {@linkplain AccordionRow}.
 */
public class AccordionRowRenderer {

    private static final float EDIT_LINE_ALPHA = 0.4F;
    private static final float IDLE_ICON_ALPHA = 0.8F;
    private static final float ACTIVE_ICON_ALPHA = 1.0F;
    private final Paint mPaint = new Paint();
    private final Paint.FontMetrics mFontMetrics;
    private final Rect mRect = new Rect();
    private final RectF mRectF = new RectF();
    private final Drawable mOptionsDrawable;
    @SizeValAttribute(56)
    private final int mIconSize = intValue();
    @SizeValAttribute(24)
    private final int mTouchTopMargin = intValue();
    @SizeValAttribute(value = 16, attrType = SizeValAttribute.AttributeType.SP)
    private final int mTextSize = intValue();
    @SizeValAttribute(16)
    private final int mEditIconSize = intValue();
    @SizeValAttribute(16)
    private final int mIconPadding = intValue();
    @SizeValAttribute(16)
    private final int mLabelIconPadding = intValue();
    @SizeValAttribute(0)
    private final int mIndicatorCornerRound = intValue();
    @SizeValAttribute(0)
    private final int mIndicatorPadding = intValue();

    public AccordionRowRenderer(Context context) {
        AttributeApplier.applyDensity(this, context);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setTextSize(mTextSize);
        mFontMetrics = mPaint.getFontMetrics();
        mOptionsDrawable = context.getDrawable(R.drawable.ic_more_horiz_24);
    }

    public void render(
        AccordionRow row,
        SwipeAttributes sharedAttrs,
        View view,
        Canvas c) {
        if (row.empty()) {
            return;
        }
        renderBlinken(c, row);

        float alphaModifier = 1F;
        if (row.isLeadInActive()) {
            alphaModifier = row.getLeadInProgression();
        } else if (row.supportsBacktrackSelection() && row.isBacktracking()) {
            alphaModifier = 1 - row.getBacktrackProgression(sharedAttrs);
        }

        // Draw the blocks always
        for (int i = 0; i < row.size(); i++) {
            final Pair<Float, Float> range = row.getRange(i);
            final int color = row.getItem(i).getTint(view.getContext());
            final int alpha =
                SwipeAttributes.getAlphaFromFloat(
                    alphaModifier,
                    i == row.getSelectedItemIdx() && !row.isOverEdit() ?
                    1F :
                    (i < row.getSelectedItemIdx() ? 0.4F : 0.6F));
            renderBlock(
                c,
                sharedAttrs,
                view,
                color,
                row.isLtr() ? range.first : range.second,
                row.isLtr() ? range.second : range.first,
                alpha);
        }

        // Draw the label above the center of the block
        final float textYLine = getTextBaseline(sharedAttrs, view);
        final float centerX = row.getCenter(row.getSelectedItemIdx());
        final String label = row.getItem(row.getSelectedItemIdx()).getLabel(view.getContext());
        mPaint.setColor(Color.WHITE);
        mPaint.setAlpha(
            SwipeAttributes.getAlphaFromFloat(
                alphaModifier,
                row.isLeadInActive() || (row.getSelectedItem() == null) ?
                IDLE_ICON_ALPHA :
                ACTIVE_ICON_ALPHA));
        renderText(c, sharedAttrs, view, label, textYLine, centerX);

        // Draw the icons
        final float iconBaseline = textYLine - (getTextHeight() / 2F) - mLabelIconPadding;
        float startLocation = row.getCenter(row.getSelectedItemIdx()) - (mIconSize / 2F);
        final float iconRenderMovement =
            (mIconSize + mIconPadding) * (row.isLeadInActive() ? row.getLeadInProgression() : 1F);
        final float[] iconRenderLocations = row.getIconRenderLocations();
        iconRenderLocations[row.getSelectedItemIdx()] = startLocation;
        float directionMultiplier = row.isLtr() ? 1F : -1F;
        for (int i = row.getSelectedItemIdx() - 1; i >= 0; i--) {
            startLocation -= (directionMultiplier * iconRenderMovement);
            iconRenderLocations[i] = startLocation;
        }
        startLocation = iconRenderLocations[row.getSelectedItemIdx()];
        for (int i = row.getSelectedItemIdx() + 1; i < row.size(); i++) {
            startLocation += (directionMultiplier * iconRenderMovement);
            iconRenderLocations[i] = startLocation;
        }
        for (int i = 0; i < row.size(); i++) {
            RowContent item = row.getItem(i);
            mRectF.set(
                iconRenderLocations[i],
                iconBaseline - mIconSize,
                iconRenderLocations[i] + mIconSize,
                iconBaseline);
            mPaint.setAlpha(
                SwipeAttributes.getAlphaFromFloat(
                    alphaModifier,
                    i < row.getSelectedItemIdx() ?
                    (row.supportsBacktrackSelection() ? 0F : IDLE_ICON_ALPHA) :
                    (
                        i == row.getSelectedItemIdx() && row.selectedItemActive() &&
                            !row.isOverEdit()
                        ?
                        ACTIVE_ICON_ALPHA
                        :
                        IDLE_ICON_ALPHA)));
            c.drawBitmap(item.getIcon(view.getContext()), null, mRectF, mPaint);
        }

        // Render a line that's used to handle the edit nub at the end
        float lastPoint =
            ((float) row.getRange(row.size() - 1).second) -
                (directionMultiplier * mIndicatorPadding);
        float startPoint = row.isLtr() ? lastPoint : 0;
        float endPoint = row.isLtr() ? view.getWidth() : lastPoint;
        final int lineBaseline = sharedAttrs.getLineBaseline(view);
        mPaint.setColor(Color.WHITE);
        mPaint.setAlpha(SwipeAttributes.getAlphaFromFloat(alphaModifier, EDIT_LINE_ALPHA));
        c.drawRect(
            startPoint,
            lineBaseline - (sharedAttrs.INDICATOR_HEIGHT_DP / 2F),
            endPoint,
            lineBaseline + (sharedAttrs.INDICATOR_HEIGHT_DP / 2F),
            mPaint);
        if (row.isOverEdit()) {
            final float centerOfLine = startPoint + (endPoint - startPoint) / 2F;
            mRect.set(
                (int) (centerOfLine - (mEditIconSize / 2F)),
                lineBaseline - mEditIconSize - mIndicatorPadding,
                (int) (centerOfLine + (mEditIconSize / 2F)),
                lineBaseline - mIndicatorPadding);
            mOptionsDrawable.setBounds(mRect);
            mOptionsDrawable.draw(c);
        }
    }

    private void renderBlinken(Canvas c, AccordionRow row) {
        if (!Constants.DEBUG_RENDER) {
            return;
        }
        int color = row.isLtr() ? Color.YELLOW : Color.RED;
        for (int i = 0; i < row.size(); i++) {
            Pair<Float, Float> range = row.getRange(i);
            float left = row.isLtr() ? range.first : range.second;
            float right = row.isLtr() ? range.second : range.first;
            mPaint.setColor(color);
            c.drawRect(left, 8, right, 0, mPaint);
            mPaint.setColor(Color.BLUE);
            c.drawRect(left, 8, left + 8, 0, mPaint);
            c.drawRect(right - 8, 8, right, 0, mPaint);
        }
    }

    private void renderBlock(
        Canvas c,
        SwipeAttributes sharedAttrs,
        View view,
        int color,
        float left,
        float right,
        int alphaPct) {
        mPaint.setColor(color);
        mPaint.setAlpha(alphaPct);
        final int lineBaseline = sharedAttrs.getLineBaseline(view);
        final float top = lineBaseline - (sharedAttrs.INDICATOR_HEIGHT_DP / 2F);
        final float bottom = lineBaseline + (sharedAttrs.INDICATOR_HEIGHT_DP / 2F);
        c.drawRoundRect(
            left + mIndicatorPadding,
            top,
            right - mIndicatorPadding,
            bottom,
            mIndicatorCornerRound,
            mIndicatorCornerRound,
            mPaint);
    }

    private float getTextBaseline(
        SwipeAttributes renderAttributes,
        View view) {
        return renderAttributes.getLineBaseline(view) - mTouchTopMargin - (getTextHeight() / 2F);
    }

    private void renderText(
        Canvas canvas,
        SwipeAttributes sharedAttrs,
        View view,
        String label,
        float centerY,
        float centerX) {
        mPaint.getTextBounds(label, 0, label.length(), mRect);
        float textXStart = centerX - (mRect.width() / 2F);
        if (textXStart + mRect.width() + sharedAttrs.ACCORDION_IDLE_EDGE_PADDING_DP >
            view.getWidth()) {
            textXStart =
                view.getWidth() - mRect.width() - sharedAttrs.ACCORDION_IDLE_EDGE_PADDING_DP;
        }
        if (textXStart < sharedAttrs.ACCORDION_IDLE_EDGE_PADDING_DP) {
            textXStart = sharedAttrs.ACCORDION_IDLE_EDGE_PADDING_DP;
        }
        canvas.drawText(label, textXStart, centerY, mPaint);
    }

    private float getTextHeight() {
        return mFontMetrics.descent - mFontMetrics.ascent;
    }
}
