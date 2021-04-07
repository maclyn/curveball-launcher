package com.inipage.homelylauncher.state;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * State machine for holding the state of a surface. Conceptually, helps combined views +
 * controllers work a little less badly by providing orchestration of when to apply view state.
 */
public abstract class StateMachine {
    public static int STATE_INVALID = Integer.MIN_VALUE;
    private final String mTag;
    private final int mAnimationFrequency;
    private final UiThreadHandle mUIThreadHandle;
    private final CopyOnWriteArrayList<StateListener> mListeners;
    private final List<Pair<Long, Float>> mObservedPoints;
    private int mLastState;
    private int mCurrentState;
    private float mTransitionPercent;
    private TransitionState mTransitionState;
    private Timer mTimer;

    public StateMachine(String tag, Context context, UiThreadHandle handle) {
        this.mTag = tag;
        this.mUIThreadHandle = handle;
        this.mLastState = STATE_INVALID;
        this.mCurrentState = getStartingState();
        this.mTransitionState = TransitionState.NO_TRANSITION;
        this.mTransitionPercent = 0.0F;
        this.mObservedPoints = new ArrayList<>();
        this.mListeners = new CopyOnWriteArrayList<>();
        this.mTimer = new Timer();
        this.mAnimationFrequency =
            (int) (
                1000 /
                    ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                        .getDefaultDisplay()
                        .getRefreshRate());
    }

    protected abstract int getStartingState();

    public void registerListener(StateListener listener) {
        mListeners.add(listener);
    }

    public void unregisterListener(StateListener listener) {
        if (!mListeners.contains(listener)) {
            throw new RuntimeException("Tried to unregister an unknown listener!");
        }
        mListeners.remove(listener);
    }

    public int request(StateChange request) {
        @Nullable StateChange.Transaction t;
        @Nullable Request requestToProcess = null;
        synchronized (this) {
            while ((t = request.getTransactions().poll()) != null) {
                if (!validate(t.getPredicate())) {
                    continue;
                }
                requestToProcess = t.getRequest();
                break;
            }
        }
        if (requestToProcess != null) {
            switch (requestToProcess.getRequestType()) {
                case IMMEDIATE:
                    doImmediateChange(requestToProcess.getState());
                    break;
                case TIMED:
                    startTimedChange(requestToProcess.getState(), requestToProcess.getDuration());
                    break;
                case INCREMENTAL:
                    startIncrementalChange(requestToProcess.getState());
                    break;
                case ABORT:
                    incrementalAbort();
                    break;
                case COMMIT:
                    incrementalCommit();
                    break;
                case INCREMENT:
                    increment(requestToProcess.getIncrement());
                    break;
                case FITTED_ABORT:
                    fittedIncrementalAbort();
                    break;
                case FITTED_COMMIT:
                    fittedIncrementalCommit();
                    break;
            }
        }
        return requestToProcess != null ? requestToProcess.getState() : STATE_INVALID;
    }

    public boolean inState(int state) {
        return mCurrentState == state;
    }

    public int getState() {
        return mCurrentState;
    }

    /**
     * Switch from the current state to the new state immediately, with no animation.
     *
     * @param newState The new state.
     */
    private void doImmediateChange(int newState) {
        log("requestImmediateChange ", newState);
        mLastState = mCurrentState;
        mCurrentState = newState;
        mTransitionPercent = 0.0F;
        mTransitionState = TransitionState.NO_TRANSITION;
        for (StateListener listener : StateMachine.this.mListeners) {
            this.mUIThreadHandle.runOnUiThread(() -> listener.onEndChange(
                mLastState, mCurrentState, translateState(mCurrentState)));
        }
    }

