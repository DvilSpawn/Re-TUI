package ohi.andre.consolelauncher.mono

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.termux.TerminalGridView
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceActivity
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceInputEditText
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSshConnection
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSshSetup
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceTerminal
import java.util.UUID

/**
 * The one link to the tmux workspace that every terminal panel on the mono home shares, set up the same way as
 * the sidebar's floating terminal. It opens when the home is shown with a terminal on it and closes when the home
 * is left; tmux keeps the shells running in between.
 */
class MonoTerms(private val context: Context) {
    enum class State { IDLE, SETUP, CONNECTING, READY, FAILED }

    interface Listener {
        /** The state or the window list changed. */
        fun changed()
    }

    private companion object { const val SETUP_TIMEOUT_MS = 15_000L }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<Listener>()
    private var started = false
    private var label: String? = null

    var state = State.IDLE
        private set
    var message: String? = null
        private set
    var windows = emptyList<Pair<String, String>>()
        private set
    var connection: TermuxWorkspaceSshConnection? = null
        private set
    /** Ctrl, Alt and Shift held down from the key band, for whichever terminal is typed into. */
    val modifiers = linkedSetOf<String>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent == null || !started || label == null || intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) != label) return
            label = null
            if (intent.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1) != 0) { set(State.SETUP); return }
            try {
                val info = TermuxWorkspaceSshSetup.parse(context, intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty())
                TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME, null,
                    TermuxWorkspaceSshSetup.daemonArguments(context))
                open(info)
            } catch (_: Exception) { set(State.SETUP) }
        }
    }

    fun add(listener: Listener) { listeners += listener }
    fun remove(listener: Listener) { listeners -= listener }

    fun start() {
        if (started) return
        started = true
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT))
        connect()
    }

    fun stop() {
        if (!started) return
        started = false
        label = null
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        main.removeCallbacksAndMessages(null)
        connection?.close()
        connection = null
        modifiers.clear()
        state = State.IDLE
    }

    fun connect() {
        if (!started) return
        connection?.close(); connection = null
        val ready = TermuxBridgeManager.status(context)
        if (!ready.termuxInstalled || !ready.runCommandDeclared || !ready.runCommandGranted || !TermuxWorkspaceSshSetup.isPaired(context)) {
            set(State.SETUP); return
        }
        set(State.CONNECTING)
        val request = "mono-terminal:${UUID.randomUUID()}"
        label = request
        try {
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                TermuxBridgeManager.createResultPendingIntent(context, request, null), TermuxWorkspaceSshSetup.commandArguments(context, false))
            main.postDelayed({ if (started && label == request) { label = null; set(State.SETUP) } }, SETUP_TIMEOUT_MS)
        } catch (_: Exception) { label = null; set(State.SETUP) }
    }

    private fun open(info: TermuxWorkspaceSshSetup.ConnectionInfo) {
        lateinit var client: TermuxWorkspaceSshConnection
        fun valid() = started && connection === client
        client = TermuxWorkspaceSshConnection(info, object : TermuxWorkspaceSshConnection.Listener {
            override fun onReady(windows: List<Pair<String, String>>) {
                if (valid()) { this@MonoTerms.windows = windows; set(State.READY) }
            }
            override fun onWindows(windows: List<Pair<String, String>>) {
                if (valid()) { this@MonoTerms.windows = windows; set(state) }
            }
            override fun onTargetGone(target: String) {
                if (valid()) { windows = windows.filter { it.first != target }; set(state) }
            }
            override fun onError(message: String) {
                if (!valid()) return
                client.close(); connection = null
                set(State.FAILED, message.lineSequence().first())
            }
        })
        connection = client
        client.connect()
    }

    private fun set(next: State, note: String? = null) {
        state = next
        message = note
        listeners.toList().forEach { it.changed() }
    }
}

/**
 * A terminal panel: one tmux window of the workspace, drawn in ink on paper. A tap lifts it over the home, above
 * the keyboard, to type into and to select text from; with no window yet it lists the workspace's windows to pick
 * from.
 */
