package ohi.andre.consolelauncher.irc

import android.content.Context
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.help.HelpWindowPanel
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.*

/**
 * Every saved network's connection, one per app process. The pane shows one network at a time; the others keep
 * running. With [stayConnected], [IrcConnectionService] keeps them open after the pane closes.
 */
class IrcTerminalSession private constructor(private val application: Application) {
    companion object {
        @Volatile private var instance: IrcTerminalSession? = null
        fun get(context: Context): IrcTerminalSession = instance ?: synchronized(this) {
            instance ?: IrcTerminalSession(context.applicationContext as Application).also { instance = it }
        }
        /** Tests start from the saved networks again, as a fresh app process would. */
        internal fun reset() = synchronized(this) { instance?.let { it.suspendAll(); it.stopWatching() }; instance = null }
    }
    val devices = IrcDeviceKey(application)
    private val store = IrcNetworkStore(java.io.File(application.noBackupFilesDir, "irc-networks.json"))
    val connections = mutableListOf<IrcConnection>()
    var active: IrcConnection; private set
    val state get() = active.state
    val navigation = IrcNavigation { active.state }
    val actions get() = active.actions
    internal var connector: ((String, Int) -> java.net.Socket)? = null
    // The open pane redraws through [changed]; the background service updates its notification through [service].
    var changed: (() -> Unit)? = null
    var service: (() -> Unit)? = null
    // Mentions and private messages while the pane is closed, for the service to notify.
    var mentioned: ((IrcConnection, IrcState.Buffer, IrcState.Line) -> Unit)? = null
    private fun dispatch() { changed?.invoke(); service?.invoke(); syncService() }
    var initialized = false
    /** Set once after upgrading from a build that used the retired Re:TUI server. */
    var retiredServer = false
    init {
        retiredServer = runCatching { devices.clearLegacy() }.getOrDefault(false)
        store.load().forEach { connections.add(connection(it).apply { stored = true }) }
        // First run: Libera.Chat is offered, and saved once it is actually used.
        if (connections.isEmpty()) connections.add(connection(IrcNetwork.PRESETS.first()))
        // A network never connected has no nickname yet; use the one chosen elsewhere.
        connections.firstNotNullOfOrNull { it.network.nickname.ifEmpty { null } }?.let { known ->
            connections.filter { it.network.nickname.isEmpty() }.forEach { it.state.nickname = known }
        }
        active = connections.first()
    }
    private fun connection(network: IrcNetwork) = IrcConnection(application, network, devices, { connector }, ::dispatch, ::persist).also { c ->
        c.state.onMention = { buffer, line -> if (!visible) mentioned?.invoke(c, buffer, line) }
        c.away = !visible && stayConnected
    }
    private fun persist() { runCatching { store.save(connections.filter { it.stored }.map { it.network }) } }
    private fun keep(connection: IrcConnection) { if (!connection.stored) { connection.stored = true; persist() } }

    // The active network, as the pane and its commands see it.
    var host: String get() = active.host; set(value) { active.host = value }
    var port: Int get() = active.port; set(value) { active.port = value }
    var account: String get() = active.account; set(value) { active.account = value }
    var rememberDevice: Boolean get() = active.rememberDevice; set(value) { active.rememberDevice = value }
    val deviceStatus get() = active.deviceStatus
    val connectionText get() = active.connectionText
    val client get() = active.client
    fun connect(password: CharArray = charArrayOf(), external: Boolean = false, register: Boolean = false) {
        keep(active); active.connect(password, external, register)
    }
    fun reconnect() { keep(active); active.reconnect() }
    fun disconnect() = active.disconnect()
    fun forgetDevice() = active.forgetDevice()
    fun send(command: String, parameters: List<String> = emptyList(), body: String? = null) = active.send(command, parameters, body)
    fun say(target: String, body: String) = active.say(target, body)

    /** The pane opened: reconnect networks that were online when it closed and need no password. */
    fun restore() {
        if (initialized) return
        initialized = true
        connections.filter { it.network.connectOnOpen && (it.deviceReady || it.network.account.isEmpty()) }
            .forEach { connection -> runCatching { connection.reconnect() }.onFailure { connection.state.status(it.message ?: "Could not reconnect") } }
    }
    /** The pane closed. Connections end, but each network remembers whether to reconnect. */
    fun suspendAll() { connections.forEach { it.suspend() }; initialized = false; dispatch() }

    private val prefs get() = application.getSharedPreferences("irc_pane", 0)
    /** Keep networks connected after the pane closes, in a foreground service with a notification. */
    var stayConnected: Boolean
        get() = prefs.getBoolean("stay_connected", false)
        set(value) { prefs.edit().putBoolean("stay_connected", value).apply(); syncService() }
    /** Whether the pane is on screen; while it is not, conversations count unread and mentions notify. */
    var visible = false; private set
    /** The pane is on screen again: you are back, and what you open counts as read. */
    fun foreground() {
        visible = true
        connections.forEach { it.away = false; it.state.conversationVisible = it === active && navigation.page == IrcNavigation.Page.CHAT }
        if (state.conversationVisible) state.acknowledge()
        IrcConnectionService.clearMentions(application)
        watch(); syncService()
    }
    /** The pane closed. Staying connected marks you away; otherwise every network disconnects. */
    fun background() {
        visible = false
        if (!stayConnected) { suspendAll(); stopWatching(); return }
        connections.forEach { it.state.conversationVisible = false; it.away = true }
        dispatch()
    }
    /** Ends every connection and the background service (its notification's Disconnect action). */
    fun disconnectAll() { connections.forEach { it.suspend() }; initialized = false; stopWatching(); dispatch() }
    private fun syncService() {
        if (stayConnected && connections.any { it.busy }) IrcConnectionService.start(application)
        else IrcConnectionService.stop(application)
    }
    // The phone coming back online retries waiting networks at once instead of after their back-off.
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private fun watch() {
        if (networkCallback != null || android.os.Build.VERSION.SDK_INT < 24) return
        val manager = application.getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val ui = android.os.Handler(android.os.Looper.getMainLooper())
        networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) { ui.post { connections.forEach { it.networkReturned() } } }
        }.also { callback -> runCatching { manager.registerDefaultNetworkCallback(callback) }.onFailure { networkCallback = null } }
    }
    private fun stopWatching() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { application.getSystemService(android.net.ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }
    fun switchTo(connection: IrcConnection) {
        if (connection === active) return
        state.conversationVisible = false
        active = connection
        state.conversationVisible = navigation.page == IrcNavigation.Page.CHAT
        if (state.conversationVisible) state.acknowledge()
        dispatch()
    }
    fun cycle(forward: Boolean) {
        val index = connections.indexOf(active)
        switchTo(connections[(index + if (forward) 1 else connections.size - 1) % connections.size])
    }
    /** Adds a network, or switches to it when it is already saved. An unused first-run network is replaced. */
    fun add(network: IrcNetwork): IrcConnection {
        connections.firstOrNull { it.host.equals(network.host, true) && it.port == network.port }?.let { keep(it); switchTo(it); return it }
        // A new network starts with the nickname you already use elsewhere.
        val connection = connection(network.copy(id = network.id.takeIf { id -> connections.none { it.network.id == id } }
            ?: IrcNetwork.custom(network.host, network.port, connections.map { it.network.id }).id,
            nickname = network.nickname.ifEmpty { state.nickname }))
        val draft = connections.singleOrNull()?.takeIf { !it.stored && it.client == null }
        if (draft != null) connections[0] = connection else connections.add(connection)
        connection.stored = true; persist()
        if (draft != null) { active = connection; dispatch() } else switchTo(connection)
        return connection
    }
    fun remove(connection: IrcConnection) {
        connection.disconnect()
        if (connection.network.device) runCatching { devices.forget(connection.identity) }
        val index = connections.indexOf(connection)
        connections.remove(connection)
        if (connections.isEmpty()) connections.add(connection(IrcNetwork.PRESETS.first()))
        persist()
        active = connections[index.coerceAtMost(connections.lastIndex)]
        dispatch()
    }
    /** Split view: a second conversation shown beside the one you type into, as in the Termux workspace. */
    enum class Split { OFF, AUTO, SIDE, STACK }
    var split = Split.valueOf(application.getSharedPreferences("irc_pane", 0).getString("split", Split.OFF.name) ?: Split.OFF.name)
        set(value) { field = value; prefs.edit().putString("split", value.name).apply() }
    // Which slot the typing conversation fills, so tiles keep their places when focus moves.
    var splitSlot = 0
    var beside: Pair<IrcConnection, String>? = null
    // Which tile a conversation chosen from the list goes to: the typing tile, or the other one.
    var choosingBeside = false
    var previous: Pair<IrcConnection, String>? = null
    fun target(pair: Pair<IrcConnection, String>?): Pair<IrcConnection, IrcState.Buffer>? = pair?.takeIf { it.first in connections }?.let { (connection, name) ->
        connection.state.buffers.firstOrNull { IrcProtocol.fold(it.name, connection.state.mapping) == IrcProtocol.fold(name, connection.state.mapping) }?.let { connection to it }
    }
    /** What the second tile shows: chosen, else the conversation you were in before, else another network's. */
    fun besideTarget(): Pair<IrcConnection, IrcState.Buffer>? {
        val current = active to state.active
        fun usable(candidate: Pair<IrcConnection, IrcState.Buffer>?) = candidate?.takeIf { it.first !== current.first || it.second !== current.second }
        // Unless chosen, a network's status window is not a conversation worth showing.
        fun conversation(candidate: Pair<IrcConnection, IrcState.Buffer>?) = usable(candidate)?.takeIf { it.second.number != 1 }
        return usable(target(beside)) ?: conversation(target(previous))
            ?: connections.filter { it !== active }.firstNotNullOfOrNull { other ->
                conversation(other to other.state.active) ?: other.state.buffers.firstOrNull { it.number != 1 }?.let { conversation(other to it) }
            }
    }
}

