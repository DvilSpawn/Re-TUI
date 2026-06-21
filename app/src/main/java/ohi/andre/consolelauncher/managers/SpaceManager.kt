package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.SharedPreferences
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.termux.TermuxAppManager
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetReminderManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.LinkedHashSet
import java.util.Locale

object SpaceManager {
    private const val SPACES_FOLDER = "spaces"
    private const val MANIFEST_FILE = "manifest.properties"
    private const val FILES_FOLDER = "files"
    private const val SHARED_PREFS_FOLDER = "shared_prefs"
    private const val PREFS = "retui_spaces"
    private const val KEY_ACTIVE_SPACE_ID = "active_space_id"
    private const val KEY_NEXT_SPACE_NUMBER = "next_space_number"
    private const val SCHEMA = 1
    private const val DEFAULT_SPACE_ID = "space-1"
    private const val DEFAULT_SPACE_NAME = "Space 1"

    private val PROVIDERS = listOf(
        FilesProvider(
            id = "xml-config",
            paths = arrayOf(
                XMLPrefsManager.XMLPrefsRoot.THEME.path,
                XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path,
                XMLPrefsManager.XMLPrefsRoot.UI.path,
                XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path,
                XMLPrefsManager.XMLPrefsRoot.APPS.path,
                XMLPrefsManager.XMLPrefsRoot.NOTIFICATIONS.path,
                XMLPrefsManager.XMLPrefsRoot.TOOLBAR.path,
                XMLPrefsManager.XMLPrefsRoot.CMD.path,
                XMLPrefsManager.XMLPrefsRoot.RSS.path
            )
        ),
        FilesProvider(
            id = "launcher-files",
            paths = arrayOf(
                AliasManager.PATH,
                NotesManager.PATH,
                SearchProviderManager.PATH,
                ReplyManager.PATH,
                "ascii.txt",
                "webhooks.xml",
                "htmlextract.xml"
            )
        ),
        SharedPrefsProvider(
            id = "launcher-prefs",
            names = arrayOf(
                "ui",
                "apps",
                "android_widget_drawer",
                "pomodoro_state",
                PinnedShortcutManager.PREFS
            )
        ),
        SharedPrefsProvider(
            id = "module-prefs",
            names = arrayOf(
                "retui_modules",
                "retui_reminders",
                "retui_module_prompt",
                "retui_tmux_workspace_launchers",
                LuaWidgetReminderManager.PREFS,
                TermuxAppManager.PREFS
            )
        )
    )

    data class Space(val id: String, val name: String, val updatedAt: Long)

    fun ensureInitialized(context: Context): Space {
        val prefs = prefs(context)
        val spaces = listSpacesInternal()
        var activeId = prefs.getString(KEY_ACTIVE_SPACE_ID, null)

        if (spaces.isEmpty()) {
            val created = Space(DEFAULT_SPACE_ID, DEFAULT_SPACE_NAME, System.currentTimeMillis())
            val dir = spaceDir(created.id)
            ensureDir(dir)
            writeManifest(dir, created)
            snapshotToSpace(context, created.id)
            prefs.edit()
                .putString(KEY_ACTIVE_SPACE_ID, created.id)
                .putInt(KEY_NEXT_SPACE_NUMBER, 2)
                .apply()
            return readSpace(created.id) ?: created
        }

        if (activeId == null || findSpace(activeId) == null) {
            activeId = spaces[0].id
            prefs.edit().putString(KEY_ACTIVE_SPACE_ID, activeId).apply()
        }

        return readSpace(activeId) ?: spaces[0]
    }

    fun activeSpace(context: Context): Space = ensureInitialized(context)

    fun listSpaces(context: Context): List<Space> {
        ensureInitialized(context)
        return listSpacesInternal()
    }

    fun describeSpaces(context: Context): String {
        val active = activeSpace(context)
        val spaces = listSpaces(context)
        if (spaces.isEmpty()) {
            return "No Spaces found."
        }

        val out = StringBuilder()
        for (space in spaces) {
            if (out.isNotEmpty()) {
                out.append(Tuils.NEWLINE)
            }
            out.append(if (space.id == active.id) "* " else "  ")
                .append(space.name)
                .append(" [")
                .append(space.id)
                .append("]")
        }
        return out.toString()
    }

