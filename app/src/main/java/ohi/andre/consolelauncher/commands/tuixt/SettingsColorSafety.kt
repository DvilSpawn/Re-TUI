package ohi.andre.consolelauncher.commands.tuixt

import ohi.andre.consolelauncher.managers.xml.options.Theme
import kotlin.math.pow
import kotlin.math.roundToInt

/** Display-only safeguards: legacy transparent terminal colors must not hide settings. */
internal object SettingsColorSafety {
    fun color(role: Theme, raw: (Theme) -> Int): Int {
        val panel = composite(raw(Theme.settings_panel_background_color),
            composite(raw(Theme.background_color), BLACK))
        fun background(role: Theme) = if (role == Theme.settings_panel_background_color) panel else composite(raw(role), panel)
        val backgroundRole = when (role) {
            Theme.settings_panel_text_color, Theme.settings_panel_border_color -> Theme.settings_panel_background_color
            Theme.settings_header_text_color, Theme.settings_header_border_color -> Theme.settings_header_background_color
            Theme.settings_row_text_color, Theme.settings_row_border_color -> Theme.settings_row_background_color
            Theme.settings_input_text_color, Theme.settings_input_hint_color, Theme.settings_input_border_color,
            Theme.focus_color -> Theme.settings_input_background_color
            Theme.dialog_panel_text_color, Theme.dialog_panel_border_color -> Theme.dialog_panel_background_color
            Theme.dialog_header_text_color, Theme.dialog_header_border_color -> Theme.dialog_header_background_color
            Theme.button_text_color, Theme.button_border_color -> Theme.button_background_color
            Theme.primary_button_text_color, Theme.primary_button_border_color -> Theme.primary_button_background_color
            Theme.pressed_text_color, Theme.pressed_border_color -> Theme.pressed_background_color
            Theme.disabled_text_color -> Theme.disabled_background_color
            Theme.list_selected_text_color, Theme.list_selected_border_color -> Theme.list_selected_background_color
            Theme.toggle_on_text_color, Theme.toggle_on_border_color -> Theme.toggle_on_background_color
            Theme.toggle_off_text_color, Theme.toggle_off_border_color -> Theme.toggle_off_background_color
            Theme.icon_button_color, Theme.icon_button_border_color -> Theme.icon_button_background_color
            Theme.slider_track_color, Theme.slider_progress_color, Theme.slider_thumb_color -> Theme.settings_row_background_color
            else -> null
        }
        if (backgroundRole != null) {
            val minimum = if (role.name.endsWith("text_color") || role == Theme.settings_input_hint_color) 4.5 else 3.0
            return readable(raw(role), background(backgroundRole), minimum)
        }
        return when (role) {
            Theme.settings_panel_background_color -> panel
            Theme.settings_header_background_color, Theme.settings_row_background_color,
            Theme.settings_input_background_color, Theme.dialog_panel_background_color,
            Theme.dialog_header_background_color, Theme.button_background_color,
            Theme.primary_button_background_color, Theme.pressed_background_color,
            Theme.disabled_background_color, Theme.list_selected_background_color,
            Theme.toggle_on_background_color, Theme.toggle_off_background_color,
            Theme.icon_button_background_color -> background(role)
            else -> raw(role)
        }
    }

    fun readable(foreground: Int, background: Int, minimum: Double = 4.5): Int {
        if (contrast(composite(foreground, background), background) >= minimum) return foreground
        val opaque = foreground or BLACK
        if (contrast(opaque, background) >= minimum) return opaque
        return if (contrast(BLACK, background) >= contrast(WHITE, background)) BLACK else WHITE
    }

    fun composite(foreground: Int, background: Int): Int {
        val alpha = (foreground ushr 24) / 255.0
        fun channel(shift: Int) = (((foreground ushr shift and 255) * alpha +
            (background ushr shift and 255) * (1 - alpha)).roundToInt() shl shift)
        return BLACK or channel(16) or channel(8) or channel(0)
    }

    fun contrast(first: Int, second: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = (color ushr shift and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val a = luminance(first) + 0.05
        val b = luminance(second) + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    private const val BLACK = -0x1000000
    private const val WHITE = -1
}
