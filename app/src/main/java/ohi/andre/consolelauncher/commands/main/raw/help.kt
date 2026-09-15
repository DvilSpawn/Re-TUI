package ohi.andre.consolelauncher.commands.main.raw

import java.util.ArrayList
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Arrays
import java.util.Collections
import java.util.Iterator

class help : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val info = pack as MainPack
        val cmd = info.get(CommandAbstraction::class.java)
        if (cmd != null && CommandTuils.isHiddenCommandName(cmd.javaClass.simpleName)) {
            return info.res.getString(R.string.output_commandnotfound)
        }
        val res = cmd?.helpRes() ?: R.string.output_commandnotfound
        return pack.context.getString(R.string.command_help_priority_6d8fc, info.cmdPrefs.getPriority(cmd!!), Tuils.NEWLINE, info.res.getString(res))
    }

    override fun helpRes(): Int = R.string.help_help

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.COMMAND)

    override fun priority(): Int = 5

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        val info = pack as MainPack
        val toPrint = ArrayList(info.commandGroup.commandNames.toList())
        val iterator = toPrint.iterator()
        while (iterator.hasNext()) {
            if (CommandTuils.isHiddenCommandName(iterator.next())) {
                iterator.remove()
            }
        }

        toPrint.sortWith(Tuils::alphabeticCompare)

        Tuils.addPrefix(toPrint, Tuils.DOUBLE_SPACE)
        Tuils.addSeparator(toPrint, Tuils.TRIBLE_SPACE)
        Tuils.insertHeaders(toPrint, true)

        return info.res.getString(R.string.help_workstation_quickstart) +
            Tuils.NEWLINE +
            Tuils.NEWLINE +
            Tuils.toPlanString(toPrint, "")
    }

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String {
        val info = pack as MainPack
        return info.res.getString(R.string.output_commandnotfound)
    }
}
