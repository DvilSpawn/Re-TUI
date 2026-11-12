package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.time.TimeManager

class time : CommandAbstraction {
    override fun exec(pack: ExecutePack): String = TimeManager.instance!!.replace("%t${pack.getInt()}").toString()

    override fun priority(): Int = 4

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.INT)

    override fun helpRes(): Int = R.string.help_time

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.invalid_integer)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = TimeManager.instance!!.replace("%t0").toString()
}
