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
import android.view.View.MeasureSpec

class PagerIndicatorView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val INACTIVE_ALPHA = 80
    private val ACTIVE_ALPHA = 220
    private val INDICATOR_WIDTH_FACTOR = 8
    private val OVERLAP_XFER_MODE = PorterDuffXfermode(PorterDuff.Mode.XOR)
    private val mPaint: Paint

    @SizeDimenAttribute(R.dimen.indicator_height)
    var DESIRED_HEIGHT = AttributeApplier.intValue()
    @SizeValAttribute(2F)
    var STROKE_WIDTH = AttributeApplier.intValue()
    @SizeValAttribute(6F)
    var INDICATOR_SIZE = AttributeApplier.intValue()

    private var mGridPageCount: Int = 0
    private var mActiveItem: Int = 0
    private var mIsSetup: Boolean = false

    fun setup(gridPageCount: Int) {
        mGridPageCount = gridPageCount
        mActiveItem = 1
        mIsSetup = true
        requestLayout()
        invalidate()
    }

    fun updateActiveItem(activeItem: Int) {
        mActiveItem = activeItem
        invalidate()
    }

    // Background is rendered as a View background;
    override fun onDraw(canvas: Canvas) {
        if (!mIsSetup) {
            return
        }

        // App drawer + other pages
        val itemCount = mGridPageCount + 1
        val itemWidth = width / itemCount
        val xOffsetInRange = itemWidth / 2 - INDICATOR_SIZE / 2
        val yOffset = height / 2 - INDICATOR_SIZE / 2
        mPaint.color = Color.WHITE
        for (i in 0 until itemCount) {
            val active = i == mActiveItem
            mPaint.alpha = if (active) ACTIVE_ALPHA else INACTIVE_ALPHA
            mPaint.style =
                if (active) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            val xOffset = i * itemWidth + xOffsetInRange
            when (i) {
                0 -> {
                    val widthOverFour = INDICATOR_SIZE / 4f
                    val xfermode = mPaint.xfermode
                    // TODO: This doesn't do what I expect it to
                    mPaint.xfermode = OVERLAP_XFER_MODE
                    // Top left
                    canvas.drawCircle(
                        xOffset + widthOverFour,
                        yOffset + widthOverFour,
                        widthOverFour,
                        mPaint
                    )
                    // Bottom right
                    canvas.drawCircle(
                        xOffset + widthOverFour * 3,
                        yOffset + widthOverFour * 3,
                        widthOverFour,
                        mPaint
                    )
                    mPaint.xfermode = xfermode
                }
                1 -> canvas.drawRect(
                    xOffset.toFloat(),
                    yOffset.toFloat(), (
                            xOffset + INDICATOR_SIZE).toFloat(), (
                            yOffset + INDICATOR_SIZE).toFloat(),
                    mPaint
                )
                else -> canvas.drawOval(
                    xOffset.toFloat(),
                    yOffset.toFloat(), (
                            xOffset + INDICATOR_SIZE).toFloat(), (
                            yOffset + INDICATOR_SIZE).toFloat(),
                    mPaint
                )
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val verticalSpace = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(
            mGridPageCount * INDICATOR_SIZE * INDICATOR_WIDTH_FACTOR,
            if (verticalSpace < DESIRED_HEIGHT) verticalSpace else DESIRED_HEIGHT
        )
    }

    init {
        AttributeApplier.applyDensity(this, getContext())
        mPaint = Paint()
        mPaint.strokeWidth = STROKE_WIDTH.toFloat()
    }
}