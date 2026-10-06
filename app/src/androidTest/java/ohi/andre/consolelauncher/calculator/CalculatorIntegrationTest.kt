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
                val result = if (expression.contains(" to ") && !expression.startsWith("2ft")) CalculatorEngine.evaluate(context, expression, CalculatorEngine.Options(true,false)) else CalculatorEngine.evaluate(context, expression)
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

    @Test fun conversionsTemplatesAndCancellation() {
        XMLPrefsManager.loadCommons(context)
        val savedMode = LauncherSettings.get(Behavior.qalculate)
        try {
            LauncherSettings.set(context, Behavior.qalculate, "true")
            fun check(input: String, expected: String) {
                val r = CalculatorEngine.evaluate(context,input,CalculatorEngine.Options(true,!input.contains(" to ")))
                assertFalse("$input: $r",r.error); assertTrue("$input: $r",r.text.contains(expected))
            }
            check("2+3*4","14"); check("1/3","1/3")
            val malformed = CalculatorEngine.evaluate(context,"2+")
            assertTrue("Incomplete expression: $malformed",malformed.error)
            val zero = CalculatorEngine.evaluate(context,"1/0")
            assertEquals(zero.toString(),"division_by_zero",zero.failure)
            check("solve(x+2=5, x)","3"); check("diff(x^3, x)","3")
            check("integrate(x, 0, 1, x)","1/2")
            check("(0) oC to oF","32"); check("(32) oF to oC","0")
            check("(1) gal to L","3.785"); check("(1) UK_gal to L","4.546")
            check("(1) kbyte to byte","1000"); check("(1) Kibyte to byte","1024")
            CalculatorUnits.categories.forEachIndexed { category, group -> group.units.indices.forEach { source ->
                val input = CalculatorUnits.expression("1",category,source,0)
                val r = CalculatorEngine.evaluate(context,input,CalculatorEngine.Options(true,false))
                assertFalse("$input: $r",r.error)
            } }
            val request = CalculatorEngine.Request()
            var response: CalculatorEngine.Result? = null
            val thread = Thread { response = CalculatorEngine.evaluate(context,"sum(sin(n^n), 1, 1000000000, n)",CalculatorEngine.Options(true,true),request) }
            thread.start(); SystemClock.sleep(500)
            // A different caller cannot cancel this request.
            CalculatorEngine.Request().cancel(); request.cancel(); thread.join(9000)
            assertFalse(thread.isAlive); assertEquals("cancelled",response?.failure)
            SystemClock.sleep(300); check("2+2","4")
        } finally { LauncherSettings.set(context,Behavior.qalculate,savedMode) }
    }

}
