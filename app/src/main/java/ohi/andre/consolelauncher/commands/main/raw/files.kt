package ohi.andre.consolelauncher.commands.main.raw

import android.content.ActivityNotFoundException
import android.content.Intent
import java.io.File
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.tuils.Tuils

class files : CommandAbstraction {
    override fun exec(info: ExecutePack): String? {
        val currentDirectory = (info as? MainPack)?.currentDirectory
        val input = info.args
            ?.takeIf { it.isNotEmpty() }
            ?.let { info.getString().trim() }
        val request = parseRequest(input, currentDirectory)
        request.error?.let { return it }

        val intent = Intent(FM_ACTION)
        intent.setPackage(FM_PACKAGE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        request.action?.let { intent.putExtra("action", it) }
        (request.path ?: currentDirectory?.absolutePath)?.let { intent.putExtra("path", it) }
        request.searchName?.let { intent.putExtra("search_name", it) }
        request.searchType?.let { intent.putExtra("search_type", it) }

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

        internal fun parseRequest(input: String?, currentDirectory: File?): FilesRequest {
            val tokens = Tuils.splitArgs(input).filterNotNull()
            if (tokens.isEmpty()) return FilesRequest()

            return when (tokens[0]) {
                "-search" -> {
                    val name = tokens.getOrNull(1)
                        ?: return FilesRequest(error = "Usage: files -search <name> [type]")
                    FilesRequest(
                        action = "search",
                        searchName = name,
                        searchType = tokens.getOrNull(2)
                    )
                }
                "-open" -> {
                    val path = tokens.getOrNull(1)
                        ?: return FilesRequest(error = "Usage: files -open <directory>")
                    val resolved = if (File(path).isAbsolute) {
                        File(path)
                    } else {
                        currentDirectory?.let { File(it, path) }
                            ?: return FilesRequest(error = "Unable to resolve relative path without a current directory.")
                    }
                    FilesRequest(action = "open", path = resolved.absoluteFile.normalize().path)
                }
                else -> FilesRequest(
                    action = "search",
                    searchName = tokens[0],
                    searchType = tokens.getOrNull(1)
                )
            }
        }
    }

    internal data class FilesRequest(
        val action: String? = null,
        val path: String? = null,
        val searchName: String? = null,
        val searchType: String? = null,
        val error: String? = null
    )
}
