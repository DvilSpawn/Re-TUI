package ohi.andre.consolelauncher.irc

import android.content.Context
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
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

class IrcTerminalSession(application: Application) : AndroidViewModel(application) {
    val state = IrcState()
    val navigation = IrcNavigation(state)
    val actions = IrcActions(state, ::send)
    val devices = IrcDeviceKey(application)
    var rememberDevice = false
    var deviceStatus = ""
    private var pendingDevice: IrcDeviceKey.Identity? = null
    private var revokingDevice: IrcDeviceKey.Identity? = null
    internal var connector: ((String, Int) -> java.net.Socket)? = null
    var initialized = false
    fun restoreDevice() {
        if (initialized) return
        initialized = true
        if (devices.remembered() != null) reconnectDevice()
    }
    fun reconnectDevice() {
        val identity = devices.remembered() ?: error("Device key unavailable. Sign in with a password.")
        host = identity.host; port = identity.port; account = identity.account; state.nickname = identity.account
        connect(external = true)
    }
    fun forgetDevice() {
        val identity = devices.remembered() ?: return
        if (client?.ready == true && host == identity.host && port == identity.port && account.equals(identity.account, true)) {
            runCatching {
                send("PRIVMSG", listOf("NickServ"), "CERT DEL ${devices.fingerprint(identity)}")
                revokingDevice = identity; deviceStatus = getApplication<Application>().getString(R.string.irc_device_removing)
                val generation = epoch
                ui.postDelayed({ if (generation == epoch && revokingDevice == identity) finishForget(identity, false) }, 10_000)
            }.onFailure { finishForget(identity, false) }
        } else finishForget(identity, false)
    }
    private fun finishForget(identity: IrcDeviceKey.Identity, confirmed: Boolean) {
        revokingDevice = null; devices.forget(identity)
        pendingDevice = null; rememberDevice = false; disconnect(); account = ""
        deviceStatus = getApplication<Application>().getString(if (confirmed) R.string.irc_device_revoked else R.string.irc_device_forgotten)
        changed?.invoke()
    }
    var host = "irc.kokodoko.cloud"; var port = 6697
    var connectionText = "Offline"
    var account = ""
    var client: IrcClient? = null; private set
    var changed: (() -> Unit)? = null
    private val ui = Handler(Looper.getMainLooper())
    private val incoming = ArrayBlockingQueue<Pair<Int, IrcMessage>>(256)
    private val drainScheduled = AtomicBoolean(false)
    private var epoch = 0
    private fun scheduleDrain() { if (drainScheduled.compareAndSet(false, true)) ui.post { drain() } }
    private fun drain() {
        var updated = false
        repeat(64) {
            val (generation, message) = incoming.poll() ?: return@repeat
            if (generation == epoch) {
                val identity = pendingDevice
                if (message.command == "NOTICE" && message.nick.equals("NickServ", true) && message.params.firstOrNull()?.let { IrcProtocol.fold(it, state.mapping) == IrcProtocol.fold(state.nickname, state.mapping) } == true) {
                    val notice = IrcProtocol.display(message.params.lastOrNull().orEmpty())
                    if (identity != null && (notice == "Certificate fingerprint successfully added" || notice == "That certificate fingerprint was already authorized")) {
                        runCatching { devices.remember(identity) }.onSuccess { deviceStatus = getApplication<Application>().getString(R.string.irc_device_saved) }
                            .onFailure { deviceStatus = getApplication<Application>().getString(R.string.irc_device_failed) }
                        pendingDevice = null
                    }
                    val removing = revokingDevice
                    if (removing != null && (notice == "Certificate fingerprint successfully removed" || notice == "Certificate fingerprint not found")) { finishForget(removing, true); return@repeat }
                }
                runCatching { state.receive(message) }.onFailure { state.status("Incoming event could not be displayed; window limit or malformed data.") }
                updated = true
            }
        }
        drainScheduled.set(false)
        if (incoming.isNotEmpty()) scheduleDrain()
        if (updated) changed?.invoke()
    }
    fun connect(password: CharArray = charArrayOf(), external: Boolean = false, register: Boolean = false) {
        val identity = IrcDeviceKey.Identity(host.lowercase(java.util.Locale.ROOT), port, account)
        val enroll = rememberDevice && !external && host.equals("irc.kokodoko.cloud", true) && account.isNotBlank()
        if (external) check(devices.remembered() == identity) { "Device key unavailable. Sign in with a password." }
        disconnect()
        deviceStatus = ""
        val generation = ++epoch
        state.status("Connecting to $host:$port with verified TLS")
        client = IrcClient(host, port, state.nickname, account, password,
            // A busy server must not create an unbounded Android main-thread callback backlog.
            receive = { message -> check(incoming.offer(generation to message)) { "IRC UI backlog full" }; scheduleDrain() },
            connection = { connected, text -> ui.post { if (generation == epoch) {
                connectionText = if (connected) "Connected · $host" else if (register) text else if (text.startsWith("Disconnected")) "Disconnected · Sign in to retry" else "Connecting…"
                if (!connected && text.startsWith("Disconnected")) { state.disconnected(); pendingDevice = null; if (external) deviceStatus = getApplication<Application>().getString(R.string.irc_device_failed) }
                if (connected && enroll) runCatching {
                    if (register) devices.ensure(identity)
                    send("PRIVMSG", listOf("NickServ"), "CERT ADD ${devices.fingerprint(identity)}")
                    pendingDevice = identity; deviceStatus = getApplication<Application>().getString(R.string.irc_device_pending)
                    ui.postDelayed({ if (generation == epoch && pendingDevice == identity) { pendingDevice = null; deviceStatus = getApplication<Application>().getString(R.string.irc_device_failed); changed?.invoke() } }, 10_000)
                }.onFailure { deviceStatus = getApplication<Application>().getString(R.string.irc_device_failed) }
                if (connected && account.isNotEmpty()) runCatching { actions.authenticated(host.equals("irc.kokodoko.cloud", true)) }.onFailure { state.status(it.message ?: "Could not join channel") }
                changed?.invoke()
            } } }, connector = connector ?: { h, p ->
                if (enroll && !register) devices.ensure(identity)
                IrcClient.tlsSocket(h, p, if (external || enroll && !register) devices.factory(identity) else javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory)
            }, external = external, register = register)
        client!!.start(); changed?.invoke()
    }
    fun disconnect() {
        val removing = revokingDevice; revokingDevice = null
        if (removing != null) { devices.forget(removing); account = ""; rememberDevice = false; deviceStatus = getApplication<Application>().getString(R.string.irc_device_forgotten) }
        epoch++; pendingDevice = null; client?.close(); client = null; incoming.clear(); state.disconnected(); connectionText = "Offline"; changed?.invoke()
    }
    fun send(command: String, parameters: List<String> = emptyList(), body: String? = null) {
        val current = client ?: throw IllegalStateException("Not connected. /connect first.")
        current.send(command, parameters, body)
    }
    fun say(target: String, body: String) {
        require(IrcProtocol.target(target) && body.isNotBlank() && target.lowercase() !in setOf("nickserv", "chanserv", "hostserv", "operserv")) { "Use masked /login, /register or /oper for service authentication." }
        require(IrcProtocol.validUnicode(body) && body.none { it < ' ' && it != '\u0001' })
        require(!body.contains('\r') && !body.contains('\n') && !body.contains('\u0000'))
        // Check buffer capacity before enqueueing; a UI failure must never encourage a duplicate send.
        state.buffer(target)
        send("PRIVMSG", listOf(target), body)
        state.local(target, body, client?.echoMessages == true); changed?.invoke()
    }
    override fun onCleared() { changed = null; disconnect(); super.onCleared() }
}

