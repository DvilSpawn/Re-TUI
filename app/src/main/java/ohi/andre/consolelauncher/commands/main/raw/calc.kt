package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand
import ohi.andre.consolelauncher.tuils.Tuils

class calc : PermanentSuggestionCommand {
    override fun exec(pack: ExecutePack): String = try {
        Tuils.eval(pack.getString()).toString()
    } catch (e: Exception) {
        e.toString()
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_calc

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? {
        openCalculator(pack, null)
        return null
    }

    override fun permanentSuggestions(context: android.content.Context): Array<String> = arrayOf("(", ")", "+", "-", "*", "/", "%", "^", "sqrt")

    private fun openCalculator(pack: ExecutePack, expression: String?) {
        val intent = Intent(UIManager.ACTION_CALCULATOR_SURFACE)
            .putExtra(UIManager.EXTRA_CALCULATOR_EXPRESSION, expression)
        Handler(Looper.getMainLooper()).post {
            LocalBroadcastManager.getInstance(pack.context.applicationContext).sendBroadcast(intent)
        }
    }
}
