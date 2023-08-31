package com.inipage.homelylauncher.state

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Keeps track of the state of a grid element as its being dragged, publishing events as it goes.
 */
class GridDropStateMachine {

    enum class State(private val dwellStateTimeMs: Int? = null) {
        // Cannot move to any other state, because something's happening (e.g. page switch), or
        // because we're not over anything "droppable"
        Waiting,
        // Waiting to drop, since something is underneath and will be displaced
        Drop(1000),
        // Waiting to scroll down, since scrolling immediately would be disruptive
        ScrollDown( 500),
        // Waiting to scroll up, since scrolling immediately would be disruptive
        ScrollUp(500);

        val dwellTimeMs: Int = dwellStateTimeMs ?: 0

        val hasDwellState: Boolean = dwellStateTimeMs != null
    }

    /**
     * The main "target" of the drop that attached to by this component. Helps us figure out
     * what state we should transition to by calling various methods.
     */
    interface Delegate {
        fun canSwitchToState(state: State): Boolean

        fun onStateChanged()

        /** Useful to do something that we might want to do faster the longer we're in a state. */
        fun onStillInState(timeInStateMs: Long)
    }

    var delegate: Delegate? = null
    var state = defaultState
        private set
    private var pendingSwitch: Pair<Long, State>? = null
    private var timeOfStateSwitchMs: Long = 0L

    fun start(context: Context) {
        tick()
        // TODO: Use actual display rate derived from the Context
        val tickRate = 1000 / 120.0F
        handler.postDelayed({
            tick()
        }, tickRate.toLong())
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
    }

    fun tick() {
        val currentTimeMs = System.currentTimeMillis()
        val commitSwitch = { updatedState: State ->
            state = updatedState
            pendingSwitch = null
            timeOfStateSwitchMs = currentTimeMs
            delegate?.onStateChanged()
        }
        val newState = State.values().reversed().firstOrNull {
            delegate?.canSwitchToState(it) ?: false
        } ?: defaultState
        if (state == newState) {
            if (state != defaultState) {
                delegate?.onStillInState(currentTimeMs - timeOfStateSwitchMs)
            }
            return
        }
        if (!newState.hasDwellState) {
            commitSwitch(newState)
            return
        }

        // New state has a dwell state; wait for it to become valid
        val queuedSwitch = pendingSwitch
        // Nothing's queued yet or it's the wrong thing; queue it up
        if (queuedSwitch == null || queuedSwitch.second != newState) {
            pendingSwitch = Pair(currentTimeMs, newState)
            return
        }
        // Maybe it's popped by now?
        if (queuedSwitch.first + queuedSwitch.second.dwellTimeMs > currentTimeMs) {
            commitSwitch(newState)
        }
    }

    companion object {
        private val defaultState = State.Waiting
        private val handler = Handler(Looper.getMainLooper())
    }
}