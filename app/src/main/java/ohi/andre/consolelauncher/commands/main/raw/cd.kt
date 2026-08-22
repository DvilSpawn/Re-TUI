@file:Suppress("DEPRECATION")

package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.file.RetuiFilesContract

class cd : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val info = pack as MainPack
        val target = info.getString()?.trim().orEmpty()
        if (target.isEmpty()) return info.res.getString(helpRes())
        return changeDirectory(info, target)
    }

    companion object {
        internal fun changeDirectory(info: MainPack, target: String): String {
            val path = RetuiFilesContract.resolveDirectory(info.context, target)
            if (path == null) {
                return info.res.getString(R.string.output_filenotfound)
            }
            val folder = File(path)
            RetuiFilesContract.rememberPath(info.context, path)
            info.currentDirectory = folder
            MainManager.interactive.addCommand("cd '" + folder.absolutePath.replace("'", "'\\''") + "'")
            LocalBroadcastManager.getInstance(info.context.applicationContext)
                .sendBroadcast(Intent(UIManager.ACTION_UPDATE_HINT))
            return path
        }
    }

    override fun helpRes(): Int = R.string.help_cd

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 5

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = pack.context.getString(helpRes())

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = (pack as MainPack).res.getString(R.string.output_filenotfound)
}
