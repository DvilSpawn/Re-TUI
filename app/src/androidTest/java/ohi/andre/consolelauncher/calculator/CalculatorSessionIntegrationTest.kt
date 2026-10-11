package ohi.andre.consolelauncher.calculator

import android.content.Intent
import android.os.SystemClock
import android.widget.EditText
import android.widget.TextView
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.descendants
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Test

class CalculatorSessionIntegrationTest {
    @get:org.junit.Rule val windowPreferences = ohi.andre.consolelauncher.help.WindowPreferencesRule(fresh = false)
    @Test fun chainedUnitsErrorsAndExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.loadCommons(context)
        val savedEngine = ohi.andre.consolelauncher.managers.settings.LauncherSettings.get(ohi.andre.consolelauncher.managers.xml.options.Behavior.qalculate)
        val prefs = CalculatorEngine.prefs(context)
        val keys = listOf("terminal_ans", "terminal_transcript", "terminal_draft")
        val saved = keys.associateWith { prefs.getString(it, null) }
        val cursor = prefs.getInt("terminal_cursor", 0)
        val selectionEnd = prefs.getInt("terminal_selection_end", cursor)
        var activity: CalculatorActivity? = null
        try {
            prefs.edit().remove("terminal_ans").remove("terminal_transcript").remove("terminal_draft").commit()
            activity = instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
            instrumentation.runOnMainSync {
                val field = activity!!.findViewById<EditText>(R.id.calculator_expression)
                assertFalse(field.showSoftInputOnFocus)
                val size = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
                assertEquals(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, size.toFloat(), context.resources.displayMetrics), field.textSize, 0.1f)
                val symbols = activity!!.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
                fun key(label: String) = symbols.descendants.filterIsInstance<TextView>().first { it.text.toString() == label }
                field.setText("12+34"); field.setSelection(2, 3); key("×").performClick()
                assertEquals("12×34", field.text.toString()); assertEquals(3, field.selectionStart)
                field.setText(""); activity!!.findViewById<View>(R.id.calculator_functions_tab).performClick(); key("sqrt").performClick()
                assertEquals("sqrt()", field.text.toString()); assertEquals(5, field.selectionStart)
                field.setText("44"); field.setSelection(2)
                activity!!.findViewById<View>(R.id.calculator_letters_tab).performClick()
                val letters = activity!!.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
                letters.descendants.filterIsInstance<TextView>().first { it.text.toString() == "F" }.performClick()
                assertEquals("44f", field.text.toString())
                activity!!.findViewById<View>(R.id.calculator_functions_tab).performClick()
                letters.descendants.filterIsInstance<TextView>().first { it.text.toString() == "sqrt" }.performClick()
                assertEquals("44fsqrt()", field.text.toString())
                assertTrue("Percent remains available on the function layer", letters.descendants.filterIsInstance<TextView>().any { it.text.toString() == "%" })
                activity!!.findViewById<View>(R.id.calculator_numbers_tab).performClick()
                assertEquals(View.VISIBLE, activity!!.findViewById<View>(R.id.calculator_main_pane).visibility)
                assertFalse(activity!!.findViewById<TextView>(R.id.calculator_help_tab).isSelected)
                assertTrue(activity!!.findViewById<TextView>(R.id.calculator_main_tab).isSelected)
                assertEquals("44fsqrt()", field.text.toString())
            }
            fun execute(expression: String): String {
                instrumentation.runOnMainSync {
                    activity!!.findViewById<EditText>(R.id.calculator_expression).setText(expression)
                    activity!!.findViewById<View>(R.id.calculator_equals).performClick()
                }
                var complete = false
                val deadline = SystemClock.uptimeMillis() + 12000
                while (!complete && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(100)
                    instrumentation.runOnMainSync { complete = activity!!.findViewById<View>(R.id.calculator_equals).isEnabled }
                }
                assertTrue("Calculation timed out: $expression", complete)
                return prefs.getString("terminal_ans", "").orEmpty()
            }
            instrumentation.runOnMainSync {
                val field = activity!!.findViewById<EditText>(R.id.calculator_expression)
                val symbols = activity!!.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
                field.setText("ans "); field.setSelection(field.length())
                symbols.descendants.filterIsInstance<TextView>().first { it.text.toString() == "=" }.performClick()
                assertEquals("ans =", field.text.toString())
                assertTrue("Equals inserts; only Enter evaluates", activity!!.findViewById<View>(R.id.calculator_equals).isEnabled)
                assertNull(prefs.getString("terminal_ans", null))
            }
            assertEquals("-5", execute("-5")) // With no previous result, signs retain their normal meaning.
            assertEquals("10", execute("5+5"))
            assertEquals("15", execute("+5"))
            assertEquals("30", execute("*2"))
            assertEquals("25", execute("−5"))
            assertEquals("5", execute("÷5"))
            assertEquals("25", execute("^2"))
            execute("/0")
            assertEquals("25", prefs.getString("terminal_ans", null))
            assertEquals("30", execute("+5"))
            assertEquals("25", execute("−5"))
            assertEquals("-5", execute("-30"))
            assertEquals("-5", execute("(-5)")) // Parentheses make a fresh negative value unambiguous.
            assertEquals("10", execute("+15"))
            assertEquals("-5", execute("ans = -5"))
            assertEquals("5", execute("ans = 5"))
            assertEquals("10", execute("5 + ans"))
            assertEquals("15", execute("ans = ans + 5"))
            execute("ans = 1/0")
            assertEquals("15", prefs.getString("terminal_ans", null))
            execute("ans = ")
            assertEquals("15", prefs.getString("terminal_ans", null))
            assertEquals("5", execute("retui_fixture_value = 5"))
            assertEquals("10", execute("5 + retui_fixture_value"))
            assertEquals("7", execute("7")) // Digits in calculator mode are arithmetic, not help choices.
            execute("2+")
            assertEquals("7", prefs.getString("terminal_ans", null))
            instrumentation.runOnMainSync {
                assertEquals("2+", activity!!.findViewById<EditText>(R.id.calculator_expression).text.toString())
                assertTrue(activity!!.findViewById<TextView>(R.id.calculator_result).text.contains("calc> 2+"))
            }
            val first = execute("5 ft to miles")
            assertTrue(first, first.contains("0.000946969696")); assertTrue(first, first.contains("mi"))
            val second = execute("* 2")
            assertTrue(second, second.contains("0.001893939393")); assertTrue(second, second.contains("mi"))
            java.io.File(context.cacheDir, "calculator-terminal-chain.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val third = execute("ans to ft")
            assertTrue(third, third.startsWith("10") && third.contains("ft"))
            execute("1/0")
            assertEquals(third, prefs.getString("terminal_ans", ""))
            assertEquals("11 ft", execute("ans + 1 ft"))
            instrumentation.runOnMainSync {
                val transcript = activity!!.findViewById<TextView>(R.id.calculator_result).text.toString()
                assertTrue(transcript.contains("calc> * 2"))
                assertTrue("Transcript retains the user's short continuation", transcript.contains("calc> +5\n15"))
                val field = activity!!.findViewById<EditText>(R.id.calculator_expression)
                assertEquals("", field.text.toString())
                field.setText("exit"); activity!!.findViewById<View>(R.id.calculator_equals).performClick()
                assertTrue(activity!!.isFinishing)
            }
            instrumentation.waitForIdleSync()
            activity = instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
            assertEquals("12 ft", execute("+1 ft"))
            execute("clear")
            assertEquals("13 ft", execute("+1 ft")) // Clearing history keeps the chain, just like reopening.
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
            val editor = prefs.edit()
            saved.forEach { (key,value) -> editor.putString(key,value) }
            editor.putInt("terminal_cursor",cursor).putInt("terminal_selection_end", selectionEnd).commit()
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Behavior.qalculate, savedEngine)
        }
    }
}
