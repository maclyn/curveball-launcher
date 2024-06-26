package com.inipage.homelylauncher.utils

import android.animation.Animator
import kotlin.jvm.JvmOverloads
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.*
import com.inipage.homelylauncher.views.ProvidesOverallDimensions
import java.lang.IllegalArgumentException
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object ViewUtils {

    private var IS_SQUARISH_DEVICE: Boolean? = null

    @JvmStatic
    fun setWidth(child: View, newWidth: Int) {
        val params = child.layoutParams
        params.width = newWidth
        child.layoutParams = params
    }

    @JvmStatic
    fun setHeight(child: View, newHeight: Int) {
        val params = child.layoutParams
        params.height = newHeight
        child.layoutParams = params
    }

    @JvmStatic
    fun setDimensions(child: View, width: Int, height: Int) {
        val params = child.layoutParams
        params.width = width
        params.height = height
        child.layoutParams = params
        child.invalidate()
    }

    @JvmStatic
    fun waitForLayoutToTakeSpace(view: View, onLayout: (() -> Unit)) {
        var listener: View.OnLayoutChangeListener? = null
        listener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            if (right - left > 0 && bottom - top > 0) {
                onLayout()
                listener?.let { view.removeOnLayoutChangeListener(it) }
            }
        }
        view.addOnLayoutChangeListener(listener)
    }

    @JvmStatic
    @JvmOverloads
    fun exceedsSlopInActionMove(
        event: MotionEvent,
        pointerId: Int,
        startRawX: Double,
        startRawY: Double,
        view: View,
        slopFactor: Double = 1.0
    ): Boolean =
        exceedsSlop(
            getRawXWithPointerId(view, event, pointerId).toDouble(),
            getRawYWithPointerId(view, event, pointerId).toDouble(),
            startRawX,
            startRawY,
            view,
            slopFactor)

    @JvmStatic
    @JvmOverloads
    fun exceedsSlop(
        rawX: Double,
        rawY: Double,
        startRawX: Double,
        startRawY: Double,
        view: View,
        slopFactor: Double = 1.0
    ): Boolean {
        val dist = hypot(rawX - startRawX, rawY - startRawY)
        return dist > ViewConfiguration.get(view.context).scaledTouchSlop * slopFactor
    }

    @JvmStatic
    fun getXWithPointerId(view: View, event: MotionEvent, ptrId: Int): Float {
        val ptrIdx = event.findPointerIndex(ptrId)
        if (ptrIdx == -1) {
            DebugLogUtils.complain(
                view,
                "Tried to getRawXOffsetByView() for $ptrId, but it wasn't in this event")
            return 0F
        }
        return event.getX(ptrIdx)
    }

    @JvmStatic
    fun getYWithPointerId(view: View, event: MotionEvent, ptrId: Int): Float {
        val ptrIdx = event.findPointerIndex(ptrId)
        if (ptrIdx == -1) {
            DebugLogUtils.complain(
                view,
                "Tried to getRawYOffsetByView() for $ptrId, but it wasn't in this event")
            return 0F
        }
        return event.getY(ptrIdx)
    }

    @JvmStatic
    fun getRawXWithPointerId(view: View, event: MotionEvent, ptrId: Int): Float {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return getXWithPointerId(view, event, ptrId) + location[0]
    }

    @JvmStatic
    fun getRawYWithPointerId(view: View, event: MotionEvent, ptrId: Int): Float {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return getYWithPointerId(view, event, ptrId)+ location[1]
    }

    fun MotionEvent.getRawXForViewAndId(view: View, ptrId: Int): Float {
        return getRawXWithPointerId(view, this, ptrId)
    }

    fun MotionEvent.getRawYForViewAndId(view: View, ptrId: Int): Float {
        return getRawYWithPointerId(view, this, ptrId)
    }

    @JvmStatic
    fun performSyntheticMeasure(view: View) {
        val windowBounds = windowBounds(view.context)
        performSyntheticMeasure(view, windowBounds.height(), windowBounds.width())
    }

    @JvmStatic
    fun performSyntheticMeasure(view: View, context: Context) {
        val windowBounds = windowBounds(context)
        performSyntheticMeasure(view, windowBounds.height(), windowBounds.width())
    }

    @JvmStatic
    fun windowBounds(context: Context?): Rect {
        val activity = requireActivityOf(context)
        val decorView = activity.window.decorView
        val out = IntArray(2)
        decorView.getLocationOnScreen(out)
        return Rect(
            out[0], out[1], out[0] + decorView.width, out[1] + decorView.height
        )
    }

    fun performSyntheticMeasure(view: View, height: Int, width: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
        )
    }

    // From privately exported android.material.contextutils
    @JvmStatic
    fun requireActivityOf(context: Context?): Activity {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        throw IllegalArgumentException("Context passed that doesn't walk up to Activity!")
    }

    @JvmStatic
    fun activityOf(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    @JvmStatic
    fun guessUsefulWidthAndHeightOfActivityPx(context: Context): Pair<Int, Int> {
        val activity = activityOf(context)
        if (activity !is ProvidesOverallDimensions) {
            return Pair(
                (context.resources.displayMetrics.widthPixels * 1.0F).toInt(),
                (context.resources.displayMetrics.heightPixels * 0.9F).toInt())
        }
        val bounds = (activity as ProvidesOverallDimensions).provideOverallBounds()
        val scrims = (activity as ProvidesOverallDimensions).provideVerticalScrims()
        return Pair(bounds.width(), max(bounds.height() - scrims.first - scrims.second, 0))
    }

    @JvmStatic
    fun onEndListener(r: Runnable): Animator.AnimatorListener {
        return object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                r.run()
            }

            override fun onAnimationCancel(animation: Animator) {
                onAnimationEnd(animation)
            }

            override fun onAnimationRepeat(animation: Animator) {}
        }
    }

    fun isEventFromVirtualTrackball(event: InputEvent): Boolean {
        return event.device.name ==
                Constants.VIRTUAL_TITAN_POCKET_SCROLLPAD_INPUT_DEVICE_NAME
    }

    fun isEventFromPhysicalKeyboard(event: InputEvent): Boolean {
        return event.device.name ==
                Constants.PHYSICAL_TITAN_POCKET_KEYBOARD_INPUT_DEVICE_NAME
    }

    fun isSquarishDevice(context: Context): Boolean {
        val squarishRef = IS_SQUARISH_DEVICE
        if (squarishRef != null) {
            return squarishRef
        }
        // This might be called before anything is laid out
        // We *don't* want window values, since we want this to return the same
        // value every time it's called
        val metrics = context.resources.displayMetrics
        val screenHeight = metrics.heightPixels.toFloat()
        val screenWidth = metrics.widthPixels.toFloat()
        val isSquarish = screenHeight / screenWidth < 1.1
        IS_SQUARISH_DEVICE = isSquarish
        return isSquarish
    }

    @JvmStatic
    fun dpToPx(dp: Float, context: Context): Float {
        val densityScalar =
            context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
        return dp * densityScalar
    }

    @JvmStatic
    fun pxToDp(px: Float, context: Context): Float {
        val densityScalar =
            context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
        return px / densityScalar
    }

    @JvmStatic
    fun dpToPx(dp: Int, context: Context): Int {
        val densityScalar =
            context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
        return dp * densityScalar
    }

    @JvmStatic
    fun pxToDp(px: Int, context: Context): Int {
        val densityScalar =
            context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
        return px / densityScalar
    }

    @JvmStatic
    fun isTablet(context: Context): Boolean {
        return context.resources.configuration.screenLayout and
                Configuration.SCREENLAYOUT_SIZE_XLARGE > 0
    }

    @JvmStatic
    fun isPhablet(context: Context): Boolean {
        val screenLayout = context.resources.configuration.screenLayout
        val matchesAttrs =
            screenLayout and Configuration.SCREENLAYOUT_LONG_MASK == Configuration.SCREENLAYOUT_LONG_YES
        val diag = diagonalSize(context)
        return matchesAttrs && diag >= 5.0 && diag <= 6.95
    }

    @JvmStatic
    fun diagonalSize(context: Context): Double {
        val displayPixels = screenSize(context)
        val displayMetrics = context.resources.displayMetrics
        val widthInches = displayPixels.x / displayMetrics.xdpi
        val heightInches = displayPixels.y / displayMetrics.ydpi
        return sqrt(widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0))
    }

    @JvmStatic
    fun screenSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayPixels = Point()
        wm.defaultDisplay.getRealSize(displayPixels)
        return displayPixels
    }
}