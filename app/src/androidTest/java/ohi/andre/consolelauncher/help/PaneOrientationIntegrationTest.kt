package ohi.andre.consolelauncher.help

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.calculator.CalculatorActivity
import ohi.andre.consolelauncher.community.CommunityActivity
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Read-only pane checks: no store clearing, draft changes, or message submission. */
class PaneOrientationIntegrationTest {
    @get:org.junit.Rule val readerPreferences = ohi.andre.consolelauncher.help.HelpReaderPreferencesRule()
    @get:org.junit.Rule val windowPreferences = WindowPreferencesRule(fresh = false)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun idle() { instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(200) }
    private fun rotate(activity: Activity, orientation: Int): Activity {
        val configuration = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
        if (activity.resources.configuration.orientation == configuration) return activity
        val monitor = instrumentation.addMonitor(activity.javaClass.name, null, false)
        try {
            instrumentation.runOnMainSync { activity.requestedOrientation = orientation }
            return (instrumentation.waitForMonitorWithTimeout(monitor, 5000) as Activity).also { idle() }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    private fun bounds(view: View): Rect {
        val xy = IntArray(2); view.getLocationOnScreen(xy)
        return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }
    private fun check(type: Class<out Activity>, prefix: String, floatingLandscape: Boolean) {
        var activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun target(tag: String) = activity.window.decorView.findViewWithTag<View>("$prefix-$tag")
        try {
            activity = rotate(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); idle()
            for (floating in listOf(true, false)) {
                instrumentation.runOnMainSync {
                    if ((target("resize-corner").visibility == View.VISIBLE) != floating) target("window-toggle").performClick()
                    if (floating) target("resize-corner").performLongClick()
                }
                idle()
                val portrait = bounds(target("resize-corner").parent as View)
                activity = rotate(activity, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                instrumentation.runOnMainSync {
                    val panel = target("resize-corner").parent as View
                    assertEquals(if (floatingLandscape) View.VISIBLE else View.GONE, target("window-toggle").visibility)
                    assertEquals(if (floatingLandscape) View.VISIBLE else View.GONE, target("window-reset").visibility)
                    assertEquals(if (floatingLandscape && floating) View.VISIBLE else View.GONE, target("resize-corner").visibility)
                    if (!floatingLandscape || !floating) assertEquals(bounds(panel.parent as View), bounds(panel))
                }
                if (floating) {
                    val screenshot = instrumentation.uiAutomation.takeScreenshot()
                    try {
                        File(activity.getExternalFilesDir(null), "$prefix-landscape.png").outputStream().use {
                            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally { screenshot.recycle() }
                }
                activity = rotate(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                instrumentation.runOnMainSync {
                    assertEquals("Portrait restores chosen mode", if (floating) View.VISIBLE else View.GONE, target("resize-corner").visibility)
                    if (!floatingLandscape) assertEquals("Portrait restores previous size and position", portrait, bounds(target("resize-corner").parent as View))
                }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() }; idle() }
    }
    @Test fun calculatorLandscapeIsExpandedAndPortraitChoiceIsPreserved() = check(CalculatorActivity::class.java, "calculator", false)
    @Test fun helpLandscapeIsExpandedAndPortraitChoiceIsPreserved() = check(OfflineHelpActivity::class.java, "help", false)
    @Test fun ircCanStillFloatInLandscape() = check(CommunityActivity::class.java, "community", true)
}
