package com.inipage.homelylauncher.state;

import android.content.Context;

import java.util.HashSet;
import java.util.Set;

public final class HomeStateMachine extends StateMachine {
    public static final int DEFAULT = 1;
    public static final int APP_DRAWER = 2;
    public static final int SWIPING = 3;
    public static final int FOLDER_SWIPING = 4;
    public static final int DROPPING_ICON = 5;
    private static final String TAG = HomeStateMachine.class.getSimpleName();
    private static final Set<Integer> VALID_STATES = new HashSet<>();

    static {
        VALID_STATES.add(DEFAULT);
        VALID_STATES.add(APP_DRAWER);
        VALID_STATES.add(SWIPING);
        VALID_STATES.add(DROPPING_ICON);
        VALID_STATES.add(FOLDER_SWIPING);
    }

    public HomeStateMachine(Context context, UiThreadHandle handle) {
        super(TAG, context, handle);
    }

    @Override
    public int getStartingState() {
        return DEFAULT;
    }

    @Override
    public Set<Integer> getValidStates() {
        return VALID_STATES;
    }
}