package ohi.andre.consolelauncher.calculator

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import org.junit.Assert.*
import org.junit.Test

class CalculatorPreviewIntegrationTest {
    @get:org.junit.Rule val windows = ohi.andre.consolelauncher.help.WindowPreferencesRule(fresh = false)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun basicPreviewWorksWithoutOptionalEngineAndDoesNotCommitAnswerOrHistory() {
        XMLPrefsManager.loadCommons(context)
        val mode = LauncherSettings.get(Behavior.qalculate)
        val prefs = CalculatorEngine.prefs(context)
        val saved = prefs.all.toMap()
        val transcript = "calc> 3*5\n15"
        var activity: CalculatorActivity? = null
        try {
            LauncherSettings.set(context, Behavior.qalculate, "false")
            prefs.edit().putString("terminal_ans", "15").putString("terminal_transcript", transcript)
                .putString("terminal_draft", "").commit()
            val options = CalculatorEngine.Options(true, false)
            fun preview(input: String, request: CalculatorEngine.Request = CalculatorEngine.Request()) =
                CalculatorEngine.evaluate(context, input, options, request, preview = true)
            for ((expression, expected) in listOf("5+5" to "10", "(2+3)×4" to "20", "9÷2" to "4.5", "2^3" to "8")) {
                val result = preview(expression)
                assertFalse(result.toString(), result.error)
                assertEquals(expected, result.text)
                assertEquals(expression, result.parsed)
                assertNull("Preview is not a committed answer", result.reusable)
            }
            for (expression in listOf("x=99", "save(99,x)", "sqrt(144)", "5 ft to cm", "ans")) {
                assertEquals(expression, "preview_unavailable", preview(expression).failure)
            }
            for (expression in listOf("2+", "(2+3", "2+3)", "1/0", "1 2")) {
                assertTrue("Incomplete/invalid expression: $expression", preview(expression).error)
            }
            val cancelled = CalculatorEngine.Request().apply { cancel() }
            assertEquals("cancelled", preview("5+5", cancelled).failure)
            assertEquals("15", prefs.getString("terminal_ans", null))
            assertEquals(transcript, prefs.getString("terminal_transcript", null))

            val screen = instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
            activity = screen
            instrumentation.runOnMainSync {
                screen.findViewById<EditText>(R.id.calculator_expression).setText("5+5")
            }
            var hint = ""
            val deadline = SystemClock.uptimeMillis() + 8000
            while (SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { hint = screen.findViewById<TextView>(R.id.calculator_preview).text.toString() }
                if (hint.contains("5+5") && hint.contains("10") && hint.contains("→")) break
                SystemClock.sleep(30)
            }
            assertTrue("Basic live preview: $hint", hint.contains("5+5") && hint.contains("10") && hint.contains("→"))
            assertEquals("15", prefs.getString("terminal_ans", null))
            assertEquals(transcript, prefs.getString("terminal_transcript", null))
            instrumentation.runOnMainSync {
                assertEquals(transcript, screen.findViewById<TextView>(R.id.calculator_result).text.toString())
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }; instrumentation.waitForIdleSync()
            val editor = prefs.edit().clear()
            for ((key, value) in saved) when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            editor.commit()
            LauncherSettings.set(context, Behavior.qalculate, mode)
        }
    }

    @Test fun nativePreviewExposesInterpretationWithoutSavingOrDiscardingVariables() {
        XMLPrefsManager.loadCommons(context)
        val mode = LauncherSettings.get(Behavior.qalculate)
        try {
            LauncherSettings.set(context, Behavior.qalculate, "true")
            val options = CalculatorEngine.Options(true, false)
            fun preview(input: String) = CalculatorEngine.evaluate(context, input, options, preview = true)
            val units = preview("cls")
            assertFalse(units.toString(), units.error)
            assertTrue(units.toString(), units.text.contains("mL") && units.text.contains("s"))
            assertTrue("Engine interpretation: $units", !units.parsed.isNullOrBlank() && units.parsed!!.contains("*"))
            assertNull("Preview cannot supply a committed answer", units.reusable)
            assertEquals("12", preview("sqrt(144)").text)
            assertEquals("Preview matches the pane's approximate angle mode",
                CalculatorEngine.evaluate(context, "sin(90)", options).text, preview("sin(90)").text)
            assertTrue(preview("5 ft to cm").text.contains("152.4"))
            for (input in listOf("61d to min", "61 day to min")) {
                assertEquals(input, "87840 min", preview(input).text)
                assertEquals(input, "87840 min", CalculatorEngine.evaluate(context, input, options).text)
            }
            assertEquals("7", CalculatorEngine.evaluate(context, "preview_fixture_value = 7", options).text)
            assertEquals("14", preview("preview_fixture_value * 2").text)
            for (input in listOf("preview_fixture_value = 99", "save(99, preview_fixture_value)",
                "1 + save(99, preview_fixture_value)", "sum(sin(n^n), 1, 1000000000, n)", "2^(2^100)", "1".repeat(257))) {
                assertEquals(input, "preview_unavailable", preview(input).failure)
                assertEquals("Preview must not change or restart the variable-owning engine: $input", "7",
                    CalculatorEngine.evaluate(context, "preview_fixture_value", options).text)
            }
            assertTrue(preview("2+").error)
            assertTrue(preview("1/0").error)
            assertEquals("4", preview("2+2").text)
        } finally { LauncherSettings.set(context, Behavior.qalculate, mode) }
    }

