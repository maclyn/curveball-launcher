package com.inipage.homelylauncher.state

import android.content.Context
import java.util.*

class SwipeFoldersStateMachine(context: Context, handle: UiThreadHandle) :
        StateMachine(TAG, context, handle) {
    companion object {
        private val TAG = HomeStateMachine::class.java.simpleName
        const val IDLE = 1
        const val CHOOSING_FOLDER = 2
        const val CHOOSING_APP = 3
        const val DROPPING_APP = 4
        private val VALID_STATES: MutableSet<Int> = HashSet()

        init {
            VALID_STATES.add(IDLE)
            VALID_STATES.add(CHOOSING_FOLDER)
            VALID_STATES.add(CHOOSING_APP)
            VALID_STATES.add(DROPPING_APP)
        }
    }

    public override fun getValidStates(): Set<Int> = VALID_STATES
    public override fun getStartingState(): Int = IDLE
}