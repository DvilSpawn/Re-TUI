package ohi.andre.consolelauncher.tuils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import kotlin.math.max

class CyberpunkBackdropDrawable(
    private val backgroundColor: Int,
    accentColor: Int,
    warningColor: Int
) : Drawable() {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ThemeColorResolver.withMaxAlpha(accentColor, 70)
    }
    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = ThemeColorResolver.withMaxAlpha(accentColor, 115)
    }
    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        color = ThemeColorResolver.withMaxAlpha(warningColor, 130)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) {
            return
        }

        canvas.drawRect(b, backgroundPaint)

        val width = b.width().toFloat()
        val height = b.height().toFloat()
        val step = max(18f, width / 26f)
        var y = b.top + step
        while (y < b.bottom) {
            var x = b.left + step
            while (x < b.right) {
                canvas.drawRect(x, y, x + 2f, y + 2f, gridPaint)
                x += step
            }
            y += step
        }

        val leftRail = b.left + width * 0.08f
        canvas.drawLine(leftRail, b.top + height * 0.1f, leftRail, b.bottom - height * 0.12f, railPaint)
        canvas.drawLine(leftRail, b.top + height * 0.1f, leftRail + width * 0.2f, b.top + height * 0.1f, railPaint)
        canvas.drawLine(leftRail, b.bottom - height * 0.12f, leftRail + width * 0.18f, b.bottom - height * 0.12f, railPaint)

        val arcSize = width * 0.58f
        val arcRect = RectF(
            b.right - arcSize * 0.72f,
            b.bottom - arcSize * 0.64f,
            b.right + arcSize * 0.28f,
            b.bottom + arcSize * 0.36f
        )
        canvas.drawArc(arcRect, 205f, 112f, false, railPaint)

        val warningY = b.top + height * 0.18f
        canvas.drawLine(b.right - width * 0.33f, warningY, b.right - width * 0.08f, warningY, warningPaint)
        canvas.drawLine(b.right - width * 0.08f, warningY, b.right - width * 0.05f, warningY + 14f, warningPaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        gridPaint.alpha = alpha
        railPaint.alpha = alpha
        warningPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        gridPaint.colorFilter = colorFilter
        railPaint.colorFilter = colorFilter
        warningPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
