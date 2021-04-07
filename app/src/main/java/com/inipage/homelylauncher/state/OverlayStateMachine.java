package com.inipage.homelylauncher.state;

import android.content.Context;

import java.util.HashSet;
import java.util.Set;

public class OverlayStateMachine extends StateMachine {
    public static final int STATE_NOT_SHOWING = 1;
    public static final int STATE_FOLDER = 2;
    public static final int STATE_ADD_TO_FOLDER = 3;
    public static final int STATE_APP_OPTIONS = 4;
    public static final int STATE_SEARCH_BOX = 5;
    public static final int STATE_ALTER_FAV = 6;
    private static final String TAG = OverlayStateMachine.class.getSimpleName();
    private static final Set<Integer> VALID_STATES = new HashSet<>();
    private static final int[] ALL_STATES = new int[]{
        STATE_NOT_SHOWING, STATE_FOLDER,
        STATE_ADD_TO_FOLDER, STATE_APP_OPTIONS, STATE_SEARCH_BOX, STATE_ALTER_FAV};

    static {
        VALID_STATES.add(STATE_NOT_SHOWING);
        for (int state : ALL_STATES) {
            if (state != STATE_NOT_SHOWING) {
                VALID_STATES.add(state);
            }
        }
    }

    public OverlayStateMachine(Context context, UiThreadHandle uiThreadHandle) {
        super(TAG, context, uiThreadHandle);
    }

    @Override
    public int getStartingState() {
        return STATE_NOT_SHOWING;
    }

    @Override
    public Set<Integer> getValidStates() {
        return VALID_STATES;
    }
}
