package ohi.andre.consolelauncher.managers.termux

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.view.doOnPreDraw
import androidx.core.view.OneShotPreDrawListener
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.BreathOutLayout
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.localization.LocalizedActivity
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.*
import java.util.UUID
import java.lang.ref.WeakReference
import kotlin.math.abs

/** An attached tmux surface. Closing views/transports never closes shells or jobs. */
class TermuxWorkspaceActivity : LocalizedActivity() {
    companion object {
        const val EXTRA_COMMAND = "workspace_command"
        private var foreground: WeakReference<TermuxWorkspaceActivity>? = null
        fun refreshIfRunning() {
            val activity = foreground?.get() ?: return
            activity.runOnUiThread {
                if (activity.visible && !activity.isFinishing && !activity.isDestroyed) activity.style()
            }
        }
        fun open(context: Context, command: String? = null) {
            context.startActivity(Intent(context, TermuxWorkspaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_COMMAND, command))
        }
    }
    private val prefs by lazy { getSharedPreferences("retui_workspace_surface", MODE_PRIVATE) }
    private lateinit var selection: TermuxWorkspaceState
    private lateinit var root: View
    private lateinit var screen: BreathOutLayout
    private lateinit var title: TextView
    private lateinit var input: TermuxWorkspaceInputEditText
    private lateinit var tiles: LinearLayout
    private val panels = arrayOfNulls<LinearLayout>(2)
    private val labels = arrayOfNulls<TextView>(2)
    private val grids = arrayOfNulls<TerminalGridView>(2)
    private val clients = arrayOfNulls<TermuxWorkspaceSocketClient>(2)
    private val lastGeometry = arrayOfNulls<Pair<Int, Int>>(2)
    private var geometryCapturePending = false
    private var geometryCaptureListener: OneShotPreDrawListener? = null
    private val frames = arrayOfNulls<TermuxWorkspaceFrame>(2)
    private val creating = BooleanArray(2)
    private val creationSeeds = arrayOfNulls<String>(2)
    private val creationNames = arrayOfNulls<String>(2)
    private val creationCommands = arrayOfNulls<String>(2)
    private var initialSelection = false
    private var windows = emptyList<Pair<String, String>>()
    private var bootstrap: TermuxWorkspaceFrame? = null
    private var visible = false
    private var generation = 0
    private var resultLabel: String? = null
    private var pendingCommand: String? = null
    private var localDraft: String? = null
    private var fnMode = false
    private val modifiers = linkedSetOf<String>()
    private val main = Handler(Looper.getMainLooper())
    private val instance = UUID.randomUUID().toString()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!visible || intent == null || resultLabel == null || intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) != resultLabel) return
            resultLabel = null
            val raw = intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty()
            val frame = try { TermuxWorkspaceFrame.parse(raw) } catch (e: Exception) { status(e.message.orEmpty()); return }
            if (frame.protocol != "retui-bridge-socket-v4" || frame.helperVersion != "0.4.1") {
                status(getString(R.string.workspace_helper_update_required)); return
            }
            if (frame.socket != TermuxWorkspaceSocketClient.SOCKET_NAME ||
                frame.token?.matches(Regex("[A-Za-z0-9_-]{1,128}")) != true ||
                frame.host != "127.0.0.1" || frame.port !in 1..65535) {
                status(getString(R.string.workspace_bridge_disconnected)); return
            }
            bootstrap = frame
            windows = frame.windows
            val previousTargets = selection.targets.toList()
            selection.reconcile(windows.map { it.first }.toSet())
            if (initialSelection) { selection.select(selection.focused, frame.target); initialSelection = false }
            if (selection.targets[0] == selection.targets[1]) selection.select(1 - selection.focused, null)
            if (previousTargets != selection.targets.toList()) clearTiles()
            applyLayout()
            selection.visibleTiles.forEach { tile -> if (selection.targets[tile] != null) connectTile(tile) }
            persist()
            if (windows.isEmpty()) status(getString(R.string.workspace_empty))
            runPendingCommand()
        }
    }

    override fun onCreate(state: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(state)
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        selection = TermuxWorkspaceState.restore(state?.getString("selection") ?: prefs.getString("selection", null))
        initialSelection = state == null && !prefs.contains("selection")
        localDraft = state?.getString("draft") ?: prefs.getString("draft", null)
        fnMode = prefs.getBoolean("fn", false)
        pendingCommand = if (state != null) state.getString("pending") else intent.getStringExtra(EXTRA_COMMAND)
        // Clearing the faded surface requires transparency before its first render.
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        screen = BreathOutLayout(this)
        val host = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        root = layoutInflater.inflate(R.layout.termux_workspace_page, host, false)
        host.addView(root, ViewGroup.LayoutParams(-1, -1))
        title = root.findViewById(R.id.termux_workspace_label)
        root.findViewById<View>(R.id.termux_workspace_page_previous).setOnClickListener { movePage(-1) }
        root.findViewById<View>(R.id.termux_workspace_page_next).setOnClickListener { movePage(1) }
        var pageStartX = 0f
        root.findViewById<View>(R.id.termux_workspace_page_position).setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> pageStartX = event.x
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - pageStartX
                    if (abs(dx) > dp(48)) movePage(if (dx < 0) 1 else -1) else view.performClick()
                }
            }
            true
        }
        input = root.findViewById(R.id.termux_workspace_input)
        RetuiThemeBridge.applyToKeyboardInput(input, TermuxBridgeManager.TERMUX_HOME, "termux")
        val output = root.findViewById<FrameLayout>(R.id.termux_workspace_output_panel)
        output.removeAllViews()
        output.setPadding(0, 0, 0, 0)
        tiles = LinearLayout(this)
        tiles.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { scheduleGeometryCapture() }
            override fun onViewDetachedFromWindow(v: View) { cancelGeometryCapture() }
        })
        output.addView(tiles, FrameLayout.LayoutParams(-1, -1))
        for (tile in 0..1) {
            val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, 0) }
            val label = TextView(this).apply {
                gravity = Gravity.CENTER_VERTICAL; typeface = Tuils.getTypeface(this@TermuxWorkspaceActivity)
                textSize = 10f; setPadding(dp(6), 0, dp(6), 0)
                setOnClickListener { focusTile(tile); showWindowChooser(tile, false) }
            }
            val grid = TerminalGridView(this).apply {
                setTerminalTypeface(Tuils.getTypeface(this@TermuxWorkspaceActivity))
                setTerminalTextSizeSp(LauncherFontScale.terminalTextSizeSp())
                updateThemeColors(color(Theme.workspace_output_text_color), color(Theme.workspace_output_background_color), color(Theme.workspace_panel_border_color))
            }
            panel.addView(label, LinearLayout.LayoutParams(-1, dp(24)))
            panel.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
            var startX = 0f; var startY = 0f
            grid.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; focusTile(tile) }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.x - startX; val dy = event.y - startY
                        if (abs(dx) > dp(72) && abs(dx) > abs(dy) * 1.4f) switchFocused(dx < 0)
                        else { view.performClick(); focusInput(true) }
                    }
                }
                true
            }
            grid.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) scheduleGeometryCapture()
            }
            panels[tile] = panel; labels[tile] = label; grids[tile] = grid
            tiles.addView(panel)
        }
        style()
        input.setTextListener { directText(it) }
        input.setBackspaceListener { inputBackspace() }
        input.setOnKeyListener { _, code, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener code == KeyEvent.KEYCODE_ENTER
            when (code) {
                KeyEvent.KEYCODE_ENTER -> { enter(); true }
                KeyEvent.KEYCODE_DEL -> { inputBackspace(); true }
                KeyEvent.KEYCODE_DPAD_UP -> { key("Up"); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { key("Down"); true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { key("Left"); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { key("Right"); true }
                KeyEvent.KEYCODE_ESCAPE -> { key("Escape"); true }
                KeyEvent.KEYCODE_TAB -> { key("Tab"); true }
                else -> {
                    val unicode = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
                    if (unicode > 0 && !Character.isISOControl(unicode)) {
                        val text = String(Character.toChars(unicode))
                        if ((event.isCtrlPressed || event.isAltPressed) && text.length == 1 && text[0].code in 32..126) key((if (event.isCtrlPressed) "C-" else "") + (if (event.isAltPressed) "M-" else "") + if (text == " ") "Space" else text)
                        else directText(text)
                        true
                    } else false
                }
            }
        }
        input.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_GO || (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) { enter(); true } else false
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val keyboardMargin = if (XMLPrefsManager.wasChanged(Ui.keyboard_visible_margin_override, false)) Ui.keyboard_visible_margin_override else Ui.display_margin_bottom_section
            val bottomMargin = XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(keyboardMargin), 4, 0)[3]
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom) + Tuils.mmToPx(resources.displayMetrics, bottomMargin))
            insets
        }
        applyLayout(); bindKeys(); setContentView(screen)
        focusInput(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        pendingCommand = intent.getStringExtra(EXTRA_COMMAND)
        runPendingCommand()
    }
    override fun onResume() {
        super.onResume(); visible = true; generation++
        foreground = WeakReference(this)
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT))
        style(); runPendingCommand(); if (!isFinishing) startBridge()
    }
    override fun onPause() {
        screen.setActive(false)
        if (foreground?.get() === this) foreground = null
        visible = false; generation++; resultLabel = null; bootstrap = null
        cancelGeometryCapture()
        main.removeCallbacksAndMessages(null)
        clients.indices.forEach { closeTile(it); creating[it] = false }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        persist(); super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::screen.isInitialized) screen.setActive(visible && hasFocus)
    }
    fun refreshBreathOutSettings() {
        if (!::screen.isInitialized) return
        val enabled = LauncherSettings.getBoolean(Behavior.breath_out)
        val wallpaper = LauncherSettings.getBoolean(Ui.system_wallpaper)
        if (wallpaper || enabled) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        screen.setBackgroundColor(color(if (wallpaper) Theme.wallpaper_overlay_color else Theme.background_color))
        screen.configure(enabled, LauncherSettings.getInt(Behavior.breath_out_seconds))
        screen.setActive(visible && hasWindowFocus())
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("selection", selection.encode()); out.putString("draft", localDraft); out.putString("pending", pendingCommand)
        persist(); super.onSaveInstanceState(out)
    }
    private fun persist() {
        prefs.edit().putString("selection", selection.encode()).putString("draft", localDraft).putBoolean("fn", fnMode).apply()
    }
    private fun startBridge() {
        if (!visible || resultLabel != null) return
        val ready = TermuxBridgeManager.status(this)
        if (!ready.termuxInstalled || !ready.runCommandDeclared) { status(getString(R.string.workspace_bridge_disconnected)); return }
        if (!ready.runCommandGranted) {
            TermuxBridgeManager.requestRunCommandPermissionIfPossible(this)
            status(getString(R.string.surface_runcommand_permission_is_not_granted_yet_allow_re_t_ui_and_dcbb9)); return
        }
        status(getString(R.string.workspace_bridge_connecting))
        val label = "workspace-activity:$instance:$generation"
        resultLabel = label
        val script = "export HOME='${TermuxBridgeManager.TERMUX_HOME}'; export PATH='/data/data/com.termux/files/usr/bin':\$PATH; " +
            "if command -v retui >/dev/null 2>&1 && [ \"\$(retui version)\" = 0.4.1 ]; then RETUI_NO_CREATE=1 retui bridge socket-start; else printf '%s\\n' 'retui helper update required'; fi"
        try {
            TermuxBridgeManager.startRunCommand(this, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                TermuxBridgeManager.createResultPendingIntent(this, label, null), arrayOf("-c", script, "retui-workspace"))
        } catch (e: Exception) { resultLabel = null; status(e.message.orEmpty()) }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (visible) startBridge()
    }
    private fun connectTile(tile: Int) {
        val boot = bootstrap ?: return
        if (!visible || clients[tile] != null || tile !in selection.visibleTiles) return
        if (selection.targets[tile] == null && !creating[tile]) return
        val ownTarget = if (creating[tile]) creationSeeds[tile] else selection.targets[tile]
        val geometry = if (creating[tile]) geometry(1 - tile) else geometry(tile)
        lateinit var client: TermuxWorkspaceSocketClient
        client = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
            override fun onFrame(raw: String) { main.post {
                if (!visible || clients[tile] !== client) return@post
                val frame = try { TermuxWorkspaceFrame.parse(raw) } catch (e: Exception) { status(e.message.orEmpty()); return@post }
                val target = frame.target ?: return@post
                if (creating[tile] && target == creationSeeds[tile]) return@post
                if (selection.targets[1 - tile] == target) return@post
                val creationCompleted = creating[tile]
                creating[tile] = false
                selection.select(tile, target); frames[tile] = frame; windows = frame.windows
                reconcileWindows()
                grids[tile]?.setFrame(frame.body, frame.cols, frame.rows, frame.cursorX, frame.cursorY)
                status(getString(R.string.workspace_bridge_ready)); updateLabels(); persist(); runPendingCommand()
                if (creationCompleted) scheduleGeometryCapture()
            } }
            override fun onStatus(status: String) { main.post {
                if (!visible || clients[tile] !== client) return@post
                if (creating[tile]) mutation(tile) { c, size -> c.newWindow(creationNames[tile], creationCommands[tile], size.first, size.second) }
                else capture(tile)
            } }
            override fun onTargetGone(target: String?) { main.post {
                if (!visible || clients[tile] !== client) return@post
                if (creating[tile]) mutation(tile) { c, size -> c.newWindow(creationNames[tile], creationCommands[tile], size.first, size.second) }
                else targetGone(target)
            } }
            override fun onError(message: String) { main.post {
                if (clients[tile] !== client) return@post
                closeTile(tile); creating[tile] = false; status(message)
            } }
            override fun onClosed() { main.post {
                if (clients[tile] !== client) return@post
                closeTile(tile); status(getString(R.string.workspace_bridge_disconnected))
            } }
        })
        clients[tile] = client
        client.connect(boot.socket!!, boot.token!!, geometry.first, geometry.second, boot.host, boot.port, true,
            ownTarget, targeted = true)
    }
    private fun closeTile(tile: Int) { clients[tile]?.close(); clients[tile] = null; lastGeometry[tile] = null }
    private fun clearTiles() {
        for (tile in 0..1) {
            closeTile(tile); frames[tile] = null; creating[tile] = false
            grids[tile]?.setFrame("", 80, 24, null, null)
        }
    }
    private fun movePage(delta: Int) {
        if (creating.any { it } || selection.pageCount < 2) return
        clearTiles(); selection.movePage(delta); applyLayout()
    }
    private fun targetGone(target: String?) {
        val alive = windows.map { it.first }.filter { it != target }.toSet()
        clearTiles(); selection.reconcile(alive); windows = windows.filter { it.first in alive }
        applyLayout()
        status(getString(if (alive.isEmpty()) R.string.workspace_empty else R.string.workspace_shell_exited))
    }
    private fun reconcileWindows() {
        if (creating.any { it }) return
        val previous = selection.targets.toList()
        selection.reconcile(windows.map { it.first }.toSet())
        if (previous != selection.targets.toList()) { clearTiles(); applyLayout() }
    }
    private fun geometry(tile: Int): Pair<Int, Int> {
        val grid = grids[tile]!!
        return (grid.width / grid.characterWidth()).toInt().coerceIn(20, 240) to (grid.height / grid.lineHeight()).coerceIn(8, 120)
    }
    private fun capture(tile: Int, onlyIfChanged: Boolean = false) {
        val client = clients[tile]
        if (visible && client?.connected == true && !creating[tile] && tile in selection.visibleTiles) {
            val size = geometry(tile)
            if ((!onlyIfChanged || size != lastGeometry[tile]) && client.capture(size.first, size.second)) lastGeometry[tile] = size
        }
    }
    private fun scheduleGeometryCapture() {
        if (geometryCapturePending) return
        if (!visible || !tiles.isAttachedToWindow) return
        geometryCapturePending = true
        // Font metrics can change without pixel bounds changing. Wait for final layout.
        geometryCaptureListener = tiles.doOnPreDraw {
            geometryCaptureListener = null
            geometryCapturePending = false
            selection.visibleTiles.forEach { capture(it, onlyIfChanged = true) }
        }
    }
    private fun cancelGeometryCapture() {
        geometryCaptureListener?.removeListener()
        geometryCaptureListener = null
        geometryCapturePending = false
    }
    private fun mutation(tile: Int = selection.focused, send: (TermuxWorkspaceSocketClient, Pair<Int, Int>) -> Boolean) {
        val client = clients[tile]
        val size = geometry(tile)
        if (!visible || client?.connected != true || !send(client, size)) status(getString(R.string.workspace_bridge_input_not_sent))
        else lastGeometry[tile] = size
    }
    private fun directText(text: String) {
        if (text.isEmpty() || screen.wake()) return
        if (localDraft != null) { localDraft += text; updateLabels(); persist(); return }
        if (text.startsWith(":")) { localDraft = text.drop(1); updateLabels(); persist(); return }
        if (modifiers.isNotEmpty()) {
            if (text.length == 1 && text[0].code in 32..126) { key(if (text == " ") "Space" else text); return }
            // KEY accepts ASCII tokens only. Unicode/composed text and paste are literal
            // TYPE input; consume the latch rather than applying it to a later character.
            modifiers.clear(); bindKeys()
        }
        mutation { client, size -> client.typeInput(text, size.first, size.second) }
    }
    private fun inputBackspace() {
        if (screen.wake()) return
        val draft = localDraft
        if (draft == null) key("BSpace") else { localDraft = if (draft.isEmpty()) null else draft.substring(0, draft.offsetByCodePoints(draft.length, -1)); updateLabels(); persist() }
    }
    private fun enter() {
        if (screen.wake()) return
        val draft = localDraft
        if (draft != null) { localDraft = null; updateLabels(); persist(); command(draft) } else key("Enter")
    }
    private fun key(name: String) {
        val prefix = modifiers.joinToString("") { "$it-" }; modifiers.clear(); bindKeys()
        mutation { client, size -> client.key(prefix + name, size.first, size.second) }
    }
    private fun focusInput(show: Boolean) {
        input.showSoftInputOnFocus = show; input.requestFocus()
        RetuiThemeBridge.sendToKeyboard(this, input, "retui_workspace:${selection.focusedTarget.orEmpty()}", "termux")
        if (show) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }
    private fun focusTile(tile: Int) {
        selection.focus(tile); updateLabels(); persist()
        RetuiThemeBridge.sendToKeyboard(this, input, "retui_workspace:${selection.focusedTarget.orEmpty()}", "termux")
    }
    private fun status(message: String) { title.text = getString(R.string.workspace_bridge_label, getString(R.string.surface_termux_workspace_0f9f7), message); title.contentDescription = message }
    private fun updateLabels() {
        root.findViewById<View>(R.id.termux_workspace_pages).visibility = if (selection.pageCount > 1) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.termux_workspace_page_position).text = getString(R.string.workspace_page_position, selection.pageIndex + 1, selection.pageCount)
        for (tile in 0..1) labels[tile]?.apply {
            text = if (tile == selection.focused && localDraft != null) ":${localDraft}" else
                (if (tile == selection.focused) "▶ " else "  ") + (frames[tile]?.window ?: selection.targets[tile] ?: getString(R.string.workspace_choose_window))
            setTextColor(color(if (tile == selection.focused) Theme.selection_text_color else Theme.workspace_panel_text_color))
            setBackgroundColor(color(if (tile == selection.focused) Theme.selection_background_color else Theme.workspace_header_background_color))
            contentDescription = getString(if (tile == selection.focused) R.string.workspace_focused_tile else R.string.workspace_unfocused_tile, tile + 1)
        }
    }
    private fun applyLayout() {
        tiles.orientation = if (selection.layout == TermuxWorkspaceState.Layout.LEFT_RIGHT) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (tile in 0..1) {
            val shown = tile in selection.visibleTiles
            panels[tile]?.visibility = if (shown) View.VISIBLE else View.GONE
            panels[tile]?.layoutParams = if (tiles.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -1, 1f)
                else LinearLayout.LayoutParams(-1, 0, 1f)
            if (!shown) closeTile(tile)
        }
        updateLabels(); persist()
        tiles.post { selection.visibleTiles.forEach { connectTile(it); capture(it) } }
    }
    private fun showLayoutChooser() {
        AlertDialog.Builder(this).setTitle(R.string.workspace_layout).setItems(arrayOf(
            getString(R.string.workspace_layout_single), getString(R.string.workspace_layout_top_bottom), getString(R.string.workspace_layout_left_right))) { _, which ->
            val layout = TermuxWorkspaceState.Layout.entries[which]
            val second = 1 - selection.focused
            if (layout != TermuxWorkspaceState.Layout.SINGLE && selection.targets[second] == null) showWindowChooser(second, true, layout)
            else { selection.layout = layout; applyLayout() }
        }.show()
    }
    private fun showWindowChooser(tile: Int, includeNew: Boolean, layout: TermuxWorkspaceState.Layout? = null) {
        val available = windows.filter { it.first != selection.targets[1 - tile] }
        val choices = available.map { it.second } + if (includeNew) listOf(getString(R.string.workspace_new_shell)) else emptyList()
        if (choices.isEmpty()) { status(getString(R.string.workspace_choose_window)); return }
        AlertDialog.Builder(this).setTitle(R.string.workspace_choose_window).setItems(choices.toTypedArray()) { _, which ->
            if (which == available.size) showPlacementChooser()
            else {
                if (layout != null) selection.layout = layout
                closeTile(tile); frames[tile] = null; selection.select(tile, available[which].first); applyLayout()
            }
        }.show()
    }
    private fun newShell(tile: Int, name: String? = null, initial: String? = null) {
        showPlacementChooser(name, initial)
    }
    private fun showPlacementChooser(name: String? = null, initial: String? = null) {
        if (bootstrap == null || creating.any { it }) { status(getString(R.string.workspace_bridge_input_not_sent)); return }
        val choices = selection.placements
        val labels = choices.map { placement -> getString(when (placement) {
            TermuxWorkspaceState.Placement.REPLACE_FOCUSED -> R.string.workspace_replace_focused
            TermuxWorkspaceState.Placement.TOP_BOTTOM -> R.string.workspace_split_top_bottom
            TermuxWorkspaceState.Placement.LEFT_RIGHT -> R.string.workspace_split_left_right
            TermuxWorkspaceState.Placement.NEW_PAGE -> R.string.workspace_new_page
            TermuxWorkspaceState.Placement.REPLACE_FIRST -> if (selection.layout == TermuxWorkspaceState.Layout.TOP_BOTTOM) R.string.workspace_replace_top else R.string.workspace_replace_left
            TermuxWorkspaceState.Placement.REPLACE_SECOND -> if (selection.layout == TermuxWorkspaceState.Layout.TOP_BOTTOM) R.string.workspace_replace_bottom else R.string.workspace_replace_right
        }) }
        AlertDialog.Builder(this).setTitle(R.string.workspace_place_shell).setItems(labels.toTypedArray()) { _, which ->
            if (!visible || bootstrap == null || creating.any { it }) return@setItems
            val seed = selection.focusedTarget ?: windows.firstOrNull()?.first
            clearTiles(); selection.place(choices[which], null)
            val tile = selection.focused
            creating[tile] = true; creationSeeds[tile] = seed
            creationNames[tile] = name; creationCommands[tile] = initial
            applyLayout(); connectTile(tile)
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun selectWindow(raw: String) {
        val target = windows.firstOrNull { it.first == raw || it.second.substringBefore(':') == raw || it.second.substringAfter(':') == raw }?.first
        if (target == null) { status(getString(R.string.workspace_choose_window)); return }
        if (target == selection.targets[1 - selection.focused]) { focusTile(1 - selection.focused); if (selection.layout == TermuxWorkspaceState.Layout.SINGLE) applyLayout(); return }
        mutation { c, size -> c.selectWindow(target, size.first, size.second) }
    }
    private fun switchFocused(next: Boolean) {
        val available = windows.filter { it.first != selection.targets[1 - selection.focused] }
        if (available.isEmpty()) return
        val current = available.indexOfFirst { it.first == selection.focusedTarget }
        selectWindow(available[Math.floorMod(current + if (next) 1 else -1, available.size)].first)
    }
    private fun runPendingCommand() {
        val raw = pendingCommand ?: return
        val clean = raw.trim().removePrefix(":").trim()
        val verb = clean.substringBefore(' ').lowercase()
        if ((verb == "new" || verb == "launch" && clean.contains(' ')) && bootstrap == null) return
        val local = verb in setOf("new", "page", "launch", "help", "status", "diagnostics", "save", "rm", "remove", "home", "back", "", "open", "show")
        if (!local && clients[selection.focused]?.connected != true) return
        pendingCommand = null; command(raw)
    }
    private fun command(raw: String) {
        val clean = raw.trim().removePrefix(":").trim()
        val verb = clean.substringBefore(' ').lowercase().ifEmpty { if (raw.trim().startsWith(":")) "help" else "" }
        val rest = clean.substringAfter(' ', "").trim()
        when (verb) {
            "", "open", "show" -> Unit
            "home", "back" -> finish()
            "help" -> localOutput(getString(R.string.surface_re_t_ui_tmux_workspace_a062d) + "\n" + getString(R.string.surface_session_5f810) + "retui_workspace\n" + listOf(R.string.surface_local_commands_c302e,
                R.string.surface_launch_id_list_or_focus_create_a_launcher_213fa, R.string.surface_save_id_command_save_a_launcher_85949,
                R.string.surface_rm_id_remove_a_saved_launcher_ad575, R.string.surface_switch_window_jump_to_a_tmux_window_index_name_d002c,
                R.string.surface_new_name_prev_next_refresh_status_reconnect_home_6ee59, R.string.surface_normal_input_is_sent_to_tmux_48b12).joinToString("\n") { getString(it) })
            "new" -> newShell(selection.focused, rest.ifEmpty { null })
            "switch", "select", "window", "tab", "focus" -> {
                val display = rest.trim('[', ']')
                val index = display.substringBefore(':')
                selectWindow(if (display.contains(':') && index.isNotEmpty() && index.all { it.isDigit() }) index else rest)
            }
            "prev", "left" -> switchFocused(false)
            "next", "right" -> switchFocused(true)
            "page" -> movePage(if (rest == "prev") -1 else 1)
            "refresh", "r" -> { selection.visibleTiles.forEach { capture(it) }; if (clients[selection.focused] == null) startBridge() }
            "reconnect" -> { clients.indices.forEach { closeTile(it) }; bootstrap = null; startBridge() }
            "status", "diagnostics" -> diagnostics()
            "launch", "run", "app" -> launch(rest)
            "save" -> { val id = rest.substringBefore(' '); val cmd = rest.substringAfter(' ', ""); status(if (TermuxWorkspaceLauncherManager.save(this, id, cmd)) getString(R.string.surface_saved_launcher_39f4b, id) else getString(R.string.surface_usage_save_id_command_21680)) }
            "rm", "remove" -> status(if (TermuxWorkspaceLauncherManager.remove(this, rest)) getString(R.string.surface_removed_launcher_4eef9, rest) else getString(R.string.surface_no_saved_launcher_removed_5af97, rest))
            else -> if (TermuxWorkspaceLauncherManager.resolve(this, verb) != null && rest.isEmpty()) launch(verb)
                else if (windows.any { it.first == clean || it.second.substringBefore(':') == clean }) selectWindow(clean)
                else status(getString(R.string.surface_unknown_workspace_command_cddf8, clean))
        }
    }
    private fun launch(id: String) {
        if (id.isEmpty()) {
            localOutput(getString(R.string.surface_tmux_launchers_f581b) + "\n" + TermuxWorkspaceLauncherManager.list(this).joinToString("\n") {
                (if (it.builtIn) "* " else "+ ") + "${it.id} - ${it.title} -> ${it.command}"
            } + "\n----\n" + listOf(R.string.surface_launch_id_ab36f, R.string.surface_save_id_command_c1052, R.string.surface_rm_id_87f06).joinToString("\n") { getString(it) })
            return
        }
        val launcher = TermuxWorkspaceLauncherManager.resolve(this, id)
        if (launcher == null) { status(getString(R.string.surface_unknown_launcher_run_launch_to_list_available_launchers_16ab0, id)); return }
        val existing = windows.firstOrNull { it.second.substringAfter(':') == launcher.id }
        if (existing != null) selectWindow(existing.first) else newShell(selection.focused, launcher.id, launcher.command)
    }
    private fun diagnostics() {
        val bridge = TermuxBridgeManager.status(this)
        val launchers = TermuxWorkspaceLauncherManager.list(this)
        val saved = launchers.count { !it.builtIn }
        localOutput(buildString {
            appendLine(getString(R.string.surface_tmux_workspace_diagnostics_ca59b))
            appendLine(getString(R.string.surface_session_5f810) + "retui_workspace")
            appendLine(selection.encode())
            appendLine(getString(R.string.surface_termux_installed_bd813) + bridge.termuxInstalled)
            appendLine(getString(R.string.surface_run_command_declared_3cdca) + bridge.runCommandDeclared)
            appendLine(getString(R.string.surface_run_command_granted_b7cc8) + bridge.runCommandGranted)
            appendLine(getString(R.string.surface_socket_name_cached_fcbbb) + (bootstrap?.socket != null))
            for (tile in selection.visibleTiles) {
                val size = geometry(tile)
                appendLine("${selection.targets[tile]} " + getString(R.string.surface_geometry_297f8) + "${size.first}x${size.second} " + getString(R.string.surface_socket_connected_d5ab4) + (clients[tile]?.connected == true))
            }
            appendLine(getString(R.string.surface_launchers_c6320) + (launchers.size - saved) + getString(R.string.surface_built_in_c12b6) + saved + getString(R.string.surface_saved_7718b))
            append(getString(R.string.surface_recovery_run_reconnect_then_refresh_if_the_pane_is_stale_af976))
        })
    }
    private fun localOutput(text: String) { AlertDialog.Builder(this).setTitle(R.string.surface_termux_workspace_0f9f7).setMessage(text).setPositiveButton(android.R.string.ok, null).show() }
    private fun bindKeys() {
        val nav = listOf("REFRESH", "ESC", "/", "-", "HOME", "↑", "END", "PGUP", "TAB", "CTRL", "ALT", "SHIFT", "←", "↓", "→", "PGDN")
        val fn = listOf("REFRESH", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "BTAB", "INS", "DEL", "ENTER", "BKSP")
        val names = mapOf("ESC" to "Escape", "HOME" to "Home", "END" to "End", "↑" to "Up", "↓" to "Down", "←" to "Left", "→" to "Right", "PGUP" to "PageUp", "PGDN" to "PageDown", "TAB" to "Tab", "BTAB" to "BTab", "INS" to "Insert", "DEL" to "Delete", "ENTER" to "Enter", "BKSP" to "BSpace")
        val modifierNames = mapOf("CTRL" to "C", "ALT" to "M", "SHIFT" to "S")
        val specs = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) fn + nav else if (fnMode) fn else nav
        val count = specs.size / 2
        for ((rowIndex, rowId) in listOf(R.id.termux_workspace_keys_row_one, R.id.termux_workspace_keys_row_two).withIndex()) {
            val row = root.findViewById<LinearLayout>(rowId); row.removeAllViews()
            for (index in 0 until count) {
                val name = specs[rowIndex * count + index]
                val key = TextView(this).apply {
                    text = name + if (modifierNames[name] in modifiers) "*" else ""
                    gravity = Gravity.CENTER; typeface = Tuils.getTypeface(this@TermuxWorkspaceActivity); textSize = 10f
                    setTextColor(color(Theme.workspace_control_text_color))
                    setOnClickListener {
                        val modifier = modifierNames[name]
                        if (modifier != null) { if (!modifiers.add(modifier)) modifiers.remove(modifier); bindKeys() }
                        else if (name == "REFRESH") { selection.visibleTiles.forEach { capture(it) }; if (clients[selection.focused] == null) startBridge() }
                        else key(names[name] ?: name)
                    }
                }
                var down = 0f
                key.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) down = event.x
                    if (event.actionMasked == MotionEvent.ACTION_UP && abs(event.x - down) > dp(48)) {
                        fnMode = !fnMode; bindKeys(); persist(); true
                    } else false
                }
                row.addView(key, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }
    }
    private fun style() {
        refreshBreathOutSettings()
        val typeface = Tuils.getTypeface(this)
        grids.forEach { grid -> grid?.apply {
            setTerminalTypeface(typeface)
            setTerminalTextSizeSp(LauncherFontScale.terminalTextSizeSp())
            updateThemeColors(color(Theme.workspace_output_text_color), color(Theme.workspace_output_background_color), color(Theme.workspace_panel_border_color))
        } }
        labels.forEach { it?.typeface = typeface }
        for (id in listOf(R.id.termux_workspace_page_previous, R.id.termux_workspace_page_position, R.id.termux_workspace_page_next)) {
            root.findViewById<TextView>(id).apply {
                this.typeface = typeface; textSize = 10f
                setTextColor(color(Theme.workspace_control_text_color))
            }
        }
        input.typeface = typeface
        input.textSize = LauncherFontScale.terminalTextSizeSp()
        input.setTextColor(color(Theme.workspace_input_text_color))
        bindKeys()
        bindChrome(R.id.termux_workspace_home, R.drawable.ic_toolbar_home_24, R.string.workspace_home) { finish() }
        bindChrome(R.id.termux_workspace_new, R.drawable.ic_workspace_add_24, R.string.workspace_new) { newShell(selection.focused) }
        bindChrome(R.id.termux_workspace_ime, R.drawable.ic_workspace_keyboard_24, R.string.workspace_ime) { focusInput(true) }
        bindChrome(R.id.termux_workspace_layout, R.drawable.ic_workspace_layout_24, R.string.workspace_layout) { showLayoutChooser() }
        RetuiThemeBridge.sendToKeyboard(this, input, "retui_workspace:${selection.focusedTarget.orEmpty()}", "termux")
        root.findViewById<View>(R.id.termux_workspace_border).background = panelDrawable(Theme.workspace_panel_background_color)
        title.typeface = Tuils.getTypeface(this); title.textSize = AppearanceSettings.outputHeaderTextSize().toFloat()
        title.setTextColor(color(Theme.workspace_panel_text_color))
        title.background = TerminalBorderRuntime.tabDrawable(this, color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(root.findViewById(R.id.termux_workspace_border), title)
        panels.forEach { it?.background = panelDrawable(Theme.workspace_output_background_color) }
        updateLabels()
        scheduleGeometryCapture()
    }
    private fun panelDrawable(background: Theme) = TerminalBorderRuntime.panelDrawable(this, color(background), color(Theme.workspace_panel_border_color),
        1.2f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
    private fun bindChrome(id: Int, drawable: Int, description: Int, action: () -> Unit) {
        root.findViewById<TextView>(id).apply {
            contentDescription = getString(description)
            background = android.graphics.drawable.InsetDrawable(TerminalBorderRuntime.tabDrawable(this@TermuxWorkspaceActivity,
                color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS), 0, 0, 0, dp(28))
            TerminalPaneIcon.apply(this, drawable); setOnClickListener { action() }
        }
    }
    private fun color(role: Theme) = ThemeColorResolver.color(role)
    private fun dp(value: Int) = Tuils.dpToPx(this, value)
}
