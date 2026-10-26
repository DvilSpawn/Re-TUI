package ohi.andre.consolelauncher.managers.termux

import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

/** Home tiles own views/connections; tmux owns processes even while this surface is paused. */
class TerminalHomeView(context: Context) : LinearLayout(context) {
    private val prefs = context.getSharedPreferences("terminal_home", Context.MODE_PRIVATE)
    private val layout = TerminalHomeLayout()
    private val main = Handler(Looper.getMainLooper())
    private val board = FrameLayout(context)
    private val scroll = ScrollView(context)
    private val status = TextView(context)
    private val runtimes = linkedMapOf<String, TileRuntime>()
    private var label: String? = null
    private var running = false
    private var registered = false
    private var active: TileRuntime? = null
    val hasTerminalFocus get() = active != null
    private var keyboardShown = false
    private var revealPending = false
    private var revealListener: androidx.core.view.OneShotPreDrawListener? = null
    private fun cancelReveal() { revealListener?.removeListener(); revealListener = null }
    private fun scheduleReveal() {
        if (!keyboardShown || !revealPending || revealListener != null) return
        revealListener = androidx.core.view.OneShotPreDrawListener.add(scroll) {
            revealListener = null
            val r = active
            if (keyboardShown && revealPending && r != null) {
                scroll.scrollTo(0, r.panel.top)
                revealPending = false
            }
        }
        scroll.invalidate()
    }
    private var editing: String? = null
    private val editor by lazy { TileEditor().apply { visibility = GONE } }
    private var creating: String? = null
    private var connection: TermuxWorkspaceSshConnection? = null
    private var nextChannel = 0
    private val syncRunnable = Runnable { syncTiles() }
    private var windows = emptyList<Pair<String, String>>()
    private val fg get() = ThemeColorResolver.color(Theme.workspace_output_text_color)
    private val bg get() = ThemeColorResolver.color(Theme.workspace_output_background_color)
    private val accent get() = ThemeColorResolver.color(Theme.workspace_panel_border_color)
    // Borderless themes make the panel border transparent; edit guides and handles must stay visible.
    private val editAccent get() = accent.let { if (it and 0xffffff == 0) fg else it } or 0xFF000000.toInt()
    private inner class TileRuntime(val tile: TerminalHomeLayout.Tile, val title: String, var command: String, var target: String?) {
        val panel = LinearLayout(context).apply { orientation = VERTICAL }
        val heading = TextView(context)
        val grid = TerminalGridView(context)
        val channel = nextChannel++
        var attached = false
        var epoch = 0
        var terminal: TermuxWorkspaceTerminal? = null
        var renderPending = false
        var ended = false
        var lastGeometry: Pair<Int, Int>? = null
        var previewX = 0f
        var previewY = 0f
        val sessionName get() = "retui-home-${tile.id}"
    }
    private val edgeMask = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }
    private var bottomChromeHeight = 0
    private var edgeMaskStart = -1f
    private var edgeMaskEnd = -1f
    private var edgeBlurNode: android.graphics.RenderNode? = null
    private var edgeSourceNode: android.graphics.RenderNode? = null
    private val edgeCut = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }
    private val edgeAdd = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    }
    private val modifiers = linkedSetOf<String>()
    private var fnMode = prefs.getBoolean("fn", false)
    private var specialKeys: View? = null
    var onTerminalFocusChanged: ((Boolean) -> Unit)? = null
    fun updateKeyboardVisibility(visible: Boolean) {
        if (keyboardShown == visible) return
        if (visible && !keyboardShown && active != null) revealPending = true
        keyboardShown = visible
        onTerminalFocusChanged?.invoke(visible && active != null)
        placeTiles(); scheduleReveal()
    }
    fun bindSpecialKeys(view: View) {
        specialKeys = view
        view.setBackgroundColor(ThemeColorResolver.color(Theme.workspace_output_background_color))
        refreshKeys()
    }
    private fun refreshKeys() {
        specialKeys?.let { TermuxSpecialKeys.bind(it, fnMode, modifiers,
            ThemeColorResolver.color(Theme.workspace_control_text_color),
            { fnMode = it; prefs.edit().putBoolean("fn", it).apply(); refreshKeys() },
            { active?.let { r -> detach(r); scheduleSync() } }, { sendKey(it) }) }
    }
    private val input = TermuxWorkspaceInputEditText(context)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || !running || label == null || intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) != label) return
            label = null
            val raw = intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty()
            if (intent.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1) != 0) {
                status.setText(R.string.terminal_home_setup); return
            }
            try {
                val info = TermuxWorkspaceSshSetup.parse(this@TerminalHomeView.context, raw)
                TermuxBridgeManager.startRunCommand(this@TerminalHomeView.context, TermuxBridgeManager.TERMUX_SH,
                    TermuxBridgeManager.TERMUX_HOME, null, TermuxWorkspaceSshSetup.daemonArguments(this@TerminalHomeView.context))
                openSsh(info)
            } catch (_: Exception) { status.setText(R.string.terminal_home_setup) }
        }
    }
    init {
        orientation = VERTICAL
        status.setTextColor(fg); status.setPadding(dp(10), 0, dp(10), dp(6)); status.setText(R.string.terminal_home_hint)
        status.setOnClickListener { pause(); resume() }
        addView(status)
        scroll.addView(board); addView(scroll, LayoutParams(-1, 0, 1f))
        board.addView(editor, FrameLayout.LayoutParams(-1, -1))
        input.setSingleLine(true); input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
        input.background = null; input.hint = ""; input.alpha = 0f; input.minHeight = 1; input.setPadding(0, 0, 0, 0)
        RetuiThemeBridge.applyToKeyboardInput(input, TermuxBridgeManager.TERMUX_HOME, "termux")
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.visibility = GONE
        input.setTextListener { text -> typeText(text) }
        input.setBackspaceListener { sendKey("BSpace") }
        input.setOnEditorActionListener { _, action, event ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_GO || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) { sendKey("Enter"); true } else false
        }
        input.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) keyCode == KeyEvent.KEYCODE_ENTER else when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> { sendKey("Enter"); true }
                KeyEvent.KEYCODE_DEL -> { sendKey("BSpace"); true }
                KeyEvent.KEYCODE_TAB -> { sendKey("Tab"); true }
                KeyEvent.KEYCODE_ESCAPE -> { sendKey("Escape"); true }
                KeyEvent.KEYCODE_DPAD_UP -> { sendKey("Up"); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { sendKey("Down"); true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { sendKey("Left"); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { sendKey("Right"); true }
                else -> {
                    val unicode = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
                    if (unicode > 0 && !Character.isISOControl(unicode)) {
                        val text = String(Character.toChars(unicode))
                        if ((event.isCtrlPressed || event.isAltPressed) && text.length == 1 && text[0].code in 32..126)
                            sendKey((if (event.isCtrlPressed) "C-" else "") + (if (event.isAltPressed) "M-" else "") + if (text == " ") "Space" else text)
                        else typeText(text)
                        true
                    } else false
                }
            }
        }
        addView(input, LayoutParams(1, 1))
        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (revealPending && bottom - top != oldBottom - oldTop) { placeTiles(); scheduleReveal() }
        }
        scroll.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                revealPending = false; cancelReveal()
            }
            false
        }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> scheduleSync() }
        restore()
        board.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ -> if (r - l != oldR - oldL) placeTiles() }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun cols(r: TileRuntime) = (r.grid.width / r.grid.characterWidth()).toInt().coerceIn(20, 240)
    private fun rows(r: TileRuntime) = (r.grid.height / r.grid.lineHeight()).coerceIn(8, 120)
    private fun sendKey(key: String) {
        val r = active ?: return
        val terminal = r.terminal ?: return
        val prefix = modifiers.joinToString("") { "$it-" }
        modifiers.clear(); refreshKeys()
        write(r, terminal.key(prefix + key))
    }
    private fun typeText(text: String) {
        val r = active ?: return
        if (modifiers.isNotEmpty()) {
            if (text.length == 1 && text[0].code in 32..126) { sendKey(if (text == " ") "Space" else text); return }
            modifiers.clear(); refreshKeys()
        }
        if (text.length > 1 && (text.contains('\n') || text.contains('\r'))) r.terminal?.paste(text)
        else write(r, text.toByteArray(Charsets.UTF_8))
    }
    private fun write(r: TileRuntime, bytes: ByteArray) {
        if (!r.ended && r.attached && connection?.write(r.channel, bytes) != true) status.setText(R.string.workspace_bridge_input_not_sent)
    }
    fun releaseInput(): Boolean {
        val hadFocus = active != null || editing != null
        revealPending = false; cancelReveal()
        editor.finishGesture(); editor.visibility = GONE
        status.setText(R.string.terminal_home_hint)
        active = null; editing = null; input.clearFocus(); input.visibility = GONE
        modifiers.clear(); refreshKeys(); onTerminalFocusChanged?.invoke(false)
        scheduleSync()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
        return hadFocus
    }
    private fun activate(r: TileRuntime) {
        if (r.ended || r.target == null) return
        if (active !== r || !keyboardShown) revealPending = true
        if (active !== r) { modifiers.clear(); refreshKeys() }
        editing = null; active = r; onTerminalFocusChanged?.invoke(true); input.visibility = VISIBLE; input.showSoftInputOnFocus = true; input.requestFocus()
        RetuiThemeBridge.sendToKeyboard(context, input, "retui_home:${r.target}", "termux")
        scheduleSync()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        placeTiles(); scheduleReveal()
    }
    fun resume() {
        if (running) return
        running = true
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT)); registered = true
        val ready = TermuxBridgeManager.status(context)
        if (!ready.termuxInstalled || !ready.runCommandDeclared || !ready.runCommandGranted) { status.setText(R.string.terminal_home_setup); return }
        if (!TermuxWorkspaceSshSetup.isPaired(context)) { status.setText(R.string.terminal_home_setup); return }
        label = "terminal-home:${UUID.randomUUID()}"
        try {
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                TermuxBridgeManager.createResultPendingIntent(context, label!!, null), TermuxWorkspaceSshSetup.commandArguments(context, false))
            val request = label
            main.postDelayed({ if (running && label == request) { label = null; status.setText(R.string.terminal_home_setup) } }, 15000)
        } catch (_: Exception) { label = null; status.setText(R.string.terminal_home_setup) }
    }
    fun pause() {
        runtimes[creating]?.ended = true
        running = false; label = null; creating = null
        releaseInput()
        connection?.close(); connection = null
        runtimes.values.forEach { detach(it) }
        if (registered) LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        registered = false; main.removeCallbacksAndMessages(null); save()
    }
    private fun connectTiles() { scheduleSync() }
    private fun openSsh(info: TermuxWorkspaceSshSetup.ConnectionInfo) {
        lateinit var client: TermuxWorkspaceSshConnection
        fun valid() = running && connection === client
        client = TermuxWorkspaceSshConnection(info, object : TermuxWorkspaceSshConnection.Listener {
            override fun onReady(windows: List<Pair<String, String>>) {
                if (!valid()) return
                runtimes.values.filter { it.target == null }.forEach { r ->
                    windows.firstOrNull { it.second.substringAfter(':') == r.sessionName }?.first?.let {
                        r.target = it; r.ended = false
                    }
                }
                updateWindows(windows)
                status.setText(R.string.terminal_home_hint); save(); scheduleSync()
            }
            override fun onWindows(windows: List<Pair<String, String>>) {
                if (valid()) { updateWindows(windows); scheduleSync() }
            }
            override fun onTargetGone(target: String) {
                if (!valid()) return
                runtimes.values.filter { it.target == target }.forEach {
                    detach(it); it.ended = true; it.heading.setText(R.string.terminal_home_exited)
                    if (it === active) releaseInput()
                }
                save(); scheduleSync()
            }
            override fun onError(message: String) {
                if (!valid()) return
                runtimes[creating]?.ended = true; creating = null
                client.close(); connection = null
                runtimes.values.forEach { detach(it); it.heading.setText(R.string.terminal_home_disconnected) }
                status.setText(R.string.terminal_home_background_help); save()
            }
        }, "retui_home")
        connection = client
        client.connect(runtimes.values.mapNotNull { r -> r.target?.let { it to r.sessionName } }.toMap())
    }
    private fun detach(r: TileRuntime) {
        r.epoch++; r.attached = false; r.renderPending = false
        connection?.detach(r.channel); r.terminal = null
    }
    private fun attach(r: TileRuntime, client: TermuxWorkspaceSshConnection) {
        val target = r.target ?: return
        val epoch = ++r.epoch
        val terminal = TermuxWorkspaceTerminal(cols(r), rows(r), r.grid) { bytes ->
            if (connection === client && r.epoch == epoch) write(r, bytes)
        }
        r.terminal = terminal; r.attached = true; r.lastGeometry = cols(r) to rows(r)
        fun valid() = running && connection === client && r.epoch == epoch
        client.attach(r.channel, target, cols(r), rows(r), { bytes ->
            if (valid()) {
                terminal.append(bytes); r.heading.text = r.title
                if (!r.renderPending) {
                    r.renderPending = true
                    r.grid.postOnAnimation {
                        r.renderPending = false
                        if (valid()) r.grid.setEmulatorFrame(terminal.emulator)
                    }
                }
            }
        }, {
            if (valid()) {
                detach(r)
                main.postDelayed({ if (running && connection === client) scheduleSync() }, 500)
            }
        })
    }
    private fun updateWindows(alive: List<Pair<String, String>>) {
        windows = alive
        runtimes.values.filter { it.target != null && alive.none { w -> w.first == it.target } }.forEach {
            it.ended = true; it.heading.setText(R.string.terminal_home_exited)
            if (it === active) releaseInput()
        }
    }
    private fun scheduleSync() {
        main.removeCallbacks(syncRunnable)
        if (running) main.post(syncRunnable)
    }
    private fun syncTiles() {
        val client = connection ?: return
        if (!running || !client.connected) return
        if (creating == null) runtimes.values.firstOrNull { it.target == null && !it.ended }?.let { r ->
            creating = r.tile.id
            client.newWindow(r.sessionName, r.command.ifBlank { null }) { target ->
                if (!running || connection !== client || runtimes[r.tile.id] !== r) return@newWindow
                r.target = target; r.ended = false
                creating = null; save(); scheduleSync()
            }
        }
        runtimes.values.forEach { r ->
            val visible = !r.ended && r.target != null && r.grid.width > 0 &&
                (r === active || r.panel.bottom + r.panel.translationY > scroll.scrollY && r.panel.top + r.panel.translationY < scroll.scrollY + scroll.height)
            if (!visible) { if (r.attached) detach(r) }
            else if (!r.attached) attach(r, client)
            else {
                val size = cols(r) to rows(r)
                if (r.lastGeometry != size) {
                    r.lastGeometry = size
                    r.terminal?.resize(size.first, size.second, r.grid)
                    client.resize(r.channel, size.first, size.second)
                }
            }
        }
    }
    fun setSearchBarClearance(bottom: Int, chromeHeight: Int = bottom) {
        if (bottomChromeHeight != chromeHeight) {
            bottomChromeHeight = chromeHeight
            invalidate()
        }
        scroll.clipToPadding = false
        if (scroll.paddingBottom != bottom) {
            scroll.setPadding(0, 0, 0, bottom)
            invalidate()
        }
    }
    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        if (android.os.Build.VERSION.SDK_INT < 31 || !canvas.isHardwareAccelerated ||
            width <= 0 || height <= 0 || scroll.paddingBottom == 0 || bottomChromeHeight <= 0) {
            super.dispatchDraw(canvas); return
        }
        val start = (height - scroll.paddingBottom).coerceAtLeast(0).toFloat()
        val end = (start + bottomChromeHeight).coerceAtMost(height.toFloat())
        if (end <= start) { super.dispatchDraw(canvas); return }
        if (edgeMaskStart != start || edgeMaskEnd != end) {
            edgeMask.shader = android.graphics.LinearGradient(0f, start, 0f, end,
                Color.TRANSPARENT, Color.WHITE, android.graphics.Shader.TileMode.CLAMP)
            edgeCut.shader = edgeMask.shader
            edgeMaskStart = start; edgeMaskEnd = end
        }
        // Record children once: sharp and blurred layers must show the same scrolling frame.
        val source = edgeSourceNode ?: android.graphics.RenderNode("terminal-edge-source").also { edgeSourceNode = it }
        source.setPosition(0, 0, width, height)
        val frame = source.beginRecording(width, height)
        try { super.dispatchDraw(frame) } finally { source.endRecording() }
        val node = edgeBlurNode ?: android.graphics.RenderNode("terminal-bottom-blur").also {
            it.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                dp(18).toFloat(), dp(18).toFloat(), android.graphics.Shader.TileMode.CLAMP))
            edgeBlurNode = it
        }
        val top = (start.toInt() - dp(36)).coerceAtLeast(0)
        node.setPosition(0, top, width, height)
        val recording = node.beginRecording(width, height - top)
        try {
            recording.translate(0f, -top.toFloat())
            recording.drawRenderNode(source)
        } finally { node.endRecording() }
        val sharp = canvas.save()
        canvas.clipOutRect(0f, start, width.toFloat(), end)
        canvas.drawRenderNode(source)
        canvas.restoreToCount(sharp)
        val bottom = canvas.saveLayer(0f, start, width.toFloat(), end, null)
        canvas.clipRect(0f, start, width.toFloat(), end)
        canvas.drawRenderNode(source)
        canvas.drawRect(0f, start, width.toFloat(), height.toFloat(), edgeCut)
        // Add complementary premultiplied layers: no sharp border underneath, no opacity dip.
        val blur = canvas.saveLayer(0f, start, width.toFloat(), height.toFloat(), edgeAdd)
        canvas.drawRenderNode(node)
        canvas.drawRect(0f, start, width.toFloat(), height.toFloat(), edgeMask)
        canvas.restoreToCount(blur)
        canvas.restoreToCount(bottom)
    }
    fun showAddTerminal() {
        releaseInput()
        val names = arrayOf(context.getString(R.string.terminal_home_shell), "Clock", "Weather", "Dashboard", context.getString(R.string.terminal_home_custom), context.getString(R.string.terminal_home_existing), context.getString(R.string.terminal_home_setup_button), "Re:TUI Fetch")
        AlertDialog.Builder(context).setTitle(R.string.terminal_home_add).setItems(names) { _, which ->
            when (which) {
                0 -> add("Shell", "", null)
                1 -> add("Clock", widgetCommand("clock", ""), null)
                2, 3 -> {
                    val location = EditText(context).apply { hint = context.getString(R.string.terminal_home_location) }
                    AlertDialog.Builder(context).setTitle(names[which]).setView(location).setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val value = location.text.toString().trim()
                            if (value.isNotEmpty()) add(names[which], widgetCommand(if (which == 2) "weather" else "dashboard", value), null)
                        }.show()
                }
                4 -> {
                    val command = EditText(context).apply { hint = "htop" }
                    AlertDialog.Builder(context).setTitle(R.string.terminal_home_custom).setView(command).setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ -> if (command.text.isNotBlank()) add("Terminal", command.text.toString(), null) }.show()
                }
                7 -> add("Re:TUI Fetch", widgetCommand("fetch", ""), null)
                6 -> TermuxWorkspaceActivity.open(context)
                5 -> {
                    val available = windows.filter { w -> runtimes.values.none { it.target == w.first } }
                    AlertDialog.Builder(context).setTitle(R.string.terminal_home_existing)
                        .setItems(available.map { it.second }.toTypedArray()) { _, index -> add(available[index].second, "", available[index].first) }.show()
                }
            }
        }.show()
    }
    private fun widgetCommand(mode: String, location: String): String {
        val source = context.assets.open("terminal-widgets/dashboard.sh").bufferedReader().use { it.readText() }
        return TerminalWidgetCommand.create(source, mode, location)
    }
    private fun add(title: String, command: String, target: String?) {
        val tile = layout.add(UUID.randomUUID().toString()) ?: run { Toast.makeText(context, R.string.terminal_home_full, Toast.LENGTH_SHORT).show(); return }
        render(TileRuntime(tile, title, command, target)); placeTiles(); save(); connectTiles()
    }
    fun refreshAppearance() {
        setBackgroundColor(bg)
        status.setTextColor(fg)
        runtimes.values.forEach(::styleTile)
        refreshKeys()
        RetuiThemeBridge.applyToKeyboardInput(input, TermuxBridgeManager.TERMUX_HOME, "termux")
        if (input.hasFocus()) RetuiThemeBridge.sendToKeyboard(context,input,TermuxBridgeManager.TERMUX_HOME,"termux")
    }

    private fun styleTile(r: TileRuntime) {
        r.heading.text = r.title; r.heading.setTextColor(fg); r.heading.typeface = Typeface.MONOSPACE
        r.heading.setPadding(dp(6), 0, dp(6), 0)
        r.panel.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(bg); cornerRadius = dp(6).toFloat()
        }
        val styledFrame = TerminalBorderRuntime.panelDrawable(context, Color.TRANSPARENT, accent,
            1.2f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        r.panel.foreground = if (AppearanceSettings.cyberdeckMode() || AppearanceSettings.dashedBorders() ||
            TerminalBorderRuntime.customFrameActive(context, FrameTarget.OVERLAYS)) styledFrame
        else android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.TRANSPARENT); setStroke(dp(1), accent); cornerRadius = dp(6).toFloat()
        }
        r.grid.updateThemeColors(fg, bg, accent)
        r.grid.setTerminalTypeface(Tuils.getTypeface(context)); r.grid.setTerminalTextSizeSp(LauncherFontScale.terminalTextSizeSp())
    }

    private fun render(r: TileRuntime) {
        runtimes[r.tile.id] = r
        styleTile(r)
        r.panel.addView(r.heading, LayoutParams(-1, dp(30))); r.panel.addView(r.grid, LayoutParams(-1, 0, 1f))
        fun bindGesture(view: View, tap: () -> Unit) {
            val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onSingleTapUp(e: MotionEvent): Boolean { tap(); return true }
                override fun onLongPress(e: MotionEvent) { edit(r) }
            })
            view.setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                if (editing == r.tile.id && event.actionMasked != MotionEvent.ACTION_DOWN) editor.touch(event)
                true
            }
        }
        bindGesture(r.heading) { }
        bindGesture(r.grid) { activate(r) }
        r.grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val geometry = cols(r) to rows(r)
            if (geometry != r.lastGeometry) {
                scheduleSync()
            }
        }
        board.addView(r.panel)
    }
    private fun edit(r: TileRuntime) {
        releaseInput()
        AlertDialog.Builder(context).setTitle(r.title).setItems(arrayOf(context.getString(R.string.terminal_home_move_resize), context.getString(R.string.terminal_home_reconnect), context.getString(R.string.terminal_home_remove))) { _, index ->
            when (index) {
                0 -> arrange(r)
                1 -> { editing = null; if (r.target !in windows.map { it.first }) { r.target = null; r.ended = false }; pause(); resume() }
                2 -> { if (active === r) releaseInput(); detach(r); if (creating == r.tile.id) creating = null; runtimes.remove(r.tile.id); layout.tiles.remove(r.tile); board.removeView(r.panel); editing = null; save(); placeTiles(); scheduleSync() }
            }
        }.setNegativeButton(android.R.string.cancel) { _, _ -> editing = null }.show()
    }
    private fun arrange(r: TileRuntime) {
        releaseInput(); editing = r.tile.id
        status.setText(R.string.terminal_home_arrange)
        editor.visibility = VISIBLE; placeTiles()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Dashboard-owned editing layer keeps gestures out of terminal input. */
    private inner class TileEditor : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var origin: TerminalHomeLayout.Tile? = null
        private var preview: TerminalHomeLayout.Tile? = null
        private var original = emptyList<TerminalHomeLayout.Tile>()
        private var flow = emptyList<TerminalHomeLayout.Tile>()
        val previewBottom get() = flow.maxOfOrNull { it.y + it.height } ?: 0
        fun previewTile(id: String) = if (dragging && edges == 0) flow.firstOrNull { it.id == id } else null
        private var startX = 0f; private var startY = 0f
        private var fingerX = 0f; private var fingerY = 0f
        private var scrollStart = 0
        private var edges = 0
        private var dragging = false
        private var held = false
        private var edgeStep = 0
        private val edgeScroll = object : Runnable {
            override fun run() {
                if (!held || edgeStep == 0) return
                scroll.scrollBy(0, edgeStep)
                updateGesture(); postDelayed(this, 16)
            }
        }
        private fun rect(t: TerminalHomeLayout.Tile) = android.graphics.RectF(
            t.x * cellWidth() + dp(4), t.y * cellHeight() + dp(4),
            (t.x + t.width) * cellWidth() - dp(4), (t.y + t.height) * cellHeight() - dp(4))
        private fun stroke(canvas: android.graphics.Canvas, r: android.graphics.RectF, color: Int, thickness: Int) {
            paint.color = color; paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = dp(thickness).toFloat()
            canvas.drawRect(r, paint)
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val selected = runtimes[editing] ?: return
            val cw = cellWidth(); val ch = cellHeight()
            val phase = (android.os.SystemClock.uptimeMillis() % 1600L) / 1600.0
            val alpha = (64 + 80 * (0.5 - 0.5 * kotlin.math.cos(phase * 2 * Math.PI))).roundToInt()
            paint.color = (editAccent and 0x00ffffff) or (alpha shl 24)
            paint.style = android.graphics.Paint.Style.FILL
            val half = resources.displayMetrics.density * 1.5f
            val firstRow = (scroll.scrollY / ch).toInt()
            val lastRow = ((scroll.scrollY + scroll.height) / ch).toInt() + 1
            for (y in firstRow..lastRow) for (x in 0..6) {
                val px = (x*cw).coerceIn(half, width-half)
                val py = (y*ch).coerceIn(half, height-half)
                canvas.drawRect(px-half, py-half, px+half, py+half, paint)
            }
            postInvalidateOnAnimation()
            preview?.let {
                val r = rect(it)
                paint.style = android.graphics.Paint.Style.FILL; paint.color = (editAccent and 0x00ffffff) or 0x33000000
                canvas.drawRect(r, paint); stroke(canvas, r, editAccent, 2)
            }
            val frame = rect(if (dragging && edges == 0) origin ?: selected.tile else selected.tile)
            if (dragging && edges == 0) {
                frame.offset(fingerX - startX, fingerY - startY + scroll.scrollY - scrollStart)
                val layer = canvas.saveLayerAlpha(frame, 217)
                canvas.translate(frame.left, frame.top)
                canvas.scale(0.96f, 0.96f, frame.width()/2, frame.height()/2)
                selected.panel.draw(canvas)
                canvas.restoreToCount(layer)
            }
            stroke(canvas, frame, editAccent, 3)
            if (!dragging || edges != 0) {
                for ((x,y) in listOf(frame.centerX() to frame.top, frame.centerX() to frame.bottom,
                    frame.left to frame.centerY(), frame.right to frame.centerY())) {
                    paint.style = android.graphics.Paint.Style.FILL; paint.color = bg
                    canvas.drawCircle(x,y,dp(9).toFloat(),paint)
                    paint.color = editAccent; canvas.drawCircle(x,y,dp(7).toFloat(),paint)
                }
            }
        }
        fun touch(event: MotionEvent): Boolean {
            val r = runtimes[editing] ?: return false
            val location = IntArray(2); scroll.getLocationOnScreen(location)
            fingerX = event.rawX - location[0]
            fingerY = event.rawY - location[1]
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val frame = rect(r.tile)
                    val y = fingerY + scroll.scrollY
                    val gx = minOf(dp(36).toFloat(), frame.width()/3)
                    val gy = minOf(dp(20).toFloat(), frame.height()/3)
                    val grab = dp(20)
                    if (fingerX < frame.left-grab || fingerX > frame.right+grab || y < frame.top-grab || y > frame.bottom+grab) {
                        releaseInput(); return true
                    }
                    edges = 0
                    if (fingerX < frame.left+gx) edges = edges or 1
                    if (fingerX > frame.right-gx) edges = edges or 2
                    if (y < frame.top+gy) edges = edges or 4
                    if (y > frame.bottom-gy) edges = edges or 8
                    startX = fingerX; startY = fingerY; scrollStart = scroll.scrollY
                    origin = r.tile.copy(); original = layout.tiles.map { it.copy() }; held = true; dragging = false
                    scroll.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (held) {
                    updateGesture()
                    val step = if (fingerY < dp(48)) -dp(12) else if (fingerY > scroll.height-dp(48)) dp(12) else 0
                    if (step != edgeStep) { removeCallbacks(edgeScroll); edgeStep = step; if (step != 0) post(edgeScroll) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP && flow.isNotEmpty()) layout.applyPlacement(layout.compact(flow))
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL && original.isNotEmpty()) layout.applyPlacement(original)
                    val moved = dragging && edges == 0 && event.actionMasked == MotionEvent.ACTION_UP
                    finishGesture(); placeTiles(); save()
                    if (moved) releaseInput()
                }
            }
            invalidate(); return true
        }
        private fun updateGesture() {
            val r = runtimes[editing] ?: return
            val from = origin ?: return
            val dx = fingerX-startX
            val dy = fingerY-startY+scroll.scrollY-scrollStart
            if (!dragging && kotlin.math.hypot(dx,dy) < ViewConfiguration.get(context).scaledTouchSlop) return
            dragging = true
            r.panel.alpha = if (edges == 0) 0f else 1f
            val cx = (dx/cellWidth()).roundToInt(); val cy = (dy/cellHeight()).roundToInt()
            if (edges != 0) {
                val left = if (edges and 1 != 0) (from.x+cx).coerceIn(0,from.x+from.width-2) else from.x
                val right = if (edges and 2 != 0) (from.x+from.width+cx).coerceIn(from.x+2,6) else from.x+from.width
                val top = if (edges and 4 != 0) (from.y+cy).coerceIn(maxOf(0,from.y+from.height-12),from.y+from.height-2) else from.y
                val bottom = if (edges and 8 != 0) (from.y+from.height+cy).coerceIn(from.y+2,from.y+12) else from.y+from.height
                flow = layout.reflow(from.copy(x=left,y=top,width=right-left,height=bottom-top), original).orEmpty()
                if (flow.isNotEmpty()) { layout.applyPlacement(flow); placeTiles() }
            } else {
                val proposed = from.copy(x=(from.x+cx).coerceIn(0,6-from.width),y=(from.y+cy).coerceAtLeast(0))
                flow = layout.reflow(proposed, original).orEmpty()
                preview = flow.firstOrNull { it.id == r.tile.id }
                placeTiles()
            }
            invalidate()
        }
        fun finishGesture() {
            runtimes.values.forEach {
                it.panel.animate().cancel(); it.panel.alpha = 1f
                it.panel.translationX = 0f; it.panel.translationY = 0f
                it.previewX = 0f; it.previewY = 0f
            }
            held = false; dragging = false; preview = null; flow = emptyList(); original = emptyList(); origin = null
            edgeStep = 0; removeCallbacks(edgeScroll)
            scroll.requestDisallowInterceptTouchEvent(false)
        }
        override fun onTouchEvent(event: MotionEvent) = touch(event)
        override fun onDetachedFromWindow() { finishGesture(); super.onDetachedFromWindow() }
    }
    private fun cellWidth() = (board.width.coerceAtLeast(dp(300)) / 6f)
    private fun cellHeight() = dp(65).toFloat()
    private fun placeTiles() {
        val cw = cellWidth(); val ch = cellHeight()
        val contentBottom = ((layout.tiles.maxOfOrNull { it.y + it.height } ?: 6) * ch).roundToInt()
        // Stop at the real content edge so the last pane rests above the keyboard controls.
        val boardHeight = maxOf(scroll.height, contentBottom, (editor.previewBottom * ch).roundToInt())
        // ScrollView measures its child with unspecified height; layoutParams alone cannot add empty scroll space.
        if (board.minimumHeight != boardHeight) board.minimumHeight = boardHeight
        if (board.layoutParams?.height != boardHeight) {
            board.layoutParams = (board.layoutParams ?: FrameLayout.LayoutParams(-1, -1)).apply { height = boardHeight }
        }
        editor.layoutParams = FrameLayout.LayoutParams(board.width.coerceAtLeast(dp(300)), boardHeight)
        editor.bringToFront(); editor.invalidate()
        if (android.os.Build.VERSION.SDK_INT >= 29) editor.systemGestureExclusionRects = runtimes[editing]?.let { r ->
            listOf(android.graphics.Rect(0,r.panel.top+r.panel.height/2-dp(80),dp(48),r.panel.top+r.panel.height/2+dp(80)),
                android.graphics.Rect(board.width-dp(48),r.panel.top+r.panel.height/2-dp(80),board.width,r.panel.top+r.panel.height/2+dp(80)))
        } ?: emptyList()
        runtimes.values.forEach { r ->
            val preview = editor.previewTile(r.tile.id)?.takeIf { r.tile.id != editing }
            val px = ((preview?.x ?: r.tile.x) - r.tile.x) * cw
            val py = ((preview?.y ?: r.tile.y) - r.tile.y) * ch
            if (px != r.previewX || py != r.previewY) {
                r.previewX = px; r.previewY = py
                r.panel.animate().translationX(px).translationY(py).setDuration(120).start()
            }
            r.panel.layoutParams = FrameLayout.LayoutParams((r.tile.width * cw).roundToInt() - dp(8), (r.tile.height * ch).roundToInt() - dp(8)).apply {
                leftMargin = (r.tile.x * cw).roundToInt() + dp(4); topMargin = (r.tile.y * ch).roundToInt() + dp(4)
            }
        }
    }
    private fun save() {
        val array = JSONArray()
        runtimes.values.forEach { r -> array.put(JSONObject().put("id", r.tile.id).put("x", r.tile.x).put("y", r.tile.y)
            .put("w", r.tile.width).put("h", r.tile.height).put("title", r.title).put("command", r.command).put("target", r.target).put("ended", r.ended)) }
        prefs.edit().putString("tiles", array.toString()).apply()
    }
    private fun restore() {
        val array = runCatching { JSONArray(prefs.getString("tiles", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until array.length()) runCatching {
            val o = array.getJSONObject(i)
            val tile = TerminalHomeLayout.Tile(o.getString("id"), o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h"))
            if (tile.id !in runtimes && layout.fits(tile)) {
                layout.tiles += tile
                val savedCommand = o.getString("command")
                render(TileRuntime(tile, o.getString("title"), savedCommand, o.optString("target").takeIf { it.matches(Regex("@[0-9]+")) }).apply {
                    ended = o.optBoolean("ended", false)
                })
            }
        }
        board.post { placeTiles() }
    }
}
