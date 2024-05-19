package com.inipage.homelylauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.RESIZE_BOTH
import android.appwidget.AppWidgetProviderInfo.RESIZE_HORIZONTAL
import android.appwidget.AppWidgetProviderInfo.RESIZE_NONE
import android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
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

    private val materialYouPaddingFactor = 0.9F

    class WidgetAddTransaction(
        val appWidgetId: Int,
        val apwi: AppWidgetProviderInfo
    )

    var activeTransaction: WidgetAddTransaction? = null
        private set

    @JvmStatic
    fun getAppWidgetManager(context: Context): AppWidgetManager =
        context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager

    @JvmStatic
    fun getAppWidgetHost(): AppWidgetHost = AppInfoCache.get().appWidgetHost

    @JvmStatic
    fun getAppWidgetProviderInfo(context: Context, appWidgetId: Int): AppWidgetProviderInfo? =
        getAppWidgetManager(context).getAppWidgetInfo(appWidgetId)

    fun AppWidgetProviderInfo.supportsResizing(): Boolean = resizeMode != RESIZE_NONE

    fun AppWidgetProviderInfo.supportsHorizontalResize(): Boolean =
        resizeMode == RESIZE_HORIZONTAL || resizeMode == RESIZE_BOTH

    fun AppWidgetProviderInfo.supportsVerticalResize(): Boolean =
        resizeMode == RESIZE_VERTICAL || resizeMode == RESIZE_BOTH

    fun AppWidgetProviderInfo.minLayoutWidth(): Int {
        if (this.supportsHorizontalResize()) {
            return maybeAddPaddingToContainerDimension(minResizeWidth)
        }
        return maybeAddPaddingToContainerDimension(minWidth)
    }

    fun AppWidgetProviderInfo.minLayoutHeight(): Int {
        if (this.supportsVerticalResize()) {
            return maybeAddPaddingToContainerDimension(minResizeHeight)
        }
        return maybeAddPaddingToContainerDimension(minHeight)
    }

    fun AppWidgetProviderInfo.defaultLayoutWidth(): Int {
        if (this.supportsHorizontalResize() && minResizeWidth > minWidth) {
            return maybeAddPaddingToContainerDimension(minResizeWidth)
        }
        return maybeAddPaddingToContainerDimension(minWidth)
    }

    fun AppWidgetProviderInfo.defaultLayoutHeight(): Int {
        if (this.supportsVerticalResize() && minResizeHeight > minResizeWidth) {
            return maybeAddPaddingToContainerDimension(minResizeHeight)
        }
        return maybeAddPaddingToContainerDimension(minHeight)
    }


    fun AppWidgetProviderInfo.maxLayoutWidth(containerWidth: Int): Int? {
        if (this.supportsHorizontalResize() && isMaterialYouCompatible()) {
            val size = if (maxResizeWidth <= 0) containerWidth else maxResizeWidth
            val proposedWidth = maybeRemovePaddingFromContainerDimension(size)
            if (proposedWidth < minLayoutWidth()) {
                return minLayoutWidth()
            }
            if (maxResizeWidth > containerWidth) {
                return containerWidth
            }
            return proposedWidth
        }
        return null
    }

    fun AppWidgetProviderInfo.maxLayoutHeight(containerHeight: Int): Int? {
        if (this.supportsVerticalResize() && isMaterialYouCompatible()) {
            val size = if (maxResizeHeight <= 0) containerHeight else maxResizeHeight
            val proposedHeight = maybeRemovePaddingFromContainerDimension(size)
            if (proposedHeight < minLayoutHeight()) {
                return minLayoutHeight()
            }
            if (maxResizeHeight > containerHeight) {
                return containerHeight
            }
            return proposedHeight
        }
        return null
    }

    fun AppWidgetProviderInfo.findIdealSize(containerWidth: Int, containerHeight: Int): Pair<Int, Int> {
        // Grab this so we can scale proportionally
        val aspectRatio =
            (minWidth.toFloat() / minHeight) // For something like a search bar, 4.0-5.0ish

        // Horizontal fit
        var hasHorizontalFit = false
        var hFitWidth = maxLayoutWidth(containerWidth) ?: defaultLayoutWidth()
        if (hFitWidth > containerWidth) {
            hFitWidth = containerWidth
        }
        val hFitHeight = (hFitWidth * aspectRatio).toInt()
        val hFitPixelCount = hFitWidth * hFitHeight
        if (hFitHeight <= containerHeight) {
            hasHorizontalFit = true
        }

        // Vertical fit
        var hasVerticalFit = false
        var vFitHeight = maxLayoutHeight(containerHeight) ?: defaultLayoutHeight()
        if (vFitHeight > containerHeight) {
            vFitHeight = containerHeight
        }
        val vFitWidth = (vFitHeight * aspectRatio).toInt()
        val vFitPixelCount = hFitWidth * hFitHeight
        if (vFitWidth <= containerWidth) {
            hasVerticalFit = true
        }

        val vFitBounds = Pair(vFitWidth, vFitHeight)
        val hFitBounds = Pair(hFitWidth, hFitHeight)
        val fallbackBounds = Pair(
            maxLayoutWidth(containerWidth) ?: minLayoutWidth(),
            maxLayoutHeight(containerHeight) ?: minLayoutHeight())
        return when {
            hasVerticalFit && hasHorizontalFit ->
                return if (vFitPixelCount > hFitPixelCount) vFitBounds else hFitBounds
            hasVerticalFit && !hasHorizontalFit -> vFitBounds
            !hasVerticalFit && hasHorizontalFit -> hFitBounds
            else -> fallbackBounds
        }
    }

    @JvmStatic
    fun buildAppWidgetHostView(
        context: Context, appWidgetId: Int, widthPx: Int, heightPx: Int
    ): AppWidgetHostView? {
        val awpi = getAppWidgetProviderInfo(context, appWidgetId) ?: return null
        val width = if (widthPx < 0) awpi.minWidth else widthPx
        val height = if (heightPx < 0) awpi.minHeight else heightPx
        // getApplicationContext is important -- we want a Context that is not themed
        // otherwise the hosted widgets will look really bad...
        val hostView =
            AppInfoCache.get().appWidgetHost.createView(
                context.applicationContext, appWidgetId, awpi)
        updateAppWidgetSize(hostView, width, height)
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
        if (isMaterialYouCompatible()) {
            // It *seems* like we have to bake in our own padding to avoid clipping the edges of
            // Material You widgets. Not sure why, but tested with all the ones I could find, and 90%
            // seems to work fine?
            hostView.updateAppWidgetSize(
                Bundle(), listOf(
                    SizeF(
                        widthDp.toFloat() * materialYouPaddingFactor,
                        heightDp.toFloat() * materialYouPaddingFactor)))
        } else {
            hostView.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        }
    }

    @JvmStatic
    fun guessDesiredPreviewBounds(context: Context, awpi: AppWidgetProviderInfo): Pair<Int, Int> {
        // Figure out a rough guess for what each grid cell's height and width will be
        // The actual values are decided by GridMetrics usually
        val isPortrait =
            context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val widthHeightPairPx = ViewUtils.guessUsefulWidthAndHeightOfActivityPx(context)
        val portraitActivityWidthPx =
            if (isPortrait) widthHeightPairPx.first else widthHeightPairPx.second
        val portraitActivityHeightPx =
            if (isPortrait) widthHeightPairPx.second else widthHeightPairPx.first

        // Let's guess that we have ~95% of the screen width, and ~80% of the screen height
        // for grid layout, and a default 5x6 (width x height) grid
        val gridCellWidth = portraitActivityWidthPx * 0.95F / 5
        val gridCellHeight = portraitActivityHeightPx * 0.80F / 6

        // Guess from targetCells and screen dimensions
        if (isMaterialYouCompatible()) {
            val widthCells = awpi.targetCellWidth
            val heightCells = awpi.targetCellHeight

            // Some apps transpose target cell width and target cell height
            // Observed with Google's At a Glance
            // If we see very different target cell and min values, just ignore target cell values
            val areTargetCellsLikelyWrong =
                widthCells < heightCells && awpi.minWidth > awpi.minHeight
            if (widthCells > 0 && heightCells > 0 && !areTargetCellsLikelyWrong) {
                val result = Pair(
                    (gridCellWidth * widthCells).toInt(),
                    (gridCellHeight * heightCells).toInt())
                return result
            }
        }

        // Guess from minWidth and minHeight
        val clampToNearest: (value: Int, unit: Float) -> Int = { value, unit ->
            // Division made easy
            var count = 1
            while (unit * count < value) {
                count++
            }
            (unit * count).toInt()
        }
        val minWidth = awpi.minWidth
        val minHeight = awpi.minHeight
        val result = Pair(
            clampToNearest(minWidth, gridCellWidth),
            clampToNearest(minHeight, gridCellHeight))
        return result
    }

    @JvmStatic
    fun isMaterialYouCompatible(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @JvmStatic
    fun startTransaction(widgetId: Int, awpi: AppWidgetProviderInfo) {
        activeTransaction = WidgetAddTransaction(widgetId, awpi)
    }

    @JvmStatic
    fun endTransaction() {
        activeTransaction = null
    }

    private fun maybeAddPaddingToContainerDimension(dimensionPx: Int): Int =
        (dimensionPx / (if (isMaterialYouCompatible()) materialYouPaddingFactor else 1.0F)).toInt()

    private fun maybeRemovePaddingFromContainerDimension(dimensionPx: Int) =
        (dimensionPx * (if (isMaterialYouCompatible()) materialYouPaddingFactor else 1.0F)).toInt()
}