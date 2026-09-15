package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import android.os.Build
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */
enum class Ui : XMLPrefsSave {
    show_enter_button {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_enter_button_description
        }
    },
    system_font {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_system_font_description
        }
    },
    ram_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_ram_size_description
        }
    },
    battery_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_battery_size_description
        }
    },
    device_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_device_size_description
        }
    },
    time_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_time_size_description
        }
    },
    storage_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_storage_size_description
        }
    },
    network_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_network_size_description
        }
    },
    notes_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_notes_size_description
        }
    },
    input_output_size {
        override fun defaultValue(): String? {
            return "15"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_input_output_size_description
        }
    },

    show_ram {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_ram_description
        }
    },
    show_device_name {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_device_name_description
        }
    },
    show_battery {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_battery_description
        }
    },
    show_network_info {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_network_info_description
        }
    },
    show_storage_info {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_storage_info_description
        }
    },
    show_notes {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_notes_description
        }
    },
    enable_battery_status {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_enable_battery_status_description
        }
    },
    show_time {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_time_description
        }
    },
    username {
        override fun defaultValue(): String? {
            return "user"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_username_description
        }
    },
    deviceName {
        override fun defaultValue(): String? {
            return Build.DEVICE
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_devicename_description
        }
    },
    system_wallpaper {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_system_wallpaper_description
        }
    },
    auto_color_pick {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_auto_color_pick_description
        }
    },
    enable_crt_vignette {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_enable_crt_vignette_description
        }
    },
    font_file {
        override fun defaultValue(): String? {
            return ""
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_font_file_description
        }
    },
    font_size_offset {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_font_size_offset_description
        }
    },
    fullscreen {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_fullscreen_description
        }
    },
    device_index {
        override fun defaultValue(): String? {
            return "9"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_device_index_description
        }
    },
    ram_index {
        override fun defaultValue(): String? {
            return "1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_ram_index_description
        }
    },
    battery_index {
        override fun defaultValue(): String? {
            return "2"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_battery_index_description
        }
    },
    time_index {
        override fun defaultValue(): String? {
            return "3"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_time_index_description
        }
    },
    show_ascii {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_ascii_description
        }
    },
    show_ascii_landscape {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_ascii_landscape_description
        }
    },
    ascii_index {
        override fun defaultValue(): String? {
            return "10"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_ascii_index_description
        }
    },
    ascii_size {
        override fun defaultValue(): String? {
            return "12"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_ascii_size_description
        }
    },
    ascii_max_lines {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_ascii_max_lines_description
        }
    },
    ascii_pane_height_rows {
        override fun defaultValue(): String? {
            return "10"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_ascii_pane_height_rows_description
        }
    },
    storage_index {
        override fun defaultValue(): String? {
            return "4"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_storage_index_description
        }
    },
    network_index {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_network_index_description
        }
    },
    notes_index {
        override fun defaultValue(): String? {
            return "6"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_notes_index_description
        }
    },
    ram_status_alignment {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_ram_status_alignment_description
        }
    },
    device_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_device_status_alignment_description
        }
    },
    time_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_time_status_alignment_description
        }
    },
    battery_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_battery_status_alignment_description
        }
    },
    storage_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_storage_status_alignment_description
        }
    },
    network_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_network_status_alignment_description
        }
    },
    notes_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_notes_status_alignment_description
        }
    },
    weather_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_weather_status_alignment_description
        }
    },
    unlock_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_unlock_status_alignment_description
        }
    },
    ascii_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_ascii_status_alignment_description
        }
    },
    input_prefix {
        override fun defaultValue(): String? {
            return "$"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_input_prefix_description
        }
    },
    input_root_prefix {
        override fun defaultValue(): String? {
            return "#"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_input_root_prefix_description
        }
    },
    display_margin_top_section {
        override fun defaultValue(): String? {
            return "0,0,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_display_margin_top_section_description
        }
    },
    display_margin_bottom_section {
        override fun defaultValue(): String? {
            return "0,0,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_display_margin_bottom_section_description
        }
    },
    display_margin_landscape_mm {
        override fun defaultValue(): String? {
            return "0,0,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_display_margin_landscape_mm_description
        }
    },
    landscape_fold_gutter_mm {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_landscape_fold_gutter_mm_description
        }
    },
    split_duo_launcher {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_split_duo_launcher_description
        }
    },
    show_app_installed {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_app_installed_description
        }
    },
    show_app_uninstalled {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_app_uninstalled_description
        }
    },
    show_session_info {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_session_info_description
        }
    },
    notes_header {
        override fun defaultValue(): String? {
            return "%( --- Notes : %c ---%n/No notes)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_notes_header_description
        }
    },
    notes_footer {
        override fun defaultValue(): String? {
            return "%(%n --- ----- ---/)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_notes_footer_description
        }
    },
    notes_divider {
        override fun defaultValue(): String? {
            return "%n"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_notes_divider_description
        }
    },
    show_restart_message {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_restart_message_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    notes_max_lines {
        override fun defaultValue(): String? {
            return "12"
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_notes_max_lines_description
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    show_scroll_notes_message {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_scroll_notes_message_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_weather {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_weather_description
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    weather_index {
        override fun defaultValue(): String? {
            return "7"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_weather_index_description
        }
    },
    weather_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_weather_size_description
        }
    },
    show_unlock_counter {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_show_unlock_counter_description
        }
    },
    unlock_index {
        override fun defaultValue(): String? {
            return "8"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_unlock_index_description
        }
    },
    unlock_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_unlock_size_description
        }
    },
    statusbar_light_icons {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_statusbar_light_icons_description
        }
    },
    shadow_params {
        override fun defaultValue(): String? {
            return "2,2,0.2"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_shadow_params_description
        }
    },
    text_redraw_times {
        override fun defaultValue(): String? {
            return "1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_text_redraw_times_description
        }
    },
    status_lines_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_status_lines_margins_description
        }
    },
    output_field_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_output_field_margins_description
        }
    },
    output_tray_max_height {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_output_tray_max_height_description
        }
    },
    input_field_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_input_field_margins_description
        }
    },
    input_area_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_input_area_margins_description
        }
    },
    toolbar_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_toolbar_margins_description
        }
    },
    android_widget_grid_columns {
        override fun defaultValue(): String? {
            return "4"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_android_widget_grid_columns_description
        }
    },
    android_widget_min_columns {
        override fun defaultValue(): String? {
            return "1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_android_widget_min_columns_description
        }
    },
    android_widget_min_rows {
        override fun defaultValue(): String? {
            return "1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_android_widget_min_rows_description
        }
    },
    suggestions_area_margin {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_suggestions_area_margin_description
        }
    },
    module_dock_spacing_dp {
        override fun defaultValue(): String? {
            return "8"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_module_dock_spacing_dp_description
        }
    },
    enable_dashed_border {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_enable_dashed_border_description
        }
    },
    dashed_border_dash_length {
        override fun defaultValue(): String? {
            return "12"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_dashed_border_dash_length_description
        }
    },
    dashed_border_gap_length {
        override fun defaultValue(): String? {
            return "4"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_dashed_border_gap_length_description
        }
    },
    dashed_border_stroke_width {
        override fun defaultValue(): String? {
            return "1.5"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_dashed_border_stroke_width_description
        }
    },
    dashed_border_corner_radius {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_dashed_border_corner_radius_description
        }
    },
    module_corner_radius {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_module_corner_radius_description
        }
    },
    output_corner_radius {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_output_corner_radius_description
        }
    },
    header_corner_radius {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_header_corner_radius_description
        }
    },
    module_header_text_size {
        override fun defaultValue(): String? {
            return "14"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_module_header_text_size_description
        }
    },
    module_body_text_size {
        override fun defaultValue(): String? {
            return "14"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_module_body_text_size_description
        }
    },
    output_header_text_size {
        override fun defaultValue(): String? {
            return "14"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_ui_output_header_text_size_description
        }
    };

    override fun parent(): XMLPrefsElement? {
        return XMLPrefsManager.XMLPrefsRoot.UI
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
