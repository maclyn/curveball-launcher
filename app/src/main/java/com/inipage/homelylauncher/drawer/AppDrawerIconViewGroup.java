package com.inipage.homelylauncher.drawer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.DecorViewDragger;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;

/**
 * The ViewGroup holds icons in the app drawer. It's just a standard LinearLayout with some special
 * touch handling logic to support drag-and-drop to home screen pages.
 */
public class AppDrawerIconViewGroup extends LinearLayout {

    public interface Listener {
        void onLongPress(int startX, int startY);

        void onDragStarted(final int startX, final int startY);
    }

    private static final int MESSAGE_LONG_PRESS = 1;

    private final LongPressHandler mLongPressHandler;
    private final int mLongPressTimeout;
    @Nullable
    private Listener mListener;
    // Bundle of values used for every touch event
    private float mStartRawX, mStartRawY;
    private float mStartX, mStartY;
    private boolean mHasDroppedEvent;
    private boolean mHasTriggeredLongPress;
    private boolean mHasTriggeredDrag;

    public AppDrawerIconViewGroup(Context context) {
        this(context, null);
    }

    public AppDrawerIconViewGroup(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppDrawerIconViewGroup(
        Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLongPressHandler = new LongPressHandler(this);
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
    }

    public void attachListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mListener == null) {
            return false;
        }

        if (isTerminalEvent(event)) {
            if (mHasTriggeredDrag) {
                DecorViewDragger.get(getContext()).forwardTouchEvent(event);
            }
            final boolean didHitCustomLogic =
                mHasTriggeredLongPress || mHasTriggeredDrag;
            resetFlags();
            if (!didHitCustomLogic && event.getAction() == MotionEvent.ACTION_UP) {
                performClick();
            }
            return didHitCustomLogic;
        } else if (mHasDroppedEvent) {
            return false;
        } else if (mHasTriggeredDrag) {
            DecorViewDragger.get(getContext()).forwardTouchEvent(event);
            return true;
        } else if (mHasTriggeredLongPress) {
            if (hasExceededSlop(event)) {
                requestDisallowInterceptTouchEvent(true);
                mListener.onDragStarted((int) event.getRawX(), (int) event.getRawY());
                mHasTriggeredDrag = true;
                return true;
            } else {
                return false;
            }
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                log("ACTION_DOWN event; posting long press");
                mLongPressHandler.sendEmptyMessageDelayed(MESSAGE_LONG_PRESS, mLongPressTimeout);
                mStartRawX = event.getRawX();
                mStartRawY = event.getRawY();
                mStartX = event.getX();
                mStartY = event.getY();
                setPressed(true);
                break;
            case MotionEvent.ACTION_MOVE:
                log("ACTION_MOVE event before long press");
                if (hasExceededSlop(event)) {
                    log("MOVE exceeded slop before long press; preventing drag from happening");
                    mLongPressHandler.removeCallbacksAndMessages(null);
                    mHasDroppedEvent = true;
                }
                break;
        }
        return true;
    }

    private boolean isTerminalEvent(MotionEvent event) {
        return event.getAction() == MotionEvent.ACTION_UP ||
            event.getAction() == MotionEvent.ACTION_CANCEL;
    }

    private void resetFlags() {
        setPressed(false);
        mHasDroppedEvent = mHasTriggeredLongPress = mHasTriggeredDrag = false;
        mStartRawX = mStartRawY = -1;
        mLongPressHandler.removeCallbacksAndMessages(null);
    }

    private boolean hasExceededSlop(MotionEvent event) {
        return ViewUtils.exceedsSlop(event, mStartRawX, mStartRawY, getContext());
    }

    private void log(String... vals) {
        DebugLogUtils.needle(DebugLogUtils.TAG_CUSTOM_TOUCHEVENTS, vals);
    }

    private static class LongPressHandler extends Handler {

        WeakReference<AppDrawerIconViewGroup> mParent;

        LongPressHandler(AppDrawerIconViewGroup parent) {
            super(Looper.getMainLooper());
            mParent = new WeakReference<>(parent);
        }

        @Override
        public void handleMessage(@NotNull Message msg) {
            @Nullable
            AppDrawerIconViewGroup parent = mParent.get();
            if (parent == null) {
                return;
            }
            @Nullable Listener listener = parent.mListener;
            if (listener == null) {
                return;
            }
            parent.log("Long press handler triggered");
            parent.setPressed(false);

            // Potentially needed in the adjustPan soft input case
            final int left = parent.getLeft(),
                top = parent.getTop(),
                stableInsetLeft = parent.getRootWindowInsets().getStableInsetLeft(),
                stableInsetTop = parent.getRootWindowInsets().getStableInsetTop();
            int x = left + stableInsetLeft + (int) parent.mStartX;
            int y = top + stableInsetTop + (int) parent.mStartY;
            listener.onLongPress(x, y);
            parent.mHasTriggeredLongPress = true;
        }
    }
}
