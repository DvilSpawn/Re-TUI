package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.ChangelogManager

class changelog : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        ChangelogManager.open(pack.context)
        return null
    }

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_changelog

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null
}
