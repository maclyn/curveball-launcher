package com.inipage.homelylauncher.state

import android.graphics.RectF

/**
 * Tracks some info about where we launched app from in a singleton so we can make a reasonable
 * guess about whether we'd like to handle the GestureNavContract intent.
 *
 * This class isn't timezone or time change safe, so we won't always deliver the right gesture
 * response.
 */
object GestureNavContractSingleton {

    class ComponentLaunch(
        val time: Long,
        val position: RectF,
        packageName: String,
        componentName: String?
    ) {
        val lastLaunchComponent: Pair<String, String?> = Pair(packageName, componentName)
    }

    private const val maxDeltaMs = 2000L

    private var lastComponentLaunch: ComponentLaunch? = null
    private var lastPauseTime: Long = 0L

    fun onAppLaunchRequest(
        packageName: String,
        componentName: String,
        position: RectF
    )
    {
        lastComponentLaunch = ComponentLaunch(now(), position, packageName, componentName)
    }

    fun onWidgetLaunchRequest(
        packageName: String,
        position: RectF
    ) {
        lastComponentLaunch = ComponentLaunch(now(), position, packageName, null)
    }

    fun onHomeActivityStopped() {
        lastPauseTime = now()
    }

    fun lastValidComponentLaunch(): ComponentLaunch? {
        val lastComponentLaunch = lastComponentLaunch ?: return null
        if (lastPauseTime - lastComponentLaunch.time > maxDeltaMs) {
            return null
        }
        return lastComponentLaunch
    }

    fun clearComponentLaunch() {
        lastComponentLaunch = null
    }

    private fun now(): Long {
        return System.currentTimeMillis()
    }
}