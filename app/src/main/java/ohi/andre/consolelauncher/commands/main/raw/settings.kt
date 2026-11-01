package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.tuils.Tuils

class settings : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val query = pack.getString().trim()
        if (query.isEmpty()) return openSettings(pack, ThemerActivity.SECTION_HOME)
        // `settings clock format` opens the hub with that search.
        pack.context.startActivity(ThemerActivity.launchIntent(pack.context, ThemerActivity.SECTION_HOME).putExtra(ThemerActivity.EXTRA_QUERY, query))
        return Tuils.EMPTYSTRING
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_settings

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_settings)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = openSettings(pack, ThemerActivity.SECTION_HOME)

    companion object {
        private fun openSettings(pack: ExecutePack, section: String): String {
            val info = pack as MainPack
            val launcher = info.context as? LauncherActivity
            val uiManager = launcher?.uiManager ?: LauncherActivity.instance?.uiManager
            if (uiManager != null) {
                uiManager.openSettingsSurface(section)
                return Tuils.EMPTYSTRING
            }

            info.context.startActivity(ThemerActivity.launchIntent(info.context, section))
            return Tuils.EMPTYSTRING
        }
    }
}
