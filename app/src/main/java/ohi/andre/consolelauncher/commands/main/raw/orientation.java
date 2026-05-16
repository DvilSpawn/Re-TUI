package ohi.andre.consolelauncher.commands.main.raw;

import android.app.Activity;
import android.content.pm.ActivityInfo;

import java.util.Locale;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;

public class orientation implements CommandAbstraction {

    static final String MODE_LANDSCAPE = "landscape";
    static final String MODE_PORTRAIT = "portrait";
    static final String MODE_AUTO = "auto";

    private static final String VALUE_LANDSCAPE = "0";
    private static final String VALUE_PORTRAIT = "1";
    private static final String VALUE_AUTO = "2";

    @Override
    public String exec(ExecutePack pack) {
        String input = pack.getString();
        return apply(pack, input);
    }

    static String apply(ExecutePack pack, String input) {
        String mode = input == null ? "" : input.trim().toLowerCase(Locale.US);
        if (mode.length() == 0 || "status".equals(mode) || "-status".equals(mode)) {
            return currentOrientation() + "\nUsage: orientation portrait|landscape|auto";
        }

        if (MODE_LANDSCAPE.equals(mode) || VALUE_LANDSCAPE.equals(mode)) {
            setOrientation(pack, VALUE_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            return "Landscape mode enabled. Use orientation portrait or orientation auto to change it.";
        }

        if (MODE_PORTRAIT.equals(mode) || VALUE_PORTRAIT.equals(mode)) {
            setOrientation(pack, VALUE_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return "Portrait mode enabled. Use orientation landscape or orientation auto to change it.";
        }

        if (MODE_AUTO.equals(mode)
                || VALUE_AUTO.equals(mode)
                || "autorotate".equals(mode)
                || "auto-rotate".equals(mode)) {
            setOrientation(pack, VALUE_AUTO, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            return "Auto-rotate enabled. Use orientation portrait or orientation landscape to pin it.";
        }

        return "Unknown orientation: " + input + "\nUsage: orientation portrait|landscape|auto";
    }

    private static void setOrientation(ExecutePack pack, String value, int fallbackOrientation) {
        LauncherSettings.set(pack.context, Behavior.orientation, value);

        if (pack.context instanceof LauncherActivity) {
            ((LauncherActivity) pack.context).applyOrientationPreference();
        } else if (pack.context instanceof Activity) {
            ((Activity) pack.context).setRequestedOrientation(fallbackOrientation);
        }
    }

    private static String currentOrientation() {
        int value = LauncherSettings.getInt(Behavior.orientation);
        if (value == 0) {
            return "Orientation: landscape";
        }
        if (value == 1) {
            return "Orientation: portrait";
        }
        if (value == 2) {
            return "Orientation: auto";
        }
        return "Orientation: " + value;
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_orientation;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return info.context.getString(R.string.help_orientation);
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return currentOrientation() + "\nUsage: orientation portrait|landscape|auto";
    }
}
