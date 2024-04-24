package com.inipage.homelylauncher.views

import android.app.Activity
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout

/**
 * Simple wrapper class for a surface view used for providing GestureNavContract
 * a handle to a surface in our app.
 */
class SurfaceViewWrapper(activity: Activity) {

    private val surfaceView = SurfaceView(activity)

    fun show(): SurfaceView {
        surfaceView.visibility = View.VISIBLE
        return surfaceView
    }

    fun hide() {
        surfaceView.visibility = View.GONE
    }

    init {
        DecorViewManager.getDecorView(activity)?.let { decorView ->
            val layoutParams = FrameLayout.LayoutParams(0, 0)
            decorView.addView(surfaceView, layoutParams)
        }
    }
}