package ohi.andre.consolelauncher.sidebar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.notes.LauncherNotes

/** The edge sidebar: a handle drawn over other apps that opens launcher search, the tmux workspace or a quick app. */
object EdgeSidebar {
    enum class Action { SEARCH, TERMINAL, QUICK_APP }

    /** The settings section holding the sidebar options, shown as its own Behavior entry. */
    const val SECTION = "Edge Sidebar"

    private val settings = setOf<XMLPrefsSave>(Behavior.edge_sidebar, Behavior.edge_sidebar_quick_app,
        Behavior.edge_pill_width, Behavior.edge_pill_length)

    fun isSetting(setting: XMLPrefsSave) = setting in settings

    fun enabled() = XMLPrefsManager.getBoolean(Behavior.edge_sidebar)

    /** Where the sidebar tab was last dropped; it is moved by dragging it, not set in settings. */
    fun onRightEdge(context: Context) = prefs(context).getString("side", "left") == "right"
    internal fun prefs(context: Context) = context.getSharedPreferences("edge_sidebar", Context.MODE_PRIVATE)

    fun canDraw(context: Context) = Settings.canDrawOverlays(context)

    /** The edge pill size settings as factors, clamped to 50-300%. */
    fun widthScale() = scale(Behavior.edge_pill_width)
    fun lengthScale() = scale(Behavior.edge_pill_length)
    private fun scale(setting: XMLPrefsSave) = (runCatching { XMLPrefsManager.getInt(setting) }.getOrDefault(100).coerceIn(50, 300)) / 100f

    /**
     * Starts, restyles or stops the sidebar to match the settings. [askPermission] opens the system page for
     * "display over other apps" when the sidebar was just turned on without it; otherwise a missing permission
     * leaves the sidebar off quietly.
     */
    fun sync(context: Context, askPermission: Boolean = false) {
        // The service also carries the timer, stopwatch and podcast pills, so it runs while any is showing.
        if (!enabled() && !EdgePills.active()) { EdgeSidebarService.stop(context); return }
        if (canDraw(context)) { EdgeSidebarService.start(context); return }
        EdgeSidebarService.stop(context)
        if (askPermission && enabled()) runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun run(context: Context, action: Action) {
        when (action) {
            Action.SEARCH -> context.startActivity(Intent(context, GlobalSearchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            Action.TERMINAL -> context.startActivity(Intent(context, FloatingTerminalActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Action.QUICK_APP -> openQuickApp(context)
        }
    }

    /** The quick app's own icon, or null when it opens a new note. */
    fun quickAppIcon(context: Context): android.graphics.drawable.Drawable? {
        val pkg = XMLPrefsManager.get(Behavior.edge_sidebar_quick_app).trim()
        if (pkg.isEmpty()) return null
        return runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    private fun openQuickApp(context: Context) {
        val pkg = XMLPrefsManager.get(Behavior.edge_sidebar_quick_app).trim()
        val intent = pkg.takeIf { it.isNotEmpty() }?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) { LauncherNotes.openNew(context); return }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
