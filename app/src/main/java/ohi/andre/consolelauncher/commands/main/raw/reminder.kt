package ohi.andre.consolelauncher.commands.main.raw

import android.app.Activity
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.ReminderActivity
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager
import java.util.Locale

class reminder : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val input = pack.get(Any::class.java, 0)?.toString()?.trim().orEmpty()
        if (input.isEmpty() || input.equals("-open", true)) return open(pack)
        if (input.equals("-ls", true) || input.equals("-list", true)) return ReminderManager.formatList(pack.context)

        val parts = input.split(Regex("\\s+"))
        return when (parts[0].lowercase(Locale.US)) {
            "-add" -> add(pack, parts)
            "-rm", "-remove" -> remove(pack, parts)
            else -> pack.context.getString(R.string.help_reminder)
        }
    }

    private fun remove(pack: ExecutePack, parts: List<String>): String {
        if (parts.size != 2) return pack.context.getString(R.string.help_reminder)
        val item = ReminderManager.get(pack.context, parts[1])
            ?: return "Reminder not found.\n${ReminderManager.formatList(pack.context)}"
        ReminderManager.remove(pack.context, item.id)
        refresh(pack)
        return "Reminder removed:\n${item.title}"
    }

    private fun add(pack: ExecutePack, parts: List<String>): String {
        if (parts.size < 4) return pack.context.getString(R.string.help_reminder)
        val date = parts[parts.lastIndex - 1]
        val time = parts.last()
        val title = parts.subList(1, parts.lastIndex - 1).joinToString(" ").trim()
        val at = ReminderManager.parseCliDateTime(date, time)
        if (title.isEmpty() || at == null) return "Use: reminder -add <task name> <dd/mm/yy> <HH:mm>"
        if (at <= System.currentTimeMillis()) return "Reminder time must be in the future."
        val saved = ReminderManager.add(pack.context, title, at)
        refresh(pack)
        return "Reminder saved:\n${saved.title}\n${ReminderManager.formatWhen(saved.atMillis)}"
    }

    private fun open(pack: ExecutePack): String {
        val intent = Intent(pack.context, ReminderActivity::class.java)
        if (pack.context is Activity) {
            (pack.context as Activity).startActivityForResult(intent, LauncherActivity.TUIXT_REQUEST)
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            pack.context.startActivity(intent)
        }
        return ""
    }

    private fun refresh(pack: ExecutePack) {
        LocalBroadcastManager.getInstance(pack.context.applicationContext).sendBroadcast(
            Intent(UIManager.ACTION_MODULE_COMMAND)
                .putExtra(UIManager.EXTRA_MODULE_COMMAND, "update")
                .putExtra(UIManager.EXTRA_MODULE_NAME, ModuleManager.REMINDER)
        )
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)
    override fun priority(): Int = 4
    override fun helpRes(): Int = R.string.help_reminder
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_reminder)
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = open(pack)
}