    fun saveActive(context: Context): Space {
        val active = activeSpace(context)
        snapshotToSpace(context, active.id)
        return readSpace(active.id) ?: active
    }

    fun createFromActive(context: Context, name: String?): Space {
        val cleanName = cleanDisplayName(name)
        require(cleanName.isNotEmpty()) { "Space name is required." }
        require(findSpaceByName(cleanName) == null) { "Space already exists." }

        val active = saveActive(context)
        val newId = nextSpaceId(context)
        val source = spaceDir(active.id)
        val target = spaceDir(newId)
        if (target.exists()) {
            Tuils.delete(target)
        }
        copyDirectory(source, target)
        val created = Space(newId, cleanName, System.currentTimeMillis())
        writeManifest(target, created)
        prefs(context).edit().putString(KEY_ACTIVE_SPACE_ID, newId).apply()
        return created
    }

    fun renameActive(context: Context, name: String?): Space {
        val cleanName = cleanDisplayName(name)
        require(cleanName.isNotEmpty()) { "Space name is required." }
        val active = activeSpace(context)
        val existing = findSpaceByName(cleanName)
        require(existing == null || existing.id == active.id) { "Space already exists." }

        val renamed = Space(active.id, cleanName, System.currentTimeMillis())
        writeManifest(spaceDir(active.id), renamed)
        return renamed
    }

    fun remove(context: Context, value: String?): Space {
        val active = activeSpace(context)
        val target = resolveSpace(value)
        requireNotNull(target) { "Space not found." }
        require(target.id != active.id) { "Cannot remove the active Space." }
        require(listSpacesInternal().size > 1) { "Cannot remove the only Space." }

        Tuils.delete(spaceDir(target.id))
        return target
    }

    fun switchTo(context: Context, value: String?): Space {
        val active = activeSpace(context)
        val target = resolveSpace(value)
        requireNotNull(target) { "Space not found." }
        if (target.id == active.id) {
            return target
        }

        snapshotToSpace(context, active.id)
        restoreFromSpace(context, target.id)
        prefs(context).edit().putString(KEY_ACTIVE_SPACE_ID, target.id).apply()
        return target
    }

    private fun snapshotToSpace(context: Context, id: String) {
        val root = Tuils.getFolder()
        val staging = File(spacesDir(), ".$id-staging")
        val previous = File(spacesDir(), ".$id-previous")
        val target = spaceDir(id)
        if (staging.exists()) {
            Tuils.delete(staging)
        }
        if (previous.exists()) {
            Tuils.delete(previous)
        }
        ensureDir(staging)

        for (provider in PROVIDERS) {
            provider.snapshot(context, root, staging)
        }

        val current = readSpace(id) ?: Space(id, DEFAULT_SPACE_NAME, System.currentTimeMillis())
        writeManifest(staging, current.copy(updatedAt = System.currentTimeMillis()))
        replaceSpaceDirectory(staging, target, previous)
    }

    private fun restoreFromSpace(context: Context, id: String) {
        val root = Tuils.getFolder()
        val dir = spaceDir(id)
        require(dir.isDirectory) { "Space not found." }
        for (provider in PROVIDERS) {
            provider.restore(context, root, dir)
        }

        XMLPrefsManager.dispose()
    }

    private fun listSpacesInternal(): List<Space> {
        val files = spacesDir().listFiles() ?: return emptyList()
        val spaces = ArrayList<Space>()
        for (file in files) {
            if (!file.isDirectory) {
                continue
            }
            val space = readSpace(file.name)
            if (space != null) {
                spaces.add(space)
            }
        }
        spaces.sortWith(compareBy<Space> { numericSpaceId(it.id) }.thenBy { it.name.lowercase(Locale.getDefault()) })
        return spaces
    }

    private fun resolveSpace(value: String?): Space? {
        val clean = cleanDisplayName(value)
        if (clean.isEmpty()) {
            return null
        }
        return findSpace(clean) ?: findSpaceByName(clean)
    }

    private fun findSpace(id: String?): Space? {
        if (id == null) {
            return null
        }
        return readSpace(id)
    }

