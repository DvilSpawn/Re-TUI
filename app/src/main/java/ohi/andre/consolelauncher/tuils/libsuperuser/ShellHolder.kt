package ohi.andre.consolelauncher.tuils.libsuperuser

import android.content.Context
import java.io.File
import java.util.regex.Pattern
import ohi.andre.consolelauncher.managers.terminal.TerminalManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils

class ShellHolder(private val context: Context) {
    private val p: Pattern = Pattern.compile("^\\n")

    fun build(): Shell.Interactive {
        val interactive = Shell.Builder()
            .setOnSTDOUTLineListener { line ->
                val output = p.matcher(line).replaceAll(Tuils.EMPTYSTRING)
                Tuils.sendOutput(context, output, TerminalManager.CATEGORY_OUTPUT)
            }
            .setOnSTDERRLineListener { line ->
                val output = p.matcher(line).replaceAll(Tuils.EMPTYSTRING)
                Tuils.sendOutput(context, output, TerminalManager.CATEGORY_OUTPUT)
            }
            .open()

        interactive.addCommand("cd " + XMLPrefsManager.get(File::class.java, Behavior.home_path))
        return interactive
    }
}
