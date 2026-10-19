package ohi.andre.consolelauncher.tuils

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.abs
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Ui

object LauncherFontScale {
    const val MIN_OFFSET = -8
    const val MAX_OFFSET = 8

    @JvmStatic
    fun offsetSp(): Int =
        LauncherSettings.getInt(Ui.font_size_offset).coerceIn(MIN_OFFSET, MAX_OFFSET)

    @JvmStatic
    fun scaledSp(baseSp: Float): Float = scaledSp(baseSp, offsetSp())

    fun terminalTextSizeSp(): Float = scaledSp(12f)

    internal fun scaledSp(baseSp: Float, offsetSp: Int): Float =
        (baseSp + offsetSp.coerceIn(MIN_OFFSET, MAX_OFFSET)).coerceAtLeast(1f)

    internal fun effectiveSp(baseSp: Int, offsetSp: Int, followsMaster: Boolean): Float =
        if (followsMaster) scaledSp(baseSp.toFloat(), offsetSp) else baseSp.coerceAtLeast(1).toFloat()

    internal fun adjustedBaseSp(current: Int, delta: Int, min: Int, max: Int): Int =
        (current + delta).coerceIn(min, max)

    @JvmStatic
    fun applyRecursively(view: View?) {
        if (view == null || view is AsciiArtTextView || view is android.appwidget.AppWidgetHostView) return

        if (view is TextView) apply(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyRecursively(view.getChildAt(index))
            }
        }
    }

    internal fun pixelsToSp(view: TextView): Float = if (android.os.Build.VERSION.SDK_INT >= 34)
        TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, view.textSize, view.resources.displayMetrics)
    else view.textSize / view.resources.displayMetrics.scaledDensity

    internal fun setBaseSize(view: TextView, baseSp: Float, offset: Int) {
        val scaled = scaledSp(baseSp, offset)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaled)
        view.setTag(R.id.retui_font_base_size_sp, baseSp)
        view.setTag(R.id.retui_font_scaled_size_sp, scaled)
    }

    internal fun apply(view: TextView, offset: Int = offsetSp()) {
        val density = view.resources.displayMetrics.scaledDensity
        if (density <= 0f) return

        val currentSp = pixelsToSp(view)
        val previousScaled = view.getTag(R.id.retui_font_scaled_size_sp) as? Float
        var baseSp = view.getTag(R.id.retui_font_base_size_sp) as? Float
        if (baseSp == null || previousScaled == null || abs(currentSp - previousScaled) > 0.05f) {
            baseSp = currentSp
            view.setTag(R.id.retui_font_base_size_sp, baseSp)
        }

        val scaledSp = scaledSp(baseSp, offset)
        if (abs(currentSp - scaledSp) > 0.05f) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSp)
        }
        view.setTag(R.id.retui_font_scaled_size_sp, scaledSp)
    }
}
