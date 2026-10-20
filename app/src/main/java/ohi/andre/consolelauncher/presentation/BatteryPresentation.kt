package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.regex.Pattern

/** Shared by BatteryManager and the settings preview, including bar/text and charging formats. */
internal object BatteryPresentation {
    fun render(context: Context, percentage: Float, charging: Boolean, size: Int,
               medium: Int, low: Int, values: HomeAppearance): CharSequence {
        val p = percentage.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
        if (values.bool(Behavior.battery_progress_bar)) {
            val length = values.number(Behavior.battery_progress_bar_length).toInt().coerceIn(0, 512)
            val symbol = values.raw(Behavior.battery_progress_bar_symbol).orEmpty().take(64)
            val full = Math.round(p / 100f * length)
            return SpannableStringBuilder().apply {
                append("[")
                val start = this.length
                append(symbol.repeat(full))
                setSpan(ForegroundColorSpan(values.color(Theme.battery_text_high)), start, this.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val emptyStart = this.length
                append(symbol.repeat(length - full))
                setSpan(ForegroundColorSpan(values.color(Theme.battery_text_low)), emptyStart, this.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("] ${p.toInt()}%")
            }
        }
        val separator = values.raw(Behavior.optional_values_separator).orEmpty().ifEmpty { Behavior.optional_values_separator.defaultValue().orEmpty() }
        val optional = Pattern.compile("%\\(([^" + Pattern.quote(separator) + "]*)" + Pattern.quote(separator) + "([^)]*)\\)", Pattern.CASE_INSENSITIVE)
        var text = values.raw(Behavior.battery_format).orEmpty()
        val matcher = optional.matcher(text)
        while (matcher.find()) text = text.replace(matcher.group(), matcher.group(if (charging) 1 else 2).orEmpty())
        text = text.replace("%v", p.toInt().toString(), ignoreCase = true)
        text = Tuils.patternNewline.matcher(text).replaceAll(Tuils.NEWLINE)
        val role = when {
            !values.bool(Ui.enable_battery_status) || p.toInt() > medium -> Theme.battery_text_high
            p.toInt() > low -> Theme.battery_text_medium
            else -> Theme.battery_text_low
        }
        return android.text.SpannableString(text).apply {
            setSpan(ForegroundColorSpan(values.color(role)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val pixels = ohi.andre.consolelauncher.tuils.LauncherFontScale.scaledSp(size.toFloat(), values.number(Ui.font_size_offset).toInt()) * context.resources.displayMetrics.scaledDensity
            setSpan(android.text.style.AbsoluteSizeSpan(pixels.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
