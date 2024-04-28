package com.inipage.homelylauncher.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FlingAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_BOTTOM_SHEET;

public class BottomSheetContainer extends LinearLayout {

    final int[] mTempLoc = new int[2];
    private final VelocityTracker mVelocityTracker;
    @Nullable
    private String mDecorViewKey;
    private float mStartX, mStartY;
    private boolean mDroppedEvent;
    private boolean mExecutingAnimation;

    public BottomSheetContainer(Context context) {
        this(context, null);
    }

    public BottomSheetContainer(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BottomSheetContainer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mVelocityTracker = VelocityTracker.obtain();
    }

    public void attachDecorView(String handle) {
        mDecorViewKey = handle;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case ACTION_DOWN:
                if (mExecutingAnimation || !canInterceptEventAtPosition(this, ev)) {
                    log("Dropping event on ACTION_DOWN; executing animation=" +
                            mExecutingAnimation);
                    mDroppedEvent = true;
                    return false;
                }
                mStartX = ev.getRawX();
                mStartY = ev.getRawY();
                mVelocityTracker.clear();
                mDroppedEvent = false;
                return false;
            case ACTION_MOVE:
                if (mDroppedEvent || !ViewUtils.exceedsSlop_DEPRECATED_FAILS_WHEN_MULTIPLE_POINTERS_DOWN(ev, mStartX, mStartY, getContext())) {
                    return false;
                }
                final float xDist = mStartX - ev.getRawX();
                final float yDist = mStartY - ev.getRawY();
                if (yDist > 0 || Math.abs(xDist) > Math.abs(yDist)) {
                    mDroppedEvent = true;
                    return false;
                }
                return onTouchEvent(ev);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case ACTION_MOVE:
                log("mVelocityTracker.addMovement() rawY=" + ev.getRawY());
                ev.offsetLocation(0, getTranslationY());
                mVelocityTracker.addMovement(ev);
                updateBottomSheetLocation(ev.getRawY() - mStartY);
                break;
            case ACTION_UP:
                beginCommitAnimation();
                break;
            case ACTION_CANCEL:
                updateBottomSheetLocation(0);
                break;
        }
        return true;
    }

    /**
     * Trigger an animation to the end or top based on the current velocity + acceleration of the
     * bottom sheet
     */
    private void beginCommitAnimation() {
        log("Beginning commit animation");

        @Nullable final View v = getTargetView();
        if (v == null) {
            return;
        }

        mVelocityTracker.computeCurrentVelocity(
            1000, // px/s
            ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());
        final float speed = mVelocityTracker.getYVelocity();
        final float startPoint = getTranslationY();
        final float startVelocity =
            Math.max(
                Math.abs(speed),
                getContext().getResources().getDimension(R.dimen.min_start_velocity_dp_s));
        // Seems like the FlingAnimation doesn't work with negative values? Not entirely sure,
        // but making the <some + translation> -> 0 animation into a 0 -> <some + translation>
        // works regardless, though it does add a little logic
        final boolean isDown = speed > 0;
        log(
            "speed",
            String.valueOf(speed),
            "startPoint",
            String.valueOf(startPoint),
            "startVelocity",
            String.valueOf(startVelocity),
            "isDown",
            String.valueOf(isDown));
        final FlingAnimation flingAnimation =
            new FlingAnimation(
                this, new FloatPropertyCompat<BottomSheetContainer>("translationY") {
                @Override
                public float getValue(BottomSheetContainer object) {
                    return isDown ?
                           getTranslationY() :
                           startPoint - getTranslationY();
                }

                @Override
                public void setValue(BottomSheetContainer object, float value) {
                    updateBottomSheetLocation(isDown ?
                                              value :
                                              startPoint - value);
                }
            });
        flingAnimation.setMinValue(0);
        flingAnimation.setMaxValue(isDown ? getHeight() : startPoint);
        flingAnimation.setStartVelocity(startVelocity);
        flingAnimation.setFriction(ViewConfiguration.getScrollFriction());
        flingAnimation.addEndListener((animation, canceled, value, velocity) -> {
            if (isDown) {
                DecorViewManager.get(getContext()).removeView(mDecorViewKey);
            } else {
                updateBottomSheetLocation(0);
            }
            mExecutingAnimation = false;
        });
        flingAnimation.start();
    }

    private void updateBottomSheetLocation(double yTranslation) {
        @Nullable final View v = getTargetView();
        if (v == null) {
            return;
        }
        final double translation = Math.max(yTranslation, 0);
        v.setTranslationY((float) translation);
        DecorViewManager.get(getContext()).updateTintPercent(
            mDecorViewKey,
            (float) (1 - (translation / v.getMeasuredHeight())));
    }

    @Nullable
    private View getTargetView() {
        if (mDecorViewKey == null) {
            return null;
        }
        return DecorViewManager.get(getContext()).getView(mDecorViewKey);
    }

    private boolean canInterceptEventAtPosition(ViewGroup viewGroup, MotionEvent ev) {
        final float rawX = ev.getRawX();
        final float rawY = ev.getRawY();
        @Nullable View underPointView = null;
        for (int childIdx = 0; childIdx < getChildCount(); childIdx++) {
            @Nullable final View child = viewGroup.getChildAt(childIdx);
            if (child == null) {
                continue;
            }
            child.getLocationOnScreen(mTempLoc);
            if (mTempLoc[0] <= rawX &&
                rawX <= mTempLoc[0] + child.getWidth() &&
                mTempLoc[0] <= rawY &&
                rawY <= mTempLoc[1] + child.getHeight()) {
                underPointView = child;
                break;
            }
        }
        if (underPointView == null) {
            return true;
        }
        if (underPointView instanceof RecyclerView || underPointView instanceof ScrollView) {
            return !underPointView.canScrollVertically(-1);
        }
        if (underPointView instanceof ViewGroup) {
            return canInterceptEventAtPosition((ViewGroup) underPointView, ev);
        }
        return true;
    }

    private void log(String... vals) {
        DebugLogUtils.needle(TAG_BOTTOM_SHEET, vals);
    }
}
