package com.inipage.homelylauncher.pocket

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.jvm.JvmOverloads
import android.graphics.drawable.RotateDrawable
import com.inipage.homelylauncher.utils.AttributeApplier
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.inipage.homelylauncher.R

/**
 * Render a square arrow that rotates.
 */
class PocketOpenArrowView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val mPaint: Paint
    private val mArrowDrawable: RotateDrawable
    private var mExpandedPercent: Float

    override fun setRotation(rotation: Float) {
        mExpandedPercent = rotation
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val center = width / 2
        val viewHeight = height
        mArrowDrawable.setBounds(center - viewHeight / 2, 0, center + viewHeight / 2, viewHeight)
        mArrowDrawable.level = (mExpandedPercent * 10000).toInt()
        mArrowDrawable.draw(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            measuredHeight
        )
    }

    init {
        AttributeApplier.applyDensity(this, context)
        mPaint = Paint()
        mPaint.color = Color.WHITE
        mPaint.style = Paint.Style.FILL
        mPaint.alpha = 180
        val arrowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.arrow_up)
        arrowDrawable!!.alpha = 180
        mArrowDrawable = RotateDrawable()
        mArrowDrawable.drawable = arrowDrawable
        mArrowDrawable.fromDegrees = 0f
        mArrowDrawable.toDegrees = 180f
        mArrowDrawable.isPivotXRelative = true
        mArrowDrawable.isPivotYRelative = true
        mArrowDrawable.pivotY = 0.5f
        mArrowDrawable.pivotX = 0.5f
        mExpandedPercent = 0f
    }
}