package ohi.andre.consolelauncher.tuils.frame

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class CyberpunkIconFrameDrawable(
    frameColor: Int,
    private val strokePx: Float,
    private val cornerLengthPx: Float,
    private val insetPx: Float
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = frameColor
        strokeWidth = strokePx
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) {
            return
        }

        val left = b.left + insetPx
        val top = b.top + insetPx
        val right = b.right - insetPx
        val bottom = b.bottom - insetPx
        val length = minOf(cornerLengthPx, (right - left) * 0.34f, (bottom - top) * 0.34f)
        if (length <= 0f) {
            return
        }

        canvas.drawLine(left, top, left + length, top, paint)
        canvas.drawLine(left, top, left, top + length, paint)

        canvas.drawLine(right, top, right - length, top, paint)
        canvas.drawLine(right, top, right, top + length, paint)

        canvas.drawLine(left, bottom, left + length, bottom, paint)
        canvas.drawLine(left, bottom, left, bottom - length, paint)

        canvas.drawLine(right, bottom, right - length, bottom, paint)
        canvas.drawLine(right, bottom, right, bottom - length, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
