package ohi.andre.consolelauncher.calculator

import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.descendants
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Test

class CalculatorHelpIntegrationTest {
    @get:org.junit.Rule val readerPreferences = ohi.andre.consolelauncher.help.HelpReaderPreferencesRule()
    @get:org.junit.Rule val windowPreferences = ohi.andre.consolelauncher.help.WindowPreferencesRule(fresh = false)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun withCalculator(onMain: Boolean = true, check: (CalculatorActivity) -> Unit) {
        ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.loadCommons(context)
        val savedEngine = ohi.andre.consolelauncher.managers.settings.LauncherSettings.get(ohi.andre.consolelauncher.managers.xml.options.Behavior.qalculate)
        val prefs = CalculatorEngine.prefs(context)
        val saved = prefs.all
        var activity: CalculatorActivity? = null
        try {
            prefs.edit().putString("terminal_transcript", "calc> 2+2\n4\n")
                .putString("terminal_ans", "4").putString("terminal_draft", "ans + 7")
                .putInt("terminal_cursor", 3).putInt("terminal_selection_end", 3).commit()
            activity = instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
            instrumentation.waitForIdleSync()
            if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                val monitor = instrumentation.addMonitor(CalculatorActivity::class.java.name, null, false)
                try {
                    instrumentation.runOnMainSync { activity!!.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CalculatorActivity
                    instrumentation.waitForIdleSync()
                } finally { instrumentation.removeMonitor(monitor) }
            }
            if (onMain) instrumentation.runOnMainSync { check(activity!!) } else check(activity!!)
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
            val editor = prefs.edit().clear()
            saved.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
            } }
            editor.commit()
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Behavior.qalculate, savedEngine)
        }
    }

    @Test fun lettersUseStaggeredQwertyRows() = withCalculator(onMain = false) { activity ->
        instrumentation.runOnMainSync { activity.findViewById<View>(R.id.calculator_letters_tab).performClick() }
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(150)
        java.io.File(context.cacheDir, "calculator-qwerty.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        instrumentation.runOnMainSync {
            val grid = activity.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
            val keys = grid.descendants.filterIsInstance<TextView>().filter { it.text.isNotEmpty() && it.visibility == View.VISIBLE }.toList()
            fun position(key: View) = IntArray(2).also { key.getLocationOnScreen(it) }
            val rows = keys.groupBy { position(it)[1] }.toSortedMap().values.map { row -> row.sortedBy { position(it)[0] } }
            assertEquals("Three letter rows and one editing row", 4, rows.size)
            assertEquals("QWERTYUIOP", rows[0].joinToString("") { it.text.toString() })
            assertEquals("ASDFGHJKL", rows[1].joinToString("") { it.text.toString() })
            assertEquals("ZXCVBNM⌫", rows[2].joinToString("") { it.text.toString() })
            assertEquals(listOf("_", "SPACE", "↵"), rows[3].map { it.text.toString() })
            assertEquals("Delete immediately follows M", rows[2][6].right, rows[2][7].left)
            assertTrue("Home row is staggered", rows[1].first().left > rows[0].first().left)
            assertTrue("Bottom letter row is staggered", rows[2].first().left > rows[1].first().left)
            assertTrue("Space key is wider than a letter", rows[3][1].width > rows[0][0].width)
            keys.forEach { key ->
                val visible = android.graphics.Rect()
                assertTrue("Every QWERTY key is visible", key.getGlobalVisibleRect(visible))
                assertEquals("No QWERTY key is clipped horizontally", key.width, visible.width())
                assertEquals("No QWERTY key ${key.text} is clipped vertically", key.height, visible.height())
            }
            val input = activity.findViewById<EditText>(R.id.calculator_expression)
            input.setText("")
            "QWERTY".forEach { letter -> keys.first { it.text.toString() == letter.toString() }.performClick() }
            assertEquals("qwerty", input.text.toString())
            keys.first { it.text == "SPACE" }.performClick()
            assertEquals("qwerty ", input.text.toString())
            keys.first { it.text == "⌫" }.performClick()
            assertEquals("qwerty", input.text.toString())
        }
    }

    @Test fun keypadModesKeepTerminalAndDockHeight() = withCalculator(onMain = false) { activity ->
        var expectedGridHeight = 0
        var expectedDockHeight = 0
        var expectedTerminalHeight = 0
        instrumentation.runOnMainSync {
            expectedGridHeight = activity.findViewById<View>(R.id.calculator_symbols).height
            expectedDockHeight = activity.findViewById<View>(R.id.calculator_keypad_dock).height
            expectedTerminalHeight = activity.findViewById<View>(R.id.calculator_transcript_scroll).height
            assertTrue("Numeric keypad is laid out", expectedGridHeight > 0)
            assertEquals("Five numeric rows use their exact pixel budget",
                ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 48) * 5, expectedGridHeight)
        }
        listOf(R.id.calculator_letters_tab, R.id.calculator_functions_tab, R.id.calculator_numbers_tab).forEach { tab ->
            instrumentation.runOnMainSync { activity.findViewById<View>(tab).performClick() }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(150)
            instrumentation.runOnMainSync {
                assertEquals("Key grid height must remain stable", expectedGridHeight,
                    activity.findViewById<View>(R.id.calculator_symbols).height)
                assertEquals("Keypad dock height must remain stable", expectedDockHeight,
                    activity.findViewById<View>(R.id.calculator_keypad_dock).height)
                assertEquals("Terminal height must remain stable", expectedTerminalHeight,
                    activity.findViewById<View>(R.id.calculator_transcript_scroll).height)
            }
        }
    }

    @Test fun firstMeasureAndSettledLayoutKeepExactDockBudget() = withCalculator { activity ->
        val panel = activity.findViewById<View>(R.id.calculator_overlay)
        val width = panel.width
        val height = minOf(panel.height, ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 360))
        val viewport = activity.findViewById<View>(R.id.calculator_keypad_scroll)
        val ids = listOf(R.id.calculator_symbols, R.id.calculator_keypad_dock,
            R.id.calculator_transcript_scroll, R.id.calculator_keypad_scroll)
        fun measure(): List<Int> {
            panel.forceLayout()
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            return ids.map { activity.findViewById<View>(it).measuredHeight }
        }
        viewport.layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        val first = measure()
        assertEquals("Numeric grid has its full pixel budget", ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 48) * 5, first[0])
        repeat(3) {
            panel.layout(panel.left, panel.top, panel.left + width, panel.top + height)
            assertEquals("First allocation must already equal the settled allocation", first, measure())
        }
        listOf(R.id.calculator_letters_tab, R.id.calculator_functions_tab, R.id.calculator_numbers_tab).forEach { tab ->
            activity.findViewById<View>(tab).performClick()
            assertEquals("Mode switches retain the first measure budget", first, measure())
        }
    }

    @Test fun landscapeRestorationKeepsLivePromptVisible() = withCalculator(onMain = false) { activity ->
        val monitor = instrumentation.addMonitor(CalculatorActivity::class.java.name, null, false)
        var landscape: CalculatorActivity? = null
        try {
            instrumentation.runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            landscape = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as? CalculatorActivity
            assertNotNull("Landscape calculator recreation", landscape)
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(400)
            val stableHeights = listOf(R.id.calculator_symbols, R.id.calculator_keypad_dock,
                R.id.calculator_transcript_scroll, R.id.calculator_keypad_scroll)
            var expectedHeights = emptyList<Int>()
            instrumentation.runOnMainSync {
                expectedHeights = stableHeights.map { landscape!!.findViewById<View>(it).height }
                assertEquals("Landscape numeric rows use the same exact pixel budget",
                    ohi.andre.consolelauncher.tuils.Tuils.dpToPx(landscape!!, 48) * 5, expectedHeights.first())
            }
            instrumentation.runOnMainSync {
                val field = landscape!!.findViewById<EditText>(R.id.calculator_expression)
                val visible = android.graphics.Rect()
                assertTrue("Restored live prompt must be visible in landscape", field.getGlobalVisibleRect(visible))
                assertEquals("Live prompt must not be clipped by the keypad", field.height, visible.height())
                assertEquals("ans + 7", field.text.toString())
                assertEquals(3, field.selectionStart)
            }
            listOf(R.id.calculator_letters_tab, R.id.calculator_functions_tab, R.id.calculator_numbers_tab).forEach { tab ->
                instrumentation.runOnMainSync { landscape!!.findViewById<View>(tab).performClick() }
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(150)
                instrumentation.runOnMainSync {
                    assertEquals("Landscape grid, dock, terminal and viewport heights stay exact",
                        expectedHeights, stableHeights.map { landscape!!.findViewById<View>(it).height })
                    val field = landscape!!.findViewById<EditText>(R.id.calculator_expression)
                    val visible = android.graphics.Rect()
                    assertTrue("Live prompt remains visible in every landscape mode", field.getGlobalVisibleRect(visible))
                    assertEquals(field.height, visible.height())
                    assertEquals("ans + 7", field.text.toString())
                    assertEquals(3, field.selectionStart)
                    val dockScroll = landscape!!.findViewById<android.widget.ScrollView>(R.id.calculator_keypad_scroll)
                    assertTrue("Short landscape retains a usable keypad viewport", dockScroll.height >= ohi.andre.consolelauncher.tuils.Tuils.dpToPx(landscape!!, 48))
                    dockScroll.fullScroll(View.FOCUS_DOWN)
                }
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(350)
                instrumentation.runOnMainSync {
                    val evaluate = landscape!!.findViewById<View>(R.id.calculator_equals)
                    val visible = android.graphics.Rect()
                    assertTrue("Evaluate can be reached by scrolling the dock", evaluate.getGlobalVisibleRect(visible))
                    assertEquals(evaluate.height, visible.height())
                    landscape!!.findViewById<android.widget.ScrollView>(R.id.calculator_keypad_scroll).fullScroll(View.FOCUS_UP)
                }
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(350)
                val mode = when (tab) {
                    R.id.calculator_letters_tab -> "qwerty"
                    R.id.calculator_functions_tab -> "functions"
                    else -> "numeric"
                }
                java.io.File(context.cacheDir, "calculator-landscape-$mode.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { landscape?.finishAndRemoveTask() }
        }
    }

    @Test fun shortViewportWithLargeTextKeepsHistoryScrollableAndDraftVisible() = withCalculator(onMain = false) { activity ->
        instrumentation.runOnMainSync {
            val root = activity.findViewById<View>(R.id.calculator_overlay)
            root.layoutParams = root.layoutParams.apply { height = minOf(root.height, ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 360)) }
            val field = activity.findViewById<EditText>(R.id.calculator_expression)
            field.textSize = 48f
            activity.findViewById<TextView>(R.id.calculator_prompt).textSize = 48f
            activity.findViewById<TextView>(R.id.calculator_result).apply {
                textSize = 48f
                text = (1..60).joinToString("\n") { "calc> $it\n$it" }
            }
            val screen = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(screen,
                androidx.core.view.WindowInsetsCompat.Builder().setInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                    androidx.core.graphics.Insets.of(16, 24, 16, 48)).build())
        }
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(400)
        listOf(R.id.calculator_numbers_tab, R.id.calculator_letters_tab, R.id.calculator_functions_tab).forEach { tab ->
            instrumentation.runOnMainSync { activity.findViewById<View>(tab).performClick() }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(150)
            instrumentation.runOnMainSync {
                val field = activity.findViewById<EditText>(R.id.calculator_expression)
                val visible = android.graphics.Rect()
                assertTrue("Large draft remains visible with insets and long history", field.getGlobalVisibleRect(visible))
                assertEquals("Large draft is not vertically clipped", field.height, visible.height())
                assertEquals("ans + 7", field.text.toString())
                assertEquals(3, field.selectionStart)
                val terminal = activity.findViewById<android.widget.ScrollView>(R.id.calculator_transcript_scroll)
                assertTrue("History can scroll independently of the controls", terminal.scrollY > 0)
                terminal.scrollTo(0, 0)
                assertEquals("Earlier output remains reachable", 0, terminal.scrollY)
                terminal.scrollTo(0, terminal.getChildAt(0).height)
            }
        }
    }

    @Test fun helpOpensDedicatedReaderWithoutChangingCalculatorDraft() = withCalculator(onMain = false) { activity ->
        val monitor = instrumentation.addMonitor(ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java.name, null, false)
        var reader: ohi.andre.consolelauncher.help.OfflineHelpActivity? = null
        try {
            instrumentation.runOnMainSync {
                val field = activity.findViewById<EditText>(R.id.calculator_expression)
                field.setSelection(2, 5)
                activity.findViewById<View>(R.id.calculator_help_tab).performClick()
                assertEquals("ans + 7", field.text.toString())
                assertEquals("calc> 2+2\n4", activity.findViewById<TextView>(R.id.calculator_result).text.toString())
            }
            reader = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? ohi.andre.consolelauncher.help.OfflineHelpActivity
            assertNotNull("Help tab opens dedicated reader", reader)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val text = reader!!.findViewById<TextView>(R.id.offline_help_output).text.toString()
                assertTrue("Calculator Help starts with using the calculator, not reader mechanics",
                    text.startsWith(context.getString(R.string.offline_help_article_calculator_help_title) + "\n\nEnter an expression"))
                assertTrue(text.indexOf("5 + 5 → 10") < text.indexOf("Using this help"))
                assertEquals("A new calculator article opens at its beginning", 0,
                    reader!!.findViewById<android.widget.ScrollView>(R.id.offline_help_scroll).scrollY)
            }
            // Idle can precede the activity transition's final composited frame.
            android.os.SystemClock.sleep(500)
            java.io.File(context.cacheDir, "calculator-help-start.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            instrumentation.runOnMainSync {
                reader!!.findViewById<EditText>(R.id.offline_help_input).setText("9")
                reader!!.finish()
                val field = activity.findViewById<EditText>(R.id.calculator_expression)
                assertEquals("ans + 7", field.text.toString())
                assertEquals(2, field.selectionStart); assertEquals(5, field.selectionEnd)
            }
        } finally { instrumentation.removeMonitor(monitor); instrumentation.runOnMainSync { reader?.finish() } }
    }

    @Test fun pendingCalculationDoesNotConsumeReaderDraftOrOverwriteNewCalculatorDraft() = withCalculator(onMain = false) { activity ->
        val monitor = instrumentation.addMonitor(ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java.name, null, false)
        var reader: ohi.andre.consolelauncher.help.OfflineHelpActivity? = null
        try {
            instrumentation.runOnMainSync {
                val field = activity.findViewById<EditText>(R.id.calculator_expression)
                field.setText("2+2")
                activity.findViewById<View>(R.id.calculator_equals).performClick()
                assertFalse("Calculation is pending", activity.findViewById<View>(R.id.calculator_equals).isEnabled)
                field.setText("ans + 7"); field.setSelection(3)
                activity.findViewById<View>(R.id.calculator_help_tab).performClick()
            }
            reader = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? ohi.andre.consolelauncher.help.OfflineHelpActivity
            assertNotNull(reader)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { reader!!.findViewById<EditText>(R.id.offline_help_input).setText("9") }
            val deadline = android.os.SystemClock.uptimeMillis() + 15000
            var completed = false
            while (!completed && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { completed = activity.findViewById<View>(R.id.calculator_equals).isEnabled }
                android.os.SystemClock.sleep(50)
            }
            assertTrue("Calculation finishes while reader is open", completed)
            instrumentation.runOnMainSync {
                assertEquals("9", reader!!.findViewById<EditText>(R.id.offline_help_input).text.toString())
                assertEquals("ans + 7", activity.findViewById<EditText>(R.id.calculator_expression).text.toString())
                val history = activity.findViewById<TextView>(R.id.calculator_result).text.toString()
                assertEquals("Pending result appends the exact continuous displayed transcript",
                    "calc> 2+2\n4\ncalc> 2+2\n4", history)
                assertEquals("New calculator cursor is retained", 3,
                    activity.findViewById<EditText>(R.id.calculator_expression).selectionStart)
            }
        } finally { instrumentation.removeMonitor(monitor); instrumentation.runOnMainSync { reader?.finish() } }
    }

    @Test fun typedHelpOpensReaderWithoutCalculationHistory() = withCalculator(onMain = false) { activity ->
        val monitor = instrumentation.addMonitor(ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java.name, null, false)
        var reader: android.app.Activity? = null
        try {
            instrumentation.runOnMainSync {
                activity.findViewById<EditText>(R.id.calculator_expression).setText("help")
                activity.findViewById<View>(R.id.calculator_equals).performClick()
            }
            reader = instrumentation.waitForMonitorWithTimeout(monitor, 5000)
            assertNotNull(reader)
            instrumentation.runOnMainSync {
                assertEquals("", activity.findViewById<EditText>(R.id.calculator_expression).text.toString())
                assertEquals("calc> 2+2\n4", activity.findViewById<TextView>(R.id.calculator_result).text.toString())
            }
        } finally { instrumentation.removeMonitor(monitor); instrumentation.runOnMainSync { reader?.finish() } }
    }

    @Test fun terminalPromptIsUnframedAboveSeparateDock() = withCalculator { activity ->
        val field = activity.findViewById<EditText>(R.id.calculator_expression)
        val scroll = activity.findViewById<android.widget.ScrollView>(R.id.calculator_transcript_scroll)
        val output = activity.findViewById<TextView>(R.id.calculator_result)
        assertEquals("Terminal uses the embedded pane font", ohi.andre.consolelauncher.tuils.Tuils.getTypeface(activity), field.typeface)
        assertNull("Live prompt has no input border or underline", field.background)
        assertSame("Prompt and history share one scrollable terminal", scroll.getChildAt(0), field.parent.parent)
        assertSame(output.parent, field.parent.parent)
        assertTrue("Terminal retains useful space", scroll.height >= ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 120))
        val symbols = activity.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
        val keys = symbols.descendants.filterIsInstance<android.widget.Button>().filter { it.visibility == View.VISIBLE }.toList()
        assertEquals(20, keys.size)
        assertTrue(keys.all { it.height >= ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 48) })
        fun countEvaluate(view: View): Int = (if (view.id == R.id.calculator_equals) 1 else 0) +
            (if (view is android.view.ViewGroup) (0 until view.childCount).sumOf { countEvaluate(view.getChildAt(it)) } else 0)
        assertEquals("Only one evaluate key", 1, countEvaluate(activity.findViewById(R.id.calculator_overlay)))
    }

}
