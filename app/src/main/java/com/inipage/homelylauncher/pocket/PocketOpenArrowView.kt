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

    private val arrowDrawable: RotateDrawable = RotateDrawable().apply {
        drawable =
            ContextCompat.getDrawable(getContext(), R.drawable.arrow_up)
                ?.constantState?.newDrawable()?.mutate()?.apply {
            alpha = 180
        }
        fromDegrees = 0F
        toDegrees = 180F
        isPivotXRelative = true
        isPivotYRelative = true
        pivotX = 0.5F
        pivotY = 0.5F
    }

    private var expandedPercent: Float = 0F

    override fun setRotation(rotation: Float) {
        expandedPercent = rotation
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val center = width / 2
        val viewHeight = height
        arrowDrawable.setBounds(center - viewHeight / 2, 0, center + viewHeight / 2, viewHeight)
        arrowDrawable.level = (expandedPercent * 10000).toInt()
        arrowDrawable.draw(canvas)
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
    }
}