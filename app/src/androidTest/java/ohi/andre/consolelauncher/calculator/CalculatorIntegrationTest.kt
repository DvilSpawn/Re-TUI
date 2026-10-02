package ohi.andre.consolelauncher.calculator

import android.content.Intent
import android.os.SystemClock
import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.tuils.Tuils
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.raw.calc
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CalculatorIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun nativeBridgeHandlesMathUnicodeErrorsAndTimeoutRecovery() {
        if (!CalculatorEngine.supported()) return
        XMLPrefsManager.loadCommons(context)
        val savedMode = LauncherSettings.get(Behavior.qalculate)
        val prefs = CalculatorEngine.prefs(context)
        val savedDegrees = prefs.getBoolean("degrees", true)
        val savedExact = prefs.getBoolean("exact", true)
        try {
            LauncherSettings.set(context, Behavior.qalculate, "true")
            prefs.edit().putBoolean("degrees", true).putBoolean("exact", true).commit()
            fun result(expression: String): String {
                val result = CalculatorEngine.evaluate(context, expression)
                assertFalse("$expression: ${result.text}", result.error)
                return result.text
            }
            assertEquals("14", result("2 * (3 + 4)"))
            assertEquals("6", result("2 × 3"))
            assertEquals("1", result("sin(90)"))
            assertTrue(result("5 km to miles").contains("3.106"))
            assertEquals("2 ft + 11 in", result("2ft 6in + 5in to ft"))
            assertTrue(result("diff(x^3)").contains("3"))
            prefs.edit().putBoolean("degrees", false).commit()
            assertEquals("1", result("sin(pi/2)"))
            prefs.edit().putBoolean("exact", false).commit()
            val approximateSine = result("sin(pi/2)")
            assertTrue(approximateSine, approximateSine.startsWith("interval(") && approximateSine.contains("1.000"))
            assertTrue(result("1/3").startsWith("0.333"))
            val approximateRoot = result("sqrt(2)")
            assertTrue(approximateRoot, approximateRoot.contains("1.414"))
            val invalid = CalculatorEngine.evaluate(context, "sin()")
            assertTrue(invalid.toString(), invalid.error)
            assertTrue(CalculatorEngine.evaluate(context, "1\u00002").error)
            // Exercise a supplementary Unicode character through the standard UTF-8 bridge.
            CalculatorEngine.evaluate(context, "😀")
            assertEquals("4", result("2+2"))
            val start = SystemClock.elapsedRealtime()
            val timeout = CalculatorEngine.evaluate(context, "sum(sin(n^n), 1, 1000000000, n)")
            assertTrue(timeout.toString(), timeout.error)
            assertEquals(context.getString(R.string.calculator_timeout), timeout.text)
            assertTrue("Native work must remain bounded", SystemClock.elapsedRealtime() - start < 10000)
            SystemClock.sleep(300)
            assertEquals("4", result("2+2"))
        } finally {
            LauncherSettings.set(context, Behavior.qalculate, savedMode)
            prefs.edit().putBoolean("degrees", savedDegrees).putBoolean("exact", savedExact).commit()
        }
    }

    private fun findText(view: View, text: String): View? {
        if (view is TextView && (view.text.toString() == text || view.hint?.toString() == text)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findText(view.getChildAt(index), text)?.let { return it }
        return null
    }

    @Test fun commandsAndSeparateTaskPreserveCalculatorState() {
        val prefs = CalculatorEngine.prefs(context)
        val saved = prefs.all.toMap()
        XMLPrefsManager.loadCommons(context)
        val savedMode = LauncherSettings.get(Behavior.qalculate)
        var activity: CalculatorActivity? = null
        fun command(input: String): String = calc().exec(object : ExecutePack(CommandGroup("")) {
            init { context = this@CalculatorIntegrationTest.context; args = arrayOf(input) }
        })
        fun awaitResult(expected: String) {
            val deadline = SystemClock.uptimeMillis() + 10000
            var actual = ""
            while (SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { actual = activity!!.findViewById<TextView>(R.id.calculator_result).text.toString() }
                if (actual.contains(expected)) return
                SystemClock.sleep(50)
            }
            fail("Expected $expected, got $actual")
        }
        try {
            prefs.edit().clear().commit()
            LauncherSettings.set(context, Behavior.qalculate, "false")
            assertFalse(CalculatorEngine.enabled(context))
            assertEquals("14", command("2 * (3 + 4)"))
            assertTrue(CalculatorEngine.evaluate(context, "5 km to miles").error)
            assertTrue(CalculatorEngine.evaluate(context, "1/0").error)
            val launcher = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
            val monitor = instrumentation.addMonitor(CalculatorActivity::class.java.name, null, false)
            calc().onNotArgEnough(object : ExecutePack(CommandGroup("")) { init { context = this@CalculatorIntegrationTest.context } }, 0)
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as CalculatorActivity
            instrumentation.removeMonitor(monitor)
            instrumentation.waitForIdleSync()
            val calculator = activity!!
            assertNotEquals(launcher.taskId, calculator.taskId)
            instrumentation.runOnMainSync {
                calculator.findViewById<EditText>(R.id.calculator_expression).setText("2 * (3 + 4)")
                calculator.findViewById<TextView>(R.id.calculator_equals).performClick()
            }
            awaitResult("14")
            instrumentation.runOnMainSync {
                assertEquals(View.GONE, calculator.findViewById<View>(R.id.calculator_advanced).visibility)
                assertTrue(calculator.findViewById<View>(R.id.calculator_window_border).height < calculator.findViewById<View>(R.id.calculator_overlay).height)
            }
            File(context.cacheDir, "calculator-basic-check.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            if (CalculatorEngine.supported()) {
                val settings = instrumentation.startActivitySync(Intent(context, TuixtActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), "behavior.xml").absolutePath)
                    .putExtra(TuixtActivity.ONLY_SECTION, "Commands")) as TuixtActivity
                instrumentation.waitForIdleSync()
                try {
                    instrumentation.runOnMainSync {
                        (findText(settings.window.decorView, context.getString(R.string.editor_tuixtactivity_search_settings_d25ad)) as EditText).setText("qalculate")
                    }
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync {
                        assertEquals("qalculate", settings.findViewById<TextView>(R.id.setting_title).text.toString())
                        settings.findViewById<View>(R.id.setting_switch).performClick()
                    }
                    File(context.cacheDir, "calculator-settings-check.png").outputStream().use {
                        instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    instrumentation.runOnMainSync {
                        findText(settings.window.decorView, context.getString(R.string.editor_tuixtactivity_save_50815))!!.performClick()
                    }
                    instrumentation.waitForIdleSync()
                } finally {
                    instrumentation.runOnMainSync { settings.finish() }
                    instrumentation.waitForIdleSync()
                }
                instrumentation.runOnMainSync { CalculatorActivity.open(context) }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(View.VISIBLE, calculator.findViewById<View>(R.id.calculator_advanced).visibility)
                }
                assertTrue(CalculatorEngine.enabled(context))
                assertEquals("4", command("2+2"))
                val conversion = command("5 km to miles")
                assertTrue(conversion, conversion.contains("3.106"))
                val derivative = command("diff(x^3)")
                assertTrue(derivative, derivative.contains("3") && derivative.contains("x"))
                assertTrue(CalculatorEngine.evaluate(context, "1".repeat(1025)).error)
                repeat(5) { assertEquals("4", command("2+2")) }
                instrumentation.runOnMainSync {
                    calculator.findViewById<EditText>(R.id.calculator_expression).setText("5 km to miles")
                    calculator.findViewById<TextView>(R.id.calculator_equals).performClick()
                }
                awaitResult("3.106")
            }
            SystemClock.sleep(500)
            File(context.cacheDir, "calculator-check.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val expected = prefs.getString("expression", "")
            instrumentation.runOnMainSync {
                launcher.startActivity(Intent(launcher, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { launcher.reload() }
            instrumentation.waitForIdleSync()
            assertFalse(calculator.isFinishing)
            instrumentation.runOnMainSync { CalculatorActivity.open(context) }
            instrumentation.waitForIdleSync()
            val recreation = instrumentation.addMonitor(CalculatorActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { calculator.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(recreation, 10000) as CalculatorActivity
            instrumentation.removeMonitor(recreation)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(expected, activity!!.findViewById<EditText>(R.id.calculator_expression).text.toString())
            }
            assertEquals(calculator.taskId, activity!!.taskId)
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
            LauncherSettings.set(context, Behavior.qalculate, savedMode)
            prefs.edit().clear().apply {
                saved.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                } }
            }.commit()
        }
    }
}