    /**
     * Request an animated change to a new state
     *
     * @param newState The new state.
     * @param duration A minimum duration for it to take to switch to the new state.
     */
    private void startTimedChange(int newState, final long duration) {
        log("requestTimedChange ", newState);
        mLastState = mCurrentState;
        mCurrentState = newState;
        mTransitionPercent = 0.0F;
        mTransitionState = TransitionState.TIMED_TRANSITION;
        for (final StateListener listener : this.mListeners) {
            this.mUIThreadHandle.runOnUiThread(() -> listener.onStartChange(
                mLastState, mCurrentState, StateMachine.this.translateState(mCurrentState)));
        }

        final ValueAnimator animator = ObjectAnimator.ofFloat(0F, 1F);
        animator.addUpdateListener(animation -> {
            if (!animation.isRunning()) {
                return;
            }
            mTransitionPercent = animation.getAnimatedFraction();
            for (StateListener listener : StateMachine.this.mListeners) {
                listener.onIncrementalChange(
                    mLastState,
                    mCurrentState,
                    animation.getAnimatedFraction(),
                    translateState(mCurrentState));
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                log("Timed change to state done ", mCurrentState);
                mTimer.cancel();
                mTimer.purge();
                mTimer = new Timer();
                mTransitionPercent = 0.0F;
                mTransitionState = TransitionState.NO_TRANSITION;
                for (StateListener listener : StateMachine.this.mListeners) {
                    listener.onEndChange(
                        mLastState,
                        mCurrentState,
                        translateState(mCurrentState));
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                this.onAnimationEnd(animation);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    private void startIncrementalChange(int newState) {
        log("requestIncrementalChange ", newState);
        mLastState = mCurrentState;
        mCurrentState = newState;
        mTransitionPercent = 0.0F;
        mTransitionState = TransitionState.INCREMENTAL_TRANSITION;
        for (final StateListener listener : this.mListeners) {
            this.mUIThreadHandle.runOnUiThread(() -> listener.onStartChange(
                mLastState, mCurrentState, translateState(mCurrentState)));
        }
        this.mObservedPoints.clear();
        this.mObservedPoints.add(new Pair<>(System.currentTimeMillis(), 0F));
    }

    private void increment(float rawPercent) {
        if (rawPercent < 0) {
            rawPercent = 0;
        }
        mTransitionPercent = rawPercent;
        mObservedPoints.add(new Pair<>(System.currentTimeMillis(), rawPercent));
        final float finalRawPercent = rawPercent;
        for (final StateListener listener : StateMachine.this.mListeners) {
            mUIThreadHandle.runOnUiThread(() -> listener.onIncrementalChange(
                mLastState, mCurrentState, finalRawPercent, translateState(mCurrentState)));
        }
    }

    private void fittedIncrementalCommit() {
        log("notifyIncrementalFittedCommit to ", mCurrentState);
        final float velocity = calculateFlingVelocityPerMillisecond(false);
        final float startPercent = mTransitionPercent;
        final long startTime = System.nanoTime();
        final TimerTask fittedUpdateTask = new TimerTask() {
            @Override
            public void run() {
                long timeDelta = (System.nanoTime() - startTime) / 1000000;
                float newPercent = startPercent + (timeDelta * velocity);
                Log.d(mTag, "timeDelta = " + timeDelta + " newPercent = " + newPercent);
                if (newPercent >= 1) {
                    mTimer.cancel();
                    mTimer.purge();
                    incrementalCommit();
                    return;
                }
                increment(newPercent);
            }
        };
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(fittedUpdateTask, mAnimationFrequency, mAnimationFrequency);
    }

    private void incrementalCommit() {
        log("notifyIncrementalCommit to ", mCurrentState);
        if (mTransitionState != TransitionState.INCREMENTAL_TRANSITION) {
            return;
        }
        mTransitionPercent = 0.0F;
        mTransitionState = TransitionState.NO_TRANSITION;
        for (final StateListener listener : StateMachine.this.mListeners) {
            mUIThreadHandle.runOnUiThread(() -> listener.onEndChange(
                mLastState, mCurrentState, translateState(mCurrentState)));
        }
    }

    private void fittedIncrementalAbort() {
        log("notifyIncrementalFittedAbort to ", mLastState);
        final float velocity = calculateFlingVelocityPerMillisecond(true);
        final float startPercent = mTransitionPercent;
        final long startTime = System.nanoTime();
        final TimerTask fittedUpdateTask = new TimerTask() {
            @Override
            public void run() {
                long tDelta = (System.nanoTime() - startTime) / 1000000;
                float newPercent = startPercent + (tDelta * velocity);
                if (newPercent <= 0) {
                    mTimer.cancel();
                    mTimer.purge();
                    incrementalAbort();
                    return;
                }
                increment(newPercent);
            }
        };
        mTimer = new Timer();
        mTimer.schedule(fittedUpdateTask, 0, mAnimationFrequency);
    }

    private void incrementalAbort() {
        log("notifyIncrementalAbort to ", mCurrentState);
        int returningToState = mLastState;
        int returningFromState = mCurrentState;
        mCurrentState = returningToState;
        mLastState = returningFromState;
        mTransitionPercent = 0.0F;
        mTransitionState = TransitionState.NO_TRANSITION;
        for (final StateListener listener : StateMachine.this.mListeners) {
            this.mUIThreadHandle.runOnUiThread(() -> listener.onEndChange(
                mLastState, mCurrentState, translateState(mCurrentState)));
        }
    }

    private float calculateFlingVelocityPerMillisecond(boolean forAbort) {
        // Use the last 5 frames
        // Floor is 0 -> 1 in .5 second
        float fallback = 0.002F;
        float multiplier = forAbort ? -1 : 1;
        fallback *= multiplier;
        if (mObservedPoints.size() < 2) {
            return fallback;
        }
        final int lookbackIdx = mObservedPoints.size() > 5 ? (mObservedPoints.size() - 5) : 0;
        final Pair<Long, Float> lookbackPoint = mObservedPoints.get(lookbackIdx);
        final Pair<Long, Float> now = mObservedPoints.get(mObservedPoints.size() - 1);
        final long tDelta = now.first - lookbackPoint.first;
        if (tDelta <= 0) {
            return fallback;
        }
        final float calculatedSpeed = (now.second - lookbackPoint.second) / tDelta;
        if (forAbort) {
            return Math.min(calculatedSpeed, fallback);
        } else {
            return Math.max(calculatedSpeed, fallback);
        }
    }

    private boolean validateState(int state) {
        return getValidStates().contains(state);
    }

    private String translateState(int state) {
        for (Field f : getClass().getFields()) {
            try {
                if (Modifier.isStatic(f.getModifiers()) &&
                    Modifier.isFinal(f.getModifiers()) &&
                    f.getInt(OverlayStateMachine.class) == state) {
                    return f.getName();
                }
            } catch (Exception ignored) {
            }
        }
        return "???";
    }

    private void log(String message, int... states) {
        String stateString = "";
        for (int state : states) {
            stateString += translateState(state) + ", ";
        }
        Log.d(mTag, message + " w/ state=" + stateString);
    }

    private boolean validate(Predicate p) {
        return p.getState() == mCurrentState &&
            p.getTransitionState() == mTransitionState &&
            validateState(p.getState());
    }

    protected abstract Set<Integer> getValidStates();

    protected enum TransitionState {
        TIMED_TRANSITION, INCREMENTAL_TRANSITION, NO_TRANSITION
    }

    public interface UiThreadHandle {
        void runOnUiThread(Runnable runnable);
    }

    public interface StateListener {
        void onStartChange(int from, int to, String name);

        void onIncrementalChange(int from, int to, float percent, String name);

        void onEndChange(int from, int to, String name);
    }
}
