package ohi.andre.consolelauncher.managers.xml.options;

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */

public enum Theme implements XMLPrefsSave {

    input_color {
        @Override
        public String defaultValue() {
            return "#ff00ff00";
        }

        @Override
        public String info() {
            return "Input color";
        }
    },
    output_color {
        @Override
        public String defaultValue() {
            return "#ffffffff";
        }

        @Override
        public String info() {
            return "Output color";
        }
    },
    bg_color {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "Background color";
        }
    },
    device_color {
        @Override
        public String defaultValue() {
            return "#ffff9800";
        }

        @Override
        public String info() {
            return "Device label color";
        }
    },
    battery_text_high {
        @Override
        public String defaultValue() {
            return "#4CAF50";
        }

        @Override
        public String info() {
            return "Battery text color when the battery level is high";
        }
    },
    battery_text_medium {
        @Override
        public String defaultValue() {
            return "#FFEB3B";
        }

        @Override
        public String info() {
            return "Battery text color when the battery level is medium";
        }
    },
    battery_text_low {
        @Override
        public String defaultValue() {
            return "#FF5722";
        }

        @Override
        public String info() {
            return "Battery text color when the battery level is low";
        }
    },
    ascii_color {
        @Override
        public String defaultValue() {
            return "#00FF00";
        }

        @Override
        public String info() {
            return "Color of the ASCII art (requires show_ascii true in ui.xml)";
        }
    },
    time_color {
        @Override
        public String defaultValue() {
            return "#03A9F4";
        }

        @Override
        public String info() {
            return "Time label color";
        }
    },
    storage_color {
        @Override
        public String defaultValue() {
            return "#9C27B0";
        }

        @Override
        public String info() {
            return "Storage label color";
        }
    },
    ram_color {
        @Override
        public String defaultValue() {
            return "#fff44336";
        }

        @Override
        public String info() {
            return "RAM label color";
        }
    },
    network_info_color {
        @Override
        public String defaultValue() {
            return "#FFCA28";
        }

        @Override
        public String info() {
            return "The color of the network info label";
        }
    },
    toolbar_bg {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "Toolbar background color";
        }
    },
    toolbar_color {
        @Override
        public String defaultValue() {
            return "#ffffff";
        }

        @Override
        public String info() {
            return "Toolbar icons color";
        }
    },
    enter_color {
        @Override
        public String defaultValue() {
            return "#ffffffff";
        }

        @Override
        public String info() {
            return "Enter icon color";
        }
    },
    cursor_color {
        @Override
        public String defaultValue() {
            return "#ffffff";
        }

        @Override
        public String info() {
            return "The color of the cursor";
        }
    },
    overlay_color {
        @Override
        public String defaultValue() {
            return "#80000000";
        }

        @Override
        public String info() {
            return "The overlay that overlaps to the background (only when system_wallpaper is true)";
        }
    },
    alias_content_color {
        @Override
        public String defaultValue() {
            return "#1DE9B6";
        }

        @Override
        public String info() {
            return "Alias content color";
        }
    },
    statusbar_color {
        @Override
        public String defaultValue() {
            return "#000000";
        }

        @Override
        public String info() {
            return "Status Bar color (5.0+)";
        }
    },
    navigationbar_color {
        @Override
        public String defaultValue() {
            return "#000000";
        }

        @Override
        public String info() {
            return "Navigation Bar color (5.0+)";
        }
    },
    app_installed_color {
        @Override
        public String defaultValue() {
            return "#FF7043";
        }

        @Override
        public String info() {
            return "App installed message color";
        }
    },
    app_uninstalled_color {
        @Override
        public String defaultValue() {
            return "#FF7043";
        }

        @Override
        public String info() {
            return "App uninstalled message color";
        }
    },
    hint_color {
        @Override
        public String defaultValue() {
            return "#4CAF50";
        }

        @Override
        public String info() {
            return "Hint color";
        }
    },
    mark_color {
        @Override
        public String defaultValue() {
            return "#CDDC39";
        }

        @Override
        public String info() {
            return "The background color that will be used as marker";
        }
    },
    notes_color {
        @Override
        public String defaultValue() {
            return "#8BC34A";
        }

        @Override
        public String info() {
            return "The default color of your notes";
        }
    },
    notes_locked_color {
        @Override
        public String defaultValue() {
            return "#3D5AFE";
        }

        @Override
        public String info() {
            return "The color of your locked notes";
        }
    },
    link_color {
        @Override
        public String defaultValue() {
            return "#0000EE";
        }

        @Override
        public String info() {
            return "The color of the links";
        }
    },
    restart_message_color {
        @Override
        public String defaultValue() {
            return output_color.defaultValue();
        }

        @Override
        public String info() {
            return "The color of the restart message";
        }
    },
    weather_color {
        @Override
        public String defaultValue() {
            return ram_color.defaultValue();
        }

        @Override
        public String info() {
            return "The color of the weather label";
        }
    },
    unlock_counter_color {
        @Override
        public String defaultValue() {
            return device_color.defaultValue();
        }

        @Override
        public String info() {
            return "The color of the unlock counter";
        }
    },
    session_info_color {
        @Override
        public String defaultValue() {
            return "#888888";
        }

        @Override
        public String info() {
            return "The color of the session info";
        }
    },
    status_lines_bgrectcolor {
        @Override
        public String defaultValue() {
            return "#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000";
        }

        @Override
        public String info() {
            return "The color of the rect behind the nth status line";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000"};
        }
    },
    input_bgrectcolor {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The color of the rect behind the input field";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    output_bgrectcolor {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The color of the rect behind the output field";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    toolbar_bgrectcolor {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The color of the rect behind the toolbar";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    suggestions_bgrectcolor {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The color of the rect behind the suggestions area";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    status_lines_bg {
        @Override
        public String defaultValue() {
            return "#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000";
        }

        @Override
        public String info() {
            return "The bg color of the nth line";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000"};
        }
    },
    input_bg {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The background color of the input field";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    output_bg {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The background color of the output field";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    suggestions_bg {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The background color of the suggestions area";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    status_lines_shadow_color {
        @Override
        public String defaultValue() {
            return "#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000,#00000000";
        }

        @Override
        public String info() {
            return "The outline color of the nth line";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000,#ff000000"};
        }
    },
    input_shadow_color {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The outline color of the input field";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    output_shadow_color {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The outline color of the output field";
        }

        @Override
        public String[] invalidValues() {
            return new String[] {"#ff000000"};
        }
    },
    apps_drawer_color {
        @Override
        public String defaultValue() {
            return "#A5D6A7";
        }

        @Override
        public String info() {
            return "The color of the apps drawer";
        }
    },
    dashed_border_color {
        @Override
        public String defaultValue() {
            return "#ffffffff";
        }

        @Override
        public String info() {
            return "The color of dashed terminal borders";
        }
    },
    module_button_bg_color {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The background color of module buttons";
        }
    },
    module_name_text_color {
        @Override
        public String defaultValue() {
            return "#ffffffff";
        }

        @Override
        public String info() {
            return "The text color of module names";
        }
    },
    module_button_border_color {
        @Override
        public String defaultValue() {
            return "#ffffffff";
        }

        @Override
        public String info() {
            return "The border color of module buttons";
        }
    },
    window_terminal_bg {
        @Override
        public String defaultValue() {
            return "#00000000";
        }

        @Override
        public String info() {
            return "The background color of the terminal windows (music, apps drawer, etc.)";
        }
    }
    ;

    @Override
    public XMLPrefsElement parent() {
        return XMLPrefsManager.XMLPrefsRoot.THEME;
    }

    @Override
    public String label() {
        return name();
    }

    @Override
    public String type() {
        return XMLPrefsSave.COLOR;
    }

    @Override
    public String[] invalidValues() {
        return null;
    }

    @Override
    public String getLowercaseString() {
        return label();
    }

    @Override
    public String getString() {
        return label();
    }

}
