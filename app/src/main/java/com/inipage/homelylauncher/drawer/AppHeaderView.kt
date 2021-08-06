package com.inipage.homelylauncher.drawer

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.setBlendMode
import com.inipage.homelylauncher.R
import java.util.*
import kotlin.properties.Delegates

/**
 * Renders an app letter header (a circle with a letter inside, and some dots representing the
 * count of apps below).
 */
class AppHeaderView : View {

    private val TEXT_BOUNDS_TMP = Rect()

    private var _circleColor: Int = Color.WHITE
    private var _letterCircleRaidus: Int = 16
    private var _appIconRadius: Int = 24
    private var _dotCircleRadius: Int = 8
    private var _headerChar: String = FastScrollable.NUMERIC.toString()
    private var _headerCount: Int = 1

    var circleColor: Int
        get() = _circleColor
        set(value) {
            _circleColor = value
            invalidate()
        }

    var letterCircleRadius: Int
        get() = _letterCircleRaidus
        set(value) {
            _letterCircleRaidus = value
            invalidate()
        }

    var appIconRadius: Int
        get() = _appIconRadius
        set(value) {
            _appIconRadius = value
            invalidate()
        }

    var dotCircleRadius: Int
        get() = _dotCircleRadius
        set(value) {
            _dotCircleRadius = value
            invalidate()
        }

    var headerChar: String
        get() = _headerChar
        set(value) {
            _headerChar = value.uppercase(Locale.getDefault())
            invalidate()
        }

    var headerCount: Int
        get() = _headerCount
        set(value) {
            _headerCount = value
            invalidate()
        }

    var horizontalPadding by Delegates.notNull<Int>()
    lateinit var paint: Paint

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.AppHeaderView, defStyle, 0
        )

        _circleColor = a.getColor(
            R.styleable.AppHeaderView_circleColor,
            circleColor
        )
        // Use getDimensionPixelSize or getDimensionPixelOffset when dealing with
        // values that should fall on pixel boundaries.
        _dotCircleRadius = a.getDimensionPixelOffset(
            R.styleable.AppHeaderView_dotCircleRadius,
            dotCircleRadius
        )

        _letterCircleRaidus = a.getDimensionPixelOffset(
            R.styleable.AppHeaderView_letterCircleRadius,
            letterCircleRadius
        )

        _appIconRadius = a.getDimensionPixelOffset(
            R.styleable.AppHeaderView_appIconRadius,
            appIconRadius
        )

        a.recycle()

        horizontalPadding = context.resources.getDimensionPixelOffset(R.dimen.app_row_horizontal_padding)

        paint = Paint().apply {
            isAntiAlias = true
            color = circleColor
            textAlign = Paint.Align.CENTER
            textSize = (letterCircleRadius * 1.25).toFloat()
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), letterCircleRadius * 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Should be roughly -- [8dp][letter][8dp][dots...]
        val bigCirclePadding = appIconRadius - letterCircleRadius
        var horizontalOffset = horizontalPadding + bigCirclePadding

        // Draw the big circle
        canvas.drawCircle(
            (horizontalOffset + letterCircleRadius).toFloat(),
            letterCircleRadius.toFloat(),
            letterCircleRadius.toFloat(),
            paint)

        // Draw the text
        paint.getTextBounds(headerChar, 0, headerChar.length, TEXT_BOUNDS_TMP)
        paint.color = Color.WHITE
        canvas.drawText(
            headerChar,
            (horizontalOffset + letterCircleRadius).toFloat(),
            height / 2F + TEXT_BOUNDS_TMP.height() / 2F - TEXT_BOUNDS_TMP.bottom,
            paint)
        paint.color = circleColor
        horizontalOffset += bigCirclePadding

        // Draw each dot centered
        horizontalOffset += (letterCircleRadius * 2) + this.horizontalPadding
        for (i in 0 until headerCount) {
            canvas.drawCircle(
                (horizontalOffset + dotCircleRadius).toFloat(),
                (height / 2).toFloat(),
                dotCircleRadius.toFloat(),
                paint)
            horizontalOffset += (dotCircleRadius * 2) + horizontalPadding
        }
    }
}