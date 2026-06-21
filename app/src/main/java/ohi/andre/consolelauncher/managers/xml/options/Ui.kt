package ohi.andre.consolelauncher.managers.xml.options

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

        override fun info(): String? {
            return "Hide/show the enter button"
        }
    },
    system_font {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the default Re:T-UI font (\"Lucida Console\") will be used for all texts"
        }
    },
    ram_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The ram label font size"
        }
    },
    battery_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The battery label font size"
        }
    },
    device_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The device label font size"
        }
    },
    time_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The time label font size"
        }
    },
    storage_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The storage label font size"
        }
    },
    network_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The network label font size"
        }
    },
    notes_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Notes size"
        }
    },
    input_output_size {
        override fun defaultValue(): String? {
            return "15"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The input/output font size"
        }
    },

    show_ram {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the RAM label will be hidden"
        }
    },
    show_device_name {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the device label will be hidden"
        }
    },
    show_battery {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the battery label will be hidden"
        }
    },
    show_network_info {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the network info label will be hidden"
        }
    },
    show_storage_info {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the time label will be hidden"
        }
    },
    show_notes {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the notes label will be hidden"
        }
    },
    enable_battery_status {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, battery text color will change when your battery level reaches different percentages: battery_text_high, battery_text_medium, battery_text_low. If false, only battery_text_high is used"
        }
    },
    show_time {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the time label will be hidden"
        }
    },
    username {
        override fun defaultValue(): String? {
            return "user"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "Your username"
        }
    },
    deviceName {
        override fun defaultValue(): String? {
            return Build.DEVICE
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "Your device name"
        }
    },
    system_wallpaper {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, your system wallpaper will be used as background"
        }
    },
    auto_color_pick {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, Re:T-UI will derive runtime theme colors from your wallpaper and override manual theme colors"
        }
    },
    font_file {
        override fun defaultValue(): String? {
            return ""
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "Selected custom font file name from the fonts folder"
        }
    },
    fullscreen {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, Re:T-UI will run in fullscreen mode"
        }
    },
    device_index {
        override fun defaultValue(): String? {
            return "9"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    ram_index {
        override fun defaultValue(): String? {
            return "1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    battery_index {
        override fun defaultValue(): String? {
            return "2"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    time_index {
        override fun defaultValue(): String? {
            return "3"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    show_ascii {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "Show the ASCII art from ascii.txt. WARNING: wide art may overflow or wrap on small screens."
        }
    },
    show_ascii_landscape {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "Show the ASCII art while the landscape layout is active"
        }
    },
    ascii_index {
        override fun defaultValue(): String? {
            return "9"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Order of the ASCII art in the status area"
        }
    },
    ascii_size {
        override fun defaultValue(): String? {
            return "12"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Legacy ASCII art text size. ASCII now auto-fits to the pane width."
        }
    },
    ascii_max_lines {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Number of source rows shown in the ASCII viewport. Use -1 to auto-zoom wide art."
        }
    },
    storage_index {
        override fun defaultValue(): String? {
            return "4"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    network_index {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    notes_index {
        override fun defaultValue(): String? {
            return "6"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    ram_status_alignment {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "RAM status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    device_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Device status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    time_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Time status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    battery_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Battery status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    storage_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Storage status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    network_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Network status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    notes_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Notes status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    weather_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Weather status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    unlock_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Unlock counter status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    ascii_status_alignment {
        override fun defaultValue(): String? {
            return "-1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "ASCII status line alignment (<0 = left, =0 = center, >0 = right)"
        }
    },
    input_prefix {
        override fun defaultValue(): String? {
            return "$"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The prefix placed before every input"
        }
    },
    input_root_prefix {
        override fun defaultValue(): String? {
            return "#"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The prefix placed before a root command (\"su ...\")"
        }
    },
    display_margin_top_section {
        override fun defaultValue(): String? {
            return "0,0,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "Top launcher section display margin in millimeters. [left margin],[top margin],[right margin],[bottom margin]"
        }
    },
    display_margin_bottom_section {
        override fun defaultValue(): String? {
            return "0,0,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "Bottom terminal/input section display margin in millimeters. [left margin],[top margin],[right margin],[bottom margin]"
        }
    },
    display_margin_landscape_mm {
        override fun defaultValue(): String? {
            return "0,0,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "Landscape display margin in millimeters. [left margin],[top margin],[right margin],[bottom margin]"
        }
    },
    landscape_fold_gutter_mm {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Landscape-only center gutter in millimeters for fold hinges or screen creases"
        }
    },
    split_duo_launcher {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, Duo keeps the status lines and side switch button on the empty screen while output, input, toolbar, and suggestions stay on the active screen"
        }
    },
    show_app_installed {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, you will receive a message when you install an app"
        }
    },
    show_app_uninstalled {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, you will receive a message when you uninstall an app"
        }
    },
    show_session_info {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, when your input field is empty there will be a short line containing some information about the current session"
        }
    },
    notes_header {
        override fun defaultValue(): String? {
            return "%( --- Notes : %c ---%n/No notes)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The header above your notes"
        }
    },
    notes_footer {
        override fun defaultValue(): String? {
            return "%(%n --- ----- ---/)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The footer below your notes"
        }
    },
    notes_divider {
        override fun defaultValue(): String? {
            return "%n"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The divider between two notes"
        }
    },
    show_restart_message {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If false, the restart message won\'t be shown"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    notes_max_lines {
        override fun defaultValue(): String? {
            return "12"
        }

        override fun info(): String? {
            return "The max number of lines of notes (-1 to disable)"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }
    },
    show_scroll_notes_message {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, you will get a message when your notes reach the value set in notes_max_lines"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }
    },
    show_weather {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun info(): String? {
            return "If true, you will see a label containing the weather in your area"
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

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    weather_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Weather size"
        }
    },
    show_unlock_counter {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If false, the unlock counter feature will be disabled"
        }
    },
    unlock_index {
        override fun defaultValue(): String? {
            return "8"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "This is used to order the labels on top of the screen"
        }
    },
    unlock_size {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Unlock size"
        }
    },
    statusbar_light_icons {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, your status bar icons will be white. Dark otherwise"
        }
    },
    shadow_params {
        override fun defaultValue(): String? {
            return "2,2,0.2"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "[Shadow X offset],[Shadow Y offset],[Shadow radius]"
        }
    },
    text_redraw_times {
        override fun defaultValue(): String? {
            return "1"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "A greater value will produce a bigger outline"
        }
    },
    status_lines_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]"
        }
    },
    output_field_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]"
        }
    },
    output_tray_max_height {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Maximum height for the expanded output tray in dp. 0 keeps the default adaptive height"
        }
    },
    input_field_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The dimension of the input field (where cmds are inserted). [horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]"
        }
    },
    input_area_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The dimension of the input area (prefix + input field + toolbar + suggestions). [horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]"
        }
    },
    toolbar_margins {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]"
        }
    },
    android_widget_grid_columns {
        override fun defaultValue(): String? {
            return "4"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Base column count for the Android widget drawer. Rows and landscape columns are computed from square cells"
        }
    },
    android_widget_min_columns {
        override fun defaultValue(): String? {
            return "2"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Minimum widget width in grid columns. Use 1 to allow 1x1 widgets"
        }
    },
    android_widget_min_rows {
        override fun defaultValue(): String? {
            return "2"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Minimum widget height in grid rows. Use 1 to allow 1x1 widgets"
        }
    },
    suggestions_area_margin {
        override fun defaultValue(): String? {
            return "3,3,0,0"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "[horizontal_margin],[vertical_margin],[horizontal_padding],[vertical_padding]"
        }
    },
    enable_dashed_border {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, terminal windows (Music, Apps Drawer, Input/Output) will use an ASCII-style dashed border"
        }
    },
    dashed_border_dash_length {
        override fun defaultValue(): String? {
            return "12"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Length of the dash in the dashed border (dp)"
        }
    },
    dashed_border_gap_length {
        override fun defaultValue(): String? {
            return "4"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Length of the gap in the dashed border (dp)"
        }
    },
    dashed_border_stroke_width {
        override fun defaultValue(): String? {
            return "1.5"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "Stroke width for dashed borders (dp). Decimal values are allowed"
        }
    },
    dashed_border_corner_radius {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Default rounded corner radius for dashed borders (dp). Specific module, output, and header corner radius settings override this when changed"
        }
    },
    module_corner_radius {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Rounded corner radius for module panels and module controls (dp)"
        }
    },
    output_corner_radius {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Rounded corner radius for the output terminal panel (dp)"
        }
    },
    header_corner_radius {
        override fun defaultValue(): String? {
            return "0"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Rounded corner radius for module and output header boxes (dp)"
        }
    },
    module_header_text_size {
        override fun defaultValue(): String? {
            return "14"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Text size for module header labels and close boxes (sp)"
        }
    },
    module_body_text_size {
        override fun defaultValue(): String? {
            return "14"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Text size for built-in module body text and controls (sp)"
        }
    },
    output_header_text_size {
        override fun defaultValue(): String? {
            return "14"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "Text size for output and overlay header labels (sp)"
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
