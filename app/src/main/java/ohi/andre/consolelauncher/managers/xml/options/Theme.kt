package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

private val TRANSPARENT_INVALID: Array<String?> = arrayOf("#ff000000")

enum class Theme(
    private val defaultValue: String,
    private val info: Int,
    private val invalidValues: Array<String?>? = null
) : XMLPrefsSave {
    background_color("#00000000", R.string.setting_theme_background_color_description),
    wallpaper_overlay_color("#00000000", R.string.setting_theme_wallpaper_overlay_color_description),
    settings_wallpaper_overlay_color("#66000000", R.string.setting_theme_settings_wallpaper_overlay_color_description),

    input_text_color("#ff00ff00", R.string.setting_theme_input_text_color_description),
    output_text_color("#ffffffff", R.string.setting_theme_output_text_color_description),
    cursor_color("#ffffff", R.string.setting_theme_cursor_color_description),
    enter_icon_color("#ffffffff", R.string.setting_theme_enter_icon_color_description),
    toolbar_icon_color("#ffffff", R.string.setting_theme_toolbar_icon_color_description),
    toolbar_background_color("#00000000", R.string.setting_theme_toolbar_background_color_description),
    restart_message_text_color("#ffffffff", R.string.setting_theme_restart_message_text_color_description),
    session_info_text_color("#888888", R.string.setting_theme_session_info_text_color_description),

    device_text_color("#ffff9800", R.string.setting_theme_device_text_color_description),
    battery_text_high("#4CAF50", R.string.setting_theme_battery_text_high_description),
    battery_text_medium("#FFEB3B", R.string.setting_theme_battery_text_medium_description),
    battery_text_low("#FF5722", R.string.setting_theme_battery_text_low_description),
    ascii_text_color("#00FF00", R.string.setting_theme_ascii_text_color_description),
    time_text_color("#03A9F4", R.string.setting_theme_time_text_color_description),
    storage_text_color("#9C27B0", R.string.setting_theme_storage_text_color_description),
    ram_text_color("#fff44336", R.string.setting_theme_ram_text_color_description),
    network_info_text_color("#FFCA28", R.string.setting_theme_network_info_text_color_description),
    weather_text_color("#fff44336", R.string.setting_theme_weather_text_color_description),
    unlock_counter_text_color("#ffff9800", R.string.setting_theme_unlock_counter_text_color_description),

    ram_status_background_color("#00000000", R.string.setting_theme_ram_status_background_color_description, TRANSPARENT_INVALID),
    device_status_background_color("#00000000", R.string.setting_theme_device_status_background_color_description, TRANSPARENT_INVALID),
    time_status_background_color("#00000000", R.string.setting_theme_time_status_background_color_description, TRANSPARENT_INVALID),
    battery_status_background_color("#00000000", R.string.setting_theme_battery_status_background_color_description, TRANSPARENT_INVALID),
    storage_status_background_color("#00000000", R.string.setting_theme_storage_status_background_color_description, TRANSPARENT_INVALID),
    network_status_background_color("#00000000", R.string.setting_theme_network_status_background_color_description, TRANSPARENT_INVALID),
    notes_status_background_color("#00000000", R.string.setting_theme_notes_status_background_color_description, TRANSPARENT_INVALID),
    weather_status_background_color("#00000000", R.string.setting_theme_weather_status_background_color_description, TRANSPARENT_INVALID),
    unlock_status_background_color("#00000000", R.string.setting_theme_unlock_status_background_color_description, TRANSPARENT_INVALID),
    ascii_status_background_color("#00000000", R.string.setting_theme_ascii_status_background_color_description, TRANSPARENT_INVALID),
    unified_status_background_color("#00000000", R.string.setting_theme_unified_status_background_color_description, TRANSPARENT_INVALID),

    ram_status_text_shadow_color("#00000000", R.string.setting_theme_ram_status_text_shadow_color_description, TRANSPARENT_INVALID),
    device_status_text_shadow_color("#00000000", R.string.setting_theme_device_status_text_shadow_color_description, TRANSPARENT_INVALID),
    time_status_text_shadow_color("#00000000", R.string.setting_theme_time_status_text_shadow_color_description, TRANSPARENT_INVALID),
    battery_status_text_shadow_color("#00000000", R.string.setting_theme_battery_status_text_shadow_color_description, TRANSPARENT_INVALID),
    storage_status_text_shadow_color("#00000000", R.string.setting_theme_storage_status_text_shadow_color_description, TRANSPARENT_INVALID),
    network_status_text_shadow_color("#00000000", R.string.setting_theme_network_status_text_shadow_color_description, TRANSPARENT_INVALID),
    notes_status_text_shadow_color("#00000000", R.string.setting_theme_notes_status_text_shadow_color_description, TRANSPARENT_INVALID),
    weather_status_text_shadow_color("#00000000", R.string.setting_theme_weather_status_text_shadow_color_description, TRANSPARENT_INVALID),
    unlock_status_text_shadow_color("#00000000", R.string.setting_theme_unlock_status_text_shadow_color_description, TRANSPARENT_INVALID),
    ascii_status_text_shadow_color("#00000000", R.string.setting_theme_ascii_status_text_shadow_color_description, TRANSPARENT_INVALID),

    alias_content_text_color("#1DE9B6", R.string.setting_theme_alias_content_text_color_description),
    app_installed_text_color("#FF7043", R.string.setting_theme_app_installed_text_color_description),
    app_uninstalled_text_color("#FF7043", R.string.setting_theme_app_uninstalled_text_color_description),
    regex_match_background_color("#CDDC39", R.string.setting_theme_regex_match_background_color_description),
    notes_text_color("#8BC34A", R.string.setting_theme_notes_text_color_description),
    locked_notes_text_color("#3D5AFE", R.string.setting_theme_locked_notes_text_color_description),
    link_text_color("#0000EE", R.string.setting_theme_link_text_color_description),
    apps_drawer_text_color("#A5D6A7", R.string.setting_theme_apps_drawer_text_color_description),

    input_background_color("#00000000", R.string.setting_theme_input_background_color_description, TRANSPARENT_INVALID),
    output_background_color("#00000000", R.string.setting_theme_output_background_color_description, TRANSPARENT_INVALID),
    suggestions_background_color("#00000000", R.string.setting_theme_suggestions_background_color_description, TRANSPARENT_INVALID),
    input_text_shadow_color("#00000000", R.string.setting_theme_input_text_shadow_color_description, TRANSPARENT_INVALID),
    output_text_shadow_color("#00000000", R.string.setting_theme_output_text_shadow_color_description, TRANSPARENT_INVALID),
    terminal_border_color("#ffffffff", R.string.setting_theme_terminal_border_color_description),
    module_button_background_color("#00000000", R.string.setting_theme_module_button_background_color_description),
    module_text_color("#ffffffff", R.string.setting_theme_module_text_color_description),
    terminal_window_background_color("#00000000", R.string.setting_theme_terminal_window_background_color_description),
    terminal_header_background_color("#00000000", R.string.setting_theme_terminal_header_background_color_description),
    terminal_header_border_color("#ffffffff", R.string.setting_theme_terminal_header_border_color_description);

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.THEME

    override fun label(): String = name

    override fun type(): String = XMLPrefsSave.COLOR

    override fun defaultValue(): String = defaultValue

    override fun infoRes(): Int = info

    override fun invalidValues(): Array<String?>? = invalidValues

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
