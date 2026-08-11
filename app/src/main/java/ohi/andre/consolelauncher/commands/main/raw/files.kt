package ohi.andre.consolelauncher.commands.main.raw

import android.content.Context
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand
import ohi.andre.consolelauncher.managers.file.RetuiFilesContract
import ohi.andre.consolelauncher.tuils.Tuils

class files : CommandAbstraction, PermanentSuggestionCommand {
    override fun exec(info: ExecutePack): String? {
        val input = info.args
            ?.takeIf { it.isNotEmpty() }
            ?.let { info.getString().trim() }
        val request = parseRequest(input)
        request.error?.let { return it }
        request.changeDirectory?.let { target ->
            return cd.changeDirectory(info as MainPack, target)
        }

        return RetuiFilesContract.launch(
            context = info.context,
            action = request.action,
            target = request.target,
            searchName = request.searchName,
            searchType = request.searchType
        )
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_files

    override fun onArgNotFound(info: ExecutePack, indexNotFound: Int): String =
        info.context.getString(R.string.help_files)

    override fun onNotArgEnough(info: ExecutePack, nArgs: Int): String? = exec(info)

    override fun permanentSuggestions(context: Context): Array<String> =
        SUGGESTIONS.copyOf()

    override fun permanentSuggestionReplacement(suggestion: String): String =
        if (suggestion.startsWith("-")) "files $suggestion" else suggestion

    companion object {
        internal val SUGGESTIONS = arrayOf("-open", "-ls", "-share", "-search", "-cd")
        internal const val FM_PACKAGE = RetuiFilesContract.PACKAGE
        internal const val FM_ACTION = RetuiFilesContract.OPEN_CONSOLE

        internal fun parseRequest(input: String?): FilesRequest {
            val tokens = Tuils.splitArgs(input).filterNotNull()
            if (tokens.isEmpty()) return FilesRequest()

            return when (tokens[0]) {
                "-search" -> {
                    val name = tokens.getOrNull(1)
                        ?: return FilesRequest(error = "Usage: files -search <name> [type]")
                    FilesRequest(
                        action = RetuiFilesContract.ACTION_SEARCH,
                        searchName = name,
                        searchType = tokens.getOrNull(2)
                    )
                }
                "-open" -> {
                    val target = tokens.getOrNull(1)
                        ?: return FilesRequest(error = "Usage: files -open <file>")
                    FilesRequest(
                        action = RetuiFilesContract.ACTION_OPEN,
                        target = target
                    )
                }
                "-ls" -> FilesRequest(action = RetuiFilesContract.ACTION_LIST)
                "-share" -> FilesRequest(
                    action = RetuiFilesContract.ACTION_SHARE,
                    target = tokens.getOrNull(1)
                )
                "-cd" -> FilesRequest(
                    changeDirectory = tokens.getOrNull(1)
                        ?: return FilesRequest(error = "Usage: files -cd <directory>")
                )
                else -> FilesRequest(error = "Unknown files option: ${tokens[0]}")
            }
        }
    }

    internal data class FilesRequest(
        val action: String? = null,
        val target: String? = null,
        val changeDirectory: String? = null,
        val searchName: String? = null,
        val searchType: String? = null,
        val error: String? = null
    )
}
