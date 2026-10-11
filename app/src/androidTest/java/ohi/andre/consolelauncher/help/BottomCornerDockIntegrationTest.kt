package ohi.andre.consolelauncher.help

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.view.descendants
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.calculator.CalculatorActivity
import ohi.andre.consolelauncher.community.CommunityActivity
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Checks current panes without clearing stores, changing drafts, or submitting any input. */
class BottomCornerDockIntegrationTest {
    @get:org.junit.Rule val readerPreferences = ohi.andre.consolelauncher.help.HelpReaderPreferencesRule()
    @get:org.junit.Rule val windowPreferences = WindowPreferencesRule(fresh = false)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun idle() { instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(200) }
    private fun bounds(view: View): Rect {
        val xy = IntArray(2); view.getLocationOnScreen(xy)
        return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }
    private fun check(type: Class<out Activity>, prefix: String, borderId: Int) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, type)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            idle()
            for (expanded in listOf(false, true)) {
                instrumentation.runOnMainSync {
                    val corner = activity.window.decorView.findViewWithTag<View>("$prefix-resize-corner")
                    if ((corner.visibility == View.GONE) != expanded)
                        activity.window.decorView.findViewWithTag<View>("$prefix-window-toggle").performClick()
                    activity.findViewById<ScrollView>(R.id.calculator_keypad_scroll)?.fullScroll(View.FOCUS_DOWN)
                }
                idle()
                instrumentation.runOnMainSync {
                    val dock = activity.window.decorView.findViewWithTag<View>("$prefix-bottom-dock")
                    val border = activity.findViewById<View>(borderId)
                    assertEquals("No full-width bottom reservation", 0, border.paddingBottom)
                    assertTrue("Dock reaches the pane bottom", bounds(border).bottom - bounds(dock).bottom <= Tuils.dpToPx(activity, 2))
                    val params = dock.layoutParams as ViewGroup.MarginLayoutParams
                    if (expanded) {
                        assertEquals(0, params.marginStart); assertEquals(0, params.marginEnd)
                    } else {
                        val left = activity.window.decorView.findViewWithTag<View>("$prefix-resize-bottom-left")
                        val right = activity.window.decorView.findViewWithTag<View>("$prefix-resize-corner")
                        assertTrue("Entire dock avoids left corner", bounds(dock).left >= bounds(left).right)
                        assertTrue("Entire dock avoids right corner", bounds(dock).right <= bounds(right).left)
                        assertEquals(Tuils.dpToPx(activity, 48), left.width)
                        assertEquals(Tuils.dpToPx(activity, 48), left.height)
                    }
                    if (dock is BottomCornerRow) dock.descendants.filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }.forEach {
                        assertTrue("Keys don't shrink below 48dp", it.width >= Tuils.dpToPx(activity, 48))
                        assertTrue(it.height >= Tuils.dpToPx(activity, 48))
                    }
                    (dock as? HorizontalScrollView)?.fullScroll(View.FOCUS_RIGHT)
                }
                idle()
                instrumentation.runOnMainSync {
                    val dock = activity.window.decorView.findViewWithTag<View>("$prefix-bottom-dock")
                    if (dock is BottomCornerRow) {
                        val last = dock.descendants.filterIsInstance<Button>().last { it.visibility == View.VISIBLE }
                        val visible = Rect()
                        assertTrue("Last key is reachable by scrolling", last.getGlobalVisibleRect(visible))
                        assertEquals(last.width, visible.width())
                    }
                    (dock as? HorizontalScrollView)?.fullScroll(View.FOCUS_LEFT)
                }
                idle()
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                try {
                    File(activity.getExternalFilesDir(null), "$prefix-corners-${if (expanded) "expanded" else "floating"}.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { screenshot.recycle() }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }; idle()
        }
    }
    @Test fun calculatorOnlyReservesLowestRowCorners() = check(CalculatorActivity::class.java, "calculator", R.id.calculator_window_border)
    @Test fun helpOnlyReservesLowestRowCorners() = check(OfflineHelpActivity::class.java, "help", R.id.offline_help_panel)
    @Test fun communityOnlyReservesChipRowCorners() = check(CommunityActivity::class.java, "community", R.id.community_panel)
}
