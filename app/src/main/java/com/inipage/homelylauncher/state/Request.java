package com.inipage.homelylauncher.state;

public class Request {
    private final Type mRequestType;
    private int mState;
    private int mDuration;
    private float mIncrement;

    private Request(Type requestType) {
        this.mRequestType = requestType;
    }

    private Request(Type requestType, int state) {
        this.mRequestType = requestType;
        this.mState = state;
    }

    private Request(Type requestType, float increment) {
        this.mRequestType = requestType;
        this.mIncrement = increment;
    }

    private Request(Type requestType, int state, int duration) {
        this.mRequestType = requestType;
        this.mState = state;
        this.mDuration = duration;
    }

    public static Request immediate(int state) {
        return new Request(Type.IMMEDIATE, state);
    }

    public static Request timed(int state, int duration) {
        return new Request(Type.TIMED, state, duration);
    }

    public static Request incremental(int state) {
        return new Request(Type.INCREMENTAL, state);
    }

    public static Request increment(float increment) {
        return new Request(Type.INCREMENT, increment);
    }

    public static Request increment(float amount, float max) {
        return new Request(Type.INCREMENT, amount / max);
    }

    public static Request abort() {
        return new Request(Type.ABORT);
    }

    public static Request commit() {
        return new Request(Type.COMMIT);
    }

    public static Request fittedAbort() {
        return new Request(Type.FITTED_ABORT);
    }

    public static Request fittedCommit() {
        return new Request(Type.FITTED_COMMIT);
    }

    protected int getState() {
        return mState;
    }

    protected Type getRequestType() {
        return mRequestType;
    }

    protected int getDuration() {
        return mDuration;
    }

    protected float getIncrement() {
        return mIncrement;
    }

    public enum Type {
        IMMEDIATE,
        TIMED,
        INCREMENTAL,
        INCREMENT,
        ABORT,
        COMMIT,
        FITTED_ABORT,
        FITTED_COMMIT
    }
}
