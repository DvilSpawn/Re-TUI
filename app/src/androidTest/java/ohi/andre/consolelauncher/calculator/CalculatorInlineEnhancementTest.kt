package ohi.andre.consolelauncher.calculator

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLStreamHandler

/** Uses a disposable launcher; test catalogue transport is scoped to this instrumentation process. */
class CalculatorInlineEnhancementTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixture get() = File(context.filesDir, "engine-fixture.retui.calc.engine")
    private val screenshots get() = File(context.getExternalFilesDir("qalculate-flow"), "inline-current").apply { mkdirs() }

    @Before fun isolatedBasicCalculator() {
        check(context.packageName.endsWith(".calculatorpackdebug"))
        Tuils.init(context); XMLPrefsManager.loadCommons(context)
        if (CalculatorPackStore.activeDirectory(context) != null) CalculatorPackStore.remove(context)
        WasmCalculatorRuntime.reset()
        CalculatorEngine.prefs(context).edit().clear().commit()
        LauncherSettings.set(context, Behavior.qalculate, "true")
    }

    private fun start() = (instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity).also { idle() }
    private fun idle() { instrumentation.waitForIdleSync(); SystemClock.sleep(180) }
    private fun card(activity: Activity): View = activity.window.decorView.findViewWithTag("calculator-enhancement-panel")
    private fun shot(name: String) {
        idle()
        File(screenshots, "$name.png").outputStream().use {
            check(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    private fun find(view: View, text: String): View? {
        if (view is TextView && view.isClickable && view.isShown && view.getGlobalVisibleRect(Rect()) && view.text.toString().equals(text, true)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), text)?.let { return it }
        return null
    }
    private fun click(resource: Int) {
        var clicked = false
        instrumentation.runOnMainSync {
            WindowInspector.getGlobalWindowViews().asReversed().firstNotNullOfOrNull { find(it, context.getString(resource)) }
                ?.let { clicked = it.performClick() }
        }
        assertTrue("Visible action: ${context.getString(resource)}", clicked)
        idle()
    }
    private fun await(timeout: Long = 15000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(80)
        assertTrue("Condition timed out", condition()); idle()
    }
    private fun calculate(activity: CalculatorActivity, expression: String) {
        instrumentation.runOnMainSync {
            activity.findViewById<EditText>(R.id.calculator_expression).setText(expression)
            activity.findViewById<View>(R.id.calculator_equals).performClick()
        }
        await {
            var done = false
            instrumentation.runOnMainSync { done = activity.findViewById<View>(R.id.calculator_equals).isEnabled }
            done
        }
    }
    private fun finish(activity: Activity) { instrumentation.runOnMainSync { activity.finishAndRemoveTask() }; idle() }

    @Test fun missingEngineOfferAndAvailabilityRemainInsideCalculator() {
        org.junit.Assume.assumeTrue(BuildConfig.CALCULATOR_ENGINE_CATALOG_URL.isBlank())
        val management = instrumentation.addMonitor(CalculatorPackActivity::class.java.name, null, false)
        val screen = start()
        try {
            assertEquals(View.VISIBLE, card(screen).visibility)
            shot("01-offer-inside-calculator")
            calculate(screen, "2+2")
            instrumentation.runOnMainSync { screen.findViewById<EditText>(R.id.calculator_expression).setText("15+6") }
            // Revealing the offer scrolls it into view without navigating to another activity.
            click(R.string.optional_calculator_status_basic)
            click(R.string.optional_calculator_download)
            shot("02-unpublished-message-inside-calculator")
            assertEquals(0, management.hits)
            assertEquals("15+6", screen.findViewById<EditText>(R.id.calculator_expression).text.toString())
            assertTrue(screen.findViewById<TextView>(R.id.calculator_result).text.toString().contains("4"))
        } finally { instrumentation.removeMonitor(management); finish(screen) }
    }

    @Test fun dismissAndHelpReopenPreserveDraftAcrossRecreation() {
        val management = instrumentation.addMonitor(CalculatorPackActivity::class.java.name, null, false)
        var screen = start()
        try {
            instrumentation.runOnMainSync {
                screen.findViewById<EditText>(R.id.calculator_expression).setText("35/7")
                fun dismiss(view: View): View? {
                    if (view.contentDescription == context.getString(R.string.optional_calculator_not_now)) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) dismiss(view.getChildAt(i))?.let { return it }
                    return null
                }
                requireNotNull(dismiss(card(screen))).performClick()
            }
            idle(); assertEquals(View.GONE, card(screen).visibility)
            val monitor = instrumentation.addMonitor(CalculatorActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync { screen.recreate() }
                screen = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CalculatorActivity
            } finally { instrumentation.removeMonitor(monitor) }
            idle()
            assertEquals(View.GONE, card(screen).visibility)
            assertEquals("35/7", screen.findViewById<EditText>(R.id.calculator_expression).text.toString())
            shot("06-dismissed-calculator-kept")
            instrumentation.runOnMainSync { screen.findViewById<View>(R.id.calculator_help_tab).performClick() }
            idle(); click(R.string.optional_calculator_title)
            assertEquals(View.VISIBLE, card(screen).visibility)
            assertEquals(0, management.hits)
            assertEquals("35/7", screen.findViewById<EditText>(R.id.calculator_expression).text.toString())
        } finally { instrumentation.removeMonitor(management); finish(screen) }
    }

    @Test fun inlinePickerInstallReturnsToEnhancedCalculatorWithoutLosingDraft() {
        check(fixture.isFile)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "Qalculate-inline-test.retui.calc.engine")
            put(MediaStore.Downloads.MIME_TYPE, CalculatorPackStore.MIME)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output -> fixture.inputStream().use { it.copyTo(output) } }
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        val screen = start()
        val management = instrumentation.addMonitor(CalculatorPackActivity::class.java.name, null, false)
        val picker = instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*")
        },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        try {
            instrumentation.runOnMainSync { screen.findViewById<EditText>(R.id.calculator_expression).setText("42+") }
            click(R.string.optional_calculator_status_basic)
            click(R.string.optional_calculator_import)
            assertEquals(1, picker.hits)
            shot("03-import-confirmation-in-calculator")
            click(R.string.optional_calculator_install)
            await {
                var hidden = false
                instrumentation.runOnMainSync { hidden = card(screen).visibility == View.GONE }
                hidden && CalculatorEngine.enabled(context)
            }
            assertEquals(0, management.hits)
            assertEquals("42+", screen.findViewById<EditText>(R.id.calculator_expression).text.toString())
            assertEquals("1/3", CalculatorEngine.evaluate(context, "1/3").text)
            calculate(screen, "sqrt(144)")
            assertTrue(screen.findViewById<TextView>(R.id.calculator_result).text.toString().contains("12"))
            shot("04-installed-same-calculator")
        } finally {
            instrumentation.removeMonitor(picker); instrumentation.removeMonitor(management)
            finish(screen); context.contentResolver.delete(uri, null, null)
        }
    }

    @Test fun publishedDownloadInstallsAndRetainsVerifiedEngine() {
        org.junit.Assume.assumeTrue(BuildConfig.CALCULATOR_ENGINE_CATALOG_URL.isNotBlank())
        val screen = start()
        try {
            click(R.string.optional_calculator_download)
            await(60000) {
                var hidden = false
                instrumentation.runOnMainSync { hidden = card(screen).visibility == View.GONE }
                CalculatorEngine.enabled(context) && hidden
            }
            assertEquals("5.8.2", CalculatorPackStore.installedVersion(context))
            val archive = File(CalculatorPackStore.downloadDirectory(context), "qalculate-5.8.2${CalculatorPackStore.EXTENSION}")
            assertTrue(archive.isFile)
            assertEquals("9449fd086db0a512f440b3038588e009274c259f36ee56d984f4fd8991ee55e3", CalculatorPackStore.sha256(archive.readBytes()))
            assertEquals("1/3", CalculatorEngine.evaluate(context, "1/3").text)
            instrumentation.runOnMainSync {
                screen.findViewById<EditText>(R.id.calculator_expression).setText("")
            }
            for (key in listOf("5", "+", "5")) {
                val point = intArrayOf(0, 0)
                instrumentation.runOnMainSync {
                    val button = requireNotNull(find(screen.window.decorView, key))
                    button.getLocationOnScreen(point)
                    point[0] += button.width / 2; point[1] += button.height / 2
                }
                val down = SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0].toFloat(), point[1].toFloat(), 0).let {
                        instrumentation.sendPointerSync(it); it.recycle()
                    }
                }
                idle()
            }
            await {
                var ready = false
                instrumentation.runOnMainSync { ready = screen.findViewById<TextView>(R.id.calculator_preview).text.contains("10") }
                ready
            }
            shot("10-real-host-download-installed-keypad-preview")
        } finally { finish(screen) }
    }

    @Test fun inlineDownloadCanCancelAndRetryWhileBasicCalculatorStillWorks() {
        org.junit.Assume.assumeTrue(BuildConfig.CALCULATOR_ENGINE_CATALOG_URL.isNotBlank())
        check(fixture.isFile)
        val bytes = fixture.readBytes()
        val savedArchive = File(CalculatorPackStore.downloadDirectory(context), "qalculate-5.8.2.retui.calc.engine")
        val previousArchive = savedArchive.takeIf { it.isFile }?.readBytes()
        val catalog = JSONObject().put("schema", 1).put("version", "5.8.2")
            .put("url", "https://re-tui.pages.dev/calculator-packs/qalculate-5.8.2.retui.calc.engine")
            .put("sha256", java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
            .toString().toByteArray()
        URL.setURLStreamHandlerFactory { protocol -> if (protocol != "https") null else object : URLStreamHandler() {
            override fun openConnection(url: URL) = object : HttpURLConnection(url) {
                val body = if (url.path.endsWith("catalog.json")) catalog else bytes
                override fun connect() { }
                override fun disconnect() { }
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getHeaderField(name: String?): String? = if (name.equals("Content-Length", true)) body.size.toString() else null
                override fun getInputStream(): InputStream = object : ByteArrayInputStream(body) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (body === bytes) SystemClock.sleep(40)
                        return super.read(buffer, offset, minOf(length, 8192))
                    }
                }
            }
        } }
        val management = instrumentation.addMonitor(CalculatorPackActivity::class.java.name, null, false)
        val screen = start()
        try {
            click(R.string.optional_calculator_download)
            calculate(screen, "2+2")
            shot("07-download-progress-in-calculator-local-preview")
            click(R.string.optional_calculator_cancel)
            await { CalculatorPackStore.installedVersion(context) == null }
            if (previousArchive == null) assertFalse("Cancel must not leave a partial engine archive", savedArchive.exists())
            else assertArrayEquals("Cancel must preserve an earlier saved archive", previousArchive, savedArchive.readBytes())
            shot("08-download-cancelled-in-calculator-local-preview")
            click(R.string.optional_calculator_download)
            await(20000) {
                var hidden = false
                instrumentation.runOnMainSync { hidden = card(screen).visibility == View.GONE }
                hidden && CalculatorEngine.enabled(context)
            }
            assertEquals(0, management.hits)
            assertEquals(File(Tuils.getFolder(), "calculator-engines"), savedArchive.parentFile)
            assertArrayEquals("Download must retain the exact checksum-verified archive", bytes, savedArchive.readBytes())
            assertEquals(CalculatorPackStore.sha256(bytes), CalculatorPackStore.sha256(savedArchive.readBytes()))
            assertFalse("The active runtime must remain app-private", requireNotNull(CalculatorPackStore.activeDirectory(context))
                .absolutePath.startsWith(savedArchive.parentFile!!.absolutePath))
            assertEquals("1/3", CalculatorEngine.evaluate(context, "1/3").text)
            shot("09-download-ready-same-calculator-local-preview")
        } finally { instrumentation.removeMonitor(management); finish(screen) }
    }
}
