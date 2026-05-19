package ohi.andre.consolelauncher.commands.main.raw;

import android.app.Activity;
import android.content.Intent;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.tuixt.NotesEditorActivity;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Created by francescoandreuzzi on 12/02/2018.
 */

public class notes implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        Intent intent = new Intent(pack.context, NotesEditorActivity.class);
        if(pack.context instanceof Activity) {
            ((Activity) pack.context).startActivityForResult(intent, LauncherActivity.TUIXT_REQUEST);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            pack.context.startActivity(intent);
        }
        return Tuils.EMPTYSTRING;
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
        return R.string.help_notes;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return pack.context.getString(R.string.help_notes);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(R.string.help_notes);
    }
}
