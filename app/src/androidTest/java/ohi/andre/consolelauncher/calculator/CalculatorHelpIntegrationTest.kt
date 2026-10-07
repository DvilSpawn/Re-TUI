package ohi.andre.consolelauncher.calculator

import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Test

class CalculatorHelpIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun withCalculator(onMain: Boolean = true, check: (CalculatorActivity) -> Unit) {
        val prefs = CalculatorEngine.prefs(context)
        val saved = prefs.all
        var activity: CalculatorActivity? = null
        try {
            prefs.edit().putString("terminal_transcript", "calc> 2+2\n4\n")
                .putString("terminal_ans", "4").putString("terminal_draft", "ans + 7")
                .putInt("terminal_cursor", 3).commit()
            activity = instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
            instrumentation.waitForIdleSync()
            if (onMain) instrumentation.runOnMainSync { check(activity!!) } else check(activity!!)
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            val editor = prefs.edit().clear()
            saved.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
            } }
            editor.commit()
        }
    }

    @Test fun lettersUseStaggeredQwertyRows() = withCalculator(onMain = false) { activity ->
        instrumentation.runOnMainSync { activity.findViewById<View>(R.id.calculator_letters_tab).performClick() }
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(150)
        instrumentation.runOnMainSync {
            val grid = activity.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
            val keys = (0 until grid.childCount).map { grid.getChildAt(it) as TextView }.filter { it.text.isNotEmpty() }
            val rows = keys.groupBy { it.top }.toSortedMap().values.map { row -> row.sortedBy { it.left } }
            assertEquals("Three letter rows and one editing row", 4, rows.size)
            assertEquals("QWERTYUIOP", rows[0].joinToString("") { it.text.toString() })
            assertEquals("ASDFGHJKL", rows[1].joinToString("") { it.text.toString() })
            assertEquals("ZXCVBNM", rows[2].joinToString("") { it.text.toString() })
            assertEquals(listOf("_", "SPACE", "⌫", "↵"), rows[3].map { it.text.toString() })
            assertTrue("Home row is staggered", rows[1].first().left > rows[0].first().left)
            assertTrue("Bottom letter row is staggered", rows[2].first().left > rows[1].first().left)
            assertTrue("Space key is wider than a letter", rows[3][1].width > rows[0][0].width)
            keys.forEach { key ->
                val visible = android.graphics.Rect()
                assertTrue("Every QWERTY key is visible", key.getGlobalVisibleRect(visible))
                assertEquals("No QWERTY key is clipped horizontally", key.width, visible.width())
                assertEquals("No QWERTY key is clipped vertically", key.height, visible.height())
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
        java.io.File(context.cacheDir, "calculator-qwerty.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
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

    @Test fun landscapeRestorationKeepsLivePromptVisible() = withCalculator(onMain = false) { activity ->
        val monitor = instrumentation.addMonitor(CalculatorActivity::class.java.name, null, false)
        var landscape: CalculatorActivity? = null
        try {
            instrumentation.runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            landscape = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as? CalculatorActivity
            assertNotNull("Landscape calculator recreation", landscape)
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(400)
            instrumentation.runOnMainSync {
                val field = landscape!!.findViewById<EditText>(R.id.calculator_expression)
                val visible = android.graphics.Rect()
                assertTrue("Restored live prompt must be visible in landscape", field.getGlobalVisibleRect(visible))
                assertEquals("Live prompt must not be clipped by the keypad", field.height, visible.height())
                assertEquals("ans + 7", field.text.toString())
                assertEquals(3, field.selectionStart)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { landscape?.finishAndRemoveTask() }
        }
    }

    @Test fun deviceBackReturnsTopicThenMenuThenCalculator() = withCalculator(onMain = false) { activity ->
        instrumentation.runOnMainSync {
            activity.findViewById<View>(R.id.calculator_help_tab).performClick()
            activity.findViewById<EditText>(R.id.calculator_expression).setText("1")
            activity.findViewById<View>(R.id.calculator_equals).performClick()
        }
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(300)
        java.io.File(context.cacheDir, "calculator-help-topic.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(300)
        java.io.File(context.cacheDir, "calculator-help-menu.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        instrumentation.runOnMainSync {
            assertFalse(activity.isFinishing)
            assertTrue(activity.findViewById<TextView>(R.id.calculator_result).text.contains("5 Keypad"))
        }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertFalse(activity.isFinishing)
            assertEquals("ans + 7", activity.findViewById<EditText>(R.id.calculator_expression).text.toString())
        }
    }

    @Test fun recreationPreservesHelpPageAndBothDrafts() = withCalculator(onMain = false) { activity ->
        val monitor = instrumentation.addMonitor(CalculatorActivity::class.java.name, null, false)
        var recreated: CalculatorActivity? = null
        try {
            instrumentation.runOnMainSync {
                val field = activity.findViewById<EditText>(R.id.calculator_expression)
                field.setSelection(2, 5)
                activity.findViewById<View>(R.id.calculator_help_tab).performClick()
                field.setText("3")
                activity.findViewById<View>(R.id.calculator_equals).performClick()
                field.setText("9")
                activity.recreate()
            }
            recreated = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as? CalculatorActivity
            assertNotNull("Recreated calculator", recreated)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val current = recreated!!
                val field = current.findViewById<EditText>(R.id.calculator_expression)
                val output = current.findViewById<TextView>(R.id.calculator_result)
                assertEquals("help> ", current.findViewById<TextView>(R.id.calculator_prompt).text.toString())
                assertTrue(output.text.contains("CONVERSIONS"))
                assertEquals("9", field.text.toString())
                current.onBackPressed()
                current.onBackPressed()
                assertEquals("ans + 7", field.text.toString())
                assertEquals(2, field.selectionStart)
                assertEquals(5, field.selectionEnd)
                assertEquals("calc> 2+2\n4\n", output.text.toString())
                assertEquals("4", CalculatorEngine.prefs(context).getString("terminal_ans", null))
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { recreated?.finishAndRemoveTask() }
        }
    }

    @Test fun plainTextChoicesNavigateWithoutChangingDraft() = withCalculator { activity ->
        val field = activity.findViewById<EditText>(R.id.calculator_expression)
        val output = activity.findViewById<TextView>(R.id.calculator_result)
        activity.findViewById<View>(R.id.calculator_help_tab).performClick()
        val text = output.text
        assertTrue("Help choices are tappable", text is android.text.Spanned)
        val spans = (text as android.text.Spanned).getSpans(0, text.length, android.text.style.ClickableSpan::class.java)
        assertEquals(6, spans.size)
        spans[2].onClick(output)
        assertTrue(output.text.contains("5 ft to miles"))
        assertEquals("help> ", activity.findViewById<TextView>(R.id.calculator_prompt).text.toString())
        val topic = output.text as android.text.Spanned
        assertEquals("Only navigation choices are links, never examples", 4, topic.getSpans(0, topic.length, android.text.style.ClickableSpan::class.java).size)
        topic.getSpans(0, topic.length, android.text.style.ClickableSpan::class.java).last().onClick(output)
        assertEquals("ans + 7", field.text.toString())
        assertEquals("calc> ", activity.findViewById<TextView>(R.id.calculator_prompt).text.toString())
    }

    @Test fun clearStaysClearedAcrossHelpAndKeepsAnswer() = withCalculator { activity ->
        val field = activity.findViewById<EditText>(R.id.calculator_expression)
        field.setText("clear")
        activity.findViewById<View>(R.id.calculator_equals).performClick()
        activity.findViewById<View>(R.id.calculator_help_tab).performClick()
        activity.onBackPressed()
        assertEquals("", activity.findViewById<TextView>(R.id.calculator_result).text.toString())
        assertEquals("", CalculatorEngine.prefs(context).getString("terminal_transcript", null))
        assertEquals("4", CalculatorEngine.prefs(context).getString("terminal_ans", null))
    }

    @Test fun typedHelpIsACommandNotCalculationHistory() = withCalculator { activity ->
        val field = activity.findViewById<EditText>(R.id.calculator_expression)
        val output = activity.findViewById<TextView>(R.id.calculator_result)
        val original = output.text.toString()
        field.setText("help")
        activity.findViewById<View>(R.id.calculator_equals).performClick()
        assertTrue(output.text.toString(), output.text.contains("5 Keypad"))
        assertEquals("", field.text.toString())
        field.setText("0")
        activity.findViewById<View>(R.id.calculator_equals).performClick()
        assertEquals(original, output.text.toString())
        assertEquals("", field.text.toString())
        assertEquals("4", CalculatorEngine.prefs(context).getString("terminal_ans", null))
    }

    @Test fun terminalPromptIsUnframedAboveSeparateDock() = withCalculator { activity ->
        val field = activity.findViewById<EditText>(R.id.calculator_expression)
        val scroll = activity.findViewById<android.widget.ScrollView>(R.id.calculator_transcript_scroll)
        val output = activity.findViewById<TextView>(R.id.calculator_result)
        assertEquals("Terminal is monospace even when controls use a proportional theme font", android.graphics.Typeface.MONOSPACE, field.typeface)
        assertNull("Live prompt has no input border or underline", field.background)
        assertSame("Prompt and history share one scrollable terminal", scroll.getChildAt(0), field.parent.parent)
        assertSame(output.parent, field.parent.parent)
        assertTrue("Terminal retains useful space", scroll.height >= ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 120))
        val symbols = activity.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
        assertEquals(20, symbols.childCount)
        assertTrue((0 until symbols.childCount).all { symbols.getChildAt(it).height >= ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 48) })
        fun countEvaluate(view: View): Int = (if (view.id == R.id.calculator_equals || (view is TextView && view.text == "=")) 1 else 0) +
            (if (view is android.view.ViewGroup) (0 until view.childCount).sumOf { countEvaluate(view.getChildAt(it)) } else 0)
        assertEquals("Only one evaluate key", 1, countEvaluate(activity.findViewById(R.id.calculator_overlay)))
    }

    @Test fun numberedTopicsHaveBoundariesAndBackRestoresCalculator() = withCalculator { activity ->
        val field = activity.findViewById<EditText>(R.id.calculator_expression)
        val output = activity.findViewById<TextView>(R.id.calculator_result)
        val original = output.text.toString()
        fun command(value: String) {
            field.setText(value)
            activity.findViewById<View>(R.id.calculator_equals).performClick()
        }
        activity.findViewById<View>(R.id.calculator_help_tab).performClick()
        command("1")
        assertTrue(output.text.toString(), output.text.contains("2 + 3 * 4"))
        assertFalse(output.text.contains("2 Previous"))
        assertTrue(output.text.contains("3 Next"))
        command("2") // No previous topic: stay here, never wrap.
        assertTrue(output.text.contains("2 + 3 * 4"))
        command("3")
        assertTrue(output.text.contains("sin"))
        command("1")
        assertTrue(output.text.contains("5 Keypad"))
        command("5")
        assertTrue(output.text.contains("⌫"))
        assertTrue(output.text.contains("2 Previous"))
        assertFalse(output.text.contains("3 Next"))
        command("3")
        assertTrue(output.text.contains("⌫"))
        activity.onBackPressed()
        assertTrue(output.text.contains("5 Keypad"))
        assertFalse(activity.isFinishing)
        activity.onBackPressed()
        assertFalse(activity.isFinishing)
        assertEquals(original, output.text.toString())
        assertEquals("ans + 7", field.text.toString())
        assertEquals(3, field.selectionStart)
        assertEquals("4", CalculatorEngine.prefs(context).getString("terminal_ans", null))
    }
}
