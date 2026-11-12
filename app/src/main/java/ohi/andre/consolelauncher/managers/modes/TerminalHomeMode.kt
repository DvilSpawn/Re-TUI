package ohi.andre.consolelauncher.managers.modes

import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.termux.TerminalHomeView
import ohi.andre.consolelauncher.tuils.Tuils

/** Terminal mode: a live terminal as the home, with the search bar layout opening over it on demand. */
internal class TerminalHomeMode(private val host: HomeModeHost) : HomeMode {
    private var home: TerminalHomeView? = null
    private var dim: View? = null
    private var searchOpen = false

    override val usesSearchLayout get() = true
    override val managesOwnKeyboard get() = true
    override val showsTerminalTabs get() = true
    override val viewsBehindModules: List<View> get() = listOfNotNull(home)

    override fun install() {
        if (home != null) return
        val tray = host.tray ?: return
        val parent = tray.parent as? FrameLayout ?: return
        val home = TerminalHomeView(host.context)
        this.home = home
        parent.addView(home, parent.indexOfChild(tray), FrameLayout.LayoutParams(-1, -1))
        dim = View(host.context).apply {
            setBackgroundColor(0x99000000.toInt())
            setOnClickListener { setSearchOpen(false) }
            visibility = View.GONE
        }.also { parent.addView(it, parent.indexOfChild(tray), FrameLayout.LayoutParams(-1, -1)) }
        host.root?.findViewById<View>(R.id.terminal_home_add_tab)?.setOnClickListener {
            setSearchOpen(false); home.showAddTerminal()
        }
        host.root?.findViewById<View>(R.id.terminal_home_settings_tab)?.setOnClickListener {
            setSearchOpen(false); host.launchSettings()
        }
        host.inputBar?.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) host.applyDisplayMargins()
        }
        val inputContainer = host.inputBar as? LinearLayout
        if (inputContainer != null) {
            val keys = LayoutInflater.from(host.context).inflate(R.layout.termux_special_keys, inputContainer, false)
            keys.visibility = View.GONE
            (keys.layoutParams as LinearLayout.LayoutParams).topMargin = Tuils.dpToPx(host.context, 2)
            val inputGroup = inputContainer.findViewById<View>(R.id.input_group)
            inputContainer.addView(keys, inputContainer.indexOfChild(inputGroup) + 1)
            home.bindSpecialKeys(keys)
            home.onTerminalFocusChanged = { focused ->
                val terminalFocused = focused && !searchOpen
                inputGroup.visibility = if (terminalFocused) View.GONE else View.VISIBLE
                inputContainer.findViewById<View>(R.id.search_mode_tabs)?.visibility = if (terminalFocused) View.GONE else View.VISIBLE
                keys.visibility = if (terminalFocused && host.keyboardUp) View.VISIBLE else View.GONE
                host.applyDisplayMargins()
            }
        }
        setSearchOpen(false)
        host.applyDisplayMargins()
        home.resume()
    }

    private fun setSearchOpen(show: Boolean) {
        if (!show) host.closeSearchModules()
        searchOpen = show
        home?.releaseInput()
        host.configureSearchLayout(closeDrawers = true)
        dim?.visibility = if (show) View.VISIBLE else View.GONE
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            home?.setRenderEffect(if (show) android.graphics.RenderEffect.createBlurEffect(18f, 18f, android.graphics.Shader.TileMode.CLAMP) else null)
        }
        home?.importantForAccessibility = if (show) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        if (show) { host.activateInput(); host.refreshSuggestions() } else host.dropKeyboard()
    }

    override fun onStart() = setSearchOpen(false)

    override fun handleBack(): Boolean {
        if (searchOpen) setSearchOpen(false) else home?.releaseInput()
        return true
    }

    /** The search bar was touched or typed in, so the terminal gives way to search. */
    override fun onInputEngaged() {
        if (!searchOpen) setSearchOpen(true)
    }

    // The module opens in the results layer, so the search overlay has to be up for it.
    override fun beforeModuleOpens() = onInputEngaged()

    override fun onKeyboardChanged(visible: Boolean) {
        home?.updateKeyboardVisibility(visible)
    }

    override fun layoutSearchTray(tray: View) {
        tray.visibility = View.VISIBLE
        host.root?.findViewById<View>(R.id.search_mode_results_scroll)?.visibility = if (searchOpen) View.VISIBLE else View.GONE
        (tray.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = if (searchOpen) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            tray.layoutParams = params
        }
    }

    override fun finishSearchLayout() {
        val terminalFocused = !searchOpen && host.keyboardUp && home?.hasTerminalFocus == true
        host.root?.findViewById<View>(R.id.search_mode_tabs)?.visibility = if (terminalFocused) View.GONE else View.VISIBLE
        host.root?.findViewById<View>(R.id.input_group)?.visibility = if (terminalFocused) View.GONE else View.VISIBLE
    }

    override fun onDisplayMargins(topMm: IntArray, bottomMm: IntArray, metrics: DisplayMetrics?, imeOffset: Int, searchInset: Int, systemTop: Int) {
        val bottomChrome = host.inputBar?.height ?: 0
        val bottomClearance = bottomChrome + searchInset + Tuils.mmToPx(metrics, bottomMm[3])
        home?.setSearchBarClearance(bottomClearance, bottomChrome)
        host.applySectionMargins(home, topMm, metrics, imeOffset, systemTop)
    }

    override fun onAppearanceChanged() {
        home?.refreshAppearance()
    }

    override fun onPause() {
        home?.pause()
    }

    override fun onResume() {
        home?.resume()
    }

    override fun dispose() {
        home?.pause()
        home = null
    }
}
