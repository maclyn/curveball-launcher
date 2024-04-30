package com.inipage.homelylauncher.views;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.inipage.homelylauncher.drawer.BitmapView;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.ViewUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

import static android.view.DragEvent.ACTION_DRAG_ENDED;
import static android.view.DragEvent.ACTION_DRAG_ENTERED;
import static android.view.DragEvent.ACTION_DRAG_EXITED;
import static android.view.DragEvent.ACTION_DRAG_LOCATION;
import static android.view.DragEvent.ACTION_DRAG_STARTED;
import static android.view.DragEvent.ACTION_DROP;
import static android.view.View.VISIBLE;

/**
 * Android's View dragging infra unfortunately isn't suitable for drag-to-create-new-page-and-drop
 * -there logic, since we have to create the new page on the fly, and thus a new View, which is
 * unable to respond to ACTION_DRAG_STARTED to continue receiving events. To address this, we supply
 * our own per-Activity object that handles a "startDrag" by attaching a Bitmap of the provided view
 * to the Activity's DecorView, and passing registered listeners "drag" events.
 */
public class DecorViewDragger {

    /**
     * Android-esque event representing a drag and drop operation.
     */
    public static class DragEvent {

        private final int[] mTempOut = new int[2];
        private final Object mLocalState;
        private final int mAction;
        private final int mRawX;
        private final int mRawY;
        private final int mOffsetX;
        private final int mOffsetY;

        private DragEvent(
            Object localState,
            int action,
            int rawX,
            int rawY,
            int offsetX,
            int offsetY)
        {
            mLocalState = localState;
            mAction = action;
            mRawX = rawX;
            mRawY = rawY;
            mOffsetX = offsetX;
            mOffsetY = offsetY;
        }

        public Object getLocalState() {
            return mLocalState;
        }

        public int getAction() {
            return mAction;
        }

        public int getOffsetX() {
            return mOffsetX;
        }

        public int getOffsetY() {
            return mOffsetY;
        }

        public int getRawXOffsetByView(View v) {
            v.getLocationOnScreen(mTempOut);
            return getRawX() - mTempOut[0];
        }

        public int getRawX() {
            return mRawX;
        }

        public int getRawYOffsetByView(View v) {
            v.getLocationOnScreen(mTempOut);
            return getRawY() - mTempOut[1];
        }

        public int getRawY() {
            return mRawY;
        }
    }

    /**
     * Some system component that wants to know about ongoing drag events (e.g. to change visual
     * appearance or collapse some menu) but doesn't expect the drag event to end on a surface it
     * manages.
     */
    public interface DragAwareComponent {

        /**
         * @param v     The target view.
         * @param event The "DragEvent".
         */
        void onDrag(View v, DragEvent event);
    }

    /**
     * Component that expects a view to be dropped on top of it. These components are logically
     * but not ViewGroup-wise z-index aware, so components with higher values returned from
     * {@linkplain TargetedDragAwareComponent#getPriority()} will receive the events first
     * regardless of whether they are beneath another component in the view hierarchy.
     */
    public interface TargetedDragAwareComponent extends DragAwareComponent {

        /**
         * @return The view that's supposed to receive these drags.
         */
        @Nullable
        View getDragAwareTargetView();

        /**
         * @return The priority of this drag-aware view. Higher numbers represent lower priorities.
         */
        default int getPriority() {
            return DRAG_PRIORITY_DEFAULT;
        }
    }

    public static final int DRAG_PRIORITY_HIGHEST = -100;
    public static final int DRAG_PRIORITY_DEFAULT = 0;
    public static final int DRAG_PRIORITY_LOWEST = 100;

    private static final WeakHashMap<Activity, DecorViewDragger> s_DRAGGER_MAP =
        new WeakHashMap<>();

    private final WeakReference<Activity> mActivityRef;
    private final List<TargetedDragAwareComponent> mRegisteredComponents = new ArrayList<>();
    private final List<DragAwareComponent> mBackgroundComponents = new ArrayList<>();
    private boolean mInDrag;
    private boolean mDragComplete;
    private boolean mDragSuccessful;
    private int mCurrentX, mCurrentY;
    private int mOffsetX, mOffsetY;
    @Nullable
    private String mDragKey;
    @Nullable
    private Object mLocalState;
    @Nullable
    private TargetedDragAwareComponent mLastComponent;

    private DecorViewDragger(Activity activity) {
        mActivityRef = new WeakReference<>(activity);
        mInDrag = mDragComplete = false;
        mOffsetX = mOffsetY = -1;
        mLocalState = null;
    }

    public static DecorViewDragger get(Context context) {
        return get(ViewUtils.requireActivityOf(context));
    }

    public static DecorViewDragger get(Activity activity) {
        if (s_DRAGGER_MAP.get(activity) == null) {
            s_DRAGGER_MAP.put(activity, new DecorViewDragger(activity));
        }
        return s_DRAGGER_MAP.get(activity);
    }