    private fun findSpaceByName(name: String): Space? {
        for (space in listSpacesInternal()) {
            if (space.name.equals(name, ignoreCase = true)) {
                return space
            }
        }
        return null
    }

    private fun readSpace(id: String?): Space? {
        if (id == null || !safeSpaceId(id)) {
            return null
        }
        val manifest = File(spaceDir(id), MANIFEST_FILE)
        if (!manifest.isFile) {
            return null
        }
        val values = parseProperties(readText(manifest))
        val manifestId = values["id"] ?: id
        if (!safeSpaceId(manifestId)) {
            return null
        }
        return Space(
            manifestId,
            values["name"]?.let { decode(it) } ?: manifestId,
            values["updatedAt"]?.toLongOrNull() ?: 0L
        )
    }

    private fun writeManifest(dir: File, space: Space) {
        ensureDir(dir)
        val text = StringBuilder()
            .append("schema=").append(SCHEMA).append('\n')
            .append("id=").append(space.id).append('\n')
            .append("name=").append(encode(space.name)).append('\n')
            .append("updatedAt=").append(space.updatedAt).append('\n')
        for (provider in PROVIDERS) {
            text.append("provider.")
                .append(provider.id)
                .append('=')
                .append(provider.version)
                .append('\n')
        }
        writeText(File(dir, MANIFEST_FILE), text.toString())
    }

    private fun nextSpaceId(context: Context): String {
        val prefs = prefs(context)
        var next = prefs.getInt(KEY_NEXT_SPACE_NUMBER, 2)
        val existing = LinkedHashSet<String>()
        for (space in listSpacesInternal()) {
            existing.add(space.id)
        }
        while (existing.contains("space-$next")) {
            next++
        }
        prefs.edit().putInt(KEY_NEXT_SPACE_NUMBER, next + 1).apply()
        return "space-$next"
    }

    private fun spacesDir(): File {
        val dir = File(Tuils.getFolder(), SPACES_FOLDER)
        ensureDir(dir)
        return dir
    }

    private fun spaceDir(id: String): File = File(spacesDir(), id)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cleanDisplayName(value: String?): String {
        if (value == null) {
            return ""
        }
        return value.trim { it <= ' ' }.replace(Regex("\\s+"), " ")
    }

    private fun safeSpaceId(value: String): Boolean =
        Regex("space-[0-9]+").matches(value)

    private fun numericSpaceId(id: String): Int =
        id.substringAfter("space-", "999999").toIntOrNull() ?: 999999

    private fun ensureDir(dir: File) {
        check(dir.exists() || dir.mkdirs()) { "Unable to create folder: " + dir.name }
    }

    private fun copyDirectory(source: File, target: File) {
        if (source.isDirectory) {
            ensureDir(target)
            val children = source.listFiles() ?: return
            for (child in children) {
                copyDirectory(child, File(target, child.name))
            }
        } else if (source.isFile) {
            copyFile(source, target)
        }
    }

    private fun replaceSpaceDirectory(staging: File, target: File, previous: File) {
        if (target.exists()) {
            check(target.renameTo(previous)) { "Unable to prepare Space folder: " + target.name }
        }

        try {
            check(staging.renameTo(target)) { "Unable to activate Space snapshot: " + target.name }
            if (previous.exists()) {
                Tuils.delete(previous)
            }
        } catch (e: RuntimeException) {
            if (!target.exists() && previous.exists()) {
                previous.renameTo(target)
            }
            throw e
        }
    }

    private fun copyFile(source: File, target: File) {
        val parent = target.parentFile
        if (parent != null) {
            ensureDir(parent)
        }
        Tuils.copy(source, target)
    }

    private fun writeText(file: File, text: String) {
        val parent = file.parentFile
        if (parent != null) {
            ensureDir(parent)
        }
        file.writeText(text, Charsets.UTF_8)
    }

    private fun readText(file: File): String =
        file.readText(Charsets.UTF_8)

