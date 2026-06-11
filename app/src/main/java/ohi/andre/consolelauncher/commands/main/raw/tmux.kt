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

class tmux : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        dispatchWorkspaceCommand(pack, pack.getString())
        return null
    }

    private fun dispatchWorkspaceCommand(pack: ExecutePack, command: String?) {
        val intent = Intent(UIManager.ACTION_TMUX_WORKSPACE)
        intent.putExtra(UIManager.EXTRA_TMUX_WORKSPACE_COMMAND, command)
        Handler(Looper.getMainLooper()).post {
            LocalBroadcastManager
                .getInstance(pack.context.applicationContext)
                .sendBroadcast(intent)
        }
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_tmux

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_tmux)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? {
        dispatchWorkspaceCommand(pack, null)
        return null
    }
}
