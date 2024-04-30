package com.inipage.homelylauncher.views

import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.ACTION_POINTER_UP
import android.view.MotionEvent.ACTION_UP
import android.view.View
import com.inipage.homelylauncher.utils.ViewUtils

/**
 * Wrapper for View.onTouchListener when a View only needs to care about the first pointer down.
 */
class SingleTouchEventHelper(
    private val view: View,
    private val listener: OnSingleTouchListener) : View.OnTouchListener {

    interface OnSingleTouchListener {

        fun onTouch(v: View, event: MotionEvent, action: Int): Boolean
    }

    private var startX: Float? = null
    private var startY: Float? = null
    private var startRawX: Float? = null
    private var startRawY: Float? = null

    private var firstPointerId: Int? = null
    private var hasDroppedEvent: Boolean = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            ACTION_DOWN -> {
                firstPointerId = event.getPointerId(event.actionIndex)
                startX = event.x
                startY = event.y
                startRawX = event.rawX
                startRawY = event.rawY
                hasDroppedEvent = false
                return listener.onTouch(v, event, ACTION_DOWN)
            }
            ACTION_POINTER_DOWN -> {
                // No-op, since these are always secondary pointers
                return false
            }
            ACTION_POINTER_UP -> {
                if (hasDroppedEvent) {
                    return false
                }
                val pointerUpId = event.getPointerId(event.actionIndex)
                if (pointerUpId != firstPointerId) {
                    return false
                }
                hasDroppedEvent = true
                return listener.onTouch(v, event, ACTION_UP)
            }
            ACTION_MOVE -> {
                if (hasDroppedEvent) {
                    return false
                }
                val pointerUpId = event.getPointerId(event.actionIndex)
                if (pointerUpId != firstPointerId) {
                    // Second pointer; we won't handle this
                    return false
                }
                return listener.onTouch(v, event, ACTION_MOVE)
            }
            ACTION_UP -> {
                var rVal = false
                if (!hasDroppedEvent) {
                    rVal = listener.onTouch(v, event, ACTION_UP)
                }
                resetState()
                hasDroppedEvent = false
                return rVal
            }
            ACTION_CANCEL -> {
                val rVal = listener.onTouch(v, event, ACTION_CANCEL)
                resetState()
                hasDroppedEvent = false
                return rVal
            }
        }
        return false
    }

    fun getX(event: MotionEvent): Float {
        val ptrId = firstPointerId ?: return 0F
        return ViewUtils.getXWithPointerId(view, event, ptrId)
    }

    fun getY(event: MotionEvent): Float {
        val ptrId = firstPointerId ?: return 0F
        return ViewUtils.getYWithPointerId(view, event, ptrId)
    }

    fun getRawX(event: MotionEvent): Float {
        val ptrId = firstPointerId ?: return 0F
        return ViewUtils.getRawXWithPointerId(view, event, ptrId)
    }

    fun getRawY(event: MotionEvent): Float {
        val ptrId = firstPointerId ?: return 0F
        return ViewUtils.getRawYWithPointerId(view, event, ptrId)
    }

    fun getStartRawX(): Float {
        return startRawX ?: 0F
    }

    fun getStartRawY(): Float {
        return startRawY ?: 0F
    }

    fun getStartX(): Float {
        return startX ?: 0F
    }

    fun getStartY(): Float {
        return startY ?: 0F
    }

    private fun resetState() {
        firstPointerId = null
        startX = null
        startY = null
        startRawX = null
        startRawY = null
    }

    init {
        view.setOnTouchListener(this)
    }
}