    private fun serializePrefs(values: Map<String, *>): String {
        val out = StringBuilder()
        val keys = values.keys.filterNotNull().sorted()
        for (key in keys) {
            val value = values[key] ?: continue
            when (value) {
                is Boolean -> appendPref(out, "boolean", key, value.toString())
                is Int -> appendPref(out, "int", key, value.toString())
                is Long -> appendPref(out, "long", key, value.toString())
                is Float -> appendPref(out, "float", key, value.toString())
                is Set<*> -> appendPref(out, "set", key, value.filterIsInstance<String>().sorted().joinToString("\u001F"))
                else -> appendPref(out, "string", key, value.toString())
            }
        }
        return out.toString()
    }

    private fun appendPref(out: StringBuilder, type: String, key: String, value: String) {
        out.append(type)
            .append('|')
            .append(encode(key))
            .append('|')
            .append(encode(value))
            .append('\n')
    }

    private fun applyPrefs(editor: SharedPreferences.Editor, text: String) {
        for (line in text.split('\n')) {
            if (line.isEmpty()) {
                continue
            }
            val parts = line.split("|", limit = 3)
            if (parts.size != 3) {
                continue
            }
            val key = decode(parts[1])
            val value = decode(parts[2])
            try {
                when (parts[0]) {
                    "boolean" -> editor.putBoolean(key, value.toBoolean())
                    "int" -> editor.putInt(key, value.toInt())
                    "long" -> editor.putLong(key, value.toLong())
                    "float" -> editor.putFloat(key, value.toFloat())
                    "set" -> editor.putStringSet(key, LinkedHashSet(value.split('\u001F').filter { it.isNotEmpty() }))
                    else -> editor.putString(key, value)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun parseProperties(text: String): Map<String, String> {
        val values = HashMap<String, String>()
        for (line in text.split('\n')) {
            val index = line.indexOf('=')
            if (index <= 0) {
                continue
            }
            values[line.substring(0, index)] = line.substring(index + 1)
        }
        return values
    }

    private fun encode(value: String): String =
        android.util.Base64.encodeToString(value.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

    private fun decode(value: String): String =
        try {
            String(android.util.Base64.decode(value, android.util.Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            value
        }

    private interface SpaceStateProvider {
        val id: String
        val version: Int

        fun snapshot(context: Context, liveRoot: File, spaceRoot: File)

        fun restore(context: Context, liveRoot: File, spaceRoot: File)
    }

    private class FilesProvider(
        override val id: String,
        private val paths: Array<String>,
        override val version: Int = 1
    ) : SpaceStateProvider {
        override fun snapshot(context: Context, liveRoot: File, spaceRoot: File) {
            val filesDir = File(spaceRoot, FILES_FOLDER)
            ensureDir(filesDir)
            for (path in paths) {
                val source = File(liveRoot, path)
                val target = File(filesDir, path)
                if (source.isFile) {
                    copyFile(source, target)
                } else if (target.exists()) {
                    Tuils.delete(target)
                }
            }
        }

        override fun restore(context: Context, liveRoot: File, spaceRoot: File) {
            val filesDir = File(spaceRoot, FILES_FOLDER)
            for (path in paths) {
                val target = File(liveRoot, path)
                if (target.exists()) {
                    Tuils.delete(target)
                }
                val source = File(filesDir, path)
                if (source.isFile) {
                    copyFile(source, target)
                }
            }
        }
    }

    private class SharedPrefsProvider(
        override val id: String,
        private val names: Array<String>,
        override val version: Int = 1
    ) : SpaceStateProvider {
        override fun snapshot(context: Context, liveRoot: File, spaceRoot: File) {
            val prefsDir = File(spaceRoot, SHARED_PREFS_FOLDER)
            ensureDir(prefsDir)
            for (name in names) {
                val values = context.applicationContext
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .all
                val target = File(prefsDir, "$name.properties")
                writeText(target, serializePrefs(values))
            }
        }

        override fun restore(context: Context, liveRoot: File, spaceRoot: File) {
            val prefsDir = File(spaceRoot, SHARED_PREFS_FOLDER)
            for (name in names) {
                val file = File(prefsDir, "$name.properties")
                val editor = context.applicationContext
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                if (file.isFile) {
                    applyPrefs(editor, readText(file))
                }
                check(editor.commit()) { "Unable to restore Space preferences: $name" }
            }
        }
    }
}
