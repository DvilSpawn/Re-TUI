package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.max
import kotlin.math.min

class OutlineEditText : AppCompatEditText {
    private val idleCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var idleCursorVisible = false
    private var idleCursorColor = 0xffffffff.toInt()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun draw(canvas: Canvas) {
        val drawTimes = OutlineTextView.drawPasses(tag)

        for (c in 0 until drawTimes) {
            super.draw(canvas)
        }
        drawIdleCursor(canvas)
    }

    fun setIdleCursorVisible(visible: Boolean) {
        if (idleCursorVisible == visible) {
            return
        }
        idleCursorVisible = visible
        invalidate()
    }

    fun setIdleCursorColor(color: Int) {
        if (idleCursorColor == color) {
            return
        }
        idleCursorColor = color
        invalidate()
    }

    private fun drawIdleCursor(canvas: Canvas) {
        if (!idleCursorVisible || isCursorVisible) {
            return
        }

        val text = text
        val end = text?.length ?: 0
        var x = compoundPaddingLeft - scrollX.toFloat()
        if (end > 0) {
            x += paint.measureText(text, 0, end)
        }

        val density = resources.displayMetrics.density
        val lineHeight = max(lineHeight, (18f * density).toInt())
        val centerY = height / 2
        val top = max(paddingTop, centerY - lineHeight / 2)
        val bottom = min(height - paddingBottom, centerY + lineHeight / 2)
        val width = max(3f * density, 3f)

        idleCursorPaint.color = idleCursorColor
        idleCursorPaint.style = Paint.Style.FILL
        canvas.drawRect(x, top.toFloat(), x + width, bottom.toFloat(), idleCursorPaint)
    }
}
