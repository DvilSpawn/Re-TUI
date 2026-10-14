package ohi.andre.consolelauncher.managers.settings

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtAdapter
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsPreviewJourneyTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun all(activity: TuixtActivity) = views(activity.window.decorView)
    private fun settings(activity: TuixtActivity) = all(activity).filterIsInstance<RecyclerView>().first { it.adapter is TuixtAdapter }
    private fun adapter(activity: TuixtActivity) = settings(activity).adapter as TuixtAdapter
    private fun pager(activity: TuixtActivity) = all(activity).filterIsInstance<RecyclerView>().first { it.adapter !is TuixtAdapter }
    private fun source(root: XMLPrefsManager.XMLPrefsRoot) = File(Tuils.getFolder(), root.path())
    private fun open(root: XMLPrefsManager.XMLPrefsRoot): TuixtActivity {
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        return (instrumentation.startActivitySync(Intent(context, TuixtActivity::class.java)
            .putExtra(TuixtActivity.PATH, source(root).absolutePath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity)
            .also { instrumentation.waitForIdleSync(); settle() }
    }
    private fun settle() { Thread.sleep(160); instrumentation.waitForIdleSync() }
    private fun search(activity: TuixtActivity, label: String) {
        instrumentation.runOnMainSync {
            all(activity).filterIsInstance<EditText>().first { it.hint == context.getString(R.string.editor_tuixtactivity_search_settings_d25ad) }.setText(label)
        }
        instrumentation.waitForIdleSync()
    }
    private fun edit(activity: TuixtActivity, value: String) {
        instrumentation.runOnMainSync { settings(activity).findViewById<EditText>(R.id.setting_input).setText(value) }
        settle()
    }
    private fun click(activity: TuixtActivity, label: Int) {
        instrumentation.runOnMainSync { all(activity).filterIsInstance<TextView>().first { it.text == context.getString(label) }.performClick() }
        instrumentation.waitForIdleSync()
    }
    private fun capture(name: String) {
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.getExternalFilesDir(null), "settings-refined-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
    private fun swipe(activity: TuixtActivity, forward: Boolean) {
        val rect = Rect()
        instrumentation.runOnMainSync { pager(activity).getGlobalVisibleRect(rect) }
        val start = rect.left + rect.width() * if (forward) .85f else .15f
        val end = rect.left + rect.width() * if (forward) .15f else .85f
        val y = rect.exactCenterY()
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float) {
            MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).also {
                instrumentation.uiAutomation.injectInputEvent(it, true); it.recycle()
            }
        }
        event(MotionEvent.ACTION_DOWN, start)
        for (step in 1..12) { Thread.sleep(20); event(MotionEvent.ACTION_MOVE, start + (end - start) * step / 12) }
        event(MotionEvent.ACTION_UP, end)
        Thread.sleep(500)
        instrumentation.waitForIdleSync()
    }
    private fun fixture(values: Map<XMLPrefsSave, String>, run: () -> Unit) {
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val original = values.keys.associateWith { LauncherSettings.get(it) }
        try { values.forEach { (key, value) -> LauncherSettings.set(key, value) }; run() }
        finally { instrumentation.runOnMainSync { original.forEach { (key, value) -> LauncherSettings.set(key, value) } } }
    }

    @Test fun realSwipesAndRecreationKeepDraftAndPage() = fixture(mapOf(
        Ui.enable_dashed_border to "true", Ui.dashed_border_stroke_width to "1.5", Behavior.enable_cyberdeck_mode to "false"
    )) {
        var activity = open(XMLPrefsManager.XMLPrefsRoot.UI)
        val before = source(XMLPrefsManager.XMLPrefsRoot.UI).readBytes()
        try {
            search(activity, "dashed_border_stroke_width"); edit(activity, "2.5")
            swipe(activity, true)
            instrumentation.runOnMainSync {
                assertEquals(1, (pager(activity).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
                assertTrue(all(activity).filterIsInstance<TextView>().any { it.text.contains("2 / 4") })
            }
            val monitor = Instrumentation.ActivityMonitor(TuixtActivity::class.java.name, null, false)
            instrumentation.addMonitor(monitor)
            try {
                instrumentation.runOnMainSync { activity.recreate() }
                activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? TuixtActivity ?: error("Recreated editor was not observed")
                settle()
            } finally { instrumentation.removeMonitor(monitor) }
            instrumentation.runOnMainSync {
                assertEquals("2.5", adapter(activity).draftValue(Ui.dashed_border_stroke_width))
                assertEquals(1, (pager(activity).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
            }
            capture("terminal")
            swipe(activity, true); capture("modules")
            swipe(activity, true); capture("ansi")
            swipe(activity, false)
            instrumentation.runOnMainSync {
                assertEquals(2, (pager(activity).layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
            }
            assertArrayEquals(before, source(XMLPrefsManager.XMLPrefsRoot.UI).readBytes())
            assertEquals("1.5", LauncherSettings.get(Ui.dashed_border_stroke_width))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun ansiDraftRendersAndCancelDiscardsIt() = fixture(mapOf(
        Behavior.show_tmux_workspace_button to "true", Ui.enable_dashed_border to "true", Ui.dashed_border_stroke_width to "1.5",
        Behavior.enable_cyberdeck_mode to "false", Theme.ansi_red to "#FFFF0000"
    )) {
        val activity = open(XMLPrefsManager.XMLPrefsRoot.THEME)
        val before = source(XMLPrefsManager.XMLPrefsRoot.THEME).readBytes()
        try {
            search(activity, "ansi_red"); edit(activity, "#FF0077CC")
            instrumentation.runOnMainSync { pager(activity).scrollToPosition(3) }
            settle()
            instrumentation.runOnMainSync {
                val sample = all(activity).filterIsInstance<TextView>().first { it.text.contains("ls --color") }.text as Spanned
                val at = sample.toString().indexOf("ERROR")
                assertTrue(sample.getSpans(at, at + 5, ForegroundColorSpan::class.java).any { it.foregroundColor == 0xFF0077CC.toInt() })
                assertFalse(ThemeColorResolver.hasPreview())
            }
            capture("ansi-draft")
            assertEquals("#FFFF0000", LauncherSettings.get(Theme.ansi_red))
            assertArrayEquals(before, source(XMLPrefsManager.XMLPrefsRoot.THEME).readBytes())
            click(activity, R.string.editor_tuixtactivity_cancel_1507c)
            val discard = instrumentation.uiAutomation.rootInActiveWindow
                .findAccessibilityNodeInfosByText(context.getString(R.string.editor_tuixtactivity_discard_36fff))
                .firstOrNull { it.isClickable } ?: error("Discard confirmation missing")
            assertTrue(discard.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            settle()
            assertTrue(activity.isFinishing || activity.isDestroyed)
            assertArrayEquals(before, source(XMLPrefsManager.XMLPrefsRoot.THEME).readBytes())
            assertEquals("#FFFF0000", LauncherSettings.get(Theme.ansi_red))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun filteredStatusSaveMatchesDraftOrder() = fixture(StatusRowResolver.settings.associateWith<Ui, String> { it.defaultValue()!! } + mapOf<XMLPrefsSave, String>(
        Ui.show_ram to "true", Ui.show_time to "true", Ui.show_battery to "true"
    )) {
        val activity = open(XMLPrefsManager.XMLPrefsRoot.UI)
        try {
            search(activity, "time_index"); edit(activity, "1")
            fun statusOrder() = views(pager(activity)).filterIsInstance<TextView>().map { it.text.toString() }
            var before: List<String> = emptyList()
            instrumentation.runOnMainSync { before = statusOrder() }
            capture("status")
            click(activity, R.string.editor_tuixtactivity_save_50815)
            settle()
            instrumentation.runOnMainSync { assertEquals(before, statusOrder()) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun keyboardKeepsPreviewAndSaveVisible() = fixture(mapOf(Ui.enable_dashed_border to "true")) {
        val activity = open(XMLPrefsManager.XMLPrefsRoot.UI)
        try {
            search(activity, "dashed_border_stroke_width")
            var originalHeight = 0
            instrumentation.runOnMainSync {
                originalHeight = pager(activity).height
                val input = settings(activity).findViewById<EditText>(R.id.setting_input)
                input.requestFocus()
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            Thread.sleep(700); instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue("Keyboard must be visible for this check", activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime()))
                assertTrue("Preview should compact when keyboard opens", pager(activity).height < originalHeight)
                assertTrue("Settings list must retain editing space", settings(activity).height > 0)
                val save = all(activity).filterIsInstance<TextView>().first { it.text == context.getString(R.string.editor_tuixtactivity_save_50815) }
                assertTrue(save.getGlobalVisibleRect(Rect()))
            }
            capture("keyboard")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun cornerRadiusPreviewMatchesInheritedRadiusAfterSave() = fixture(mapOf(
        Ui.enable_dashed_border to "true", Ui.dashed_border_corner_radius to "16", Ui.module_corner_radius to "8",
        Behavior.enable_cyberdeck_mode to "false"
    )) {
        val activity = open(XMLPrefsManager.XMLPrefsRoot.UI)
        try {
            search(activity, "module_corner_radius"); edit(activity, "0")
            val previewField = TuixtActivity::class.java.getDeclaredField("appearancePreview").apply { isAccessible = true }
            val preview = previewField.get(activity)!!
            val radius = preview.javaClass.getDeclaredMethod("radius", Ui::class.java).apply { isAccessible = true }
            instrumentation.runOnMainSync { assertEquals(16, radius.invoke(preview, Ui.module_corner_radius)) }
            click(activity, R.string.editor_tuixtactivity_save_50815); settle()
            instrumentation.runOnMainSync {
                assertEquals(AppearanceSettings.moduleCornerRadius(), radius.invoke(preview, Ui.module_corner_radius))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

}