    @Test fun liveHintStaysBelowInputAndOnlyEnterChangesHistoryAndAnswer() {
        XMLPrefsManager.loadCommons(context)
        val mode = LauncherSettings.get(Behavior.qalculate)
        val prefs = CalculatorEngine.prefs(context)
        val keys = listOf("terminal_ans", "terminal_transcript", "terminal_draft")
        val saved = keys.associateWith { prefs.getString(it, null) }
        val cursor = prefs.getInt("terminal_cursor", 0)
        val selection = prefs.getInt("terminal_selection_end", cursor)
        var activity: CalculatorActivity? = null
        try {
            prefs.edit().putString("terminal_ans", "15").putString("terminal_transcript", "calc> 5+5\n10\ncalc> +5\n15")
                .putString("terminal_draft", "").commit()
            val screen = instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
            activity = screen
            fun type(value: String) = instrumentation.runOnMainSync {
                screen.findViewById<EditText>(R.id.calculator_expression).apply { setText(value); setSelection(length()) }
            }
            fun hint(check: (String) -> Boolean): String {
                val deadline = SystemClock.uptimeMillis() + 8000
                var text = ""
                while (SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync { text = screen.findViewById<TextView>(R.id.calculator_preview).text.toString() }
                    if (check(text)) return text
                    SystemClock.sleep(30)
                }
                fail("Preview did not settle: $text"); return text
            }
            type("+5")
            hint { it.contains("15") && it.contains("20") && it.contains("→") }
            type("sqrt(144)"); type("cls")
            hint { it.contains("mL") && it.contains("→") }
            instrumentation.runOnMainSync {
                val input = screen.findViewById<EditText>(R.id.calculator_expression)
                val preview = screen.findViewById<TextView>(R.id.calculator_preview)
                val inputPosition = IntArray(2).also { input.getLocationOnScreen(it) }
                val hintPosition = IntArray(2).also { preview.getLocationOnScreen(it) }
                assertTrue("Hint sits below the editable line", hintPosition[1] >= inputPosition[1] + input.height)
                val visible = android.graphics.Rect()
                assertTrue(preview.getGlobalVisibleRect(visible)); assertEquals(preview.height, visible.height())
                assertTrue(preview.textSize < input.textSize)
                assertFalse(input.showSoftInputOnFocus)
                assertEquals(3, input.selectionStart)
                assertEquals("15", prefs.getString("terminal_ans", null))
                assertEquals("calc> 5+5\n10\ncalc> +5\n15", screen.findViewById<TextView>(R.id.calculator_result).text.toString())
            }
            SystemClock.sleep(400)
            java.io.File(context.cacheDir, "calculator-preview-units.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            type("61d to min")
            hint { it.contains("87840 min") && it.contains("→") }
            assertEquals("Conversions in preview do not replace the answer", "15", prefs.getString("terminal_ans", null))
            type("tax = 18%")
            hint { it == context.getString(R.string.calculator_preview_enter_only) }
            assertEquals("15", prefs.getString("terminal_ans", null))
            type("clear"); hint { it == context.getString(R.string.calculator_preview_clear) }
            type("2+"); hint { it == context.getString(R.string.calculator_preview_incomplete) }
            type("+5"); hint { it.contains("20") && it.contains("→") }
            instrumentation.runOnMainSync { screen.findViewById<View>(R.id.calculator_equals).performClick() }
            val deadline = SystemClock.uptimeMillis() + 8000
            while (prefs.getString("terminal_ans", "") != "20" && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30)
            assertEquals("20", prefs.getString("terminal_ans", null))
            instrumentation.runOnMainSync {
                assertEquals("", screen.findViewById<EditText>(R.id.calculator_expression).text.toString())
                assertEquals("", screen.findViewById<TextView>(R.id.calculator_preview).text.toString())
                assertTrue(screen.findViewById<TextView>(R.id.calculator_result).text.endsWith("calc> +5\n20"))
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }; instrumentation.waitForIdleSync()
            val editor = prefs.edit()
            saved.forEach { (key, value) -> editor.putString(key, value) }
            editor.putInt("terminal_cursor", cursor).putInt("terminal_selection_end", selection).commit()
            LauncherSettings.set(context, Behavior.qalculate, mode)
        }
    }
}