    public synchronized void startDrag(
        View view,
        Object localState,
        boolean centerTouchOnView,
        final int startX,
        final int startY
    ) {
        if (mInDrag) {
            DebugLogUtils.complain(view, "[debug] Drag started when one is ongoing!");
            return;
        }

        log("Starting drag w/ startX=" + startX + " && startY=" + startY);
        mInDrag = true;
        mLocalState = localState;

        // (1) Draw the view out as a Bitmap
        // This presumes the view has already been measured and laid out, and getWidth()
        // getHeight() and the like return sensible values
        // Wipe out pressed + focus states, since this is usually part of a hold-and-drag
        // operation
        view.clearAnimation();
        view.clearFocus();
        view.setPressed(false);

        final int height = view.getHeight();
        final int width = view.getWidth();
        mCurrentX = startX;
        mCurrentY = startY;
        if (centerTouchOnView) {
            mOffsetY = -(height / 2);
            mOffsetX = -(width / 2);
        } else {
            final int[] viewLocationOut = new int[2];
            view.getLocationOnScreen(viewLocationOut);
            mOffsetX = viewLocationOut[0] - startX;
            mOffsetY = viewLocationOut[1] - startY;
        }
        if (height == 0 || width == 0) {
            return;
        }
        final Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas drawingCanvas = new Canvas(newBitmap);
        view.draw(drawingCanvas);

        // (2) Create a BitmapView
        final BitmapView dragView = new BitmapView(view.getContext());
        dragView.setAlpha(0.8F);
        dragView.setBitmap(newBitmap);

        // (3) Attach the view as a top-level view
        mDragKey =
            DecorViewManager
                .get(mActivityRef.get())
                .attachView(
                    dragView,
                    new DecorViewManager.Callback() {
                        @Override
                        public boolean canBeDismissedWithBackgroundTap() {
                            return false;
                        }
                    },
                    width,
                    height,
                    startX + mOffsetX,
                    startY + mOffsetY);

        // Post a _STARTED event to every component
        for (TargetedDragAwareComponent component : mRegisteredComponents) {
            sendDragEvent(component, ACTION_DRAG_STARTED);
        }
        broadcastBackgroundDragEvent(ACTION_DRAG_STARTED);
        @Nullable TargetedDragAwareComponent component = findRelevantComponent();
        if (component != null) {
            sendDragEvent(component, ACTION_DRAG_ENTERED);
            mLastComponent = component;
        }
    }

    public boolean isDragActive() {
        return mInDrag;
    }

    public synchronized boolean onDragMoveEvent(float currentX, float currentY) {
        DebugLogUtils.needle(
            DebugLogUtils.TAG_CUSTOM_TOUCHEVENTS,
            "onDragMoveEvent x=" + currentX + ", y=" + currentY);
        return onDragMoveEvent((int) currentX, (int) currentY);
    }

    public synchronized boolean onDragMoveEvent(int currentX, int currentY) {
        mCurrentX = currentX;
        mCurrentY = currentY;
        recalculate();
        return true;
    }

    public synchronized boolean onDragEndEvent(float currentX, float currentY) {
        return onDragEndEvent((int) currentX, (int) currentY);
    }

    public synchronized boolean onDragEndEvent(int currentX, int currentY) {
        if (!isValidDragEventOngoing()) {
            return false;
        }
        mCurrentX = currentX;
        mCurrentY = currentY;
        mDragComplete = true;
        mDragSuccessful = true;
        recalculate();
        return true;
    }

    public synchronized boolean onDragCancelEvent() {
        if (!isValidDragEventOngoing()) {
            return false;
        }
        log("Drag cancelled forwarded");
        mDragComplete = true;
        mDragSuccessful = false;
        recalculate();
        return true;
    }

    /**
     * On a new touch event or view location change, recalculate the drag. Externally, this should
     * only be invoked when a listener-view changes position onscreen. We *could* listen to global
     * layout events for each listener-view, but when many are changes (e.g. page swipe) this would
     * cause duplicated events, so we leave it to consumers to do that.
     */
    public synchronized void update() {
        log("Update called");
        if (!mInDrag) {
            log("Update dropped; not in drag...");
            return;
        }
        recalculate();
    }

    /**
     * @param dragAwareComponent The component to register. Registration during a drag operation is
     *                           considered defined behavior; the newly registered component will at
     *                           least receive a DRAG_STARTED in this case.
     */
    public synchronized void registerDragAwareComponent(TargetedDragAwareComponent dragAwareComponent) {
        log("Adding listener");
        mRegisteredComponents.add(dragAwareComponent);
        mRegisteredComponents.sort(Comparator.comparingInt(TargetedDragAwareComponent::getPriority));
        if (mInDrag) {
            sendDragEvent(dragAwareComponent, ACTION_DRAG_STARTED);
        }
        recalculate();
    }

