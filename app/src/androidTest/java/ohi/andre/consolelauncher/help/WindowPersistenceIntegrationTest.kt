package ohi.andre.consolelauncher.help

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.calculator.CalculatorActivity
import ohi.andre.consolelauncher.community.CommunityActivity
import org.junit.Assert.*
import org.junit.Test

/** Changes only window geometry, restored by the rule; never edits drafts or clears content stores. */
class WindowPersistenceIntegrationTest {
    @get:org.junit.Rule val readerPreferences = ohi.andre.consolelauncher.help.HelpReaderPreferencesRule()
    @get:org.junit.Rule val windowPreferences = WindowPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun idle() { instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(200) }
    private fun rotate(activity: Activity, orientation: Int): Activity {
        val desired = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
        if (activity.resources.configuration.orientation == desired) return activity
        val monitor = instrumentation.addMonitor(activity.javaClass.name, null, false)
        try {
            instrumentation.runOnMainSync { activity.requestedOrientation = orientation }
            return (instrumentation.waitForMonitorWithTimeout(monitor, 5000) as Activity).also { idle() }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    private fun open(type: Class<out Activity>, orientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) =
        rotate(instrumentation.startActivitySync(Intent(instrumentation.targetContext, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), orientation).also { idle() }
    private fun close(activity: Activity) { instrumentation.runOnMainSync { activity.finish() }; idle() }
    private fun bounds(view: View): Rect {
        val xy = IntArray(2); view.getLocationOnScreen(xy)
        return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }
    private fun check(type: Class<out Activity>, prefix: String, other: Class<out Activity>, otherPrefix: String) {
        var activity: Activity? = null
        fun target(tag: String) = activity!!.window.decorView.findViewWithTag<View>("$prefix-$tag")
        fun panel() = target("resize-corner").parent as View
        try {
            activity = open(type)
            assertEquals("First use starts expanded", View.GONE, target("resize-corner").visibility)
            instrumentation.runOnMainSync { target("window-toggle").performClick(); target("resize-top-left").performLongClick() }
            idle()
            val floating = bounds(panel())
            val first = activity
            close(activity!!); activity = null
            activity = open(other)
            assertEquals("Another pane starts independently", View.GONE, activity!!.window.decorView.findViewWithTag<View>("$otherPrefix-resize-corner").visibility)
            close(activity!!); activity = null
            activity = open(type)
            assertNotSame("A new activity reads durable preferences", first, activity)
            assertEquals("Reopen restores floating mode", View.VISIBLE, target("resize-corner").visibility)
            assertEquals("Reopen restores size and position", floating, bounds(panel()))
            instrumentation.runOnMainSync { target("window-toggle").performClick() }
            close(activity!!); activity = null
            activity = open(type)
            assertEquals("Expanded choice also persists", View.GONE, target("resize-corner").visibility)
            instrumentation.runOnMainSync { target("window-toggle").performClick() }
            idle()
            assertEquals("Expanding never loses the floating rectangle", floating, bounds(panel()))
            if (prefix != "community") {
                activity = rotate(activity!!, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                assertEquals(View.GONE, target("resize-corner").visibility)
                close(activity!!); activity = null
                activity = open(type, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                assertEquals("Reopening in landscape stays expanded", View.GONE, target("resize-corner").visibility)
                activity = rotate(activity!!, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                assertEquals("Landscape closure preserves portrait floating choice", View.VISIBLE, target("resize-corner").visibility)
                assertEquals(floating, bounds(panel()))
            }
        } finally { activity?.let { close(it) } }
    }
    @Test fun calculatorRemembersModeAndRectangleAfterClosing() = check(CalculatorActivity::class.java, "calculator", OfflineHelpActivity::class.java, "help")
    @Test fun helpRemembersModeAndRectangleAfterClosing() = check(OfflineHelpActivity::class.java, "help", CommunityActivity::class.java, "community")
    @Test fun ircRemembersModeAndRectangleAfterClosing() = check(CommunityActivity::class.java, "community", CalculatorActivity::class.java, "calculator")
}
