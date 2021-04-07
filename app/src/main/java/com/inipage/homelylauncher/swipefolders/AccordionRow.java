package com.inipage.homelylauncher.swipefolders;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.SizeValAttribute;

import java.util.List;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

/**
 * Holds and calculates values for swipable rows.
 */
public class AccordionRow<T extends RowContent> {

    private final ImmutableList<T> mItems;
    private final SwipeFoldersView.GestureDirection mDirection;
    private final float mStartX;
    private final float mRange;
    private final ImmutableList<Float> mCenters;
    private final ImmutableList<Pair<Float, Float>> mRanges;
    private final float[] mIconRenderLocations;
    private final float mLeadInAmount;
    private final boolean mSupportsBacktrackSelection;
    /**
     * The most we'll require you to swipe in one direction, regardless of folder/app count.
     */
    @SizeValAttribute(240)
    private final int mMaxSwipeDistance = intValue();
    /**
     * A reasonable range of swipe space for each element; this is a target. This MUST be less than
     * mIconSize
     */
    @SizeValAttribute(40)
    private final int mPerElementComfyDistance = intValue();
    private float mCurrentX;
    private float mFurthestX;
    private boolean mLeadInActive;
    private float mLeadInProgression;
    private boolean mDidPassLeadIn;
    private int mSelectedItemIdx;
    private boolean mSelectedItemActive;
    private boolean mIsOverEdit;

    public AccordionRow(
        Context context,
        SwipeAttributes swipeAttributes,
        List<T> items,
        SwipeFoldersView.GestureDirection direction,
        float startX,
        float leadInAmount,
        float screenWidth,
        boolean supportsBacktrackSelection) {
        AttributeApplier.applyDensity(this, context);
        mItems = ImmutableList.copyOf(items);
        mDirection = direction;
        mStartX = startX;
        mLeadInAmount = leadInAmount;
        mLeadInActive = true;
        mSupportsBacktrackSelection = supportsBacktrackSelection;
        mCurrentX = 0F;
        mFurthestX = isLtr() ? 0 : screenWidth;
        mLeadInProgression = 0F;
        mSelectedItemIdx = 0;
        if (empty()) {
            mRange = 0F;
            mCenters = null;
            mRanges = null;
            mIconRenderLocations = null;
            return;
        }

        float directionMultiplier = isLtr() ? 1 : -1;
        float contentScrollRange = mPerElementComfyDistance * items.size();
        if (contentScrollRange > mMaxSwipeDistance) {
            contentScrollRange = mMaxSwipeDistance;
        }
        if (isLtr() &&
            (
                startX + contentScrollRange + swipeAttributes.ACCORDION_IDLE_EDGE_PADDING_DP +
                    leadInAmount) > (screenWidth)) {
            contentScrollRange = screenWidth - startX - mLeadInAmount -
                swipeAttributes.ACCORDION_IDLE_EDGE_PADDING_DP;
        } else if (!isLtr() && (
            startX - contentScrollRange - leadInAmount -
                swipeAttributes.ACCORDION_IDLE_EDGE_PADDING_DP) < 0) {
            contentScrollRange =
                startX - mLeadInAmount - swipeAttributes.ACCORDION_IDLE_EDGE_PADDING_DP;
        }
        float xLocationPointer = startX + (directionMultiplier * mLeadInAmount);
        mRange = contentScrollRange / size();
        ImmutableList.Builder<Float> centerBuilder = new ImmutableList.Builder<>();
        ImmutableList.Builder<Pair<Float, Float>> rangeBuilder =
            new ImmutableList.Builder<>();
        for (int i = 0; i < size(); i++) {
            float rangeStartX = xLocationPointer;
            float rangeEndX = xLocationPointer + (directionMultiplier * mRange);
            float rangeCenter = rangeEndX - (directionMultiplier * (mRange / 2F));
            centerBuilder.add(rangeCenter);
            rangeBuilder.add(new Pair<>(rangeStartX, rangeEndX));
            xLocationPointer = rangeEndX;
        }
        mCenters = centerBuilder.build();
        mRanges = rangeBuilder.build();
        mIconRenderLocations = new float[size()];
    }

    public boolean isLtr() {
        return mDirection == SwipeFoldersView.GestureDirection.LEFT_TO_RIGHT;
    }

    public boolean empty() {
        return mItems.isEmpty();
    }

    public int size() {
        return mItems.size();
    }

    public boolean updateX(float x) {
        if (empty()) {
            return false;
        }
        mCurrentX = x;
        return refresh();
    }

