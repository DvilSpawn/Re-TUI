package ohi.andre.consolelauncher.managers.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.WindowManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.help.OfflineHelpActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Opt-in real Termux stress; creates and removes exactly one uniquely named fixture window. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceSshStressTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixture = "ssh-stress-${UUID.randomUUID()}"
    private fun stage(value: String) = File(context.cacheDir, "ssh-stress-stage.txt").writeText(value)

    private fun nativeCommand(arguments: Array<String?>): String {
        val label = "$fixture-${UUID.randomUUID()}"
        val finished = CountDownLatch(1)
        val reply = AtomicReference<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) == label) {
                    reply.set(intent); finished.countDown()
                }
            }
        }
        val broadcasts = LocalBroadcastManager.getInstance(context)
        broadcasts.registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT))
        try {
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH,
                TermuxBridgeManager.TERMUX_HOME, TermuxBridgeManager.createResultPendingIntent(context, label, null), arguments)
            assertTrue("Native Termux fixture timed out", finished.await(20, TimeUnit.SECONDS))
            val result = reply.get()
            assertEquals(result.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR).orEmpty() + "\n" +
                result.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty(), 0,
                result.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1))
            return result.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty().trim()
        } finally { broadcasts.unregisterReceiver(receiver) }
    }

    private fun await(message: String, error: AtomicReference<String>, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            error.get()?.let { fail("$message: $it") }
            if (condition()) return
            Thread.sleep(25)
        }
        fail(message)
    }

    @Test fun unicodeBulkInputBurstResizeAndFiveReconnectsRetainOneJob() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("termuxSshDevice") == "true")
        val host = ActivityScenario.launch<OfflineHelpActivity>(Intent(context, OfflineHelpActivity::class.java)
            .putExtra("topic", "termux-workspace"))
        host.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        var connection: TermuxWorkspaceSshConnection? = null
        var target: String? = null
        val error = AtomicReference<String>()
        val epoch = AtomicInteger()
        val terminal = AtomicReference<TermuxWorkspaceTerminal>()
        val screen = AtomicReference("")
        val streamedBytes = AtomicInteger()
        val results = JSONArray()
        try {
            stage("native setup")
            assertTrue(TermuxBridgeManager.isTermuxInstalled(context))
            assertTrue(TermuxBridgeManager.hasRunCommandPermission(context))
            val info = TermuxWorkspaceSshSetup.parse(context,
                nativeCommand(TermuxWorkspaceSshSetup.commandArguments(context, false)))
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH,
                TermuxBridgeManager.TERMUX_HOME, null, TermuxWorkspaceSshSetup.daemonArguments(context))

            fun connect(): TermuxWorkspaceSshConnection {
                val ownEpoch = epoch.incrementAndGet()
                error.set(null)
                val ready = CountDownLatch(1)
                val next = TermuxWorkspaceSshConnection(info, object : TermuxWorkspaceSshConnection.Listener {
                    override fun onReady(windows: List<Pair<String, String>>) { ready.countDown() }
                    override fun onWindows(windows: List<Pair<String, String>>) = Unit
                    override fun onTargetGone(target: String) {
                        if (epoch.get() == ownEpoch) error.set("Fixture window exited unexpectedly: $target")
                    }
                    override fun onError(message: String) {
                        if (epoch.get() == ownEpoch) error.set(message)
                        ready.countDown()
                    }
                })
                connection = next
                next.connect()
                assertTrue("SSH readiness timed out", ready.await(20, TimeUnit.SECONDS))
                assertNull(error.get())
                assertTrue(next.connected)
                return next
            }

            fun attach(active: TermuxWorkspaceSshConnection, window: String, cols: Int, rows: Int) {
                val ownEpoch = epoch.get()
                screen.set("")
                instrumentation.runOnMainSync {
                    val grid = TerminalGridView(context)
                    terminal.set(TermuxWorkspaceTerminal(cols, rows, grid) { bytes -> active.write(0, bytes) })
                }
                active.attach(0, window, cols, rows, { bytes ->
                    if (epoch.get() == ownEpoch) {
                        streamedBytes.addAndGet(bytes.size)
                        terminal.get().append(bytes)
                        screen.set(terminal.get().text())
                    }
                }, { if (epoch.get() == ownEpoch) error.set("Attached fixture closed unexpectedly") })
            }

            fun write(active: TermuxWorkspaceSshConnection, bytes: ByteArray) {
                await("SSH input did not become writable", error) { active.write(0, bytes) }
            }

            val payload = ByteArrayOutputStream().apply {
                val unit = "é中🙂e\u0301".toByteArray(Charsets.UTF_8)
                while (size() + unit.size <= 65536) write(unit)
                while (size() < 65536) write('x'.code)
            }.toByteArray()
            val expectedSha = MessageDigest.getInstance("SHA-256").digest(payload)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val nonce = UUID.randomUUID().toString()
            // Raw mode removes input echo. SHA is computed remotely over exactly the transmitted bytes.
            val python = """
                import hashlib, os, signal, tty
                tty.setraw(0)
                nonce = '$nonce'
                def output(data):
                    while data:
                        data = data[os.write(1, data):]
                output(('STRESS_WAIT_' + nonce).encode())
                data = bytearray()
                while len(data) < 65536:
                    part = os.read(0, 65536 - len(data))
                    if not part:
                        raise SystemExit('bulk input disconnected')
                    data.extend(part)
                digest = hashlib.sha256(data).hexdigest()
                unit = '界🙂e\u0301 '.encode()
                burst = unit * (20480 // len(unit))
                burst += b'x' * (20480 - len(burst))
                for offset in range(0, len(burst), 317):
                    output(burst[offset:offset + 317])
                round_number = 0
                def redraw(*unused):
                    size = os.get_terminal_size(0)
                    text = ('\x1b[2J\x1b[HSTRESS_DONE_' + nonce + '\r\nJOB_PID=' + str(os.getpid()) +
                        '\r\nSHA=' + digest + '\r\nUNICODE=界🙂e\u0301\r\nBURST_BYTES=' + str(len(burst)) +
                        '\r\nSIZE=' + str(size.columns) + 'x' + str(size.lines) + '\r\nROUND=' + str(round_number))
                    output(text.encode())
                signal.signal(signal.SIGWINCH, redraw)
                redraw()
                while True:
                    command = os.read(0, 1)
                    if not command:
                        break
                    if command == b'r':
                        round_number += 1
                        redraw()
            """.trimIndent()
            val initial = "python -u -c '" + python.replace("'", "'\\''") + "'"
            stage("create raw-mode fixture")
            var active = connect()
            val created = CountDownLatch(1)
            val window = AtomicReference<String>()
            active.newWindow(fixture, initial) { window.set(it); created.countDown() }
            assertTrue("Stress window creation timed out: ${error.get()}", created.await(15, TimeUnit.SECONDS))
            target = window.get()
            assertTrue(target!!.matches(Regex("@[0-9]+")))
            attach(active, target!!, 120, 32)
            await("Raw-mode fixture never became ready", error) { screen.get().contains("STRESS_WAIT_$nonce") }
            stage("transmit 65536-byte Unicode payload")
            write(active, payload)
            await("Remote SHA or Unicode burst was not rendered", error) {
                val text = screen.get()
                text.contains("SHA=$expectedSha") && text.contains("UNICODE=界🙂e\u0301") && text.contains("BURST_BYTES=20480")
            }
            val process = Regex("JOB_PID=([0-9]+)").find(screen.get())?.groupValues?.get(1)
            assertNotNull("Remote job did not identify itself", process)

            repeat(5) { iteration ->
                val round = iteration + 1
                val cols = 92 + iteration * 4
                val rows = 28 + iteration
                stage("resize and reconnect $round")
                instrumentation.runOnMainSync {
                    val grid = TerminalGridView(context)
                    terminal.get().resize(cols, rows, grid)
                }
                active.resize(0, cols, rows)
                await("Remote PTY did not resize to ${cols}x$rows", error) { screen.get().contains("SIZE=${cols}x$rows") }
                val nativeSize = nativeCommand(arrayOf("-c", "tmux display-message -p -t \"\$1\" '#{pane_width}x#{pane_height}'", fixture, target))
                assertEquals("Native tmux PTY size differs", "${cols}x$rows", nativeSize)
                active.detach(0)
                active.close()
                active = connect()
                attach(active, target!!, cols, rows)
                await("Reconnect lost the persisted terminal screen", error) {
                    screen.get().contains("STRESS_DONE_$nonce") && screen.get().contains("SHA=$expectedSha")
                }
                write(active, byteArrayOf('r'.code.toByte()))
                await("Reconnected job did not process input for round $round", error) {
                    val text = screen.get()
                    text.contains("ROUND=$round") && text.contains("JOB_PID=$process") && text.contains("SIZE=${cols}x$rows") &&
                        text.contains("SHA=$expectedSha") && text.contains("UNICODE=界🙂e\u0301")
                }
                results.put(JSONObject().put("round", round).put("job_pid", process).put("pty", nativeSize))
            }
            stage("passed; cleaning fixture")
            File(context.filesDir, "termux-ssh-stress.json").writeText(JSONObject().put("payload_bytes", payload.size)
                .put("sha256", expectedSha).put("remote_burst_bytes", 20480).put("observed_stream_bytes", streamedBytes.get())
                .put("reconnects", results).put("bounds", "Raw-mode remote SHA proves bulk input; tmux may coalesce the 20 KiB burst before SSH delivery. A fresh emulator verifies Unicode and persistent redraw after each new SSH connection.").toString(2))
        } finally {
            epoch.incrementAndGet()
            connection?.close()
            try {
                target?.let { window ->
                    check(window.matches(Regex("@[0-9]+")))
                    nativeCommand(arrayOf("-c", "tmux kill-window -t \"\$1\" 2>/dev/null || true", fixture, window))
                }
            } finally { host.close() }
        }
    }
}
