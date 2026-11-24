package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.apps.AppsManager
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.terminal.AliasManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Picks up files edited outside the launcher (the Files app, an editor, Termux) and refreshes only what changed.
 * Settings files are compared with the loaded values and the changed settings go through the same refresh as an
 * in-app save, so a colour repaints and a margin re-lays out without rebuilding the launcher. The launcher's own
 * saves already match the loaded values, so they change nothing here.
 */
internal object ConfigFileWatcher {
    private const val DEBOUNCE_MS = 400L
    private const val ASCII = "ascii.txt"

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var observer: FileObserver? = null
    private var folder: File? = null
    private var context: Context? = null
    private var resumed = false
    private val pending = LinkedHashSet<String>()

    private val settingsByFile: Map<String, List<XMLPrefsSave>> by lazy {
        XMLPrefsManager.XMLPrefsRoot.entries.flatMap { it.enums }.distinct()
            .groupBy { LauncherSettings.filePath(it) ?: "" }.filterKeys { it.isNotEmpty() }
    }

    /** Files whose changes this launcher makes itself while open; outside edits to them happen while it is paused. */
    private val otherFiles = setOf(AliasManager.PATH, ASCII, AppsManager.PATH, NotificationManager.PATH)

    fun start(context: Context) {
        val root = Tuils.getFolder()
        if (!root.isDirectory || (observer != null && root == folder)) return
        stop()
        this.context = context.applicationContext
        folder = root
        @Suppress("DEPRECATION")
        observer = object : FileObserver(root.path, CLOSE_WRITE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                val name = path ?: return
                if (name !in settingsByFile && name !in otherFiles) return
                handler.post { record(name) }
            }
        }.also { it.startWatching() }
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
        handler.removeCallbacks(flush)
        pending.clear()
    }

    /** Edits made while another app is in front are applied when the launcher comes back. */
    fun setResumed(resumed: Boolean) {
        this.resumed = resumed
        if (resumed) schedule()
    }

    private fun record(name: String) {
        if (name !in settingsByFile && resumed) return
        pending += name
        schedule()
    }

    private fun schedule() {
        handler.removeCallbacks(flush)
        if (resumed && pending.isNotEmpty()) handler.postDelayed(flush, DEBOUNCE_MS)
    }

    private val flush = Runnable {
        val names = pending.toList()
        pending.clear()
        names.forEach { name -> runCatching { apply(name) }.onFailure { Log.w("TUI-WATCH", "Could not apply $name", it) } }
    }

    private fun apply(name: String) {
        val root = folder ?: return
        val ctx = context
        settingsByFile[name]?.let { settings -> adopt(File(root, name), settings) }
        val pack = LauncherActivity.instance?.uiManager?.pack
        when (name) {
            AliasManager.PATH -> pack?.aliasManager?.reload()
            ASCII -> LauncherActivity.previewIfRunning()
            NotificationManager.PATH -> NotificationService.requestReload(ctx)
            AppsManager.PATH -> pack?.appsManager?.let { apps ->
                Thread {
                    apps.fill()
                    ctx?.let { LocalBroadcastManager.getInstance(it).sendBroadcast(android.content.Intent(UIManager.ACTION_UPDATE_APPS)) }
                }.start()
            }
        }
    }

    private fun adopt(file: File, settings: List<XMLPrefsSave>) {
        val rejected = ArrayList<XMLPrefsSave>()
        // Under the settings lock, so a save the launcher is making itself finishes before the file is read.
        synchronized(SettingsFiles.lock) {
            // A half-written or broken file keeps the settings already loaded.
            val values = runCatching { read(file) }.getOrNull() ?: return
            val changes = settings.mapNotNull { setting -> values[setting.label()]?.let { setting to it } }.toMap()
            LauncherSettings.adoptFileEdits(context, changes, rejected)
        }
        val ctx = context ?: return
        // A toast, since Mono, Dot and Terminal mode have no output area to print into.
        if (rejected.isNotEmpty()) android.widget.Toast.makeText(ctx, ctx.getString(R.string.settings_file_edit_rejected,
            file.name, rejected.joinToString(", ") { it.label().orEmpty() }), android.widget.Toast.LENGTH_LONG).show()
    }

    /** Each setting element's value attribute, by tag name; the first wins if a tag repeats. */
    private fun read(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.documentElement.childNodes
        val values = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? org.w3c.dom.Element ?: continue
            if (element.hasAttribute(XMLPrefsManager.VALUE_ATTRIBUTE) && element.tagName !in values)
                values[element.tagName] = element.getAttribute(XMLPrefsManager.VALUE_ATTRIBUTE)
        }
        return values
    }
}
