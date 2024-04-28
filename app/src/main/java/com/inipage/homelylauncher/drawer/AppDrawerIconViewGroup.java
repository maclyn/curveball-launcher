package com.inipage.homelylauncher.drawer;

import static com.inipage.homelylauncher.utils.ViewUtils.getRawXWithPointerId;
import static com.inipage.homelylauncher.utils.ViewUtils.getRawYWithPointerId;
import static com.inipage.homelylauncher.utils.ViewUtils.requireActivityOf;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.ClassicGridItem;
import com.inipage.homelylauncher.state.LayoutEditingSingleton;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;
import com.inipage.homelylauncher.views.DecorViewDragger;
import com.inipage.homelylauncher.views.DecorViewManager;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * The ViewGroup holds icons in the app drawer. It's just a standard LinearLayout with some special
 * touch handling logic to support drag-and-drop to home screen pages.
 */
public class AppDrawerIconViewGroup extends LinearLayout {

    public interface Listener {
        void onLongPress(int startX, int startY);

        ApplicationIcon getHostedApp();

        View getAppIconView();
    }

    private static final int MESSAGE_LONG_PRESS = 1;

    private final int UNSET_INT_VALUE = -1;
    private final float UNSET_FLOAT_VALUE = Float.MIN_VALUE;
    private final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    private final LongPressHandler mLongPressHandler;

    @Nullable
    private Listener mListener;

    // Bundle of values used for every touch event that can't be looked up in subsequent
    // MotionEvents
    private int mFirstPointerId;
    private float mStartRawX, mStartRawY;
    private float mStartX, mStartY;

    // States values
    /**
     * Whether we are currently in a state where we don't care about the event, either because the
     * first pointer is already up, or because we moved before drag started
     */
    private boolean mHasDroppedEvent;
    // Whether the long press has been triggered (haven't moved too much + held down pointer)
    private boolean mHasTriggeredLongPress;
    // Whether drag has been triggered (long press triggered + moved beyond slop)
    private boolean mHasTriggeredDrag;

    public AppDrawerIconViewGroup(Context context) {
        this(context, null);
    }

