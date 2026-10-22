package ohi.andre.consolelauncher.tuils

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class CrtOverlayDrawableTest {
    /** The previous per-frame loop, kept as the visual reference for the tiled shader. */
    private fun reference(canvas: Canvas, width: Int, height: Int, density: Float, accent: Int) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(10, Color.red(accent), Color.green(accent), Color.blue(accent)) })
        val scanline = Paint().apply { color = Color.argb(44, 0, 0, 0) }
        val beam = Paint().apply { color = Color.argb(10, 255, 255, 255) }
        val mask = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.argb(18, 0, 0, 0) }
        val step = max(3f, density * 3f); val line = max(1f, density); val glow = max(1f, density * 0.5f)
        var y = 0f
        while (y < height) {
            canvas.drawRect(0f, y, width.toFloat(), y + line, scanline)
            canvas.drawRect(0f, y + line, width.toFloat(), y + line + glow, beam)
            y += step
        }
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), mask); x += max(4f, density * 4f) }
    }

    @Test fun tiledOverlayMatchesPreviousRendering() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val accent = Color.rgb(255, 140, 40)
        // The overlay always lands on opaque Home content; transparent targets only measure un-premultiply noise.
        for (background in intArrayOf(Color.rgb(28, 12, 8), Color.rgb(220, 210, 200))) for (dpi in intArrayOf(160, 320, 420, 480, 560)) {
            val context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { densityDpi = dpi })
            val density = context.resources.displayMetrics.density
            val width = 1260; val height = 2800
            val expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                expected.eraseColor(background); actual.eraseColor(background)
                reference(Canvas(expected), width, height, density, accent)
                CrtOverlayDrawable(context, vignette = false).apply { setAccentColor(accent); setBounds(0, 0, width, height) }.draw(Canvas(actual))
                val a = IntArray(width * height).also { expected.getPixels(it, 0, width, 0, 0, width, height) }
                val b = IntArray(width * height).also { actual.getPixels(it, 0, width, 0, 0, width, height) }
                var worst = 0; var at = 0
                // The old loop stopped before the right edge, so it never drew the mask line in the last column.
                for (i in a.indices) if (i % width != width - 1) for (shift in intArrayOf(0, 8, 16, 24)) {
                    val d = abs((a[i] ushr shift and 0xFF) - (b[i] ushr shift and 0xFF))
                    if (d > worst) { worst = d; at = i }
                }
                // Pre-blending translucent layers differs from layering them only by 8-bit rounding.
                assertTrue("density $density bg ${Integer.toHexString(background)}: worst $worst at (${at % width},${at / width}) " +
                    "expected ${Integer.toHexString(a[at])} actual ${Integer.toHexString(b[at])}", worst <= 3)
            } finally { expected.recycle(); actual.recycle() }
        }
    }
}
