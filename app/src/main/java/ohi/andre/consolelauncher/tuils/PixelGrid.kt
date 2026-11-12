package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi

/**
 * A hidden, purely visual effect: the launcher drawn on a coarse pixel grid, so text and motion land on whole
 * cells, optionally dithered to 1 bit (black and the theme ink). It only re-samples what is drawn; layout and
 * touch are untouched. Android 13+ (a runtime shader); elsewhere it does nothing.
 */
object PixelGrid {
    enum class Mode(val id: String) { SNAP("snap"), ONE_BIT("1bit") }

    private const val PREFS = "pixel_grid"
    private const val MIN_CELL = 0.75f
    private const val MAX_CELL = 6f
    const val DEFAULT_CELL = 1f

    /** Averages each cell; in 1-bit mode a 4×4 Bayer threshold picks ink or black per cell. */
    private const val SHADER = """
        uniform shader content;
        uniform float cell;
        uniform float oneBit;
        uniform half3 ink;

        float bayer2(float2 v) { return 2.0 * mod(v.x + v.y, 2.0) + mod(v.y, 2.0); }

        float luma(half4 c) { return c.a <= 0.0 ? 0.0 : dot(c.rgb / c.a, half3(0.299, 0.587, 0.114)); }

        // Four reads a quarter-cell in from each corner; the GPU's filtering blends the pixels around each, so
        // together they cover the cell and thin strokes shade it instead of slipping between samples.
        half4 cellColor(float2 index) {
            float2 c = (index + 0.5) * cell;
            float q = cell * 0.25;
            return (content.eval(c + float2(-q, -q)) + content.eval(c + float2(q, -q)) +
                    content.eval(c + float2(-q, q)) + content.eval(c + float2(q, q))) * 0.25;
        }

        half4 main(float2 p) {
            float2 index = floor(p / cell);
            half4 color = cellColor(index);
            if (oneBit < 0.5 || color.a <= 0.0) return color;
            float lum = luma(color);
            // The darkest, brightest and average of the neighbourhood: four reads three cells out diagonally.
            float2 centre = (index + 0.5) * cell;
            float low = lum;
            float high = lum;
            float mean = lum;
            for (int k = 0; k < 4; k++) {
                float2 d = float2(k < 2 ? -3.0 : 3.0, mod(float(k), 2.0) < 0.5 ? -3.0 : 3.0);
                float l = luma(content.eval(centre + d * cell));
                low = min(low, l);
                high = max(high, l);
                mean += l;
            }
            mean /= 5.0;
            bool on;
            if (high - low > 0.12) {
                // Contrast nearby (text, lines, icons). Each side of the edge takes its own flat colour; when both
                // would be the same (light text on a light chip), the side the area is mostly made of keeps it and
                // the other side flips, so the detail always reads and nothing grows a halo.
                bool lowOn = low > 0.44;
                bool highOn = high > 0.44;
                bool nearHigh = lum - low > high - lum;
                if (lowOn != highOn) on = nearHigh ? highOn : lowOn;
                else {
                    bool mostlyHigh = mean - low > high - mean;
                    on = nearHigh == mostlyHigh ? lowOn : !lowOn;
                }
            } else {
                // Flat areas: panels go black, light areas ink, and only in-between tones dither.
                float tone = smoothstep(0.30, 0.58, lum);
                float2 small = mod(index, 2.0);
                float2 large = mod(floor(index / 2.0), 2.0);
                on = tone > (4.0 * bayer2(small) + bayer2(large) + 0.5) / 16.0;
            }
            half3 dot = on ? ink : half3(0.0);
            return half4(dot * color.a, color.a);
        }
    """

    fun supported() = Build.VERSION.SDK_INT >= 33

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun mode(context: Context) = prefs(context).getString("mode", Mode.SNAP.id).let { id -> Mode.entries.firstOrNull { it.id == id } ?: Mode.SNAP }
    fun cellDp(context: Context) = prefs(context).getFloat("cell", DEFAULT_CELL).coerceIn(MIN_CELL, MAX_CELL)

    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("enabled", on).apply()
    fun setMode(context: Context, mode: Mode) = prefs(context).edit().putString("mode", mode.id).apply()
    fun setCell(context: Context, dp: Float) = prefs(context).edit().putFloat("cell", dp.coerceIn(MIN_CELL, MAX_CELL)).apply()

    /** Applies or clears the effect on [views] to match the saved state. [ink] is the 1-bit foreground colour. */
    fun apply(context: Context, views: List<View>, ink: Int) {
        if (!supported()) return
        val effect = if (enabled(context)) effect(context, ink) else null
        views.forEach { it.setRenderEffect(effect) }
    }

    /** Null, and the grid switched off, if the shader cannot be built: a gimmick must never break Home. */
    private fun effect(context: Context, ink: Int): RenderEffect? = runCatching { build(context, ink) }
        .onFailure { setEnabled(context, false) }.getOrNull()

    @RequiresApi(33)
    private fun build(context: Context, ink: Int): RenderEffect {
        val shader = RuntimeShader(SHADER).apply {
            // Whole device pixels per cell, rounded down: past about 3 px small text stops being readable.
            setFloatUniform("cell", maxOf(2f, kotlin.math.floor(cellDp(context) * context.resources.displayMetrics.density)))
            setFloatUniform("oneBit", if (mode(context) == Mode.ONE_BIT) 1f else 0f)
            setFloatUniform("ink", (ink shr 16 and 255) / 255f, (ink shr 8 and 255) / 255f, (ink and 255) / 255f)
        }
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}
