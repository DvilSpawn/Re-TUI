package ohi.andre.consolelauncher.managers.modules.home

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.tuils.frame.FrameTarget

/** Launcher happenings a module can ask to be redrawn for. */
enum class HomeModuleEvent { CLOCK, WEATHER }

/**
 * A built-in Home module. Everything specific to a module belongs in its own class; add a new one
 * by implementing this and listing it where UIManager builds its [HomeModules].
 */
internal interface HomeModule {
    val id: String

    /** Body text uses the theme font rather than monospace. */
    val usesThemeTypeface: Boolean get() = false

    /** Events that redraw this module while it is open. */
    val refreshOn: Set<HomeModuleEvent> get() = emptySet()

    /** Whether this module's suggestion chips are shown right now. */
    val showsSuggestions: Boolean get() = true

    /** Broadcast actions delivered to [onBroadcast], whether or not the module is open. */
    val broadcasts: Set<String> get() = emptySet()

    /** While open, content that does not fit may push the status rows off screen. */
    val pushesStatusRowsUp: Boolean get() = false

    /** Called as the module opens, not when an open module redraws. */
    fun onOpening() = Unit
    fun onBroadcast(intent: Intent) = Unit
    fun onLauncherPaused() = Unit
    fun onLauncherResumed() = Unit

    fun show(host: HomeModuleHost)

    /** Draws a custom text-panel body; returns false to use the plain text body. */
    fun renderBody(host: HomeModuleHost, text: CharSequence?, body: TextView, scroll: ScrollView): Boolean = false
}

/** What a module may ask of the launcher. */
internal interface HomeModuleHost {
    val context: Context
    val root: View?
    val moduleContainer: ViewGroup?
    val handler: Handler?
    val activeModuleId: String?
    val windowFocused: Boolean
    fun presentModule(moduleId: String)
    fun closeModule()
    fun refreshSuggestions()
    fun refreshContextVisibility()
    fun refreshDockSelection()
    fun repaintTextModule(moduleId: String?)
    fun executeCommand(command: String?)
    fun launchActivity(intent: Intent)

    /** Makes a module's close button close it, in the shared header style. */
    fun bindCloseButton(close: TextView)
    fun styleCloseButton(close: TextView?, target: FrameTarget)
    fun renderText(moduleId: String?, text: CharSequence?)
    fun bodyTextColor(): Int
    fun accentTextColor(): Int
    fun bodyTextSize(): Int
    fun appearance(): HomeAppearance
    fun applyBodyTypeface(view: TextView, moduleId: String?, fontMode: String? = null)

    /** Adds a row of buttons; each pair is a label and the command it runs. */
    fun addCommandButtons(parent: LinearLayout, commands: List<Pair<String, String>>)
}

internal class HomeModules(modules: List<HomeModule>) {
    private val byId = modules.associateBy { it.id }

    init {
        require(byId.size == modules.size) { "Two Home modules share an id" }
    }

    operator fun get(id: String?): HomeModule? = byId[id]

    val broadcasts: Set<String> = modules.flatMapTo(HashSet()) { it.broadcasts }

    fun handlesBroadcast(action: String?): Boolean = action in broadcasts

    fun onBroadcast(intent: Intent) = byId.values.filter { intent.action in it.broadcasts }.forEach { it.onBroadcast(intent) }
    fun onLauncherPaused() = byId.values.forEach { it.onLauncherPaused() }
    fun onLauncherResumed() = byId.values.forEach { it.onLauncherResumed() }
}
