package ohi.andre.consolelauncher.commands.main.raw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.callback.CallbackAuthManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class retuitoken implements CommandAbstraction {

    @Override
    public String exec(ExecutePack info) {
        String command = "";
        if (info.args != null && info.args.length > 0) {
            Object arg = info.get();
            if (arg != null) {
                command = arg.toString().trim().toLowerCase();
            }
        }

        if (command.isEmpty() || command.equals("-status") || command.equals("status")) {
            return status(info);
        }
        if (command.equals("-show") || command.equals("show")) {
            String token = CallbackAuthManager.getOrCreateToken(info.context);
            CallbackAuthManager.setEnabled(info.context, true);
            copyToken(info.context, token);
            return tokenOutput("Callback auth enabled.", token);
        }
        if (command.equals("-rotate") || command.equals("rotate")) {
            String token = CallbackAuthManager.rotateToken(info.context);
            copyToken(info.context, token);
            return tokenOutput("Callback token rotated.", token);
        }
        if (command.equals("-on") || command.equals("on")) {
            String token = CallbackAuthManager.getOrCreateToken(info.context);
            CallbackAuthManager.setEnabled(info.context, true);
            copyToken(info.context, token);
            return tokenOutput("Callback auth enabled.", token);
        }
        if (command.equals("-off") || command.equals("off")) {
            CallbackAuthManager.setEnabled(info.context, false);
            return "Callback auth disabled.";
        }

        return info.context.getString(R.string.help_retuitoken);
    }

    private String status(ExecutePack info) {
        return "Callback auth: " + (CallbackAuthManager.isEnabled(info.context) ? "enabled" : "disabled")
                + Tuils.NEWLINE
                + "Token present: " + (!CallbackAuthManager.getToken(info.context).isEmpty())
                + Tuils.NEWLINE
                + "Package: " + BuildConfig.APPLICATION_ID
                + Tuils.NEWLINE
                + "Action: " + callbackAction();
    }

    private void copyToken(Context context, String token) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Re-TUI callback token", token));
        }
    }

    private String tokenOutput(String message, String token) {
        return message
                + Tuils.NEWLINE
                + "Token copied to clipboard."
                + Tuils.NEWLINE
                + "Token: " + token
                + Tuils.NEWLINE
                + "Package: " + BuildConfig.APPLICATION_ID
                + Tuils.NEWLINE
                + "Action: " + callbackAction();
    }

    private String callbackAction() {
        return BuildConfig.APPLICATION_ID + ".RETUI_CALLBACK";
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public int helpRes() {
        return R.string.help_retuitoken;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int indexNotFound) {
        return info.context.getString(R.string.help_retuitoken);
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return status(info);
    }
}
