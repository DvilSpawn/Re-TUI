package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TermuxHelperUpdateIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun field(activity: TermuxWorkspaceActivity, name: String) =
        TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun call(activity: TermuxWorkspaceActivity, name: String, vararg args: Any?) =
        TermuxWorkspaceActivity::class.java.declaredMethods.single { it.name == name && it.parameterTypes.size == args.size }
            .apply { isAccessible = true }.invoke(activity, *args)
    private fun action(activity: TermuxWorkspaceActivity, label: Int) {
        val actions = field(activity, "helperActions").get(activity) as LinearLayout
        val button = (0 until actions.childCount).map { actions.getChildAt(it) as TextView }
            .single { it.text.toString() == activity.getString(label) }
        assertTrue(button.minimumHeight >= (48 * activity.resources.displayMetrics.density).toInt())
        button.performClick()
    }

    @Test fun promptLaterRotationAndFailureKeepWorkspaceStateAndNeverInstallSilently() {
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        try {
            ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
                var saved = ""
                scenario.onActivity {
                    call(it, "dismissHelperPrompt")
                    field(it, "resultLabel").set(it, null)
                    (field(it, "clients").get(it) as Array<*>).indices.forEach { index -> call(it, "closeTile", index) }
                    val state = field(it, "selection").get(it) as TermuxWorkspaceState
                    state.select(0, "@700"); state.select(1, "@701"); state.focus(1)
                    state.layout = TermuxWorkspaceState.Layout.LEFT_RIGHT
                    saved = state.encode()
                    call(it, "directText", ":retained update draft")
                    call(it, "command", "update")
                    assertEquals(View.VISIBLE, (field(it, "helperPanel").get(it) as View).visibility)
                    assertFalse(field(it, "helperUpdating").getBoolean(it))
                    action(it, R.string.workspace_helper_later)
                    assertEquals(View.GONE, (field(it, "helperPanel").get(it) as View).visibility)
                }
                scenario.recreate()
                scenario.onActivity {
                    field(it, "resultLabel").set(it, null)
                    (field(it, "clients").get(it) as Array<*>).indices.forEach { index -> call(it, "closeTile", index) }
                    assertTrue(field(it, "helperDeferred").getBoolean(it))
                    assertEquals(saved, (field(it, "selection").get(it) as TermuxWorkspaceState).encode())
                    assertEquals("retained update draft", field(it, "localDraft").get(it))
                    call(it, "command", "update")
                }
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(300) // Let the compositor submit the prompt, not the previous tiles.
                scenario.onActivity {
                    assertEquals(View.VISIBLE, (field(it, "helperPanel").get(it) as View).visibility)
                    assertEquals(View.GONE, (field(it, "tiles").get(it) as View).visibility)
                }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    try { java.io.File(context.getExternalFilesDir(null), "helper-update-prompt.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    } } finally { bitmap.recycle() }
                }
                scenario.onActivity {
                    // Fixture callback only: never click Update on the user's real helper.
                    field(it, "resultLabel").set(it, "helper-fixture")
                    field(it, "helperUpdating").setBoolean(it, true)
                    val receiver = field(it, "receiver").get(it) as BroadcastReceiver
                    receiver.onReceive(it, Intent(UIManager.ACTION_TERMUX_RESULT)
                        .putExtra(UIManager.EXTRA_TERMUX_RESULT_PATH, "stale-fixture")
                        .putExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT, "__RETUI_UPDATE_OK__ 0.4.4")
                        .putExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, 0))
                    assertTrue(field(it, "helperUpdating").getBoolean(it))
                    receiver.onReceive(it, Intent(UIManager.ACTION_TERMUX_RESULT)
                        .putExtra(UIManager.EXTRA_TERMUX_RESULT_PATH, "helper-fixture")
                        .putExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT, "__RETUI_UPDATE_OK__ 0.4.4")
                        .putExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, 1))
                    assertFalse(field(it, "helperUpdating").getBoolean(it))
                    assertEquals(it.getString(R.string.workspace_helper_update_failed), (field(it, "helperBody").get(it) as TextView).text.toString())
                    assertEquals(saved, (field(it, "selection").get(it) as TermuxWorkspaceState).encode())
                    assertEquals("retained update draft", field(it, "localDraft").get(it))
                }
            }
        } finally {
            val editor = prefs.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            assertTrue(editor.commit()); assertEquals(original, prefs.all)
        }
    }

    @Test fun optInRealTermuxStdinUpdatePreservesPrivateJobsAndOriginalHelper() {
        assumeTrue("Explicit live Termux fixture required", InstrumentationRegistry.getArguments().getString("helperUpdateLive") == "true")
        val context = instrumentation.targetContext
        val status = TermuxBridgeManager.status(context)
        assumeTrue("Existing Termux command access required", status.termuxInstalled && status.runCommandGranted)
        val helper = context.assets.open("termux-helper/retui").use { it.readBytes() }
        val update = context.assets.open("termux-helper/update.sh").bufferedReader().use { it.readText() }
        val server = "retui-update-" + UUID.randomUUID().toString().replace("-", "")
        val label = "helper-device-fixture:" + UUID.randomUUID()
        val done = CountDownLatch(1)
        var output = ""; var diagnostic = ""; var exitCode = Int.MIN_VALUE
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) != label) return
                output = intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty()
                diagnostic = intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR).orEmpty().take(512)
                exitCode = intent.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, Int.MIN_VALUE)
                done.countDown()
            }
        }
        val manager = LocalBroadcastManager.getInstance(context)
        manager.registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT))
        // A separate prefix and tmux server, never the installed helper or user's sessions.
        val script = """
            set -eu
            real_prefix='/data/data/com.termux/files/usr'
            export PATH="${'$'}real_prefix/bin:${'$'}PATH"
            fixture="${'$'}(mktemp -d "${'$'}real_prefix/tmp/retui-helper-test.XXXXXX")"
            cleanup_fixture() {
                "${'$'}real_prefix/bin/tmux" -L '$server' kill-server 2>/dev/null || true
                case "${'$'}fixture" in "${'$'}real_prefix"/tmp/retui-helper-test.*) [ ! -L "${'$'}fixture" ] && rm -r -- "${'$'}fixture";; esac
            }
            trap cleanup_fixture EXIT
            trap 'exit 1' HUP INT TERM
            mkdir -p "${'$'}fixture/bin" "${'$'}fixture/share/retui"
            cat > "${'$'}fixture/payload"
            helper_before="${'$'}(sha256sum "${'$'}real_prefix/bin/retui" 2>/dev/null || true)"
            jobs_before="${'$'}("${'$'}real_prefix/bin/tmux" list-panes -a -F '#{window_id}:#{pane_pid}' 2>/dev/null || true)"
            printf '#!/bin/sh\nprintf "0.3.4\\n"\n' > "${'$'}fixture/bin/retui"
            chmod 755 "${'$'}fixture/bin/retui"
            printf '0.3.4\n' > "${'$'}fixture/share/retui/version"
            "${'$'}real_prefix/bin/tmux" -L '$server' new-session -d -s helper-fixture 'sleep 120'
            "${'$'}real_prefix/bin/tmux" -L '$server' new-window -d -t helper-fixture 'sleep 120'
            before="${'$'}("${'$'}real_prefix/bin/tmux" -L '$server' list-panes -a -F '#{window_id}:#{pane_pid}')"
            printf '%s\n' '__RETUI_FIXTURE_STAGE__ update'
            PREFIX="${'$'}fixture" sh -c "${'$'}1" retui-update '${TermuxHelperUpdate.VERSION}' '${TermuxHelperUpdate.checksum(helper)}' < "${'$'}fixture/payload"
            printf '%s\n' '__RETUI_FIXTURE_STAGE__ private-jobs'
            [ "${'$'}before" = "${'$'}("${'$'}real_prefix/bin/tmux" -L '$server' list-panes -a -F '#{window_id}:#{pane_pid}')" ]
            printf '%s\n' '__RETUI_FIXTURE_STAGE__ original-helper'
            [ "${'$'}helper_before" = "${'$'}(sha256sum "${'$'}real_prefix/bin/retui" 2>/dev/null || true)" ]
            printf '%s\n' '__RETUI_FIXTURE_STAGE__ original-jobs'
            [ "${'$'}jobs_before" = "${'$'}("${'$'}real_prefix/bin/tmux" list-panes -a -F '#{window_id}:#{pane_pid}' 2>/dev/null || true)" ]
            printf '%s\n' '__RETUI_DEVICE_UPDATE_FIXTURE_OK__'
        """.trimIndent()
        try {
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH, TermuxBridgeManager.TERMUX_HOME,
                TermuxBridgeManager.createResultPendingIntent(context, label, null),
                arrayOf("-c", script, "helper-test", update), TermuxHelperUpdate.payload(helper))
            assertTrue("Termux fixture did not return", done.await(30, TimeUnit.SECONDS))
            assertEquals("Private Termux fixture failed: ${output.take(512)} $diagnostic", 0, exitCode)
            assertEquals("__RETUI_FIXTURE_STAGE__ update\n__RETUI_UPDATE_OK__ 0.4.4\n__RETUI_FIXTURE_STAGE__ private-jobs\n__RETUI_FIXTURE_STAGE__ original-helper\n__RETUI_FIXTURE_STAGE__ original-jobs\n__RETUI_DEVICE_UPDATE_FIXTURE_OK__", output.trim())
        } finally { manager.unregisterReceiver(receiver) }
    }
}
