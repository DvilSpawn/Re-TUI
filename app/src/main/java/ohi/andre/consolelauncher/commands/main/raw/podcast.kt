package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.mediaplayer.MediaPlayerActivity

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
        // The player is its own screen now, so there is no surface left to close or shrink from the terminal.
        if (command?.lowercase() in setOf("exit", "close", "minimize", "min")) return
        val context = pack.context
        context.startActivity(Intent(context, MediaPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MediaPlayerActivity.EXTRA_TAB, MediaPlayerActivity.Tab.PODCASTS.name)
            .putExtra(MediaPlayerActivity.EXTRA_PODCAST_COMMAND, command))
    }
}
