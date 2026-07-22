package ohi.andre.consolelauncher.commands.main.raw

import android.content.ActivityNotFoundException
import android.content.Intent
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.tuils.Tuils

class files : CommandAbstraction {
    override fun exec(info: ExecutePack): String? {
        val search = info.args
            ?.takeIf { it.isNotEmpty() }
            ?.let { info.getString().trim() }
            ?.let(::parseSearch)

        val intent = Intent(FM_ACTION)
        intent.setPackage(FM_PACKAGE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (info is MainPack && info.currentDirectory != null) {
            intent.putExtra("path", info.currentDirectory.absolutePath)
        }
        search?.name?.let { intent.putExtra("search_name", it) }
        search?.type?.let { intent.putExtra("search_type", it) }

        RetuiThemeBridge.putLauncherThemeExtras(intent, info.context)

        try {
            info.context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            return "Re:T-UI Files is not installed."
        }
        return null
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_files

    override fun onArgNotFound(info: ExecutePack, indexNotFound: Int): String =
        info.context.getString(R.string.help_files)

    override fun onNotArgEnough(info: ExecutePack, nArgs: Int): String? = exec(info)

    companion object {
        private const val FM_PACKAGE = "com.dvil.retui.fm"
        private const val FM_ACTION = "com.dvil.retui.fm.OPEN_CONSOLE"

        internal fun parseSearch(input: String): SearchHandoff? {
            val tokens = Tuils.splitArgs(input).filterNotNull()
            if (tokens.isEmpty()) return null
            return SearchHandoff(tokens[0], tokens.getOrNull(1))
        }
    }

    internal data class SearchHandoff(val name: String, val type: String?)
}
