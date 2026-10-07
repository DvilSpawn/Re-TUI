package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.help.OfflineHelpActivity

class tutorial : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        OfflineHelpActivity.open(pack.context, "getting-started")
        return null
    }

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.offline_help_tutorial_description

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null
}
