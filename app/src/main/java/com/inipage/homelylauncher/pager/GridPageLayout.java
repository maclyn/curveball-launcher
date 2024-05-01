package com.inipage.homelylauncher.pager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.inipage.homelylauncher.state.GridDropStateMachine;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.DecorViewDragger;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import static com.inipage.homelylauncher.utils.ViewUtils.getRawXWithPointerId;
import static com.inipage.homelylauncher.utils.ViewUtils.getRawYWithPointerId;

import java.lang.ref.WeakReference;

/**
 * Container for the grid. Handles stealing events for big swipes down and up, and long presses on
 * blank spaces in the background, which can't be done with a direct touch listener on the
 * background.
 */
public class GridPageLayout extends FrameLayout {

    public interface Listener {

        /**
         * @return If we should continue to handle events after the long press.
         */
        boolean onLongPress(int rawX, int rawY);

        void onEventAfterLongPress(
            MotionEvent event, int action, int firstPointerId, float startRawX, float startRawY);

        void onSwipeUpStarted(MotionEvent event, int firstPointerId, float startRawY);

        void onEventAfterSwipeUp(
            MotionEvent event, int action, int firstPointerId, float startRawY);

        void onSwipeDown();

        void onUnhandledTouchUpInGridLayoutBounds(MotionEvent event);
    }

    private enum GestureDetectedState {
        UNDECIDED,
        FORWARD_AFTER_LONG_PRESS,
        FORWARD_AFTER_SWIPE_UP,
        EVENT_DEAD
    }

    private final LongPressHandler mHandler;
    private final long mLongPressTimeout;

    @Nullable private Listener mListener;

    private GestureDetectedState mDetectedState;
    private int mFirstPointerId;
    private float mStartRawX;
    private float mStartRawY;

    public GridPageLayout(@NonNull Context context) {
        this(context, null);
    }

    public GridPageLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GridPageLayout(
        @NonNull Context context,
        @Nullable AttributeSet attrs,
        int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        mHandler = new LongPressHandler(this);
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
    }