class MonoTermPanel(
    kit: MonoKit,
    host: MonoPanelHost,
    private val terms: MonoTerms,
    item: MonoLayout.Item,
    private val tile: Int,
    private val events: Events
) : MonoPanel(kit, host), MonoTerms.Listener {
    interface Events {
        /** Lift this terminal over the home to type into; it is tapped. */
        fun lift(panel: MonoTermPanel)
        /** Put it back in its place on the home. */
        fun drop(panel: MonoTermPanel)
        /** The window this terminal shows changed; keep it in the layout. */
        fun window(panel: MonoTermPanel, window: String)
    }

    var window: String? = item.window
        private set
    private var terminal: TermuxWorkspaceTerminal? = null
    private var epoch = 0
    private var renderPending = false
    private var size: Pair<Int, Int>? = null
    private var picking = window == null
    private var released = false

    private val name = kit.text("", MonoKit.SMALL_SP).apply { maxLines = 1 }
    private val note = kit.text("", MonoKit.SMALL_SP, kit.dim).apply { maxLines = 1 }
    private val done = kit.text(kit.context.getString(R.string.mono_term_done), MonoKit.SMALL_SP).apply {
        maxLines = 1
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(10), 0)
        kit.pressable(this, framed = false)
        visibility = View.GONE
        setOnClickListener { events.drop(this@MonoTermPanel) }
    }
    private val doneRule = View(kit.context).apply { setBackgroundColor(kit.ink); visibility = View.GONE }
    private val grid = TerminalGridView(kit.context).apply {
        updateThemeColors(kit.ink, kit.paper, kit.ink)
        monochrome = true
        // On the home a long press opens the panel's menu; lifted, it selects text.
        selectable = false
        setTerminalTypeface(kit.face)
        setTerminalTextSizeSp(if (item.columns < MonoLayout.COLUMNS) 13f else 15f)
        visibility = View.INVISIBLE
        setOnClickListener { focus() }
        addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob -> if (r - l != or_ - ol || b - t != ob - ot) resize() }
    }
    private val list = LinearLayout(kit.context).apply { orientation = LinearLayout.VERTICAL }
    private val listScroll = scrolling(list)
    private val input = TermuxWorkspaceInputEditText(kit.context).apply {
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_FULLSCREEN
        background = null; alpha = 0f; minHeight = 1; setPadding(0, 0, 0, 0)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        RetuiThemeBridge.applyToKeyboardInput(this, TermuxBridgeManager.TERMUX_HOME, "termux")
        setTextListener { typeText(it) }
        setBackspaceListener { key("BSpace") }
        setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_GO || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) { key("Enter"); true } else false
        }
        setOnKeyListener { _, keyCode, event -> hardwareKey(keyCode, event) }
        setOnFocusChangeListener { _, focused -> showTyping(focused) }
    }

    override val view: View = titled(kit.context.getString(R.string.mono_term_title), note, padH = 4, padV = 4).let { (frame, body) ->
        // The window's name sits right after the tab.
        val header = frame.getChildAt(0) as LinearLayout
        header.addView(name, 1, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        header.addView(doneRule, LinearLayout.LayoutParams(kit.line, -1))
        header.addView(done, LinearLayout.LayoutParams(-2, -1))
        body.addView(FrameLayout(kit.context).apply {
            addView(grid, FrameLayout.LayoutParams(-1, -1))
            addView(listScroll, FrameLayout.LayoutParams(-1, -1))
            addView(input, FrameLayout.LayoutParams(1, 1))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        frame
    }

    init {
        terms.add(this)
        changed()
    }

    val focused: Boolean get() = input.hasFocus()

    /** Over the home ([on]) the header offers done and a long press selects text to copy, or pastes. */
    fun lifted(on: Boolean) {
        done.visibility = if (on) View.VISIBLE else View.GONE
        doneRule.visibility = done.visibility
        grid.selectable = on
        grid.onPasteText = if (on) { text -> terminal?.paste(text) } else null
    }

    override fun changed() {
        if (released) return
        val known = terms.windows.firstOrNull { it.first == window }
        name.text = known?.second.orEmpty()
        when {
            terms.state != MonoTerms.State.READY -> { detach(); showList() }
            known == null || picking -> { detach(); showList() }
            terminal == null -> attach()
        }
        showTyping(focused)
    }

    private fun showTyping(typing: Boolean) {
        note.text = when {
            terminal == null -> ""
            typing -> kit.context.getString(R.string.mono_term_typing)
            else -> kit.context.getString(R.string.mono_term_tap)
        }
    }

    private fun showList() {
        grid.visibility = View.INVISIBLE
        listScroll.visibility = View.VISIBLE
        list.removeAllViews()
        fun line(text: String, color: Int = kit.ink) = list.addView(kit.text(text, MonoKit.SMALL_SP, color).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) })
        fun row(text: String, run: () -> Unit) = list.addView(kit.text(text, MonoKit.SMALL_SP).apply {
            setPadding(dp(4), dp(6), dp(4), dp(6))
            kit.pressable(this, framed = false)
            setOnClickListener { run() }
        }, LinearLayout.LayoutParams(-1, -2))
        val context = kit.context
        when (terms.state) {
            MonoTerms.State.IDLE, MonoTerms.State.CONNECTING -> line(context.getString(R.string.workspace_bridge_connecting), kit.dim)
            MonoTerms.State.SETUP -> {
                line(context.getString(R.string.floating_terminal_setup), kit.dim)
                row(context.getString(R.string.floating_terminal_open_workspace)) { TermuxWorkspaceActivity.open(context) }
            }
            MonoTerms.State.FAILED -> {
                line(terms.message.orEmpty(), kit.dim)
                row(context.getString(R.string.workspace_helper_retry)) { terms.connect() }
            }
            MonoTerms.State.READY -> {
                line(context.getString(R.string.mono_term_pick), kit.dim)
                terms.windows.forEach { (id, label) -> row(label) { pick(id) } }
                row(context.getString(R.string.floating_terminal_new_window)) {
                    terms.connection?.newWindow(null, null) { created -> if (!released) pick(created) }
                }
            }
        }
    }

    private fun pick(target: String) {
        picking = false
        if (target != window) { detach(); window = target; events.window(this, target) }
        changed()
    }

    /** Shows the windows to pick another. */
    fun chooseWindow() {
        picking = true
        changed()
    }

    private fun gridSize() = (grid.width / grid.characterWidth()).toInt().coerceIn(10, 240) to (grid.height / grid.lineHeight()).coerceIn(4, 120)

    private fun attach() {
        val client = terms.connection ?: return
        val target = window ?: return
        listScroll.visibility = View.GONE
        grid.visibility = View.VISIBLE
        if (grid.width == 0) { grid.post { if (!released && terminal == null && window == target) attach() }; return }
        val current = ++epoch
        val (cols, rows) = gridSize()
        size = cols to rows
        val term = TermuxWorkspaceTerminal(cols, rows, grid) { bytes -> if (epoch == current) write(bytes) }
        terminal = term
        fun valid() = !released && terms.connection === client && epoch == current
        client.attach(tile, target, cols, rows, { bytes ->
            if (valid()) {
                term.append(bytes)
                if (!renderPending) {
                    renderPending = true
                    grid.postOnAnimation { renderPending = false; if (valid()) grid.setEmulatorFrame(term.emulator) }
                }
            }
        }, {
            if (valid()) { detach(); grid.postDelayed({ if (!released && terminal == null) changed() }, 500) }
        })
        showTyping(focused)
    }

    private fun detach() {
        if (terminal == null) return
        epoch++
        renderPending = false
        terms.connection?.detach(tile)
        terminal = null
    }

    private fun resize() {
        val term = terminal ?: return
        val next = gridSize()
        if (next == size) return
        size = next
        term.resize(next.first, next.second, grid)
        terms.connection?.resize(tile, next.first, next.second)
        grid.setEmulatorFrame(term.emulator)
    }

    private fun focus() {
        if (terminal == null) return
        events.lift(this)
        input.requestFocus()
        RetuiThemeBridge.sendToKeyboard(kit.context, input, "retui_mono:${window.orEmpty()}", "termux")
        (kit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Stops typing here and puts the keyboard away. */
    fun unfocus() {
        if (!input.hasFocus()) return
        (kit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    /** A key from the band, such as Escape or Up, with any held modifiers. */
    fun key(token: String) {
        val term = terminal ?: return
        val modifiers = terms.modifiers
        val name = modifiers.joinToString("") { "$it-" } + token
        modifiers.clear()
        write(term.key(name))
    }

    private fun typeText(text: String) {
        val term = terminal ?: return
        if (terms.modifiers.isNotEmpty() && text.length == 1 && text[0].code in 32..126) { key(if (text == " ") "Space" else text); return }
        if (text.length > 1 && (text.contains('\n') || text.contains('\r'))) term.paste(text) else write(text.toByteArray(Charsets.UTF_8))
    }

    private fun write(bytes: ByteArray) {
        if (bytes.isNotEmpty()) terms.connection?.write(tile, bytes)
    }

    private fun hardwareKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return keyCode == KeyEvent.KEYCODE_ENTER
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> key("Enter")
            KeyEvent.KEYCODE_DEL -> key("BSpace")
            KeyEvent.KEYCODE_TAB -> key("Tab")
            KeyEvent.KEYCODE_ESCAPE -> key("Escape")
            KeyEvent.KEYCODE_DPAD_UP -> key("Up")
            KeyEvent.KEYCODE_DPAD_DOWN -> key("Down")
            KeyEvent.KEYCODE_DPAD_LEFT -> key("Left")
            KeyEvent.KEYCODE_DPAD_RIGHT -> key("Right")
            KeyEvent.KEYCODE_BACK -> return false
            else -> {
                val unicode = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
                if (unicode <= 0 || Character.isISOControl(unicode)) return false
                val text = String(Character.toChars(unicode))
                if ((event.isCtrlPressed || event.isAltPressed) && text.length == 1 && text[0].code in 32..126)
                    terminal?.let { write(it.key((if (event.isCtrlPressed) "C-" else "") + (if (event.isAltPressed) "M-" else "") + if (text == " ") "Space" else text)) }
                else typeText(text)
            }
        }
        return true
    }

    override fun release() {
        released = true
        unfocus()
        detach()
        terms.remove(this)
    }
}
