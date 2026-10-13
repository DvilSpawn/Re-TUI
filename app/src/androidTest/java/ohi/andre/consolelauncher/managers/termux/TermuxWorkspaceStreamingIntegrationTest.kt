package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit private helper fixture, rendered by the full launcher's existing workspace. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceStreamingIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun field(a: TermuxWorkspaceActivity, name: String): Any? =
        TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun set(a: TermuxWorkspaceActivity, name: String, value: Any?) =
        TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)
    private fun call(a: TermuxWorkspaceActivity, name: String, vararg args: Any?) =
        TermuxWorkspaceActivity::class.java.declaredMethods.single { it.name == name && it.parameterTypes.size == args.size }
            .apply { isAccessible = true }.invoke(a, *args)
    private fun text(a: TermuxWorkspaceActivity, tile: Int = 0): String =
        ((field(a, "terminals") as Array<*>)[tile] as? TermuxWorkspaceTerminal)?.text().orEmpty()
    private fun await(s: ActivityScenario<TermuxWorkspaceActivity>, label: String, condition: (TermuxWorkspaceActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 12000
        while (SystemClock.uptimeMillis() < deadline) {
            var ok = false; s.onActivity { ok = condition(it) }
            if (ok) return
            Thread.sleep(25)
        }
        s.onActivity { println("$label; pane: ${text(it)}; status: ${(field(it, "title") as android.widget.TextView).text}") }
        fail(label)
    }
    private fun type(a: TermuxWorkspaceActivity, value: String) {
        val input = field(a, "input") as TermuxWorkspaceInputEditText
        assertTrue(input.onCreateInputConnection(EditorInfo())!!.commitText(value, 1))
    }
    private fun command(s: ActivityScenario<TermuxWorkspaceActivity>, value: String) {
        s.onActivity { call(it, "key", "C-u"); type(it, value); call(it, "enter") }
    }
    private fun attach(a: TermuxWorkspaceActivity, port: Int) {
        // Ignore the regular helper's pending probe; this test owns no production Termux jobs.
        set(a, "resultLabel", null)
        set(a, "bootstrap", TermuxWorkspaceFrame.parse("__RETUI_SOCKET__ retui-bridge-socket-v4\n__RETUI_SOCKET_NAME__ retui_bridge_com_dvil_tui_renewed_v2\n__RETUI_TOKEN__ fixture-token\n__RETUI_TCP_HOST__ 127.0.0.1\n__RETUI_TCP_PORT__ $port\n"))
        call(a, "dismissHelperPrompt"); set(a, "resultLabel", null)
        val selection = field(a, "selection") as TermuxWorkspaceState
        selection.visibleTiles.forEach { call(a, "connectTile", it) }
    }
    private fun normalize(value: String) = value.lines().joinToString("\n") { it.trimEnd() }.trimEnd()
    private fun assertMatchesTmux(s: ActivityScenario<TermuxWorkspaceActivity>, port: Int, tile: Int = 0) {
        lateinit var frame: TermuxWorkspaceFrame
        s.onActivity { frame = (field(it, "frames") as Array<*>)[tile] as TermuxWorkspaceFrame }
        val done = CountDownLatch(1)
        var actual: TermuxWorkspaceFrame? = null
        var error: String? = null
        val oracle = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
            override fun onFrame(raw: String) { actual = TermuxWorkspaceFrame.parse(raw); done.countDown() }
            override fun onStatus(status: String) {}
            override fun onError(message: String) { error = message; done.countDown() }
            override fun onClosed() {}
        })
        try {
            oracle.connect(TermuxWorkspaceSocketClient.SOCKET_NAME, "fixture-token", frame.cols, frame.rows,
                "127.0.0.1", port, false, frame.target, true)
            assertTrue("Authoritative tmux capture must arrive", done.await(5, TimeUnit.SECONDS))
            assertNull(error)
            val expected = normalize(requireNotNull(actual?.body).replace(Regex("\u001b\\[[0-?]*[ -/]*[@-~]"), ""))
            await(s, "Existing pane must match authoritative tmux cells") { normalize(text(it, tile)) == expected }
            s.onActivity {
                val emulator = ((field(it, "terminals") as Array<*>)[tile] as TermuxWorkspaceTerminal).emulator
                assertEquals("Cursor column", actual!!.cursorX, emulator.cursorCol)
                assertEquals("Cursor row", actual!!.cursorY, emulator.cursorRow)
            }
        } finally { oracle.close() }
    }
    private fun productionStress(s: ActivityScenario<TermuxWorkspaceActivity>, port: Int, checks: JSONArray) {
        // A reconnect cannot infer the current rendition from already painted cells.
        command(s, "printf '\\033c\\033[2J\\033[H\\033[31mRED'; read -r reply; printf '\\033[0m\\nREAD_DONE\\n'")
        await(s, "Red foreground wait") { text(it).contains("RED") }
        s.recreate(); s.onActivity { attach(it, port) }
        await(s, "Reconnect while shell read is blocked") { text(it).contains("RED") }
        s.onActivity { type(it, "X") }
        await(s, "Read echo after reconnect") { text(it).contains("REDX") }
        s.onActivity {
            val emulator = ((field(it, "terminals") as Array<*>)[0] as TermuxWorkspaceTerminal).emulator
            assertEquals("Echo must retain tmux's active red rendition across reconnect", 1,
                com.termux.terminal.TextStyle.decodeForeColor(emulator.screen.getStyleAt(0, 3)))
            call(it, "enter")
        }
        await(s, "Read command completes") { text(it).contains("READ_DONE") }
        checks.put("Active colour survives reconnect without an application SGR reset")
        command(s, "printf '\\033c'; i=0; while [ \"\$i\" -lt 3000 ]; do printf 'BURST_%04d_λ界_é\\n' \"\$i\"; i=\$((i+1)); done; printf 'BURST_%s\\n' DONE")
        await(s, "Large output burst completes") { text(it).contains("BURST_DONE") }
        assertMatchesTmux(s, port)
        checks.put("3000-line Unicode output burst matches authoritative tmux screen and cursor")
        command(s, "printf '\\033c'; i=0; while [ \"\$i\" -lt 400 ]; do printf 'BUSY_%04d_λ界\\n' \"\$i\"; i=\$((i+1)); sleep 0.01; done; printf 'BUSY_%s\\n' DONE")
        await(s, "Busy output begins") { text(it).contains("BUSY_") }
        repeat(5) {
            s.recreate(); s.onActivity { attach(it, port) }
            await(s, "Busy reconnect $it renders") { text(it).contains("BUSY_") }
        }
        await(s, "Busy output finishes after repeated reconnects") { text(it).contains("BUSY_DONE") }
        assertMatchesTmux(s, port)
        checks.put("Five recreations during continuous output recover exact tmux cells and cursor")
        val paste = "λ界🙂_".repeat(1000)
        val bytes = paste.toByteArray(Charsets.UTF_8)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        command(s, "tty_state=\$(stty -g); stty -icanon -echo; printf 'PASTE_%s\\n' READY; head -c ${bytes.size} | sha256sum; stty \"\$tty_state\"; printf 'PASTE_%s\\n' DONE")
        await(s, "Raw reader ready for long Unicode paste") { text(it).contains("PASTE_READY") }
        s.onActivity { type(it, paste) }
        await(s, "Large paste byte checksum") { text(it).replace(Regex("\\s"), "").contains(hash) && text(it).contains("PASTE_DONE") }
        checks.put("10 KB Unicode paste retains exact bytes while capture requests share the control pipe")
        val native = "/data/data/com.termux/files/usr/bin/tmux -L retui_integration_phone_probe"
        command(s, "$native new-window -d -n external-window /data/data/com.termux/files/usr/bin/bash")
        await(s, "External window addition") { (field(it, "windows") as List<*>).any { entry -> (entry as Pair<*, *>).second.toString().contains("external-window") } }
        command(s, "$native rename-window -t external-window renamed-window")
        await(s, "External window rename") { (field(it, "windows") as List<*>).any { entry -> (entry as Pair<*, *>).second.toString().contains("renamed-window") } }
        command(s, "$native kill-window -t renamed-window")
        await(s, "External window removal") { (field(it, "windows") as List<*>).size == 2 }
        checks.put("External window add, rename and remove update the original pane chooser")
        command(s, "$native detach-client -s retui_workspace")
        await(s, "Transport loss closes both clients without removing live jobs") { (field(it, "clients") as Array<*>).all { client -> client == null } }
        s.onActivity {
            assertEquals(listOf("@0", "@1"), (field(it, "selection") as TermuxWorkspaceState).targets.toList())
            attach(it, port)
        }
        await(s, "Both live jobs recover after control-client loss") { text(it).contains("PASTE_DONE") && text(it, 1).contains("SECOND_TILE_OK") }
        assertMatchesTmux(s, port, 0); assertMatchesTmux(s, port, 1)
        checks.put("Control-client loss retains both jobs and recovers exact tmux screens")
        s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        Thread.sleep(300)
        s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        s.onActivity { attach(it, port) }
        await(s, "Foreground return reconnects both original jobs") { text(it).contains("PASTE_DONE") && text(it, 1).contains("SECOND_TILE_OK") }
        checks.put("Background/foreground return preserves both jobs")
        val seconds = InstrumentationRegistry.getArguments().getString("idleSeconds")?.toIntOrNull() ?: 0
        if (seconds > 0) {
            require(seconds in 30..600)
            instrumentation.targetContext.openFileOutput("production-idle-ready", Context.MODE_PRIVATE).use { it.write("ready".toByteArray()) }
            Thread.sleep(seconds * 1000L)
            assertMatchesTmux(s, port, 0); assertMatchesTmux(s, port, 1)
            checks.put("Both idle panes remain correct for $seconds seconds")
        }
        command(s, "$native set-option -t @0 remain-on-exit on; exit")
        await(s, "Retained dead shell collapses the original split") {
            val state = field(it, "selection") as TermuxWorkspaceState
            state.targets.none { target -> target == "@0" } && state.focusedTarget == "@1"
        }
        checks.put("Retained dead shell is removed from pane selection")
        await(s, "Remaining shell is connected after collapse") {
            val state = field(it, "selection") as TermuxWorkspaceState
            ((field(it, "clients") as Array<*>)[state.focused] as? TermuxWorkspaceSocketClient)?.connected == true && text(it, state.focused).contains("SECOND_TILE_OK")
        }
        command(s, "exit")
        await(s, "Final live shell exit leaves an empty workspace") { (field(it, "selection") as TermuxWorkspaceState).targets.all { target -> target == null } }
        s.recreate(); s.onActivity { attach(it, port) }
        Thread.sleep(1000)
        s.onActivity { assertTrue((field(it, "selection") as TermuxWorkspaceState).targets.all { target -> target == null }) }
        checks.put("Final shell exit and recreation do not create a replacement job")
        s.onActivity {
            call(it, "clearTiles")
            val state = field(it, "selection") as TermuxWorkspaceState
            state.place(TermuxWorkspaceState.Placement.REPLACE_FOCUSED, null)
            (field(it, "creating") as BooleanArray)[state.focused] = true
            (field(it, "creationSeeds") as Array<String?>)[state.focused] = null
            call(it, "applyLayout")
        }
        await(s, "Explicit new shell recovers an empty workspace with a retained dead pane") {
            val state = field(it, "selection") as TermuxWorkspaceState
            state.focusedTarget != null && state.focusedTarget != "@0" && text(it, state.focused).contains("$")
        }
        checks.put("Explicit Add creates and selects exactly a new live shell after the final exit")

    }
    @Test fun existingPaneStreamsInputEditorsResizeAndRecreation() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Private helper fixture required", args.getString("phoneStreamProbe") == "true")
        val port = requireNotNull(args.getString("hostFixturePort")?.toIntOrNull())
        val targets = requireNotNull(args.getString("targets")).split(',')
        require(targets.size == 2 && targets.all { it.matches(Regex("@[0-9]+")) })
        val context = instrumentation.targetContext
        assumeTrue("Phone fixture never writes the regular launcher", context.packageName.endsWith(".fetchdebug"))
        val appearance = java.io.File(context.filesDir, "stream-probe-appearance")
        if (appearance.isDirectory) {
            val destination = java.io.File(context.getExternalFilesDir(null), "Re-T-UI")
            check(destination.exists() || destination.mkdirs())
            appearance.listFiles()?.forEach { file -> file.copyTo(java.io.File(destination, file.name), overwrite = true) }
        }
        val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        assumeTrue("Disable regular RUN_COMMAND while using private fixture", !TermuxBridgeManager.status(context).runCommandGranted)
        val original = prefs.all.toMap()
        val samples = JSONArray()
        val checks = JSONArray()
        val state = TermuxWorkspaceState().apply {
            select(0, targets[0]); select(1, targets[1]); layout = TermuxWorkspaceState.Layout.TOP_BOTTOM
        }
        prefs.edit().putString("selection", state.encode()).remove("draft").commit()
        val report = JSONObject().put("checks", checks).put("input_to_after_draw_ms", samples)
        try {
            ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
                scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                await(scenario, "Workspace must be in foreground") { it.hasWindowFocus() && field(it, "visible") == true }
                scenario.onActivity { attach(it, port) }
                await(scenario, "Both original grid views must have engine seeds") { (field(it, "terminals") as Array<*>).all { terminal -> terminal != null } }
                checks.put("Existing activity, input editor, grids and both tiles use the engine")
                scenario.onActivity { call(it, "focusTile", 0); call(it, "key", "C-u") }
                var expected = ""
                for (character in "abcdefghijklmnopqrstuvwx") {
                    expected += character
                    val marker = expected
                    val latch = CountDownLatch(1)
                    var started = 0L
                    scenario.onActivity { activity ->
                        val grid = (field(activity, "grids") as Array<*>)[0] as TerminalGridView
                        var posted = false
                        val cellsField = TerminalGridView::class.java.getDeclaredField("cells").apply { isAccessible = true }
                        val textField = (cellsField.get(grid) as Array<*>)[0]!!.javaClass.getDeclaredField("text").apply { isAccessible = true }
                        fun drawnText() = (cellsField.get(grid) as Array<*>).joinToString("") { textField.get(it) as String }
                        val listener = object : ViewTreeObserver.OnDrawListener {
                            override fun onDraw() {
                                if (!posted && drawnText().contains(marker)) {
                                    posted = true
                                    grid.post {
                                        grid.viewTreeObserver.removeOnDrawListener(this)
                                        samples.put((SystemClock.elapsedRealtimeNanos() - started) / 1e6)
                                        latch.countDown()
                                    }
                                }
                            }
                        }
                        grid.viewTreeObserver.addOnDrawListener(listener)
                        started = SystemClock.elapsedRealtimeNanos(); type(activity, character.toString())
                    }
                    assertTrue("Committed key must be drawn", latch.await(5, TimeUnit.SECONDS))
                }
                checks.put("24 committed keys measured through the existing input connection and after-draw callback")
                command(scenario, "printf '\\nUNICODE_λ界🙂_é\\n'")
                await(scenario, "Unicode output must survive chunks") { text(it).contains("UNICODE_λ界🙂_é") }
                checks.put("Unicode, wide characters and combining characters")
                scenario.onActivity { call(it, "focusTile", 1) }
                command(scenario, "printf '\\nSECOND_TILE_OK\\n'")
                await(scenario, "Second shell must be independent") { text(it, 1).contains("SECOND_TILE_OK") }
                scenario.onActivity {
                    assertFalse(text(it, 0).contains("SECOND_TILE_OK")); call(it, "focusTile", 0)
                    (field(it, "selection") as TermuxWorkspaceState).layout = TermuxWorkspaceState.Layout.LEFT_RIGHT
                    call(it, "applyLayout")
                }
                await(scenario, "Landscape tile layout must reseed") { (field(it, "terminals") as Array<*>).all { terminal -> terminal != null } }
                command(scenario, "vim -Nu NONE -n retui-stream-integration-note")
                await(scenario, "Vim alternate screen must open") { ((field(it, "terminals") as Array<*>)[0] as? TermuxWorkspaceTerminal)?.emulator?.isAlternateBufferActive == true }
                scenario.onActivity { type(it, "iVIM_INTEGRATED_OK") }
                await(scenario, "Vim live editing") { text(it).contains("VIM_INTEGRATED_OK") }
                scenario.recreate(); scenario.onActivity { attach(it, port) }
                await(scenario, "Vim reconnect must recover alternate screen") { text(it).contains("VIM_INTEGRATED_OK") }
                scenario.onActivity { call(it, "key", "Escape"); type(it, ":q!"); call(it, "enter") }
                await(scenario, "Vim quit must restore original shell buffer") {
                    val terminal = (field(it, "terminals") as Array<*>)[0] as? TermuxWorkspaceTerminal
                    terminal != null && !terminal.emulator.isAlternateBufferActive && text(it).contains("UNICODE_")
                }
                checks.put("Vim edit, activity recreation in alternate screen and saved-shell recovery")
                command(scenario, "nano retui-stream-integration-note")
                await(scenario, "Nano opens") { text(it).contains("Help") && text(it).contains("Write Out") && text(it).contains("Exit") }
                scenario.onActivity { type(it, "NANO_INTEGRATED_OK") }
                await(scenario, "Nano live editing") { text(it).contains("NANO_INTEGRATED_OK") }
                scenario.onActivity { call(it, "key", "C-x") }
                await(scenario, "Nano discard prompt") { text(it).contains("Save modified") || text(it).contains("Save file") }
                scenario.onActivity { type(it, "n") }
                await(scenario, "Nano returns to shell") { text(it).contains("UNICODE_") }
                checks.put("Nano edit and discard")
                scenario.onActivity { call(it, "focusInput", true) }
                Thread.sleep(1200)
                scenario.onActivity { call(it, "focusInput", false); (it.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(it.window.decorView.windowToken, 0) }
                Thread.sleep(500)
                scenario.recreate(); scenario.onActivity { attach(it, port) }
                await(scenario, "Both shells must reconnect after keyboard and recreation") { text(it).contains("UNICODE_") && text(it, 1).contains("SECOND_TILE_OK") }
                checks.put("Installed keyboard resize and both-shell recreation")
                if (args.getString("productionStress") == "true") productionStress(scenario, port, checks)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    context.openFileOutput("integrated-pane.png", Context.MODE_PRIVATE).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
            }
        } finally {
            context.openFileOutput("integrated-pane-results.json", Context.MODE_PRIVATE).use { it.write(report.toString(2).toByteArray()) }
            val edit = prefs.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value); is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }; edit.commit()
        }
    }
}
