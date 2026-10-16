package ohi.andre.consolelauncher.managers.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in physical-device proof; pairing state is restored through Android's native permission boundary. */
class TermuxWorkspaceSshAppearanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var debugCycles = 0
    private var debugReconnects = 0
    private val prefix = "sshappearance-${UUID.randomUUID().toString().take(8)}"
    private fun field(instance: Any, name: String): Any? = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
    private fun call(activity: TermuxWorkspaceActivity, name: String, vararg arguments: Any?): Any? =
        activity.javaClass.declaredMethods.single { it.name == name && it.parameterTypes.size == arguments.size }
            .apply { isAccessible = true }.invoke(activity, *arguments)
    private fun native(arguments: Array<String?>): String {
        val label = "$prefix-${UUID.randomUUID()}"
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
                TermuxBridgeManager.createResultPendingIntent(context, label, null), arguments)
            assertTrue("Native Termux result timed out for $label", latch.await(20, TimeUnit.SECONDS))
            val answer = result.get()
            assertEquals(answer.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR), 0,
                answer.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1))
            return answer.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty()
        } finally { broadcasts.unregisterReceiver(receiver) }
    }
    private fun script(body: String, vararg arguments: String) = native(arrayOf("-c", "set -eu\nexport PATH=\"\$PREFIX/bin:\$PATH\"\n$body", "retui-appearance", *arguments))
    private fun controls(): String = script("""
        tmux list-clients -t '=retui_workspace' -F '#{client_control_mode}|#{client_pid}' 2>/dev/null |
            awk -F '|' '${'$'}1 == 1 { print ${'$'}2 }' | sort -n
    """.trimIndent()).trim()
    private fun awaitControls(expected: String) {
        val deadline = SystemClock.uptimeMillis() + 3000
        var actual: String
        do {
            actual = controls()
            if (actual == expected) return
            Thread.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals("Closing SSH must dispose only its control client and preserve pre-existing clients", expected, actual)
    }
    private fun await(scenario: ActivityScenario<TermuxWorkspaceActivity>, description: String,
                      predicate: (TermuxWorkspaceActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            Thread.sleep(50)
        }
        scenario.onActivity {
            val details = JSONObject().put("description", description).put("focus", it.hasWindowFocus())
                .put("status", it.findViewById<android.widget.TextView>(R.id.termux_workspace_label).text.toString())
                .put("helper", (field(it, "helperBody") as android.widget.TextView).text.toString())
                .put("helper_visible", (field(it, "helperPanel") as View).visibility)
                .put("ssh_connected", (field(it, "ssh") as? TermuxWorkspaceSshConnection)?.connected)
                .put("ssh_connecting", field(it, "sshConnecting"))
                .put("cycles", debugCycles).put("reconnects", debugReconnects)
                .put("result_pending", field(it, "resultLabel"))
                .put("draft", field(it, "localDraft"))
                .put("selection", (field(it, "selection") as TermuxWorkspaceState).encode())
                .put("ready", (field(it, "sshTileReady") as BooleanArray).contentToString())
                .put("terminal_text", terminals(it).map { terminal -> terminal?.text().orEmpty().take(300) }.toString())
            File(context.filesDir, "ssh-appearance-failure.json").writeText(details.toString(2))
        }
        fail(description)
    }
    private fun terminals(activity: TermuxWorkspaceActivity) = (field(activity, "terminals") as Array<*>).map { it as? TermuxWorkspaceTerminal }
    private fun dimensions(activity: TermuxWorkspaceActivity) = terminals(activity).map { requireNotNull(it).emulator.let { emulator -> emulator.mColumns to emulator.mRows } }
    private fun attachments(activity: TermuxWorkspaceActivity): List<Any?> {
        val map = field(requireNotNull(field(activity, "ssh")), "tiles") as Map<*, *>
        return synchronized(map) { listOf(map[0], map[1]) }
    }
    @Test fun runtimeDiagnostics() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("termuxSshDevice") == "true")
        println(script("""
            sshd -V 2>&1 || true
            printf 'PID_FILE '; cat "${'$'}HOME/.retui/ssh/sshd.pid" || true
            printf 'NOAUTH_ZERO_PROBE\n'
            if sshd -T -f "${'$'}HOME/.retui/ssh/sshd_config" -o PerSourcePenalties=noauth:0 > "${'$'}TMPDIR/retui-noauth-probe" 2>&1; then
                grep -i persource "${'$'}TMPDIR/retui-noauth-probe"
            else
                cat "${'$'}TMPDIR/retui-noauth-probe"
            fi
            rm -f "${'$'}TMPDIR/retui-noauth-probe"
            sshd -T -f "${'$'}HOME/.retui/ssh/sshd_config" | grep -i persource || true
            command -v flock || true
            if command -v flock >/dev/null; then dpkg -S "${'$'}(command -v flock)"; fi
        """.trimIndent()))
    }
    @Test fun defaultSshAppearanceResizesBothRemoteTilesAndPreservesDraftAcrossReconnect() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("termuxSshDevice") == "true")
        assertTrue(BuildConfig.TERMUX_SSH_PANE)
        check(BuildConfig.DEBUG && context.packageName != "com.dvil.tui_renewed") { "Use an isolated debug test application" }
        assertTrue(TermuxBridgeManager.hasRunCommandPermission(context))
        XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val settings = listOf(Ui.font_size_offset, Ui.system_font, Theme.workspace_output_text_color,
            Theme.workspace_control_text_color, Behavior.breath_out).associateWith { LauncherSettings.get(it) }
        val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        prefs.edit().putString("selection", TermuxWorkspaceState().encode()).remove("draft").commit()
        val owned = mutableListOf<String>()
        var backup: String? = null
        var originalHashes = ""
        val traceDaemon = InstrumentationRegistry.getArguments().getString("sshDaemonTrace") == "true"
        var baselineControls = ""
        val diagnosticLog = "/data/data/com.termux/files/home/.retui/$prefix-sshd.log"
        var cycles = 0
        var reconnects = 0
        val started = SystemClock.uptimeMillis()
        // Prevent onResume from provisioning the isolated identity before the native-state backup.
        TermuxWorkspaceSshSetup.clearPairing(context)
        val scenario = ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java))
        try {
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            await(scenario, "Test pane must remain foreground") { it.hasWindowFocus() }
            backup = script("""
                base="${'$'}HOME/.retui"
                mkdir -p "${'$'}base"
                saved=${'$'}(mktemp -d "${'$'}base/ssh-appearance-backup.XXXXXXXX")
                if [ -d "${'$'}base/ssh" ]; then cp -a "${'$'}base/ssh" "${'$'}saved/state"; fi
                printf '%s\n' "${'$'}saved"
            """.trimIndent()).trim()
            require(backup!!.matches(Regex("/data/data/com[.]termux/files/home/[.]retui/ssh-appearance-backup[.][A-Za-z0-9]+")))
            originalHashes = script("if [ -d \"\$1/state\" ]; then cd \"\$1/state\"; find . -type f ! -name sshd.pid -exec sha256sum {} \\; | sort; fi", backup!!)
            TermuxWorkspaceSshSetup.parse(context, native(TermuxWorkspaceSshSetup.commandArguments(context, false)))
            if (traceDaemon) {
            script("""
                state="${'$'}HOME/.retui/ssh"
                for process in /proc/[0-9]*; do
                    [ "${'$'}(readlink "${'$'}process/exe" 2>/dev/null || true)" = "${'$'}PREFIX/bin/sshd" ] || continue
                    if tr '\000' '\n' < "${'$'}process/cmdline" 2>/dev/null | grep -F -x -- "${'$'}state/sshd_config" >/dev/null; then
                        pid=${'$'}{process##*/}; kill "${'$'}pid"
                        n=0; while kill -0 "${'$'}pid" 2>/dev/null && [ "${'$'}n" -lt 30 ]; do sleep 0.1; n=${'$'}((n + 1)); done
                        ! kill -0 "${'$'}pid" 2>/dev/null
                    fi
                done
            """.trimIndent())
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                null, arrayOf("-c", "exec \"\$PREFIX/bin/sshd\" -D -E \"\$1\" -o LogLevel=DEBUG3 -f \"\$HOME/.retui/ssh/sshd_config\"", "retui-sshd-diagnostic", diagnosticLog))
            Thread.sleep(500)
            } else {
                TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                    null, TermuxWorkspaceSshSetup.daemonArguments(context))
            }
            File(context.filesDir, "ssh-appearance-native-before.log").writeText(script("""
                sshd -V 2>&1 || true
                cat "${'$'}HOME/.retui/ssh/sshd_config"
                sshd -T -f "${'$'}HOME/.retui/ssh/sshd_config"
                printf '\nPROCESS\n'; ps -A -o pid,ppid,args | grep '[s]shd' || true
            """.trimIndent()))
            repeat(2) { index ->
                val target = script("if tmux has-session -t '=retui_workspace' 2>/dev/null; then tmux new-window -d -t '=retui_workspace' -P -F '#{window_id}' -n \"\$1\" 'exec sh'; else tmux new-session -d -s retui_workspace -P -F '#{window_id}' -n \"\$1\" 'exec sh'; fi", "$prefix-$index").trim()
                require(target.matches(Regex("@[0-9]+"))); owned += target
            }
            baselineControls = controls()
            scenario.onActivity { call(it, "startBridge") }
            await(scenario, "Dedicated pinned SSH connection must become ready") {
                (field(it, "ssh") as? TermuxWorkspaceSshConnection)?.connected == true && field(it, "sshConnecting") == false
            }
            await(scenario, "Both unique fixture windows must reach the live window list") {
                val windows = field(it, "windows") as List<*>
                owned.all { target -> windows.any { window -> (window as Pair<*, *>).first == target } }
            }
            val state = TermuxWorkspaceState().apply { select(0, owned[0]); select(1, owned[1]); focus(1); layout = TermuxWorkspaceState.Layout.LEFT_RIGHT }
            scenario.onActivity {
                LauncherSettings.set(Behavior.breath_out, "false")
                LauncherSettings.set(Ui.system_font, "true")
                LauncherSettings.set(Ui.font_size_offset, "0")
                call(it, "clearTiles")
                it.javaClass.getDeclaredField("selection").apply { isAccessible = true }.set(it, state)
                call(it, "applyLayout")
            }
            await(scenario, "Both SSH tiles must attach and render") {
                terminals(it).all { terminal -> terminal?.text()?.isNotBlank() == true } &&
                    (field(it, "sshTileReady") as BooleanArray).all { ready -> ready }
            }
            val baselineControlSet = baselineControls.lineSequence().filter { it.isNotBlank() }.toSet()
            val attachedControlSet = controls().lineSequence().filter { it.isNotBlank() }.toSet()
            assertTrue("Existing control clients must survive opening the SSH workspace", attachedControlSet.containsAll(baselineControlSet))
            assertEquals("Live SSH workspace must own exactly one additional control client", baselineControlSet.size + 1, attachedControlSet.size)
            val pids = owned.map { script("tmux display-message -p -t \"\$1\" '#{pane_pid}'", it).trim() }
            scenario.onActivity { call(it, "directText", ":retained 😀 appearance") }
            await(scenario, "Shell colon draft must be created over actual SSH") { field(it, "localDraft") == "retained 😀 appearance" }
            val before = AtomicReference<List<Pair<Int, Int>>>()
            scenario.onActivity { before.set(dimensions(it)) }
            val expectedSelection = state.encode()
            val loopStarted = SystemClock.uptimeMillis()
            val loopDeadline = loopStarted + 120000
            while (SystemClock.uptimeMillis() < loopDeadline) {
                val offset = if (cycles % 2 == 0) "7" else "0"
                var oldConnection: Any? = null
                var oldTerminals = emptyList<TermuxWorkspaceTerminal?>()
                var oldAttachments = emptyList<Any?>()
                scenario.onActivity {
                    oldConnection = field(it, "ssh"); oldTerminals = terminals(it); oldAttachments = attachments(it)
                    LauncherSettings.set(Ui.font_size_offset, offset)
                    LauncherSettings.set(Theme.workspace_output_text_color, "#FF123456")
                    LauncherSettings.set(Theme.workspace_control_text_color, "#FF654321")
                }
                await(scenario, "Both emulator geometries must follow the changed font") { activity ->
                    if (terminals(activity).any { it == null }) return@await false
                    val grids = (field(activity, "grids") as Array<*>).map { it as TerminalGridView }
                    val expected = grids.map { grid -> (grid.width / grid.characterWidth()).toInt().coerceIn(20, 240) to (grid.height / grid.lineHeight()).coerceIn(8, 120) }
                    dimensions(activity) == expected && if (offset == "7") expected.indices.all { expected[it] != before.get()[it] } else expected == before.get()
                }
                var expected = emptyList<Pair<Int, Int>>()
                scenario.onActivity { activity ->
                    expected = dimensions(activity)
                    assertSame(oldConnection, field(activity, "ssh"))
                    terminals(activity).indices.forEach { tile -> assertSame(oldTerminals[tile], terminals(activity)[tile]); assertSame(oldAttachments[tile], attachments(activity)[tile]) }
                    assertEquals(expectedSelection, (field(activity, "selection") as TermuxWorkspaceState).encode())
                    assertEquals("retained 😀 appearance", field(activity, "localDraft"))
                    val input = activity.findViewById<TermuxWorkspaceInputEditText>(R.id.termux_workspace_input)
                    assertEquals(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                        if (offset == "7") 19f else 12f, activity.resources.displayMetrics), input.textSize, 0.01f)
                    (field(activity, "grids") as Array<*>).forEach { grid ->
                        assertEquals(0xff123456.toInt(), field(grid!!, "terminalForeground"))
                        assertEquals(ohi.andre.consolelauncher.tuils.Tuils.getTypeface(activity), (field(grid, "textPaint") as android.graphics.Paint).typeface)
                    }
                }
                owned.forEachIndexed { tile, target ->
                    val wanted = "${expected[tile].first}x${expected[tile].second}|${pids[tile]}"
                    var actual = ""
                    val remoteDeadline = SystemClock.uptimeMillis() + 5000
                    do {
                        actual = script("tmux display-message -p -t \"\$1\" '#{pane_width}x#{pane_height}|#{pane_pid}'", target).trim()
                        if (actual == wanted) break
                        Thread.sleep(100)
                    } while (SystemClock.uptimeMillis() < remoteDeadline)
                    assertEquals("Native tmux dimensions and process must follow tile $tile", wanted, actual)
                }
                cycles++; debugCycles = cycles
                if (cycles % 3 == 0) {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    awaitControls(baselineControls)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.onActivity {
                        it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        (field(it, "screen") as ohi.andre.consolelauncher.BreathOutLayout).configure(false, 900)
                    }
                    await(scenario, "The real SSH pane must regain foreground window focus") { it.hasWindowFocus() }
                    await(scenario, "Both tiles and colon draft must survive foreground reconnect") {
                        terminals(it).all { terminal -> terminal?.text()?.isNotBlank() == true } &&
                            (field(it, "sshTileReady") as BooleanArray).all { ready -> ready } && field(it, "localDraft") == "retained 😀 appearance"
                    }
                    reconnects++; debugReconnects = reconnects
                }
                Thread.sleep(1000)
            }
            File(context.filesDir, "ssh-appearance-proof.json").writeText(JSONObject().put("cycles", cycles)
                .put("reconnects", reconnects).put("loop_ms", SystemClock.uptimeMillis() - loopStarted)
                .put("remote_pids_preserved", true).put("both_tiles_resized", true).put("control_clients_restored_each_pause", true).put("diagnostic_daemon", traceDaemon).put("pre_existing_control_clients", baselineControlSet.size).toString(2))
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.filesDir, "ssh-appearance-proof.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        } finally {
            try {
                scenario.onActivity { (field(it, "ssh") as? TermuxWorkspaceSshConnection)?.close() }
                File(context.filesDir, "ssh-appearance-native-after.log").writeText(script("""
                    if [ "${'$'}2" = 1 ]; then printf 'DAEMON LOG\n'; tail -n 1600 "${'$'}1" || true; fi
                    printf '\nPROCESS\n'; ps -A -o pid,ppid,args | grep '[s]shd' || true
                    printf '\nPIDFILE\n'; cat "${'$'}HOME/.retui/ssh/sshd.pid" || true
                """.trimIndent(), diagnosticLog, if (traceDaemon) "1" else "0"))
                if (owned.isNotEmpty()) script("for target in \"\$@\"; do tmux kill-window -t \"\$target\" 2>/dev/null || true; done", *owned.toTypedArray())
            } finally {
                try {
                    backup?.let { saved ->
                        val restored = script("""
                            saved="${'$'}1"; state="${'$'}HOME/.retui/ssh"
                            [ -d "${'$'}saved" ] || exit 1
                            if [ "${'$'}2" = 1 ]; then
                            for process in /proc/[0-9]*; do
                                [ "${'$'}(readlink "${'$'}process/exe" 2>/dev/null || true)" = "${'$'}PREFIX/bin/sshd" ] || continue
                                if tr '\000' '\n' < "${'$'}process/cmdline" 2>/dev/null | grep -F -x -- "${'$'}state/sshd_config" >/dev/null; then
                                    kill "${'$'}{process##*/}"
                                fi
                            done
                            fi
                            rm -rf -- "${'$'}state"
                            if [ -d "${'$'}saved/state" ]; then mv "${'$'}saved/state" "${'$'}state"; fi
                            rmdir "${'$'}saved"
                            if [ -r "${'$'}state/sshd.pid" ]; then
                                pid=${'$'}(cat "${'$'}state/sshd.pid")
                                case "${'$'}pid" in ''|*[!0-9]*) ;; *)
                                    if kill -0 "${'$'}pid" 2>/dev/null && tr '\000' '\n' < "/proc/${'$'}pid/cmdline" 2>/dev/null | grep -F -- "${'$'}state/sshd_config" >/dev/null; then kill -HUP "${'$'}pid"; fi ;;
                                esac
                            fi
                            if [ -d "${'$'}state" ]; then cd "${'$'}state"; find . -type f ! -name sshd.pid -exec sha256sum {} \; | sort; fi
                        """.trimIndent(), saved, if (traceDaemon) "1" else "0")
                        TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                            null, TermuxWorkspaceSshSetup.daemonArguments(context))
                        assertEquals("Dedicated production SSH state must be restored byte-for-byte", originalHashes, restored)
                    }
                } finally {
                    scenario.onActivity { settings.forEach { (key, value) -> LauncherSettings.set(key, value) } }
                    scenario.close()
                    val edit = prefs.edit().clear()
                    original.forEach { (key, value) -> when (value) {
                        is String -> edit.putString(key, value)
                        is Boolean -> edit.putBoolean(key, value)
                        is Int -> edit.putInt(key, value)
                        is Long -> edit.putLong(key, value)
                        is Float -> edit.putFloat(key, value)
                        is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                    } }
                    edit.commit()
                    println("SSH appearance duration_ms=${SystemClock.uptimeMillis() - started}; cycles=$cycles; reconnects=$reconnects; backup=$backup")
                }
            }
        }
    }
}
