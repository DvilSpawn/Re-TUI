package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;
import android.net.Uri;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.tuils.Tuils;

public class install implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        MainPack info = (MainPack) pack;
        String appName = (String) pack.args[0];

        if (appName == null || appName.isEmpty()) {
            return info.res.getString(helpRes());
        }

        try {
            Uri marketUri = Uri.parse("market://search?q=" + Uri.encode(appName));
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            info.context.startActivity(marketIntent);
        } catch (Exception e) {
            // Fallback to browser search if Play Store app is not installed
            try {
                Uri webUri = Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(appName) + "&c=apps");
                Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                info.context.startActivity(webIntent);
            } catch (Exception e2) {
                return e2.toString();
            }
        }

        return "Searching for \"" + appName + "\" on Play Store...";
    }

    @Override
    public int helpRes() {
        return R.string.help_install;
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        MainPack info = (MainPack) pack;
        return info.res.getString(helpRes());
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return null;
    }

}
