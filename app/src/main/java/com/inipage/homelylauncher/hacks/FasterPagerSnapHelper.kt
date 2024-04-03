package com.inipage.homelylauncher.hacks

import android.R.attr
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.min


/**
 * Modified from https://stackoverflow.com/a/77606153.
 */
class FasterPagerSnapHelper(private val recyclerView: RecyclerView, private val speedUpPercent: Float) : PagerSnapHelper() {

    private val linearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {

        override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
            val layoutManager = recyclerView.layoutManager ?: return
            val snapDistances = calculateDistanceToFinalSnap(
                layoutManager,
                targetView
            ) ?: return
            val dx = snapDistances[0]
            val dy = snapDistances[1]
            val time = calculateTimeForDeceleration(abs(dx).coerceAtLeast(abs(dy)))
            if (time > 0) {
                action.update(dx, dy, (time / speedUpPercent).toInt(), mDecelerateInterpolator)
            }
        }

        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float =
            msPerInch / displayMetrics.densityDpi

        override fun calculateTimeForScrolling(dx: Int): Int =
            min(maxFlingScrollDurationMs, super.calculateTimeForScrolling(dx))
    }

    @Deprecated("")
    override fun createSnapScroller(layoutManager: RecyclerView.LayoutManager): LinearSmoothScroller? =
        if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) null else linearSmoothScroller

    companion object {
        private const val msPerInch = 100F
        private const val maxFlingScrollDurationMs = 200
        private const val tag = "FasterPagerSnapHelper"

        fun apply(viewPager: ViewPager2) {
            try {
                val scroller = ViewPager2::class.java.getDeclaredField("mRecyclerView")
                scroller.isAccessible = true

                val recyclerView = scroller.get(viewPager) as? RecyclerView ?: throw IllegalStateException("Wrong viewPager type")
                val pagerSnapHelper = ViewPager2::class.java.getDeclaredField("mPagerSnapHelper")
                pagerSnapHelper.isAccessible = true

                val customPagerSnapHelper = FasterPagerSnapHelper(recyclerView, 2.0F)
                pagerSnapHelper.set(viewPager, customPagerSnapHelper)
                recyclerView.onFlingListener = null
                customPagerSnapHelper.attachToRecyclerView(recyclerView)
            } catch (e: Exception) {
                Log.w(tag, "Failed to apply new snap helper", e)
            }
        }
    }
}