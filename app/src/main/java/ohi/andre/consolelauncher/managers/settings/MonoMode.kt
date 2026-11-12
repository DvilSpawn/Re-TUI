package ohi.andre.consolelauncher.managers.settings

import android.graphics.Color
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui

/**
 * Mono mode: the launcher in two colours. The only choice left to the user is the pair, ink and paper; the
 * font, borders, shadows and every colour role are taken over while the mode is on. Nothing is written to the
 * user's settings, so switching to another mode restores their own setup exactly.
 */
object MonoMode {
    const val FONT_ASSET = "VT323-Regular.ttf"
    private const val FONT_BOOST = 3

    /** Guards the mode check against reading settings that are themselves overridden. */
    private val checking = ThreadLocal<Boolean>()

    fun active(): Boolean {
        if (checking.get() == true) return false
        checking.set(true)
        try {
            return LauncherMode.resolve(LauncherSettings.raw(Behavior.launcher_mode),
                LauncherSettings.raw(Behavior.search_only_mode).toBoolean()) == LauncherMode.MONO
        } finally {
            checking.set(false)
        }
    }

    fun ink(): Int = colorOf(Behavior.mono_ink_color, 0xFFF2F0E6.toInt())
    fun paper(): Int = colorOf(Behavior.mono_paper_color, 0xFF0A0A0A.toInt())

    private fun colorOf(setting: Behavior, fallback: Int): Int =
        runCatching { Color.parseColor(LauncherSettings.raw(setting)) }.getOrDefault(fallback) or 0xFF000000.toInt()

    private fun hex(color: Int) = String.format("#%08X", color)

    private val fixedFalse: Set<XMLPrefsSave> = setOf(Ui.system_wallpaper, Ui.system_font, Ui.enable_crt_vignette,
        Ui.auto_color_pick, Behavior.enable_crt_filter, Behavior.enable_cyberdeck_mode)
    // Borders: the "dashed" style with no gap draws one solid square line around every surface.
    private val fixedTrue: Set<XMLPrefsSave> = setOf(Ui.enable_dashed_border)
    private val fixedZero: Set<XMLPrefsSave> = setOf(Ui.dashed_border_corner_radius, Ui.module_corner_radius,
        Ui.output_corner_radius, Ui.header_corner_radius)

    /** Whether mono mode takes [setting] over at all; cheap, so ordinary reads cost nothing extra. */
    private fun takesOver(setting: XMLPrefsSave): Boolean = when (setting) {
        Behavior.launcher_mode, Behavior.search_only_mode, Behavior.mono_ink_color, Behavior.mono_paper_color -> false
        in fixedFalse, in fixedTrue, in fixedZero, Ui.font_file, Ui.font_size_offset, Ui.dashed_border_gap_length, Ui.dashed_border_stroke_width, Ui.shadow_params, Ui.text_redraw_times, Ui.statusbar_light_icons -> true
        is Theme -> true
        is Suggestions -> setting.type() == XMLPrefsSave.COLOR
        is SurfaceBorderOption -> setting !== SurfaceBorderOption.unified_status_border
        else -> false
    }

    /** The value mono mode uses for [setting], or null when the user's own value stands. */
    fun override(setting: XMLPrefsSave?): String? {
        if (setting == null || !takesOver(setting) || !active()) return null
        return when (setting) {
            in fixedFalse -> "false"
            in fixedTrue -> "true"
            Ui.dashed_border_gap_length -> "0"
            Ui.dashed_border_stroke_width -> "1"
            in fixedZero -> "0"
            Ui.font_file -> ""
            // VT323 is a smaller face than the default at the same size; the user's own offset still counts.
            Ui.font_size_offset -> ((LauncherSettings.raw(Ui.font_size_offset)?.toIntOrNull() ?: 0) + FONT_BOOST).toString()
            Ui.shadow_params -> "0,0,0"
            Ui.text_redraw_times -> "1"
            Ui.statusbar_light_icons -> (luminance(paper()) < 0.5f).toString()
            is Theme -> hex(themeColor(setting))
            is Suggestions -> hex(suggestionColor(setting))
            is SurfaceBorderOption -> if (setting.type() == XMLPrefsSave.COLOR) hex(ink()) else "true"
            else -> hex(ink())
        }
    }

    /** Suggestion chips are inverted: ink chips with paper labels. */
    private fun suggestionColor(setting: Suggestions): Int = when {
        setting.name.endsWith("background_color") -> ink()
        setting.name.endsWith("text_color") -> paper()
        else -> ink()
    }

    /**
     * Every colour role as ink or paper, by what it is: backgrounds are paper and everything drawn on them is ink,
     * except selected, pressed and active things, which are inverted. Shadows vanish.
     */
    fun themeColor(role: Theme): Int {
        val ink = ink()
        val paper = paper()
        val name = role.name
        val inverted = listOf("selected", "selection", "pressed", "primary_button", "active", "toggle_on", "regex_match")
            .any { name.contains(it) }
        return when {
            name.contains("shadow") -> Color.TRANSPARENT
            name.startsWith("ansi_") -> if (name == "ansi_black" || name == "ansi_bright_black") paper else ink
            name.contains("scrim") -> (paper and 0x00FFFFFF) or 0xCC000000.toInt()
            name.contains("background") || name.endsWith("overlay_color") || name == "settings_screen_color" ||
                name.contains("slider_track") -> if (inverted) ink else paper
            inverted && !name.contains("border") -> paper
            else -> ink
        }
    }

    private fun luminance(color: Int) =
        ((color shr 16 and 255) * 299 + (color shr 8 and 255) * 587 + (color and 255) * 114) / 255000f
}
