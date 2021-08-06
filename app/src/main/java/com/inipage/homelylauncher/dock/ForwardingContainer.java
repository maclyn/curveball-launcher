package com.inipage.homelylauncher.dock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

/**
 * Class for forwarding events to another view. There's probably a way to not, but, eh, it works?
 * Current use is proxying views in the dock to the PocketController.
 */
public class ForwardingContainer extends RelativeLayout {

    private float mStartX;
    private float mStartY;
    private boolean mIsDropped;
    private boolean mIsForwarding;
    @Nullable
    private ForwardingListener mListener;

    public ForwardingContainer(Context context) {
        this(context, null);
    }

    public ForwardingContainer(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ForwardingContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        reset();
    }

    private void reset() {
        mStartX = mStartY = -1F;
        mIsForwarding = false;
        mIsDropped = false;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsForwarding) {
            if (mListener == null) {
                return false;
            }
            mListener.onForwardEvent(event, mStartY - event.getRawY());
            switch (event.getAction()) {
                case ACTION_UP:
                case ACTION_CANCEL:
                    reset();
            }
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                handleMoveEvent(event);
                break;
            case ACTION_CANCEL:
            case ACTION_UP:
                reset();
                return true;
            case ACTION_DOWN:
                // We'll always set these in the intercept first, so we don't need to do it here
                break;
        }
        return true;
    }

    private boolean handleMoveEvent(MotionEvent event) {
        if (mListener == null || mIsDropped) {
            return false;
        }

        final float distX = mStartX - event.getRawX();
        final float distY = mStartY - event.getRawY();
        if (!ViewUtils.exceedsSlop(event, mStartX, mStartY, getContext())) {
            return false;
        }

        if (Math.abs(distY) > Math.abs(distX)) {
            if (!mListener.shouldHandleEvent(event, distY)) {
                mIsDropped = true;
                return false;
            }
            DebugLogUtils.needle(DebugLogUtils.TAG_POCKET_ANIMATION, "Forwarding");
            mIsForwarding = true;
            return true;
        }
        return false;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // Empty: FowardingContainers are ALWAYS aware of what happens inside
        // This means ScrollViews embedded inside need special treatment
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                reset();
                mStartX = ev.getRawX();
                mStartY = ev.getRawY();
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                return handleMoveEvent(ev);
            default:
                break;
        }
        return false;
    }

    public void setForwardingListener(ForwardingListener listener) {
        this.mListener = listener;
    }

    public interface ForwardingListener {
        void onForwardEvent(MotionEvent event, float deltaY);

        boolean shouldHandleEvent(MotionEvent event, float deltaY);
    }
}
