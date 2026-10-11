package ohi.andre.consolelauncher.calculator

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.help.OfflineHelpActivity
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

/** Screen-coordinate acceptance; compile before deployment, run only on an authorized target. */
class CalculatorWindowIntegrationTest {
    @get:org.junit.Rule val readerPreferences = ohi.andre.consolelauncher.help.HelpReaderPreferencesRule()
    @get:org.junit.Rule val windowPreferences = ohi.andre.consolelauncher.help.WindowPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val corners = listOf("top-left", "top-right", "bottom-left", "corner")
    private fun idle() { instrumentation.waitForIdleSync(); SystemClock.sleep(200) }
    private fun target(activity: Activity, tag: String): View = activity.window.decorView.findViewWithTag(tag)
    private fun bounds(view: View): Rect {
        var result = Rect()
        instrumentation.runOnMainSync {
            val xy = IntArray(2); view.getLocationOnScreen(xy)
            result = Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
        }
        return result
    }
    private fun gesture(view: View, dx: Float = 0f, dy: Float = 0f) {
        val rect = bounds(view); val now = SystemClock.uptimeMillis()
        val actions = if (dx == 0f && dy == 0f) listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)
            else listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)
        actions.forEachIndexed { index, action ->
            val event = MotionEvent.obtain(now, now + index * 100L, action,
                rect.exactCenterX() + if (index == 0) 0f else dx,
                rect.exactCenterY() + if (index == 0) 0f else dy, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event); event.recycle()
        }
        idle()
    }
    private fun checkHeader(activity: Activity, prefix: String, stage: String) {
        val title = activity.findViewById<View>(if (prefix == "calculator") R.id.calculator_main_tab else R.id.offline_help_header)
        val reset = target(activity, "$prefix-window-reset")
        val toggle = target(activity, "$prefix-window-toggle")
        val actions = (if (prefix == "calculator") listOf(activity.findViewById<View>(R.id.calculator_help_tab), toggle, reset) else listOf(toggle, reset)).filter { it.visibility == View.VISIBLE }
        val row = (title.parent as View).parent as View
        val group = reset.parent as View
        assertEquals("Dedicated action group", "$prefix-header-actions", group.tag)
        assertEquals("Actions anchored at header end", bounds(row).right, bounds(group).right)
        assertEquals("Actions anchored at header top", bounds(row).top, bounds(group).top)
        actions.zipWithNext().forEach { (first, second) ->
            assertEquals("Uniform header gap", Tuils.dpToPx(activity, 10), bounds(second).left - bounds(first).right)
        }
        assertTrue("Title yields without overlap", bounds(title).right <= bounds(group).left)
        actions.forEach {
            assertEquals(Tuils.dpToPx(activity, 48), bounds(it).width())
            assertEquals(Tuils.dpToPx(activity, 48), bounds(it).height())
        }
        if (InstrumentationRegistry.getArguments().getString("headerScreens") == "true") {
            java.io.File(instrumentation.targetContext.cacheDir, "header-$prefix-${activity.window.decorView.width}-$stage.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    private fun geometry(panel: View): List<Int> {
        var result = emptyList<Int>()
        instrumentation.runOnMainSync {
            val p = panel.layoutParams as FrameLayout.LayoutParams
            result = listOf(p.leftMargin, p.topMargin, panel.width, panel.height)
        }
        return result
    }
    private fun check(className: Class<out Activity>, prefix: String, orientation: Int, narrow: Boolean = false) {
        var activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, className)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var monitor = instrumentation.addMonitor(className.name, null, false)
        try {
            instrumentation.runOnMainSync { activity.requestedOrientation = orientation }
            (instrumentation.waitForMonitorWithTimeout(monitor, 1500) as? Activity)?.let { activity = it }
            instrumentation.removeMonitor(monitor)
            idle()
            assertEquals(orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                activity.window.decorView.width > activity.window.decorView.height)
            val titleId = if (prefix == "calculator") R.id.calculator_main_tab else R.id.offline_help_header
            val title = activity.findViewById<View>(titleId)
            val toggle = target(activity, "$prefix-window-toggle")
            val reset = target(activity, "$prefix-window-reset")
            val panel = target(activity, "$prefix-resize-corner").parent as View
            val host = panel.parent as View
            if (narrow) {
                instrumentation.runOnMainSync {
                    host.layoutParams = host.layoutParams.apply { width = minOf(host.width, Tuils.dpToPx(activity, 320)) }
                }
                idle()
            }
            checkHeader(activity, prefix, "initial-${if (narrow) "narrow" else "full"}")
            var initial = geometry(panel)
            if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                assertEquals(listOf(0, 0, host.width, host.height), initial)
                assertEquals(View.GONE, toggle.visibility)
                assertEquals(View.GONE, reset.visibility)
                corners.forEach { assertEquals(View.GONE, target(activity, "$prefix-resize-$it").visibility) }
                monitor = instrumentation.addMonitor(className.name, null, false)
                instrumentation.runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as Activity
                idle()
                assertEquals("First launch remains expanded in portrait", View.GONE, target(activity, "$prefix-resize-corner").visibility)
                assertEquals(View.VISIBLE, target(activity, "$prefix-window-toggle").visibility)
                return
            }
            gesture(toggle)
            initial = geometry(panel)
            assertEquals(listOf((host.width * .05f).roundToInt(), (host.height * .05f).roundToInt(),
                (host.width * .9f).roundToInt(), (host.height * .9f).roundToInt()), initial)
            val actions = listOf(toggle, reset) + if (prefix == "calculator") listOf(activity.findViewById<View>(R.id.calculator_help_tab)) else emptyList()
            actions.forEach { action ->
                val rect = bounds(action)
                assertTrue(rect.width() >= Tuils.dpToPx(activity, 48))
                assertTrue(rect.height() >= Tuils.dpToPx(activity, 48))
                assertNotNull((action as TextView).compoundDrawables[0])
                corners.forEach { assertFalse("$prefix action overlaps $it", Rect.intersects(rect, bounds(target(activity, "$prefix-resize-$it")))) }
            }
            corners.forEach { assertFalse("Title overlaps $it", Rect.intersects(bounds(title), bounds(target(activity, "$prefix-resize-$it")))) }
            assertNull(activity.window.decorView.findViewWithTag<View>("$prefix-resize-width"))
            assertNull(activity.window.decorView.findViewWithTag<View>("$prefix-resize-height"))
            gesture(title, 12f, 12f)
            val moved = geometry(panel)
            assertEquals(initial[2], moved[2]); assertEquals(initial[3], moved[3])
            assertTrue(moved[0] > initial[0]); assertTrue(moved[1] > initial[1])
            gesture(reset); assertEquals(initial, geometry(panel))
            corners.forEach { kind ->
                val handle = target(activity, "$prefix-resize-$kind")
                val delta = Tuils.dpToPx(activity, 8).toFloat()
                gesture(handle, if (kind.endsWith("left")) -delta else delta,
                    if (kind.startsWith("top")) -delta else delta)
                checkHeader(activity, prefix, "resized-$kind")
                val resized = geometry(panel)
                assertTrue("$kind grows width", resized[2] > initial[2])
                assertTrue("$kind grows height", resized[3] > initial[3])
                assertEquals("Opposite x anchor", if (kind.endsWith("left")) initial[0] + initial[2] else initial[0],
                    if (kind.endsWith("left")) resized[0] + resized[2] else resized[0])
                assertEquals("Opposite y anchor", if (kind.startsWith("top")) initial[1] + initial[3] else initial[1],
                    if (kind.startsWith("top")) resized[1] + resized[3] else resized[1])
                gesture(toggle)
                corners.forEach { assertEquals(View.GONE, target(activity, "$prefix-resize-$it").visibility) }
                assertEquals(listOf(0, 0, host.width, host.height), geometry(panel))
                checkHeader(activity, prefix, "maximized-$kind")
                gesture(toggle); assertEquals(resized, geometry(panel))
                gesture(handle, if (kind.endsWith("left")) -10000f else 10000f,
                    if (kind.startsWith("top")) -10000f else 10000f)
                val bounded = geometry(panel)
                assertTrue(bounded[0] >= 0 && bounded[1] >= 0)
                assertTrue(bounded[0] + bounded[2] <= host.width)
                assertTrue(bounded[1] + bounded[3] <= host.height)
                gesture(reset); assertEquals(initial, geometry(panel))
            }
            // Recreation preserves a nondefault floating rectangle and then its maximized state.
            gesture(target(activity, "$prefix-resize-corner"), 10f, 10f)
            val retained = geometry(panel)
            val draftId = if (prefix == "calculator") R.id.calculator_expression else R.id.offline_help_input
            val input = activity.findViewById<EditText>(draftId)
            val draft = input.text.toString(); val start = input.selectionStart; val end = input.selectionEnd
            gesture(toggle)
            monitor = instrumentation.addMonitor(className.name, null, false)
            instrumentation.runOnMainSync { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as Activity
            idle()
            if (narrow) {
                val recreatedHost = (target(activity, "$prefix-resize-corner").parent as View).parent as View
                instrumentation.runOnMainSync {
                    recreatedHost.layoutParams = recreatedHost.layoutParams.apply { width = minOf(recreatedHost.width, Tuils.dpToPx(activity, 320)) }
                }
                idle()
            }
            checkHeader(activity, prefix, "recreated-maximized")
            assertEquals(View.GONE, target(activity, "$prefix-resize-corner").visibility)
            assertEquals(draft, activity.findViewById<EditText>(draftId).text.toString())
            assertEquals(start, activity.findViewById<EditText>(draftId).selectionStart)
            assertEquals(end, activity.findViewById<EditText>(draftId).selectionEnd)
            gesture(target(activity, "$prefix-window-toggle"))
            assertEquals(retained, geometry(target(activity, "$prefix-resize-corner").parent as View))
            gesture(target(activity, "$prefix-window-reset"))
            assertEquals(initial, geometry(target(activity, "$prefix-resize-corner").parent as View))
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
    @Test fun calculatorAndHelpKeepIndependentWindowGeometry() {
        val calculator = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CalculatorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
        var help: Activity? = null
        val monitor = instrumentation.addMonitor(OfflineHelpActivity::class.java.name, null, false)
        try {
            idle()
            gesture(target(calculator, "calculator-window-toggle"))
            gesture(target(calculator, "calculator-resize-corner"), 10f, 10f)
            val retained = geometry(target(calculator, "calculator-resize-corner").parent as View)
            val input = calculator.findViewById<EditText>(R.id.calculator_expression)
            val draft = input.text.toString(); val start = input.selectionStart; val end = input.selectionEnd
            val transcript = calculator.findViewById<TextView>(R.id.calculator_result).text.toString()
            gesture(calculator.findViewById(R.id.calculator_help_tab))
            val reader = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as Activity
            help = reader
            idle()
            gesture(target(reader, "help-window-toggle"))
            val initialHelp = geometry(target(reader, "help-resize-corner").parent as View)
            gesture(target(reader, "help-window-toggle"))
            gesture(target(reader, "help-window-reset"))
            assertEquals(initialHelp, geometry(target(reader, "help-resize-corner").parent as View))
            instrumentation.runOnMainSync { reader.finish() }
            idle()
            assertEquals(retained, geometry(target(calculator, "calculator-resize-corner").parent as View))
            assertEquals(draft, input.text.toString()); assertEquals(start, input.selectionStart); assertEquals(end, input.selectionEnd)
            assertEquals(transcript, calculator.findViewById<TextView>(R.id.calculator_result).text.toString())
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { help?.finish(); calculator.finish() }
        }
    }

    @Test fun calculatorPortraitScreenTargetsAndGeometry() = check(CalculatorActivity::class.java, "calculator", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    @Test fun calculatorLandscapeScreenTargetsAndGeometry() = check(CalculatorActivity::class.java, "calculator", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    @Test fun helpPortraitScreenTargetsAndGeometry() = check(OfflineHelpActivity::class.java, "help", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    @Test fun helpLandscapeScreenTargetsAndGeometry() = check(OfflineHelpActivity::class.java, "help", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    @Test fun narrowCalculatorLandscapeKeepsActionsOutsideCorners() = check(CalculatorActivity::class.java, "calculator", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, narrow = true)
    @Test fun narrowHelpLandscapeKeepsActionsOutsideCorners() = check(OfflineHelpActivity::class.java, "help", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, narrow = true)

    @Test fun shrinkingCalculatorStopsBeforeKeypadNeedsScrolling() {
        var activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CalculatorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
        val monitor = instrumentation.addMonitor(CalculatorActivity::class.java.name, null, false)
        try {
            if (activity.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                instrumentation.runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CalculatorActivity
            }
            idle()
            gesture(target(activity, "calculator-window-toggle"))
            instrumentation.runOnMainSync {
                val controller = activity.javaClass.getDeclaredField("windowPanel").apply { isAccessible = true }.get(activity)
                controller.javaClass.getDeclaredMethod("resize", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(controller, "corner", -10000, -10000)
            }
            idle()
            for (tab in listOf(R.id.calculator_numbers_tab, R.id.calculator_letters_tab, R.id.calculator_functions_tab)) {
                instrumentation.runOnMainSync { activity.findViewById<View>(tab).performClick() }
                idle()
                instrumentation.runOnMainSync {
                    val scroll = activity.findViewById<android.widget.ScrollView>(R.id.calculator_keypad_scroll)
                    assertTrue("Minimum height fits all keypad rows", scroll.height >= scroll.getChildAt(0).height)
                    val bottom = target(activity, "calculator-bottom-dock") as android.widget.HorizontalScrollView
                    assertTrue("Minimum width fits the bottom keys", bottom.width >= bottom.getChildAt(0).width)
                    assertTrue("Input retains useful room", activity.findViewById<View>(R.id.calculator_main_pane).height >= Tuils.dpToPx(activity, 120))
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
