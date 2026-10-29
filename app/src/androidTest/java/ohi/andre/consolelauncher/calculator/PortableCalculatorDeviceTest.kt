package ohi.andre.consolelauncher.calculator

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Real-engine tests run exclusively in the disposable calculator test application. */
class PortableCalculatorDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixture get() = File(context.filesDir, "engine-fixture.retui.calc.engine")

    @Before fun isolateAndInstall() {
        check(context.packageName.endsWith(".calculatorpackdebug")) { "Use -PcalculatorPackDebug=true" }
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        check(fixture.isFile) { "Copy the real current-version pack into files/engine-fixture.retui.calc.engine first" }
        CalculatorPackStore.install(context, fixture.inputStream())
        WasmCalculatorRuntime.reset()
        LauncherSettings.set(context, Behavior.qalculate, "true")
    }

    @Test fun currentEngineRunsMathUnitsAnglesAndPurePreviewOffline() {
        assertEquals("5.8.2", CalculatorPackStore.installedVersion(context))
        fun value(input: String, exact: Boolean = true): String {
            val result = CalculatorEngine.evaluate(context, input, CalculatorEngine.Options(true, exact))
            assertFalse("$input: $result", result.error)
            return result.text
        }
        assertEquals("4", value("2+2"))
        assertEquals("1/3", value("1/3"))
        assertEquals("12", value("sqrt(144)"))
        assertEquals("1", value("sin(90)"))
        assertTrue(value("5 ft to cm", false).contains("152.4"))
        val preview = CalculatorEngine.evaluate(context, "sqrt(144)", preview = true)
        assertFalse(preview.toString(), preview.error)
        assertEquals("12", preview.text)
        assertNull(preview.reusable)
        assertFalse(preview.parsed.isNullOrBlank())
        assertTrue(CalculatorEngine.evaluate(context, "2+").error)
        assertEquals("division_by_zero", CalculatorEngine.evaluate(context, "1/0").failure)
    }

    @Test fun cancellationAndTimeoutRecreateWorker() {
        val request = CalculatorEngine.Request()
        var response: CalculatorEngine.Result? = null
        val thread = Thread {
            response = CalculatorEngine.evaluate(context, "sum(sin(n^n), 1, 1000000000, n)", request = request)
        }
        thread.start(); SystemClock.sleep(750); request.cancel(); thread.join(9000)
        assertFalse(thread.isAlive)
        assertEquals("cancelled", response?.failure)
        assertEquals("4", CalculatorEngine.evaluate(context, "2+2").text)
        val timeout = CalculatorEngine.evaluate(context, "sum(sin(n^n), 1, 1000000000, n)")
        assertEquals(timeout.toString(), "timeout", timeout.failure)
        assertEquals("1/3", CalculatorEngine.evaluate(context, "1/3").text)
    }

    @Test fun failedImportAndRemovalPreservePersonalCalculatorData() {
        CalculatorEngine.prefs(context).edit().putString("terminal_draft", "keep me").commit()
        val directory = CalculatorPackStore.activeDirectory(context)
        try {
            CalculatorPackStore.install(context, "broken".byteInputStream())
            fail("Invalid engine must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(directory, CalculatorPackStore.activeDirectory(context))
        WasmCalculatorRuntime.reset(); CalculatorPackStore.remove(context)
        assertFalse(CalculatorEngine.enabled(context))
        assertEquals("4", CalculatorEngine.evaluate(context, "2×2").text)
        assertEquals("keep me", CalculatorEngine.prefs(context).getString("terminal_draft", null))
    }

    @Test fun downloadedFileOpensConfirmationAndInstallsInsideLauncher() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "retui-engine-device-test.retui.calc.engine")
            put(MediaStore.Downloads.MIME_TYPE, CalculatorPackStore.MIME)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        var activity: CalculatorPackActivity? = null
        try {
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { output -> fixture.inputStream().use { it.copyTo(output) } }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            CalculatorPackStore.remove(context); WasmCalculatorRuntime.reset()
            activity = instrumentation.startActivitySync(Intent(Intent.ACTION_VIEW).apply {
                setClass(context, CalculatorPackActivity::class.java)
                setDataAndType(uri, CalculatorPackStore.MIME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }) as CalculatorPackActivity
            instrumentation.waitForIdleSync()
            fun find(view: View): View? {
                if (view is TextView && view.text.toString().equals(context.getString(R.string.optional_calculator_install), true)) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                return null
            }
            var clicked = false
            instrumentation.runOnMainSync {
                android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { find(it) }?.let {
                    clicked = it.performClick()
                }
            }
            assertTrue("Open must show an explicit Install pack confirmation", clicked)
            val deadline = SystemClock.uptimeMillis() + 12000
            while (CalculatorPackStore.installedVersion(context) == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            assertEquals("5.8.2", CalculatorPackStore.installedVersion(context))
            assertEquals("1/3", CalculatorEngine.evaluate(context, "1/3").text)
        } finally {
            activity?.let { screen -> instrumentation.runOnMainSync { screen.finish() } }
            context.contentResolver.delete(uri, null, null)
        }
    }
}
