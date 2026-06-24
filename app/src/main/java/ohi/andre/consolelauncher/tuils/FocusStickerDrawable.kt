package ohi.andre.consolelauncher.tuils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min

class FocusStickerDrawable(
    private val fillColor: Int,
    private val strokeColor: Int,
    private val strokeWidthPx: Int,
    private val cutPx: Float
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = strokeColor
        strokeWidth = strokeWidthPx.toFloat()
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ColorUtils.setAlphaComponent(strokeColor, 150)
        strokeWidth = max(1f, strokeWidthPx / 2f)
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val left = b.left.toFloat()
        val top = b.top.toFloat()
        val right = b.right.toFloat()
        val bottom = b.bottom.toFloat()
        val cut = min(cutPx, min(b.width(), b.height()) * 0.22f)

        path.reset()
        path.moveTo(left + cut, top)
        path.lineTo(right - cut, top)
        path.lineTo(right, top + cut)
        path.lineTo(right, bottom - cut)
        path.lineTo(right - cut, bottom)
        path.lineTo(left + cut, bottom)
        path.lineTo(left, bottom - cut)
        path.lineTo(left, top + cut)
        path.close()

        canvas.drawPath(path, fillPaint)
        if (strokeWidthPx > 0) {
            canvas.drawPath(path, strokePaint)
        }

        val stripeTop = top + max(strokeWidthPx * 2f, 6f)
        val stripeBottom = bottom - max(strokeWidthPx * 2f, 6f)
        var x = left + cut
        while (x < right - cut) {
            canvas.drawLine(x, stripeTop, x + cut * 0.45f, stripeTop, detailPaint)
            canvas.drawLine(x, stripeBottom, x + cut * 0.45f, stripeBottom, detailPaint)
            x += max(10f, cut * 0.8f)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        detailPaint.alpha = min(alpha, 150)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        detailPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
