package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.managers.xml.options.Theme
import kotlin.math.roundToInt

object ThemeColorResolver {
    private val previewValues = LinkedHashMap<Theme, String>()

    @JvmStatic
    fun color(role: Theme): Int = resolve(
        role,
        { LauncherSettings.get(it) },
        synchronized(previewValues) { HashMap(previewValues) }
    )

    @JvmStatic
    fun inheritedColor(role: Theme): Int = inherited(
        role,
        { LauncherSettings.get(it) },
        synchronized(previewValues) { HashMap(previewValues).apply { remove(role) } },
        HashSet()
    )

    @JvmStatic
    fun preview(role: Theme, raw: String?): Boolean {
        val value = raw?.trim() ?: return false
        if (!isValid(role, value)) return false
        synchronized(previewValues) { previewValues[role] = value }
        return true
    }

    @JvmStatic
    fun clearPreview() = synchronized(previewValues) { previewValues.clear() }

    @JvmStatic
    fun clearPreview(role: Theme) = synchronized(previewValues) { previewValues.remove(role) }

    @JvmStatic
    fun hasPreview(): Boolean = synchronized(previewValues) { previewValues.isNotEmpty() }

    @JvmStatic
    fun isValid(role: Theme, raw: String): Boolean =
        (role.advanced && raw.equals("auto", true)) || parse(raw) != null

    @JvmStatic
    fun blendPreservingAlpha(base: Int, overlay: Int, ratio: Float): Int {
        val amount = ratio.coerceIn(0f, 1f)
        val inverse = 1f - amount
        val alpha = minOf(alpha(base), lerp(alpha(base), alpha(overlay), amount))
        return argb(
            alpha,
            lerp(red(base), red(overlay), amount),
            lerp(green(base), green(overlay), amount),
            lerp(blue(base), blue(overlay), amount)
        )
    }

    @JvmStatic
    fun withMaxAlpha(color: Int, maximum: Int): Int =
        argb(minOf(alpha(color), maximum.coerceIn(0, 255)), red(color), green(color), blue(color))

    @JvmStatic
    fun ansi(index: Int, bright: Boolean): Int {
        val normal = arrayOf(
            Theme.ansi_black, Theme.ansi_red, Theme.ansi_green, Theme.ansi_yellow,
            Theme.ansi_blue, Theme.ansi_magenta, Theme.ansi_cyan, Theme.ansi_white
        )
        val intense = arrayOf(
            Theme.ansi_bright_black, Theme.ansi_bright_red, Theme.ansi_bright_green,
            Theme.ansi_bright_yellow, Theme.ansi_bright_blue, Theme.ansi_bright_magenta,
            Theme.ansi_bright_cyan, Theme.ansi_bright_white
        )
        return color((if (bright) intense else normal)[index.coerceIn(0, 7)])
    }

    internal fun resolve(
        role: Theme,
        values: (Theme) -> String?,
        previews: Map<Theme, String>
    ): Int = resolve(role, values, previews, HashSet())

    private fun resolve(
        role: Theme,
        values: (Theme) -> String?,
        previews: Map<Theme, String>,
        resolving: MutableSet<Theme>
    ): Int {
        if (!resolving.add(role)) return parse(role.defaultValue()) ?: WHITE
        val raw = previews[role] ?: values(role) ?: role.defaultValue()
        val explicit = if (raw.equals("auto", true)) null else parse(raw)
        val result = explicit ?: if (role.advanced) {
            inherited(role, values, previews, resolving)
        } else {
            parse(role.defaultValue()) ?: WHITE
        }
        resolving.remove(role)
        return result
    }

