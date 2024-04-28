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

import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

public class GesturePageLayout extends FrameLayout {

    private static final int GESTURE_LONG_PRESS = 1;
    private static final int SLOP_SCALAR = 3;
    private final LongPressHandler mHandler;
    private final long mLongPressTimeout;
    @Nullable private Listener mListener;
    private boolean mFinishedParsing;
    private boolean mForwardPostProcessEvents;
    private int mStartX;
    private int mStartY;

    public GesturePageLayout(@NonNull Context context) {
        this(context, null);
    }

    public GesturePageLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GesturePageLayout(
        @NonNull Context context,
        @Nullable AttributeSet attrs,
        int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHandler = new LongPressHandler();
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        log("onTouchEvent");

        boolean resetFlags = false;
        if (mListener == null) {
            return false;
        }
        if (event.getAction() == ACTION_UP ||
            event.getAction() == ACTION_CANCEL) {
            mHandler.removeMessages(GESTURE_LONG_PRESS);
            resetFlags = true;
        }

        // The only thing we can do here is, if relevant, eat the event and forward it
        if (mForwardPostProcessEvents) {
            log("Forwarding additional data: ", event.toString());
            mListener.onAdditionalEvent(
                event, event.getRawX() - mStartX, event.getRawY() - mStartY);
        }
        if (resetFlags) {
            mForwardPostProcessEvents = mFinishedParsing = false;
        }
        return true;
    }

    private void log(String... vals) {
        DebugLogUtils.needle(
            DebugLogUtils.TAG_CUSTOM_TOUCHEVENTS,
            1,
            getClass().getSimpleName(),
            vals);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        log("requestDisallowInterceptTouchEvent");

        // We skip this, because if we respond we'll miss drag event
        if (disallowIntercept) {
            if (mFinishedParsing) {
                return;
            }
            mHandler.removeMessages(GESTURE_LONG_PRESS);
            mFinishedParsing = true;
            mForwardPostProcessEvents = false;
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        log("onInterceptTouchEvent");
        if (mListener == null) {
            return false;
        }


        switch (event.getAction()) {
            // ACTION_DOWNs always reset
            case ACTION_DOWN:
                log("ACTION_DOWN");
                mFinishedParsing = mForwardPostProcessEvents = false;
                mStartX = (int) event.getRawX();
                mStartY = (int) event.getRawY();
                log("Posting long press message");
                mHandler.sendMessageAtTime(
                    Message.obtain(
                        mHandler,
                        GESTURE_LONG_PRESS,
                        (int) event.getRawX(),
                        (int) event.getRawY()),
                    SystemClock.uptimeMillis() + mLongPressTimeout);
                return false;
            case ACTION_MOVE:
                log("ACTION_MOVE");
                if (mFinishedParsing) {
                    log("Move, but finished parsing");
                    return mForwardPostProcessEvents;
                }
                if (!ViewUtils.exceedsSlop_DEPRECATED_FAILS_WHEN_MULTIPLE_POINTERS_DOWN(event, mStartX, mStartY, getContext(), SLOP_SCALAR)) {
                    log("Move motion wasn't far enough");
                    return false;
                }

                final float xDelta = event.getRawX() - mStartX;
                final float yDelta = event.getRawY() - mStartY;
                mHandler.removeMessages(GESTURE_LONG_PRESS);
                mFinishedParsing = true;
                mForwardPostProcessEvents = false;
                if (Math.abs(xDelta) > Math.abs(yDelta)) {
                    log("Detected horizontal scroll on gesture page, so failing");
                    return false;
                } else {
                    log("Detected vertical scroll on gesture page");
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (yDelta > 0) {
                        log("Processing as swipe down");
                        mListener.onSwipeDown();
                    } else {
                        log("Processing as swipe up");
                        mForwardPostProcessEvents = true;
                        mListener.onSwipeUpStarted(event, event.getRawY() - mStartY);
                    }
                    return true;
                }
            case ACTION_UP:
            case ACTION_CANCEL:
                mForwardPostProcessEvents = false;
                if (mFinishedParsing) {
                    return true;
                }
                log("Event complete by " +
                        (event.getAction() == ACTION_UP ? "ACTION_UP" : "ACTION_CANCEL"));
                mFinishedParsing = false;
                mHandler.removeMessages(GESTURE_LONG_PRESS);
                if (event.getAction() == ACTION_UP) {
                    mListener.onUnhandledTouchUp(event);
                }
                return false;
            default:
                return false;
        }
    }

    public interface Listener {
        boolean onLongPress(int x, int y);

        void onSwipeUpStarted(MotionEvent event, float deltaY);

        void onSwipeDown();

        void onAdditionalEvent(MotionEvent event, float deltaX, float deltaY);

        void onUnhandledTouchUp(MotionEvent event);
    }

    @SuppressLint("HandlerLeak")
    private class LongPressHandler extends Handler {
        LongPressHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GESTURE_LONG_PRESS:
                    log("Got long press event");
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (mListener != null) {
                        mForwardPostProcessEvents = mListener.onLongPress(mStartX, mStartY);
                    }
                    mFinishedParsing = true;
                    break;
            }
        }
    }
}
