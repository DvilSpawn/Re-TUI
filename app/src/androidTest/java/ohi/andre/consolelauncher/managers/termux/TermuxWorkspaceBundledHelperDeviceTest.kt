package ohi.andre.consolelauncher.managers.termux

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in installation through the same pinned updater offered by the existing pane. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceBundledHelperDeviceTest {
    @Test fun installExactBundledHelperThroughExistingPane() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicit helper installation required", InstrumentationRegistry.getArguments().getString("installBundledTermuxHelper") == "true")
        val context = instrumentation.targetContext
        assumeTrue("Debug launcher only", context.packageName.endsWith(".fetchdebug"))
        assertTrue(TermuxBridgeManager.status(context).runCommandGranted)
        ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
            val label = TermuxWorkspaceActivity::class.java.getDeclaredField("resultLabel").apply { isAccessible = true }
            val updating = TermuxWorkspaceActivity::class.java.getDeclaredField("helperUpdating").apply { isAccessible = true }
            val bootstrap = TermuxWorkspaceActivity::class.java.getDeclaredField("bootstrap").apply { isAccessible = true }
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                label.set(activity, null)
                TermuxWorkspaceActivity::class.java.getDeclaredMethod("updateHelper").apply { isAccessible = true }.invoke(activity)
                assertTrue(updating.getBoolean(activity))
            }
            val deadline = SystemClock.uptimeMillis() + 35000
            var installed = false
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    installed = !updating.getBoolean(it) && (bootstrap.get(it) as? TermuxWorkspaceFrame)?.helperVersion == TermuxHelperUpdate.VERSION
                }
                if (installed) break
                Thread.sleep(50)
            }
            assertTrue("Exact bundled helper update must finish and bootstrap the normal Termux bridge", installed)
        }
    }
}
