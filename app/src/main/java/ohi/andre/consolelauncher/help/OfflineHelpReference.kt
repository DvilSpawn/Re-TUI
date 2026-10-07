package ohi.andre.consolelauncher.help

import android.content.Context
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandTuils

object OfflineHelpReference {
    fun text(context: Context, command: CommandAbstraction): String = try {
        context.getString(command.helpRes())
    } catch (_: android.content.res.Resources.NotFoundException) {
        context.getString(ohi.andre.consolelauncher.R.string.offline_help_no_reference)
    }

    fun commands(context: Context): List<CommandAbstraction> =
        CommandGroup(context, "ohi.andre.consolelauncher.commands.main.raw").commands
            .filterNot { CommandTuils.isHiddenCommandName(it.javaClass.simpleName) }
            .sortedBy { it.javaClass.simpleName }
}
