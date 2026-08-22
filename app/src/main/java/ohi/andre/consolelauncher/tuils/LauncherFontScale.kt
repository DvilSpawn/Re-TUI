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

    internal fun scaledSp(baseSp: Float, offsetSp: Int): Float =
        (baseSp + offsetSp.coerceIn(MIN_OFFSET, MAX_OFFSET)).coerceAtLeast(1f)

    internal fun effectiveSp(baseSp: Int, offsetSp: Int, followsMaster: Boolean): Float =
        if (followsMaster) scaledSp(baseSp.toFloat(), offsetSp) else baseSp.coerceAtLeast(1).toFloat()

    internal fun adjustedBaseSp(current: Int, delta: Int, min: Int, max: Int): Int =
        (current + delta).coerceIn(min, max)

    @JvmStatic
    fun applyRecursively(view: View?) {
        if (view == null || view is AsciiArtTextView) return

        if (view is TextView) apply(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyRecursively(view.getChildAt(index))
            }
        }
    }

    private fun apply(view: TextView) {
        val density = view.resources.displayMetrics.scaledDensity
        if (density <= 0f) return

        val currentSp = view.textSize / density
        val previousScaled = view.getTag(R.id.retui_font_scaled_size_sp) as? Float
        var baseSp = view.getTag(R.id.retui_font_base_size_sp) as? Float
        if (baseSp == null || previousScaled == null || abs(currentSp - previousScaled) > 0.05f) {
            baseSp = currentSp
            view.setTag(R.id.retui_font_base_size_sp, baseSp)
        }

        val scaledSp = scaledSp(baseSp)
        if (abs(currentSp - scaledSp) > 0.05f) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSp)
        }
        view.setTag(R.id.retui_font_scaled_size_sp, scaledSp)
    }
}
