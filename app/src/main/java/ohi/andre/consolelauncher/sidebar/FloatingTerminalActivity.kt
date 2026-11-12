package ohi.andre.consolelauncher.sidebar

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.termux.TerminalGridView
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.managers.termux.TermuxSpecialKeys
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceActivity
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceInputEditText
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSshConnection
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSshSetup
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceTerminal
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.UUID
import kotlin.math.roundToInt

/**
 * One tmux window of the workspace in a pane over the current app, for quick jobs such as pasting a link
 * copied a moment ago. It opens on a picker of the workspace's windows; tmux keeps the shells running
 * when the pane closes. The Terminal mode key band sits above the keyboard.
 */
class FloatingTerminalActivity : Activity() {
    private companion object {
        const val CHANNEL = 0
        const val SETUP_TIMEOUT_MS = 15000L
    }

    private val context: Context get() = this
    private val main = Handler(Looper.getMainLooper())
    private val fg get() = ThemeColorResolver.color(Theme.workspace_output_text_color)
    private val bg get() = ThemeColorResolver.color(Theme.workspace_output_background_color)
    private val accent get() = ThemeColorResolver.color(Theme.workspace_panel_border_color)
    private val prefs by lazy { getSharedPreferences("floating_terminal", Context.MODE_PRIVATE) }

    private lateinit var column: LinearLayout
    private lateinit var root: LinearLayout
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var picker: LinearLayout
    private lateinit var pickerScroll: ScrollView
    private lateinit var grid: TerminalGridView
    private lateinit var band: View
    private lateinit var input: TermuxWorkspaceInputEditText
    private var paneHeight = 0
    private var offsetY = 0
    private var imeBottom = 0