    /**
     * @param dragAwareComponent The component to unregister. Note that removal during a drag
     *                           operation is undefined behavior.
     */
    public synchronized void unregisterDragAwareComponent(TargetedDragAwareComponent dragAwareComponent) {
        log("Removing listener");
        mRegisteredComponents.remove(dragAwareComponent);
        recalculate();
    }

    /**
     * @param dragAwareComponent Register a component to receive all DRAG_STARTED, DRAG_LOCATION,
     *                           and DRAG_ENDED events. This is useful for determining where a drag
     *                           event is happening on screen, for things such as knowing when to
     *                           switch screen pages.
     */
    public synchronized void registerBackgroundDragAwareComponent(DragAwareComponent dragAwareComponent) {
        log("Adding background listener");
        mBackgroundComponents.add(dragAwareComponent);
        if (mInDrag) {
            sendBackgroundDragEvent(dragAwareComponent, ACTION_DRAG_STARTED);
        }
        recalculate();
    }

    public synchronized void unregisterBackgroundDragAwareComponent(DragAwareComponent dragAwareComponent) {
        log("Removing background listener");
        mBackgroundComponents.remove(dragAwareComponent);
    }

    private boolean isValidDragEventOngoing() {
        if (!mInDrag) {
            log("Forward touch event dropped; not in drag...");
            DebugLogUtils.complain(
                mActivityRef,
                "Checked for valid drag event but none were present!");
            return false;
        }
        return true;
    }


    private void log(String... vals) {
        DebugLogUtils.needle(DebugLogUtils.TAG_DECOR_DRAGGER, 1, getClass().getSimpleName(), vals);
    }

    private void sendDragEvent(TargetedDragAwareComponent component, int action) {
        component.onDrag(
            component.getDragAwareTargetView(),
            new DragEvent(mLocalState, action, mCurrentX, mCurrentY, mOffsetX, mOffsetY));
    }

    private void broadcastBackgroundDragEvent(int action) {
        for (DragAwareComponent component : mBackgroundComponents) {
            sendBackgroundDragEvent(component, action);
        }
    }

    private void sendBackgroundDragEvent(DragAwareComponent component, int action) {
        component.onDrag(
            null,
            new DragEvent(mLocalState, action, mCurrentX, mCurrentY, mOffsetX, mOffsetY));
    }

    @Nullable
    private TargetedDragAwareComponent findRelevantComponent() {
        final int[] locationOut = new int[2];
        for (TargetedDragAwareComponent component : mRegisteredComponents) {
            final View targetView = component.getDragAwareTargetView();
            if (targetView == null) {
                continue;
            }
            if (targetView.getVisibility() != VISIBLE) {
                continue;
            }
            targetView.getLocationOnScreen(locationOut);
            final int xStart = locationOut[0];
            final int yStart = locationOut[1];
            final int xEnd = xStart + targetView.getWidth();
            final int yEnd = yStart + targetView.getHeight();
            if ((xStart <= mCurrentX && xEnd >= mCurrentX) &&
                (yStart < mCurrentY && yEnd >= mCurrentY)) {
                return component;
            }
        }
        return null;
    }

    private void recalculate() {
        log("Recalculating w/ " + mCurrentX + ", " + mCurrentY);
        if (!mInDrag) {
            log("Recalculate dropped; not in drag anymore...");
            return;
        }

        @Nullable final TargetedDragAwareComponent relevantComponent = findRelevantComponent();
        if (relevantComponent != mLastComponent) {
            log("Found new target view mid-drag");
            if (mLastComponent != null) {
                sendDragEvent(mLastComponent, ACTION_DRAG_EXITED);
            }
            if (relevantComponent != null) {
                sendDragEvent(relevantComponent, ACTION_DRAG_ENTERED);
            }
            mLastComponent = relevantComponent;
        } else {
            if (relevantComponent != null) {
                sendDragEvent(relevantComponent, ACTION_DRAG_LOCATION);
            }
        }
        broadcastBackgroundDragEvent(ACTION_DRAG_LOCATION);

        if (mDragComplete) {
            DecorViewManager.get(mActivityRef.get()).removeView(mDragKey);
            mDragKey = null;
            mInDrag = mDragComplete = false;

            // Post a _DROP event if there's a relevant component and we dropped on it
            if (mDragSuccessful && relevantComponent != null) {
                sendDragEvent(relevantComponent, ACTION_DROP);
            }

            // Post an _ENDED event to every component
            for (TargetedDragAwareComponent component : mRegisteredComponents) {
                sendDragEvent(component, ACTION_DRAG_ENDED);
            }
            broadcastBackgroundDragEvent(ACTION_DRAG_ENDED);
            mInDrag = false;
        } else {
            DecorViewManager.get(mActivityRef.get()).updateViewPosition(
                mDragKey, mCurrentX + mOffsetX, mCurrentY + mOffsetY);
        }
    }
}
