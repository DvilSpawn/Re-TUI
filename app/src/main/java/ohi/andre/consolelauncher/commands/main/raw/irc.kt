package ohi.andre.consolelauncher.commands.main.raw

import android.os.Handler
import android.os.Looper
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.irc.IrcActivity
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand

/** Native IRC entry, deliberately separate from the preserved HTTP community service. */
class irc : ParamCommand() {
    private val open = object : Param {
        override fun label(): String = ""
        override fun args(): IntArray = intArrayOf()
        override fun exec(pack: ExecutePack): String? {
            Handler(Looper.getMainLooper()).post { IrcActivity.open(pack.context) }; return null
        }
        override fun onNotArgEnough(pack: ExecutePack, n: Int): String = pack.context.getString(helpRes())
        override fun onArgNotFound(pack: ExecutePack, index: Int): String = pack.context.getString(helpRes())
    }
    override fun emptyParam(pack: MainPack): Param = open
    override fun params(): Array<String?> = emptyArray()
    override fun paramForString(pack: MainPack, param: String): Param? = null
    override fun doThings(pack: ExecutePack): String? = null
    override fun helpRes(): Int = R.string.help_irc
    override fun priority(): Int = 4
}
