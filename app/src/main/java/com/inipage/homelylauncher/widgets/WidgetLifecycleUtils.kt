package com.inipage.homelylauncher.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import com.inipage.homelylauncher.caches.AppInfoCache
import com.inipage.homelylauncher.utils.ViewUtils

/**
 * Helper functions for handling hosting different widgets.
 */
object WidgetLifecycleUtils {

    @JvmStatic
    fun getAppWidgetManager(context: Context): AppWidgetManager =
        context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager

    @JvmStatic
    fun buildAppWidgetHostView(
        context: Context, appWidgetId: Int, widthPx: Int, heightPx: Int
    ): AppWidgetHostView? {
        val awpi = getAppWidgetManager(context).getAppWidgetInfo(appWidgetId) ?: return null
        // getApplicationContext is important -- we want a Context that is not themed
        // otherwise the hosted widgets will look really bad...
        val hostView =
            AppInfoCache.get().appWidgetHost.createView(context.applicationContext, appWidgetId, awpi)
        updateAppWidgetSize(hostView, widthPx, heightPx)
        return hostView
    }

    @JvmStatic
    fun updateAppWidgetSize(hostView: AppWidgetHostView, widthPx: Int, heightPx: Int) {
        // AWHVs applies its own padding, but updateAppWidgetSize() doesn't account for it,
        // so we have to handle it here
        val effectiveWidthPx =
            widthPx - (hostView.paddingStart + hostView.paddingEnd + hostView.marginStart + hostView.marginEnd)
        val effectiveHeightPx =
            heightPx - (hostView.paddingTop + hostView.paddingBottom + hostView.marginTop + hostView.marginBottom)
        val widthDp = ViewUtils.pxToDp(effectiveWidthPx, hostView.context)
        val heightDp = ViewUtils.pxToDp(effectiveHeightPx, hostView.context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // It *seems* like we have to bake in our own padding to avoid clipping the edges of
            // Material You widgets. Not sure why, but tested with all the ones I could find, and 90%
            // seems to work fine?
            hostView.updateAppWidgetSize(
                Bundle(), listOf(SizeF(widthDp.toFloat() * 0.9F, heightDp.toFloat() * 0.9F)))
        } else {
            hostView.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        }
    }
}