package ohi.andre.consolelauncher.commands.main.raw

import android.os.Handler
import android.os.Looper
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.calculator.CalculatorActivity
import ohi.andre.consolelauncher.calculator.CalculatorEngine
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand

class calc : PermanentSuggestionCommand {
    override fun exec(pack: ExecutePack): String = CalculatorEngine.evaluate(pack.context, pack.getString()).text

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_calc

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? {
        Handler(Looper.getMainLooper()).post { CalculatorActivity.open(pack.context) }
        return null
    }

    override fun permanentSuggestions(context: android.content.Context): Array<String> =
        if (CalculatorEngine.enabled(context)) arrayOf("(", ")", "+", "-", "*", "/", "^", "sqrt", "to", "solve(", "diff(", "integrate(", "pi")
        else arrayOf("(", ")", "+", "-", "*", "/", "^", "sqrt")
}
