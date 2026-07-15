package ohi.andre.consolelauncher.commands

import android.content.Context
import android.os.Build
import java.io.IOException
import java.util.ArrayList
import java.util.Collections
import java.util.Locale
import ohi.andre.consolelauncher.commands.main.specific.APICommand
import ohi.andre.consolelauncher.tuils.Tuils
import java.lang.reflect.Constructor
import java.util.Arrays
import java.util.Iterator

class CommandGroup(private val packageName: String) {
    var commands: Array<CommandAbstraction> = emptyArray()
        private set
    var commandNames: Array<String> = emptyArray()
        private set

    constructor(context: Context, packageName: String) : this(packageName) {
        var cmds: MutableList<String> = try {
            Tuils.getClassesInPackage(packageName, context)
        } catch (e: IOException) {
            ArrayList()
        }

        if (cmds.isEmpty()) {
            if (packageName == "ohi.andre.consolelauncher.commands.main.raw") {
                cmds.addAll(
                    listOf(
                        "airplane", "alias", "apps", "beep", "bluetooth", "breach", "brightness", "calc", "call", "cd",
                        "changelog", "clear", "cntcts", "config", "contacts", "credits", "ctrlc", "data", "debug", "devutils",
                        "donate", "duo", "exit", "files", "flash", "guide", "hack", "help", "htmlextract", "install",
                        "intent", "landscape", "location", "lockdown", "ls", "lua", "module", "music", "notes", "notifications", "open",
                        "orientation", "podcast", "pomodoro", "portrait", "post", "preset", "pwd", "rate", "refresh", "regex",
                        "reply", "restart", "retuitoken", "rss", "search", "settings", "share", "shell", "shortcut",
                        "status", "stopwatch", "tbridge", "termux", "termuxopen", "theme", "themer", "time", "timer", "tmux",
                        "tui", "tuiweather", "tuixt", "tutorial", "uninstall", "username", "vibrate", "volume",
                        "wallet", "wallpaper", "webhook", "widget", "wifi"
                    )
                )
            } else if (packageName == "ohi.andre.consolelauncher.commands.tuixt.raw") {
                cmds.addAll(listOf("exit", "help", "save"))
            }
        }

        val cmdAbs: MutableList<CommandAbstraction> = ArrayList()
        val iterator = cmds.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            val ca = buildCommand(s)
            if (ca != null && (ca !is APICommand || ca.willWorkOn(Build.VERSION.SDK_INT))) {
                cmdAbs.add(ca)
            } else {
                iterator.remove()
            }
        }

        Collections.sort(cmds)
        commandNames = cmds.toTypedArray()

        cmdAbs.sortWith { o1, o2 -> o2.priority() - o1.priority() }
        commands = cmdAbs.toTypedArray()
    }

    fun getCommandByName(name: String?): CommandAbstraction? {
        val normalized = normalizeCommandName(name)
        for (command in commands) {
            val commandName = command.javaClass.simpleName
            if (commandName.equals(name, ignoreCase = true) || commandName.equals(normalized, ignoreCase = true)) {
                return command
            }
        }

        val fallback = buildCommand(normalized.lowercase(Locale.getDefault()))
        if (fallback != null && (fallback !is APICommand || fallback.willWorkOn(Build.VERSION.SDK_INT))) {
            return fallback
        }

        return null
    }

    private fun buildCommand(name: String): CommandAbstraction? {
        val fullCmdName = packageName + Tuils.DOT + name
        return try {
            val clazz = Class.forName(fullCmdName)
            if (CommandAbstraction::class.java.isAssignableFrom(clazz)) {
                val constructor = clazz.getConstructor()
                constructor.newInstance() as CommandAbstraction
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private fun normalizeCommandName(name: String?): String {
            if (name == null) {
                return Tuils.EMPTYSTRING
            }
            return name.replace("-", Tuils.EMPTYSTRING).replace("_", Tuils.EMPTYSTRING)
        }
    }
}
