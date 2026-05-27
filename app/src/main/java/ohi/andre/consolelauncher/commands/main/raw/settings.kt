package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.tuils.Tuils

class settings : CommandAbstraction {
    override fun exec(pack: ExecutePack): String = openSettings(pack, ThemerActivity.SECTION_HOME)

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_settings

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_settings)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = exec(pack)

    companion object {
        private fun openSettings(pack: ExecutePack, section: String): String {
            val info = pack as MainPack
            val launcher = info.context as? LauncherActivity
            val uiManager = launcher?.uiManager ?: LauncherActivity.instance?.uiManager
            if (uiManager != null) {
                uiManager.openSettingsSurface(section)
                return Tuils.EMPTYSTRING
            }

            val intent = Intent(info.context, ThemerActivity::class.java)
            intent.putExtra(ThemerActivity.EXTRA_SECTION, section)
            info.context.startActivity(intent)
            return Tuils.EMPTYSTRING
        }
    }
}
