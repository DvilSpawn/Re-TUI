package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.annotation.WorkerThread
import ohi.andre.consolelauncher.LauncherActivity
import java.util.HashMap
import java.util.Locale
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceActivity
import ohi.andre.consolelauncher.managers.xml.AutoColorManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.UIUtils
import java.io.File
import android.util.AtomicFile
import javax.xml.parsers.DocumentBuilderFactory

object LauncherSettings {
    private val LOCK = Any()
    private val snapshot: MutableMap<String, String> = HashMap()
    private var snapshotLoaded = false

    @JvmStatic
    fun get(value: XMLPrefsSave?): String? {
        if (value == null) {
            return null
        }

        synchronized(LOCK) {
            if (!snapshotLoaded) {
                refreshFromLoadedPrefsLocked()
            }

            val current = snapshot[key(value)]
            if (current != null) {
                return current
            }
        }

        return XMLPrefsManager.get(value)
    }

    @JvmStatic
    fun getBoolean(value: XMLPrefsSave?): Boolean = java.lang.Boolean.parseBoolean(getOrDefault(value))

    @JvmStatic
    fun getInt(value: XMLPrefsSave?): Int {
        return try {
            getOrDefault(value).toInt()
        } catch (e: Exception) {
            XMLPrefsManager.getInt(value)
        }
    }

    @JvmStatic
    fun getColor(value: XMLPrefsSave?): Int {
        if (value == null) return Color.WHITE
        if (value is Theme) return ThemeColorResolver.color(value)
        val color = try {
            Color.parseColor(getOrDefault(value))
        } catch (e: Exception) {
            XMLPrefsManager.getColor(value)
        }
        return color
    }

    @JvmStatic
    fun refreshFromLoadedPrefs() {
        synchronized(LOCK) {
            refreshFromLoadedPrefsLocked()
        }
    }

    @JvmStatic
    fun invalidate() {
        synchronized(LOCK) {
            snapshot.clear()
            snapshotLoaded = false
            revision++
        }
    }

    @JvmStatic
    fun debugSummary(): String {
        synchronized(LOCK) {
            return "settings_snapshot_loaded: " + snapshotLoaded +
                "\nsettings_snapshot_values: " + snapshot.size +
                "\nsettings_revision: " + revision +
                "\nsettings_validation_warnings: " + XMLPrefsManager.validationWarnings.joinToString(",")
        }
    }

    @JvmStatic
    fun set(value: XMLPrefsSave?, rawValue: String?) {
        set(null, value, rawValue)
    }

    data class Snapshot(val revision: Long, val values: kotlin.collections.Map<XMLPrefsSave, String?>)
    data class Change(val previous: Snapshot, val current: Snapshot, val settings: Set<XMLPrefsSave>)
    private var revision = 0L

    fun currentRevision(): Long = synchronized(LOCK) {
        if (!snapshotLoaded) refreshFromLoadedPrefsLocked()
        revision
    }

    fun currentSnapshot(): Snapshot = synchronized(LOCK) {
        if (!snapshotLoaded) refreshFromLoadedPrefsLocked()
        Snapshot(revision, java.util.Collections.unmodifiableMap(XMLPrefsManager.XMLPrefsRoot.entries
            .flatMap { it.enums }.associateWith { snapshot[key(it)] ?: it.defaultValue() }))
    }

    @JvmStatic
    fun set(context: Context?, value: XMLPrefsSave?, rawValue: String?) {
        if (value != null) applyChanges(context, mapOf(value to rawValue.orEmpty()))
    }

    @JvmOverloads
    fun applyChanges(context: Context?, changes: kotlin.collections.Map<XMLPrefsSave, String?>): Change =
        applyChangesWithFiles(context, changes, emptyMap())

    /** The public config -erase command deliberately stores an empty override. */
    fun erase(context: Context?, setting: XMLPrefsSave): Change =
        applyChangesWithFiles(context, mapOf(setting to ""), emptyMap(), setOf(setting))

