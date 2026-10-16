package ohi.andre.consolelauncher.managers.termux

import android.content.*
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.UIManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in physical comparison: terminal input -> completed view draw, plus foreground idle cost. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspacePaneBenchmarkTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val fixture = "sshbench-${UUID.randomUUID().toString().take(8)}"
    private fun field(activity: TermuxWorkspaceActivity, name: String): Any? =
        TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    @Suppress("UNCHECKED_CAST")
    private fun terminal(activity: TermuxWorkspaceActivity, tile: Int) =
        (field(activity, "terminals") as Array<TermuxWorkspaceTerminal?>)[tile]
    private fun command(script: String, vararg args: String): String {
        val label = "$fixture-${UUID.randomUUID()}"
        val done = CountDownLatch(1)
        val result = AtomicReference<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) == label) { result.set(intent); done.countDown() }
            }
        }
        val broadcasts = LocalBroadcastManager.getInstance(context)
        broadcasts.registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT))
        try {
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                TermuxBridgeManager.createResultPendingIntent(context, label, null), arrayOf("-c", script, "retui-benchmark", *args))
            assertTrue("Termux benchmark command timed out", done.await(20, TimeUnit.SECONDS))
            val reply = result.get()
            assertEquals(reply.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR), 0, reply.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1))
            return reply.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty().trim()
        } finally { broadcasts.unregisterReceiver(receiver) }
    }
    private fun await(scenario: ActivityScenario<TermuxWorkspaceActivity>, predicate: (TermuxWorkspaceActivity) -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < end) {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            Thread.sleep(25)
        }
        fail("Pane benchmark did not become ready")
    }

    private fun termuxSnapshot(): JSONObject {
        val python = """
            import json, os, time
            started = time.monotonic_ns()
            uid, sampler = os.getuid(), os.getpid()
            page_kib = os.sysconf('SC_PAGE_SIZE') // 1024
            processes, rss_total, pss_total, pss_readable, pss_denied = {}, 0, 0, 0, 0
            def counters(pid):
                with open('/proc/' + pid + '/stat') as source:
                    fields = source.read().rsplit(')', 1)[1].split()
                return fields[19], int(fields[11]) + int(fields[12]), int(fields[21]) * page_kib
            for pid in os.listdir('/proc'):
                if not pid.isdigit() or int(pid) == sampler:
                    continue
                try:
                    if os.stat('/proc/' + pid).st_uid != uid:
                        continue
                    birth, ticks, rss = counters(pid)
                    pss = None
                    for memory_file in ('smaps_rollup', 'smaps'):
                        try:
                            with open('/proc/' + pid + '/' + memory_file) as memory:
                                pss = sum(int(line.split()[1]) for line in memory if line.startswith('Pss:'))
                            break
                        except OSError:
                            pass
                    if counters(pid)[0] != birth:
                        continue
                    processes[pid + ':' + birth] = ticks
                    rss_total += rss
                    if pss is None:
                        pss_denied += 1
                    else:
                        pss_total += pss
                        pss_readable += 1
                except (OSError, ValueError, IndexError):
                    continue
            ended = time.monotonic_ns()
            print(json.dumps(dict(uid=uid, clk_tck=os.sysconf('SC_CLK_TCK'), processes=processes,
                midpoint_ns=(started + ended) // 2, sampling_ms=(ended - started) / 1000000,
                owned_rss_kib=rss_total, readable_pss_kib=pss_total, pss_readable_processes=pss_readable,
                pss_denied_processes=pss_denied, memory_metric='pss' if pss_denied == 0 else 'rss',
                owned_memory_kib=pss_total if pss_denied == 0 else rss_total)))
        """.trimIndent()
        return JSONObject(command("python -c \"\$1\"", python))
    }

    private fun termuxIdleCost(before: JSONObject, after: JSONObject): JSONObject {
        val first = before.getJSONObject("processes")
        val last = after.getJSONObject("processes")
        val firstKeys = first.keys().asSequence().toSet()
        val lastKeys = last.keys().asSequence().toSet()
        val common = firstKeys.intersect(lastKeys)
        val ticks = common.sumOf { key -> (last.getLong(key) - first.getLong(key)).coerceAtLeast(0) }
        val hz = before.getLong("clk_tck")
        assertEquals("Termux clock tick rate changed", hz, after.getLong("clk_tck"))
        assertTrue("Termux process counters were unavailable", common.isNotEmpty() && hz > 0)
        val observedMs = (after.getLong("midpoint_ns") - before.getLong("midpoint_ns")) / 1e6
        val cpuMs = ticks * 1000.0 / hz
        return JSONObject().put("uid", before.getInt("uid")).put("clk_tck", hz)
            .put("observation_ms", observedMs).put("common_process_count", common.size)
            .put("new_process_count", lastKeys.minus(firstKeys).size).put("ended_process_count", firstKeys.minus(lastKeys).size)
            .put("common_process_cpu_ticks", ticks).put("common_process_cpu_ms", cpuMs)
            .put("common_process_cpu_one_core_percent", cpuMs * 100.0 / observedMs)
            .put("memory_before", JSONObject(before.toString()).apply { remove("processes") })
            .put("memory_after", JSONObject(after.toString()).apply { remove("processes") })
            .put("bounds", "Whole Termux UID including existing jobs; CPU matches PID plus process start time, excludes the sampler and short-lived processes. Sampling surrounds the app CPU interval; Termux counters include native command dispatch overhead. PSS is used only when readable for every observed process, otherwise total RSS is reported and may double-count shared pages.")
    }
    @Test fun measureDrawnEchoAndIdleCostInSingleAndSplitPane() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("termuxPaneBenchmark") == "true")
        if (BuildConfig.TERMUX_SSH_PANE) assertTrue(TermuxWorkspaceSshSetup.isPaired(context))
        val host = ActivityScenario.launch<ohi.andre.consolelauncher.help.OfflineHelpActivity>(
            Intent(context, ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java).putExtra("topic", "termux-workspace"))
        host.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val targets = mutableListOf<String>()
        val measurements = JSONArray()
        // Raw terminal mode suppresses line echo: only the remote program can emit FRAME_<character>.
        val python = "import os,tty;tty.setraw(0);os.write(1,b'BENCH_READY');\nwhile True:\n c=os.read(0,1)\n if not c: break\n os.write(1,b'\\x1b[2J\\x1b[HFRAME_'+c)"
        val shellCommand = "python -u -c '" + python.replace("'", "'\\''") + "'"
        try {
            repeat(2) { index ->
                val target = command("if tmux has-session -t '=retui_workspace' 2>/dev/null; then tmux new-window -d -t retui_workspace: -P -F '#{window_id}' -n \"\$1\" \"\$2\"; else tmux new-session -d -s retui_workspace -P -F '#{window_id}' -n \"\$1\" \"\$2\"; fi", "$fixture-$index", shellCommand)
                assertTrue(target.matches(Regex("@[0-9]+"))); targets.add(target)
            }
            for (layout in listOf(TermuxWorkspaceState.Layout.SINGLE, TermuxWorkspaceState.Layout.LEFT_RIGHT)) {
                val state = TermuxWorkspaceState().apply { select(0, targets[0]); select(1, targets[1]); this.layout = layout }
                prefs.edit().putString("selection", state.encode()).remove("draft").commit()
                ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                    await(scenario) { activity -> state.visibleTiles.all { terminal(activity, it)?.text()?.isNotBlank() == true } }
                    Thread.sleep(1000)
                    val samples = JSONArray()
                    // Distinct characters prevent a previous terminal frame from satisfying the next sample.
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEachIndexed { index, character ->
                        val drawn = CountDownLatch(1)
                        var elapsed = 0.0
                        scenario.onActivity { activity ->
                            val grid = (field(activity, "grids") as Array<*>)[0] as TerminalGridView
                            val started = System.nanoTime()
                            lateinit var observer: ViewTreeObserver.OnDrawListener
                            observer = ViewTreeObserver.OnDrawListener {
                                if (terminal(activity, 0)?.text()?.contains("FRAME_$character") == true && drawn.count > 0) {
                                    // Posting from OnDraw runs once this frame's draw traversal has finished.
                                    grid.post { elapsed = (System.nanoTime() - started) / 1e6; grid.viewTreeObserver.removeOnDrawListener(observer); drawn.countDown() }
                                }
                            }
                            grid.viewTreeObserver.addOnDrawListener(observer)
                            TermuxWorkspaceActivity::class.java.getDeclaredMethod("directText", String::class.java).apply { isAccessible = true }.invoke(activity, character.toString())
                        }
                        assertTrue("No actual drawn remote echo for $character", drawn.await(5, TimeUnit.SECONDS))
                        if (index >= 2) samples.put(elapsed) // Two warmups; 24 measured inputs.
                        Thread.sleep(40)
                    }
                    // Native sampling runs outside the app's measured CPU interval.
                    val termuxBefore = termuxSnapshot()
                    val cpuStart = Process.getElapsedCpuTime()
                    val idleStart = SystemClock.elapsedRealtime()
                    Thread.sleep(10000)
                    val idleMillis = SystemClock.elapsedRealtime() - idleStart
                    val cpuMillis = Process.getElapsedCpuTime() - cpuStart
                    val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    val termuxAfter = termuxSnapshot()
                    measurements.put(JSONObject().put("layout", layout.name).put("drawn_echo_ms", samples)
                        .put("idle_ms", idleMillis).put("app_cpu_ms", cpuMillis).put("app_cpu_one_core_percent", cpuMillis * 100.0 / idleMillis)
                        .put("app_pss_kib", memory.totalPss).put("termux_uid_idle", termuxIdleCost(termuxBefore, termuxAfter)))
                }
            }
            File(context.filesDir, "termux-pane-benchmark.json").writeText(JSONObject()
                .put("ssh", BuildConfig.TERMUX_SSH_PANE).put("device", android.os.Build.MODEL)
                .put("api", android.os.Build.VERSION.SDK_INT).put("measurements", measurements).toString(2))
        } finally {
            try { if (targets.isNotEmpty()) command("for target in \"\$@\"; do tmux kill-window -t \"\$target\" 2>/dev/null || true; done", *targets.toTypedArray()) }
            finally {
                val edit = prefs.edit().clear()
                original.forEach { (key, value) -> when (value) {
                    is String -> edit.putString(key, value); is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
                    is Boolean -> edit.putBoolean(key, value); is Float -> edit.putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
                } }
                edit.commit()
                host.close()
            }
        }
    }
}
