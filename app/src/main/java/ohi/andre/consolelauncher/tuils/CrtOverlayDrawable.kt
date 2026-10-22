package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings

/**
 * Tint, scanlines, beam and mask repeat with a fixed period, so they are pre-blended once into a small tile
 * and filled with a repeating shader: two draw operations per frame instead of hundreds of translucent rects.
 */
class CrtOverlayDrawable(context: Context, vignette: Boolean = AppearanceSettings.crtVignette()) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val scanlineStepPx = max(3f, density * 3f)
    private val scanlineHeightPx = max(1f, density)
    private val beamHeightPx = max(1f, density * 0.5f)
    private val maskStepPx = max(4f, density * 4f)
    private val patternPaint = Paint()
    private val vignettePaint = if (vignette) Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } else null

    init { buildPattern(Color.rgb(120, 255, 190)) }

    fun setAccentColor(color: Int) {
        buildPattern(color)
        invalidateSelf()
    }

    private fun buildPattern(accent: Int) {
        val width = period(maskStepPx)
        val height = period(scanlineStepPx)
        val tile = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        canvas.drawColor(Color.argb(10, Color.red(accent), Color.green(accent), Color.blue(accent)))
        val scanline = Paint().apply { color = Color.argb(44, 0, 0, 0) }
        val beam = Paint().apply { color = Color.argb(10, 255, 255, 255) }
        val mask = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.argb(18, 0, 0, 0) }
        var y = 0f
        while (y < height) {
            canvas.drawRect(0f, y, width.toFloat(), y + scanlineHeightPx, scanline)
            canvas.drawRect(0f, y + scanlineHeightPx, width.toFloat(), y + scanlineHeightPx + beamHeightPx, beam)
            y += scanlineStepPx
        }
        // Inclusive bound: a 1px line on the tile edge lands in either the first or last column after rounding.
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), mask)
            x += maskStepPx
        }
        patternPaint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(origin()) }
    }

    /** Smallest whole-pixel span of complete periods, so fractional steps (e.g. 10.5px) repeat exactly. */
    private fun period(step: Float): Int {
        for (count in 1..8) {
            val span = step * count
            if (abs(span - span.roundToInt()) < 0.01f) return span.roundToInt()
        }
        return step.roundToInt().coerceAtLeast(1)
    }

    private fun origin() = Matrix().apply { setTranslate(bounds.left.toFloat(), bounds.top.toFloat()) }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        patternPaint.shader?.setLocalMatrix(origin())
        vignettePaint?.shader = if (bounds.isEmpty) null else RadialGradient(
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
        if (b.isEmpty) return
        canvas.drawRect(b, patternPaint)
        vignettePaint?.let { canvas.drawRect(b, it) }
    }

    override fun setAlpha(alpha: Int) {
        patternPaint.alpha = alpha
        vignettePaint?.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        patternPaint.colorFilter = colorFilter
        vignettePaint?.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
