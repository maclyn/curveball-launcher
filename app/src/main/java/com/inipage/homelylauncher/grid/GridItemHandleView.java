package com.inipage.homelylauncher.grid;

import android.animation.AnimatorInflater;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;
import com.inipage.homelylauncher.utils.ViewUtils;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

public class GridItemHandleView extends FrameLayout {

    private static final float ENABLED_ALPHA = 1F;
    private static final float DISABLED_ALPHA = 0F;
    private final View mLeftTopView;
    private final View mRightBottomView;
    private final int mOrientation;
    @SizeDimenAttribute(R.dimen.scale_button_size)
    private final int mHandleSize = intValue();
    private boolean mHasTouchDown;
    private boolean mFinishedParsing;
    private int mStartX, mStartY;
    @Nullable
    private Listener mListener;
    private boolean mLeftTopEnabled, mRightBottomEnabled;

    public GridItemHandleView(@NonNull Context context) {
        this(context, null);
    }

    public GridItemHandleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GridItemHandleView(
        @NonNull Context context,
        @Nullable AttributeSet attrs,
        int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        AttributeApplier.applyDensity(this, context);
        setBackground(ContextCompat.getDrawable(context, R.drawable.widget_button_bg_file));
        setStateListAnimator(AnimatorInflater.loadStateListAnimator(
            context,
            R.xml.touch_animate_scale));

        final TypedArray array = context.obtainStyledAttributes(
            attrs, R.styleable.GridItemHandleView, defStyleAttr, 0);
        mOrientation =
            array.getInt(
                R.styleable.GridItemHandleView_android_orientation, HORIZONTAL);
        array.recycle();
        if (mOrientation == HORIZONTAL) {
            mLeftTopView = addArrowButton(
                R.drawable.ic_arrow_drop_left_48,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
            mRightBottomView = addArrowButton(
                R.drawable.ic_arrow_drop_right_48,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        } else {
            mLeftTopView = addArrowButton(
                R.drawable.ic_arrow_drop_up_48,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            mRightBottomView = addArrowButton(
                R.drawable.ic_arrow_drop_down_48,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        }

        if (isInEditMode()) {
            setArrowsEnabled(true, true);
        }
    }

    private View addArrowButton(int arrowDrawableId, int gravity) {
        final int arrowSize = (int) (mHandleSize / 1.5);
        final ImageView arrowView = new ImageView(getContext());
        arrowView.setImageDrawable(ContextCompat.getDrawable(getContext(), arrowDrawableId));
        addView(arrowView, new FrameLayout.LayoutParams(arrowSize, arrowSize, gravity));
        return arrowView;
    }

    public void setArrowsEnabled(boolean leftUp, boolean rightDown) {
        mLeftTopEnabled = leftUp;
        mRightBottomEnabled = rightDown;
        mLeftTopView.setAlpha(mLeftTopEnabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        mRightBottomView.setAlpha(mRightBottomEnabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        setVisibility(mLeftTopEnabled || mRightBottomEnabled ? VISIBLE : GONE);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getAlpha() == 0 || getVisibility() != VISIBLE || mListener == null) {
            return false;
        }

        switch (event.getAction()) {
            case ACTION_DOWN:
                log("ACTION_DOWN");
                mHasTouchDown = true;
                mFinishedParsing = false;
                mStartX = (int) event.getRawX();
                mStartY = (int) event.getRawY();
                return true;
            case ACTION_MOVE:
                log("ACTION_MOVE");
                if (mFinishedParsing || !mHasTouchDown) {
                    log("ACTION_MOVE, but finished parsing");
                    return true;
                }
                final float xDelta = event.getRawX() - mStartX;
                final float yDelta = event.getRawY() - mStartY;
                if (!ViewUtils.exceedsSlop(event, mStartX, mStartY, getContext(), 0.8)) {
                    log("Move motion wasn't far enough for grid item");
                    return true;
                }
                mFinishedParsing = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                final boolean isHorizontalScroll = Math.abs(xDelta) > Math.abs(yDelta);
                final boolean isRightDown = isHorizontalScroll ?
                                            (xDelta > 0) :
                                            (yDelta > 0);
                final boolean isActionValidForDirection =
                    (
                        (isRightDown && mRightBottomEnabled) ||
                            (!isRightDown && mLeftTopEnabled));
                boolean tookAction = false;
                if (isHorizontalScroll && mOrientation == HORIZONTAL && isActionValidForDirection) {
                    log("Detected horizontal scroll on grid item");
                    tookAction = true;
                    if (isRightDown) {
                        mListener.onTriggered(Direction.RIGHT_DOWN);
                    } else {
                        mListener.onTriggered(Direction.LEFT_UP);
                    }
                } else if (!isHorizontalScroll && mOrientation == VERTICAL &&
                    isActionValidForDirection) {
                    log("Detected vertical scroll on grid item");
                    tookAction = true;
                    if (isRightDown) {
                        mListener.onTriggered(Direction.RIGHT_DOWN);
                    } else {
                        mListener.onTriggered(Direction.LEFT_UP);
                    }
                }
                if (tookAction) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                } else {
                    return false;
                }
            case ACTION_UP:
            case ACTION_CANCEL:
                log("Event complete by " +
                        (event.getAction() == ACTION_UP ? "ACTION_UP" : "ACTION_CANCEL"));
                return mFinishedParsing;
        }
        return false;
    }

    // No intercept needed; everything below isn't touchable

    private void log(String... vals) {
        DebugLogUtils.needle(DebugLogUtils.TAG_GRID_HANDLE, vals);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        if (disallowIntercept) {
            mFinishedParsing = true;
        }
    }

    public enum Direction {
        LEFT_UP, RIGHT_DOWN
    }

    public interface Listener {
        void onTriggered(Direction d);
    }
}
