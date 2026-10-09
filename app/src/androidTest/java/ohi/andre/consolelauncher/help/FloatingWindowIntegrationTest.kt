package ohi.andre.consolelauncher.help

import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.calculator.CalculatorActivity
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class FloatingWindowIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun drag(view: View, dx: Float, dy: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        listOf(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0),
            MotionEvent.obtain(now, now + 100, MotionEvent.ACTION_MOVE, 10f + dx, 10f + dy, 0),
            MotionEvent.obtain(now, now + 120, MotionEvent.ACTION_UP, 10f + dx, 10f + dy, 0)).forEach {
            view.dispatchTouchEvent(it); it.recycle()
        }
    }
    private fun tapCenter(view: View) {
        val location = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(location) }
        val now = android.os.SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
            val event = MotionEvent.obtain(now, now + index * 50L, action,
                location[0] + view.width / 2f, location[1] + view.height / 2f, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun headerCentersDoNotHitResizeCorners() = checkHeaderCenters(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    @Test fun landscapeHeaderCentersDoNotHitResizeCorners() = checkHeaderCenters(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    private fun checkHeaderCenters(orientation: Int) {
        var activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, OfflineHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        val monitor = instrumentation.addMonitor(OfflineHelpActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync { activity.requestedOrientation = orientation }
            val recreated = instrumentation.waitForMonitorWithTimeout(monitor, 1500) as? OfflineHelpActivity
            if (recreated != null) activity = recreated
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(400)
            assertEquals("Rendered orientation", orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                activity.window.decorView.width > activity.window.decorView.height)
            val title = activity.findViewById<View>(R.id.offline_help_header)
            val reset = activity.window.decorView.findViewWithTag<View>("help-window-reset")
            val toggle = activity.window.decorView.findViewWithTag<View>("help-window-toggle")
            assertEquals("help-window-toggle", toggle.tag)
            assertEquals(activity.getString(R.string.offline_help_reset_window), reset.contentDescription)
            instrumentation.runOnMainSync {
                assertNull("No middle-right width control", activity.window.decorView.findViewWithTag<View>("help-resize-width"))
                assertNull("No bottom height control", activity.window.decorView.findViewWithTag<View>("help-resize-height"))
                listOf(toggle, reset).forEach {
                    val action = it as android.widget.TextView
                    assertEquals("Icon-only window action", "", action.text.toString())
                    assertNotNull(action.compoundDrawables[0])
                    assertTrue(action.width >= ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 48))
                    assertTrue(action.height >= ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 48))
                }
                fun bounds(view: View): android.graphics.Rect {
                    val xy = IntArray(2)
                    view.getLocationOnScreen(xy)
                    return android.graphics.Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
                }
                listOf("top-left", "top-right", "bottom-left", "corner").forEach { kind ->
                    val handle = activity.window.decorView.findViewWithTag<View>("help-resize-$kind")
                    listOf(title, toggle, reset).forEach { control ->
                        assertFalse("Header targets must not overlap $kind", android.graphics.Rect.intersects(bounds(control), bounds(handle)))
                    }
                }
            }
            val corner = activity.window.decorView.findViewWithTag<View>("help-resize-top-right")
            val panel = corner.parent as View
            fun geometry(): List<Int> {
                var result = emptyList<Int>()
                instrumentation.runOnMainSync {
                    val p = panel.layoutParams as FrameLayout.LayoutParams
                    result = listOf(p.leftMargin, p.topMargin, p.width, p.height)
                }
                return result
            }
            val initial = geometry()
            tapCenter(reset)
            assertEquals("Reset center must not resize", initial, geometry())
            tapCenter(corner)
            assertNotEquals("Corner center still resizes", initial, geometry())
            tapCenter(reset)
            assertEquals("Reset center restores geometry", initial, geometry())
            tapCenter(toggle)
            assertEquals("Max center hides corners", View.GONE, corner.visibility)
            tapCenter(toggle)
            assertEquals("Float center restores corners", View.VISIBLE, corner.visibility)
            assertEquals(initial, geometry())
            listOf("top-left", "top-right", "bottom-left", "corner").forEach { kind ->
                val handle = activity.window.decorView.findViewWithTag<View>("help-resize-$kind")
                val location = IntArray(2)
                instrumentation.runOnMainSync { handle.getLocationOnScreen(location) }
                val x = location[0] + handle.width / 2f
                val y = location[1] + handle.height / 2f
                val dx = if (kind.endsWith("left")) 60f else -60f
                val dy = if (kind.startsWith("top")) 60f else -60f
                val now = android.os.SystemClock.uptimeMillis()
                listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                    val event = MotionEvent.obtain(now, now + index * 100L, action,
                        x + if (index == 0) 0f else dx, y + if (index == 0) 0f else dy, 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.sendPointerSync(event); event.recycle()
                }
                instrumentation.waitForIdleSync()
                assertNotEquals("$kind screen drag resizes", initial, geometry())
                tapCenter(reset)
                assertEquals("$kind center reset restores", initial, geometry())
            }
            android.os.SystemClock.sleep(300)
            java.io.File(instrumentation.targetContext.cacheDir, "help-hit-target-$orientation.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
    @Test fun eachCornerResizesBothAxesAndKeepsOppositeCornerAnchored() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, OfflineHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        try {
            instrumentation.waitForIdleSync()
            listOf("top-left", "top-right", "bottom-left", "corner").forEach { corner ->
                instrumentation.runOnMainSync {
                    activity.window.decorView.findViewWithTag<View>("help-window-reset").performClick()
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val handle = activity.window.decorView.findViewWithTag<View>("help-resize-$corner")
                    val panel = handle.parent as View
                    val before = panel.layoutParams as FrameLayout.LayoutParams
                    val x = before.leftMargin; val y = before.topMargin
                    val w = before.width; val h = before.height
                    val left = corner.endsWith("left"); val top = corner.startsWith("top")
                    drag(handle, if (left) -12f else 12f, if (top) -12f else 12f)
                    val after = panel.layoutParams as FrameLayout.LayoutParams
                    assertEquals("$corner width", w + 12, after.width)
                    assertEquals("$corner height", h + 12, after.height)
                    assertEquals("$corner opposite horizontal anchor", if (left) x - 12 else x, after.leftMargin)
                    assertEquals("$corner opposite vertical anchor", if (top) y - 12 else y, after.topMargin)
                    drag(handle, if (left) -10000f else 10000f, if (top) -10000f else 10000f)
                    val bounded = panel.layoutParams as FrameLayout.LayoutParams
                    val host = panel.parent as View
                    assertTrue(bounded.leftMargin >= 0 && bounded.topMargin >= 0)
                    assertTrue(bounded.leftMargin + bounded.width <= host.width)
                    assertTrue(bounded.topMargin + bounded.height <= host.height)
                }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun calculatorHasSymmetricOuterInsetAndModalTranslucentBackdrop() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CalculatorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val content = activity.findViewById<View>(R.id.calculator_overlay)
                val panel = content.parent as View
                val host = panel.parent as View
                assertEquals("Floating width", (host.width * .9f).roundToInt(), panel.width)
                assertEquals("Floating height", (host.height * .9f).roundToInt(), panel.height)
                assertEquals("Five-percent left inset", (host.width * .05f).roundToInt(), panel.left)
                assertEquals("Five-percent top inset", (host.height * .05f).roundToInt(), panel.top)
                assertTrue("Symmetric horizontal inset", kotlin.math.abs(panel.left - (host.width - panel.right)) <= 2)
                assertTrue("Symmetric vertical inset", kotlin.math.abs(panel.top - (host.height - panel.bottom)) <= 2)
                val border = activity.findViewById<View>(R.id.calculator_window_border)
                assertEquals("Frame retains the workspace horizontal inset", content.paddingLeft, border.left)
                assertEquals(content.paddingRight, content.width - border.right)
                val frameMargins = border.layoutParams as android.view.ViewGroup.MarginLayoutParams
                assertEquals(content.paddingTop + frameMargins.topMargin, border.top)
                assertEquals(content.paddingBottom + frameMargins.bottomMargin, content.height - border.bottom)
                val attributes = activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
                try { assertTrue(attributes.getBoolean(0, false)) } finally { attributes.recycle() }
                val field = activity.findViewById<android.widget.EditText>(R.id.calculator_expression)
                val draft = field.text.toString()
                val screen = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
                drag(screen, 0f, 0f)
                assertFalse("Outside does not dismiss", activity.isFinishing)
                assertEquals("Outside retains draft", draft, field.text.toString())
                assertTrue("Outside consumes touch", screen.isClickable)
            }
        } finally { instrumentation.runOnMainSync { activity.finishAndRemoveTask() } }
    }
}
