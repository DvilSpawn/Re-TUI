package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand
import ohi.andre.consolelauncher.managers.tasker.TaskerIntegrationManager

class tasker : CommandAbstraction, PermanentSuggestionCommand {
    override fun exec(pack: ExecutePack): String? {
        var input = pack.getString()?.trim().orEmpty()
        if (input.startsWith("-run ", true)) input = input.substring(5).trim()
        else if (input.equals("-run", true)) input = ""
        if (input.length >= 2 && input.first() == '"' && input.last() == '"') {
            input = input.substring(1, input.length - 1)
        }
        val result = TaskerIntegrationManager.runTaskerTask(pack.context, input)
        return result.message.takeUnless { result.success && it.isEmpty() }
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)
    override fun priority(): Int = 3
    override fun helpRes(): Int = R.string.help_tasker
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = pack.context.getString(helpRes())
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(helpRes())
    override fun permanentSuggestions(context: android.content.Context): Array<String> =
        TaskerIntegrationManager.taskerTaskNames(context).toTypedArray()
    override fun permanentSuggestionsExecuteOnClick(): Boolean = true
}
