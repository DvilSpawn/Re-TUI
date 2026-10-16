package ohi.andre.consolelauncher.managers.termux

import android.content.*
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.UIManager
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

/** Two-phase, opt-in external force-stop proof. Seed signals readiness, host kills app, verify restores it. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceProcessDeathTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val proof get() = File(context.cacheDir, "termux-process-death-proof.json")
    private fun field(activity: TermuxWorkspaceActivity, name: String): Any? =
        TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    @Suppress("UNCHECKED_CAST")
    private fun terminal(activity: TermuxWorkspaceActivity, tile: Int = 0) = (field(activity, "terminals") as Array<TermuxWorkspaceTerminal?>)[tile]
    private fun command(script: String, vararg args: String): String {
        val label = "process-proof-${UUID.randomUUID()}"
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
                TermuxBridgeManager.createResultPendingIntent(context, label, null), arrayOf("-c", script, "retui-process-proof", *args))
            assertTrue("Native fixture command timed out", done.await(20, TimeUnit.SECONDS))
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
            Thread.sleep(40)
        }
        fail("Persisted pane did not restore its live shell")
    }
    private fun restore(saved: JSONObject) {
        val edit = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE).edit().clear()
        val original = saved.getJSONObject("prefs")
        original.keys().forEach { key -> when (val value = original.get(key)) {
            is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
        } }
        edit.commit()
    }
    @Test fun liveShellAndSelectionSurviveExternalProcessDeath() {
        val phase = InstrumentationRegistry.getArguments().getString("termuxProcessDeathPhase")
        assumeTrue(phase == "seed" || phase == "verify")
        if (phase == "seed") {
            assertFalse("An unfinished process-death fixture requires verification first", proof.exists())
            val host = instrumentation.startActivitySync(Intent(context, ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("topic", "termux-workspace"))
            instrumentation.runOnMainSync { host.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
            val marker = "PROCESS_SURVIVAL_${UUID.randomUUID().toString().replace("-", "")}"
            val target = command("tmux new-window -d -t retui_workspace: -P -F '#{window_id}' -n \"\$1\" \"printf '%s\\n' '\$2'; exec bash --noprofile --norc\"", "process-proof-${UUID.randomUUID()}", marker)
            assertTrue(target.matches(Regex("@[0-9]+")))
            val pid = command("tmux display-message -p -t \"\$1\" '#{pane_pid}'", target)
            val saved = JSONObject().put("target", target).put("panePid", pid).put("marker", marker).put("prefs", JSONObject(prefs.all))
            proof.writeText(saved.toString())
            prefs.edit().putString("selection", TermuxWorkspaceState().apply { select(0, target) }.encode()).remove("draft").commit()
            try {
                ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                    await(scenario) { terminal(it)?.text()?.contains(marker) == true }
                    proof.writeText(saved.put("stage", "READY_FOR_KILL").toString())
                    Thread.sleep(90000) // Host must force-stop this isolated package while its real pane is visible.
                    fail("Host did not force-stop the seed phase")
                }
            } finally {
                command("tmux kill-window -t \"\$1\" 2>/dev/null || true", target)
                restore(saved); proof.delete()
                instrumentation.runOnMainSync { host.finish() }
            }
        } else {
            assertTrue("Missing seed proof", proof.isFile)
            val saved = JSONObject(proof.readText())
            val target = saved.getString("target")
            assertTrue(target.matches(Regex("@[0-9]+")))
            try {
            ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
                scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                try {
                    await(scenario) { (field(it, "selection") as TermuxWorkspaceState).focusedTarget == target && terminal(it)?.text()?.contains(saved.getString("marker")) == true }
                    assertEquals("A replacement shell was created", saved.getString("panePid"), command("tmux display-message -p -t \"\$1\" '#{pane_pid}'", target))
                    scenario.onActivity { activity ->
                        TermuxWorkspaceActivity::class.java.getDeclaredMethod("directText", String::class.java).apply { isAccessible = true }
                            .invoke(activity, "printf '%s%s\\n' 'AFTER_PROCESS_' 'DEATH_OK'")
                        TermuxWorkspaceActivity::class.java.getDeclaredMethod("enter").apply { isAccessible = true }.invoke(activity)
                    }
                    await(scenario) { terminal(it)?.text()?.contains("AFTER_PROCESS_DEATH_OK") == true }
                    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    File(context.filesDir, "termux-process-death-restored.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                } finally {
                    command("tmux kill-window -t \"\$1\" 2>/dev/null || true", target)
                }
            }
            } finally { restore(saved); proof.delete() }
        }
    }
    /** Same application ID/data: seed with the legacy APK, install -r SSH APK, then verify. */
    @Test fun bridgeWorkspaceSurvivesInPlaceSshUpgrade() {
        val phase = InstrumentationRegistry.getArguments().getString("termuxUpgradePhase")
        assumeTrue(phase == "seed" || phase == "verify")
        assertTrue("Upgrade proof only owns the isolated test package", context.packageName.endsWith(".termuxsshdebug"))
        val upgrade = File(context.cacheDir, "termux-upgrade-proof.json")
        fun invoke(activity: TermuxWorkspaceActivity, name: String, vararg args: Any?) =
            TermuxWorkspaceActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
                .apply { isAccessible = true }.invoke(activity, *args)
        fun find(view: android.view.View, text: String): android.view.View? {
            if (view is android.widget.TextView && view.text.toString() == text) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), text)?.let { return it }
            return null
        }
        fun shot(name: String) {
            instrumentation.waitForIdleSync(); SystemClock.sleep(500)
            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.filesDir, name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        fun windowIds() = command("tmux list-windows -t '=retui_workspace' -F '#{window_id}' | sort")
        fun cleanup(saved: JSONObject) {
            val targets = saved.getJSONArray("windows")
            for (index in 0 until targets.length()) command("tmux kill-window -t \"\$1\" 2>/dev/null || true", targets.getJSONObject(index).getString("target"))
            TermuxWorkspaceLauncherManager.remove(context, saved.getString("launcherId"))
        }
        if (phase == "seed") {
            assertFalse("Seed must run the real old bridge", ohi.andre.consolelauncher.BuildConfig.TERMUX_SSH_PANE)
            assertFalse("An unfinished upgrade fixture needs verification first", upgrade.exists())
            val host = instrumentation.startActivitySync(Intent(context, ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("topic", "termux-workspace"))
            val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
            val id = "upgrade_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val saved = JSONObject().put("prefs", JSONObject(prefs.all)).put("launcherId", id)
                .put("launcherCommand", "printf '%s\\n' UPGRADE_LAUNCHER_SAVED").put("windows", org.json.JSONArray())
            try {
                for (index in 0..2) {
                    val marker = "UPGRADE_${id}_$index"
                    val target = command("if tmux has-session -t '=retui_workspace' 2>/dev/null; then tmux new-window -d -t '=retui_workspace' -P -F '#{window_id}' -n \"\$1\" \"printf '%s\\n' '\$2'; exec bash --noprofile --norc\"; else tmux new-session -d -s retui_workspace -P -F '#{window_id}' -n \"\$1\" \"printf '%s\\n' '\$2'; exec bash --noprofile --norc\"; fi", "$id-$index", marker)
                    assertTrue(target.matches(Regex("@[0-9]+")))
                    saved.getJSONArray("windows").put(JSONObject().put("target", target).put("marker", marker)
                        .put("panePid", command("tmux display-message -p -t \"\$1\" '#{pane_pid}'", target)))
                }
                val windows = saved.getJSONArray("windows")
                val selection = TermuxWorkspaceState().apply {
                    layout = TermuxWorkspaceState.Layout.LEFT_RIGHT
                    select(0, windows.getJSONObject(0).getString("target")); select(1, windows.getJSONObject(1).getString("target")); focus(1)
                    addPage(windows.getJSONObject(2).getString("target")); movePage(-1)
                }.encode()
                saved.put("selection", selection).put("windowIds", windowIds())
                assertTrue(TermuxWorkspaceLauncherManager.save(context, id, saved.getString("launcherCommand")))
                prefs.edit().putString("selection", selection).remove("draft").commit()
                ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
                    scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                    await(scenario) { activity -> (field(activity, "selection") as TermuxWorkspaceState).encode() == selection &&
                        (0..1).all { tile -> terminal(activity, tile)?.text()?.contains(windows.getJSONObject(tile).getString("marker")) == true } }
                    TermuxWorkspaceSshSetup.clearPairing(context)
                    shot("termux-upgrade-legacy.png")
                }
                saved.put("stage", "READY_FOR_UPDATE"); upgrade.writeText(saved.toString())
            } catch (error: Throwable) { cleanup(saved); restore(saved); upgrade.delete(); throw error }
            finally { instrumentation.runOnMainSync { host.finish() } }
        } else {
            assertTrue("Verify must run SSH", ohi.andre.consolelauncher.BuildConfig.TERMUX_SSH_PANE)
            assertTrue("Missing legacy upgrade fixture", upgrade.isFile)
            val saved = JSONObject(upgrade.readText()); val windows = saved.getJSONArray("windows")
            val scenario = ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java))
            try {
                scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                val enable = context.getString(ohi.andre.consolelauncher.R.string.workspace_ssh_enable)
                await(scenario) { find(it.window.decorView, enable) != null }
                scenario.onActivity {
                    assertEquals(saved.getString("selection"), (field(it, "selection") as TermuxWorkspaceState).encode())
                    assertEquals(saved.getString("launcherCommand"), TermuxWorkspaceLauncherManager.resolve(context, saved.getString("launcherId"))?.command)
                }
                assertEquals("Upgrade must not create a shell before Enable", saved.getString("windowIds"), windowIds())
                shot("termux-upgrade-enable.png")
                scenario.onActivity { assertTrue(find(it.window.decorView, enable)!!.performClick()) }
                await(scenario) { activity -> (field(activity, "ssh") as? TermuxWorkspaceSshConnection)?.connected == true &&
                    (field(activity, "selection") as TermuxWorkspaceState).encode() == saved.getString("selection") &&
                    (0..1).all { tile -> terminal(activity, tile)?.text()?.contains(windows.getJSONObject(tile).getString("marker")) == true } }
                for (index in 0 until windows.length()) {
                    val entry = windows.getJSONObject(index)
                    assertEquals("SSH must attach the original live job", entry.getString("panePid"), command("tmux display-message -p -t \"\$1\" '#{pane_pid}'", entry.getString("target")))
                }
                scenario.onActivity { invoke(it, "directText", "printf '%s%s\\n' 'UPGRADE_SSH_' 'INPUT_OK'"); invoke(it, "enter") }
                await(scenario) { terminal(it, 1)?.text()?.contains("UPGRADE_SSH_INPUT_OK") == true }
                scenario.onActivity { invoke(it, "movePage", 1) }
                await(scenario) { (field(it, "selection") as TermuxWorkspaceState).pageIndex == 1 && terminal(it)?.text()?.contains(windows.getJSONObject(2).getString("marker")) == true }
                scenario.onActivity { invoke(it, "movePage", -1) }
                await(scenario) { (field(it, "selection") as TermuxWorkspaceState).encode() == saved.getString("selection") && terminal(it, 1)?.text()?.contains("UPGRADE_SSH_INPUT_OK") == true }
                assertEquals("SSH must not create duplicate shells", saved.getString("windowIds"), windowIds())
                shot("termux-upgrade-restored.png")
                File(context.filesDir, "termux-upgrade-result.json").writeText(JSONObject(saved.toString()).apply {
                    remove("prefs"); put("passed", true); put("samePackage", context.packageName); put("guidedEnable", true); put("sameLivePids", true); put("noDuplicateShells", true)
                }.toString(2))
            } finally { try { cleanup(saved) } finally { scenario.close(); restore(saved); upgrade.delete() } }
        }
    }

}
