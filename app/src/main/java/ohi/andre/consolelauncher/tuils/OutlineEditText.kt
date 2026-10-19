package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.max
import kotlin.math.min

class OutlineEditText : AppCompatEditText {
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var idleCursorVisible = false
    private var idleCursorDrawn = true
    private var idleCursorColor = 0xffffffff.toInt()
    private val idleCursorBlink = object : Runnable {
        override fun run() {
            if (!idleCursorVisible || !isAttachedToWindow) return
            idleCursorDrawn = !idleCursorDrawn
            invalidate()
            postDelayed(this, IDLE_CURSOR_BLINK_MS)
        }
    }

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
        idleCursorDrawn = true
        removeCallbacks(idleCursorBlink)
        if (visible && isAttachedToWindow) {
            postDelayed(idleCursorBlink, IDLE_CURSOR_BLINK_MS)
        }
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
        if (!idleCursorVisible || !idleCursorDrawn || isCursorVisible) {
            return
        }

        IdleCursorPresentation.draw(canvas,this,idleCursorColor,cursorPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (idleCursorVisible) {
            idleCursorDrawn = true
            removeCallbacks(idleCursorBlink)
            postDelayed(idleCursorBlink, IDLE_CURSOR_BLINK_MS)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(idleCursorBlink)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val IDLE_CURSOR_BLINK_MS = 500L
    }
}
