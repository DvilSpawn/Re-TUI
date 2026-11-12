package ohi.andre.consolelauncher.managers.modes

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.settings.LauncherMode

/**
 * One launcher mode's home. Everything specific to a mode belongs in its own class, so changing one cannot touch
 * another; add a mode by implementing this and listing it in [HomeModes]. Classic is the launcher's own layout and
 * has no class: it is what runs when no mode takes over.
 */
internal interface HomeMode {
    /** Runs on the search bar layout rather than the classic one. */
    val usesSearchLayout: Boolean get() = false

    /** Keeps the keyboard away until its own gesture reveals it, instead of opening it on focus. */
    val managesOwnKeyboard: Boolean get() = false

    /** Builds this mode's home once the launcher layout exists. */
    fun install() = Unit

    /** The launcher came to the front. Only called when [managesOwnKeyboard]. */
    fun onStart() = Unit

    /** The last step of Back, after surfaces and drawers have closed. True when this mode dealt with it. */
    fun handleBack(): Boolean = false

    /** Home was pressed while the launcher was already in front. */
    fun onHome() = Unit

    fun beforeSearchCommand(command: String) = Unit
    fun afterSearchCommand() = Unit

    /** Rebuilding the search layout hands focus back to the search bar. */
    val focusesSearchInput: Boolean get() = false

    /** Shows the add-terminal and settings tabs beside the clock on the search bar layout. */
    val showsTerminalTabs: Boolean get() = false

    /** Views the module carousel has to blur along with the wallpaper. */
    val viewsBehindModules: List<View> get() = emptyList()

    /** The search bar was touched, focused by touch, or typed in. */
    fun onInputEngaged() = Unit
    fun beforeModuleOpens() = Unit
    fun onKeyboardChanged(visible: Boolean) = Unit
    fun layoutSearchTray(tray: View) = Unit
    fun finishSearchLayout() = Unit
    fun onDisplayMargins(topMm: IntArray, bottomMm: IntArray, metrics: DisplayMetrics?, imeOffset: Int, searchInset: Int, systemTop: Int) = Unit
    fun onAppearanceChanged() = Unit
    fun onPause() = Unit
    fun onResume() = Unit
    fun dispose() = Unit

    /** Something was printed to the terminal. */
    fun onOutput(text: CharSequence?) = Unit

    /** Music or notifications changed. */
    fun onDataChanged() = Unit
}

/** What a mode may ask of the launcher. */
internal interface HomeModeHost {
    val context: Context
    val root: ViewGroup?
    val pack: MainPack
    val weatherDetails: String?
    val weatherCondition: String?
    val weatherSymbol: String?
    val notifications: List<NotificationService.Notification>
    val music: Music?

    /** Puts the keyboard away, even when it still belongs to the layout from before a mode change. */
    fun hideKeyboard()
    fun raiseKeyboard()
    fun closeSearchModules()
    val tray: View?
    val inputBar: View?
    val keyboardUp: Boolean
    fun applyDisplayMargins()
    fun launchSettings()
    fun configureSearchLayout(closeDrawers: Boolean)
    fun activateInput()
    fun refreshSuggestions()
    fun dropKeyboard()
    fun applySectionMargins(view: View?, marginMm: IntArray, metrics: DisplayMetrics?, extraBottomPx: Int, extraTopPx: Int)
    fun onMain(action: () -> Unit)

    /** Runs a command the launcher has already resolved, such as an app picked from suggestions. */
    fun executeResolved(command: String?, resolved: Any?)

    /** Runs a command as if it were typed in the launcher's search. */
    fun executeSearchCommand(command: String)

    class Music(val title: String, val artist: String?, val playing: Boolean)
}

/** The built-in modes, one instance each, for a single launcher layout. */
internal class HomeModes(modes: Map<String, HomeMode>) {
    private val modes = HashMap(modes)

    /**
     * This layout's mode, or a do-nothing one for classic. Fixed for the layout's life: `mode` builds a new layout,
     * and the old one must still be able to shut its own mode down after the setting has changed.
     */
    val active: HomeMode by lazy { modes[LauncherMode.current()] ?: Classic }

    private object Classic : HomeMode
}