    public AppDrawerIconViewGroup(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppDrawerIconViewGroup(
        Context context, @Nullable AttributeSet attrs, int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        mLongPressHandler = new LongPressHandler(this);
        resetContainerStateExceptHasDroppedEvent();
        mHasDroppedEvent = false;
    }

    public void attachListener(Listener listener) {
        Objects.requireNonNull(listener);
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mListener == null) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // This is fired when the first pointer is down
                // Theoretically multiple pointers could go down at once, but we just pick the
                // one at pointerIdx = 0
                if (mHasDroppedEvent) {
                    break;
                }
                mFirstPointerId = event.getPointerId(event.getActionIndex());
                log("ACTION_DOWN with " + mFirstPointerId);
                mStartX = event.getX();
                mStartY = event.getY();
                mStartRawX = event.getRawX();
                mStartRawY = event.getRawY();
                mLongPressHandler.sendEmptyMessageDelayed(MESSAGE_LONG_PRESS, LONG_PRESS_TIMEOUT);
                setPressed(true);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                log("ACTION_POINTER_DOWN with "
                        + event.getPointerId(event.getActionIndex())
                        + " (first pointer down was " + mFirstPointerId + ")");
                break;
            case MotionEvent.ACTION_MOVE:
                // This is fired when any pointer moves, so check if event is active
                if (mHasDroppedEvent) {
                    break;
                }
                int firstPointerIdx = event.findPointerIndex(mFirstPointerId);
                if (firstPointerIdx == -1) {
                    log("ACTION_MOVE didn't have data for first pointerIdx");
                    break;
                }

                boolean exceededSlop =
                    ViewUtils.exceedsSlopInActionMove(
                        event, mFirstPointerId, mStartRawX, mStartRawY, this);
                if (mHasTriggeredDrag) {
                    DecorViewDragger.get(getContext())
                        .onDragMoveEvent(
                            getRawXWithPointerId(this, event, mFirstPointerId),
                            getRawYWithPointerId(this, event, mFirstPointerId));
                } else if (mHasTriggeredLongPress) {
                    if (exceededSlop) {
                        // Start a synthetic drag
                        float rawX = getRawXWithPointerId(this, event, mFirstPointerId);
                        float rawY = getRawYWithPointerId(this, event, mFirstPointerId);
                        DebugLogUtils.needle(
                            DebugLogUtils.TAG_DRAG_OFFSET,
                            "Starting drag on app icon (rawX=" + rawX + "; rawY=" + rawY + ")");

                        @Nullable
                        final Activity activity = requireActivityOf(getContext());
                        final Listener listener = mListener;
                        if (listener == null) {
                            return false;
                        }
                        final AppViewHolder appViewHolder =
                            new AppViewHolder(
                                activity,
                                ClassicGridItem.getNewAppItem(listener.getHostedApp()));

                        requestDisallowInterceptTouchEvent(true);
                        LayoutEditingSingleton.getInstance().setEditing(true);
                        DecorViewManager.get(activity).detachAllViews();
                        DecorViewDragger.get(activity).startDrag(
                            listener.getAppIconView(), appViewHolder, true, (int) rawX, (int) rawY);
                        mHasTriggeredDrag = true;
                    }
                } else {
                    if (exceededSlop) {
                        log("ACTION_MOVE exceeded slop before long press; preventing drag from happening");
                        resetContainerStateExceptHasDroppedEvent();
                        mHasDroppedEvent = true;
                        return false;
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                log("ACTION_POINTER_UP");
                // This is fired when *a* pointer goes up.
                // Note we WILL NOT get this if only 1 pointer was ever down, so we need to handle
                // this carefully
                if (mHasDroppedEvent) {
                    // We don't care what happens with pointers after the event is "over" from our
                    // perspective, because we'll just get an ACTION_UP to reset on regardless
                    break;
                }
                int pointerUpId = event.getPointerId(event.getActionIndex());
                if (pointerUpId != mFirstPointerId) {
                    // Secondary pointer is up; we don't care
                    log("ACTION_POINTER_UP irrelevant because " + pointerUpId + " does not match " + mFirstPointerId);
                    break;
                }

                // Duplicated below for readability
                if (mHasTriggeredDrag) {
                    float rawX = getRawXWithPointerId(this, event, mFirstPointerId);
                    float rawY = getRawYWithPointerId(this, event, mFirstPointerId);
                    DecorViewDragger.get(getContext()).onDragEndEvent(rawX, rawY);
                } else if (mHasTriggeredLongPress) {
                    // No-op
                } else {
                    // If we didn't show the menu or start dragging, click on the View (i.e. launch app)
                    performClick();
                }

                // Primary pointer has gone up, rest of event will be dropped
                resetContainerStateExceptHasDroppedEvent();
                mHasDroppedEvent = true;
                break;
            case MotionEvent.ACTION_UP:
                log("ACTION_UP");
                // This is a terminal event! This is fired when the last pointer is up
                if (!mHasDroppedEvent) {
                    // Since we haven't dropped the event yet, this means we're finally getting the
                    // first pointer down's up
                    if (mHasTriggeredDrag) {
                        float rawX = getRawXWithPointerId(this, event, mFirstPointerId);
                        float rawY = getRawYWithPointerId(this, event, mFirstPointerId);
                        DecorViewDragger.get(getContext()).onDragEndEvent(rawX, rawY);
                    } else if (mHasTriggeredLongPress) {
                        // No-op
                    } else {
                        // If we didn't show the menu or start dragging, click on the View (i.e. launch app)
                        performClick();
                    }
                } else {
                    log("ACTION_UP irrelevant because event has already been dropped");
                }
                resetContainerStateExceptHasDroppedEvent();
                mHasDroppedEvent = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                log("ACTION_CANCEL");
                // This is a terminal event! This is fired when the event is stolen
                if (mHasTriggeredDrag) {
                    DecorViewDragger.get(getContext()).onDragCancelEvent();
                }
                resetContainerStateExceptHasDroppedEvent();
                mHasDroppedEvent = false;
                break;
        }
        return true;
    }

    private void resetContainerStateExceptHasDroppedEvent() {
        setPressed(false);
        mLongPressHandler.removeCallbacksAndMessages(null);
        mFirstPointerId = UNSET_INT_VALUE;
        mStartX = mStartY = mStartRawX = mStartRawY = UNSET_FLOAT_VALUE;
        mHasTriggeredLongPress = false;
        mHasTriggeredDrag = false;
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
            if (parent.mHasDroppedEvent || parent.mFirstPointerId == parent.UNSET_INT_VALUE) {
                // Race condition; we hit this during cleanup
                // Return early
                return;
            }
            parent.log("Long press handler triggered");
            parent.mHasTriggeredLongPress = true;
            parent.setPressed(false);

            // Potentially needed in the adjustPan soft input case
            final int left = parent.getLeft(),
                top = parent.getTop(),
                stableInsetLeft = parent.getRootWindowInsets().getStableInsetLeft(),
                stableInsetTop = parent.getRootWindowInsets().getStableInsetTop();
            int x = left + stableInsetLeft + (int) parent.mStartX;
            int y = top + stableInsetTop + (int) parent.mStartY;
            listener.onLongPress(x, y);
        }
    }
}
