@file:Suppress("DEPRECATION")

package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack

class hack : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        LocalBroadcastManager.getInstance(pack.context.applicationContext)
            .sendBroadcast(Intent(UIManager.ACTION_HACK))
        return pack.context.getString(R.string.command_hack_injecting_cinematic_nonsense_5b526)
    }

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_hack

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null
}
