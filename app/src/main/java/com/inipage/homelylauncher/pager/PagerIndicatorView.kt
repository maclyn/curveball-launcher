package com.inipage.homelylauncher.pager

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.jvm.JvmOverloads
import com.inipage.homelylauncher.utils.SizeDimenAttribute
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.utils.AttributeApplier
import com.inipage.homelylauncher.utils.SizeValAttribute

class PagerIndicatorView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint: Paint

    @SizeValAttribute(2F)
    private var strokeWidth = AttributeApplier.floatValue()
    @SizeValAttribute(4F)
    private var rectRounding = AttributeApplier.floatValue()
    @SizeValAttribute(8F)
    private var indicatorHeight = AttributeApplier.floatValue()

    private var paddingBetweenIndicators = 0F
    private var gridPageIndicatorWidth = 0F
    private var appIndicatorRadius = 0F
    private var desiredHeight = 0F
    private var desiredWidth = 0F

    private var gridPageCount: Int = 0
    private var activePageIdx: Int = 0
    private var isSetup: Boolean = false

    fun setup(pageCount: Int) {
        gridPageCount = pageCount
        activePageIdx = 1
        desiredWidth =
            indicatorHeight +
                ((gridPageCount + 1) * paddingBetweenIndicators) +
                (gridPageCount * gridPageIndicatorWidth)
        isSetup = true
        requestLayout()
        invalidate()
    }

    fun updateActiveItem(activeItem: Int) {
        activePageIdx = activeItem
        invalidate()
    }

    // Renders as () (---) (   ) (   )
    override fun onDraw(canvas: Canvas) {
        if (!isSetup) {
            return
        }

        // We assume we have the height and width we need to layout, but we self-center inside
        // to avoid clipping the edge
        // Start at 0 and walk until the end of the view
        var xOffset = (width / 2.0F) - (desiredWidth / 2.0F)
        val yOffset = (height / 2.0F) - (desiredHeight / 2.0F)

        // App indicator
        val appIndicatorActive = 0 == activePageIdx
        paint.alpha = if (appIndicatorActive) activeAlpha else inactiveAlpha
        paint.style =
            if (appIndicatorActive) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        canvas.drawCircle(
            xOffset + appIndicatorRadius,
            yOffset + appIndicatorRadius,
            appIndicatorRadius,
            paint
        )
        xOffset += (appIndicatorRadius * 2.0F) + paddingBetweenIndicators

        // Page indicator(s)
        for (idx in 0 until gridPageCount) {
            val active = (idx + 1) == activePageIdx
            paint.alpha = if (active) activeAlpha else inactiveAlpha
            paint.style =
                if (active) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            canvas.drawRoundRect(
                xOffset,
                yOffset,
                xOffset + gridPageIndicatorWidth,
                yOffset + indicatorHeight,
                rectRounding,
                rectRounding,
                paint)
            xOffset += paddingBetweenIndicators + gridPageIndicatorWidth
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val horizontalSpace = MeasureSpec.getSize(widthMeasureSpec)
        val verticalSpace = MeasureSpec.getSize(heightMeasureSpec)
        // We self-center in the provided area
        setMeasuredDimension(horizontalSpace, verticalSpace)
    }

    init {
        AttributeApplier.applyDensity(this, getContext())
        paddingBetweenIndicators = indicatorHeight
        appIndicatorRadius = indicatorHeight / 2.0F
        gridPageIndicatorWidth = indicatorHeight * 1.5F
        desiredHeight = indicatorHeight
        paint = Paint().also {
            it.strokeWidth = strokeWidth
            it.color = Color.WHITE
        }
    }

    companion object {
        private const val inactiveAlpha = 80
        private const val activeAlpha = 255
    }
}