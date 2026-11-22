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
    private var fetch: TerminalFetchView? = null
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
        // The pane opens with the launcher's own fetch; the sessions follow below it.
        val fetch = TerminalFetchView(host.context, apps = { runCatching { host.pack.appsManager.shownApps().size }.getOrDefault(0) }, now = ::nowLines)
        this.fetch = fetch
        home.setHeader(fetch)
        // Search results open above the bar and grow the tray; the pane gives up that room instead of being dimmed.
        tray.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) host.applyDisplayMargins()
        }
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
        // The results sit above the bar at their own height, so the tray only grows by what they need.
        host.root?.findViewById<View>(R.id.search_mode_results_scroll)?.let { results ->
            results.visibility = if (searchOpen) View.VISIBLE else View.GONE
            (results.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT || params.weight != 0f) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT; params.weight = 0f
                    results.layoutParams = params
                }
            }
        }
        (tray.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                tray.layoutParams = params
            }
        }
    }

    override fun finishSearchLayout() {
        val terminalFocused = !searchOpen && host.keyboardUp && home?.hasTerminalFocus == true
        host.root?.findViewById<View>(R.id.search_mode_tabs)?.visibility = if (terminalFocused) View.GONE else View.VISIBLE
        host.root?.findViewById<View>(R.id.input_group)?.visibility = if (terminalFocused) View.GONE else View.VISIBLE
    }

    override fun onDisplayMargins(topMm: IntArray, bottomMm: IntArray, metrics: DisplayMetrics?, imeOffset: Int, searchInset: Int, systemTop: Int) {
        // The whole tray (bar plus any results above it) is room the pane gives up.
        val bottomChrome = host.tray?.height ?: host.inputBar?.height ?: 0
        val bottomClearance = bottomChrome + searchInset + Tuils.mmToPx(metrics, bottomMm[3])
        home?.setSearchBarClearance(bottomClearance, bottomChrome, push = searchOpen)
        host.applySectionMargins(home, topMm, metrics, imeOffset, systemTop)
    }

    override fun onAppearanceChanged() {
        home?.refreshAppearance()
        fetch?.refresh()
    }

    override fun onDataChanged() {
        fetch?.let { view -> host.onMain { view.refreshLive() } }
    }

    /** The live lines under the fetch: the time and date, the weather, what's playing, the next alarm. */
    private fun nowLines(): List<Pair<String, String>> {
        val context = host.context
        val now = java.util.Date()
        val twentyFour = android.text.format.DateFormat.is24HourFormat(context)
        val lines = ArrayList<Pair<String, String>>()
        lines += java.text.SimpleDateFormat(if (twentyFour) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(now) to
            java.text.SimpleDateFormat("EEE d MMM yyyy", java.util.Locale.getDefault()).format(now)
        val weather = listOfNotNull(host.weatherDetails?.trim()?.takeIf { it.isNotEmpty() },
            host.weatherCondition?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" · ")
        if (weather.isNotEmpty()) lines += (host.weatherSymbol?.takeIf { it.isNotBlank() } ?: "~") to weather
        host.music?.let { music -> lines += (if (music.playing) "▶" else "❚❚") to listOfNotNull(music.title, music.artist).joinToString(" · ") }
        runCatching { (context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).nextAlarmClock?.triggerTime }
            .getOrNull()?.let { at ->
                lines += context.getString(R.string.term_now_alarm) to
                    java.text.SimpleDateFormat(if (twentyFour) "EEE HH:mm" else "EEE h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at))
            }
        return lines
    }

    override fun onPause() {
        home?.pause()
    }

    override fun onResume() {
        fetch?.refresh()
        home?.resume()
    }

    override fun dispose() {
        home?.pause()
        home = null
    }
}
