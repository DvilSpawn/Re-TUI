package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */
enum class Behavior : XMLPrefsSave {
    double_tap_lock {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_double_tap_lock_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    double_tap_cmd {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_double_tap_cmd_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    random_play {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_random_play_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    launcher_sounds {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_launcher_sounds_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    sound_boot {
        override fun defaultValue(): String? = "true"
        override fun infoRes(): Int {
            return R.string.setting_behavior_sound_boot_description
        }
        override fun type(): String? = XMLPrefsSave.BOOLEAN
    },
    sound_click {
        override fun defaultValue(): String? = "true"
        override fun infoRes(): Int {
            return R.string.setting_behavior_sound_click_description
        }
        override fun type(): String? = XMLPrefsSave.BOOLEAN
    },
    sound_success {
        override fun defaultValue(): String? = "true"
        override fun infoRes(): Int {
            return R.string.setting_behavior_sound_success_description
        }
        override fun type(): String? = XMLPrefsSave.BOOLEAN
    },
    sound_failure {
        override fun defaultValue(): String? = "true"
        override fun infoRes(): Int {
            return R.string.setting_behavior_sound_failure_description
        }
        override fun type(): String? = XMLPrefsSave.BOOLEAN
    },
    sound_notification {
        override fun defaultValue(): String? = "true"
        override fun infoRes(): Int {
            return R.string.setting_behavior_sound_notification_description
        }
        override fun type(): String? = XMLPrefsSave.BOOLEAN
    },
    sound_reminder {
        override fun defaultValue(): String? = "true"
        override fun infoRes(): Int {
            return R.string.setting_behavior_sound_reminder_description
        }
        override fun type(): String? = XMLPrefsSave.BOOLEAN
    },
    sound_timer {
        override fun defaultValue(): String? = "true"
        override fun infoRes(): Int {
            return R.string.setting_behavior_sound_timer_description
        }
        override fun type(): String? = XMLPrefsSave.BOOLEAN
    },
    songs_folder {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_songs_folder_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    songs_from_mediastore {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_songs_from_mediastore_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    tui_notification {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    auto_show_keyboard {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_auto_show_keyboard_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    search_only_mode {
        override fun defaultValue(): String? = "false"

        override fun infoRes(): Int {
            return R.string.setting_behavior_search_only_mode_description
        }

        override fun type(): String? = XMLPrefsSave.BOOLEAN
    },
    auto_scroll {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_auto_scroll_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_alias_content {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_show_alias_content_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_launch_history {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_show_launch_history_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_module_dock {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_show_module_dock_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_tmux_workspace_button {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_show_tmux_workspace_button_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_android_widget_drawer_button {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_show_android_widget_drawer_button_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    enable_cyberdeck_mode {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_enable_cyberdeck_mode_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    enable_crt_filter {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_enable_crt_filter_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    ascii_animation {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_ascii_animation_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    ascii_animation_frame_delay_ms {
        override fun defaultValue(): String? {
            return "750"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_ascii_animation_frame_delay_ms_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    ascii_animation_max_file_kb {
        override fun defaultValue(): String? {
            return "512"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_ascii_animation_max_file_kb_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    clear_after_cmds {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_clear_after_cmds_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    clear_input_after_command {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_clear_input_after_command_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    clear_after_seconds {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_clear_after_seconds_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    max_lines {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_max_lines_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    status_time_format {
        override fun defaultValue(): String? {
            return "d MMM yyyy HH:mm:ss"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_status_time_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    output_time_format {
        override fun defaultValue(): String? {
            return "HH:mm:ss"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_output_time_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    time_format_separator {
        override fun defaultValue(): String? {
            return "@"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_time_format_separator_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    battery_medium {
        override fun defaultValue(): String? {
            return "50"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_battery_medium_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    battery_low {
        override fun defaultValue(): String? {
            return "15"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_battery_low_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    device_format {
        override fun defaultValue(): String? {
            return "%d: %u"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_device_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    ram_format {
        override fun defaultValue(): String? {
            return "Available RAM: %avgb GB of %totgb GB (%av%%)"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_ram_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    battery_format {
        override fun defaultValue(): String? {
            return "%(Charging: /)%v%"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_battery_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    battery_progress_bar {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_battery_progress_bar_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    battery_progress_bar_symbol {
        override fun defaultValue(): String? {
            return "#"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_battery_progress_bar_symbol_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    battery_progress_bar_length {
        override fun defaultValue(): String? {
            return "20"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_battery_progress_bar_length_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    storage_format {
        override fun defaultValue(): String? {
            return "Internal Storage: %iavgb GB / %itotgb GB (%iav%%)"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_storage_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    network_info_format {
        override fun defaultValue(): String? {
            return "%(WiFi - %wn/%[Mobile Data: %d3/No Internet access])"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_network_info_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    input_format {
        override fun defaultValue(): String? {
            return "[%t] %p %i"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_input_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    output_format {
        override fun defaultValue(): String? {
            return "%o"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_output_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    session_info_format {
        override fun defaultValue(): String? {
            return "%u@%d:%p"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_session_info_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    app_launch_format {
        override fun defaultValue(): String? {
            return "--> %a"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_app_launch_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    alias_param_marker {
        override fun defaultValue(): String? {
            return "%"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_alias_param_marker_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    alias_param_separator {
        override fun defaultValue(): String? {
            return ","
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_alias_param_separator_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    alias_replace_all_markers {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_alias_replace_all_markers_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    multiple_cmd_separator {
        override fun defaultValue(): String? {
            return ";"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_multiple_cmd_separator_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    toggle_output_state {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_toggle_output_state_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    output_tray_mode {
        override fun defaultValue(): String? {
            return "native"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_output_tray_mode_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    output_header_mode {
        override fun defaultValue(): String? {
            return "normal"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_output_header_mode_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    auto_hide_output {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_auto_hide_output_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    output_auto_hide_seconds {
        override fun defaultValue(): String? {
            return "10"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_output_auto_hide_seconds_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    alias_content_format {
        override fun defaultValue(): String? {
            return "%a --> [%v]"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_alias_content_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    home_path {
        override fun defaultValue(): String? {
            return Tuils.getFolder().getAbsolutePath()
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_home_path_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    app_installed_format {
        override fun defaultValue(): String? {
            return "App installed: %p"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_app_installed_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    app_updated_format {
        override fun defaultValue(): String? {
            return "App updated: %p"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_app_updated_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    app_uninstalled_format {
        override fun defaultValue(): String? {
            return "App uninstalled: %p"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_app_uninstalled_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    enable_music {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_enable_music_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    max_optional_depth {
        override fun defaultValue(): String? {
            return "2"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_max_optional_depth_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    tui_notification_title {
        override fun defaultValue(): String? {
            return "Re:T-UI"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_title_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    tui_notification_subtitle {
        override fun defaultValue(): String? {
            return "Re:T-UI is running"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_subtitle_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    tui_notification_click_cmd {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_click_cmd_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    tui_notification_click_showhome {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_click_showhome_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    tui_notification_lastcmds_size {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_lastcmds_size_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    tui_notification_lastcmds_updown {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_lastcmds_updown_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    tui_notification_priority {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_priority_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    long_click_vibration_duration {
        override fun defaultValue(): String? {
            return "100"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_long_click_vibration_duration_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    long_click_duration {
        override fun defaultValue(): String? {
            return "700"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_long_click_duration_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    click_commands {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_click_commands_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    long_click_commands {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_long_click_commands_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    append_quote_before_file {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_append_quote_before_file_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    optional_values_separator {
        override fun defaultValue(): String? {
            return "/"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_optional_values_separator_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    notes_sorting {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_notes_sorting_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    notes_allow_link {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_notes_allow_link_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    orientation {
        override fun defaultValue(): String? {
            return "2"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_orientation_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    duo_mode {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_duo_mode_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    duo_swap_top_panes {
        override fun defaultValue(): String = "false"
        override fun infoRes(): Int = R.string.setting_behavior_duo_swap_top_panes_description
        override fun type(): String = XMLPrefsSave.BOOLEAN
    },
    htmlextractor_default_format {
        override fun defaultValue(): String? {
            return "%t -> %v%n%a(%an = %av)(%n)"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_htmlextractor_default_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    tui_notification_time_text_color {
        override fun defaultValue(): String? {
            return Theme.time_text_color.defaultValue()
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_time_text_color_description
        }

        override fun type(): String? {
            return XMLPrefsSave.COLOR
        }
    },
    tui_notification_input_text_color {
        override fun defaultValue(): String? {
            return Theme.input_text_color.defaultValue()
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_tui_notification_input_text_color_description
        }

        override fun type(): String? {
            return XMLPrefsSave.COLOR
        }
    },
    weather_key {
        override fun defaultValue(): String? {
            return "1f798f99228596c20ccfda51b9771a86"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_weather_key_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    weather_temperature_measure {
        override fun defaultValue(): String? {
            return "metric"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_weather_temperature_measure_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    weather_location {
        override fun defaultValue(): String? {
            return "null"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_weather_location_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    weather_format {
        override fun defaultValue(): String? {
            return "Weather: %main, Temp: %temp"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_weather_format_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    clear_on_lock {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_clear_on_lock_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    unlock_counter_format {
        override fun defaultValue(): String? {
            return "Unlocked %c times (%a10/)%n%t(Unlock n. %i --> %w)3"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_unlock_counter_format_description
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

        override fun infoRes(): Int {
            return R.string.setting_behavior_unlock_time_divider_description
        }
    },
    unlock_time_order {
        override fun defaultValue(): String? {
            return "1"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_unlock_time_order_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    unlock_counter_cycle_start {
        override fun defaultValue(): String? {
            return "6.00"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_unlock_counter_cycle_start_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    not_available_text {
        override fun defaultValue(): String? {
            return "n/a"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_not_available_text_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    back_button_enabled {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_back_button_enabled_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    swipe_down_notifications {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_swipe_down_notifications_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    weather_update_time {
        override fun defaultValue(): String? {
            return "3600"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_weather_update_time_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    location_update_mintime {
        override fun defaultValue(): String? {
            return "20"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_location_update_mintime_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    location_update_mindistance {
        override fun defaultValue(): String? {
            return "500"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_location_update_mindistance_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    show_weather_updates {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_show_weather_updates_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    swipe_up_apps_drawer {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_swipe_up_apps_drawer_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_music_widget {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_show_music_widget_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    auto_show_music_widget {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_auto_show_music_widget_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    preferred_music_app {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_preferred_music_app_description
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }
    },
    pomodoro_focus_minutes {
        override fun defaultValue(): String? {
            return "25"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_pomodoro_focus_minutes_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    pomodoro_relax_minutes {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_pomodoro_relax_minutes_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    shell_requires_prefix {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_shell_requires_prefix_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    events_lookahead_days {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun infoRes(): Int {
            return R.string.setting_behavior_events_lookahead_days_description
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
