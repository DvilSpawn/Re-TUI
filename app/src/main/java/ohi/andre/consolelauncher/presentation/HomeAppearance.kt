package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.*

/** Immutable values: rendering a draft never writes the settings singleton or its caches. */
internal class HomeAppearance(private val values: Map<XMLPrefsSave, String?>) {
    private val colors=java.util.concurrent.ConcurrentHashMap<Theme,Int>()
    private var typeface:Typeface?=null
    fun raw(key: XMLPrefsSave): String? = values[key] ?: key.defaultValue()
    private fun effective(key: XMLPrefsSave) = raw(key)?.takeIf { it.isNotEmpty() } ?: key.defaultValue()
    fun bool(key: XMLPrefsSave) = effective(key).toBoolean()
    fun number(key: XMLPrefsSave, fallback: Float = 0f) = effective(key)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: fallback
    fun rawColor(key: XMLPrefsSave): Int = runCatching { Color.parseColor(raw(key)) }.getOrDefault(Color.WHITE)
    fun color(role: Theme):Int = colors.getOrPut(role) { ThemeColorResolver.resolve(role, { raw(it) }, emptyMap()) }
    fun differs(saved: HomeAppearance) = values.any { (key,_) -> changed(key,saved) }
    fun changed(key: XMLPrefsSave, saved: HomeAppearance) = raw(key) != saved.raw(key)
    fun radius(key: Ui): Int = (if (raw(key) != key.defaultValue()) number(key) else number(Ui.dashed_border_corner_radius)).toInt().coerceIn(0,48)
    fun font(context: Context): Typeface = typeface ?: LauncherTypeface.load(context,Tuils.getFolder(),
        bool(Ui.system_font),raw(Ui.font_file)).typeface.also { typeface=it }
    fun textSize(key: Ui) = LauncherFontScale.scaledSp(number(key, 12f), number(Ui.font_size_offset).toInt())
    fun overlay(draft: (XMLPrefsSave) -> String?, hasDraft: (XMLPrefsSave) -> Boolean): HomeAppearance =
        HomeAppearance(values.mapValues { (k,v) -> if (hasDraft(k)) draft(k) ?: v else v })

    companion object {
        @Volatile private var cached: Pair<Long,HomeAppearance>? = null

        // Snapshots copy every setting and drop the colour/font caches, so reuse one until settings change.
        fun saved(): HomeAppearance {
            val revision = LauncherSettings.currentRevision()
            cached?.takeIf { it.first == revision }?.let { return it.second }
            return HomeAppearance(LauncherSettings.currentSnapshot().values).also { cached = revision to it }
        }

        /** Shared by Home's existing surface construction and the detached draft scene. */
        fun surface(context: Context, view: View, fill: Int, border: Int, radiusPx: Float,
                    dashed: Boolean, notch: Boolean, surface: SurfaceBorder,
                    allowBorder: Boolean, allowFrame: Boolean, appearance: HomeAppearance? = null) {
            view.background = TerminalBorderRuntime.panelDrawablePx(context, fill, border, 1.5f, radiusPx,
                dashed, notch, allowBorder && (appearance?.let { (dashed || it.bool(Behavior.enable_cyberdeck_mode)) && it.bool(surface.enabled) } ?: AppearanceSettings.surfaceBorderEnabled(surface)), allowFrame,
                FrameTarget.fromSurface(surface), appearance?.borderStyle())
        }
    }

    fun borderStyle() = TerminalBorderRuntime.Style(bool(Behavior.enable_cyberdeck_mode),
        number(Ui.dashed_border_stroke_width,1.5f).coerceIn(.5f,8f), number(Ui.dashed_border_dash_length,8f),
        number(Ui.dashed_border_gap_length,4f), radius(Ui.header_corner_radius))

}
