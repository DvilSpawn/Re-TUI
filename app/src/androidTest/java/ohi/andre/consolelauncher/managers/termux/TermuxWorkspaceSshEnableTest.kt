package ohi.andre.consolelauncher.managers.termux

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Revoke only this isolated test app's RUN_COMMAND permission before opting into this check. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceSshEnableTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun field(activity: TermuxWorkspaceActivity, name: String): Any? =
        TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun await(scenario: ActivityScenario<TermuxWorkspaceActivity>, message: String, predicate: (TermuxWorkspaceActivity) -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < end) {
            var ready = false
            scenario.onActivity { ready = predicate(it) }
            if (ready) return
            Thread.sleep(50)
        }
        fail(message)
    }
    private fun findText(view: View, text: String): View? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
            findText(view.getChildAt(index), text)?.let { return it }
        }
        return null
    }
    @Test fun enableContinuesAutomaticallyAfterActualAndroidPermissionGrant() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("termuxSshEnable") == "true")
        assertTrue("Run only against an isolated test package", context.packageName.endsWith(".termuxsshdebug"))
        assertFalse("Revoke the test app's permission before running", TermuxBridgeManager.hasRunCommandPermission(context))
        ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val enable = context.getString(R.string.workspace_ssh_enable)
            await(scenario, "Enable prompt did not appear") { findText(it.window.decorView, enable) != null }
            scenario.onActivity { assertTrue(findText(it.window.decorView, enable)!!.performClick()) }
            // Exercise the real system permission surface, rather than injecting a callback.
            val end = SystemClock.uptimeMillis() + 10000
            var granted = false
            while (SystemClock.uptimeMillis() < end && !granted) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                val button = listOf("com.google.android.permissioncontroller", "com.android.permissioncontroller")
                    .flatMap { pkg -> root?.findAccessibilityNodeInfosByViewId("$pkg:id/permission_allow_button").orEmpty() }
                    .firstOrNull()
                    ?: root?.findAccessibilityNodeInfosByText("Allow")?.firstOrNull { it.text?.toString() == "Allow" }
                if (button != null) granted = button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                if (!granted) Thread.sleep(100)
            }
            assertTrue("Actual Android permission dialog did not offer Allow", granted)
            await(scenario, "Grant did not automatically continue into the connected pane") {
                TermuxBridgeManager.hasRunCommandPermission(it) && (field(it, "ssh") as? TermuxWorkspaceSshConnection)?.connected == true &&
                    field(it, "sshConnecting") == false && field(it, "sshInstallAfterPermission") == false
            }
            scenario.onActivity { assertEquals(View.GONE, (field(it, "helperPanel") as View).visibility) }
        }
    }
}
