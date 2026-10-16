package ohi.andre.consolelauncher.managers.termux

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Requires a fresh, bootstrapped official Termux on a disposable emulator, with external commands allowed. */
@RunWith(AndroidJUnit4::class)
class TermuxFreshInstallIntegrationTest {
    @Test(timeout = 210000) fun enablingPaneInstallsMissingPackagesAndPairsRealSsh() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("termuxFreshInstall") == "true")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.startsWith("sdk_"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith("debug"))
        assertFalse("Use a genuinely unpaired launcher", TermuxWorkspaceSshSetup.isPaired(context))
        fun field(activity: TermuxWorkspaceActivity, name: String) =
            TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val enable = descendants(activity.window.decorView).filterIsInstance<TextView>()
                    .singleOrNull { it.text == context.getString(R.string.workspace_ssh_enable) }
                assertNotNull("Fresh setup must show the enable action", enable)
                assertTrue(enable!!.performClick())
            }
            val until = SystemClock.uptimeMillis() + 180000
            var connected = false
            var status = ""
            while (SystemClock.uptimeMillis() < until && !connected) {
                scenario.onActivity {
                    connected = (field(it, "ssh") as? TermuxWorkspaceSshConnection)?.connected == true
                    status = descendants(it.window.decorView).filterIsInstance<TextView>().joinToString(" | ") { it.text }
                }
                if (!connected) SystemClock.sleep(300)
            }
            File(context.getExternalFilesDir(null), "termux-fresh-install.txt").writeText("connected=$connected\npaired=${TermuxWorkspaceSshSetup.isPaired(context)}\nstatus=$status\n")
            assertTrue("Real install/pairing failed: $status", connected)
            assertTrue(TermuxWorkspaceSshSetup.isPaired(context))
            scenario.onActivity { assertEquals(View.GONE, (field(it, "helperPanel") as View).visibility) }
        }
    }
}
