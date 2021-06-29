package com.inipage.homelylauncher.grid;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Display;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Places {@linkplain GridViewHolder}s on the {@linkplain GridPageController}.
 */
public class GridChoreographer {

    interface Callback {

        void onChangesCommitted(Point cellCommitted);
    }

    private static final AccelerateDecelerateInterpolator INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private static final long HINT_DURATION = 100L;
    private static final long PAUSE_DURATION = 500L;
    private static final long COMMIT_DURATION = 250L;
    private static final long REVERT_DURATION = 100L;

    private enum ChangeState {
        /** Moving a little bit of the way to the destination to indicate a potential solution. */
        HINTING(),
        /** Waiting to go the rest of the way until nothing else has changed. */
        PAUSING(),
        /** Moving from the hint to final position; at this point the change has actually been committed. */
        COMMITTING(),
        /** Reverting from HINTING/PAUSING back to the original location. */
        REVERTING()
    }

    private static class ChangeElement {

        private final GridViewHolder mHolder;
        private int mStartTranslationX;
        private int mStartTranslationY;
        private int mEndTranslationX;
        private int mEndTranslationY;

        public ChangeElement(GridViewHolder holder) {
            mHolder = holder;
            rebaseForCommitOrHint();
        }

        public GridViewHolder getHolder() {
            return mHolder;
        }

        public void rebaseForCommitOrHint() {
            mStartTranslationX = (int) getHolder().mRootView.getTranslationX();
            mStartTranslationY = (int) getHolder().mRootView.getTranslationY();
            mEndTranslationX = getHolder().getQueuedTranslationPx().x;
            mEndTranslationY = getHolder().getQueuedTranslationPx().y;
        }

        public void rebaseForRevert() {
            mStartTranslationX = (int) getHolder().mRootView.getTranslationX();
            mStartTranslationY = (int) getHolder().mRootView.getTranslationY();
            mEndTranslationX = getHolder().getPositionPx().x;
            mEndTranslationY = getHolder().getPositionPx().y;
        }

        public void updateAnimatedPosition(float scale, float interpolatedPercent) {
            mHolder.mRootView.setTranslationX(
                mStartTranslationX +
                    ((mEndTranslationX - mStartTranslationX) * scale * interpolatedPercent));
            mHolder.mRootView.setTranslationY(
                mStartTranslationY +
                    ((mEndTranslationY - mStartTranslationY) * scale * interpolatedPercent));
        }

        @Override
        public int hashCode() {
            return mHolder.hashCode();
        }
    }

    private static class ChangeSet {

        private final Set<ChangeElement> mChangedElements;
        private final Point mTargetCell;
        private long mStartTime;
        private long mAnimDuration;
        private ChangeState mState;

        ChangeSet(Set<ChangeElement> changeElements, Point targetCell) {
            mChangedElements = changeElements;
            mTargetCell = targetCell;
            mStartTime = System.currentTimeMillis();
            mAnimDuration = HINT_DURATION;
            mState = ChangeState.HINTING;
        }

        Set<ChangeElement> getChangeElements() {
            return mChangedElements;
        }

        long getStartTime() {
            return mStartTime;
        }

        long getAnimDuration() {
            return mAnimDuration;
        }

        ChangeState getState() {
            return mState;
        }

        public Point getTargetCell() {
            return mTargetCell;
        }

        void update(long startTime, long animDuration, ChangeState state) {
            mStartTime = startTime;
            mAnimDuration = animDuration;
            mState = state;
        }
    }

    private class Ticker implements Handler.Callback {

        private final Handler mHandler;
        private final int mTickRate;

        Ticker(Context context) {
            mHandler = new Handler(Looper.getMainLooper(), this);
            final DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            final Display[] displays = displayManager.getDisplays();
            if (displays.length > 0) {
                mTickRate = (int) (1000 / displayManager.getDisplays()[0].getRefreshRate());
            } else {
                mTickRate = 1000 / 60;
            }
        }

        public void scheduleTick() {
            mHandler.sendMessageDelayed(Message.obtain(), mTickRate);
        }

