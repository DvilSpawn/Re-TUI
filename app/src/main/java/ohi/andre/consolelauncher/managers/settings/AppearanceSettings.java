package ohi.andre.consolelauncher.managers.settings;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;

public final class AppearanceSettings {

    private AppearanceSettings() {}

    public static boolean autoColorPick() {
        return LauncherSettings.getBoolean(Ui.auto_color_pick);
    }

    public static boolean useSystemFont() {
        return LauncherSettings.getBoolean(Ui.system_font);
    }

    public static String fontFile() {
        return LauncherSettings.get(Ui.font_file);
    }

    public static int musicWidgetBorderColor() {
        return terminalBorderColor();
    }

    public static int musicWidgetTextColor() {
        return moduleNameTextColor();
    }

    public static int notificationWidgetBorderColor() {
        return terminalBorderColor();
    }

    public static int notificationWidgetTextColor() {
        return moduleNameTextColor();
    }

    public static int terminalWindowBackground() {
        return LauncherSettings.getColor(Theme.window_terminal_bg);
    }

    public static int terminalHeaderBackground() {
        int terminalBg = terminalWindowBackground();
        if (Color.alpha(terminalBg) > 0) {
            return ColorUtils.setAlphaComponent(terminalBg, 255);
        }

        int baseBg = LauncherSettings.getColor(Theme.bg_color);
        if (Color.alpha(baseBg) > 0) {
            return ColorUtils.setAlphaComponent(baseBg, 255);
        }

        return Color.BLACK;
    }

    public static boolean dashedBorders() {
        return LauncherSettings.getBoolean(Ui.enable_dashed_border);
    }

    public static int dashedBorderColor() {
        return LauncherSettings.getColor(Theme.dashed_border_color);
    }

    public static int terminalBorderColor() {
        return dashedBorderColor();
    }

    public static int moduleButtonBackgroundColor() {
        return LauncherSettings.getColor(Theme.module_button_bg_color);
    }

    public static int moduleNameTextColor() {
        return LauncherSettings.getColor(Theme.module_name_text_color);
    }

    public static int moduleButtonBorderColor() {
        return terminalBorderColor();
    }

    public static int dashLength() {
        return LauncherSettings.getInt(Ui.dashed_border_dash_length);
    }

    public static int dashGap() {
        return LauncherSettings.getInt(Ui.dashed_border_gap_length);
    }

    public static float dashedBorderStrokeWidthDp() {
        return dashedBorderStrokeWidthDp(1f);
    }

    public static float dashedBorderStrokeWidthDp(float scale) {
        float width;
        try {
            width = Float.parseFloat(LauncherSettings.get(Ui.dashed_border_stroke_width));
        } catch (Exception e) {
            width = 1.5f;
        }
        if (Float.isNaN(width) || Float.isInfinite(width)) {
            width = 1.5f;
        }
        return clampStrokeWidth(width * scale);
    }

    public static int dashedBorderCornerRadius() {
        return clampRadius(LauncherSettings.getInt(Ui.dashed_border_corner_radius));
    }

    public static int moduleCornerRadius() {
        return cornerRadiusWithFallback(Ui.module_corner_radius);
    }

    public static int outputCornerRadius() {
        return cornerRadiusWithFallback(Ui.output_corner_radius);
    }

    public static int outputTrayMaxHeightDp() {
        int height = LauncherSettings.getInt(Ui.output_tray_max_height);
        if (height <= 0) return 0;
        return Math.min(height, 1200);
    }

    public static int headerCornerRadius() {
        return cornerRadiusWithFallback(Ui.header_corner_radius);
    }

    public static int moduleHeaderTextSize() {
        return clampTextSize(LauncherSettings.getInt(Ui.module_header_text_size));
    }

    public static int outputHeaderTextSize() {
        return clampTextSize(LauncherSettings.getInt(Ui.output_header_text_size));
    }

    public static String outputHeaderMode() {
        String value = LauncherSettings.get(Behavior.output_header_mode);
        if (value == null) return "normal";
        value = value.trim().toLowerCase(java.util.Locale.US);
        if ("arrows".equals(value) || "none".equals(value)) {
            return value;
        }
        return "normal";
    }

    private static int clampRadius(int radius) {
        if (radius < 0) return 0;
        return Math.min(radius, 48);
    }

    private static int cornerRadiusWithFallback(Ui setting) {
        if (XMLPrefsManager.wasChanged(setting, false)) {
            return clampRadius(LauncherSettings.getInt(setting));
        }
        return dashedBorderCornerRadius();
    }

    private static float clampStrokeWidth(float width) {
        if (width < 0.5f) return 0.5f;
        return Math.min(width, 8f);
    }

    private static int clampTextSize(int size) {
        if (size < 8) return 8;
        return Math.min(size, 32);
    }
}
