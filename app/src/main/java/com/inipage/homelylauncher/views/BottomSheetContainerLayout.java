package com.inipage.homelylauncher.views;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static com.inipage.homelylauncher.utils.DebugLogUtils.TAG_BOTTOM_SHEET;
import static com.inipage.homelylauncher.utils.ViewUtils.getRawXWithPointerId;
import static com.inipage.homelylauncher.utils.ViewUtils.getRawYWithPointerId;

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

import java.util.Objects;

/**
 * Simple ViewGroup that expects to be hosted in a {@linkplain BottomSheetHelper} and attached to
 * an Activity with {@linkplain DecorViewManager}. Provides exit translation and animation with a
 * swipe down gesture.
 */
public class BottomSheetContainerLayout extends LinearLayout {

    private final int[] mLocationBuffer = new int[2];

    @Nullable
    private String mDecorViewKey;
    @Nullable
    private VelocityTracker mVelocityTracker;
    private int mFirstPointerId;
    private float mStartRawX, mStartRawY;
    private boolean mDroppedEvent;
    private boolean mExecutingAnimation;

    public BottomSheetContainerLayout(Context context) {
        this(context, null);
    }

    public BottomSheetContainerLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BottomSheetContainerLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void attachDecorView(String handle) {
        mDecorViewKey = handle;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case ACTION_DOWN:
                if (mExecutingAnimation || !canInterceptEventAtPosition(this, ev)) {
                    mDroppedEvent = true;
                    return false;
                }
                mFirstPointerId = ev.getPointerId(ev.getActionIndex());
                mStartRawX = ev.getRawX();
                mStartRawY = ev.getRawY();
                mVelocityTracker = VelocityTracker.obtain();
                addMovementToVelocityTracker(ev);
                mDroppedEvent = false;
                return false;
            case ACTION_MOVE:
                if (mDroppedEvent ||
                    !ViewUtils.exceedsSlopInActionMove(
                        ev, mFirstPointerId, mStartRawX, mStartRawY, this)) {
                    addMovementToVelocityTracker(ev);
                    return false;
                }
                final int firstPointerIdx = ev.findPointerIndex(mFirstPointerId);
                if (firstPointerIdx == -1) {
                    return false;
                }
                addMovementToVelocityTracker(ev);
                final float xDelta = getRawXWithPointerId(this, ev, mFirstPointerId) - mStartRawX;
                final float yDelta = getRawYWithPointerId(this, ev, mFirstPointerId) - mStartRawY;
                if (yDelta < 0 || Math.abs(xDelta) > Math.abs(yDelta)) {
                    mDroppedEvent = true;
                    return false;
                }

                // We have a mostly vertical scroll down that exceeds slop -- steal it!
                return true;
            case ACTION_POINTER_UP:
                if (ev.getPointerId(ev.getActionIndex()) == mFirstPointerId) {
                    // Initial pointer is up; we should stop tracking this event
                    mDroppedEvent = true;
                }
                return false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case ACTION_MOVE: {
                final int firstPointerIdx = ev.findPointerIndex(mFirstPointerId);
                if (firstPointerIdx == -1) {
                    return false;
                }
                final float yDelta = getRawYWithPointerId(this, ev, firstPointerIdx) - mStartRawY;
                updateBottomSheetLocation(yDelta);
                addMovementToVelocityTracker(ev);
                return true;
            }
            case ACTION_UP:
            case ACTION_POINTER_UP: {
                final int actionPointerId = ev.getPointerId(ev.getActionIndex());
                if (actionPointerId == mFirstPointerId) {
                    if (ViewUtils.exceedsSlopInActionMove(
                            ev, mFirstPointerId, mStartRawX, mStartRawY, this)
                    ) {
                        addMovementToVelocityTracker(ev);
                        commitAnimation();
                    } else {
                        updateBottomSheetLocation(0);
                    }
                    return true;
                } else {
                    return false;
                }
            }
            case ACTION_CANCEL: {
                if (!mExecutingAnimation) {
                    updateBottomSheetLocation(0);
                    if (mVelocityTracker != null) {
                        mVelocityTracker.recycle();
                    }
                }
                return true;
            }
        }
        return true;
    }

    /**
     * Trigger an animation to the end or top based on the current velocity + acceleration of the
     * bottom sheet
     */
    private void commitAnimation() {
        @Nullable final View v = getTargetView();
        if (v == null) {
            return;
        }

        mExecutingAnimation = true;
        mVelocityTracker.computeCurrentVelocity(
            1000, // px/s
            ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity());
        final float speed = mVelocityTracker.getYVelocity(mFirstPointerId);
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
                this, new FloatPropertyCompat<>("translationY") {
                @Override
                public float getValue(BottomSheetContainerLayout object) {
                    return isDown ? getTranslationY() : startPoint - getTranslationY();
                }

                @Override
                public void setValue(BottomSheetContainerLayout object, float value) {
                    updateBottomSheetLocation(isDown ? value : startPoint - value);
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

    private void addMovementToVelocityTracker(MotionEvent ev) {
        @Nullable VelocityTracker vt = mVelocityTracker;
        if (vt == null) {
            return;
        }
        ev.offsetLocation(0, getTranslationY());
        vt.addMovement(ev);
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
            child.getLocationOnScreen(mLocationBuffer);
            if (mLocationBuffer[0] <= rawX &&
                rawX <= mLocationBuffer[0] + child.getWidth() &&
                mLocationBuffer[0] <= rawY &&
                rawY <= mLocationBuffer[1] + child.getHeight()) {
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
