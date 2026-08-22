package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack

class shell : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val info = pack as MainPack
        val input = info.getString()
        if (input.trim().isEmpty()) {
            return info.res.getString(helpRes())
        }

        MainManager.runShellCommand(info, input, true)
        return null
    }

    override fun helpRes(): Int = R.string.help_shell

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        val info = pack as MainPack
        return info.res.getString(helpRes())
    }

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null
}
