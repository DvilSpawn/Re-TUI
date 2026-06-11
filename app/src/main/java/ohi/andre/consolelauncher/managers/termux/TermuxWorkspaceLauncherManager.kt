package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.Locale

object TermuxWorkspaceLauncherManager {
    private const val PREFS = "retui_tmux_workspace_launchers"
    private const val KEY_IDS = "ids"
    private const val KEY_COMMAND_PREFIX = "command_"

    private val BUILT_INS = listOf(
        Launcher("shell", "Shell", "", true),
        Launcher("htop", "htop", "htop", true),
        Launcher("mc", "Midnight Commander", "mc", true),
        Launcher("nano", "Nano", "nano", true),
        Launcher("vim", "Vim", "vim", true),
        Launcher("python", "Python REPL", "python", true),
        Launcher("node", "Node REPL", "node", true),
        Launcher("logs", "Bridge Log", "tail -f ~/.retui/bridge.log", true)
    )

    fun list(context: Context): List<Launcher> {
        val out = ArrayList<Launcher>()
        out.addAll(BUILT_INS)
        out.addAll(custom(context))
        return out
    }

    fun resolve(context: Context, id: String?): Launcher? {
        val normalized = normalizeId(id)
        if (TextUtils.isEmpty(normalized)) {
            return null
        }
        return list(context).firstOrNull { it.id == normalized }
    }

    fun save(context: Context, id: String?, command: String?): Boolean {
        val normalized = normalizeId(id)
        val cleanCommand = command?.trim { it <= ' ' } ?: ""
        if (TextUtils.isEmpty(normalized) || TextUtils.isEmpty(cleanCommand) || isBuiltIn(normalized)) {
            return false
        }
        val store = prefs(context)
        val ids = LinkedHashSet<String>(store.getStringSet(KEY_IDS, LinkedHashSet<String>()) ?: LinkedHashSet())
        ids.add(normalized)
        store.edit()
            .putStringSet(KEY_IDS, ids)
            .putString(KEY_COMMAND_PREFIX + normalized, cleanCommand)
            .apply()
        return true
    }

    fun remove(context: Context, id: String?): Boolean {
        val normalized = normalizeId(id)
        if (TextUtils.isEmpty(normalized) || isBuiltIn(normalized)) {
            return false
        }
        val store = prefs(context)
        val ids = LinkedHashSet<String>(store.getStringSet(KEY_IDS, LinkedHashSet<String>()) ?: LinkedHashSet())
        val removed = ids.remove(normalized)
        store.edit()
            .putStringSet(KEY_IDS, ids)
            .remove(KEY_COMMAND_PREFIX + normalized)
            .apply()
        return removed
    }

    fun normalizeId(value: String?): String {
        if (value == null) {
            return ""
        }
        return value.trim { it <= ' ' }
            .lowercase(Locale.US)
            .replace("[^a-z0-9_-]".toRegex(), "")
            .take(32)
    }

    private fun custom(context: Context): List<Launcher> {
        val store = prefs(context)
        val out = ArrayList<Launcher>()
        val seen = LinkedHashSet<String>()
        for (rawId in store.getStringSet(KEY_IDS, LinkedHashSet<String>()) ?: LinkedHashSet()) {
            val id = normalizeId(rawId)
            if (TextUtils.isEmpty(id) || seen.contains(id) || isBuiltIn(id)) {
                continue
            }
            val command = store.getString(KEY_COMMAND_PREFIX + id, "")?.trim { it <= ' ' } ?: ""
            if (TextUtils.isEmpty(command)) {
                continue
            }
            seen.add(id)
            out.add(Launcher(id, titleFromId(id), command, false))
        }
        return out
    }

    private fun titleFromId(id: String): String {
        val parts = id.replace('-', '_').split("_").filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return id
        }
        return parts.joinToString(" ") { part ->
            part.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
        }
    }

    private fun isBuiltIn(id: String): Boolean = BUILT_INS.any { it.id == id }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Launcher(
        val id: String,
        val title: String,
        val command: String,
        val builtIn: Boolean
    )
}