/** One transcript, one composer, terminal commands and numbered buffer shortcuts. */
class IrcActivity : LocalizedComponentActivity() {
    companion object { fun open(context: Context) = context.startActivity(Intent(context, IrcActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    internal lateinit var session: IrcTerminalSession
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private lateinit var root: View
    private lateinit var windowPanel: HelpWindowPanel
    private var secretCommand = ""
    private var secretAccount = ""
    private var priorDraft = ""
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
        session = ViewModelProvider(this)[IrcTerminalSession::class.java]
        // Non-exported debug fixtures must never reconnect a maintainer's real account.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("irc_offline_fixture", false)) session.initialized = true
        val screen = FrameLayout(this).apply { isClickable = true }
        val safe = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val host = FrameLayout(this); safe.addView(host, ViewGroup.LayoutParams(-1, -1))
        root = layoutInflater.inflate(R.layout.irc_surface, host, false)
        windowPanel = HelpWindowPanel(host, root, saved, "irc", root.findViewById(R.id.irc_header), root.findViewById(R.id.irc_header_row), showHeaderReset = false)
        windowPanel.reserveBottomCorners(root.findViewById(R.id.irc_suggestion_scroll))
        input = root.findViewById(R.id.irc_input); output = root.findViewById(R.id.irc_output); scroll = root.findViewById(R.id.irc_scroll)
        input.isSaveEnabled = false
        input.setOnKeyListener { _, key, event ->
            when {
                key == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed -> { if (event.action == KeyEvent.ACTION_UP) submit(); true }
                event.action == KeyEvent.ACTION_DOWN && event.isAltPressed && key in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> { switchWindow((key - KeyEvent.KEYCODE_1 + 1).toString()); true }
                event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && key == KeyEvent.KEYCODE_TAB -> { switchWindow(if (event.isShiftPressed) "prev" else "next"); true }
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
            override fun handleOnBackPressed() { if (secretCommand.isNotEmpty()) cancelSecret() else if (navigation.page != IrcNavigation.Page.CHAT) closeNavigation() else { session.disconnect(); finish() } }
        })
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> if (!restoringScroll) { if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.scroll = y else navigation.scroll = y } }
        restoringScroll = true
        restoreDraft(); render()
        scroll.post { scroll.scrollTo(0, if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.scroll else navigation.scroll); restoringScroll = false }
    }
    override fun onStart() {
        super.onStart(); session.changed = { render() }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("irc_offline_fixture", false)) session.initialized = true
        action { session.restoreDevice() }; render()
    }
    override fun onPause() { windowPanel.persist(); super.onPause() }
    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) { style(); render() }
    }
    override fun onStop() {
        cancelSecret(); session.changed = null
        if (!isChangingConfigurations && navigation.page in listOf(IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER)) navigation.close()
        if (!isChangingConfigurations) { session.disconnect(); session.initialized = false }
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
    private fun chip(label: String, action: () -> Unit): View {
        val spaces = SuggestionChipStyler.spacing()
        val chip = SuggestionChipStyler.createView(this, spaces).apply { text = label; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        SuggestionChipStyler(this).apply(chip, SuggestionsManager.Suggestion.TYPE_COMMAND, null)
        return FrameLayout(this).apply {
            minimumHeight = dp(48); minimumWidth = dp(48); isClickable = true; isFocusable = true; contentDescription = label
            layoutParams = LinearLayout.LayoutParams(-2, -1).apply { setMargins(spaces[0], 0, spaces[0], 0) }
            addView(chip, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER)); setOnClickListener { action() }
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
        } else if (navigation.page != IrcNavigation.Page.CHAT) {
            rows.addView(chip(getString(R.string.irc_back)) { closeNavigation() })
            if (navigation.page in listOf(IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER, IrcNavigation.Page.JOIN, IrcNavigation.Page.NETWORK))
                rows.addView(chip(getString(R.string.irc_continue)) { submit() })
        } else if (session.client?.ready == true) {
            rows.addView(chip(getString(R.string.irc_channels)) { openNavigation(IrcNavigation.Page.CHANNELS) })
            rows.addView(chip(getString(R.string.irc_people)) { openPeople() })
            rows.addView(chip(getString(R.string.irc_help_title)) { openNavigation(IrcNavigation.Page.HELP) })
            rows.addView(chip(getString(R.string.irc_more)) { openNavigation(IrcNavigation.Page.MORE) })
        } else {
            if (session.devices.remembered() != null) rows.addView(chip(getString(R.string.irc_reconnect)) { action { session.reconnectDevice() } })
            rows.addView(chip(getString(R.string.irc_sign_in)) { openNavigation(IrcNavigation.Page.ACCOUNT) })
            rows.addView(chip(getString(R.string.irc_network)) { openNavigation(IrcNavigation.Page.NETWORK) })
            rows.addView(chip(getString(R.string.irc_help_title)) { openNavigation(IrcNavigation.Page.HELP) })
        }
    }
    private fun restoreDraft() { switching = true; input.setText(if (navigation.page == IrcNavigation.Page.CHAT) session.state.active.draft else navigation.text); input.setSelection(input.length()); switching = false; suggestions() }
    private fun openNavigation(page: IrcNavigation.Page) {
        if (secretCommand.isNotEmpty()) return
        if (page == IrcNavigation.Page.REGISTER) {
            session.rememberDevice = session.host.equals("irc.kokodoko.cloud", true)
        }
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
        val title = if (active.number == 1) getString(R.string.irc_title) else active.name
        root.findViewById<TextView>(R.id.irc_context).text = title + if (active.number != 1 && !active.name.startsWith('#') && !active.name.startsWith('&')) " · ${getString(R.string.irc_private)}" else active.topic.takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()
        root.findViewById<TextView>(R.id.irc_status).text = when {
            secretCommand.isNotEmpty() -> getString(R.string.irc_password_context, secretAccount)
            state.joining.isNotEmpty() -> getString(R.string.irc_joining, state.joining)
            state.joinError.isNotEmpty() -> state.joinError
            else -> "${state.nickname} · ${session.connectionText}" + session.deviceStatus.takeIf { it.isNotEmpty() }?.let { "\n$it" }.orEmpty()
        }
        val searchable = navigation.page in listOf(IrcNavigation.Page.CHANNELS, IrcNavigation.Page.DIRECTORY, IrcNavigation.Page.PEOPLE, IrcNavigation.Page.COMMANDS, IrcNavigation.Page.DELETE_MESSAGES)
        val editable = secretCommand.isNotEmpty() || searchable || navigation.page in listOf(IrcNavigation.Page.CHAT, IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER, IrcNavigation.Page.NETWORK, IrcNavigation.Page.JOIN)
        input.isEnabled = editable
        root.findViewById<View>(R.id.irc_prompt_row).visibility = if (editable) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.irc_send).isEnabled = editable && !searchable
        root.findViewById<TextView>(R.id.irc_prompt).text = when {
            secretCommand.isNotEmpty() -> "password> "
            navigation.page == IrcNavigation.Page.CHAT -> "> "
            navigation.page in listOf(IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER) -> "account> "
            navigation.page == IrcNavigation.Page.NETWORK -> "server> "
            navigation.page == IrcNavigation.Page.JOIN -> "join> "
            else -> if (searchable) "find> " else "nav> "
        }
        if (secretCommand.isEmpty()) input.hint = getString(when (navigation.page) {
            IrcNavigation.Page.ACCOUNT, IrcNavigation.Page.REGISTER -> R.string.irc_account_hint
            IrcNavigation.Page.NETWORK -> R.string.irc_network_hint
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
            val styled = SpannableStringBuilder()
            active.lines.forEachIndexed { i, line ->
                if (i > 0) styled.append('\n')
                val start = styled.length; styled.append(line.text)
                if (line.sender.isNotEmpty()) {
                    val end = if (line.text.startsWith('<')) line.text.indexOf('>').let { if (it >= 0) it + 1 else 0 } else 0
                    if (end > 0) styled.setSpan(ForegroundColorSpan(ThemeColorResolver.color(Theme.workspace_control_text_color)), start, start + end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (!line.text.contains("rejected", true) && !line.text.contains("uncertain", true)) {
                    styled.setSpan(ForegroundColorSpan(ThemeColorResolver.color(Theme.muted_text_color)), start, styled.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            output.text = styled
            scroll.post { if (navigation.page == IrcNavigation.Page.CHAT) scroll.scrollTo(0, if (!changedBuffer && !pageChanged && bottom) scroll.getChildAt(0).height else if (pageChanged) active.scroll else oldY) }
        }
        displayedText = transcript; displayedWindow = active.number; displayedPage = navigation.page
        if (!showTranscript) renderNavigation()
        val windows = root.findViewById<LinearLayout>(R.id.irc_windows); windows.removeAllViews()
        state.buffers.filter { it.number != 1 }.forEach { b ->
            val marker = if (b.highlight) " !${b.unread}" else if (b.unread > 0) " +${b.unread}" else ""
            windows.addView(chip("${if (b.number == state.current) "[" else ""}${b.name}$marker${if (b.number == state.current) "]" else ""}") { switchWindow(b.number.toString()) })
        }
        root.findViewById<View>(R.id.irc_window_scroll).visibility = if (windows.childCount == 0) View.GONE else View.VISIBLE
        suggestions()
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
                line(label(R.string.irc_welcome))
                line(label(if (session.host.equals("irc.kokodoko.cloud", true)) R.string.irc_signup_info else R.string.irc_other_network))
                if (session.connectionText.startsWith("Disconnected")) line(session.connectionText)
                if (session.client?.ready == true) line(label(R.string.irc_channels)) { openNavigation(IrcNavigation.Page.CHANNELS) }
                else {
                    line(label(R.string.irc_sign_in)) { openNavigation(IrcNavigation.Page.ACCOUNT) }
                    line(label(R.string.irc_create_account)) { openNavigation(IrcNavigation.Page.REGISTER) }
                }
                line(label(R.string.irc_help_title)) { openNavigation(IrcNavigation.Page.HELP) }
            }
            IrcNavigation.Page.CHANNELS -> {
                line(label(R.string.irc_channels))
                val open = state.buffers.filter { it.number != 1 && navigation.matches(it.name) }
                open.forEach { b -> line(b.name + if (b.unread > 0) " · ${b.unread}${if (b.highlight) " !" else ""}" else "") {
                    if ((b.name.startsWith('#') || b.name.startsWith('&')) && !b.joined) joinChannel(b.name) else switchWindow(b.number.toString())
                } }
                if (session.host.equals("irc.kokodoko.cloud", true)) listOf("#retui", "#help").filter { channel -> navigation.matches(channel) && state.buffers.none { IrcProtocol.fold(it.name, state.mapping) == IrcProtocol.fold(channel, state.mapping) } }.forEach { channel -> line(channel) { joinChannel(channel) } }
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
                line(label(R.string.irc_disconnect)) { session.disconnect(); session.account = ""; closeNavigation() }
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
                line(label(R.string.irc_sign_in)); line(label(R.string.irc_account_explanation))
                line(label(R.string.irc_create_account)) { openNavigation(IrcNavigation.Page.REGISTER) }
                if (session.host.equals("irc.kokodoko.cloud", true)) {
                    line(label(R.string.irc_signup_info))
                    line((if (session.rememberDevice) "[✓] " else "[ ] ") + label(R.string.irc_remember_device)) { session.rememberDevice = !session.rememberDevice }
                    line(label(R.string.irc_device_explanation))
                }
            }
            IrcNavigation.Page.REGISTER -> {
                line(label(R.string.irc_create_account)); line(label(R.string.irc_register_explanation))
                if (session.host.equals("irc.kokodoko.cloud", true)) {
                    line(label(R.string.irc_signup_info))
                    line((if (session.rememberDevice) "[✓] " else "[ ] ") + label(R.string.irc_remember_device)) { session.rememberDevice = !session.rememberDevice }
                    line(label(R.string.irc_device_explanation))
                }
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
                if (session.devices.remembered() != null) line(label(R.string.irc_forget_device)) { openNavigation(IrcNavigation.Page.FORGET_DEVICE) }
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
        secretCommand = ""; secretAccount = ""
        if (!priorSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.typeface = Tuils.getTypeface(this)
        input.imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.hint = getString(R.string.irc_input_hint); input.maxLines = 3
        session.state.active.draft = priorDraft; priorDraft = ""; restoreDraft(); render()
    }
    private fun submit() {
        input.error = null
        if (secretCommand.isNotEmpty()) {
            val secret = input.text.toString().toCharArray(); val command = secretCommand; val name = secretAccount
            try {
                require(secret.isNotEmpty() && secret.size <= 256 && secret.none { it == '\u0000' || it == '\r' || it == '\n' }) { "Password must be 1–256 characters with no line breaks." }
                when (command) {
                    "SASL login" -> { session.account = name; session.connect(secret) }
                    "Create account" -> { session.account = name; session.state.nickname = name; session.connect(secret, register = true) }
                    "NickServ register" -> { require(secret.none { it.isWhitespace() }) { "NickServ passwords cannot contain spaces." }; session.send("PRIVMSG", listOf("NickServ"), "REGISTER ${String(secret)}"); session.state.status("Registration submitted privately to NickServ; check its response in status.") }
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
                        val pieces = original.trim().split(Regex("\\s+"))
                        require(pieces.size in 1..2 && pieces[0].length <= 253 && pieces[0].matches(Regex("[A-Za-z0-9.:-]+"))) { getString(R.string.irc_network_hint) }
                        val port = pieces.getOrNull(1)?.toInt() ?: 6697; require(port in 1..65535)
                        session.disconnect(); session.host = pieces[0]; session.port = port; session.account = ""; closeNavigation()
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
                    "server" -> { check(session.client == null) { "/disconnect before changing servers." }; if (rest.isNotEmpty()) { val p = rest.split(' '); require(p.size <= 2 && p[0].length <= 253 && p[0].matches(Regex("[A-Za-z0-9.:-]+"))); val port = p.getOrNull(1)?.toInt() ?: 6697; require(port in 1..65535); session.host = p[0]; session.port = port; session.account = "" }; session.state.status("Server ${session.host}:${session.port} · verified TLS only") }
                    "nick" -> { require(IrcProtocol.nick(rest)) { "Use /nick nickname (up to 30 IRC nickname characters)." }; if (session.client?.ready == true) session.send("NICK", listOf(rest)) else session.state.nickname = rest }
                    "connect", "reconnect" -> {
                        require(rest.isEmpty()) { "Use /server host port, /nick nickname, then /connect or /login account." }
                        val remembered = session.devices.remembered()
                        if (remembered != null && session.host.equals(remembered.host, true) && session.port == remembered.port && (session.account.isEmpty() || session.account.equals(remembered.account, true))) session.reconnectDevice()
                        else if (session.account.isEmpty()) session.connect()
                        else { requestSecret("SASL login", session.account); return }
                    }
                    "login" -> { requestSecret("SASL login", rest.ifEmpty { session.state.nickname }); return }
                    "register" -> {
                        require(rest.isEmpty()) { "Use /register with no password argument." }
                        if (session.client?.ready == true) requestSecret("NickServ register", session.state.nickname) else openNavigation(IrcNavigation.Page.REGISTER)
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
                    "disconnect", "quit" -> { require(rest.isEmpty()); session.disconnect(); session.account = "" }
                    else -> throw IllegalArgumentException("Unknown IRC command. /help lists supported commands; nothing was sent.")
                }
            }
            session.state.active.draft = ""; restoreDraft(); render()
        } catch (error: Exception) { input.error = error.message ?: "IRC command rejected; input kept." }
    }
}
