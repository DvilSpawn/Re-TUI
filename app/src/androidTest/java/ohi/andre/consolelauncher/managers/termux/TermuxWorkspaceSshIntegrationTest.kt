package ohi.andre.consolelauncher.managers.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.UIManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt in with -e termuxSshDevice true; uses only uniquely named disposable windows. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceSshIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun runCommand(args: Array<String?>): String {
        val label = "retui-ssh-test-" + UUID.randomUUID()
        val latch = CountDownLatch(1)
        val result = AtomicReference<Intent>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH) == label) {
                    result.set(intent); latch.countDown()
                }
            }
        }
        val broadcasts = LocalBroadcastManager.getInstance(context)
        broadcasts.registerReceiver(receiver, IntentFilter(UIManager.ACTION_TERMUX_RESULT))
        try {
            TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH,
                TermuxBridgeManager.TERMUX_HOME, TermuxBridgeManager.createResultPendingIntent(context, label, null), args)
            assertTrue("Termux command timed out", latch.await(20, TimeUnit.SECONDS))
            val intent = result.get()
            assertEquals(intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR).orEmpty() + "\n" + intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty(), 0,
                intent.getIntExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE, -1))
            return intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT).orEmpty()
        } finally { broadcasts.unregisterReceiver(receiver) }
    }

    @Test fun pairedSshConnectsAndIndependentTilesStreamThenDetachWithoutKillingShells() = verifyWorkspace("retui_workspace")
    @Test fun homeSshConnectsAndIndependentTilesStreamThenDetachWithoutKillingShells() = verifyWorkspace("retui_home")

    private fun verifyWorkspace(workspace: String) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("termuxSshDevice") == "true")
        // Vivo freezes background instrumentation despite framework isFrozen=false.
        // A real visible screen keeps native command and SSH callbacks scheduled.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val host = instrumentation.startActivitySync(Intent(context, ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java)
            .putExtra("topic", "termux-workspace").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun stage(value: String) { java.io.File(context.cacheDir, "ssh-integration-stage.txt").writeText(value) }
        stage("checking Termux")
        assertTrue(TermuxBridgeManager.isTermuxInstalled(context))
        assertTrue(TermuxBridgeManager.hasRunCommandPermission(context))
        stage("preparing setup")
        val info = TermuxWorkspaceSshSetup.parse(context,
            runCommand(TermuxWorkspaceSshSetup.commandArguments(context, false)))
        stage("starting daemon")
        TermuxBridgeManager.startRunCommand(context, TermuxBridgeManager.TERMUX_SH,
            TermuxBridgeManager.TERMUX_HOME, null, TermuxWorkspaceSshSetup.daemonArguments(context))
        stage("constructing connection")
        val ready = CountDownLatch(1)
        val error = AtomicReference<String>()
        val connection = TermuxWorkspaceSshConnection(info, object : TermuxWorkspaceSshConnection.Listener {
            override fun onReady(windows: List<Pair<String, String>>) { ready.countDown() }
            override fun onWindows(windows: List<Pair<String, String>>) = Unit
            override fun onTargetGone(target: String) = Unit
            override fun onError(message: String) { error.set(message); ready.countDown() }
        }, workspace)
        val created = mutableListOf<String>()
        try {
            stage("connecting")
            connection.connect()
            assertTrue("SSH readiness timed out", ready.await(20, TimeUnit.SECONDS))
            assertNull(error.get())
            assertTrue(connection.connected)
            for (tile in listOf(0, 7)) {
                stage("creating tile $tile")
                val made = CountDownLatch(1)
                val target = AtomicReference<String>()
                connection.newWindow("ssh-proof-${UUID.randomUUID()}", null) { target.set(it); made.countDown() }
                assertTrue("Window creation timed out: ${error.get()}", made.await(10, TimeUnit.SECONDS))
                created.add(target.get())
                val output = CountDownLatch(1)
                val suffix = "TILE_${tile}_${UUID.randomUUID()}"
                val marker = "SSH_PROOF_$suffix"
                val seen = StringBuilder()
                stage("attaching tile $tile")
                connection.attach(tile, target.get(), 60 + tile * 10, 20 + tile * 4, { bytes ->
                    synchronized(seen) { seen.append(String(bytes, Charsets.UTF_8)); if (seen.contains(marker)) output.countDown() }
                }, {})
                stage("writing tile $tile")
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                while (!connection.write(tile, "printf '%s%s\\n' 'SSH_PROOF_' '$suffix'\r".toByteArray()) &&
                    android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
                assertTrue("Tile $tile did not stream terminal output: ${error.get()}", output.await(10, TimeUnit.SECONDS))
            }
            connection.detach(0); connection.detach(7)
            val windows = runCommand(arrayOf("-c", "tmux list-windows -t '=$workspace' -F '#{window_id}'"))
            for (target in created) assertTrue("Detach killed $target", windows.lineSequence().contains(target))
        } finally {
            stage("closing connection")
            connection.close()
            if (created.isNotEmpty()) {
                // IDs come from tmux and are validated before becoming a command argument.
                assertTrue(created.all { it.matches(Regex("@[0-9]+")) })
                runCommand(arrayOf("-c", "for target in \"\$@\"; do tmux kill-window -t \"\$target\" 2>/dev/null || true; done", "retui-ssh-cleanup", *created.toTypedArray()))
            }
            instrumentation.runOnMainSync { host.finish() }
        }
    }
}
