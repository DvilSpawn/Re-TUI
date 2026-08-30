package ohi.andre.consolelauncher.commands.main.raw

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.Locale
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils

class mode : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val requested = pack.getString()?.trim()?.lowercase(Locale.US).orEmpty()
        if (requested.isEmpty() || requested == "status" || requested == "-status") {
            return status()
        }
        val enabled = when (requested) {
            "search" -> true
            "classic" -> false
            else -> return "Unknown launcher mode: $requested\nUsage: mode search|classic"
        }
        LauncherSettings.set(pack.context, Behavior.search_only_mode, enabled.toString())
        val launcher = pack.context as? LauncherActivity ?: LauncherActivity.instance
        Handler(Looper.getMainLooper()).postDelayed(
            {
                Toast.makeText(
                    pack.context,
                    if (enabled) "Search mode enabled." else "Classic mode enabled.",
                    Toast.LENGTH_SHORT
                ).show()
                launcher?.refreshUiInPlace() ?: LauncherActivity.preview(pack.context.applicationContext)
            },
            200L
        )
        return Tuils.EMPTYSTRING
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_mode

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.help_mode)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = status()

    private fun status(): String =
        "Launcher mode: " + if (LauncherSettings.getBoolean(Behavior.search_only_mode)) "search" else "classic"
}