    internal fun applyChangesWithFiles(context: Context?, changes: kotlin.collections.Map<XMLPrefsSave, String?>,
                                      files: kotlin.collections.Map<File, File?>, clear: Set<XMLPrefsSave> = emptySet()): Change = synchronized(SettingsFiles.lock) {
        val owner = context?.applicationContext ?: XMLPrefsManager.settingsContext
        SettingsFiles.recover(owner)
        val previous = currentSnapshot()
        val edits = changes.filter { (setting, raw) -> (raw ?: "") != get(setting) }
        for ((setting, raw) in edits) {
            val error = if (setting in clear && raw.isNullOrEmpty()) null else SettingsValidation.error(setting, raw)
            require(error == null) { "${setting.label()}: ${owner?.getString(error!!) ?: "Invalid setting value"}" }
        }
        if (edits.isEmpty() && files.isEmpty()) return@synchronized Change(previous, previous, emptySet())
        val folder = Tuils.getFolder()
        val staging = File.createTempFile(".settings-save-", ".tmp", folder)
        check(staging.delete() && staging.mkdir()) { "Unable to prepare settings save" }
        try {
            val replacements = files.toMutableMap()
            for ((path, group) in edits.entries.groupBy { (setting, _) ->
                setting.parent()?.path() ?: XMLPrefsManager.XMLPrefsRoot.entries.firstOrNull { setting in it.enums }?.path
                    ?: error("Settings manager is not available: ${setting.label()}")
            }) {
                val root = XMLPrefsManager.XMLPrefsRoot.entries.first { it.path == path }
                val target = File(folder, path)
                val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                val document = if (target.exists()) AtomicFile(target).openRead().use { parser.parse(it) }
                    else parser.newDocument().also { it.appendChild(it.createElement(root.name)) }
                require(document.documentElement.tagName == root.name) { "Unexpected root in $path" }
                for ((setting, raw) in group) {
                    val nodes = document.documentElement.childNodes
                    val matching = (0 until nodes.length).mapNotNull { nodes.item(it) as? org.w3c.dom.Element }
                        .filter { it.tagName == setting.label() }
                    val node = matching.firstOrNull() ?: document.createElement(setting.label()).also { document.documentElement.appendChild(it) }
                    matching.drop(1).forEach { document.documentElement.removeChild(it) }
                    node.setAttribute(XMLPrefsManager.VALUE_ATTRIBUTE, raw.orEmpty())
                }
                val staged = File(staging, path)
                XMLPrefsManager.writeTo(document, staged)
                replacements[target] = staged
            }
            SettingsFiles.replace(owner, replacements) {
                synchronized(LOCK) {
                    for ((setting, raw) in edits) {
                        val root = XMLPrefsManager.XMLPrefsRoot.entries.first { setting in it.enums }
                        root.getValues()?.add(setting.label(), raw.orEmpty())
                        setting.parent()?.getValues()?.add(setting.label(), raw.orEmpty())
                        snapshot[key(setting)] = raw.orEmpty()
                    }
                    snapshotLoaded = true
                    revision++
                }
            }
            if (files.keys.any { it.name == "frames" }) ohi.andre.consolelauncher.tuils.FrameManager.settingsCommitted()
            val change = Change(previous, currentSnapshot(), edits.keys.toSet())
            SettingsRefreshCoordinator.request(owner, change)
            change
        } finally { staging.deleteRecursively() }
    }

    @JvmStatic
    fun setTheme(value: Theme, rawValue: String?) {
        set(value, rawValue)
    }

    fun setStatusIndex(context: Context?, setting: Ui, value: String?) {
        val current = StatusRowResolver.settings.associateWith { get(it) }
        val reordered = StatusRowResolver.reorder(current, mapOf(setting to value))
        applyChanges(context, reordered.values.filter { (item, value) -> current[item] != value })
    }

    @JvmStatic
    fun setSuggestion(value: Suggestions, rawValue: String?) {
        set(value, rawValue)
    }

    @JvmStatic
    fun setUi(value: Ui, rawValue: String?) {
        set(value, rawValue)
    }

    @JvmStatic
    fun setAutoColorPick(enabled: Boolean) {
        setUi(Ui.auto_color_pick, enabled.toString())
    }

    @WorkerThread
    @JvmStatic
    fun applyWallpaperAuto(context: Context): AutoColorManager.Snapshot =
        applyWallpaperSnapshot(context, AutoColorManager.currentWallpaperColorSnapshot(context))

    internal fun applyWallpaperSnapshot(context: Context, snapshot: AutoColorManager.Snapshot): AutoColorManager.Snapshot {
        if (!snapshot.succeeded) return snapshot
        val changes = linkedMapOf<XMLPrefsSave, String?>(Ui.system_wallpaper to "true")
        snapshot.colors.forEach { (setting, color) ->
            if (setting !is Theme || !setting.advanced) changes[setting] = String.format(Locale.US, "#%08X", color)
        }
        changes[Theme.wallpaper_overlay_color] = "#00000000"
        changes[Ui.auto_color_pick] = "false"
        applyChanges(context, changes)
        return snapshot
    }

    @JvmStatic
    fun freezeLegacyWallpaperAuto(context: Context) {
        if (!getBoolean(Ui.auto_color_pick)) return
        Thread({
            val result = applyWallpaperAuto(context)

        }, "retui-auto-theme").start()
    }

    @JvmStatic
    fun getEffective(value: XMLPrefsSave): String? {
        val current = get(value)
        if (current == null || current.isEmpty()) {
            return value.defaultValue()
        }
        return current
    }

    private fun getOrDefault(value: XMLPrefsSave?): String {
        val current = get(value)
        if (current == null || current.isEmpty()) {
            return value?.defaultValue() ?: ""
        }
        return current
    }

    private fun refreshFromLoadedPrefsLocked() {
        val loaded = HashMap<String, String>()
        for (root in XMLPrefsManager.XMLPrefsRoot.values()) {
            for (save in root.enums) {
                try {
                    // Some settings (notifications/apps) are owned by a live manager, not the XML root cache.
                    val value: String? = (save.parent() ?: root).getValues()?.get(save)?.value
                    if (value != null) {
                        loaded[key(save)] = value
                    }
                } catch (ignored: Exception) {
                }
            }
        }
        if (snapshotLoaded && loaded != snapshot) revision++
        snapshot.clear()
        snapshot.putAll(loaded)
        snapshotLoaded = true
    }

    private fun key(value: XMLPrefsSave): String {
        val parent = value.parent()
        val parentPath = parent?.path() ?: "unknown"
        return parentPath + ":" + value.label()
    }

}
