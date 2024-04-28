package com.inipage.homelylauncher.grid;

import android.animation.AnimatorInflater;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.TouchDelegate;
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
import com.inipage.homelylauncher.views.SingleTouchEventHelper;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;
import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

public class GridItemHandleView extends FrameLayout {

    public enum Direction {
        LEFT_UP, RIGHT_DOWN
    }

    public interface Listener {
        void onTriggered(Direction d);
    }

    private static final float ENABLED_ALPHA = 1F;
    private static final float DISABLED_ALPHA = 0F;

    private final SingleTouchEventHelper mTouchEventHelper;
    private final View mLeftTopView;
    private final View mRightBottomView;
    private final int mOrientation;

    @SizeDimenAttribute(R.dimen.scale_button_size)
    private final int mHandleSize = intValue();

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
        int defStyleAttr
    ) {
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
        mTouchEventHelper = new SingleTouchEventHelper(this, buildTouchListener());
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

    private final Rect hitRect = new Rect();

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            getHitRect(hitRect);
            final int width = hitRect.width();
            final int height = hitRect.height();
            hitRect.left -= (width / 2);
            hitRect.right += (width / 2);
            hitRect.top -= (height / 2);
            hitRect.bottom += (height / 2);
            setTouchDelegate(new TouchDelegate(hitRect, this));
        }
    }

    private void log(String... vals) {
        DebugLogUtils.needle(DebugLogUtils.TAG_GRID_HANDLE, vals);
    }

    private View addArrowButton(int arrowDrawableId, int gravity) {
        final int arrowSize = (int) (mHandleSize / 1.5);
        final ImageView arrowView = new ImageView(getContext());
        arrowView.setImageDrawable(ContextCompat.getDrawable(getContext(), arrowDrawableId));
        addView(arrowView, new FrameLayout.LayoutParams(arrowSize, arrowSize, gravity));
        return arrowView;
    }

    private boolean mDetectedSwipe = false;

    private SingleTouchEventHelper.OnSingleTouchListener buildTouchListener() {
        return (v, event, action) -> {
            if (getAlpha() == 0 || getVisibility() != VISIBLE || mListener == null) {
                return false;
            }

            switch (action) {
                case ACTION_DOWN:
                    return true;
                case ACTION_MOVE:
                    if (mDetectedSwipe) {
                        return true;
                    }

                    float rawX = mTouchEventHelper.getRawX(event);
                    float rawY = mTouchEventHelper.getRawY(event);
                    float startRawX = mTouchEventHelper.getStartRawX();
                    float startRawY = mTouchEventHelper.getStartRawY();
                    boolean exceedsSlop =
                        ViewUtils.exceedsSlop(rawX, rawY, startRawX, startRawY, this, 0.8);
                    if (!exceedsSlop) {
                        return true;
                    }

                    // Perform swipe gesture
                    mDetectedSwipe = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    final float xDelta = rawX - startRawX;
                    final float yDelta = rawY - startRawY;
                    final boolean isHorizontalScroll = Math.abs(xDelta) > Math.abs(yDelta);
                    final boolean isRightDown = isHorizontalScroll ? (xDelta > 0) : (yDelta > 0);
                    final boolean isActionValidForDirection =
                        ((isRightDown && mRightBottomEnabled) || (!isRightDown && mLeftTopEnabled));
                    boolean tookAction = false;
                    if (isHorizontalScroll &&
                        mOrientation == HORIZONTAL &&
                        isActionValidForDirection)
                    {
                        log("Detected horizontal scroll on grid item");
                        tookAction = true;
                        if (isRightDown) {
                            mListener.onTriggered(Direction.RIGHT_DOWN);
                        } else {
                            mListener.onTriggered(Direction.LEFT_UP);
                        }
                    } else if (!isHorizontalScroll &&
                        mOrientation == VERTICAL &&
                        isActionValidForDirection)
                    {
                        log("Detected vertical scroll on grid item");
                        tookAction = true;
                        if (isRightDown) {
                            mListener.onTriggered(Direction.RIGHT_DOWN);
                        } else {
                            mListener.onTriggered(Direction.LEFT_UP);
                        }
                    }

                    if (tookAction && getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return tookAction;
                case ACTION_UP:
                case ACTION_CANCEL:
                    boolean didComplete = mDetectedSwipe;
                    mDetectedSwipe = false;
                    return didComplete;
            }
            return false;
        };
    }
}
