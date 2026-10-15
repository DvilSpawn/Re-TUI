package ohi.andre.consolelauncher.navigation

import android.app.Dialog
import android.os.SystemClock
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.BackupRestoreTransaction
import ohi.andre.consolelauncher.managers.FileOperationCancellation
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Host runs each phase separately with force-stop between stage and verify. */
class SettingsRecoveryProcessTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun initialize() {
        check(context.packageName.endsWith(".palettedebug"))
        Tuils.init(context)
    }
    private fun hostPhase() {
        org.junit.Assume.assumeTrue("Host must sequence phases and force-stop the app",
            InstrumentationRegistry.getArguments().getString("hostProcessRecovery") == "true")
        initialize()
    }
    @Test fun stageFrameDraftForProcessKill() {
        hostPhase()
        val session = FrameManager.beginEdit(context)
        val expected = !session.applyToAll
        session.applyToAll = expected
        File(context.filesDir, "frame-process-expected").writeText(expected.toString())
        assertTrue(session.hasChanges())
    }
    @Test fun verifyFrameDraftAfterProcessKill() {
        hostPhase()
        val marker = File(context.filesDir, "frame-process-expected")
        val expected = marker.readText().toBoolean()
        val recovered = FrameManager.beginEdit(context)
        try {
            assertTrue(recovered.hasChanges())
            assertEquals(expected, recovered.applyToAll)
        } finally { recovered.discard(); marker.delete() }
    }
    @Test fun stageRestoreAndWaitForHostKill() {
        hostPhase()
        val target = File(Tuils.getFolder(), "restore-process-probe.txt")
        target.writeText("original before restore")
        val incoming = File(context.cacheDir, "restore-process-incoming.txt").apply { writeText("replacement during restore") }
        val journal = BackupManager.restoreJournalFile(context)
        check(journal.parentFile!!.isDirectory || journal.parentFile!!.mkdirs())
        val prefs = context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
        check(prefs.edit().putBoolean("restore-process-probe", false).commit())
        val serialize = BackupManager::class.java.getDeclaredMethod("serializePrefs", Map::class.java).apply { isAccessible = true }
        val snapshot = serialize.invoke(BackupManager, prefs.all) as String
        BackupRestoreTransaction.replace(mapOf(target to incoming), journal, mapOf("ui" to snapshot),
            { BackupManager.restoreRecoveryTargetAllowed(context, it) },
            { BackupManager.restorePreferenceSnapshots(context, it) },
            BackupManager::syncRestoreDirectory, checkpoint = { phase ->
                if (phase == "preferences") {
                    assertEquals("replacement during restore", target.readText())
                    assertTrue(prefs.getBoolean("restore-process-probe", false))
                    FileOutputStream(File(context.filesDir, "restore-process-ready")).use {
                        it.write("ready".toByteArray()); it.fd.sync()
                    }
                    check(CountDownLatch(1).await(90, TimeUnit.SECONDS)) { "Host did not stop the process" }
                }
            }, commit = { check(prefs.edit().putBoolean("restore-process-probe", true).commit()) })
    }
    @Test fun verifyStartupRecoveredInterruptedRestore() {
        hostPhase()
        val target = File(Tuils.getFolder(), "restore-process-probe.txt")
        assertEquals("original before restore", target.readText())
        assertFalse("Application startup must recover before this test starts", BackupManager.restoreJournalFile(context).exists())
        assertFalse(Tuils.getFolder().list().orEmpty().any { it.startsWith(".restore-old-") || it.startsWith(".restore-new-") })
        val prefs = context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
        assertFalse("Original preferences must recover too", prefs.getBoolean("restore-process-probe", true))
        check(prefs.edit().remove("restore-process-probe").commit())
        target.delete()
        File(context.filesDir, "restore-process-ready").delete()
        File(context.cacheDir, "restore-process-incoming.txt").delete()
    }
    @Test fun cancelButtonCancelsWorkerWithoutApplyingItsResult() {
        initialize()
        val activity = instrumentation.startActivitySync(ThemerActivity.launchIntent(context, ThemerActivity.SECTION_HOME)) as ThemerActivity
        val entered = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val applied = AtomicBoolean(false)
        try {
            val method = ThemerActivity::class.java.declaredMethods.first { it.name == "runFileOperation" }.apply { isAccessible = true }
            val work: () -> Unit = {
                entered.countDown()
                try {
                    while (true) { FileOperationCancellation.check(); Thread.sleep(10) }
                } finally { finished.set(true) }
            }
            val complete: ThemerActivity.(Result<Unit>) -> Unit = { applied.set(true) }
            instrumentation.runOnMainSync { method.invoke(activity, work, complete) }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val progressField = ThemerActivity::class.java.getDeclaredField("fileProgress").apply { isAccessible = true }
            instrumentation.runOnMainSync {
                val dialog = progressField.get(activity) as android.app.ProgressDialog
                val cancel = dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                assertTrue(cancel.isShown && cancel.isEnabled)
                cancel.performClick()
            }
            val deadline = SystemClock.uptimeMillis() + 3000
            while (!finished.get() && SystemClock.uptimeMillis() < deadline) Thread.sleep(20)
            assertTrue(finished.get())
            instrumentation.waitForIdleSync()
            assertFalse("Cancelled operations must not invoke their apply callback", applied.get())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