/** One transcript, one composer, terminal commands and numbered buffer shortcuts. */
class IrcActivity : LocalizedComponentActivity() {
    companion object { fun open(context: Context) = context.startActivity(Intent(context, IrcActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    internal lateinit var session: IrcTerminalSession
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private lateinit var tiles: LinearLayout
    private lateinit var besideTile: LinearLayout
    private lateinit var mainTile: LinearLayout
    private lateinit var mainLabel: TextView
    private lateinit var besideLabel: TextView
    private lateinit var besideOutput: TextView
    private lateinit var besideScroll: ScrollView
    private var displayedTarget: Pair<IrcConnection, String>? = null
    private lateinit var root: View
    private lateinit var windowPanel: HelpWindowPanel
    private var secretCommand = ""
    private var secretAccount = ""
    private var priorDraft = ""
    private var registerEmail = ""
    private var switching = false
    private var displayedWindow = 1
    private var displayedText = ""
    private var displayedPage = IrcNavigation.Page.CHAT
    private var priorSecure = false
    private var restoringScroll = false
    private val navigation get() = session.navigation
    override fun onCreate(saved: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(saved)
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        session = IrcTerminalSession.get(this)
        // Non-exported debug fixtures must never reconnect a maintainer's real account.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("irc_offline_fixture", false)) session.initialized = true
        val screen = FrameLayout(this).apply { isClickable = true }
        val safe = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val host = FrameLayout(this); safe.addView(host, ViewGroup.LayoutParams(-1, -1))
        root = layoutInflater.inflate(R.layout.irc_surface, host, false)
        windowPanel = HelpWindowPanel(host, root, saved, "irc", root.findViewById(R.id.irc_header), root.findViewById(R.id.irc_header_row), showHeaderReset = false)
        windowPanel.reserveBottomCorners(root.findViewById(R.id.irc_suggestion_scroll))
        input = root.findViewById(R.id.irc_input); output = root.findViewById(R.id.irc_output); scroll = root.findViewById(R.id.irc_scroll)
        tiles = root.findViewById(R.id.irc_tiles)
        // Each tile gets a label row, as in the Termux workspace: ▶ marks the one you type into; tap a label to choose what it shows.
        fun tileLabel() = TextView(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(dp(6), 0, dp(6), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        mainLabel = tileLabel().apply { tag = "irc-main-label"; setOnClickListener { chooseConversation(false) } }
        tiles.removeView(scroll)
        mainTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(mainLabel, LinearLayout.LayoutParams(-1, dp(24)))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        tiles.addView(mainTile, LinearLayout.LayoutParams(-1, 0, 1f))
        besideLabel = tileLabel().apply { tag = "irc-beside-label"; setOnClickListener { chooseConversation(true) } }
        besideOutput = TextView(this).apply { setPadding(dp(6), dp(2), dp(6), dp(6)) }
        // Fill the viewport so a tap below short text still lands on the tile rather than the scroller.
        besideScroll = ScrollView(this).apply { isFillViewport = true; addView(besideOutput, FrameLayout.LayoutParams(-1, -1)) }
        besideTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; tag = "irc-beside"
            addView(besideLabel, LinearLayout.LayoutParams(-1, dp(24)))
            addView(besideScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            // Tapping the second tile makes it the conversation you type into, like focusing a Termux tile.
            isClickable = true; isFocusable = true; contentDescription = getString(R.string.irc_split_focus)
            setOnClickListener { focusBeside() }
        }
        besideOutput.setOnClickListener { focusBeside() }
        tiles.addView(besideTile)
        // A tap on the transcript means "type here"; long-press still selects text to copy.
        val taps = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean { input.post { focusInput() }; return false }
        })
        output.setOnTouchListener { _, event -> taps.onTouchEvent(event); false }
        scroll.setOnTouchListener { _, event -> taps.onTouchEvent(event); false }
        input.isSaveEnabled = false
        input.setOnKeyListener { _, key, event ->
            when {
                key == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed -> { if (event.action == KeyEvent.ACTION_UP) submit(); true }
                event.action == KeyEvent.ACTION_DOWN && event.isAltPressed && key in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> { switchWindow((key - KeyEvent.KEYCODE_1 + 1).toString()); true }
                event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && key == KeyEvent.KEYCODE_TAB -> { switchWindow(if (event.isShiftPressed) "prev" else "next"); true }
                event.action == KeyEvent.ACTION_DOWN && event.isAltPressed && key in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT) -> { switchNetwork { session.cycle(key == KeyEvent.KEYCODE_DPAD_RIGHT) }; true }
                else -> false
            }
        }
        input.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { submit(); true } else false }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!switching && secretCommand.isEmpty()) {
                    val text = s.toString()
                    if (navigation.page == IrcNavigation.Page.CHAT) { if (!text.startsWith('/') || text.startsWith("//")) session.state.active.draft = text }
                    else { navigation.text = text; navigation.limit = 50 }
                    suggestions(); if (navigation.page != IrcNavigation.Page.CHAT) renderNavigation()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        root.findViewById<View>(R.id.irc_send).setOnClickListener { submit() }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout() or androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        style(); setContentView(screen)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (secretCommand.isNotEmpty()) cancelSecret() else if (navigation.page != IrcNavigation.Page.CHAT) closeNavigation() else { if (!session.stayConnected) session.suspendAll(); finish() } }
        })
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> if (!restoringScroll) { if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.scroll = y else navigation.scroll = y } }
        restoringScroll = true
        restoreDraft(); render()
        scroll.post { scroll.scrollTo(0, if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.scroll else navigation.scroll); restoringScroll = false }
    }
    override fun onStart() {
        super.onStart(); session.changed = { render() }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("irc_offline_fixture", false)) session.initialized = true
        openMention(intent)
        session.foreground()
        action { session.restore() }; render()
    }
    override fun onNewIntent(next: Intent) {
        super.onNewIntent(next); setIntent(next)
        if (openMention(next)) { session.foreground(); render() }
    }
    private val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) session.state.status(getString(R.string.irc_stay_connected_no_notifications))
    }
    private fun setStayConnected(on: Boolean) {
        session.stayConnected = on
        session.state.status(getString(if (on) R.string.irc_stay_connected_on else R.string.irc_stay_connected_off))
        if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    /** A mention notification names the network and conversation to open. */
    private fun openMention(from: Intent): Boolean {
        val id = from.getStringExtra(IrcConnectionService.EXTRA_NETWORK) ?: return false
        from.removeExtra(IrcConnectionService.EXTRA_NETWORK)
        val connection = session.connections.firstOrNull { it.network.id == id } ?: return false
        if (navigation.page != IrcNavigation.Page.CHAT) navigation.close()
        session.switchTo(connection)
        from.getStringExtra(IrcConnectionService.EXTRA_BUFFER)?.let { runCatching { connection.state.switch(it) } }
        return true
    }
    override fun onPause() { windowPanel.persist(); super.onPause() }
    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) { style(); render() }
    }
    override fun onStop() {
        cancelSecret(); session.changed = null
        if (!isChangingConfigurations && navigation.page in listOf(IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER)) navigation.close()
        if (!isChangingConfigurations) session.background()
        super.onStop()
    }
    override fun onSaveInstanceState(out: Bundle) { windowPanel.save(out); super.onSaveInstanceState(out) }
    private fun dp(value: Int) = Tuils.dpToPx(this, value)
    private fun style() {
        fun text(view: View) { if (view is TextView) { view.typeface = Tuils.getTypeface(this); view.textSize = LauncherFontScale.terminalTextSizeSp(); view.setTextColor(ThemeColorResolver.color(Theme.workspace_panel_text_color)) }; if (view is ViewGroup) for (i in 0 until view.childCount) text(view.getChildAt(i)) }
        text(root)
        root.findViewById<View>(R.id.irc_panel).background = TerminalBorderRuntime.panelDrawable(this, ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color), 1.5f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        root.findViewById<View>(R.id.irc_main_pane).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_output_background_color),
            ThemeColorResolver.withMaxAlpha(ThemeColorResolver.color(Theme.workspace_panel_border_color), 210),
            1.2f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        val header = root.findViewById<TextView>(R.id.irc_header)
        header.setTypeface(Tuils.getTypeface(this), android.graphics.Typeface.BOLD)
        header.includeFontPadding = false; header.textSize = AppearanceSettings.outputHeaderTextSize().toFloat()
        header.background = TerminalBorderRuntime.tabDrawable(this, ThemeColorResolver.color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(root.findViewById(R.id.irc_panel), header)
        input.background = TerminalBorderRuntime.panelDrawable(this, ThemeColorResolver.color(Theme.workspace_input_background_color), AppearanceSettings.surfaceBorderColor(SurfaceBorder.INPUT), 1.5f, AppearanceSettings.dashedBorderCornerRadius(), AppearanceSettings.dashedBorders(), borderEnabled = AppearanceSettings.surfaceBorderEnabled(SurfaceBorder.INPUT), target = FrameTarget.INPUT)
        input.setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
        input.setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
        Tuils.setCursorDrawableColor(input, ThemeColorResolver.color(Theme.cursor_color))
        input.setPadding(dp(8), dp(4), dp(8), dp(4))
        windowPanel.refreshTheme()
    }
    private fun chip(label: String, hold: (() -> Unit)? = null, action: () -> Unit): View {
        val spaces = SuggestionChipStyler.spacing()
        val chip = SuggestionChipStyler.createView(this, spaces).apply { text = label; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        SuggestionChipStyler(this).apply(chip, SuggestionsManager.Suggestion.TYPE_COMMAND, null)
        return FrameLayout(this).apply {
            minimumHeight = dp(48); minimumWidth = dp(48); isClickable = true; isFocusable = true; contentDescription = label
            // Wraps the chip so a large launcher font is never clipped; 48dp stays the minimum tap target.
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(spaces[0], 0, spaces[0], 0) }
            addView(chip, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER)); setOnClickListener { action() }
            if (hold != null) setOnLongClickListener { hold(); true }
        }
    }
    private fun suggestions() {
        val rows = root.findViewById<LinearLayout>(R.id.irc_suggestions); rows.removeAllViews()
        val guidance = root.findViewById<TextView>(R.id.irc_guidance)
        guidance.visibility = View.GONE
        if (secretCommand.isNotEmpty()) { rows.addView(chip(getString(R.string.irc_cancel)) { cancelSecret() }); return }
        val text = input.text.toString()
        if (navigation.page == IrcNavigation.Page.CHAT && text.startsWith('/') && !text.startsWith("//")) {
            val prefix = text.substringBefore(' ').lowercase(java.util.Locale.ROOT)
            val reference = resources.getStringArray(R.array.irc_commands).filter { it.startsWith('/') }
            reference.filter { it.substringBefore(' ').startsWith(prefix) }.take(6).forEach { entry ->
                val command = entry.substringBefore(' ')
                rows.addView(chip(command) { input.setText("$command "); input.setSelection(input.length()); input.requestFocus() })
            }
            guidance.text = reference.firstOrNull { it.substringBefore(' ') == prefix } ?: getString(R.string.irc_command_guidance)
            guidance.visibility = View.VISIBLE
        } else if (navigation.page == IrcNavigation.Page.CHAT && session.client?.ready == true && text.isNotEmpty() &&
            text.substring(0, input.selectionStart.coerceIn(0, text.length)).substringAfterLast(" ").startsWith("@")) {
            val cursor = input.selectionStart.coerceIn(0, text.length)
            val tokenStart = text.substring(0, cursor).indexOfLast { it.isWhitespace() } + 1
            val token = text.substring(tokenStart, cursor)
            if (token.startsWith("@")) {
                val query = token.drop(1)
                session.state.active.names.asSequence()
                    .filter { query.isEmpty() || IrcProtocol.fold(it, session.state.mapping).startsWith(IrcProtocol.fold(query, session.state.mapping)) }
                    .toList().sortedWith(String.CASE_INSENSITIVE_ORDER)
                    .take(6)
                    .forEach { nickname ->
                        rows.addView(chip("@$nickname") {
                            val current = input.text.toString()
                            val end = input.selectionStart.coerceIn(0, current.length)
                            val start = current.substring(0, end).indexOfLast { it.isWhitespace() } + 1
                            val replacement = current.substring(0, start) + "@$nickname " + current.substring(end)
                            switching = true
                            input.setText(replacement)
                            input.setSelection((start + nickname.length + 2).coerceAtMost(replacement.length))
                            switching = false
                            suggestions()
                        })
                    }
            }
        } else if (navigation.page != IrcNavigation.Page.CHAT) {
            rows.addView(chip(getString(R.string.irc_back)) { closeNavigation() })
            if (navigation.page in listOf(IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER, IrcNavigation.Page.JOIN, IrcNavigation.Page.NETWORK, IrcNavigation.Page.ADD_NETWORK, IrcNavigation.Page.NICKNAME))
                rows.addView(chip(getString(R.string.irc_continue)) { submit() })
        } else if (session.client?.ready == true) {
            rows.addView(chip(getString(R.string.irc_channels)) { openNavigation(IrcNavigation.Page.CHANNELS) })
            rows.addView(chip(getString(R.string.irc_people)) { openPeople() })
            rows.addView(chip(getString(R.string.irc_help_title)) { openNavigation(IrcNavigation.Page.HELP) })
            rows.addView(chip(getString(R.string.irc_more)) { openNavigation(IrcNavigation.Page.MORE) })
        } else {
            if (session.active.deviceReady) rows.addView(chip(getString(R.string.irc_reconnect)) { action { session.reconnect() } })
            else rows.addView(chip(getString(R.string.irc_connect_guest)) { action { session.connect() } })
            rows.addView(chip(getString(R.string.irc_sign_in)) { openNavigation(IrcNavigation.Page.ACCOUNT) })
            rows.addView(chip(getString(R.string.irc_networks)) { openNavigation(IrcNavigation.Page.NETWORKS) })
            rows.addView(chip(getString(R.string.irc_help_title)) { openNavigation(IrcNavigation.Page.HELP) })
        }
    }
    private fun restoreDraft() { switching = true; input.setText(if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.draft else navigation.text); input.setSelection(input.length()); switching = false; suggestions() }
    private fun openNavigation(page: IrcNavigation.Page) {
        if (secretCommand.isNotEmpty()) return
        // A new account is remembered unless the user opts out; existing sign-ins opt in.
        if (page == IrcNavigation.Page.REGISTER) session.rememberDevice = true
        if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.scroll = scroll.scrollY
        navigation.open(page); input.error = null; restoreDraft(); render(); scroll.post { scroll.scrollTo(0, 0) }
    }
    private fun closeNavigation() {
        restoringScroll = true
        navigation.close(); input.error = null; restoreDraft(); render()
        scroll.post { scroll.scrollTo(0, session.state.active.scroll); restoringScroll = false }
    }
    private fun action(block: () -> Unit) { runCatching(block).onFailure { input.error = it.message ?: getString(R.string.irc_action_failed) }; render() }
    private fun joinChannel(name: String) = action {
        session.actions.join(name); if (session.state.active.name == name && session.state.active.joined) closeNavigation()
    }
    private fun openPeople() = action { openNavigation(IrcNavigation.Page.PEOPLE); session.actions.names() }
    private fun openDeleteMessages() = action {
        check(session.client?.ready == true && session.client?.messageRedaction == true && session.state.active.joined) { getString(R.string.irc_delete_unavailable) }
        openNavigation(IrcNavigation.Page.DELETE_MESSAGES)
    }
    /** Switches network, keeping each network's reading position and draft. */
    private fun switchNetwork(change: () -> Unit) {
        if (secretCommand.isNotEmpty()) return
        action {
            if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.scroll = scroll.scrollY
            restoringScroll = true; navigation.close(); change(); displayedWindow = -1; restoreDraft(); render()
            scroll.post { scroll.scrollTo(0, session.state.active.scroll); restoringScroll = false }
        }
    }
    private fun switchWindow(value: String) {
        if (secretCommand.isNotEmpty()) return
        action { if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.scroll = scroll.scrollY; restoringScroll = true; navigation.close(); session.state.switch(value); restoreDraft(); render(); scroll.post { scroll.scrollTo(0, session.state.active.scroll); restoringScroll = false } }
    }
    private fun render() {
        val state = session.state; val active = state.active
        val changedBuffer = displayedWindow != active.number
        if (changedBuffer && active.joined && secretCommand.isEmpty() && navigation.page in listOf(IrcNavigation.Page.CHANNELS, IrcNavigation.Page.DIRECTORY, IrcNavigation.Page.JOIN)) navigation.close()
        if (changedBuffer && secretCommand.isEmpty()) restoreDraft()
        val bottom = scroll.getChildAt(0)?.let { it.height - scroll.scrollY - scroll.height < dp(32) } ?: true
        val oldY = if (changedBuffer) active.scroll else scroll.scrollY
        val title = if (active.number == 1) session.active.network.name else active.name
        root.findViewById<TextView>(R.id.irc_context).text = title + if (active.number != 1 && !active.name.startsWith('#') && !active.name.startsWith('&')) " · ${getString(R.string.irc_private)}" else active.topic.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
        root.findViewById<TextView>(R.id.irc_status).text = when {
            secretCommand.isNotEmpty() -> getString(R.string.irc_password_context, secretAccount)
            state.joining.isNotEmpty() -> getString(R.string.irc_joining, state.joining)
            state.joinError.isNotEmpty() -> state.joinError
            else -> "${state.nickname} · ${if (active.number == 1) session.connectionText else "${session.active.network.name} · ${session.connectionText}"}" + session.deviceStatus.takeIf { it.isNotEmpty() }?.let { "\n$it" }.orEmpty()
        }
        val searchable = navigation.page in listOf(IrcNavigation.Page.CHANNELS, IrcNavigation.Page.DIRECTORY, IrcNavigation.Page.PEOPLE, IrcNavigation.Page.COMMANDS, IrcNavigation.Page.DELETE_MESSAGES, IrcNavigation.Page.CONVERSATIONS)
        val editable = secretCommand.isNotEmpty() || searchable || navigation.page in listOf(IrcNavigation.Page.CHAT, IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER, IrcNavigation.Page.NETWORK, IrcNavigation.Page.JOIN, IrcNavigation.Page.ADD_NETWORK, IrcNavigation.Page.NICKNAME)
        input.isEnabled = editable
        root.findViewById<View>(R.id.irc_prompt_row).visibility = if (editable) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.irc_send).isEnabled = editable && !searchable
        root.findViewById<TextView>(R.id.irc_prompt).text = when {
            secretCommand.isNotEmpty() -> "password> "
            navigation.page == IrcNavigation.Page.CHAT -> "> "
            navigation.page in listOf(IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER) -> "account> "
            navigation.page in listOf(IrcNavigation.Page.NETWORK, IrcNavigation.Page.ADD_NETWORK) -> "server> "
            navigation.page == IrcNavigation.Page.NICKNAME -> "nick> "
            navigation.page == IrcNavigation.Page.JOIN -> "join> "
            else -> if (searchable) "find> " else "nav> "
        }
        if (secretCommand.isEmpty()) input.hint = getString(when (navigation.page) {
            IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER -> R.string.irc_account_hint
            IrcNavigation.Page.NETWORK, IrcNavigation.Page.ADD_NETWORK -> R.string.irc_network_hint
            IrcNavigation.Page.NICKNAME -> R.string.irc_nickname_hint
            IrcNavigation.Page.JOIN -> R.string.irc_join_hint
            IrcNavigation.Page.CHAT -> R.string.irc_input_hint
            else -> if (searchable) R.string.irc_search_hint else R.string.irc_input_hint
        })
        val transcript = active.lines.joinToString("\n") { it.text }
        val pageChanged = displayedPage != navigation.page
        val showTranscript = navigation.page == IrcNavigation.Page.CHAT && active.number != 1
        output.visibility = if (showTranscript) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.irc_navigation).visibility = if (showTranscript) View.GONE else View.VISIBLE
        if (showTranscript && (transcript != displayedText || changedBuffer || pageChanged)) {
            output.text = styled(active.lines)
            scroll.post { if (navigation.page == IrcNavigation.Page.CHAT) scroll.scrollTo(0, if (!changedBuffer && !pageChanged && bottom) scroll.getChildAt(0).height else if (pageChanged) active.scroll else oldY) }
        }
        displayedText = transcript; displayedWindow = active.number; displayedPage = navigation.page
        val shown = session.active to active.name
        displayedTarget?.let { before -> if ((before.first !== shown.first || before.second != shown.second) && before.second != "status") session.previous = before }
        displayedTarget = shown
        renderSplit()
        if (!showTranscript) renderNavigation()
        val windows = root.findViewById<LinearLayout>(R.id.irc_windows); windows.removeAllViews()
        // The first chip names this network; its marker counts unread messages on the others.
        val others = session.connections.filter { it !== session.active }
        val elsewhere = others.sumOf { it.unread }
        val networkMarker = if (others.any { it.highlighted }) " !$elsewhere" else if (elsewhere > 0) " +$elsewhere" else ""
        windows.addView(chip("⇄ ${session.active.network.name}$networkMarker") { openNavigation(IrcNavigation.Page.NETWORKS) }.apply {
            contentDescription = getString(R.string.irc_networks_chip, session.active.network.name)
        })
        state.buffers.filter { it.number != 1 }.forEach { b ->
            val marker = if (b.highlight) " !${b.unread}" else if (b.unread > 0) " +${b.unread}" else ""
            windows.addView(chip("${if (b.number == state.current) "[" else ""}${b.name}$marker${if (b.number == state.current) "]" else ""}",
                hold = { showBeside(session.active, b.name) }) { switchWindow(b.number.toString()) })
        }
        root.findViewById<View>(R.id.irc_window_scroll).visibility = View.VISIBLE
        suggestions()
    }
    private fun styled(lines: List<IrcState.Line>): SpannableStringBuilder {
        val styled = SpannableStringBuilder()
        lines.forEachIndexed { i, line ->
            if (i > 0) styled.append('\n')
            val start = styled.length; styled.append(line.text)
            if (line.sender.isNotEmpty()) {
                val end = if (line.text.startsWith('<')) line.text.indexOf('>').let { if (it >= 0) it + 1 else 0 } else 0
                if (end > 0) styled.setSpan(ForegroundColorSpan(ThemeColorResolver.color(Theme.workspace_control_text_color)), start, start + end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (!line.text.contains("rejected", true) && !line.text.contains("uncertain", true)) {
                styled.setSpan(ForegroundColorSpan(ThemeColorResolver.color(Theme.muted_text_color)), start, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return styled
    }
    private fun layoutSide() = when (session.split) {
        IrcTerminalSession.Split.SIDE -> true
        IrcTerminalSession.Split.AUTO -> resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        else -> false
    }
    private fun conversationName(connection: IrcConnection, buffer: IrcState.Buffer) =
        "${connection.network.name} · ${if (buffer.number == 1) getString(R.string.irc_split_status) else buffer.name}"
    private fun styleLabel(label: TextView, focused: Boolean) {
        label.typeface = Tuils.getTypeface(this); label.textSize = LauncherFontScale.terminalTextSizeSp() * 0.85f
        label.setTextColor(ThemeColorResolver.color(if (focused) Theme.selection_text_color else Theme.workspace_panel_text_color))
        label.setBackgroundColor(ThemeColorResolver.color(if (focused) Theme.selection_background_color else Theme.workspace_header_background_color))
    }
    /** Lays out the tiles and fills the second one; it reads like any open conversation, so it counts as read. */
    private fun renderSplit() {
        val on = session.split != IrcTerminalSession.Split.OFF
        besideTile.visibility = if (on) View.VISIBLE else View.GONE
        mainLabel.visibility = if (on) View.VISIBLE else View.GONE
        // Tile labels carry the names, so the context line above would only repeat the focused one.
        root.findViewById<View>(R.id.irc_context).visibility = if (on) View.GONE else View.VISIBLE
        tiles.orientation = if (layoutSide()) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val gap = dp(6)
        val horizontal = tiles.orientation == LinearLayout.HORIZONTAL
        val first = if (session.splitSlot == 0) mainTile else besideTile
        if (tiles.getChildAt(0) !== first) { tiles.removeView(first); tiles.addView(first, 0) }
        for (i in 0 until tiles.childCount) tiles.getChildAt(i).layoutParams = (if (horizontal) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f)).apply {
            if (on && i == 1) { if (horizontal) leftMargin = gap else topMargin = gap }
        }
        if (!on) return
        styleLabel(mainLabel, true); styleLabel(besideLabel, false)
        mainLabel.text = "▶ " + conversationName(session.active, session.state.active)
        mainLabel.contentDescription = getString(R.string.irc_split_main_label, mainLabel.text.drop(2))
        besideOutput.setTextColor(ThemeColorResolver.color(Theme.workspace_panel_text_color))
        besideOutput.typeface = Tuils.getTypeface(this); besideOutput.textSize = LauncherFontScale.terminalTextSizeSp()
        besideTile.background = null
        val target = session.besideTarget()
        if (target == null) {
            besideLabel.text = "  " + getString(R.string.irc_split_title)
            besideOutput.text = getString(R.string.irc_split_empty)
            return
        }
        val (connection, buffer) = target
        session.beside = connection to buffer.name
        buffer.unread = 0; buffer.highlight = false
        besideLabel.text = "  " + conversationName(connection, buffer)
        besideLabel.contentDescription = getString(R.string.irc_split_beside_label, conversationName(connection, buffer))
        val atBottom = besideScroll.getChildAt(0)?.let { it.height - besideScroll.scrollY - besideScroll.height < dp(32) } ?: true
        besideOutput.text = styled(buffer.lines)
        if (atBottom) besideScroll.post { besideScroll.scrollTo(0, besideOutput.height) }
    }
    /** Like the Termux window chooser: lists every open conversation on every network for one tile. */
    private fun chooseConversation(beside: Boolean) {
        session.choosingBeside = beside
        openNavigation(IrcNavigation.Page.CONVERSATIONS)
    }
    private fun showBeside(connection: IrcConnection, name: String) {
        if (connection === session.active && IrcProtocol.fold(name, connection.state.mapping) == IrcProtocol.fold(session.state.active.name, connection.state.mapping)) return
        session.beside = connection to name
        if (session.split == IrcTerminalSession.Split.OFF) session.split = IrcTerminalSession.Split.AUTO
        render()
    }
    /** The second tile becomes the conversation you type into; the one you left takes its place. */
    private fun focusBeside() {
        val (connection, buffer) = session.besideTarget() ?: return
        val left = session.active to session.state.active.name
        session.splitSlot = 1 - session.splitSlot
        switchNetwork { session.switchTo(connection); connection.state.switch(buffer.number.toString()) }
        session.beside = left
        render(); focusInput()
    }
    private fun focusInput() {
        if (!input.requestFocus()) return
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input, 0)
    }
    private fun renderNavigation() {
        val rows = root.findViewById<LinearLayout>(R.id.irc_navigation); rows.removeAllViews()
        fun line(value: String, click: (() -> Unit)? = null) {
            rows.addView(TextView(this).apply {
                text = if (click == null) value else "> $value"
                typeface = Tuils.getTypeface(this@IrcActivity); textSize = LauncherFontScale.terminalTextSizeSp()
                setTextColor(ThemeColorResolver.color(if (click == null) Theme.workspace_panel_text_color else Theme.workspace_control_text_color))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                if (click != null) {
                    minHeight = dp(48); gravity = android.view.Gravity.CENTER_VERTICAL; isFocusable = true; contentDescription = value
                    isEnabled = secretCommand.isEmpty(); setOnClickListener { if (secretCommand.isEmpty()) action(click) }
                }
            })
        }
        fun label(id: Int) = getString(id)
        fun more(total: Int) { if (total > navigation.limit) line(label(R.string.irc_show_more)) { navigation.limit = (navigation.limit + 50).coerceAtMost(1000); renderNavigation() } }
        val state = session.state
        when (navigation.page) {
            IrcNavigation.Page.CHAT -> {
                // The network's home: who you are here and how to connect.
                val connection = session.active
                if (session.retiredServer) line(label(R.string.irc_retired_server))
                line("${connection.host}:${connection.port} · TLS")
                if (connection.ready) {
                    // Open conversations are on the chips below; these start new ones.
                    line(label(R.string.irc_join_name)) { openNavigation(IrcNavigation.Page.JOIN) }
                    line(label(R.string.irc_browse)) { session.actions.directory(); openNavigation(IrcNavigation.Page.DIRECTORY) }
                    if (connection.network.channels.isNotEmpty()) line(getString(R.string.irc_rejoin_list, connection.network.channels.joinToString(" ")))
                    line(label(R.string.irc_change_nickname)) { openNavigation(IrcNavigation.Page.NICKNAME) }
                    line(label(R.string.irc_disconnect)) { session.disconnect() }
                } else {
                    line(label(R.string.irc_welcome))
                    if (connection.deviceReady) line(getString(R.string.irc_reconnect_as, connection.account)) { session.reconnect() }
                    line(getString(R.string.irc_connect_as, state.nickname)) { session.connect() }
                    line(label(R.string.irc_change_nickname)) { openNavigation(IrcNavigation.Page.NICKNAME) }
                    line(label(R.string.irc_sign_in)) { openNavigation(IrcNavigation.Page.ACCOUNT) }
                    line(label(R.string.irc_create_account)) { openNavigation(IrcNavigation.Page.REGISTER) }
                    if (connection.network.channels.isNotEmpty()) line(getString(R.string.irc_rejoin_list, connection.network.channels.joinToString(" ")))
                }
                line(label(R.string.irc_networks)) { openNavigation(IrcNavigation.Page.NETWORKS) }
                line(label(R.string.irc_help_title)) { openNavigation(IrcNavigation.Page.HELP) }
            }
            IrcNavigation.Page.NETWORKS -> {
                line(label(R.string.irc_networks))
                session.connections.forEach { connection ->
                    val status = if (connection.ready) label(R.string.irc_network_online) else if (connection.client != null) label(R.string.irc_network_connecting) else label(R.string.irc_network_offline)
                    val marker = if (connection.highlighted) " · !${connection.unread}" else if (connection.unread > 0) " · +${connection.unread}" else ""
                    val current = if (connection === session.active) "[${connection.network.name}]" else connection.network.name
                    line("$current · $status$marker") {
                        // The current network opens its home; another network opens where you left it.
                        if (connection === session.active) { state.switch("1"); closeNavigation() } else switchNetwork { session.switchTo(connection) }
                    }
                }
                line(label(R.string.irc_add_network)) { openNavigation(IrcNavigation.Page.ADD_NETWORK) }
                line((if (session.stayConnected) "[✓] " else "[ ] ") + label(R.string.irc_stay_connected)) { setStayConnected(!session.stayConnected); renderNavigation() }
                line(label(R.string.irc_stay_connected_explanation))
                if (session.connections.size > 1 || session.active.stored) line(getString(R.string.irc_remove_network, session.active.network.name)) { openNavigation(IrcNavigation.Page.REMOVE_NETWORK) }
            }
            IrcNavigation.Page.ADD_NETWORK -> {
                line(label(R.string.irc_add_network)); line(label(R.string.irc_add_network_explanation))
                IrcNetwork.PRESETS.filter { preset -> session.connections.none { it.stored && it.host.equals(preset.host, true) } && navigation.matches(preset.name + " " + preset.host) }
                    .forEach { preset -> line("${preset.name} · ${preset.host}") { switchNetwork { session.add(preset) } } }
            }
            IrcNavigation.Page.NICKNAME -> { line(label(R.string.irc_change_nickname)); line(getString(R.string.irc_nickname_explanation, state.nickname)) }
            IrcNavigation.Page.CONVERSATIONS -> {
                line(label(if (session.choosingBeside) R.string.irc_split_choose_beside else R.string.irc_split_choose_main))
                session.connections.forEach { connection ->
                    connection.state.buffers.filter { it.number == 1 || it.joined || !(it.name.startsWith('#') || it.name.startsWith('&')) }
                        .filter { navigation.matches(conversationName(connection, it)) }.forEach { buffer ->
                            line(conversationName(connection, buffer)) {
                                if (session.choosingBeside) { closeNavigation(); showBeside(connection, buffer.name) }
                                else switchNetwork { session.switchTo(connection); connection.state.switch(buffer.number.toString()) }
                            }
                        }
                }
            }
            IrcNavigation.Page.REMOVE_NETWORK -> {
                line(getString(R.string.irc_remove_network_question, session.active.network.name))
                line(getString(R.string.irc_remove_network, session.active.network.name)) { switchNetwork { session.remove(session.active) } }
                line(label(R.string.irc_cancel)) { closeNavigation() }
            }
            IrcNavigation.Page.CHANNELS -> {
                line(label(R.string.irc_channels))
                val open = state.buffers.filter { it.number != 1 && navigation.matches(it.name) }
                open.forEach { b -> line(b.name + if (b.unread > 0) " · ${b.unread}${if (b.highlight) " !" else ""}" else "") {
                    if ((b.name.startsWith('#') || b.name.startsWith('&')) && !b.joined) joinChannel(b.name) else switchWindow(b.number.toString())
                } }
                // Channels this network rejoins, when they are not open.
                session.active.network.channels.filter { channel -> navigation.matches(channel) && state.buffers.none { IrcProtocol.fold(it.name, state.mapping) == IrcProtocol.fold(channel, state.mapping) } }.forEach { channel -> line(channel) { joinChannel(channel) } }
                line(label(R.string.irc_browse)) { session.actions.directory(); openNavigation(IrcNavigation.Page.DIRECTORY) }
                line(label(R.string.irc_join_name)) { openNavigation(IrcNavigation.Page.JOIN) }
            }
            IrcNavigation.Page.DIRECTORY -> {
                line(label(R.string.irc_directory))
                if (state.directoryLoading) line(label(R.string.irc_loading))
                if (state.directoryError.isNotEmpty()) line(state.directoryError)
                val channels = state.directory.filter { navigation.matches(it.name + " " + it.topic) }
                channels.take(navigation.limit).forEach { channel -> line("${channel.name} · ${channel.users}" + channel.topic.takeIf { it.isNotEmpty() }?.let { "\n$it" }.orEmpty()) { joinChannel(channel.name) } }
                more(channels.size)
                if (channels.isEmpty() && !state.directoryLoading) line(label(R.string.irc_no_matches))
                if (state.directoryTruncated) line(label(R.string.irc_directory_limit))
                line(label(R.string.irc_refresh)) { session.actions.directory() }
                line(label(R.string.irc_join_name)) { openNavigation(IrcNavigation.Page.JOIN) }
            }
            IrcNavigation.Page.PEOPLE -> {
                line(label(R.string.irc_people))
                if (!state.active.joined) line(label(R.string.irc_people_requires_channel))
                if (state.active.namesLoading) line(label(R.string.irc_loading))
                if (state.active.namesError.isNotEmpty()) line(state.active.namesError)
                val people = state.active.names.sorted().filter { navigation.matches(it) }
                people.take(navigation.limit).forEach { nickname -> line(nickname) { session.actions.query(nickname); closeNavigation() } }
                more(people.size)
                if (people.isEmpty()) line(label(R.string.irc_no_matches))
                if (state.active.joined) line(label(R.string.irc_refresh)) { session.actions.names() }
            }
            IrcNavigation.Page.HELP -> {
                line(label(R.string.irc_help_title)); line(label(R.string.irc_quick_help))
                line(label(R.string.irc_channels)) { openNavigation(IrcNavigation.Page.CHANNELS) }
                line(label(R.string.irc_all_commands)) { openNavigation(IrcNavigation.Page.COMMANDS) }
                line(label(R.string.irc_connection_details)) { openNavigation(IrcNavigation.Page.DETAILS) }
            }
            IrcNavigation.Page.COMMANDS -> {
                line(label(R.string.irc_all_commands))
                var group = ""
                var shownGroup = ""
                resources.getStringArray(R.array.irc_commands).forEach { entry ->
                    if (!entry.startsWith('/')) group = entry else if (navigation.matches(entry)) {
                        if (group != shownGroup) { line(group); shownGroup = group }
                        line(entry) { closeNavigation(); input.setText(entry.substringBefore(' ') + " "); input.setSelection(input.length()); input.requestFocus() }
                    }
                }
            }
            IrcNavigation.Page.MORE -> {
                line(label(R.string.irc_more))
                line(label(R.string.irc_connection_details)) { openNavigation(IrcNavigation.Page.DETAILS) }
                if (state.active.joined && session.client?.ready == true && session.client?.messageRedaction == true) line(label(R.string.irc_delete_message)) { openDeleteMessages() }
                if (state.active.joined) line(getString(R.string.irc_leave, state.active.name)) { openNavigation(IrcNavigation.Page.LEAVE) }
                else if (state.active.number != 1) line(label(R.string.irc_close_conversation)) { state.closeActive(); closeNavigation() }
                line(label(R.string.irc_networks)) { openNavigation(IrcNavigation.Page.NETWORKS) }
                val split = session.split
                fun splitLine(mode: IrcTerminalSession.Split, text: Int) = line((if (split == mode) "[✓] " else "[ ] ") + label(text)) { session.split = mode; closeNavigation() }
                splitLine(IrcTerminalSession.Split.OFF, R.string.irc_split_off)
                splitLine(IrcTerminalSession.Split.AUTO, R.string.irc_split_auto)
                splitLine(IrcTerminalSession.Split.SIDE, R.string.irc_split_side)
                splitLine(IrcTerminalSession.Split.STACK, R.string.irc_split_stack)
                line(label(R.string.irc_disconnect)) { session.disconnect(); closeNavigation() }
            }
            IrcNavigation.Page.DELETE_MESSAGES -> {
                line(label(R.string.irc_delete_message)); line(label(R.string.irc_delete_explanation))
                val messages = state.active.lines.asReversed().filter { it.messageId.isNotEmpty() && it.command in setOf("PRIVMSG", "NOTICE") && !it.pending && navigation.matches(it.text) }
                messages.take(navigation.limit).forEach { message -> line(message.text) {
                    navigation.deleteTarget = state.active.name; navigation.deleteMessageId = message.messageId
                    openNavigation(IrcNavigation.Page.DELETE_CONFIRM)
                } }
                if (messages.isEmpty()) line(label(R.string.irc_no_matches))
                more(messages.size)
            }
            IrcNavigation.Page.DELETE_CONFIRM -> {
                line(label(R.string.irc_delete_message))
                val target = navigation.deleteTarget; val id = navigation.deleteMessageId
                val buffer = state.buffers.firstOrNull { it.name == target }
                val message = buffer?.lines?.firstOrNull { it.messageId == id && it.command in setOf("PRIVMSG", "NOTICE") }
                if (message == null) line(label(R.string.irc_delete_missing))
                else if (buffer?.joined != true || session.client?.ready != true || session.client?.messageRedaction != true) line(label(R.string.irc_delete_unavailable))
                else {
                    line(message.text); line(label(R.string.irc_delete_question))
                    line(label(R.string.irc_delete_confirm)) {
                        check(session.client?.messageRedaction == true) { getString(R.string.irc_delete_unavailable) }
                        session.actions.deleteMessage(target, id); closeNavigation()
                    }
                }
            }
            IrcNavigation.Page.NETWORK -> {
                line(label(R.string.irc_network)); line("${session.host}:${session.port}")
                line(label(R.string.irc_network_explanation))
            }
            IrcNavigation.Page.ACCOUNT -> {
                line(label(R.string.irc_sign_in)); line(getString(R.string.irc_account_explanation, session.active.network.name))
                line(label(R.string.irc_create_account)) { openNavigation(IrcNavigation.Page.REGISTER) }
                line((if (session.rememberDevice) "[✓] " else "[ ] ") + label(R.string.irc_remember_device)) { session.rememberDevice = !session.rememberDevice }
                line(label(R.string.irc_device_explanation))
            }
            IrcNavigation.Page.REGISTER -> {
                line(label(R.string.irc_create_account)); line(label(R.string.irc_register_explanation))
                line(label(R.string.irc_signup_info))
                line((if (session.rememberDevice) "[✓] " else "[ ] ") + label(R.string.irc_remember_device)) { session.rememberDevice = !session.rememberDevice }
                line(label(R.string.irc_device_explanation))
                line(label(R.string.irc_sign_in)) { openNavigation(IrcNavigation.Page.ACCOUNT) }
            }
            IrcNavigation.Page.JOIN -> { line(label(R.string.irc_join_name)); line(label(R.string.irc_join_hint)) }
            IrcNavigation.Page.LEAVE -> {
                line(getString(R.string.irc_leave_question, state.active.name))
                line(label(R.string.irc_leave_confirm)) { session.actions.part(); closeNavigation() }
                line(label(R.string.irc_cancel)) { closeNavigation() }
            }
            IrcNavigation.Page.DETAILS -> {
                line(label(R.string.irc_connection_details)); line("${session.host}:${session.port}\n${session.connectionText}")
                line(label(R.string.irc_security_details))
                if (session.deviceStatus.isNotEmpty()) line(session.deviceStatus)
                if (session.active.network.device) line(label(R.string.irc_forget_device)) { openNavigation(IrcNavigation.Page.FORGET_DEVICE) }
                line(label(R.string.irc_diagnostics)) { openNavigation(IrcNavigation.Page.DIAGNOSTICS) }
                if (session.client?.ready != true) line(label(R.string.irc_sign_in)) { openNavigation(IrcNavigation.Page.ACCOUNT) }
            }
            IrcNavigation.Page.DIAGNOSTICS -> { line(label(R.string.irc_diagnostics)); state.buffers.first().lines.forEach { line(it.text) } }
            IrcNavigation.Page.FORGET_DEVICE -> {
                line(label(R.string.irc_forget_explanation))
                line(label(R.string.irc_forget_device)) { session.forgetDevice(); closeNavigation() }
                line(label(R.string.irc_cancel)) { closeNavigation() }
            }
        }
    }
    private fun requestSecret(command: String, account: String) {
        require(account.isNotBlank() && account.length <= 64 && account.none { it <= ' ' })
        priorDraft = session.state.active.draft; secretCommand = command; secretAccount = account
        navigation.close()
        switching = true; input.setText(""); switching = false
        priorSecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.typeface = Tuils.getTypeface(this)
        input.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        input.hint = getString(R.string.irc_secret_hint); input.maxLines = 1
        render(); input.requestFocus()
    }
    private fun cancelSecret() {
        if (secretCommand.isEmpty()) return
        switching = true; input.text.clear(); switching = false
        secretCommand = ""; secretAccount = ""; registerEmail = ""
        if (!priorSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.typeface = Tuils.getTypeface(this)
        input.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.hint = getString(R.string.irc_input_hint); input.maxLines = 3
        session.state.active.draft = priorDraft; priorDraft = ""; restoreDraft(); render()
    }
    private fun server(text: String): Pair<String, Int> {
        val pieces = text.trim().split(Regex("\\s+"))
        require(pieces.size in 1..2 && IrcNetwork.validHost(pieces[0])) { getString(R.string.irc_network_hint) }
        val port = pieces.getOrNull(1)?.toIntOrNull() ?: if (pieces.size == 2) 0 else 6697
        require(port in 1..65535) { getString(R.string.irc_network_hint) }
        return pieces[0] to port
    }
    private fun submit() {
        input.error = null
        if (secretCommand.isNotEmpty()) {
            val secret = input.text.toString().toCharArray(); val command = secretCommand; val name = secretAccount
            try {
                require(secret.isNotEmpty() && secret.size <= 256 && secret.none { it == '\u0000' || it == '\r' || it == '\n' }) { "Password must be 1–256 characters with no line breaks." }
                when (command) {
                    // Services identify the current nickname, so sign in under the account's name.
                    "SASL login" -> { session.account = name; session.state.nickname = name; session.connect(secret) }
                    "Create account" -> { session.account = name; session.state.nickname = name; session.connect(secret, register = true) }
                    "NickServ register" -> {
                        require(secret.none { it.isWhitespace() }) { "NickServ passwords cannot contain spaces." }
                        // Networks such as Libera.Chat also need an email address after the password.
                        session.send("PRIVMSG", listOf("NickServ"), "REGISTER ${String(secret)}" + registerEmail.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty())
                        session.state.status("Registration submitted privately to NickServ; check its response in status.")
                    }
                    "Operator login" -> { session.send("OPER", listOf(name, String(secret))); session.state.status("Operator authentication submitted; check the server response.") }
                }
                cancelSecret(); render()
            } catch (error: Exception) { cancelSecret(); input.error = error.message ?: "Authentication could not be submitted." }
            finally { secret.fill('\u0000') }
            return
        }
        val original = input.text.toString()
        try {
            if (navigation.page != IrcNavigation.Page.CHAT) {
                when (navigation.page) {
                    IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER -> {
                        val name = original.trim()
                        require(IrcProtocol.nick(name)) { getString(R.string.irc_account_invalid) }
                        val registering = navigation.page == IrcNavigation.Page.REGISTER
                        session.state.nickname = name; requestSecret(if (registering) "Create account" else "SASL login", name)
                    }
                    IrcNavigation.Page.JOIN -> joinChannel(original.trim())
                    IrcNavigation.Page.NETWORK -> {
                        check(session.client?.ready != true) { getString(R.string.irc_disconnect_first) }
                        val (host, port) = server(original)
                        session.disconnect(); session.host = host; session.port = port; closeNavigation()
                    }
                    IrcNavigation.Page.ADD_NETWORK -> { val (host, port) = server(original); switchNetwork { session.add(IrcNetwork.custom(host, port, emptyList())) } }
                    IrcNavigation.Page.NICKNAME -> {
                        val name = original.trim()
                        require(IrcProtocol.nick(name)) { getString(R.string.irc_account_invalid) }
                        if (session.client?.ready == true) session.send("NICK", listOf(name)) else session.state.nickname = name
                        closeNavigation()
                    }
                    else -> renderNavigation()
                }
                return
            }
            require(original.isNotBlank() && original.none { it == '\r' || it == '\n' || it == '\u0000' }) { "Use one IRC message or command per line." }
            if (!original.startsWith('/') || original.startsWith("//")) {
                val b = session.state.active
                check(b.number != 1 && (!b.name.startsWith('#') && !b.name.startsWith('&') || b.joined)) { "Use /join #channel or /query nickname first." }
                session.say(b.name, if (original.startsWith("//")) original.drop(1) else original)
            } else {
                val command = original.substringBefore(' ').drop(1).lowercase(java.util.Locale.ROOT)
                val rest = original.substringAfter(' ', "").trim()
                fun channel(): String { val target = rest.ifEmpty { session.state.active.name }; require(IrcProtocol.target(target) && target.first() in "#&") { "Use #channel." }; return target }
                when (command) {
                    "help" -> { require(rest.isEmpty()); openNavigation(IrcNavigation.Page.HELP); return }
                    "server" -> { check(session.client == null) { "/disconnect before changing servers." }; if (rest.isNotEmpty()) { val (host, port) = server(rest); session.host = host; session.port = port }; session.state.status("Server ${session.host}:${session.port} · verified TLS only") }
                    "background", "stay" -> {
                        setStayConnected(when (rest.lowercase(java.util.Locale.ROOT)) {
                            "" -> !session.stayConnected
                            "on" -> true
                            "off" -> false
                            else -> throw IllegalArgumentException("Use /background, /background on or /background off.")
                        })
                    }
                    "split" -> {
                        session.split = when (rest.lowercase(java.util.Locale.ROOT)) {
                            "" -> if (session.split == IrcTerminalSession.Split.OFF) IrcTerminalSession.Split.AUTO else IrcTerminalSession.Split.OFF
                            "auto", "on" -> IrcTerminalSession.Split.AUTO
                            "side" -> IrcTerminalSession.Split.SIDE
                            "stack", "top" -> IrcTerminalSession.Split.STACK
                            "off" -> IrcTerminalSession.Split.OFF
                            else -> throw IllegalArgumentException("Use /split, /split auto, /split side, /split stack or /split off.")
                        }
                    }
                    "network", "networks" -> {
                        val verb = rest.substringBefore(' ').lowercase(java.util.Locale.ROOT); val target = rest.substringAfter(' ', "").trim()
                        when {
                            rest.isEmpty() || verb == "list" -> { openNavigation(IrcNavigation.Page.NETWORKS); return }
                            verb == "add" -> { val (host, port) = server(target); switchNetwork { session.add(IrcNetwork.PRESETS.firstOrNull { it.host.equals(host, true) && it.port == port } ?: IrcNetwork.custom(host, port, emptyList())) }; return }
                            verb == "next" || verb == "prev" -> { switchNetwork { session.cycle(verb == "next") }; return }
                            else -> {
                                val match = session.connections.firstOrNull { it.network.name.equals(rest, true) || it.host.equals(rest, true) }
                                    ?: throw IllegalArgumentException("No such network. /network lists saved networks; /network add host [port] adds one.")
                                switchNetwork { session.switchTo(match) }; return
                            }
                        }
                    }
                    "nick" -> { require(IrcProtocol.nick(rest)) { "Use /nick nickname (up to 30 IRC nickname characters)." }; if (session.client?.ready == true) session.send("NICK", listOf(rest)) else session.state.nickname = rest }
                    "connect", "reconnect" -> {
                        require(rest.isEmpty()) { "Use /server host port, /nick nickname, then /connect or /login account." }
                        if (session.active.deviceReady) session.reconnect()
                        else if (session.account.isEmpty()) session.connect()
                        else { requestSecret("SASL login", session.account); return }
                    }
                    "login" -> { requestSecret("SASL login", rest.ifEmpty { session.state.nickname }); return }
                    "register" -> {
                        require(rest.isEmpty() || rest.matches(Regex("[^\\s@]+@[^\\s@]+"))) { "Use /register, or /register email where the network asks for one. Never type the password here." }
                        if (session.client?.ready == true) { registerEmail = rest; requestSecret("NickServ register", session.state.nickname) } else openNavigation(IrcNavigation.Page.REGISTER)
                        return
                    }
                    "oper" -> { require(session.client?.ready == true); requestSecret("Operator login", rest); return }
                    "join", "j" -> session.actions.join(channel())
                    "part", "leave" -> session.send("PART", listOf(channel()))
                    "window", "buffer" -> { if (rest == "close") session.state.closeActive() else if (rest.isNotEmpty() && rest != "list") { session.state.active.scroll = scroll.scrollY; session.state.switch(rest.removePrefix("goto ")) } else { session.state.status(session.state.buffers.joinToString(" · ") { "${it.number}:${it.name}${if (it.unread > 0) "*" else ""}" }); session.state.switch("1") }; restoreDraft(); render(); return }
                    "query" -> { session.actions.query(rest); restoreDraft(); render(); return }
                    "msg" -> { val target = rest.substringBefore(' '); val body = rest.substringAfter(' ', ""); require(IrcProtocol.target(target) && target.lowercase() !in setOf("nickserv", "chanserv", "hostserv", "operserv")) { "Service credentials use masked /login, /register or /oper, not /msg." }; session.say(target, body) }
                    "me" -> { require(rest.isNotBlank() && session.state.active.number != 1); session.say(session.state.active.name, "\u0001ACTION $rest\u0001") }
                    "names" -> { openPeople(); return }
                    "list" -> { require(rest.isEmpty()); session.actions.directory(); openNavigation(IrcNavigation.Page.DIRECTORY); return }
                    "topic" -> { require(session.state.active.joined); session.send("TOPIC", listOf(session.state.active.name), rest.takeIf { it.isNotEmpty() }) }
                    "whois" -> { require(IrcProtocol.nick(rest)); session.send("WHOIS", listOf(rest)); openNavigation(IrcNavigation.Page.DIAGNOSTICS); return }
                    "mode" -> { val p = rest.split(' '); require(p.size in 1..5 && IrcProtocol.target(p[0])); session.send("MODE", p) }
                    "kick" -> { require(session.state.active.joined); val nick = rest.substringBefore(' '); require(IrcProtocol.nick(nick)); session.send("KICK", listOf(session.state.active.name, nick), rest.substringAfter(' ', "").takeIf { it.isNotEmpty() }) }
                    "delete" -> { require(rest.isEmpty()); openDeleteMessages(); return }
                    "clear" -> { require(rest.isEmpty()); session.state.active.lines.clear() }
                    "disconnect", "quit" -> { require(rest.isEmpty()); session.disconnect() }
                    else -> throw IllegalArgumentException("Unknown IRC command. /help lists supported commands; nothing was sent.")
                }
            }
            session.state.active.draft = ""; restoreDraft(); render()
        } catch (error: Exception) { input.error = error.message ?: "IRC command rejected; input kept." }
    }
}
