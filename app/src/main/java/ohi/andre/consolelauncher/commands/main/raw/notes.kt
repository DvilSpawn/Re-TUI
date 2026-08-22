package ohi.andre.consolelauncher.commands.main.raw

import android.app.Activity
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.util.Locale
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.NotesEditorActivity
import ohi.andre.consolelauncher.managers.NotesManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.tuils.Tuils

class notes : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val input = pack.getString().trim()
        if (input.isEmpty()) {
            return openEditor(pack)
        }
        val parts = Tuils.splitArgs(input)
        val option = parts[0]?.lowercase(Locale.US) ?: Tuils.EMPTYSTRING
        val optionLength = parts[0]?.length ?: 0
        val rest = if (input.length > optionLength) input.substring(optionLength).trim() else Tuils.EMPTYSTRING

        if (option == "-add") {
            if (rest.isEmpty()) return pack.context.getString(R.string.help_notes)
            send(pack, NotesManager.ACTION_ADD, rest, false)
            refreshNotesModule(pack)
            return null
        }
        if (option == "-rm" || option == "-remove") {
            if (parts.size < 2) return pack.context.getString(R.string.help_notes)
            send(pack, NotesManager.ACTION_RM, parts[1], false)
            refreshNotesModule(pack)
            return null
        }
        if (option == "-ls" || option == "-list") {
            send(pack, NotesManager.ACTION_LS, null, false)
            return null
        }
        if (option == "-clear") {
            send(pack, NotesManager.ACTION_CLEAR, null, false)
            refreshNotesModule(pack)
            return null
        }
        if (option == "-lock") {
            if (parts.size < 3) return pack.context.getString(R.string.help_notes)
            send(pack, NotesManager.ACTION_LOCK, parts[1], parts[2].toBoolean())
            refreshNotesModule(pack)
            return null
        }
        if (option == "-cp" || option == "-copy") {
            if (parts.size < 2) return pack.context.getString(R.string.help_notes)
            send(pack, NotesManager.ACTION_CP, parts[1], false)
            return null
        }
        if (option == "-file") {
            pack.context.startActivity(Tuils.openFile(pack.context, File(Tuils.getFolder(), NotesManager.PATH)))
            return null
        }

        return pack.context.getString(R.string.help_notes)
    }

    private fun openEditor(pack: ExecutePack): String {
        val intent = Intent(pack.context, NotesEditorActivity::class.java)
        if (pack.context is Activity) {
            (pack.context as Activity).startActivityForResult(intent, LauncherActivity.TUIXT_REQUEST)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            pack.context.startActivity(intent)
        }
        return Tuils.EMPTYSTRING
    }

    private fun send(pack: ExecutePack, action: String, text: String?, lock: Boolean) {
        val intent = Intent(action)
        intent.putExtra(NotesManager.BROADCAST_COUNT, NotesManager.broadcastCount)
        if (text != null) {
            intent.putExtra(NotesManager.TEXT, text)
        }
        intent.putExtra(NotesManager.LOCK, lock)
        LocalBroadcastManager.getInstance(pack.context.applicationContext).sendBroadcast(intent)
    }

    private fun refreshNotesModule(pack: ExecutePack) {
        val intent = Intent(UIManager.ACTION_MODULE_COMMAND)
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, "update")
        intent.putExtra(UIManager.EXTRA_MODULE_NAME, ModuleManager.NOTES)
        LocalBroadcastManager.getInstance(pack.context.applicationContext).sendBroadcast(intent)
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_notes

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.help_notes)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = openEditor(pack)
}