    private var started = false
    private var label: String? = null
    private var connection: TermuxWorkspaceSshConnection? = null
    private var terminal: TermuxWorkspaceTerminal? = null
    private var tmuxWindows = emptyList<Pair<String, String>>()
    private var target: String? = null
    private var epoch = 0
    private var renderPending = false
    private var size: Pair<Int, Int>? = null
    private val modifiers = linkedSetOf<String>()
    private var fnMode = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent == null || !started || label == null || intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) != label) return
            label = null
            val raw = intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty()
            if (intent.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1) != 0) { needsSetup(); return }
            try {
                val info = TermuxWorkspaceSshSetup.parse(context, raw)
                TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH,
                    TermuxBridgeManager.TERMUX_HOME, null, TermuxWorkspaceSshSetup.daemonArguments(context))
                openSsh(info)
            } catch (_: Exception) { needsSetup() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fnMode = prefs.getBoolean("fn", false)
        val metrics = resources.displayMetrics
        paneHeight = (metrics.heightPixels * 0.46f).toInt()
        offsetY = (metrics.heightPixels * 0.04f).toInt()
        buildViews()
        setContentView(column)
    }

    override fun onStart() {
        super.onStart()
        started = true
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT))
        connect()
    }

    override fun onStop() {
        started = false; label = null
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        main.removeCallbacksAndMessages(null)
        detach()
        connection?.close(); connection = null
        super.onStop()
    }

    private fun updateBand() {
        if (!::band.isInitialized) return
        band.visibility = if (imeBottom > 0 && terminal != null) View.VISIBLE else View.GONE
    }

    /** The pane keeps its height unless the keyboard and key band leave too little room. */
    private fun fitPane() {
        val pad = column.paddingTop + column.paddingBottom
        val bandHeight = if (band.visibility == View.VISIBLE) band.measuredHeight.takeIf { it > 0 } ?: dp(72) else 0
        val room = column.height - pad - bandHeight - dp(12)
        val maxOffset = (room - dp(160)).coerceAtLeast(0)
        offsetY = offsetY.coerceIn(0, maxOffset)
        val height = paneHeight.coerceAtMost((room - offsetY).coerceAtLeast(dp(160)))
        (root.layoutParams as LinearLayout.LayoutParams).let {
            if (it.height != height || it.topMargin != offsetY) { it.height = height; it.topMargin = offsetY; root.layoutParams = it }
        }
    }

    private fun buildViews() {
        val typeface = Tuils.getTypeface(context)
        fun text(value: String, sizeSp: Float = 14f) = TextView(context).apply {
            text = value; setTextColor(fg); setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface?.let { this.typeface = it }
        }
        fun button(value: String, onClick: () -> Unit) = text(value).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8)); setOnClickListener { onClick() }
        }

        title = text(context.getString(R.string.floating_terminal_title)).apply {
            setTypeface(typeface, Typeface.BOLD); setPadding(dp(12), dp(8), dp(4), dp(8))
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(button(context.getString(R.string.floating_terminal_paste)) { paste() })
            addView(button(context.getString(R.string.floating_terminal_windows)) { showPicker() })
            addView(button("×") { finish() })
            setOnTouchListener(DragPane())
        }
        status = text("", 12f).apply { alpha = 0.7f; setPadding(dp(12), 0, dp(12), dp(4)) }
        picker = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        pickerScroll = ScrollView(context).apply { addView(picker) }
        grid = TerminalGridView(context).apply {
            updateThemeColors(fg, bg, accent)
            setTerminalTypeface(typeface); setTerminalTextSizeSp(LauncherFontScale.terminalTextSizeSp())
            visibility = View.GONE
            setOnClickListener { focusInput() }
            onPasteText = { text -> terminal?.paste(text) }
            addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob -> if (r - l != or_ - ol || b - t != ob - ot) resizeTerminal() }
        }
        val body = android.widget.FrameLayout(context).apply {
            addView(pickerScroll, android.widget.FrameLayout.LayoutParams(-1, -1))
            addView(grid, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        // The same special-key band as Terminal mode, above the keyboard while it is up.
        band = android.view.LayoutInflater.from(context).inflate(R.layout.termux_special_keys, null).apply {
            setBackgroundColor(bg); visibility = View.GONE
        }
        refreshKeys()

        input = TermuxWorkspaceInputEditText(context).apply {
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_FULLSCREEN
            background = null; alpha = 0f; minHeight = 1; setPadding(0, 0, 0, 0)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            RetuiThemeBridge.applyToKeyboardInput(this, TermuxBridgeManager.TERMUX_HOME, "termux")
            setTextListener { typeText(it) }
            setBackspaceListener { key("BSpace") }
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_GO || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) { key("Enter"); true } else false
            }
            setOnKeyListener { _, keyCode, event -> hardwareKey(keyCode, event) }
        }

        root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(bg); setStroke(dp(1), accent); cornerRadius = dp(12).toFloat() }
            clipToOutline = true
            isClickable = true
            setPadding(dp(4), dp(2), dp(4), dp(4))
            addView(header)
            addView(status)
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(input, LinearLayout.LayoutParams(1, 1))
        }
        val side = (resources.displayMetrics.widthPixels * 0.03f).toInt()
        column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(root, LinearLayout.LayoutParams(-1, paneHeight).apply { leftMargin = side; rightMargin = side; topMargin = offsetY })
            // A tap on the empty space below the pane closes it, like a tap outside a dialog.
            addView(View(context).apply { setOnClickListener { finish() } }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(band, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, imeBottom))
                updateBand()
                view.post { fitPane() }
                WindowInsetsCompat.CONSUMED
            }
            addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob -> if (b - t != ob - ot) post { fitPane() } }
        }
    }

    /** Dragging the header moves the pane up and down. */
    private inner class DragPane : View.OnTouchListener {
        private var lastY = 0f
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> lastY = event.rawY
                MotionEvent.ACTION_MOVE -> {
                    offsetY += (event.rawY - lastY).roundToInt()
                    lastY = event.rawY
                    fitPane()
                }
            }
            return true
        }
    }

    private fun connect() {
        val ready = TermuxBridgeManager.status(context)
        if (!ready.termuxInstalled || !ready.runCommandDeclared || !ready.runCommandGranted || !TermuxWorkspaceSshSetup.isPaired(context)) {
            needsSetup(); return
        }
        status.setText(R.string.workspace_bridge_connecting)
        val request = "floating-terminal:${UUID.randomUUID()}"
        label = request
        try {
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                TermuxBridgeManager.createResultPendingIntent(context, request, null), TermuxWorkspaceSshSetup.commandArguments(context, false))
            main.postDelayed({ if (started && label == request) { label = null; needsSetup() } }, SETUP_TIMEOUT_MS)
        } catch (_: Exception) { label = null; needsSetup() }
    }

    private fun openSsh(info: TermuxWorkspaceSshSetup.ConnectionInfo) {
        lateinit var client: TermuxWorkspaceSshConnection
        fun valid() = started && connection === client
        client = TermuxWorkspaceSshConnection(info, object : TermuxWorkspaceSshConnection.Listener {
            override fun onReady(windows: List<Pair<String, String>>) {
                if (!valid()) return
                tmuxWindows = windows
                status.text = ""
                if (target != null && tmuxWindows.any { it.first == target }) attach() else showPicker()
            }
            override fun onWindows(windows: List<Pair<String, String>>) {
                if (!valid()) return
                tmuxWindows = windows
                // tmux moves a viewer to another window when its own closes; never follow it there.
                if (target != null && windows.none { it.first == target }) { detach(); target = null; showPicker() }
                else if (pickerScroll.visibility == View.VISIBLE) showPicker() else updateTitle()
            }
            override fun onTargetGone(target: String) {
                if (valid() && target == this@FloatingTerminalActivity.target) { detach(); this@FloatingTerminalActivity.target = null; showPicker() }
            }
            override fun onError(message: String) {
                if (!valid()) return
                detach(); client.close(); connection = null
                status.text = message.lineSequence().first()
                showRetry()
            }
        })
        connection = client
        client.connect()
    }

    private fun showPicker() {
        detach()
        grid.visibility = View.GONE; pickerScroll.visibility = View.VISIBLE; updateBand()
        title.setText(R.string.floating_terminal_title)
        picker.removeAllViews()
        fun row(value: String, onClick: () -> Unit) = TextView(context).apply {
            text = value; setTextColor(fg); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            Tuils.getTypeface(context)?.let { typeface = it }
            setPadding(dp(14), dp(12), dp(14), dp(12)); setOnClickListener { onClick() }
            picker.addView(this)
        }
        if (connection?.connected == true) {
            for ((id, name) in tmuxWindows) row(name) { target = id; attach() }
            row(context.getString(R.string.floating_terminal_new_window)) {
                connection?.newWindow(null, null) { created -> if (started) { target = created; attach() } }
            }
        }
        row(context.getString(R.string.floating_terminal_open_workspace)) { TermuxWorkspaceActivity.open(context); finish() }
    }

    private fun needsSetup() {
        status.setText(R.string.floating_terminal_setup)
        picker.removeAllViews(); pickerScroll.visibility = View.VISIBLE; grid.visibility = View.GONE
        showPicker()
    }

    private fun showRetry() {
        showPicker()
        TextView(context).apply {
            setText(R.string.workspace_helper_retry); setTextColor(fg); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(14), dp(12), dp(14), dp(12)); setOnClickListener { connect() }
            picker.addView(this, 0)
        }
    }

    private fun updateTitle() {
        title.text = tmuxWindows.firstOrNull { it.first == target }?.second ?: context.getString(R.string.floating_terminal_title)
    }

    private fun gridSize() = (grid.width / grid.characterWidth()).toInt().coerceIn(20, 240) to (grid.height / grid.lineHeight()).coerceIn(8, 120)

    private fun attach() {
        val client = connection ?: return
        val window = target ?: return
        pickerScroll.visibility = View.GONE; grid.visibility = View.VISIBLE
        updateTitle()
        if (grid.width == 0) { grid.post { if (started && target == window) attach() }; return }
        detach()
        val current = ++epoch
        val (cols, rows) = gridSize()
        size = cols to rows
        val term = TermuxWorkspaceTerminal(cols, rows, grid) { bytes -> if (epoch == current) write(bytes) }
        terminal = term
        updateBand()
        fun valid() = started && connection === client && epoch == current
        client.attach(CHANNEL, window, cols, rows, { bytes ->
            if (valid()) {
                term.append(bytes)
                if (!renderPending) {
                    renderPending = true
                    grid.postOnAnimation { renderPending = false; if (valid()) grid.setEmulatorFrame(term.emulator) }
                }
            }
        }, {
            if (valid()) { detach(); main.postDelayed({ if (started && connection === client && target == window) attach() }, 500) }
        })
        // Once the picker has gone the pane relayouts; ask for the keyboard after that.
        column.postDelayed({ if (epoch == current) focusInput() }, 150)
    }

    private fun detach() {
        if (terminal == null) return
        epoch++; renderPending = false
        connection?.detach(CHANNEL); terminal = null
        updateBand()
    }

    private fun resizeTerminal() {
        val term = terminal ?: return
        val next = gridSize()
        if (next == size) return
        size = next
        term.resize(next.first, next.second, grid)
        connection?.resize(CHANNEL, next.first, next.second)
        grid.setEmulatorFrame(term.emulator)
    }

    private fun focusInput() {
        if (terminal == null) return
        input.requestFocus()
        RetuiThemeBridge.sendToKeyboard(context, input, "retui_floating:${target.orEmpty()}", "termux")
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun refreshKeys() {
        TermuxSpecialKeys.bind(band, fnMode, modifiers, ThemeColorResolver.color(Theme.workspace_control_text_color),
            { fnMode = it; prefs.edit().putBoolean("fn", it).apply(); refreshKeys() },
            { target?.let { detach(); attach() } }, { key(it) })
    }

    private fun key(token: String) {
        val term = terminal ?: return
        val name = modifiers.joinToString("") { "$it-" } + token
        if (modifiers.isNotEmpty()) { modifiers.clear(); refreshKeys() }
        write(term.key(name))
    }

    private fun typeText(text: String) {
        val term = terminal ?: return
        if (modifiers.isNotEmpty() && text.length == 1 && text[0].code in 32..126) { key(if (text == " ") "Space" else text); return }
        if (text.length > 1 && (text.contains('\n') || text.contains('\r'))) term.paste(text)
        else write(text.toByteArray(Charsets.UTF_8))
    }

    private fun paste() {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) terminal?.paste(text)
    }

    private fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (connection?.write(CHANNEL, bytes) != true) status.setText(R.string.workspace_bridge_input_not_sent)
        else if (status.text.isNotEmpty()) status.text = ""
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

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
}
