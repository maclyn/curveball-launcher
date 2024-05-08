package com.inipage.homelylauncher.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.utils.DebugLogUtils
import com.inipage.homelylauncher.utils.ViewUtils.exceedsSlopInActionMove
import com.inipage.homelylauncher.utils.ViewUtils.getRawXWithPointerId
import com.inipage.homelylauncher.utils.ViewUtils.getRawYWithPointerId
import java.lang.IllegalArgumentException
import kotlin.math.abs
import kotlin.math.max

/**
 * Layout that looks for large downwards vertical swipes insides, and intercepts touch events when
 * they happen.
 */
class DraggableLayout : LinearLayout {

    interface Host {
        fun onAnimationPartial(percentComplete: Float, translationMagnitude: Float)

        fun onAnimationInComplete()

        fun onAnimationOutComplete()
    }

    private val locationBuffer = IntArray(2)

    private var host: Host? = null
    private var velocityTracker: VelocityTracker? = null
    private var firstPointerId = 0
    private var startRawX = 0f
    private var startRawY = 0f
    private var maxDraggableDistance = 0f
    private var hasDroppedEvent = false
    private var isHandlingEvent = true
    private var isExecutingAnimation = false

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    fun attachHost(host: Host?) {
        this.host = host
    }

    fun runAnimationFromBrainSlug(newVelocityTracker: VelocityTracker, newFirstPointerId: Int) {
        velocityTracker?.recycle()
        velocityTracker = newVelocityTracker
        firstPointerId = newFirstPointerId
        maxDraggableDistance = measuredHeight.toFloat()
        commitAnimation()
    }

    fun triggerExitAnimation() {
        runAnimationImpl(
            0.0F,
            context.resources.getDimension(R.dimen.speedy_exit_velocity_dp_s),
            true)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isExecutingAnimation || !canInterceptEventAtPosition(this, ev)) {
                    hasDroppedEvent = true
                    return false
                }
                firstPointerId = ev.getPointerId(ev.actionIndex)
                startRawX = ev.rawX
                startRawY = ev.rawY
                maxDraggableDistance = measuredHeight.toFloat()
                velocityTracker = VelocityTracker.obtain()
                addMovementToVelocityTracker(ev)
                hasDroppedEvent = false
                isHandlingEvent = false
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (hasDroppedEvent ||
                    !exceedsSlopInActionMove(
                        ev, firstPointerId, startRawX.toDouble(), startRawY.toDouble(), this
                    )
                ) {
                    addMovementToVelocityTracker(ev)
                    return false
                }
                val firstPointerIdx = ev.findPointerIndex(firstPointerId)
                if (firstPointerIdx == -1) {
                    return false
                }
                addMovementToVelocityTracker(ev)
                val xDelta = getRawXWithPointerId(this, ev, firstPointerId) - startRawX
                val yDelta: Float = getRawYWithPointerId(this, ev, firstPointerId) - startRawY
                if (yDelta < 0 || abs(xDelta) > abs(yDelta)) {
                    hasDroppedEvent = true
                    return false
                }

