package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils

object RetuiThemeBridge {
    private const val KEYBOARD_PRIVATE_OPTIONS_PREFIX = "com.dvil.retui.keyboard"
    private const val KEYBOARD_APPLY_CONTEXT_ACTION = "com.dvil.retui.keyboard.APPLY_CONTEXT"

    fun putLauncherThemeExtras(intent: Intent, context: Context) {
        intent.putExtras(buildLauncherThemeBundle(context))
    }

    fun applyToKeyboardInput(
        input: EditText?,
        contextLabel: String? = null,
        mode: String? = null
    ) {
        if (input == null) {
            return
        }
        input.privateImeOptions = buildKeyboardPrivateOptions(contextLabel, mode)
    }

    fun sendToKeyboard(
        context: Context?,
        input: EditText?,
        contextLabel: String? = null,
        mode: String? = null
    ) {
        if (context == null || input == null) {
            return
        }
        val bundle = buildLauncherThemeBundle(context, contextLabel, mode)
        applyToKeyboardInput(input, contextLabel, mode)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.sendAppPrivateCommand(input, KEYBOARD_APPLY_CONTEXT_ACTION, bundle)
    }

    private fun buildKeyboardPrivateOptions(contextLabel: String?, mode: String?): String {
        val bundle = buildLauncherThemeBundle(null, contextLabel, mode)
        val options = buildList {
            KEYBOARD_COLOR_KEYS.forEach { key ->
                if (bundle.containsKey(key)) {
                    add(key + "=" + colorOption(bundle.getInt(key)))
                }
            }
            KEYBOARD_INT_KEYS.forEach { key ->
                if (bundle.containsKey(key)) {
                    add(key + "=" + bundle.getInt(key))
                }
            }
            KEYBOARD_FLOAT_KEYS.forEach { key ->
                if (bundle.containsKey(key)) {
                    add(key + "=" + bundle.getFloat(key))
                }
            }
            KEYBOARD_BOOLEAN_KEYS.forEach { key ->
                if (bundle.containsKey(key)) {
                    add(key + "=" + bundle.getBoolean(key))
                }
            }
            KEYBOARD_STRING_KEYS.forEach { key ->
                bundle.getString(key)?.takeIf { it.isNotBlank() }?.let { value ->
                    add(key + "=" + value)
                }
            }
        }
        return KEYBOARD_PRIVATE_OPTIONS_PREFIX + ":" + options.joinToString(";")
    }

