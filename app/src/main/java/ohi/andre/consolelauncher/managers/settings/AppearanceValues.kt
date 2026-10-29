package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.Tuils

/** The same renderer can read saved settings or a private editor draft. Never writes settings. */
class AppearanceValues(private val read: (XMLPrefsSave) -> String? = LauncherSettings::get) {
    fun get(item: XMLPrefsSave): String = read(item) ?: item.defaultValue().orEmpty()
    fun bool(item: XMLPrefsSave): Boolean = get(item).toBoolean()
    fun int(item: XMLPrefsSave): Int = get(item).toIntOrNull() ?: item.defaultValue()?.toIntOrNull() ?: 0
    fun float(item: XMLPrefsSave): Float = get(item).toFloatOrNull()?.takeIf { it.isFinite() }
        ?: item.defaultValue()?.toFloatOrNull() ?: 0f
    fun color(role: Theme): Int = ThemeColorResolver.resolve(role, { get(it) }, emptyMap())
    fun color(item: XMLPrefsSave): Int = if (item is Theme) color(item) else
        runCatching { android.graphics.Color.parseColor(get(item)) }.getOrElse { runCatching { android.graphics.Color.parseColor(item.defaultValue()) }.getOrDefault(android.graphics.Color.WHITE) }
    fun radius(item: Ui): Int = int(if (get(item) == item.defaultValue()) Ui.dashed_border_corner_radius else item).coerceIn(0, 48)
    fun size(item: Ui): Float = LauncherFontScale.scaledSp(float(item), int(Ui.font_size_offset))
    fun applySize(view: android.widget.TextView, item: Ui) = applySize(view, float(item))
    fun applySize(view: android.widget.TextView, baseSp: Float) {
        val scaled = LauncherFontScale.scaledSp(baseSp, int(Ui.font_size_offset))
        view.textSize = scaled
        view.setTag(ohi.andre.consolelauncher.R.id.retui_font_base_size_sp, baseSp)
        view.setTag(ohi.andre.consolelauncher.R.id.retui_font_scaled_size_sp, scaled)
    }
    fun sized(context: Context, text: CharSequence?, size: Int): android.text.SpannableString =
        SpannableString(text ?: "").apply {
            setSpan(AbsoluteSizeSpan((LauncherFontScale.scaledSp(size.toFloat(), int(Ui.font_size_offset)) * context.resources.displayMetrics.scaledDensity).toInt()),
                0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    fun span(context: Context, text: CharSequence?, role: Theme, size: Int): CharSequence =
        sized(context, text, size).apply { setSpan(ForegroundColorSpan(color(role)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    fun typeface(context: Context): Typeface = if (bool(Ui.system_font)) Typeface.DEFAULT else {
        val file = Tuils.resolveConfiguredFontFile(Tuils.getFolder(), get(Ui.font_file))
        runCatching { if (file != null) Typeface.createFromFile(file) else Typeface.createFromAsset(context.assets, Tuils.BUNDLED_FONT_ASSET) }
            .getOrDefault(Typeface.MONOSPACE)
    }
    companion object { val saved = AppearanceValues() }
}
