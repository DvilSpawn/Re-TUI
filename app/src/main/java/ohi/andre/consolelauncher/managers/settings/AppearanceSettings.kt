package ohi.andre.consolelauncher.managers.settings

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import java.util.Locale
import kotlin.math.min
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui

object AppearanceSettings {
    @JvmStatic
    fun autoColorPick(): Boolean = LauncherSettings.getBoolean(Ui.auto_color_pick)

    @JvmStatic
    fun cyberdeckMode(): Boolean = LauncherSettings.getBoolean(Behavior.enable_cyberdeck_mode)

    @JvmStatic
    fun crtFilter(): Boolean = LauncherSettings.getBoolean(Behavior.enable_crt_filter)

    @JvmStatic
    fun useSystemFont(): Boolean = LauncherSettings.getBoolean(Ui.system_font)

    @JvmStatic
    fun fontFile(): String? = LauncherSettings.get(Ui.font_file)

    @JvmStatic
    fun musicWidgetBorderColor(): Int = terminalBorderColor()

    @JvmStatic
    fun musicWidgetTextColor(): Int = moduleNameTextColor()

    @JvmStatic
    fun notificationWidgetBorderColor(): Int = terminalBorderColor()

    @JvmStatic
    fun notificationWidgetTextColor(): Int = moduleNameTextColor()

    @JvmStatic
    fun terminalWindowBackground(): Int = LauncherSettings.getColor(Theme.terminal_window_background_color)

    @JvmStatic
    fun terminalHeaderBackground(): Int {
        val terminalBg = terminalWindowBackground()
        if (Color.alpha(terminalBg) > 0) {
            return ColorUtils.setAlphaComponent(terminalBg, 255)
        }

        val baseBg = LauncherSettings.getColor(Theme.background_color)
        if (Color.alpha(baseBg) > 0) {
            return ColorUtils.setAlphaComponent(baseBg, 255)
        }

        return Color.BLACK
    }

    @JvmStatic
    fun terminalHeaderTabBackground(): Int = LauncherSettings.getColor(Theme.terminal_header_background_color)

    @JvmStatic
    fun terminalHeaderTabBorderColor(): Int = LauncherSettings.getColor(Theme.terminal_header_border_color)

    @JvmStatic
    fun dashedBorders(): Boolean = LauncherSettings.getBoolean(Ui.enable_dashed_border)

    @JvmStatic
    fun dashedBorderColor(): Int = LauncherSettings.getColor(Theme.terminal_border_color)

    @JvmStatic
    fun terminalBorderColor(): Int = dashedBorderColor()

    @JvmStatic
    fun moduleButtonBackgroundColor(): Int = LauncherSettings.getColor(Theme.module_button_background_color)

    @JvmStatic
    fun moduleNameTextColor(): Int = LauncherSettings.getColor(Theme.module_text_color)

    @JvmStatic
    fun moduleButtonBorderColor(): Int = terminalBorderColor()

    @JvmStatic
    fun dashLength(): Int = LauncherSettings.getInt(Ui.dashed_border_dash_length)

    @JvmStatic
    fun dashGap(): Int = LauncherSettings.getInt(Ui.dashed_border_gap_length)

    @JvmStatic
    fun dashedBorderStrokeWidthDp(): Float = dashedBorderStrokeWidthDp(1f)

    @JvmStatic
    fun dashedBorderStrokeWidthDp(scale: Float): Float {
        var width: Float = try {
            LauncherSettings.get(Ui.dashed_border_stroke_width)!!.toFloat()
        } catch (e: Exception) {
            1.5f
        }
        if (width.isNaN() || width.isInfinite()) {
            width = 1.5f
        }
        return clampStrokeWidth(width * scale)
    }

    @JvmStatic
    fun dashedBorderCornerRadius(): Int = clampRadius(LauncherSettings.getInt(Ui.dashed_border_corner_radius))

    @JvmStatic
    fun moduleCornerRadius(): Int = cornerRadiusWithFallback(Ui.module_corner_radius)

    @JvmStatic
    fun outputCornerRadius(): Int = cornerRadiusWithFallback(Ui.output_corner_radius)

    @JvmStatic
    fun outputTrayMaxHeightDp(): Int {
        val height = LauncherSettings.getInt(Ui.output_tray_max_height)
        if (height <= 0) {
            return 0
        }
        return min(height, 1200)
    }

    @JvmStatic
    fun headerCornerRadius(): Int = cornerRadiusWithFallback(Ui.header_corner_radius)

    @JvmStatic
    fun moduleHeaderTextSize(): Int = clampTextSize(LauncherSettings.getInt(Ui.module_header_text_size))

    @JvmStatic
    fun moduleBodyTextSize(): Int = clampTextSize(LauncherSettings.getInt(Ui.module_body_text_size))

    @JvmStatic
    fun outputHeaderTextSize(): Int = clampTextSize(LauncherSettings.getInt(Ui.output_header_text_size))

    @JvmStatic
    fun outputHeaderMode(): String {
        var value = LauncherSettings.get(Behavior.output_header_mode) ?: return "normal"
        value = value.trim().lowercase(Locale.US)
        if ("arrows" == value || "none" == value) {
            return value
        }
        return "normal"
    }

    private fun clampRadius(radius: Int): Int {
        if (radius < 0) {
            return 0
        }
        return min(radius, 48)
    }

    private fun cornerRadiusWithFallback(setting: Ui): Int {
        if (XMLPrefsManager.wasChanged(setting, false)) {
            return clampRadius(LauncherSettings.getInt(setting))
        }
        return dashedBorderCornerRadius()
    }

    private fun clampStrokeWidth(width: Float): Float {
        if (width < 0.5f) {
            return 0.5f
        }
        return min(width, 8f)
    }

    private fun clampTextSize(size: Int): Int {
        if (size < 8) {
            return 8
        }
        return min(size, 32)
    }
}
