package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;

public class termux implements CommandAbstraction {

    @Override
    public String exec(ExecutePack info) {
        String command = null;
        if (info.args != null && info.args.length > 0) {
            Object arg = info.get();
            if (arg != null) {
                command = arg.toString();
            }
        }

        openConsole(info, command);
        return null;
    }

    private void openConsole(ExecutePack info, String command) {
        Intent intent = new Intent(UIManager.ACTION_TERMUX_CONSOLE);
        intent.putExtra(UIManager.EXTRA_TERMUX_COMMAND, command);
        LocalBroadcastManager.getInstance(info.context.getApplicationContext()).sendBroadcast(intent);
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_termux;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return info.context.getString(R.string.help_termux);
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        openConsole(info, null);
        return null;
    }
}
