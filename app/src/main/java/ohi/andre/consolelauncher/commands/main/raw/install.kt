package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack

class install : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val info = pack as MainPack
        val appName = pack.args!![0] as String?

        if (appName == null || appName.isEmpty()) {
            return info.res.getString(helpRes())
        }

        search.playstoreSearch(appName, info.context)
        return pack.context.getString(R.string.command_install_install_is_deprecated_use_search_ps_53a0b, appName)
    }

    override fun helpRes(): Int = R.string.help_install

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 0

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        val info = pack as MainPack
        return info.res.getString(helpRes())
    }

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null
}
