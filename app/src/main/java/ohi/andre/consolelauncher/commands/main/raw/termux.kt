@file:Suppress("DEPRECATION")

package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import java.util.Locale

/**
 * Termux surface entry: interactive TUIs go to the tmux workspace.
 * One-shot script dispatch (`-run`) still uses the console overlay.
 */
class termux : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        var command: String? = null
        val currentArgs = pack.args
        if (currentArgs != null && currentArgs.isNotEmpty()) {
            val arg = pack.get()
            if (arg != null) {
                command = arg.toString()
            }
        }
        dispatch(pack, command)
        return null
    }

    private fun dispatch(pack: ExecutePack, command: String?) {
        val raw = command?.trim { it <= ' ' }.orEmpty()
        if (raw.isEmpty()) {
            openWorkspace(pack, null)
            return
        }

        val stripped = raw.removePrefix("-").trim { it <= ' ' }
        val lower = stripped.lowercase(Locale.getDefault())
        when {
            lower == "run" || lower.startsWith("run ") -> openConsole(pack, stripped)
            lower == "status" || lower == "setup" || lower == "help" -> openWorkspace(pack, lower)
            lower == "apps" || lower == "app-ls" -> openWorkspace(pack, "help")
            lower == "app" || lower.startsWith("app ") -> {
                val id = stripped.substringAfter(' ', "").trim { it <= ' ' }
                openWorkspace(pack, if (id.isEmpty()) "help" else "launch $id")
            }
            lower.startsWith("app-add") || lower.startsWith("add-app")
                || lower.startsWith("app-rm") || lower.startsWith("rm-app")
                || lower.startsWith("app-info") || lower.startsWith("app-sync")
                || lower.startsWith("app-action") ->
                openWorkspace(pack, "help")
            lower == "open" -> openWorkspace(pack, null)
            else -> openWorkspace(pack, stripped)
        }
    }

    private fun openWorkspace(pack: ExecutePack, command: String?) {
        val intent = Intent(UIManager.ACTION_TMUX_WORKSPACE)
        intent.putExtra(UIManager.EXTRA_TMUX_WORKSPACE_COMMAND, command)
        Handler(Looper.getMainLooper()).post {
            LocalBroadcastManager
                .getInstance(pack.context.applicationContext)
                .sendBroadcast(intent)
        }
    }

    private fun openConsole(pack: ExecutePack, command: String?) {
        val intent = Intent(UIManager.ACTION_TERMUX_CONSOLE)
        intent.putExtra(UIManager.EXTRA_TERMUX_COMMAND, command)
        Handler(Looper.getMainLooper()).post {
            LocalBroadcastManager
                .getInstance(pack.context.applicationContext)
                .sendBroadcast(intent)
        }
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_termux

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.help_termux)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? {
        dispatch(pack, null)
        return null
    }
}