    public synchronized void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    @Override
    public synchronized boolean onInterceptTouchEvent(MotionEvent event) {
        log("onInterceptTouchEvent");
        if (mListener == null) {
            return false;
        }
        if (mDetectedState == GestureDetectedState.FORWARD_AFTER_LONG_PRESS &&
            event.getActionMasked() != ACTION_DOWN)
        {
            log("Long press happened; requesting intercept");
            return true;
        }

        switch (event.getActionMasked()) {
            case ACTION_DOWN:
                log("ACTION_DOWN");
                if (DecorViewDragger.get(getContext()).isDragActive()) {
                    log("Skipping touch handling because a drag is already active!");
                    mDetectedState = GestureDetectedState.EVENT_DEAD;
                    return false;
                }
                mDetectedState = GestureDetectedState.UNDECIDED;
                mFirstPointerId = event.getPointerId(event.getActionIndex());
                mStartRawX = event.getRawX();
                mStartRawY =  event.getRawY();
                mHandler.sendMessageAtTime(
                    Message.obtain(
                        mHandler,
                        LongPressHandler.GESTURE_LONG_PRESS),
                    SystemClock.uptimeMillis() + mLongPressTimeout);
                return false;
            case ACTION_MOVE: {
                log("ACTION_MOVE");
                if (mDetectedState == GestureDetectedState.EVENT_DEAD) {
                    return false;
                }
                int firstPointerIdx = event.findPointerIndex(mFirstPointerId);
                if (firstPointerIdx == -1) {
                    log("Ignoring ACTION_MOVE missing first pointer");
                    return false;
                }
                boolean exceededSlop = ViewUtils.exceedsSlopInActionMove(
                    event, firstPointerIdx, mStartRawX, mStartRawY, this, /* slopFactor */ 3.0F);
                if (!exceededSlop) {
                    log("Move motion wasn't far enough");
                    return false;
                }
                log("Significant move detected");
                mHandler.removeMessages(LongPressHandler.GESTURE_LONG_PRESS);

                final float rawX = getRawXWithPointerId(this, event, firstPointerIdx);
                final float rawY = getRawYWithPointerId(this, event, firstPointerIdx);
                final float xDelta = rawX - mStartRawX;
                final float yDelta = rawY - mStartRawY;
                if (Math.abs(xDelta) > Math.abs(yDelta)) {
                    log("Detected horizontal scroll on gesture page; ignoring");
                    mDetectedState = GestureDetectedState.EVENT_DEAD;
                    return false;
                }

                log("Detected vertical scroll on gesture page");
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (yDelta > 0) {
                    log("Processing as swipe down");
                    mDetectedState = GestureDetectedState.EVENT_DEAD;
                    mListener.onSwipeDown();
                } else {
                    log("Processing as swipe up");
                    mDetectedState = GestureDetectedState.FORWARD_AFTER_SWIPE_UP;
                    mListener.onSwipeUpStarted(event, mFirstPointerId, mStartRawY);
                }
                // Even if we don't care about subsequent events (swipe down), we still want
                // to steal the event
                return true;
            }
            case ACTION_POINTER_UP:
            case ACTION_UP: {
                if (mDetectedState == GestureDetectedState.EVENT_DEAD) {
                    return false;
                }
                mDetectedState = GestureDetectedState.EVENT_DEAD;
                mHandler.removeMessages(LongPressHandler.GESTURE_LONG_PRESS);
                int firstPointerIdx = event.findPointerIndex(mFirstPointerId);
                if (firstPointerIdx == -1) {
                    log("Ignoring ACTION(_POINTER)_UP event for secondary pointer!");
                    return false;
                }
                mListener.onUnhandledTouchUpInGridLayoutBounds(event);
                log("Event complete in ACTION_UP");
                return false;
            }
            case ACTION_CANCEL:
                if (mDetectedState == GestureDetectedState.EVENT_DEAD) {
                    return false;
                }
                mDetectedState = GestureDetectedState.EVENT_DEAD;
                mHandler.removeMessages(LongPressHandler.GESTURE_LONG_PRESS);
                log("Event complete in ACTION_CANCEL");
                return false;
            default:
                return false;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public synchronized boolean onTouchEvent(MotionEvent event) {
        if (mListener == null || mDetectedState == GestureDetectedState.EVENT_DEAD) {
            return false;
        }

        switch (event.getActionMasked()) {
            case ACTION_MOVE: {
                int pointerIndex = event.findPointerIndex(mFirstPointerId);
                if (pointerIndex == -1) {
                    return false;
                }
                sendPostInterceptAction(event, ACTION_MOVE);
                return true;
            }
            case ACTION_POINTER_UP:
            case ACTION_UP:
                int pointerId = event.getPointerId(event.getActionIndex());
                if (pointerId != mFirstPointerId) {
                    return false;
                }
                log("ACTION_UP on first pointer; event done");
                sendPostInterceptAction(event, ACTION_UP);
                mDetectedState = GestureDetectedState.EVENT_DEAD;
                return true;
            case ACTION_CANCEL:
                log("ACTION_CANCEL; event done");
                sendPostInterceptAction(event, ACTION_CANCEL);
                mDetectedState = GestureDetectedState.EVENT_DEAD;
                return true;
        }
        return false;
    }


    /*
     * We respect this, because if we ignore it we'll miss drag events inside
     */
    @Override
    public synchronized void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        log("requestDisallowInterceptTouchEvent disallowIntercept=" + disallowIntercept);
        if (!disallowIntercept) {
            log("Allowing intercept disable (we always allow this)");
        } else {
            if (mDetectedState == GestureDetectedState.UNDECIDED) {
                // Pass to parent
                log("Allowing intercept to kill event request b/c current state is " + mDetectedState.name());
                mHandler.removeMessages(LongPressHandler.GESTURE_LONG_PRESS);
                mDetectedState = GestureDetectedState.EVENT_DEAD;
            } else {
                log("Not handling intercept during state " + mDetectedState.name());
            }
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private synchronized void onLongPressAction() {
        log("Got long press event");
        if (mDetectedState != GestureDetectedState.UNDECIDED) {
            return;
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (mListener != null) {
            boolean shouldForward = mListener.onLongPress((int) mStartRawX, (int) mStartRawY);
            if (shouldForward) {
                mDetectedState = GestureDetectedState.FORWARD_AFTER_LONG_PRESS;
            } else {
                mDetectedState = GestureDetectedState.EVENT_DEAD;
            }
        }
    }

    private void sendPostInterceptAction(MotionEvent event, int actionType) {
        @Nullable Listener listener = mListener;
        if (listener == null) {
            return;
        }
        if (mDetectedState == GestureDetectedState.FORWARD_AFTER_LONG_PRESS) {
            listener.onEventAfterLongPress(
                event, actionType, mFirstPointerId, mStartRawX, mStartRawY);
        } else if (mDetectedState == GestureDetectedState.FORWARD_AFTER_SWIPE_UP) {
            listener.onEventAfterSwipeUp(
                event, actionType, mFirstPointerId, mStartRawY);
        }
    }

    private void log(String... vals) {
        DebugLogUtils.needle(
            DebugLogUtils.TAG_CUSTOM_TOUCHEVENTS,
            1,
            getClass().getSimpleName(),
            vals);
    }

    private static class LongPressHandler extends Handler {

        private static final int GESTURE_LONG_PRESS = 1;

        private final WeakReference<GridPageLayout> mGridPageLayoutRef;

        LongPressHandler(GridPageLayout pageLayout) {
            super(Looper.getMainLooper());
            mGridPageLayoutRef = new WeakReference<>(pageLayout);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == GESTURE_LONG_PRESS) {
                @Nullable GridPageLayout parent = mGridPageLayoutRef.get();
                if (parent == null) {
                    return;
                }
                parent.onLongPressAction();
            }
        }
    }
}
