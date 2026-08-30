package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

private val TRANSPARENT_INVALID: Array<String?> = arrayOf("#ff000000")

enum class Theme(
    private val defaultValue: String,
    private val info: String,
    private val invalidValues: Array<String?>? = null
) : XMLPrefsSave {
    background_color("#00000000", "Launcher background color when system wallpaper is disabled"),
    wallpaper_overlay_color("#00000000", "Overlay color applied over the system wallpaper"),
    settings_wallpaper_overlay_color("#66000000", "Overlay color applied over the wallpaper behind Settings"),

    input_text_color("#ff00ff00", "Command input text color"),
    output_text_color("#ffffffff", "Terminal output text color"),
    cursor_color("#ffffff", "Input cursor color"),
    enter_icon_color("#ffffffff", "Enter icon color"),
    toolbar_icon_color("#ffffff", "Toolbar icon color"),
    toolbar_background_color("#00000000", "Toolbar background color"),
    restart_message_text_color("#ffffffff", "Restart message text color"),
    session_info_text_color("#888888", "Session info text color"),

    device_text_color("#ffff9800", "Device label text color"),
    battery_text_high("#4CAF50", "Battery text color when the battery level is high"),
    battery_text_medium("#FFEB3B", "Battery text color when the battery level is medium"),
    battery_text_low("#FF5722", "Battery text color when the battery level is low"),
    ascii_text_color("#00FF00", "ASCII art text color"),
    time_text_color("#03A9F4", "Time label text color"),
    storage_text_color("#9C27B0", "Storage label text color"),
    ram_text_color("#fff44336", "RAM label text color"),
    network_info_text_color("#FFCA28", "Network info label text color"),
    weather_text_color("#fff44336", "Weather label text color"),
    unlock_counter_text_color("#ffff9800", "Unlock counter text color"),

    ram_status_background_color("#00000000", "RAM status line background color", TRANSPARENT_INVALID),
    device_status_background_color("#00000000", "Device status line background color", TRANSPARENT_INVALID),
    time_status_background_color("#00000000", "Time status line background color", TRANSPARENT_INVALID),
    battery_status_background_color("#00000000", "Battery status line background color", TRANSPARENT_INVALID),
    storage_status_background_color("#00000000", "Storage status line background color", TRANSPARENT_INVALID),
    network_status_background_color("#00000000", "Network status line background color", TRANSPARENT_INVALID),
    notes_status_background_color("#00000000", "Notes status line background color", TRANSPARENT_INVALID),
    weather_status_background_color("#00000000", "Weather status line background color", TRANSPARENT_INVALID),
    unlock_status_background_color("#00000000", "Unlock counter status line background color", TRANSPARENT_INVALID),
    ascii_status_background_color("#00000000", "ASCII status line background color", TRANSPARENT_INVALID),
    unified_status_background_color("#00000000", "Unified status pane background color", TRANSPARENT_INVALID),

    ram_status_text_shadow_color("#00000000", "RAM status line text shadow color", TRANSPARENT_INVALID),
    device_status_text_shadow_color("#00000000", "Device status line text shadow color", TRANSPARENT_INVALID),
    time_status_text_shadow_color("#00000000", "Time status line text shadow color", TRANSPARENT_INVALID),
    battery_status_text_shadow_color("#00000000", "Battery status line text shadow color", TRANSPARENT_INVALID),
    storage_status_text_shadow_color("#00000000", "Storage status line text shadow color", TRANSPARENT_INVALID),
    network_status_text_shadow_color("#00000000", "Network status line text shadow color", TRANSPARENT_INVALID),
    notes_status_text_shadow_color("#00000000", "Notes status line text shadow color", TRANSPARENT_INVALID),
    weather_status_text_shadow_color("#00000000", "Weather status line text shadow color", TRANSPARENT_INVALID),
    unlock_status_text_shadow_color("#00000000", "Unlock counter status line text shadow color", TRANSPARENT_INVALID),
    ascii_status_text_shadow_color("#00000000", "ASCII status line text shadow color", TRANSPARENT_INVALID),

    alias_content_text_color("#1DE9B6", "Alias content text color"),
    app_installed_text_color("#FF7043", "App installed message text color"),
    app_uninstalled_text_color("#FF7043", "App uninstalled message text color"),
    regex_match_background_color("#CDDC39", "Regex match marker background color"),
    notes_text_color("#8BC34A", "Notes text color"),
    locked_notes_text_color("#3D5AFE", "Locked notes text color"),
    link_text_color("#0000EE", "Link text color"),
    apps_drawer_text_color("#A5D6A7", "Apps drawer text and accent color"),

    input_background_color("#00000000", "Input field background color", TRANSPARENT_INVALID),
    output_background_color("#00000000", "Output field background color", TRANSPARENT_INVALID),
    suggestions_background_color("#00000000", "Suggestions area background color", TRANSPARENT_INVALID),
    input_text_shadow_color("#00000000", "Input text shadow color", TRANSPARENT_INVALID),
    output_text_shadow_color("#00000000", "Output text shadow color", TRANSPARENT_INVALID),
    terminal_border_color("#ffffffff", "Shared border color for terminal chrome, headers, panels, and module outlines"),
    module_button_background_color("#00000000", "Module button background color"),
    module_text_color("#ffffffff", "Module label and control text color"),
    terminal_window_background_color("#00000000", "Terminal window background color for modules, drawers, and panels"),
    terminal_header_background_color("#00000000", "Terminal header tab background color"),
    terminal_header_border_color("#ffffffff", "Terminal header tab border color");

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.THEME

    override fun label(): String = name

    override fun type(): String = XMLPrefsSave.COLOR

    override fun defaultValue(): String = defaultValue

    override fun info(): String = info

    override fun invalidValues(): Array<String?>? = invalidValues

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
