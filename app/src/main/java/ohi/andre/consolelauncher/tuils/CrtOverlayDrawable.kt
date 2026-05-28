package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max

class CrtOverlayDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(10, 120, 255, 190)
    }
    private val scanlinePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(44, 0, 0, 0)
    }
    private val beamPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(10, 255, 255, 255)
    }
    private val maskPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(18, 0, 0, 0)
    }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val scanlineStepPx = max(3f, density * 3f)
    private val scanlineHeightPx = max(1f, density)
    private val beamHeightPx = max(1f, density * 0.5f)
    private val maskStepPx = max(4f, density * 4f)

    fun setAccentColor(color: Int) {
        tintPaint.color = Color.argb(
            10,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) {
            vignettePaint.shader = null
            return
        }
        vignettePaint.shader = RadialGradient(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            max(bounds.width(), bounds.height()) * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.argb(116, 0, 0, 0)),
            floatArrayOf(0.58f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) {
            return
        }

        canvas.drawRect(b, tintPaint)

        var y = b.top.toFloat()
        while (y < b.bottom) {
            canvas.drawRect(b.left.toFloat(), y, b.right.toFloat(), y + scanlineHeightPx, scanlinePaint)
            canvas.drawRect(
                b.left.toFloat(),
                y + scanlineHeightPx,
                b.right.toFloat(),
                y + scanlineHeightPx + beamHeightPx,
                beamPaint
            )
            y += scanlineStepPx
        }

        var x = b.left.toFloat()
        while (x < b.right) {
            canvas.drawLine(x, b.top.toFloat(), x, b.bottom.toFloat(), maskPaint)
            x += maskStepPx
        }

        canvas.drawRect(b, vignettePaint)
    }

    override fun setAlpha(alpha: Int) {
        tintPaint.alpha = alpha
        scanlinePaint.alpha = alpha
        beamPaint.alpha = alpha
        maskPaint.alpha = alpha
        vignettePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        tintPaint.colorFilter = colorFilter
        scanlinePaint.colorFilter = colorFilter
        beamPaint.colorFilter = colorFilter
        maskPaint.colorFilter = colorFilter
        vignettePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
