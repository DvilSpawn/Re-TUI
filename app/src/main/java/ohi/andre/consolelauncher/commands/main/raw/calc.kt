package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand
import ohi.andre.consolelauncher.tuils.Tuils

class calc : PermanentSuggestionCommand {
    override fun exec(pack: ExecutePack): String {
        return try {
            Tuils.eval(pack.getString()).toString()
        } catch (e: Exception) {
            e.toString()
        }
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_calc

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        val info = pack as MainPack
        return info.res.getString(helpRes())
    }

    override fun permanentSuggestions(context: android.content.Context): Array<String> = arrayOf("(", ")", "+", "-", "*", "/", "%", "^", "sqrt")
}
