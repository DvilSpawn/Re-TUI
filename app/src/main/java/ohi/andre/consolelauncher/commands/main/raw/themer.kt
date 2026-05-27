package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.tuils.Tuils

class themer : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val info = pack as MainPack
        val launcher = info.context as? LauncherActivity
        val uiManager = launcher?.uiManager ?: LauncherActivity.instance?.uiManager
        if (uiManager != null) {
            uiManager.openSettingsSurface(ThemerActivity.SECTION_HOME)
            return Tuils.EMPTYSTRING
        }

        val intent = Intent(info.context, ThemerActivity::class.java)
        info.context.startActivity(intent)
        return Tuils.EMPTYSTRING
    }

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 3

    override fun helpRes(): Int = 0

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null
}
