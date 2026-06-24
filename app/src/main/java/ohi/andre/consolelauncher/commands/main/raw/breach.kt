package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.BreachDialog
import ohi.andre.consolelauncher.managers.BreachManager
import ohi.andre.consolelauncher.managers.RetuiCreditManager

class breach : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        if (!RetuiCreditManager.isDystopiaEnabled(pack.context)) {
            return RetuiCreditManager.status(pack.context)
        }
        BreachDialog.show(pack.context, BreachManager.Mode.NORMAL)
        return "Opening breach..."
    }

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 2

    override fun helpRes(): Int = 0

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = exec(pack)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = exec(pack)
}
