package ohi.andre.consolelauncher.commands.main.raw;

import java.io.File;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.PresetManager;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

public class debug extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {
        theme {
            @Override
            public String exec(ExecutePack pack) {
                StringBuilder output = new StringBuilder();
                output.append("Theme source").append(Tuils.NEWLINE);
                output.append("auto_color_pick: ").append(LauncherSettings.getBoolean(Ui.auto_color_pick)).append(Tuils.NEWLINE);
                output.append("system_wallpaper: ").append(LauncherSettings.getBoolean(Ui.system_wallpaper)).append(Tuils.NEWLINE);
                output.append("system_font: ").append(LauncherSettings.getBoolean(Ui.system_font)).append(Tuils.NEWLINE);
                output.append("font_file: ").append(LauncherSettings.get(Ui.font_file)).append(Tuils.NEWLINE);
                output.append(Tuils.NEWLINE);
                output.append("Effective colors").append(Tuils.NEWLINE);
                appendValue(output, Theme.bg_color);
                appendValue(output, Theme.overlay_color);
                appendValue(output, Theme.input_color);
                appendValue(output, Theme.output_color);
                appendValue(output, Theme.apps_drawer_color);
                appendValue(output, Theme.music_widget_color);
                appendValue(output, Suggestions.apps_bg_color);
                appendValue(output, Suggestions.alias_bg_color);
                appendValue(output, Suggestions.cmd_bg_color);
                appendValue(output, Suggestions.contact_bg_color);
                return output.toString();
            }
        },
        presets {
            @Override
            public String exec(ExecutePack pack) {
                StringBuilder output = new StringBuilder();
                File dir = PresetManager.getPresetsDir();
                output.append("Preset dir: ").append(dir.getAbsolutePath()).append(Tuils.NEWLINE);
                output.append("Saved + built-in presets").append(Tuils.NEWLINE);
                output.append(Tuils.toPlanString(PresetManager.listAllPresetNames(), Tuils.NEWLINE));
                return output.toString();
            }
        };

        @Override
        public int[] args() {
            return new int[0];
        }

        static Param get(String p) {
            p = p.toLowerCase();
            for (Param param : values()) {
                if (p.endsWith(param.label())) {
                    return param;
                }
            }
            return null;
        }

        static String[] labels() {
            Param[] params = values();
            String[] labels = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                labels[i] = params[i].label();
            }
            return labels;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name();
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_debug);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return pack.context.getString(R.string.help_debug);
        }
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    public String[] params() {
        return Param.labels();
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public int helpRes() {
        return R.string.help_debug;
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return pack.context.getString(R.string.help_debug);
    }

    private static void appendValue(StringBuilder output, ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave value) {
        output.append(value.label())
                .append(": ")
                .append(LauncherSettings.getEffective(value))
                .append(Tuils.NEWLINE);
    }
}