        public void removePendingTicks() {
            mHandler.removeCallbacksAndMessages(null);
        }

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            tick();
            return true;
        }
    }

    private final Ticker mTicker;
    private final Callback mCallback;
    private Set<ChangeSet> mActiveChangeSets;

    GridChoreographer(Context context, Callback callback) {
        mTicker = new Ticker(context);
        mCallback = callback;
        mActiveChangeSets = new LinkedHashSet<>();
    }

    synchronized void clear() {
        queueSolve(new HashSet<>(), null);
    }

    synchronized void queueSolve(Set<GridViewHolder> newChanges, @Nullable Point cellDisplacingFor) {
        final long now = System.currentTimeMillis();
        final Set<ChangeElement> newElements = new HashSet<>();
        final Set<GridViewHolder> consideredHolders = new HashSet<>();

        // Update the existing change sets
        for (ChangeSet changeSet : mActiveChangeSets) {
            // Move existing mActiveChangeSets to new modes
            final ChangeState originalState = changeSet.getState();
            switch (originalState) {
                case HINTING:
                case PAUSING:
                    changeSet.update(now, REVERT_DURATION, ChangeState.REVERTING);
                    break;
                case COMMITTING:
                case REVERTING:
                    // These can continue as they were
                    break;
            }
            final ChangeState newState = changeSet.getState();

            Set<ChangeElement> elementsToDropForOriginalChangeSet = new HashSet<>();
            for (ChangeElement element : changeSet.getChangeElements()) {
                if (newChanges.contains(element.getHolder())) {
                    element.rebaseForCommitOrHint();
                    newElements.add(element);
                    consideredHolders.add(element.getHolder());
                    elementsToDropForOriginalChangeSet.add(element);
                } else if (originalState != newState){
                    // We're going from HINTING/PAUSING -> REVERT
                    element.rebaseForRevert();
                }
            }
            changeSet.getChangeElements().removeAll(elementsToDropForOriginalChangeSet);
        }
        mActiveChangeSets = mActiveChangeSets.parallelStream()
            .filter(changeSet -> !changeSet.getChangeElements().isEmpty())
            .collect(Collectors.toSet());

        for (GridViewHolder newChange : newChanges) {
            if (consideredHolders.contains(newChange)) {
                continue;
            }
            newElements.add(new ChangeElement(newChange));
        }
        if (!newElements.isEmpty()) {
            mActiveChangeSets.add(new ChangeSet(newElements, cellDisplacingFor));
        }
        if (!mActiveChangeSets.isEmpty()) {
            tick();
        }
    }

    synchronized void halt() {
        mTicker.removePendingTicks();
        for (ChangeSet set : mActiveChangeSets) {
            for (ChangeElement changeElement : set.getChangeElements()) {
                changeElement.getHolder().resetTranslation();
                changeElement.getHolder().clearQueuedTranslation();
            }
        }
        mActiveChangeSets = new HashSet<>();
    }

    synchronized private void tick() {
        Set<ChangeSet> toDrop = new HashSet<>();
        final long now = System.currentTimeMillis();
        for (ChangeSet changeSet : mActiveChangeSets) {
            final long timeSinceAnimationStart = Math.max(now - changeSet.getStartTime(), 0);
            final float animationPercentComplete =
                (float) timeSinceAnimationStart / Math.max(changeSet.getAnimDuration(), 1);
            final float interpolatedValue = INTERPOLATOR.getInterpolation(animationPercentComplete);
            final boolean isAnimationComplete = animationPercentComplete > 1;
            switch (changeSet.getState()) {
                case HINTING:
                    // Move, or move to pausing
                    if (isAnimationComplete) {
                        changeSet.update(now, PAUSE_DURATION, ChangeState.PAUSING);
                    } else {
                        for (ChangeElement element : changeSet.getChangeElements()) {
                            element.updateAnimatedPosition(0.2F, interpolatedValue);
                        }
                    }
                    break;
                case PAUSING:
                    // Move, or move to committing
                    if (isAnimationComplete) {
                        changeSet.update(now, COMMIT_DURATION, ChangeState.COMMITTING);
                        for (ChangeElement element : changeSet.getChangeElements()) {
                            element.rebaseForCommitOrHint();
                            element.getHolder().commitTranslationChange();
                        }
                        mCallback.onChangesCommitted(changeSet.getTargetCell());
                    }
                    break;
                case COMMITTING:
                case REVERTING:
                    // Move, or set final position + add toDrop
                    if (isAnimationComplete) {
                        for (ChangeElement element : changeSet.getChangeElements()) {
                            element.getHolder().resetTranslation();
                        }
                        toDrop.add(changeSet);
                    } else {
                        for (ChangeElement element : changeSet.getChangeElements()) {
                            element.updateAnimatedPosition(1F, interpolatedValue);
                        }
                    }
                    break;
            }
        }
        mActiveChangeSets.removeAll(toDrop);
        if (!mActiveChangeSets.isEmpty()) {
            mTicker.scheduleTick();
        }
    }
}