    private boolean refresh() {
        // If we're backtracking, we do anything else
        if (mSupportsBacktrackSelection && isBacktracking()) {
            mIsOverEdit = false;
            return false;
        }

        final boolean afterEndPoint = isLtr() ?
                                      mCurrentX > mRanges.get(size() - 1).second :
                                      mCurrentX < mRanges.get(size() - 1).second;
        // Handle after ranges
        if (afterEndPoint) {
            mLeadInActive = false;
            mLeadInProgression = 0F;
            mSelectedItemIdx = size() - 1;
            mSelectedItemActive = false;
            mIsOverEdit = true;
            return false;
        }
        mIsOverEdit = false;

        // Handle lead in; this beats out backtracking in precedence
        final float distanceFromStart = isLtr() ?
                                        mCurrentX - mStartX :
                                        mStartX - mCurrentX;
        final boolean leadInActive = distanceFromStart < mLeadInAmount;
        if (leadInActive) {
            if (mDidPassLeadIn) {
                // Nope, nope, nope -- can't go back; fix to start point
                Log.d("MACLYN", "did pass lead in");
                mLeadInActive = false;
                mSelectedItemIdx = 0;
                mSelectedItemActive = false;
                return false;
            }
            mLeadInActive = true;
            mLeadInProgression = distanceFromStart / mLeadInAmount;
            Log.d("MACLYN", "distance from start = " + distanceFromStart);
            Log.d("MACLYN", "mLeadINAmount = " + mLeadInAmount);
            Log.d("MACLYN", "lead in progression = " + mLeadInProgression);
            if (mLeadInProgression < 0) {
                mLeadInProgression = 0;
            }
            mSelectedItemIdx = 0;
            return false;
        }
        boolean shouldBuzz = false;
        if (mLeadInActive) {
            // This is a switch
            mLeadInActive = false;
            mDidPassLeadIn = true;
            shouldBuzz = true;
        }
        mSelectedItemActive = true;

        // mFurthestX is only valid within the scrollable range; do not set if we're out of
        // bounds
        if (isLtr() && mCurrentX > mFurthestX) {
            mFurthestX = mCurrentX;
        } else if (!isLtr() && mCurrentX < mFurthestX) {
            mFurthestX = mCurrentX;
        }

        // Invariant: mCurrentX in (mCenters[0], mRanges[size() - 1].second)
        final int previousSelectedItem = mSelectedItemIdx;
        for (int i = 0; i < mRanges.size(); i++) {
            Pair<Float, Float> range = mRanges.get(i);
            if (isLtr() && range.first <= mCurrentX && range.second >= mCurrentX) {
                mSelectedItemIdx = i;
                break;
            } else if (!isLtr() && mCurrentX <= range.first && mCurrentX >= range.second) {
                mSelectedItemIdx = i;
                break;
            }
        }

        // Vibrate when moving within the switch
        if (mSelectedItemIdx != previousSelectedItem) {
            shouldBuzz = true;
        }
        return shouldBuzz;
    }

    public boolean isBacktracking() {
        return mSupportsBacktrackSelection &&
            !mLeadInActive &&
            mSelectedItemActive &&
            (isLtr() ? mCurrentX < mFurthestX : mCurrentX > mFurthestX);
    }

    public Pair<Float, Float> getRange(int i) {
        return mRanges.get(i);
    }

    public float getCenter(int i) {
        return mCenters.get(i);
    }

    public boolean isLeadInActive() {
        return mLeadInActive;
    }

    public float getLeadInProgression() {
        return mLeadInProgression;
    }

    public float getLeadInAmount() {
        return mLeadInAmount;
    }

    public float[] getIconRenderLocations() {
        return mIconRenderLocations;
    }

    public boolean supportsBacktrackSelection() {
        return mSupportsBacktrackSelection;
    }

    public boolean selectedItemActive() {
        return mSelectedItemActive;
    }

    public boolean hasCompletedBacktrack(SwipeAttributes sharedAttrs) {
        if (!isBacktracking()) {
            return false;
        }
        return getBacktrackProgression(sharedAttrs) >= 1F;
    }

    public float getBacktrackProgression(SwipeAttributes sharedAttrs) {
        return isLtr() ?
               ((mFurthestX - mCurrentX) / sharedAttrs.TOUCH_BACKTRACKING_LEAD_IN_DISTANCE_DP) :
               ((mCurrentX - mFurthestX) / sharedAttrs.TOUCH_BACKTRACKING_LEAD_IN_DISTANCE_DP);
    }

    public T getItem(int i) {
        return mItems.get(i);
    }

    public ImmutableList<Float> getCenters() {
        return mCenters;
    }

    @Nullable
    public T getSelectedItem() {
        return mLeadInActive || !mSelectedItemActive ? null : mItems.get(mSelectedItemIdx);
    }

    public int getSelectedItemIdx() {
        return mSelectedItemIdx;
    }

    public boolean isOverEdit() {
        return mIsOverEdit;
    }
}