    private fun inherited(
        role: Theme,
        values: (Theme) -> String?,
        previews: Map<Theme, String>,
        resolving: MutableSet<Theme>
    ): Int {
        fun c(parent: Theme) = resolve(parent, values, previews, resolving)
        fun blend(base: Theme, overlay: Theme, ratio: Float) =
            blendPreservingAlpha(c(base), c(overlay), ratio)

        return when (role) {
            Theme.accent_color -> c(Theme.module_text_color)
            Theme.muted_text_color -> c(Theme.session_info_text_color)
            Theme.success_text_color -> c(Theme.battery_text_high)
            Theme.warning_text_color -> c(Theme.battery_text_medium)
            Theme.error_text_color -> c(Theme.battery_text_low)
            Theme.divider_color, Theme.focus_color -> c(Theme.terminal_border_color)
            Theme.disabled_text_color -> withMaxAlpha(c(Theme.muted_text_color), 150)
            Theme.disabled_background_color -> withMaxAlpha(c(Theme.module_button_background_color), 96)
            Theme.selection_background_color -> blend(Theme.module_button_background_color, Theme.accent_color, 0.25f)
            Theme.selection_text_color -> c(Theme.module_text_color)
            Theme.selection_border_color -> c(Theme.accent_color)
            Theme.pressed_background_color -> blend(Theme.module_button_background_color, Theme.accent_color, 0.18f)
            Theme.pressed_text_color -> c(Theme.module_text_color)
            Theme.pressed_border_color -> c(Theme.accent_color)

            Theme.button_background_color -> c(Theme.module_button_background_color)
            Theme.button_text_color -> c(Theme.module_text_color)
            Theme.button_border_color -> c(Theme.terminal_border_color)
            Theme.primary_button_background_color -> c(Theme.selection_background_color)
            Theme.primary_button_text_color -> c(Theme.selection_text_color)
            Theme.primary_button_border_color -> c(Theme.selection_border_color)
            Theme.icon_button_background_color -> c(Theme.button_background_color)
            Theme.icon_button_color -> c(Theme.toolbar_icon_color)
            Theme.icon_button_border_color -> c(Theme.button_border_color)
            Theme.field_background_color -> c(Theme.input_background_color)
            Theme.field_text_color -> c(Theme.input_text_color)
            Theme.field_hint_color -> withMaxAlpha(c(Theme.field_text_color), 150)
            Theme.field_border_color -> c(Theme.terminal_border_color)
            Theme.list_row_background_color -> c(Theme.module_button_background_color)
            Theme.list_row_text_color -> c(Theme.module_text_color)
            Theme.list_row_border_color -> c(Theme.terminal_border_color)
            Theme.list_selected_background_color -> c(Theme.selection_background_color)
            Theme.list_selected_text_color -> c(Theme.selection_text_color)
            Theme.list_selected_border_color -> c(Theme.selection_border_color)
            Theme.toggle_off_background_color -> c(Theme.button_background_color)
            Theme.toggle_off_text_color -> c(Theme.button_text_color)
            Theme.toggle_off_border_color -> c(Theme.button_border_color)
            Theme.toggle_on_background_color -> c(Theme.selection_background_color)
            Theme.toggle_on_text_color -> c(Theme.selection_text_color)
            Theme.toggle_on_border_color -> c(Theme.selection_border_color)
            Theme.slider_track_color -> withMaxAlpha(c(Theme.muted_text_color), 80)
            Theme.slider_progress_color, Theme.slider_thumb_color -> c(Theme.accent_color)
            Theme.chip_background_color -> c(Theme.button_background_color)
            Theme.chip_text_color -> c(Theme.button_text_color)
            Theme.chip_border_color -> c(Theme.button_border_color)
            Theme.tab_background_color -> c(Theme.terminal_header_background_color)
            Theme.tab_text_color -> c(Theme.module_text_color)
            Theme.tab_border_color -> c(Theme.terminal_header_border_color)

            Theme.app_drawer_panel_background_color -> c(Theme.terminal_window_background_color)
            Theme.app_drawer_panel_text_color, Theme.app_drawer_row_text_color -> c(Theme.apps_drawer_text_color)
            Theme.app_drawer_panel_border_color, Theme.app_drawer_selection_border_color -> c(Theme.terminal_border_color)
            Theme.app_drawer_header_background_color -> c(Theme.terminal_header_background_color)
            Theme.app_drawer_header_text_color -> c(Theme.apps_drawer_text_color)
            Theme.app_drawer_header_border_color -> c(Theme.terminal_header_border_color)
            Theme.app_drawer_row_background_color -> TRANSPARENT
            Theme.app_drawer_group_background_color -> c(Theme.app_drawer_row_background_color)
            Theme.app_drawer_group_text_color -> c(Theme.app_drawer_row_text_color)
            Theme.app_drawer_group_border_color -> c(Theme.app_drawer_panel_border_color)
            Theme.app_drawer_selection_background_color -> blend(Theme.app_drawer_panel_background_color, Theme.app_drawer_panel_text_color, 0.25f)
            Theme.app_drawer_selection_text_color -> c(Theme.app_drawer_panel_text_color)

            Theme.module_panel_background_color -> c(Theme.terminal_window_background_color)
            Theme.module_panel_text_color -> c(Theme.module_text_color)
            Theme.module_panel_border_color -> c(Theme.terminal_border_color)
            Theme.module_header_background_color -> c(Theme.terminal_header_background_color)
            Theme.module_header_text_color -> c(Theme.module_text_color)
            Theme.module_header_border_color -> c(Theme.terminal_header_border_color)
            Theme.module_dock_background_color -> c(Theme.module_button_background_color)
            Theme.module_dock_text_color -> c(Theme.module_text_color)
            Theme.module_dock_border_color -> c(Theme.terminal_border_color)
            Theme.module_action_background_color -> c(Theme.module_button_background_color)
            Theme.module_action_text_color -> c(Theme.module_text_color)
            Theme.module_action_border_color -> c(Theme.terminal_border_color)
            Theme.module_active_background_color -> c(Theme.selection_background_color)
            Theme.module_active_text_color -> c(Theme.selection_text_color)
            Theme.module_active_border_color -> c(Theme.selection_border_color)

            Theme.widget_panel_background_color -> c(Theme.module_panel_background_color)
            Theme.widget_panel_text_color -> c(Theme.module_panel_text_color)
            Theme.widget_panel_border_color -> c(Theme.module_panel_border_color)
            Theme.widget_header_background_color -> c(Theme.module_header_background_color)
            Theme.widget_header_text_color -> c(Theme.module_header_text_color)
            Theme.widget_header_border_color -> c(Theme.module_header_border_color)
            Theme.widget_row_background_color -> blend(Theme.widget_panel_background_color, Theme.ansi_black, 0.22f)
            Theme.widget_row_text_color -> c(Theme.widget_panel_text_color)
            Theme.widget_command_background_color -> blend(Theme.widget_panel_background_color, Theme.ansi_black, 0.16f)
            Theme.widget_command_text_color -> c(Theme.widget_panel_text_color)
            Theme.widget_command_border_color -> withMaxAlpha(c(Theme.widget_panel_border_color), 180)
            Theme.widget_control_background_color -> c(Theme.button_background_color)
            Theme.widget_control_text_color -> c(Theme.button_text_color)
            Theme.widget_control_border_color -> c(Theme.button_border_color)

            Theme.workspace_panel_background_color -> c(Theme.terminal_window_background_color)
            Theme.workspace_panel_text_color -> c(Theme.output_text_color)
            Theme.workspace_panel_border_color -> c(Theme.terminal_border_color)
            Theme.workspace_header_background_color -> c(Theme.terminal_header_background_color)
            Theme.workspace_header_text_color -> c(Theme.module_text_color)
            Theme.workspace_header_border_color -> c(Theme.terminal_header_border_color)
            Theme.workspace_output_background_color -> blend(Theme.workspace_panel_background_color, Theme.ansi_black, 0.10f)
            Theme.workspace_output_text_color -> c(Theme.output_text_color)
            Theme.workspace_input_background_color -> blend(Theme.workspace_panel_background_color, Theme.ansi_black, 0.16f)
            Theme.workspace_input_text_color -> c(Theme.input_text_color)
            Theme.workspace_control_background_color -> c(Theme.module_button_background_color)
            Theme.workspace_control_text_color -> c(Theme.module_text_color)
            Theme.workspace_control_border_color -> c(Theme.terminal_border_color)

            Theme.settings_screen_color -> c(Theme.settings_wallpaper_overlay_color)
            Theme.settings_panel_background_color -> c(Theme.terminal_header_background_color)
            Theme.settings_panel_text_color -> c(Theme.output_text_color)
            Theme.settings_panel_border_color -> c(Theme.terminal_header_border_color)
            Theme.settings_header_background_color -> c(Theme.settings_panel_background_color)
            Theme.settings_header_text_color -> c(Theme.accent_color)
            Theme.settings_header_border_color -> c(Theme.settings_panel_border_color)
            Theme.settings_row_background_color -> c(Theme.button_background_color)
            Theme.settings_row_text_color -> c(Theme.button_text_color)
            Theme.settings_row_border_color -> c(Theme.button_border_color)
            Theme.settings_input_background_color -> c(Theme.settings_panel_background_color)
            Theme.settings_input_text_color -> readableText(c(Theme.settings_input_background_color))
            Theme.settings_input_hint_color -> withMaxAlpha(c(Theme.settings_input_text_color), 150)
            Theme.settings_input_border_color -> c(Theme.settings_panel_border_color)

            Theme.dialog_scrim_color -> c(Theme.settings_wallpaper_overlay_color)
            Theme.dialog_panel_background_color -> c(Theme.settings_panel_background_color)
            Theme.dialog_panel_text_color -> c(Theme.settings_panel_text_color)
            Theme.dialog_panel_border_color -> c(Theme.settings_panel_border_color)
            Theme.dialog_header_background_color -> c(Theme.settings_header_background_color)
            Theme.dialog_header_text_color -> c(Theme.settings_header_text_color)
            Theme.dialog_header_border_color -> c(Theme.settings_header_border_color)
            Theme.overlay_scrim_color -> argb(210, 0, 0, 0)
            Theme.overlay_panel_background_color -> c(Theme.module_panel_background_color)
            Theme.overlay_panel_text_color -> c(Theme.module_panel_text_color)
            Theme.overlay_panel_border_color -> c(Theme.module_panel_border_color)
            Theme.overlay_header_background_color -> c(Theme.module_header_background_color)
            Theme.overlay_header_text_color -> c(Theme.module_header_text_color)
            Theme.overlay_header_border_color -> c(Theme.module_header_border_color)

            Theme.ansi_black -> rgb(0, 0, 0)
            Theme.ansi_red -> rgb(190, 62, 62)
            Theme.ansi_green -> blend(Theme.terminal_window_background_color, Theme.accent_color, 0.28f)
            Theme.ansi_yellow -> c(Theme.warning_text_color)
            Theme.ansi_blue -> blend(Theme.terminal_window_background_color, Theme.output_text_color, 0.18f)
            Theme.ansi_magenta -> rgb(176, 112, 204)
            Theme.ansi_cyan -> blend(Theme.accent_color, Theme.output_text_color, 0.16f)
            Theme.ansi_white -> c(Theme.output_text_color)
            Theme.ansi_bright_black -> c(Theme.muted_text_color)
            Theme.ansi_bright_red -> rgb(255, 70, 70)
            Theme.ansi_bright_green -> blend(Theme.accent_color, Theme.output_text_color, 0.28f)
            Theme.ansi_bright_yellow -> blend(Theme.warning_text_color, Theme.output_text_color, 0.28f)
            Theme.ansi_bright_blue -> blend(Theme.output_text_color, Theme.accent_color, 0.42f)
            Theme.ansi_bright_magenta -> rgb(220, 150, 255)
            Theme.ansi_bright_cyan -> blend(Theme.accent_color, Theme.output_text_color, 0.32f)
            Theme.ansi_bright_white -> c(Theme.output_text_color)
            else -> parse(role.defaultValue()) ?: WHITE
        }
    }

    internal fun parse(raw: String?): Int? {
        val digits = raw?.trim()?.removePrefix("#") ?: return null
        if (!digits.matches(Regex("[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))) return null
        val value = digits.toLong(16)
        return (if (digits.length == 6) value or 0xFF000000 else value).toInt()
    }

    private fun readableText(background: Int): Int {
        val luminance = (0.2126 * red(background) + 0.7152 * green(background) + 0.0722 * blue(background)) / 255.0
        return if (luminance > 0.45) BLACK else WHITE
    }

    private fun lerp(start: Int, end: Int, ratio: Float): Int =
        (start + (end - start) * ratio).roundToInt().coerceIn(0, 255)

    private fun alpha(color: Int) = color ushr 24 and 0xFF
    private fun red(color: Int) = color ushr 16 and 0xFF
    private fun green(color: Int) = color ushr 8 and 0xFF
    private fun blue(color: Int) = color and 0xFF
    private fun rgb(red: Int, green: Int, blue: Int) = argb(255, red, green, blue)
    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

    private const val TRANSPARENT = 0x00000000
    private const val BLACK = -0x1000000
    private const val WHITE = -0x1
}
