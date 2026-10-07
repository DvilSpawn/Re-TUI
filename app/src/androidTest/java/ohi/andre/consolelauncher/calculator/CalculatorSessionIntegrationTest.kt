package ohi.andre.consolelauncher.calculator

import android.content.Intent
import android.os.SystemClock
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Test

class CalculatorSessionIntegrationTest {
    @Test fun chainedUnitsErrorsAndExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
                val size = ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.getInt(ohi.andre.consolelauncher.managers.xml.options.Suggestions.suggestions_size)
                assertEquals(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, size.toFloat(), context.resources.displayMetrics), field.textSize, 0.1f)
                val symbols = activity!!.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
                fun key(label: String) = (0 until symbols.childCount).map { symbols.getChildAt(it) as TextView }.first { it.text.toString() == label }
                field.setText("12+34"); field.setSelection(2, 3); key("×").performClick()
                assertEquals("12×34", field.text.toString()); assertEquals(3, field.selectionStart)
                field.setText(""); key("√").performClick()
                assertEquals("sqrt()", field.text.toString()); assertEquals(5, field.selectionStart)
                field.setText("44"); field.setSelection(2)
                activity!!.findViewById<View>(R.id.calculator_letters_tab).performClick()
                val letters = activity!!.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
                (0 until letters.childCount).map { letters.getChildAt(it) as TextView }.first { it.text.toString() == "F" }.performClick()
                assertEquals("44f", field.text.toString())
                activity!!.findViewById<View>(R.id.calculator_functions_tab).performClick()
                (0 until letters.childCount).map { letters.getChildAt(it) as TextView }.first { it.text.toString() == "sqrt" }.performClick()
                assertEquals("44fsqrt()", field.text.toString())
                assertTrue("Percent remains available on the function layer", (0 until letters.childCount).any { (letters.getChildAt(it) as TextView).text.toString() == "%" })
                activity!!.findViewById<View>(R.id.calculator_numbers_tab).performClick()
                assertEquals(View.VISIBLE, activity!!.findViewById<View>(R.id.calculator_main_pane).visibility)
                assertEquals(96, activity!!.findViewById<ImageButton>(R.id.calculator_help_tab).imageAlpha)
                assertEquals(255, activity!!.findViewById<ImageButton>(R.id.calculator_main_tab).imageAlpha)
                activity!!.findViewById<View>(R.id.calculator_help_tab).performClick()
                assertEquals("Help uses the same terminal and keypad", View.VISIBLE, activity!!.findViewById<View>(R.id.calculator_main_pane).visibility)
                assertEquals("", field.text.toString())
                assertTrue(activity!!.findViewById<TextView>(R.id.calculator_result).text.contains("1"))
                assertEquals(255, activity!!.findViewById<ImageButton>(R.id.calculator_help_tab).imageAlpha)
                assertEquals(96, activity!!.findViewById<ImageButton>(R.id.calculator_main_tab).imageAlpha)
                activity!!.findViewById<View>(R.id.calculator_main_tab).performClick()
                assertEquals(View.VISIBLE, activity!!.findViewById<View>(R.id.calculator_main_pane).visibility)
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
            assertEquals("7", execute("7")) // Digits in calculator mode are arithmetic, not help choices.
            execute("2+")
            assertEquals("7", prefs.getString("terminal_ans", null))
            instrumentation.runOnMainSync {
                assertEquals("2+", activity!!.findViewById<EditText>(R.id.calculator_expression).text.toString())
                assertTrue(activity!!.findViewById<TextView>(R.id.calculator_result).text.contains("calc> 2+"))
            }
            val first = execute("5 ft to miles")
            assertTrue(first, first.contains("0.000946969696")); assertTrue(first, first.contains("mi"))
            val second = execute("ans * 2")
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
                assertTrue(transcript.contains("calc> ans * 2"))
                val field = activity!!.findViewById<EditText>(R.id.calculator_expression)
                assertEquals("", field.text.toString())
                field.setText("exit"); activity!!.findViewById<View>(R.id.calculator_equals).performClick()
                assertTrue(activity!!.isFinishing)
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            val editor = prefs.edit()
            saved.forEach { (key,value) -> editor.putString(key,value) }
            editor.putInt("terminal_cursor",cursor).putInt("terminal_selection_end", selectionEnd).commit()
        }
    }
}
