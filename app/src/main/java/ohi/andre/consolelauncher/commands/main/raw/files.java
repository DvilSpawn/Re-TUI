package ohi.andre.consolelauncher.commands.main.raw;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

public class files implements CommandAbstraction {

    private static final String FM_PACKAGE = "com.dvil.retui.fm";
    private static final String FM_ACTION = "com.dvil.retui.fm.OPEN_CONSOLE";

    @Override
    public String exec(ExecutePack info) {
        String command = null;
        if (info.args != null && info.args.length > 0) {
            Object arg = info.get();
            if (arg != null) {
                command = arg.toString();
            }
        }

        Intent intent = new Intent(FM_ACTION);
        intent.setPackage(FM_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (info instanceof MainPack && ((MainPack) info).currentDirectory != null) {
            intent.putExtra("path", ((MainPack) info).currentDirectory.getAbsolutePath());
        }
        if (command != null && command.trim().length() > 0) {
            intent.putExtra("command", command);
        }

        int terminalSurfaceColor = terminalSurfaceColor();
        int terminalHeaderColor = AppearanceSettings.terminalHeaderBackground();
        int terminalBorderColor = AppearanceSettings.terminalBorderColor();

        intent.putExtra("theme_bg", XMLPrefsManager.getColor(Theme.bg_color));
        intent.putExtra("theme_text", XMLPrefsManager.getColor(Theme.output_color));
        intent.putExtra("theme_border", terminalBorderColor);
        intent.putExtra("terminal_bg", terminalSurfaceColor);
        intent.putExtra("module_bg_color", terminalSurfaceColor);
        intent.putExtra("module_text_color", AppearanceSettings.moduleNameTextColor());
        intent.putExtra("module_border_color", terminalBorderColor);
        intent.putExtra("module_header_bg_color", terminalHeaderColor);
        intent.putExtra("module_header_text_color", AppearanceSettings.moduleNameTextColor());
        intent.putExtra("module_button_bg_color", AppearanceSettings.moduleButtonBackgroundColor());
        intent.putExtra("module_button_text_color", AppearanceSettings.moduleNameTextColor());
        intent.putExtra("module_button_border_color", terminalBorderColor);
        intent.putExtra("input_bg_color", XMLPrefsManager.getColor(Theme.input_bg));
        intent.putExtra("input_text_color", XMLPrefsManager.getColor(Theme.input_color));
        intent.putExtra("output_bg_color", XMLPrefsManager.getColor(Theme.output_bg));
        intent.putExtra("output_text_color", XMLPrefsManager.getColor(Theme.output_color));
        intent.putExtra("output_border_color", terminalBorderColor);
        intent.putExtra("top_margin", 18);
        intent.putExtra("input_font_size", XMLPrefsManager.getInt(Ui.input_output_size));
        String topDisplayMargin = XMLPrefsManager.get(Ui.display_margin_top_section);
        intent.putExtra("display_margin_mm", topDisplayMargin);
        intent.putExtra("display_margin_top_section", topDisplayMargin);
        intent.putExtra("display_margin_bottom_section", XMLPrefsManager.get(Ui.display_margin_bottom_section));
        intent.putExtra("module_corner_radius", AppearanceSettings.moduleCornerRadius());
        intent.putExtra("header_corner_radius", AppearanceSettings.headerCornerRadius());
        intent.putExtra("output_corner_radius", AppearanceSettings.outputCornerRadius());
        intent.putExtra("module_header_text_size", AppearanceSettings.moduleHeaderTextSize());
        intent.putExtra("output_header_text_size", AppearanceSettings.outputHeaderTextSize());

        Tuils.getTypeface(info.context);
        if (Tuils.fontPath != null && Tuils.fontPath.startsWith("/")) {
            intent.putExtra("font_path", Tuils.fontPath);
        } else if (AppearanceSettings.useSystemFont()) {
            intent.putExtra("font_name", "system");
        } else {
            intent.putExtra("font_name", "lucida_console");
        }

        try {
            info.context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            return "Re:T-UI Files is not installed.";
        }
        return null;
    }

    private int terminalSurfaceColor() {
        int terminalBg = AppearanceSettings.terminalWindowBackground();
        if (Color.alpha(terminalBg) > 0) {
            return terminalBg;
        }
        int outputBg = XMLPrefsManager.getColor(Theme.output_bg);
        return Color.alpha(outputBg) > 0 ? outputBg : terminalBg;
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_files;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return info.context.getString(R.string.help_files);
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return exec(info);
    }
}
