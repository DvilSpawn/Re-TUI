package ohi.andre.consolelauncher.managers.settings;

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
        return moduleButtonBorderColor();
    }

    public static int musicWidgetTextColor() {
        return moduleNameTextColor();
    }

    public static int notificationWidgetBorderColor() {
        return moduleButtonBorderColor();
    }

    public static int notificationWidgetTextColor() {
        return moduleNameTextColor();
    }

    public static int terminalWindowBackground() {
        return LauncherSettings.getColor(Theme.window_terminal_bg);
    }

    public static boolean dashedBorders() {
        return LauncherSettings.getBoolean(Ui.enable_dashed_border);
    }

    public static int dashedBorderColor() {
        return LauncherSettings.getColor(Theme.dashed_border_color);
    }

    public static int moduleButtonBackgroundColor() {
        return LauncherSettings.getColor(Theme.module_button_bg_color);
    }

    public static int moduleNameTextColor() {
        return LauncherSettings.getColor(Theme.module_name_text_color);
    }

    public static int moduleButtonBorderColor() {
        return LauncherSettings.getColor(Theme.module_button_border_color);
    }

    public static int dashLength() {
        return LauncherSettings.getInt(Ui.dashed_border_dash_length);
    }

    public static int dashGap() {
        return LauncherSettings.getInt(Ui.dashed_border_gap_length);
    }

    public static int moduleCornerRadius() {
        return clampRadius(LauncherSettings.getInt(Ui.module_corner_radius));
    }

    public static int outputCornerRadius() {
        return clampRadius(LauncherSettings.getInt(Ui.output_corner_radius));
    }

    public static int outputTrayMaxHeightDp() {
        int height = LauncherSettings.getInt(Ui.output_tray_max_height);
        if (height <= 0) return 0;
        return Math.min(height, 1200);
    }

    public static int headerCornerRadius() {
        return clampRadius(LauncherSettings.getInt(Ui.header_corner_radius));
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

    private static int clampTextSize(int size) {
        if (size < 8) return 8;
        return Math.min(size, 32);
    }
}
