package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui

/**
 * Which home modes each setting changes, so Settings can show the ones that matter in the mode you are in. A
 * setting not covered below changes every mode. These follow what each home actually draws:
 *
 * - Search, Terminal and Dot hide Classic's status header, module pager, toolbar and suggestion strip
 *   ([ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.configureSearch]), so those are Classic's.
 * - Mono draws its own home and overrides every theme colour, suggestion colour, font, border and corner
 *   ([MonoMode]); those apply everywhere else. Its own `mono_*` settings apply only to it.
 * - The landscape split and Duo choices apply to the homes that split: Classic, Mono and Dot.
 *
 * Presets and backups ignore this: they save and restore every setting.
 */
object SettingModes {
    private val ALL: Set<String> = LauncherMode.values.toSet()
    private val CLASSIC = setOf(LauncherMode.CLASSIC)
    private val NOT_MONO = ALL - LauncherMode.MONO
    private val SEARCH_HOMES = setOf(LauncherMode.SEARCH, LauncherMode.TERMINAL, LauncherMode.DOT)
    private val SPLITTING = setOf(LauncherMode.CLASSIC, LauncherMode.MONO, LauncherMode.DOT)

    /** Classic's status labels, by the prefix their show/index/size/alignment/format/border/colour settings share. */
    private val STATUS = listOf("ascii", "battery", "device", "network", "notes", "ram", "storage", "time", "unlock", "weather", "session_info")

    private val byName: Map<XMLPrefsSave, Set<String>> = buildMap {
        // Mono's own.
        listOf(Behavior.mono_preset, Behavior.mono_ink_color, Behavior.mono_paper_color, Behavior.mono_layout,
            Behavior.mono_layout_style).forEach { put(it, setOf(LauncherMode.MONO)) }
        put(Behavior.dot_apps, setOf(LauncherMode.DOT))
        // The homes that split on wide screens and around a hinge.
        listOf(Behavior.duo_mode, Ui.landscape_fold_gutter_mm).forEach { put(it, SPLITTING) }
        listOf(Behavior.duo_swap_top_panes, Ui.split_duo_launcher, Ui.show_ascii_landscape, Ui.display_margin_landscape_mm)
            .forEach { put(it, CLASSIC) }
        // The search bar homes.
        listOf(Behavior.search_category_tabs).forEach { put(it, SEARCH_HOMES) }
        // Classic's module dock and the ASCII art above it.
        listOf(Behavior.show_module_dock, Behavior.ascii_animation, Behavior.ascii_animation_frame_delay_ms,
            Behavior.ascii_animation_max_file_kb, Behavior.ascii_effect, Behavior.ascii_effect_loop_seconds,
            Behavior.ascii_effect_speed, Ui.module_dock_spacing_dp, Ui.status_lines_margins, SurfaceBorderOption.unified_status_border,
            Ui.enable_battery_status).forEach { put(it, CLASSIC) }
        // Weather shows in Classic's status, Mono's weather panel and Dot's temperature ring.
        listOf(Behavior.weather_location, Behavior.weather_temperature_measure,
            Behavior.weather_update_time, Behavior.show_weather_updates, Behavior.weather_format)
            .forEach { put(it, setOf(LauncherMode.CLASSIC, LauncherMode.MONO, LauncherMode.DOT)) }
        put(Ui.show_weather, setOf(LauncherMode.CLASSIC, LauncherMode.MONO))
    }

    /** The modes [setting] changes. */
    fun of(setting: XMLPrefsSave): Set<String> {
        byName[setting]?.let { return it }
        val name = (setting as? Enum<*>)?.name.orEmpty()
        return when {
            setting is Toolbar -> CLASSIC
            setting is SurfaceBorderOption && (STATUS + "toolbar").any { name.startsWith(it + "_") } -> CLASSIC
            setting is SurfaceBorderOption -> NOT_MONO
            setting is Ui && STATUS.any { name == "show_$it" || name.startsWith(it + "_") } -> CLASSIC
            setting is Ui && name in CLASSIC_STATUS_UI -> CLASSIC
            setting is Behavior && STATUS.any { name == it + "_format" } -> CLASSIC
            setting is Behavior && name in CLASSIC_STATUS_BEHAVIOR -> CLASSIC
            setting is Theme && STATUS.any { name.startsWith(it + "_") } -> CLASSIC
            setting is Suggestions && setting.type() == XMLPrefsSave.COLOR -> NOT_MONO
            setting == Suggestions.transparent_suggestions -> NOT_MONO
            MonoMode.overrides(setting) -> NOT_MONO
            else -> ALL
        }
    }

    fun appliesTo(setting: XMLPrefsSave, mode: String): Boolean = mode in of(setting)

    /** Status labels whose names do not start with their label's prefix. */
    private val CLASSIC_STATUS_UI = setOf("show_device_name", "show_network_info", "show_storage_info", "show_unlock_counter",
        "show_scroll_notes_message", "show_session_info")
    private val CLASSIC_STATUS_BEHAVIOR = setOf("unlock_counter_cycle_start", "unlock_counter_format", "unlock_time_divider",
        "unlock_time_order", "network_info_format", "status_time_format", "time_format_separator", "device_format",
        "battery_low", "battery_medium", "battery_progress_bar", "battery_progress_bar_length", "battery_progress_bar_symbol")
}
