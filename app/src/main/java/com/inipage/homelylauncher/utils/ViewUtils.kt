package com.inipage.homelylauncher.utils

import android.animation.Animator
import kotlin.jvm.JvmOverloads
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Rect
import android.util.DisplayMetrics
import android.graphics.drawable.Drawable
import android.view.*
import androidx.core.content.ContextCompat
import com.inipage.homelylauncher.R
import java.io.IOException

object ViewUtils {
    private var IS_SQUARISH_DEVICE: Boolean? = null

    fun createFillerView(context: Context?, newHeight: Int): View {
        val view = View(context)
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, newHeight)
        setHeight(view, newHeight)
        return view
    }

    @JvmStatic
    fun setHeight(child: View, newHeight: Int) {
        val params = child.layoutParams
        params.height = newHeight
        child.layoutParams = params
    }

    fun createFillerWidthView(context: Context?, newWidth: Int): View {
        val view = View(context)
        view.layoutParams = ViewGroup.LayoutParams(newWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        setWidth(view, newWidth)
        return view
    }

    fun setWidth(child: View, newWidth: Int) {
        val params = child.layoutParams
        params.width = newWidth
        child.layoutParams = params
    }

    @JvmStatic
    @JvmOverloads
    fun exceedsSlop(
        event: MotionEvent, startX: Double, startY: Double, ctx: Context?, slopFactor: Double = 1.0
    ): Boolean {
        if (event.action == MotionEvent.ACTION_CANCEL) {
            return false
        }
        val dist = Math.hypot(event.rawX - startX, event.rawY - startY)
        return dist > ViewConfiguration.get(ctx).scaledTouchSlop * slopFactor
    }

    @JvmStatic
    fun performSyntheticMeasure(view: View) {
        val windowBounds = windowBounds(view.context)
        performSyntheticMeasure(view, windowBounds.height(), windowBounds.width())
    }

    @JvmStatic
    fun windowBounds(context: Context?): Rect {
        val activity = activityOf(context)
            ?: return Rect()
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
    fun activityOf(context: Context?): Activity? {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

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
    fun isTablet(context: Context): Boolean {
        return context.resources.configuration.screenLayout and
                Configuration.SCREENLAYOUT_SIZE_XLARGE > 0
    }

    fun getDrawableFromAssetPNG(context: Context, assetId: String): Drawable? {
        return try {
            Drawable.createFromStream(context.assets.open("$assetId.png"), null)
        } catch (e: IOException) {
            ContextCompat.getDrawable(context, R.drawable.ic_info_white_48dp)
        }
    }
}