    private fun buildLauncherThemeBundle(
        context: Context?,
        contextLabel: String? = null,
        mode: String? = null
    ): Bundle {
        val bundle = Bundle()
        val terminalSurfaceColor = terminalSurfaceColor()
        val terminalHeaderColor = AppearanceSettings.terminalHeaderTabBackground()
        val terminalBorderColor = AppearanceSettings.terminalBorderColor()
        val outputSurfaceColor = ColorUtils.blendARGB(terminalSurfaceColor, Color.BLACK, 0.10f)
        val inputSurfaceColor = ColorUtils.blendARGB(terminalSurfaceColor, Color.BLACK, 0.16f)
        val topDisplayMargin = XMLPrefsManager.get(Ui.display_margin_top_section)

        bundle.putInt("theme_bg", XMLPrefsManager.getColor(Theme.background_color))
        bundle.putInt("theme_text", XMLPrefsManager.getColor(Theme.output_text_color))
        bundle.putInt("theme_border", terminalBorderColor)
        bundle.putInt("terminal_bg", terminalSurfaceColor)
        bundle.putInt("terminal_window_background_color", terminalSurfaceColor)
        bundle.putInt("module_bg_color", terminalSurfaceColor)
        bundle.putInt("module_text_color", AppearanceSettings.moduleNameTextColor())
        bundle.putInt("module_border_color", terminalBorderColor)
        bundle.putInt("module_header_bg_color", terminalHeaderColor)
        bundle.putInt("module_header_text_color", AppearanceSettings.moduleNameTextColor())
        bundle.putInt("module_button_bg_color", AppearanceSettings.moduleButtonBackgroundColor())
        bundle.putInt("module_button_background_color", AppearanceSettings.moduleButtonBackgroundColor())
        bundle.putInt("module_button_text_color", AppearanceSettings.moduleNameTextColor())
        bundle.putInt("module_button_border_color", terminalBorderColor)
        bundle.putInt("input_bg_color", inputSurfaceColor)
        bundle.putInt("input_background_color", inputSurfaceColor)
        bundle.putInt("input_text_color", XMLPrefsManager.getColor(Theme.input_text_color))
        bundle.putInt("output_bg_color", outputSurfaceColor)
        bundle.putInt("output_background_color", outputSurfaceColor)
        bundle.putInt("output_text_color", XMLPrefsManager.getColor(Theme.output_text_color))
        bundle.putInt("output_border_color", terminalBorderColor)
        bundle.putInt("top_margin", 18)
        bundle.putInt("input_font_size", XMLPrefsManager.getInt(Ui.input_output_size))
        bundle.putString("display_margin_mm", topDisplayMargin)
        bundle.putString("display_margin_top_section", topDisplayMargin)
        bundle.putString("display_margin_bottom_section", XMLPrefsManager.get(Ui.display_margin_bottom_section))
        bundle.putBoolean("enable_dashed_border", AppearanceSettings.dashedBorders())
        bundle.putBoolean("dashed_borders", AppearanceSettings.dashedBorders())
        bundle.putInt("dashed_border_dash_length", AppearanceSettings.dashLength())
        bundle.putInt("dashed_border_gap_length", AppearanceSettings.dashGap())
        bundle.putFloat("dashed_border_stroke_width_dp", AppearanceSettings.dashedBorderStrokeWidthDp())
        bundle.putInt("module_corner_radius", AppearanceSettings.moduleCornerRadius())
        bundle.putInt("header_corner_radius", AppearanceSettings.headerCornerRadius())
        bundle.putInt("output_corner_radius", AppearanceSettings.outputCornerRadius())
        bundle.putInt("module_header_text_size", AppearanceSettings.moduleHeaderTextSize())
        bundle.putInt("module_body_text_size", AppearanceSettings.moduleBodyTextSize())
        bundle.putInt("output_header_text_size", AppearanceSettings.outputHeaderTextSize())
        bundle.putBoolean("enable_cyberdeck_mode", AppearanceSettings.cyberdeckMode())
        bundle.putBoolean("cyberdeck_mode", AppearanceSettings.cyberdeckMode())
        bundle.putBoolean("enable_crt_filter", AppearanceSettings.crtFilter())
        bundle.putBoolean("crt_filter", AppearanceSettings.crtFilter())

        contextLabel?.takeIf { it.isNotBlank() }?.let {
            bundle.putString("keyboard_context", it)
            bundle.putString("retui_context", it)
        }
        mode?.takeIf { it.isNotBlank() }?.let {
            bundle.putString("keyboard_mode", it)
            bundle.putString("retui_mode", it)
        }

        if (context != null) {
            Tuils.getTypeface(context)
            val fontPath = Tuils.fontPath
            if (fontPath != null && fontPath.startsWith("/")) {
                bundle.putString("font_path", fontPath)
            } else if (AppearanceSettings.useSystemFont()) {
                bundle.putString("font_name", "system")
            } else {
                bundle.putString("font_name", "lucida_console")
            }
        }

        return bundle
    }

    private fun terminalSurfaceColor(): Int {
        val terminalBg = AppearanceSettings.terminalWindowBackground()
        if (Color.alpha(terminalBg) > 0) {
            return terminalBg
        }
        val outputBg = XMLPrefsManager.getColor(Theme.output_background_color)
        return if (Color.alpha(outputBg) > 0) outputBg else terminalBg
    }

    private fun colorOption(color: Int): String {
        return "#" + Integer.toHexString(color).padStart(8, '0').takeLast(8)
    }

    private val KEYBOARD_COLOR_KEYS = arrayOf(
        "theme_bg",
        "theme_text",
        "theme_border",
        "terminal_bg",
        "terminal_window_background_color",
        "module_bg_color",
        "module_text_color",
        "module_border_color",
        "module_header_bg_color",
        "module_header_text_color",
        "module_button_bg_color",
        "module_button_background_color",
        "module_button_text_color",
        "module_button_border_color",
        "input_bg_color",
        "input_background_color",
        "input_text_color",
        "output_bg_color",
        "output_background_color",
        "output_text_color",
        "output_border_color"
    )

    private val KEYBOARD_INT_KEYS = arrayOf(
        "input_font_size",
        "dashed_border_dash_length",
        "dashed_border_gap_length",
        "module_corner_radius",
        "header_corner_radius",
        "output_corner_radius",
        "module_header_text_size",
        "module_body_text_size",
        "output_header_text_size"
    )

    private val KEYBOARD_FLOAT_KEYS = arrayOf(
        "dashed_border_stroke_width_dp"
    )

    private val KEYBOARD_BOOLEAN_KEYS = arrayOf(
        "enable_dashed_border",
        "dashed_borders",
        "enable_cyberdeck_mode",
        "cyberdeck_mode",
        "enable_crt_filter",
        "crt_filter"
    )

    private val KEYBOARD_STRING_KEYS = arrayOf(
        "keyboard_context",
        "retui_context",
        "keyboard_mode",
        "retui_mode"
    )
}
