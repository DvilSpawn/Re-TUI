package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.help.OfflineHelpActivity
import ohi.andre.consolelauncher.help.OfflineHelpCatalog
import ohi.andre.consolelauncher.tuils.Tuils

class help : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val info = pack as MainPack
        val query = pack.getString().trim()
        if (query.startsWith("-topic ")) {
            val topic = OfflineHelpCatalog.find(query.removePrefix("-topic ").trim())
                ?: return info.res.getString(R.string.output_commandnotfound)
            OfflineHelpActivity.open(pack.context, topic.id)
            return null
        }
        val command = info.commandGroup.getCommandByName(query)
            ?: return info.res.getString(R.string.output_commandnotfound)
        if (CommandTuils.isHiddenCommandName(command.javaClass.simpleName)) {
            return info.res.getString(R.string.output_commandnotfound)
        }
        return pack.context.getString(R.string.command_help_priority_6d8fc,
            info.cmdPrefs.getPriority(command), Tuils.NEWLINE, ohi.andre.consolelauncher.help.OfflineHelpReference.text(pack.context, command))
    }
    override fun helpRes(): Int = R.string.offline_help_description
    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)
    override fun priority(): Int = 5
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? {
        OfflineHelpActivity.open(pack.context)
        return null
    }
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.output_commandnotfound)
}
