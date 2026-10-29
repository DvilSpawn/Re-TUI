package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtAdapter
import ohi.andre.consolelauncher.managers.status.BatteryManager
import ohi.andre.consolelauncher.managers.status.BatteryTextRenderer
import ohi.andre.consolelauncher.managers.status.LiveStatusText
import ohi.andre.consolelauncher.managers.status.StatusUpdateListener
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LiveLauncherPreviewTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ins.targetContext
    private fun init() { Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs() }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun fixture(settings: Map<XMLPrefsSave, String>, run: () -> Unit) {
        init()
        val old = settings.keys.associateWith(LauncherSettings::get)
        try { settings.forEach { (key, value) -> LauncherSettings.set(key, value) }; run() }
        finally { ins.runOnMainSync { old.forEach { (key, value) -> LauncherSettings.set(key, value) } } }
    }
    private fun open(root: XMLPrefsManager.XMLPrefsRoot): TuixtActivity =
        (ins.startActivitySync(Intent(context, TuixtActivity::class.java).putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), root.path()).absolutePath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity).also { settle() }
    private fun settle() { Thread.sleep(200); ins.waitForIdleSync() }
    private fun pager(activity: TuixtActivity) = views(activity.window.decorView).filterIsInstance<RecyclerView>().first { it.adapter !is TuixtAdapter }
    private fun settings(activity: TuixtActivity) = views(activity.window.decorView).filterIsInstance<RecyclerView>().first { it.adapter is TuixtAdapter }
    private fun drafts(activity: TuixtActivity, values: Map<XMLPrefsSave, String>) {
        ins.runOnMainSync { (settings(activity).adapter as TuixtAdapter).restoreDraftState(Bundle().apply {
            values.forEach { (key, value) -> putString("${key.parent()?.path()}:${key.label()}", value) }
        }) }
        settle()
    }
    private fun capture(name: String) {
        ins.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.getExternalFilesDir(null), "live-preview-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun colorAt(text: CharSequence, index: Int): Int? = (text as? Spanned)?.getSpans(index, index + 1, ForegroundColorSpan::class.java)?.lastOrNull()?.foregroundColor

    @Test fun batteryBarUsesSameRendererAndRealLevelWithoutChangingLauncher() = fixture(mapOf(
        Behavior.battery_progress_bar to "false", Behavior.battery_format to "BAT %v%%( charging; idle)",
        Behavior.optional_values_separator to ";", Behavior.battery_progress_bar_length to "10", Behavior.battery_progress_bar_symbol to "#",
        Ui.show_battery to "true", Ui.battery_index to "1", Ui.show_ram to "false", Ui.show_device_name to "false", Ui.show_time to "false",
        Ui.show_storage_info to "false", Ui.show_network_info to "false", Ui.show_notes to "false", Ui.show_weather to "false", Ui.show_unlock_counter to "false", Ui.show_ascii to "false",
        SurfaceBorderOption.unified_status_border to "false", Theme.battery_text_high to "#FF00FF00", Theme.battery_text_low to "#FFFF0000"
    )) {
        val activity = open(XMLPrefsManager.XMLPrefsRoot.BEHAVIOR)
        val before = File(Tuils.getFolder(), "behavior.xml").readBytes()
        try {
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
            val level = sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) * 100f / sticky.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val status = sticky.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
            var actual: CharSequence? = null
            ins.runOnMainSync {
                val manager = BatteryManager(context, LauncherSettings.getInt(Ui.battery_size), LauncherSettings.getInt(Behavior.battery_medium), LauncherSettings.getInt(Behavior.battery_low),
                    StatusUpdateListener { _, text -> actual = text })
                if (charging) manager.onCharging()
                manager.update(level)
                assertEquals(actual.toString(), pager(activity).findViewById<TextView>(R.id.tv0).text.toString())
            }
            drafts(activity, mapOf(Behavior.battery_progress_bar to "true", Behavior.battery_progress_bar_symbol to "=", Behavior.battery_progress_bar_length to "12"))
            ins.runOnMainSync {
                val shown = pager(activity).findViewById<TextView>(R.id.tv0).text
                assertEquals("[${"=".repeat(12)}] ${level.toInt()}%", shown.toString())
                val full = Math.round(level / 100f * 12)
                if (full > 0) assertEquals(0xFF00FF00.toInt(), colorAt(shown, 1))
                if (full < 12) assertEquals(0xFFFF0000.toInt(), colorAt(shown, 1 + full))
                assertEquals("false", LauncherSettings.get(Behavior.battery_progress_bar))
                assertFalse(ThemeColorResolver.hasPreview())
                assertArrayEquals(before, File(Tuils.getFolder(), "behavior.xml").readBytes())
            }
            capture("battery-draft")
            drafts(activity, mapOf(SurfaceBorderOption.unified_status_border to "true"))
            ins.runOnMainSync { assertTrue(pager(activity).findViewById<TextView>(R.id.unified_status_battery).text.toString().contains("=".repeat(12))) }
            capture("unified-battery")
            ins.runOnMainSync { activity.finish() }
            assertArrayEquals(before, File(Tuils.getFolder(), "behavior.xml").readBytes())
            // Reproduce the original bug: the saved configuration already enables the progress bar.
            ins.runOnMainSync { LauncherSettings.set(Behavior.battery_progress_bar, "true") }
            val savedPreview = open(XMLPrefsManager.XMLPrefsRoot.UI)
            try {
                ins.runOnMainSync {
                    assertEquals("[${"#".repeat(10)}] ${level.toInt()}%", pager(savedPreview).findViewById<TextView>(R.id.tv0).text.toString())
                }
                capture("battery-saved")
            } finally { ins.runOnMainSync { savedPreview.finish() } }
        } finally { ins.runOnMainSync { activity.finish() } }
    }

    @Test fun batteryThresholdsChargingAndDraftColorsMatchIndependentExpectations() {
        init()
        ins.runOnMainSync {
            val edits = mutableMapOf<XMLPrefsSave, String>(Behavior.battery_progress_bar to "false", Behavior.battery_format to "%v% %(charging;idle)",
                Behavior.optional_values_separator to ";", Ui.enable_battery_status to "true", Theme.battery_text_high to "#FF112233", Theme.battery_text_medium to "#FF445566", Theme.battery_text_low to "#FF778899")
            val values = AppearanceValues { edits[it] ?: LauncherSettings.get(it) }
            assertEquals("82% charging", BatteryTextRenderer.render(context, 82f, true, 13, 50, 20, values).toString())
            assertEquals("15% idle", BatteryTextRenderer.render(context, 15f, false, 13, 50, 20, values).toString())
            assertEquals(0xFF778899.toInt(), colorAt(BatteryTextRenderer.render(context, 15f, false, 13, 50, 20, values), 0))
            assertEquals(0xFF445566.toInt(), colorAt(BatteryTextRenderer.render(context, 40f, false, 13, 50, 20, values), 0))
            assertEquals(0xFF112233.toInt(), colorAt(BatteryTextRenderer.render(context, 82f, true, 13, 50, 20, values), 0))
            edits[Behavior.battery_progress_bar] = "true"; edits[Behavior.battery_progress_bar_length] = "8"; edits[Behavior.battery_progress_bar_symbol] = "█"
            val bar = BatteryTextRenderer.render(context, 50f, false, 13, 50, 20, values)
            assertEquals("[${"█".repeat(8)}] 50%", bar.toString())
            assertEquals(0xFF112233.toInt(), colorAt(bar, 1)); assertEquals(0xFF778899.toInt(), colorAt(bar, 5))
        }
    }

    @Test fun liveClockUsesDraftFormatWithoutReplacingGlobalClock() {
        init()
        ins.runOnMainSync {
            val clock = ohi.andre.consolelauncher.managers.TimeManager.instance
            val values = AppearanceValues { if (it == Behavior.status_time_format) "'DRAFT-CLOCK' HH:mm" else LauncherSettings.get(it) }
            val data = LiveStatusText(context, values)
            data.refresh(LauncherAppearanceState())
            assertTrue(data.text[UIManager.Label.time].toString().startsWith("DRAFT-CLOCK"))
            assertFalse(data.text[UIManager.Label.ram].toString().contains("4.2 / 8 GB"))
            assertSame(clock, ohi.andre.consolelauncher.managers.TimeManager.instance)
            val incomplete = AppearanceValues { if (it == Behavior.time_format_separator) "[" else LauncherSettings.get(it) }
            ohi.andre.consolelauncher.managers.TimeManager(context, incomplete, standalone = true)
            assertSame(clock, ohi.andre.consolelauncher.managers.TimeManager.instance)
        }
    }

    @Test fun previewCopiesActualTerminalOutputAndInputAndKeepsControlsPassive() = fixture(mapOf(
        Behavior.tui_notification to "false", Ui.show_weather to "false", Behavior.enable_crt_filter to "false",
        Behavior.battery_progress_bar to "true", Ui.show_battery to "true", Ui.battery_index to "1", Ui.font_size_offset to "2",
        Ui.show_ram to "false", Ui.show_time to "false", Ui.show_device_name to "false", Ui.show_storage_info to "false",
        Ui.show_network_info to "false", Ui.show_notes to "false", Ui.show_unlock_counter to "false", Ui.show_ascii to "false",
        SurfaceBorderOption.unified_status_border to "false"
    )) {
        val startup = context.getSharedPreferences("retui_startup", 0)
        val startupValues = startup.all
        startup.edit().putBoolean("complete", true).putBoolean("active", false).commit()
        var launcher: LauncherActivity? = null
        var activity: TuixtActivity? = null
        var originalTerminal: ohi.andre.consolelauncher.managers.TerminalManager.State? = null
        val accessMonitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? =
                if (intent.action.orEmpty().contains("NOTIFICATION_LISTENER")) android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null) else null
        }
        ins.addMonitor(accessMonitor)
        try {
            ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
            launcher = ins.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
            settle()
            ins.runOnMainSync {
                assertNotNull(launcher!!.uiManager)
                originalTerminal = launcher!!.uiManager!!.snapshotTerminalState()
                launcher!!.uiManager!!.setOutput("LIVE-PREVIEW-PROOF", ohi.andre.consolelauncher.managers.TerminalManager.CATEGORY_OUTPUT)
                launcher!!.uiManager!!.setInput("draft command")
            }
            settle()
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            var batteryReady = false
            while (!batteryReady && android.os.SystemClock.uptimeMillis() < deadline) {
                ins.runOnMainSync { batteryReady = launcher!!.findViewById<TextView>(R.id.tv0).text.toString().startsWith("[") }
                if (!batteryReady) Thread.sleep(100)
            }
            capture("launcher")
            activity = open(XMLPrefsManager.XMLPrefsRoot.UI)
            ins.runOnMainSync {
                val actualBattery = launcher!!.findViewById<TextView>(R.id.tv0)
                val previewBattery = pager(activity!!).findViewById<TextView>(R.id.tv0)
                assertTrue(actualBattery.text.toString().startsWith("["))
                assertEquals(actualBattery.text.toString(), previewBattery.text.toString())
                assertEquals(actualBattery.textSize, previewBattery.textSize, .01f)
                assertEquals(actualBattery.gravity, previewBattery.gravity)
                fun pixels(view: TextView): Int {
                    val drawable = view.background
                    val bounds = android.graphics.Rect(drawable.bounds)
                    val bitmap = Bitmap.createBitmap(320, 100, Bitmap.Config.ARGB_8888)
                    try {
                        drawable.setBounds(0, 0, 320, 100); drawable.draw(android.graphics.Canvas(bitmap))
                        val pixels = IntArray(32000); bitmap.getPixels(pixels, 0, 320, 0, 0, 320, 100)
                        return pixels.contentHashCode()
                    } finally { drawable.bounds = bounds; bitmap.recycle() }
                }
                assertEquals("Launcher and preview must draw the same battery border", pixels(actualBattery), pixels(previewBattery))
            }
            capture("status-parity")
            ins.runOnMainSync { pager(activity!!).scrollToPosition(1) }; settle()
            ins.runOnMainSync {
                val shown = pager(activity!!).findViewById<TextView>(R.id.terminal_view)
                assertTrue(shown.text.toString().contains("LIVE-PREVIEW-PROOF"))
                assertTrue(views(pager(activity!!)).filterIsInstance<TextView>().any { it.text.contains("draft command") })
                assertFalse(shown.isClickable); assertNull(shown.movementMethod)
            }
            capture("terminal")
            ins.runOnMainSync { pager(activity!!).scrollToPosition(2) }; settle()
            ins.runOnMainSync {
                assertNotNull(pager(activity!!).findViewById<View>(R.id.music_module))
                assertFalse(pager(activity!!).findViewById<View>(R.id.music_play_pause).isClickable)
            }
            capture("modules")
            val moduleColor = LauncherSettings.get(Theme.module_panel_text_color)
            ins.runOnMainSync {
                val ui = launcher!!.uiManager!!
                val result = ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine.RenderResult().apply {
                    title = "Live Lua layout"
                    layoutJson = """{"type":"column","children":[{"type":"text","text":"Live count: 7"},{"type":"progress","label":"Focus","value":0.5},{"type":"button","label":"Run","command":"clear"}]}"""
                }
                UIManager::class.java.getDeclaredField("activeModule").apply { isAccessible = true }.set(ui, "preview_fixture")
                UIManager::class.java.getDeclaredMethod("showLuaWidgetModule", String::class.java, String::class.java,
                    ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine.RenderResult::class.java).apply { isAccessible = true }
                    .invoke(ui, "preview_fixture", "preview_fixture", result)
            }
            drafts(activity!!, mapOf(Theme.module_panel_text_color to "#FFABCDEF"))
            ins.runOnMainSync {
                val rendered = views(pager(activity!!)).filterIsInstance<TextView>()
                val body = rendered.first { it.text.toString() == "Live count: 7" }
                assertEquals(0xFFABCDEF.toInt(), body.currentTextColor)
                val run = rendered.first { it.text.toString() == "Run" }
                assertFalse(run.isClickable); assertFalse(run.performClick())
                assertEquals(moduleColor, LauncherSettings.get(Theme.module_panel_text_color))
                assertTrue(launcher!!.uiManager!!.snapshotTerminalState()!!.output.contains("LIVE-PREVIEW-PROOF"))
            }
            ins.runOnMainSync {
                views(pager(activity!!)).filterIsInstance<android.widget.ScrollView>().first().fullScroll(View.FOCUS_DOWN)
            }
            settle()
            capture("lua-draft")
        } finally {
            ins.runOnMainSync { activity?.finish(); launcher?.uiManager?.restoreTerminalState(originalTerminal); launcher?.finish() }
            ins.removeMonitor(accessMonitor)
            startup.edit().clear().apply { startupValues.forEach { (key, value) -> when (value) { is Boolean -> putBoolean(key, value); is Int -> putInt(key, value); is Long -> putLong(key, value); is String -> putString(key, value) } } }.commit()
        }
    }
    @Test fun readingNotesForPreviewDoesNotCreateDatabaseOrChangeSorting() {
        init()
        val folder = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "preview-notes-").toFile()
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir(): File = folder }
        val originalSorting = ohi.andre.consolelauncher.managers.NotesManager.Note.sorting
        try {
            ins.runOnMainSync {
                listOf("", "[", ";").forEach { separator ->
                    val values = AppearanceValues { when (it) {
                        Behavior.notes_sorting -> "2"
                        Behavior.optional_values_separator -> separator
                        else -> LauncherSettings.get(it)
                    } }
                    ohi.andre.consolelauncher.managers.NotesManager(isolated, null, values, readOnly = true).getNotes()
                }
                assertFalse(File(folder, "remember-notes.xml").exists())
                assertEquals(originalSorting, ohi.andre.consolelauncher.managers.NotesManager.Note.sorting)
            }
        } finally { folder.deleteRecursively() }
    }

}
