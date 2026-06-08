package ohi.andre.consolelauncher.commands.main.raw

import android.app.Activity
import android.content.Intent
import android.text.TextUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.WidgetEditorActivity
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.ModulePromptManager
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Arrays
import java.util.Locale
import java.util.ArrayList

class module : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val arg = pack.get(Any::class.java, 0)
        val input = if (arg == null) "" else arg.toString().trim { it <= ' ' }
        if (input.length == 0 || "-ls".equals(input, ignoreCase = true)) {
            return listModules(pack)
        }

        val parts: Array<String?> =
            input.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val option = parts[0]!!.lowercase(Locale.getDefault())

        if ("-new" == option || "-create" == option) {
            return createLuaModule(pack, input)
        }

        if (isLuaModuleCommand(option)) {
            return delegateLuaCommand(pack, input)
        }

        if ("-show" == option || "-open" == option) {
            if (parts.size < 2) return pack.context.getString(R.string.help_module)
            val module = ModuleManager.normalize(parts[1])
            if (!ModuleManager.isKnown(pack.context, module)) {
                if (LuaWidgetManager.exists(module)) {
                    return delegateLuaCommand(pack, "-show " + module)
                }
                return "Unknown module: " + parts[1]
            }
            send(pack, "show", module)
            return "Module opened: " + module
        }

        if ("-close" == option) {
            send(pack, "close", null)
            return "Module closed."
        }

        if ("-prompt" == option) {
            if (parts.size < 3) return pack.context.getString(R.string.help_module)
            val module = ModuleManager.normalize(parts[1])
            val action = parts[2]!!.lowercase(Locale.getDefault())
            if (ModuleManager.REMINDER != module) {
                return "No native prompt session for module: " + module
            }
            send(pack, "show", ModuleManager.REMINDER)
            if ("add" == action || "-add" == action) {
                ModulePromptManager.startReminderAdd(pack.context)
                return "Reminder prompt started."
            }
            if ("edit" == action || "-edit" == action) {
                ModulePromptManager.startReminderEdit(pack.context)
                return "Reminder edit prompt started."
            }
            if ("remove" == action || "rm" == action || "-rm" == action) {
                ModulePromptManager.startReminderRemove(pack.context)
                return "Reminder remove prompt started."
            }
            return pack.context.getString(R.string.output_invalid_param) + " " + parts[2]
        }

        if ("-hide" == option) {
            if (parts.size < 2) return pack.context.getString(R.string.help_module)
            ModuleManager.hideFromDock(pack.context, parts[1])
            send(pack, "rebuild", null)
            return "Module hidden from dock: " + ModuleManager.normalize(parts[1])
        }

        if ("-add" == option) {
            val args = Tuils.splitArgs(input)
            if (args.size < 3) return pack.context.getString(R.string.help_module)
            val module = ModuleManager.normalize(args.get(1))
            val path = args.get(2)
            ModuleManager.setScriptModule(pack.context, module, path)
            ModuleManager.addToDock(pack.context, Arrays.asList<String?>(module))
            send(pack, "rebuild", null)
            if (ModuleManager.isLauncherSource(
                    ModuleManager.getModuleSource(
                        pack.context,
                        module
                    )
                )
            ) {
                send(pack, "refresh", module)
            }
            return ("Module added: " + module
                    + "\nSource: " + ModuleManager.getModuleSource(pack.context, module)
                    + "\nRun module -refresh " + module + " to update it.")
        }

        if ("-refresh" == option) {
            if (parts.size < 2) return pack.context.getString(R.string.help_module)
            val module = ModuleManager.normalize(parts[1])
            if (!ModuleManager.isKnown(pack.context, module)) {
                if (LuaWidgetManager.exists(module)) {
                    return delegateLuaCommand(pack, "-refresh " + module)
                }
                return "Unknown module: " + parts[1]
            }
            if (TextUtils.isEmpty(ModuleManager.getModuleSource(pack.context, module))) {
                return "Module has no source: " + module
            }
            send(pack, "refresh", module)
            return "Module refresh dispatched: " + module
        }

        if ("-rm" == option || "-remove" == option) {
            if (parts.size < 2) return pack.context.getString(R.string.help_module)
            val module = ModuleManager.normalize(parts[1])
            if (!ModuleManager.isKnown(pack.context, module)) {
                return "Unknown module: " + parts[1]
            }
            if (ModuleManager.builtIns.contains(module)) {
                return "Built-in modules cannot be removed. Use module -hide " + module + " instead."
            }
            ModuleManager.removeScriptModule(pack.context, module)
            send(pack, "rebuild", null)
            return "Module removed from registry: " + module
        }

        if ("-dock" == option) {
            if (parts.size >= 2) {
                val dockMode = parts[1]!!.lowercase(Locale.getDefault())
                if ("-toggle" == dockMode || "toggle" == dockMode) {
                    val next = !XMLPrefsManager.getBoolean(Behavior.show_module_dock)
                    LauncherSettings.set(pack.context, Behavior.show_module_dock, next.toString())
                    send(pack, "rebuild", null)
                    return "Module dock " + (if (next) "shown." else "hidden.")
                }
            }
            if (parts.size < 3) return pack.context.getString(R.string.help_module)
            val mode = parts[1]!!.lowercase(Locale.getDefault())
            val verb: String?
            if ("add" == mode || "-add" == mode) {
                verb = "added"
            } else if ("remove" == mode || "-remove" == mode || "rm" == mode || "-rm" == mode) {
                verb = "removed"
            } else {
                return (pack.context.getString(R.string.output_invalid_param) + " " + parts[1]
                        + "\nUse module -dock -toggle, module -dock add [name], or module -dock remove [name].")
            }

            val modules: MutableList<String?> =
                ArrayList<String?>(Arrays.asList<String?>(*parts).subList(2, parts.size))
            if ("added" == verb) {
                ModuleManager.addToDock(pack.context, modules)
            } else {
                ModuleManager.removeFromDock(pack.context, modules)
            }
            send(pack, "rebuild", null)
            return "Module dock " + verb + ": " + formatDock(pack)
        }

        return pack.context.getString(R.string.output_invalid_param) + " " + parts[0]
    }

    private fun listModules(pack: ExecutePack): String {
        val modules = ModuleManager.listAll(pack.context)
        val localLua = ArrayList<String?>()
        for (id in LuaWidgetManager.listIds()) {
            if (!modules.contains(id)) {
                localLua.add(id)
            }
        }
        return ("Modules: " + TextUtils.join(", ", modules)
                + (if (localLua.isEmpty()) "" else "\nLocal Lua modules: " + TextUtils.join(", ", localLua))
                + "\nDock: " + formatDock(pack)
                + "\nUse module -new lua [name], module -edit [name], module -config [name], module -check [name], module -add [name] termux:/path/script.sh, module -refresh [name], module -show [name], events -access, module -prompt reminder add|edit|remove, module -hide [name], module -dock -toggle, module -dock add [name], module -dock remove [name], module -rm [name], module -close.")
    }

    private fun formatDock(pack: ExecutePack): String? {
        val dock = ModuleManager.getDock(pack.context)
        return if (dock.isEmpty()) "<empty>" else TextUtils.join(", ", dock)
    }

    private fun createLuaModule(pack: ExecutePack, input: String): String? {
        val args = Tuils.splitArgs(input)
        if (args.size < 3 || !"lua".equals(args.get(1), ignoreCase = true)) {
            return pack.context.getString(R.string.help_module)
        }
        val requestedName = TextUtils.join(" ", args.subList(2, args.size)).trim { it <= ' ' }
        val id = LuaWidgetManager.idFromName(requestedName)
        if (TextUtils.isEmpty(id)) {
            return "Invalid Lua module id."
        }
        if (!LuaWidgetManager.exists(id)) {
            LuaWidgetManager.save(id, requestedName, LuaWidgetManager.newWidgetTemplate(id))
        }
        val intent = Intent(pack.context, WidgetEditorActivity::class.java)
        intent.putExtra(WidgetEditorActivity.EXTRA_WIDGET_ID, id)
        (pack.context as Activity).startActivity(intent)
        return "Lua module created: " + id
    }

    private fun isLuaModuleCommand(option: String?): Boolean {
        return "-edit" == option
                || "-config" == option
                || "-prefs" == option
                || "-check" == option
                || "-info" == option
                || "-approve" == option
                || "-trust" == option
                || "-copy-error" == option
                || "-disable" == option
                || "-enable" == option
                || "-export" == option
                || "-rename" == option
                || "-mv" == option
                || "-click" == option
                || "-action" == option
                || "-send" == option
                || "-input" == option
                || "-dialog" == option
                || "-expand" == option
                || "-collapse" == option
                || "-toggle" == option
    }

    private fun delegateLuaCommand(pack: ExecutePack, input: String): String? {
        val previousArgs = pack.args
        val previousIndex = pack.currentIndex
        return try {
            pack.set(arrayOf(input))
            pack.currentIndex = 0
            widget().exec(pack)
        } finally {
            pack.set(previousArgs)
            pack.currentIndex = previousIndex
        }
    }

    private fun send(pack: ExecutePack, command: String?, module: String?) {
        val intent = Intent(UIManager.ACTION_MODULE_COMMAND)
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, command)
        if (module != null) {
            intent.putExtra(UIManager.EXTRA_MODULE_NAME, module)
        }
        LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
            .sendBroadcast(intent)
    }

    override fun argType(): IntArray? {
        return intArrayOf(CommandAbstraction.PLAIN_TEXT)
    }

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_module
    }

    override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
        return pack.context.getString(R.string.help_module)
    }

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        return listModules(pack)
    }
}
