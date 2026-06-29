package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.RetuiCreditManager

open class credits : CommandAbstraction {
    override fun exec(pack: ExecutePack): String = RetuiCreditManager.status(pack.context)

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 2

    override fun helpRes(): Int = 0

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = exec(pack)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = exec(pack)
}
