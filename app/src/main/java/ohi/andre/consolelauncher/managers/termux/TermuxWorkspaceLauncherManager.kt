package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.Locale

object TermuxWorkspaceLauncherManager {
    const val PREFS = "retui_tmux_workspace_launchers"
    private const val KEY_IDS = "ids"
    private const val KEY_COMMAND_PREFIX = "command_"
    private const val KEY_TITLE_PREFIX = "title_"
    private const val KEY_WORKDIR_PREFIX = "workdir_"
    private const val KEY_LEGACY_MIGRATED = "legacy_apps_migrated_v1"

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
            .remove(KEY_TITLE_PREFIX + normalized)
            .remove(KEY_WORKDIR_PREFIX + normalized)
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
            out.add(Launcher(id, store.getString(KEY_TITLE_PREFIX + id, null) ?: titleFromId(id), command, false,
                store.getString(KEY_WORKDIR_PREFIX + id, null)))
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

    @Synchronized
    private fun prefs(context: Context): SharedPreferences {
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!store.getBoolean(KEY_LEGACY_MIGRATED, false)) {
            val ids = LinkedHashSet(store.getStringSet(KEY_IDS, emptySet()) ?: emptySet())
            val edit = store.edit()
            for (app in TermuxAppManager.list(context).sortedBy { it.id }) {
                var id = normalizeId(app.id)
                if (id.isEmpty()) continue
                // Built-ins must stay available; retain the old custom app under an explicit name.
                if (isBuiltIn(id)) id = id.take(25) + "-legacy"
                if (ids.contains(id) || store.contains(KEY_COMMAND_PREFIX + id)) continue
                ids.add(id)
                edit.putString(KEY_COMMAND_PREFIX + id, app.command)
                    .putString(KEY_TITLE_PREFIX + id, app.title)
                    .putString(KEY_WORKDIR_PREFIX + id, app.workDir)
            }
            // Keep the legacy store intact, including action metadata, even after migration succeeds.
            edit.putStringSet(KEY_IDS, ids).putBoolean(KEY_LEGACY_MIGRATED, true).commit()
        }
        return store
    }

    data class Launcher(
        val id: String,
        val title: String,
        val command: String,
        val builtIn: Boolean,
        val workDir: String? = null
    ) {
        val launchCommand: String
            get() {
                val directory = workDir?.trim().orEmpty()
                if (directory.isEmpty()) return command
                val quoted = when {
                    directory == "~" -> "\"\$HOME\""
                    directory.startsWith("~/") -> "\"\$HOME\"/" + shellQuote(directory.substring(2))
                    else -> shellQuote(directory)
                }
                return "cd -- $quoted && {\n$command\n}"
            }

        private fun shellQuote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
