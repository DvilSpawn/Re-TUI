package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack

class podcast : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val command = pack.args
            ?.takeIf { it.isNotEmpty() }
            ?.let { pack.getString().trim() }
        openPodcast(pack, command)
        return null
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_podcast

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.help_podcast)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? {
        openPodcast(pack, null)
        return null
    }

    private fun openPodcast(pack: ExecutePack, command: String?) {
        val intent = Intent(UIManager.ACTION_PODCAST_SURFACE)
        intent.putExtra(UIManager.EXTRA_PODCAST_COMMAND, command)
        Handler(Looper.getMainLooper()).post {
            LocalBroadcastManager
                .getInstance(pack.context.applicationContext)
                .sendBroadcast(intent)
        }
    }
}
