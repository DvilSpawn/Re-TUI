package ohi.andre.consolelauncher.commands.main.raw

import android.content.ActivityNotFoundException
import android.content.Intent
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.RetuiThemeBridge

class files : CommandAbstraction {
    override fun exec(info: ExecutePack): String? {
        val command = info.args
            ?.takeIf { it.isNotEmpty() }
            ?.let { info.getString().trim() }

        val intent = Intent(FM_ACTION)
        intent.setPackage(FM_PACKAGE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (info is MainPack && info.currentDirectory != null) {
            intent.putExtra("path", info.currentDirectory.absolutePath)
        }
        if (command != null && command.trim().isNotEmpty()) {
            intent.putExtra("command", command)
        }

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
    }
}
