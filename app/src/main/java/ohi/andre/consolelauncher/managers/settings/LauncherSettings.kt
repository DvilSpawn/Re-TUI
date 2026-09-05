package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import android.graphics.Color
import java.util.HashMap
import java.util.Locale
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.xml.AutoColorManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.UIUtils
import java.util.Map

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
        AutoColorManager.invalidate()
    }

    @JvmStatic
    fun invalidate() {
        synchronized(LOCK) {
            snapshot.clear()
            snapshotLoaded = false
        }
        AutoColorManager.invalidate()
    }

    @JvmStatic
    fun debugSummary(): String {
        synchronized(LOCK) {
            return "settings_snapshot_loaded: " + snapshotLoaded +
                "\nsettings_snapshot_values: " + snapshot.size
        }
    }

    @JvmStatic
    fun set(value: XMLPrefsSave?, rawValue: String?) {
        set(null, value, rawValue)
    }

    @JvmStatic
    fun set(context: Context?, value: XMLPrefsSave?, rawValue: String?) {
        if (value == null) {
            return
        }

        val parent: XMLPrefsElement = value.parent() ?: return

        parent.write(value, rawValue ?: Tuils.EMPTYSTRING)
        synchronized(LOCK) {
            if (rawValue != null) {
                snapshot[key(value)] = rawValue
            } else {
                snapshot.remove(key(value))
            }
            snapshotLoaded = true
        }
        onSettingChanged(context, value)
    }

    @JvmStatic
    fun setTheme(value: Theme, rawValue: String?) {
        set(value, rawValue)
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

    @JvmStatic
    fun enableWallpaperAuto(context: Context? = null): Boolean {
        AutoColorManager.init(context)
        val colors = AutoColorManager.currentWallpaperColorSnapshot()
        set(context, Ui.system_wallpaper, "true")
        colors.forEach { (setting, color) ->
            set(context, setting, String.format(Locale.US, "#%08X", color))
        }
        set(context, Theme.wallpaper_overlay_color, "#00000000")
        set(context, Ui.auto_color_pick, "false")
        return colors.isNotEmpty()
    }

    @JvmStatic
    fun freezeLegacyWallpaperAuto(context: Context) {
        if (getBoolean(Ui.auto_color_pick)) enableWallpaperAuto(context)
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
        snapshot.clear()
        for (root in XMLPrefsManager.XMLPrefsRoot.values()) {
            for (save in root.enums) {
                try {
                    val value: String? = root.getValues()!!.get(save)?.value
                    if (value != null) {
                        snapshot[key(save)] = value
                    }
                } catch (ignored: Exception) {
                }
            }
        }
        snapshotLoaded = true
    }

    private fun key(value: XMLPrefsSave): String {
        val parent = value.parent()
        val parentPath = parent?.path() ?: "unknown"
        return parentPath + ":" + value.label()
    }

    private fun onSettingChanged(context: Context?, value: XMLPrefsSave) {
        if (value == Ui.auto_color_pick) {
            AutoColorManager.invalidate()
        }

        if (value == Ui.system_font || value == Ui.font_file) {
            Tuils.cancelFont()
            UIUtils.cancelFont()
        }

        if (context == null) {
            return
        }

        if (value is Notifications || value == Behavior.preferred_music_app) {
            NotificationService.requestReload(context)
        }
    }
}
