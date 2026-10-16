package ohi.andre.consolelauncher.managers.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real pane journey. Opt in explicitly; only unique fixture windows ever receive input or cleanup. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceSshUiJourneyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixturePrefix = "sshui-${UUID.randomUUID().toString().take(8)}"
    private val created = linkedSetOf<String>()
    private fun field(activity: TermuxWorkspaceActivity, name: String): Any? =
        TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun call(activity: TermuxWorkspaceActivity, name: String, vararg args: Any?): Any? =
        TermuxWorkspaceActivity::class.java.declaredMethods.single { it.name == name && it.parameterTypes.size == args.size }
            .apply { isAccessible = true }.invoke(activity, *args)
    private fun state(activity: TermuxWorkspaceActivity) = field(activity, "selection") as TermuxWorkspaceState
    @Suppress("UNCHECKED_CAST")
    private fun terminal(activity: TermuxWorkspaceActivity, tile: Int) = (field(activity, "terminals") as Array<TermuxWorkspaceTerminal?>)[tile]
    @Suppress("UNCHECKED_CAST")
    private fun windows(activity: TermuxWorkspaceActivity) = field(activity, "windows") as List<Pair<String, String>>
    private fun await(scenario: ActivityScenario<TermuxWorkspaceActivity>, description: String, predicate: (TermuxWorkspaceActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20000
        var lastStatus = ""
        while (SystemClock.uptimeMillis() < deadline) {
            var passed = false
            scenario.onActivity { passed = predicate(it); lastStatus = it.findViewById<android.widget.TextView>(R.id.termux_workspace_label).text.toString() +
                "; selection=" + state(it).encode() + "; creating=" + (field(it, "creating") as BooleanArray).contentToString() +
                "; windows=" + windows(it).joinToString { window -> window.first } }
            if (passed) return
            Thread.sleep(50)
        }
        val directory = File(context.filesDir, "termux-ssh-ui-proof").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "failure-journey.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        fail("$description; status=$lastStatus")
    }
    private fun runCommand(script: String, vararg arguments: String): String {
        val label = "$fixturePrefix-${UUID.randomUUID()}"
        val latch = CountDownLatch(1)
        val result = AtomicReference<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) == label) { result.set(intent); latch.countDown() }
            }
        }
        val broadcasts = LocalBroadcastManager.getInstance(context)
        broadcasts.registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT))
        try {
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                TermuxBridgeManager.createResultPendingIntent(context, label, null), arrayOf("-c", script, "retui-ui-proof", *arguments))
            assertTrue("Termux fixture command timed out", latch.await(30, TimeUnit.SECONDS))
            val intent = result.get()
            assertEquals(intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR), 0, intent.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1))
            return intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty()
        } finally { broadcasts.unregisterReceiver(receiver) }
    }
    private fun waitDialogGone(label: String) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val remaining = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)
                ?.any { it.text?.toString() == label } == true
            if (!remaining) {
                instrumentation.uiAutomation.waitForIdle(100, 3000)
                return
            }
            Thread.sleep(30)
        }
        fail("Dialog did not dismiss after selecting $label")
    }
    private fun selectDialog(label: String) {
        instrumentation.uiAutomation.waitForIdle(100, 3000)
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val item = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)
                ?.firstOrNull { it.text?.toString() == label }
            if (item != null) {
                if (item.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) { waitDialogGone(label); return }
                val bounds = android.graphics.Rect()
                item.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    val now = SystemClock.uptimeMillis()
                    listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                        val event = MotionEvent.obtain(now, now, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                        try { instrumentation.uiAutomation.injectInputEvent(event, true) } finally { event.recycle() }
                    }
                    waitDialogGone(label)
                    return
                }
            }
            Thread.sleep(30)
        }
        fun texts(node: android.view.accessibility.AccessibilityNodeInfo?): List<String> = if (node == null) emptyList() else
            listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { texts(node.getChild(it)) }
        val actual = texts(instrumentation.uiAutomation.rootInActiveWindow)
        val directory = File(context.filesDir, "termux-ssh-ui-proof").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "failure-dialog.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        fail("Dialog choice missing: $label; visible=$actual")
    }
    private fun foreground(scenario: ActivityScenario<TermuxWorkspaceActivity>) {
        scenario.onActivity {
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Keep this test screen visible without changing the user's persisted fade settings.
            (field(it, "screen") as ohi.andre.consolelauncher.BreathOutLayout).configure(false, 900)
        }
        await(scenario, "The actual Termux pane must stay in front") { it.hasWindowFocus() }
    }
    private fun screenshot(scenario: ActivityScenario<TermuxWorkspaceActivity>, name: String) {
        foreground(scenario)
        instrumentation.waitForIdleSync()
        // Window exit animations may outlive focus/accessibility dismissal; settle only captures.
        SystemClock.sleep(300)
        instrumentation.waitForIdleSync()
        val directory = File(context.filesDir, "termux-ssh-ui-proof").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private fun type(scenario: ActivityScenario<TermuxWorkspaceActivity>, target: String, command: String) {
        assertTrue("Refuse input to a non-fixture target", target in created)
        await(scenario, "Fixture $target must redraw before accepting input") { activity ->
            state(activity).focusedTarget == target && terminal(activity, state(activity).focused)?.text()?.isNotBlank() == true
        }
        scenario.onActivity { activity ->
            assertEquals("Refuse input after an unexpected focus change", target, state(activity).focusedTarget)
            call(activity, "directText", command); call(activity, "enter")
        }
    }
    private fun focus(scenario: ActivityScenario<TermuxWorkspaceActivity>, tile: Int) {
        scenario.onActivity { activity ->
            val grid = (field(activity, "grids") as Array<*>)[tile] as TerminalGridView
            val now = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL).forEach { action ->
                val event = MotionEvent.obtain(now, now, action, 4f, 4f, 0)
                try { grid.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            assertEquals(tile, state(activity).focused)
        }
    }
    private fun layout(scenario: ActivityScenario<TermuxWorkspaceActivity>, label: Int, expected: TermuxWorkspaceState.Layout) {
        scenario.onActivity { it.findViewById<View>(R.id.termux_workspace_layout).performClick() }
        selectDialog(context.getString(label))
        await(scenario, "Layout $expected did not attach visible tiles") { activity ->
            state(activity).layout == expected && state(activity).visibleTiles.all { terminal(activity, it)?.text()?.isNotBlank() == true }
        }
    }

    private fun awaitPaneCommand(target: String, expected: String) {
        val deadline = SystemClock.uptimeMillis() + 10000
        var actual = ""
        while (SystemClock.uptimeMillis() < deadline) {
            actual = runCommand("tmux display-message -p -t \"\$1\" '#{pane_current_command}'", target).trim()
            if (actual == expected || expected == "vim" && actual == "nvim") return
            Thread.sleep(100)
        }
        fail("Expected $expected in fixture $target; actual=$actual")
    }
    @Test fun panePreservesSplitsPagesLaunchersAndEditorAcrossDetach() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("termuxSshDevice") == "true")
        assumeTrue(BuildConfig.TERMUX_SSH_PANE)
        assertTrue("Pair the test application first", TermuxWorkspaceSshSetup.isPaired(context))
        assertTrue(TermuxBridgeManager.hasRunCommandPermission(context))
        val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        val originalPrefs = prefs.all.toMap()
        val networkViolations = java.util.Collections.synchronizedList(mutableListOf<String>())
        val originalMainPolicy = AtomicReference<android.os.StrictMode.ThreadPolicy>()
        if (android.os.Build.VERSION.SDK_INT >= 28) instrumentation.runOnMainSync {
            originalMainPolicy.set(android.os.StrictMode.getThreadPolicy())
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder().detectNetwork()
                .penaltyListener(java.util.concurrent.Executor { it.run() }) { violation -> networkViolations.add(violation.toString()) }.build())
        }
        val fixtureTargets = mutableListOf<String>()
        val launcherId = fixturePrefix.replace('-', '_')
        var host: ActivityScenario<TermuxWorkspaceActivity>? = null
        var vimFile: String? = null
        try {
            prefs.edit().putString("selection", TermuxWorkspaceState().encode()).remove("draft").commit()
            val scenario = ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java))
            host = scenario
            foreground(scenario)
            await(scenario, "Initial empty surface did not finish connecting") { (field(it, "ssh") as? TermuxWorkspaceSshConnection)?.connected == true && field(it, "sshConnecting") == false }
            repeat(3) { index ->
                val target = runCommand("if tmux has-session -t '=retui_workspace' 2>/dev/null; then tmux new-window -d -t '=retui_workspace' -P -F '#{window_id}' -n \"\$1\" 'exec sh'; else tmux new-session -d -s retui_workspace -P -F '#{window_id}' -n \"\$1\" 'exec sh'; fi", "$fixturePrefix-$index").trim()
                assertTrue(target.matches(Regex("@[0-9]+")))
                created.add(target); fixtureTargets.add(target)
            }
            val beforeOpen = runCommand("tmux list-windows -t '=retui_workspace' -F '#{?pane_dead,,#{window_id}}'").lineSequence().filter { it.startsWith('@') }.toSet()
            val empty = scenario
            run {
                await(empty, "An empty surface did not connect without creating a shell") { (field(it, "ssh") as? TermuxWorkspaceSshConnection)?.connected == true && field(it, "sshConnecting") == false && state(it).focusedTarget == null }
                assertEquals(beforeOpen, runCommand("tmux list-windows -t '=retui_workspace' -F '#{?pane_dead,,#{window_id}}'").lineSequence().filter { it.startsWith('@') }.toSet())
                empty.onActivity { activity ->
                    assertNull(terminal(activity, 0)); assertNull(terminal(activity, 1))
                    call(activity, "directText", ":new $fixturePrefix-empty")
                    call(activity, "enter")
                }
                selectDialog(context.getString(R.string.workspace_replace_focused))
                var emptyTarget = ""
                await(empty, "Explicit new from an empty surface did not create a shell") { activity ->
                    val target = state(activity).focusedTarget
                    if (target != null && target !in beforeOpen) { created.add(target); emptyTarget = target }
                    emptyTarget.isNotEmpty() && terminal(activity, state(activity).focused)?.text()?.isNotBlank() == true
                }
                type(empty, emptyTarget, "exit")
                await(empty, "Exiting the selected shell should return to an empty surface") { state(it).focusedTarget == null && terminal(it, 0) == null && terminal(it, 1) == null }
                assertEquals(beforeOpen, runCommand("tmux list-windows -t '=retui_workspace' -F '#{?pane_dead,,#{window_id}}'").lineSequence().filter { it.startsWith('@') }.toSet())
                screenshot(empty, "00-empty-after-shell-exit")
            }
            val selected = TermuxWorkspaceState().apply { select(0, fixtureTargets[0]); select(1, fixtureTargets[1]) }
            scenario.onActivity { activity ->
                call(activity, "clearTiles")
                TermuxWorkspaceActivity::class.java.getDeclaredField("selection").apply { isAccessible = true }.set(activity, selected)
                call(activity, "applyLayout")
            }
            run {
                await(scenario, "Initial single tile did not attach") { terminal(it, 0)?.text()?.isNotBlank() == true }
                val afterOpen = runCommand("tmux list-windows -t '=retui_workspace' -F '#{?pane_dead,,#{window_id}}'").lineSequence().filter { it.startsWith('@') }.toSet()
                assertEquals("Opening the pane must not create shells", beforeOpen, afterOpen)
                scenario.onActivity { activity ->
                    call(activity, "directText", ":shell_")
                    call(activity, "directText", "draft")
                    call(activity, "inputBackspace")
                }
                await(scenario, "Shell colon input did not retain queued text/backspace ordering") { field(it, "localDraft") == "shell_draf" }
                scenario.onActivity { activity -> repeat("shell_draf".length + 1) { call(activity, "inputBackspace") }; assertNull(field(activity, "localDraft")) }
                screenshot(scenario, "01-single")
                var singleGeometry: Pair<Int, Int>? = null
                scenario.onActivity {
                    @Suppress("UNCHECKED_CAST")
                    val geometry = call(it, "geometry", 0) as Pair<Int, Int>
                    singleGeometry = geometry
                }
                layout(scenario, R.string.workspace_layout_top_bottom, TermuxWorkspaceState.Layout.TOP_BOTTOM)
                await(scenario, "Top/bottom must resize both terminal rows") { activity ->
                    terminal(activity, 0)!!.emulator.mRows < singleGeometry!!.second && terminal(activity, 1)!!.emulator.mRows < singleGeometry!!.second
                }
                focus(scenario, 0)
                scenario.onActivity { activity ->
                    call(activity, "directText", ":discard_on_focus_change")
                    call(activity, "focusTile", 1)
                    assertNull(field(activity, "pendingColon")); assertNull(field(activity, "localDraft"))
                }
                focus(scenario, 0)
                type(scenario, fixtureTargets[0], "printf '%s%s\\n' 'TOP_' 'INDEPENDENT'")
                await(scenario, "First tile did not receive input") { terminal(it, 0)?.text()?.contains("TOP_INDEPENDENT") == true }
                scenario.onActivity { assertFalse(terminal(it, 1)!!.text().contains("TOP_INDEPENDENT")) }
                focus(scenario, 1)
                type(scenario, fixtureTargets[1], "printf '%s%s\\n' 'BOTTOM_' 'INDEPENDENT'")
                await(scenario, "Second tile did not receive input") { terminal(it, 1)?.text()?.contains("BOTTOM_INDEPENDENT") == true }
                screenshot(scenario, "02-top-bottom")
                layout(scenario, R.string.workspace_layout_left_right, TermuxWorkspaceState.Layout.LEFT_RIGHT)
                await(scenario, "Left/right must resize terminal columns") { terminal(it, 0)!!.emulator.mColumns < singleGeometry!!.first }
                scenario.onActivity { activity ->
                    assertEquals(fixtureTargets[0], state(activity).targets[0]); assertEquals(fixtureTargets[1], state(activity).targets[1])
                }
                screenshot(scenario, "03-left-right")
                var thirdLabel = ""
                scenario.onActivity { activity ->
                    thirdLabel = windows(activity).single { it.first == fixtureTargets[2] }.second
                    (field(activity, "labels") as Array<*>)[0].let { (it as View).performClick() }
                }
                selectDialog(thirdLabel)
                await(scenario, "Window chooser failed to replace only the first tile") { state(it).targets[0] == fixtureTargets[2] && state(it).targets[1] == fixtureTargets[1] && terminal(it, 0) != null }
                var replacedTarget = ""
                var beforeAdd = emptySet<String>()
                scenario.onActivity { activity ->
                    beforeAdd = windows(activity).map { it.first }.toSet()
                    activity.findViewById<View>(R.id.termux_workspace_new).performClick()
                }
                selectDialog(context.getString(R.string.workspace_replace_left))
                await(scenario, "Add did not create and place a shell in only the left tile") { activity ->
                    val target = state(activity).focusedTarget
                    if (target != null && target !in beforeAdd) { created.add(target); replacedTarget = target }
                    replacedTarget.isNotEmpty() && state(activity).targets[1] == fixtureTargets[1] && terminal(activity, 0)?.text()?.isNotBlank() == true
                }
                screenshot(scenario, "04-add-replace-left")
                scenario.onActivity { activity ->
                    call(activity, "command", "save $launcherId printf 'SAVED_LAUNCHER_READY\\n'; exec sh")
                    assertNotNull(TermuxWorkspaceLauncherManager.resolve(context, launcherId))
                    call(activity, "command", "launch $launcherId")
                }
                selectDialog(context.getString(R.string.workspace_new_page))
                await(scenario, "Saved launcher did not create its requested page") { activity ->
                    windows(activity).filter { it.second.substringAfter(':') == launcherId }.forEach { created.add(it.first) }
                    state(activity).pageCount == 2 && state(activity).focusedTarget in created && terminal(activity, state(activity).focused)?.text()?.contains("SAVED_LAUNCHER_READY") == true
                }
                screenshot(scenario, "04-saved-launcher-page")
                scenario.onActivity { it.findViewById<View>(R.id.termux_workspace_page_previous).performClick() }
                await(scenario, "Previous page did not restore split selections") { state(it).pageIndex == 0 && state(it).layout == TermuxWorkspaceState.Layout.LEFT_RIGHT && state(it).targets.toList() == listOf(replacedTarget, fixtureTargets[1]) }
                scenario.onActivity { it.findViewById<View>(R.id.termux_workspace_page_next).performClick() }
                await(scenario, "Next page did not reattach launcher") { state(it).pageIndex == 1 && terminal(it, state(it).focused)?.text()?.isNotBlank() == true }
                var editorTarget = ""
                scenario.onActivity { editorTarget = state(it).focusedTarget!! }
                type(scenario, editorTarget, "printf '\\033[?1049hEDITOR_RESUME_PROOF'; read retui_line; printf '\\033[?1049l'")
                await(scenario, "Editor did not enter alternate screen") { terminal(it, state(it).focused)?.emulator?.isAlternateBufferActive == true && terminal(it, state(it).focused)?.text()?.contains("EDITOR_RESUME_PROOF") == true }
                screenshot(scenario, "05-editor-before-detach")
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                foreground(scenario)
                assertTrue("Backgrounding killed the editor window", runCommand("tmux list-windows -t '=retui_workspace' -F '#{?pane_dead,,#{window_id}}'").lineSequence().contains(editorTarget))
                await(scenario, "Reconnect failed to restore the alternate screen") { terminal(it, state(it).focused)?.emulator?.isAlternateBufferActive == true && terminal(it, state(it).focused)?.text()?.contains("EDITOR_RESUME_PROOF") == true }
                screenshot(scenario, "06-editor-after-resume")
                type(scenario, editorTarget, ":literal-editor-input")
                await(scenario, "Editor exit did not restore normal terminal") { terminal(it, state(it).focused)?.text()?.contains("SAVED_LAUNCHER_READY") == true && field(it, "localDraft") == null }
                assertEquals("0", runCommand("tmux display-message -p -t \"\$1\" '#{alternate_on}'", editorTarget).trim())
                type(scenario, editorTarget, "printf '%s%s\\n' 'RESUMED_' 'SHELL_OK'")
                await(scenario, "Resumed terminal did not accept shell input") { terminal(it, state(it).focused)?.text()?.contains("RESUMED_SHELL_OK") == true }
                vimFile = runCommand("mktemp \"\$PREFIX/tmp/retui-ssh-vim.XXXXXXXX\"").trim()
                assertTrue(vimFile!!.startsWith("/data/data/com.termux/files/usr/tmp/retui-ssh-vim."))
                val quotedVimFile = "'" + vimFile!!.replace("'", "'\\''") + "'"
                type(scenario, editorTarget, "vim -Nu NONE -n $quotedVimFile")
                awaitPaneCommand(editorTarget, "vim")
                val edited = "Re:TUI — नमस्ते 世界 😀"
                scenario.onActivity { activity ->
                    call(activity, "directText", "i$edited")
                    call(activity, "key", "Escape")
                    call(activity, "directText", ":w")
                    call(activity, "enter")
                }
                val savedDeadline = SystemClock.uptimeMillis() + 10000
                var saved = ""
                while (SystemClock.uptimeMillis() < savedDeadline) {
                    saved = runCommand("cat \"\$1\"", vimFile!!)
                    if (saved == "$edited\n") break
                    Thread.sleep(100)
                }
                assertEquals("Vim must save the exact Unicode input", "$edited\n", saved)
                screenshot(scenario, "08-vim-saved-unicode")

                // Exercise the actual Android input connection, including provisional composition.
                val imeLine = "InputConnection: नमस्ते 世界 😀 e\u0301"
                scenario.onActivity { activity ->
                    val input = field(activity, "input") as TermuxWorkspaceInputEditText
                    input.requestFocus()
                    input.setText("")
                    val connection = requireNotNull(input.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                    connection.commitText("oInputConnection: ", 1)
                    connection.setComposingText("न", 1)
                    connection.setComposingText("नमस्ते", 1)
                    connection.commitText("नमस्ते", 1)
                    connection.finishComposingText()
                    connection.commitText(" 世界 😀 e\u0301", 1)
                    connection.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_GO)
                    call(activity, "key", "Escape")
                }
                // Let the IME action settle before issuing the editor's Save command.
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val input = field(activity, "input") as TermuxWorkspaceInputEditText
                    val connection = requireNotNull(input.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
                    connection.commitText(":w", 1)
                    connection.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_GO)
                }
                val expectedEditorFile = "$edited\n$imeLine\n\n"
                val compositionDeadline = SystemClock.uptimeMillis() + 10000
                do {
                    saved = runCommand("cat \"\$1\"", vimFile!!)
                    if (saved == expectedEditorFile) break
                    Thread.sleep(50)
                } while (SystemClock.uptimeMillis() < compositionDeadline)
                assertEquals("InputConnection must commit Unicode exactly once, without provisional composition", expectedEditorFile, saved)

                fun imeVisible(activity: TermuxWorkspaceActivity): Boolean =
                    androidx.core.view.ViewCompat.getRootWindowInsets(field(activity, "input") as View)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                fun hideIme() = scenario.onActivity { activity ->
                    val input = field(activity, "input") as View
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .hideSoftInputFromWindow(input.windowToken, 0)
                }
                hideIme()
                await(scenario, "Keyboard must be hidden before measuring the terminal") { activity ->
                    val grid = (field(activity, "grids") as Array<*>)[state(activity).focused] as TerminalGridView;
                    !imeVisible(activity) && terminal(activity, state(activity).focused)?.emulator?.mRows == (grid.height / grid.lineHeight()).coerceIn(8, 120)
                }
                var fullRows = 0
                scenario.onActivity { fullRows = terminal(it, state(it).focused)!!.emulator.mRows }
                scenario.onActivity { activity ->
                    val input = field(activity, "input") as View
                    input.requestFocus()
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                await(scenario, "Showing the real keyboard must reduce terminal rows") { activity ->
                    imeVisible(activity) && terminal(activity, state(activity).focused)!!.emulator.mRows < fullRows
                }
                var keyboardRows = 0
                scenario.onActivity { keyboardRows = terminal(it, state(it).focused)!!.emulator.mRows }
                fun assertRemoteRows(expected: Int) {
                    val deadline = SystemClock.uptimeMillis() + 5000
                    var actual = ""
                    do {
                        actual = runCommand("tmux display-message -p -t \"\$1\" '#{pane_height}'", editorTarget).trim()
                        if (actual == expected.toString()) return
                        Thread.sleep(50)
                    } while (SystemClock.uptimeMillis() < deadline)
                    assertEquals("Native PTY must follow keyboard geometry", expected.toString(), actual)
                }
                assertRemoteRows(keyboardRows)
                screenshot(scenario, "10-input-connection-ime-shown")
                hideIme()
                await(scenario, "Hiding the keyboard must restore terminal rows") { activity ->
                    !imeVisible(activity) && terminal(activity, state(activity).focused)!!.emulator.mRows == fullRows
                }
                assertRemoteRows(fullRows)
                screenshot(scenario, "11-input-connection-ime-hidden")
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                foreground(scenario)
                awaitPaneCommand(editorTarget, "vim")
                await(scenario, "Actual Vim buffer did not redraw after resume") { terminal(it, state(it).focused)?.text()?.contains("Re:TUI") == true }
                screenshot(scenario, "09-vim-after-resume")
                type(scenario, editorTarget, ":q")
                await(scenario, "Vim quit did not restore shell output") { terminal(it, state(it).focused)?.text()?.contains("RESUMED_SHELL_OK") == true }
                assertEquals(expectedEditorFile, runCommand("cat \"\$1\"", vimFile!!))
                scenario.recreate()
                foreground(scenario)
                await(scenario, "Activity recreation lost saved page or shell") { state(it).pageIndex == 1 && state(it).focusedTarget == editorTarget && terminal(it, state(it).focused)?.text()?.contains("RESUMED_SHELL_OK") == true }
                screenshot(scenario, "07-recreated")
            }
        } finally {
            TermuxWorkspaceLauncherManager.remove(context, launcherId)
            try {
            vimFile?.let { runCommand("rm -f -- \"\$1\"", it) }
            // Discover only fixture-owned names, including a launcher created immediately before a failed assertion.
            val discovered = runCommand("tmux list-windows -t '=retui_workspace' -F '#{window_id}:#{window_name}' 2>/dev/null || true")
                .lineSequence().filter { it.substringAfter(':').startsWith(fixturePrefix) || it.substringAfter(':') == launcherId }
                .map { it.substringBefore(':') }.filter { it.matches(Regex("@[0-9]+")) }.toSet()
            val owned = created + discovered
            if (owned.isNotEmpty()) runCommand("for target in \"\$@\"; do tmux kill-window -t \"\$target\" 2>/dev/null || true; done", *owned.toTypedArray())
            } finally {
            host?.close()
            val editor = prefs.edit().clear()
            originalPrefs.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
            } }
            editor.commit()
            originalMainPolicy.get()?.let { policy -> instrumentation.runOnMainSync { android.os.StrictMode.setThreadPolicy(policy) } }
            }
        }
        assertTrue("SSH must never perform network I/O on Android's main thread: $networkViolations", networkViolations.isEmpty())
    }
}
