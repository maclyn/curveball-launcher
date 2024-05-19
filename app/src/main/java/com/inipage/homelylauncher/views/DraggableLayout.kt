package com.inipage.homelylauncher.views

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.utils.ViewUtils
import com.inipage.homelylauncher.utils.ViewUtils.exceedsSlopInActionMove
import com.inipage.homelylauncher.utils.ViewUtils.getRawXWithPointerId
import com.inipage.homelylauncher.utils.ViewUtils.getRawYWithPointerId
import kotlin.math.abs
import kotlin.math.sign

/**
 * Layout that looks for large downwards vertical swipes insides, and intercepts touch events when
 * they happen.
 */
class DraggableLayout : LinearLayout {

    interface Host {
        fun onAnimationPartial(percentComplete: Float, translationY: Float)

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

    fun markViewBeingAnimated(isViewBeingAnimated: Boolean) {
        isExecutingAnimation = isViewBeingAnimated
    }

    /**
     * Run an animation from an existing VelocityTracker. Useful to animate this view without
     * needing to duplicate the animation logic if the touch events that are the source of the
     * motion are not originating from this veiw.
     */
    fun runAnimationFromBrainSlug(newVelocityTracker: VelocityTracker, newFirstPointerId: Int) {
        velocityTracker?.recycle()
        velocityTracker = newVelocityTracker
        firstPointerId = newFirstPointerId
        commitAnimation()
    }

    // Entrance animations are either triggered by a touch event or, in the bottom sheet case,
    // provided by DecorViewManager

    /*
     * Exit animations are either trigger by a touch event, DecorViewManager, or this function
     */
    fun triggerExitAnimation() {
        runAnimationImpl(context.resources.getDimension(R.dimen.speedy_exit_velocity_dp_s))
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

    @SuppressLint("ClickableViewAccessibility")
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
        var fingerVelocity = velocityTracker?.getYVelocity(firstPointerId) ?: 0.0F
        val minVelocityMagnitude = resources.getDimension(R.dimen.min_scroll_velocity_dp_s)
        if (
            ViewUtils.pxToDp(abs(fingerVelocity), context) <
            minVelocityMagnitude
        ) {
            fingerVelocity = sign(fingerVelocity) * minVelocityMagnitude
        }
        runAnimationImpl(fingerVelocity)
    }

    private fun runAnimationImpl(startVelocityPixelPerSec: Float) {
        val isExitAnimation = startVelocityPixelPerSec > 0
        ViewUtils.performSyntheticMeasure(this)
        val viewHeight = measuredHeight

        val velocityPixelsPerMs = startVelocityPixelPerSec / 1000
        val startTime = SystemClock.elapsedRealtime()
        val startY = translationY.toInt()
        val animationFrameRunnable = object : Runnable {
            override fun run() {
                val currTime = SystemClock.elapsedRealtime()
                val elapsedTimeMs = currTime - startTime
                if (elapsedTimeMs < 0) {
                    // Should be impossible
                    onAnimationComplete(isExitAnimation)
                    return
                }

                val newTranslationY = startY + (elapsedTimeMs * velocityPixelsPerMs)
                if (isExitAnimation && newTranslationY >= viewHeight) {
                    onAnimationComplete(true)
                } else if (!isExitAnimation && newTranslationY <= 0) {
                    onAnimationComplete(false)
                } else {
                    this@DraggableLayout.translationY = newTranslationY
                    host?.onAnimationPartial(
                        1.0F - (newTranslationY / viewHeight),
                        newTranslationY
                    )
                    ViewCompat.postOnAnimation(this@DraggableLayout, this)
                }
            }
        }
        animationFrameRunnable.run()
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
        for (childIdx in 0 until viewGroup.childCount) {
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
        if (underPointView is RecyclerView || underPointView is ScrollView || underPointView is ListView) {
            return !underPointView.canScrollVertically(-1)
        }
        return if (underPointView is ViewGroup) {
            canInterceptEventAtPosition(underPointView, ev)
        } else true
    }
}