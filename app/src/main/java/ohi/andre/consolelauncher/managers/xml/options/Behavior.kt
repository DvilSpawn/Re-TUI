package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import android.os.Environment

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */
enum class Behavior : XMLPrefsSave {
    double_tap_lock {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, Re:T-UI will lock the screen on double tap"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    double_tap_cmd {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "The command that will run when you touch two times the screen quickly"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    random_play {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, music player will play your tracks in random order"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    songs_folder {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "The folder that contains your music files"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    songs_from_mediastore {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, Re:T-UI will get tracks from the system mediastore"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    tui_notification {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, there will always be a notification in your status bar, telling you that Re:T-UI is running"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    auto_show_keyboard {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, your keyboard will be shown everytime you go back to Re:T-UI"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    auto_scroll {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, the terminal will be automatically scrolled down when the keyboard is open"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_alias_content {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, when you use an alias you'll also be able to know what command has been executed"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_launch_history {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If false, Re:T-UI won't show the apps that you launch"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_module_dock {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If false, Re:T-UI will hide the module dock row while keeping modules available through commands"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_tmux_workspace_button {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, Re:T-UI will show the tmux workspace button in the launcher toolbar"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_android_widget_drawer_button {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, Re:T-UI will show the Android widgets button in the launcher toolbar"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    enable_cyberdeck_mode {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, Re:T-UI uses angular cyberdeck drawables while keeping the active theme colors"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    enable_crt_filter {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, Re:T-UI draws a CRT scanline overlay over the launcher"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    clear_after_cmds {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun info(): String? {
            return "Auto-clear after n commands (if -1, this feature will be disabled)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    clear_after_seconds {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun info(): String? {
            return "Auto-clear after n seconds (if -1, this feature will be disabled)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    max_lines {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun info(): String? {
            return "Set maximum number of lines that will be shown in the terminal (if -1, this feature is be disabled)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    status_time_format {
        override fun defaultValue(): String? {
            return "d MMM yyyy HH:mm:ss"
        }

        override fun info(): String? {
            return "Define the time format for the status lines at the top. You can resize parts with [size=30]HH:mm[/size], and use %n for a new line"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    output_time_format {
        override fun defaultValue(): String? {
            return "HH:mm:ss"
        }

        override fun info(): String? {
            return "Define the time format for the output lines. You can resize parts with [size=30]HH:mm[/size]"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    time_format_separator {
        override fun defaultValue(): String? {
            return "@"
        }

        override fun info(): String? {
            return "This is the separator between your different time formats"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    battery_medium {
        override fun defaultValue(): String? {
            return "50"
        }

        override fun info(): String? {
            return "The percentage below which the battery level will be considered \"medium\""
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    battery_low {
        override fun defaultValue(): String? {
            return "15"
        }

        override fun info(): String? {
            return "The percentage below which the battery level will be considered \"low\""
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    device_format {
        override fun defaultValue(): String? {
            return "%d: %u"
        }

        override fun info(): String? {
            return "Define the device format"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    ram_format {
        override fun defaultValue(): String? {
            return "Available RAM: %avgb GB of %totgb GB (%av%%)"
        }

        override fun info(): String? {
            return "Define the RAM format"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    battery_format {
        override fun defaultValue(): String? {
            return "%(Charging: /)%v%"
        }

        override fun info(): String? {
            return "Define the battery format"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    battery_progress_bar {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, the battery will be shown as a progress bar"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    battery_progress_bar_symbol {
        override fun defaultValue(): String? {
            return "#"
        }

        override fun info(): String? {
            return "The character used to build the battery progress bar"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    battery_progress_bar_length {
        override fun defaultValue(): String? {
            return "20"
        }

        override fun info(): String? {
            return "The length of the battery progress bar"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    storage_format {
        override fun defaultValue(): String? {
            return "Internal Storage: %iavgb GB / %itotgb GB (%iav%%)"
        }

        override fun info(): String? {
            return "Define the storage format"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    network_info_format {
        override fun defaultValue(): String? {
            return "%(WiFi - %wn/%[Mobile Data: %d3/No Internet access])"
        }

        override fun info(): String? {
            return "Define the network format"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    input_format {
        override fun defaultValue(): String? {
            return "[%t] %p %i"
        }

        override fun info(): String? {
            return "Define the input format "
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    output_format {
        override fun defaultValue(): String? {
            return "%o"
        }

        override fun info(): String? {
            return "Define the output format "
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    session_info_format {
        override fun defaultValue(): String? {
            return "%u@%d:%p"
        }

        override fun info(): String? {
            return "Define the session info format"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    app_launch_format {
        override fun defaultValue(): String? {
            return "--> %a"
        }

        override fun info(): String? {
            return "Define app launch format (%l = label, %p = package, %a = activity)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    alias_param_marker {
        override fun defaultValue(): String? {
            return "%"
        }

        override fun info(): String? {
            return "Define the marker that will be replaced with a provided param"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    alias_param_separator {
        override fun defaultValue(): String? {
            return ","
        }

        override fun info(): String? {
            return "Define the separator between a group of params"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    alias_replace_all_markers {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, if you pass a lower number of parameters to an alias, Re:T-UI will use the first one to replace the others"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    multiple_cmd_separator {
        override fun defaultValue(): String? {
            return ";"
        }

        override fun info(): String? {
            return "The separator between two or more commands in a single input"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    toggle_output_state {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "Legacy output tray toggle. Use output_tray_mode=toggled for manual expanded/collapsed control"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    output_tray_mode {
        override fun defaultValue(): String? {
            return "native"
        }

        override fun info(): String? {
            return "Canonical output tray behavior: native, auto, or toggled"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    output_header_mode {
        override fun defaultValue(): String? {
            return "normal"
        }

        override fun info(): String? {
            return "Output header display: normal, arrows, or none. None hides the manual tray toggle."
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    alias_content_format {
        override fun defaultValue(): String? {
            return "%a --> [%v]"
        }

        override fun info(): String? {
            return "Define the format used to show your alias contents "
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    external_storage_path {
        override fun defaultValue(): String? {
            val path = System.getenv("SECONDARY_STORAGE")
            if (path == null) return Tuils.EMPTYSTRING

            val file = File(path)
            if (file != null && file.exists()) return file.getAbsolutePath()

            return Tuils.EMPTYSTRING
        }

        override fun info(): String? {
            return "The path to your external storage (used to evaluate free/total space)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    home_path {
        override fun defaultValue(): String? {
            return Tuils.getFolder().getAbsolutePath()
        }

        override fun info(): String? {
            return "The path to your home directory"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    app_installed_format {
        override fun defaultValue(): String? {
            return "App installed: %p"
        }

        override fun info(): String? {
            return "The format of the \"app installed\" message (%l = label, %p = package)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    app_updated_format {
        override fun defaultValue(): String? {
            return "App updated: %p"
        }

        override fun info(): String? {
            return "The format of the \"app updated\" message (%l = label, %p = package)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    app_uninstalled_format {
        override fun defaultValue(): String? {
            return "App uninstalled: %p"
        }

        override fun info(): String? {
            return "The format of the \"app uninstalled\" message (%l = label, %p = package)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    enable_music {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, you will be able to use Re:T-UI as a music player. Otherwise, the music command will try to communicate with the music player that your using"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    max_optional_depth {
        override fun defaultValue(): String? {
            return "2"
        }

        override fun info(): String? {
            return "A value which is used to tell how deep Re:T-UI can go in a nested optional value"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    tui_notification_title {
        override fun defaultValue(): String? {
            return "Re:T-UI"
        }

        override fun info(): String? {
            return "The title of the Re:T-UI notification"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    tui_notification_subtitle {
        override fun defaultValue(): String? {
            return "Re:T-UI is running"
        }

        override fun info(): String? {
            return "The subtitle of the Re:T-UI notification"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    tui_notification_click_cmd {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "The command ran when the Re:T-UI notification is clicked"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    tui_notification_click_showhome {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If false, the click on the Re:T-UI notification won't bring you to your phone home"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    tui_notification_lastcmds_size {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun info(): String? {
            return "The number of used commands that will appear inside the Re:T-UI notification (<0 will disable the feature)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    tui_notification_lastcmds_updown {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, the last used command will appear on top"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    tui_notification_priority {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun info(): String? {
            return "The priority of the Re:T-UI notification (min: -2, max: 2)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    long_click_vibration_duration {
        override fun defaultValue(): String? {
            return "100"
        }

        override fun info(): String? {
            return "The duration (in milliseconds) of the vibration when you long click a notification or an RSS item (<0 will disable the feature)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    long_click_duration {
        override fun defaultValue(): String? {
            return "700"
        }

        override fun info(): String? {
            return "The minimum duration of the long click on a notification or an RSS item"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    click_commands {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, you will be able to use a command again clicking on it"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    long_click_commands {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, you will be able to put a used command in the input field long-clicking it"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    append_quote_before_file {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, Re:T-UI will automatically append a quote before a file inserted clicking on a suggestion"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    optional_values_separator {
        override fun defaultValue(): String? {
            return "/"
        }

        override fun info(): String? {
            return "The separator between two optional values (doesn\'t affect notification optional values)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    notes_sorting {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun info(): String? {
            return "0 = time up->down; 1 = time down->up; 2 = alphabetical up->down; 3 = alphabetical down->up; 4 = locked before; 5 = unlocked before"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    notes_allow_link {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If false, adding links to notes will be disallowed (may slightly increase performance)"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    orientation {
        override fun defaultValue(): String? {
            return "2"
        }

        override fun info(): String? {
            return "0 = landscape, 1 = portrait, 2 = auto-rotate"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    duo_mode {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, enables the Duo command for manually switching landscape into a single portrait-style pane on the left or right screen"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    htmlextractor_default_format {
        override fun defaultValue(): String? {
            return "%t -> %v%n%a(%an = %av)(%n)"
        }

        override fun info(): String? {
            return "The default format used by htmlextract -use"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    tui_notification_time_text_color {
        override fun defaultValue(): String? {
            return Theme.time_text_color.defaultValue()
        }

        override fun info(): String? {
            return "The time color inside the Re:T-UI notification"
        }

        override fun type(): String? {
            return XMLPrefsSave.COLOR
        }
    },
    tui_notification_input_text_color {
        override fun defaultValue(): String? {
            return Theme.input_text_color.defaultValue()
        }

        override fun info(): String? {
            return "The input color inside the Re:T-UI notification"
        }

        override fun type(): String? {
            return XMLPrefsSave.COLOR
        }
    },
    weather_key {
        override fun defaultValue(): String? {
            return "1f798f99228596c20ccfda51b9771a86"
        }

        override fun info(): String? {
            return "The key of your account on OpenWeatherMap. You can keep the default one, or create your custom key (check the wiki)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    weather_temperature_measure {
        override fun defaultValue(): String? {
            return "metric"
        }

        override fun info(): String? {
            return "metric = Celsius; imperial = Fahrenheit; standard = Kelvin"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    weather_location {
        override fun defaultValue(): String? {
            return "null"
        }

        override fun info(): String? {
            return "The ID of your country (check the wiki) or your coords separated by a comma (lat,lon)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    weather_format {
        override fun defaultValue(): String? {
            return "Weather: %main, Temp: %temp"
        }

        override fun info(): String? {
            return "The format used to show the weather"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    clear_on_lock {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, Re:T-UI will clear the screen when you lock the phone"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    unlock_counter_format {
        override fun defaultValue(): String? {
            return "Unlocked %c times (%a10/)%n%t(Unlock n. %i --> %w)3"
        }

        override fun info(): String? {
            return "The format used to show the unlock counter"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    unlock_time_divider {
        override fun defaultValue(): String? {
            return "%n"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The divider between the last unlock times"
        }
    },
    unlock_time_order {
        override fun defaultValue(): String? {
            return "1"
        }

        override fun info(): String? {
            return "1 = up-down. 2 = down-up"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    unlock_counter_cycle_start {
        override fun defaultValue(): String? {
            return "6.00"
        }

        override fun info(): String? {
            return "The starting hour of the unlock counter cycle (hh.mm)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    not_available_text {
        override fun defaultValue(): String? {
            return "n/a"
        }

        override fun info(): String? {
            return "The text shown when a value is not available"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    back_button_enabled {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, the back button will put the previous command inside the input area"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    swipe_down_notifications {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, swiping down will expand the notification shade"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    weather_update_time {
        override fun defaultValue(): String? {
            return "3600"
        }

        override fun info(): String? {
            return "The weather update time in seconds. This can only be used if you\'re using a custom weather key"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    location_update_mintime {
        override fun defaultValue(): String? {
            return "20"
        }

        override fun info(): String? {
            return "The amount of time between two location updates (in minutes, must be an integer value)"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    location_update_mindistance {
        override fun defaultValue(): String? {
            return "500"
        }

        override fun info(): String? {
            return "The minimum distance (in meters) to get a location update"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    show_weather_updates {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If false, Re:T-UI won't show information about the weather in the output field"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    swipe_up_apps_drawer {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, swiping up will open the apps drawer"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_music_widget {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, the music module will be shown in the context container"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    auto_show_music_widget {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun info(): String? {
            return "If true, Re:T-UI will automatically show the music module when music starts playing"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    preferred_music_app {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "Package name of the preferred external music app. Leave empty for automatic detection."
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    pomodoro_focus_minutes {
        override fun defaultValue(): String? {
            return "25"
        }

        override fun info(): String? {
            return "Focus period length in minutes for Pomodoro sessions"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    pomodoro_relax_minutes {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun info(): String? {
            return "Relax/break period length in minutes for Pomodoro sessions"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    shell_requires_prefix {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, embedded shell commands must be run with shell [command] instead of falling through from unknown input"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    file_backend {
        override fun defaultValue(): String? {
            return "auto"
        }

        override fun info(): String? {
            return "File backend used by Re:T-UI file commands. Values: auto, native, termux, off"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    events_lookahead_days {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun info(): String? {
            return "Number of days after today to include in the launcher Events module. 0 shows only upcoming events today"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    };

    override fun parent(): XMLPrefsElement? {
        return XMLPrefsManager.XMLPrefsRoot.BEHAVIOR
    }

    override fun label(): String? {
        return name
    }

    override fun invalidValues(): Array<String?>? {
        return null
    }

    override fun getLowercaseString(): String? {
        return label()
    }

    override fun getString(): String? {
        return label()
    }
}
