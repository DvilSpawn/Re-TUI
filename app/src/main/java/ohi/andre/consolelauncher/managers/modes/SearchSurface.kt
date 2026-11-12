package ohi.andre.consolelauncher.managers.modes

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.ui.SearchModuleCarousel
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.presentation.HomeLayoutPresentation
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderRuntime

/** What the search bar layout may ask of the launcher. */
internal interface SearchSurfaceHost {
    val context: Context
    val root: ViewGroup?
    val tray: View?
    val appearance: HomeAppearance
    val drawersOpen: Boolean
    val searchInput: View?
    val suggestions: SuggestionsManager?
    fun hideDrawers()

    /** Draws a module into the carousel's [container] instead of Classic's home page. */
    fun renderModuleInto(moduleId: String, container: ViewGroup)
    fun moduleCarouselClosed()
    fun activateInput()
    fun dropKeyboard()
}

/**
 * The search bar layout and its module carousel, shared by every mode that sets [HomeMode.usesSearchLayout]. A mode's
 * own differences come in through the [HomeMode] hooks, so changing one mode leaves the others' search as it was.
 */
internal class SearchSurface(private val host: SearchSurfaceHost, private val mode: () -> HomeMode) {
    private var carousel: SearchModuleCarousel? = null
    private var extraModule: String? = null

    val isModuleOpen: Boolean get() = carousel?.isOpen == true

    fun configure(closeDrawers: Boolean) {
        val root = host.root ?: return
        if (!closeDrawers && host.drawersOpen) return
        HomeLayoutPresentation.configureSearch(root, host.appearance)
        host.hideDrawers()
        val tray = host.tray ?: return
        // Rebuilding this layout drops focus; without this it lands on whichever focusable view comes first.
        if (Build.VERSION.SDK_INT >= 26 && mode().focusesSearchInput) host.searchInput?.isFocusedByDefault = true
        root.findViewById<View>(R.id.search_mode_tabs)?.visibility = View.VISIBLE
        mode().layoutSearchTray(tray)
        val clock = root.findViewById<TextView>(R.id.search_mode_clock_tab) ?: return
        val add = root.findViewById<ImageButton>(R.id.terminal_home_add_tab)
        val settings = root.findViewById<ImageButton>(R.id.terminal_home_settings_tab)
        for (button in listOfNotNull(add, settings)) {
            button.visibility = if (mode().showsTerminalTabs) View.VISIBLE else View.GONE
            button.setColorFilter(clock.currentTextColor)
            button.background = clock.background?.constantState?.newDrawable()?.mutate()
            (button.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = (clock.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin ?: 0
        }
        TerminalBorderRuntime.bind(root.findViewById<View>(R.id.input_group), clock, add, settings)
        mode().finishSearchLayout()
    }

    fun closeModules(backToSearch: Boolean = true) {
        carousel?.close(backToSearch = backToSearch)
    }

    fun refreshModules() {
        carousel?.refresh()
    }

    /** Opens [module] (or the last one) under the MODULES chips in the results; says so when the dock has no modules. */
    fun openModule(module: String?, toggle: Boolean): Boolean {
        if (!mode().usesSearchLayout) return false
        val context = host.context
        val id = module?.let { ModuleManager.normalize(it) }?.takeIf { ModuleManager.isKnown(context, it) }
        val slot = carousel ?: buildCarousel().also { slot ->
            carousel = slot
            host.suggestions?.openModule = { slot.current?.takeIf { slot.isOpen }?.let { it to slot.view } }
        }
        if (toggle && id != null && slot.isOpen && slot.current == id) { slot.close(); return true }
        extraModule = id?.takeIf { it !in ModuleManager.getDock(context) }
        mode().beforeModuleOpens()
        // The keyboard would leave the module little room; the search bar stays.
        host.dropKeyboard()
        if (!slot.open(id)) {
            Tuils.sendOutput(context, context.getString(R.string.search_modules_empty))
            return false
        }
        return true
    }

    private fun buildCarousel() = SearchModuleCarousel(host.context as Activity,
        dock = { (ModuleManager.getDock(host.context).filterNotNull().filter { it.isNotEmpty() } + listOfNotNull(extraModule)).distinct() },
        render = { target, container -> host.renderModuleInto(target, container) },
        changed = {
            host.suggestions?.refreshSearchResults()
            // Bring the open module into view. Not fullScroll: outside touch mode it moves focus.
            host.root?.findViewById<ScrollView>(R.id.search_mode_results_scroll)?.let { results ->
                results.post { carousel?.view?.takeIf { it.isAttachedToWindow }?.let { module ->
                    val bottom = generateSequence(module) { it.parent as? View }.takeWhile { it !== results }.sumOf { it.top } + module.height
                    results.scrollTo(0, (bottom - results.height + results.paddingBottom).coerceAtLeast(0))
                } }
            }
        },
        closed = { backToSearch ->
            host.moduleCarouselClosed()
            extraModule = null
            // Opening the module put the keyboard away; closing it returns to the search as it was.
            if (backToSearch) host.activateInput()
        },
        behind = { mode().viewsBehindModules }
    )
}