                // We have a mostly vertical scroll down that exceeds slop -- steal it
                isHandlingEvent = true
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (ev.getPointerId(ev.actionIndex) == firstPointerId) {
                    // Initial pointer is up; we should stop tracking this event
                    hasDroppedEvent = true
                }
                return false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isHandlingEvent) {
            // We delegate *back* to onInterceptTouchEvent if we prematurely receive the event --
            // this happens when users (for example) touch on the background
            onInterceptTouchEvent(ev)
            return true
        }
        when (ev.action) {
            MotionEvent.ACTION_MOVE -> {
                val firstPointerIdx = ev.findPointerIndex(firstPointerId)
                if (firstPointerIdx == -1) {
                    return false
                }
                var yDelta = getRawYWithPointerId(this, ev, firstPointerIdx) - startRawY
                if (yDelta > maxDraggableDistance) {
                    yDelta = maxDraggableDistance
                } else if (yDelta < 0) {
                    yDelta = 0.0F
                }
                host?.onAnimationPartial(1.0F - (yDelta / maxDraggableDistance), yDelta)
                addMovementToVelocityTracker(ev)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val actionPointerId = ev.getPointerId(ev.actionIndex)
                return if (actionPointerId == firstPointerId) {
                    if (exceedsSlopInActionMove(
                            ev, firstPointerId, startRawX.toDouble(), startRawY.toDouble(), this
                        )
                    ) {
                        addMovementToVelocityTracker(ev)
                        commitAnimation()
                    } else {
                        host?.onAnimationPartial(0.0F, 0.0F)
                    }
                    true
                } else {
                    false
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!isExecutingAnimation) {
                    host?.onAnimationPartial(0.0F, 0.0F)
                    velocityTracker?.recycle()
                }
                return true
            }
        }
        return true
    }

    /**
     * Trigger an animation to the end or top based on the current velocity + acceleration of the
     * bottom sheet
     */
    private fun commitAnimation() {
        isExecutingAnimation = true
        velocityTracker?.computeCurrentVelocity(
            1000,  // px/s
            ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
        )
        val speed = velocityTracker?.getYVelocity(firstPointerId) ?: 0.0F
        val startPoint = translationY

        // Seems like the FlingAnimation doesn't work with negative values? Not entirely sure,
        // but making the <some + translation> -> 0 animation into a 0 -> <some + translation>
        // works regardless, though it does add a little logic
        val isAnimatingOut = speed > 0
        val startVelocityMagnitude = max(
            abs(speed),
            // The ViewConfiguration default here (50dp/s) is WAY too slow
            context.resources.getDimension(R.dimen.min_start_velocity_dp_s))
        val startVelocity = startVelocityMagnitude * (if (isAnimatingOut) 1.0F else -1.0F)
        log(
            "speed", speed.toString(),
            "startPoint", startPoint.toString(),
            "startVelocity", startVelocity.toString(),
            "isAnimatingOut", isAnimatingOut.toString()
        )

        runAnimationImpl(startPoint, startVelocity, isAnimatingOut)
    }

    private fun runAnimationImpl(startPoint: Float, startVelocity: Float, isAnimatingOut: Boolean) {
        val flingAnimation = FlingAnimation(
            this, object : FloatPropertyCompat<DraggableLayout>("translationY") {
                override fun getValue(`object`: DraggableLayout): Float {
                    return translationY
                }

                override fun setValue(`object`: DraggableLayout, value: Float) {
                    host?.onAnimationPartial(1.0F - (value / maxDraggableDistance), value)
                }
            })
        flingAnimation.setStartValue(startPoint)
        flingAnimation.setMinValue(0F)
        flingAnimation.setMaxValue(maxDraggableDistance)
        flingAnimation.setStartVelocity(startVelocity)
        flingAnimation.friction = 0.1F;
        flingAnimation.addEndListener { _: DynamicAnimation<*>?, _: Boolean, _: Float, _: Float ->
            onAnimationComplete(isAnimatingOut)
        }
        try {
            flingAnimation.start()
        } catch (_: IllegalArgumentException) {
            DebugLogUtils.complain(
                context,
                "startPoint=$startPoint maxDraggableDistance=$maxDraggableDistance")
            onAnimationComplete(isAnimatingOut)
        }
    }

    private fun onAnimationComplete(isAnimatingOut: Boolean) {
        if (isAnimatingOut) {
            host?.onAnimationOutComplete()
        } else {
            host?.onAnimationInComplete()
        }
        isExecutingAnimation = false
    }

    private fun addMovementToVelocityTracker(ev: MotionEvent) {
        val vt = velocityTracker ?: return
        ev.offsetLocation(0f, translationY)
        vt.addMovement(ev)
    }

    private fun canInterceptEventAtPosition(viewGroup: ViewGroup, ev: MotionEvent): Boolean {
        val rawX = ev.rawX
        val rawY = ev.rawY
        var underPointView: View? = null
        for (childIdx in 0 until childCount) {
            val child = viewGroup.getChildAt(childIdx) ?: continue
            child.getLocationOnScreen(locationBuffer)
            if (locationBuffer[0] <= rawX && rawX <= locationBuffer[0] + child.width && locationBuffer[0] <= rawY && rawY <= locationBuffer[1] + child.height) {
                underPointView = child
                break
            }
        }
        if (underPointView == null) {
            return true
        }
        if (underPointView is RecyclerView || underPointView is ScrollView) {
            return !underPointView.canScrollVertically(-1)
        }
        return if (underPointView is ViewGroup) {
            canInterceptEventAtPosition(underPointView, ev)
        } else true
    }

    private fun log(vararg vals: String) {
        DebugLogUtils.needle(DebugLogUtils.TAG_BOTTOM_SHEET, *vals)
    }
}