package com.inipage.homelylauncher.state;

import java.util.ArrayList;
import java.util.List;

public class Predicate {
    private final int mState;
    private final StateMachine.TransitionState mTransitionState;

    private Predicate(int state, StateMachine.TransitionState transitionState) {
        this.mState = state;
        this.mTransitionState = transitionState;
    }

    public static List<Predicate> any(int state) {
        List<Predicate> ps = new ArrayList<>(3);
        ps.add(incremental(state));
        ps.add(timed(state));
        ps.add(get(state));
        return ps;
    }

    public static Predicate incremental(int state) {
        return new Predicate(state, StateMachine.TransitionState.INCREMENTAL_TRANSITION);
    }

    public static Predicate timed(int state) {
        return new Predicate(state, StateMachine.TransitionState.TIMED_TRANSITION);
    }

    public static Predicate get(int state) {
        return new Predicate(state, StateMachine.TransitionState.NO_TRANSITION);
    }

    protected int getState() {
        return mState;
    }

    protected StateMachine.TransitionState getTransitionState() {
        return mTransitionState;
    }
}
