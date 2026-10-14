package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtAdapter
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsPreviewIntegrationTest {
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun editsRenderLocallyAndOnlySaveWritesSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val fixture = listOf(Ui.enable_dashed_border, Ui.dashed_border_stroke_width, Behavior.enable_cyberdeck_mode)
        val original = fixture.associateWith { LauncherSettings.get(it) }
        fixture.zip(listOf("true", "1.5", "false")).forEach { (item, value) -> LauncherSettings.set(item, value) }
        val source = File(Tuils.getFolder(), "UI.xml")
        val before = source.readBytes()
        val intent = Intent(context, TuixtActivity::class.java).putExtra(TuixtActivity.PATH, source.absolutePath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as TuixtActivity
        fun all() = views(activity.window.decorView)
        fun settings() = all().filterIsInstance<RecyclerView>().first { it.adapter is TuixtAdapter }
        fun adapter() = settings().adapter as TuixtAdapter
        fun preview() = all().filterIsInstance<RecyclerView>().first { it.adapter !is TuixtAdapter }
        fun pixels(): Int {
            val view = preview()
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
            return pixels.contentHashCode()
        }
        try {
            instrumentation.waitForIdleSync()
            var oldPixels = 0
            instrumentation.runOnMainSync {
                oldPixels = pixels()
                all().filterIsInstance<EditText>().first { it.hint == context.getString(R.string.editor_tuixtactivity_search_settings_d25ad) }
                    .setText("dashed_border_stroke_width")
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                settings().findViewById<EditText>(R.id.setting_input).setText("7")
                assertEquals("7", adapter().draftValue(Ui.dashed_border_stroke_width))
                assertEquals("1.5", LauncherSettings.get(Ui.dashed_border_stroke_width))
                assertFalse(ThemeColorResolver.hasPreview())
                assertArrayEquals(before, source.readBytes())
                val recovered = TuixtAdapter(mutableListOf(TuixtAdapter.SettingsRow.setting(Ui.dashed_border_stroke_width, "Border")), source)
                recovered.restoreDraftState(adapter().saveDraftState())
                assertEquals("7", recovered.draftValue(Ui.dashed_border_stroke_width))
            }
            Thread.sleep(180)
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null), "settings-preview-status.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            instrumentation.runOnMainSync {
                assertNotEquals("Draft border must change the rendered preview", oldPixels, pixels())
                all().filterIsInstance<TextView>().first { it.text == context.getString(R.string.editor_tuixtactivity_save_50815) }.performClick()
                assertEquals("7", LauncherSettings.get(Ui.dashed_border_stroke_width))
                assertFalse(adapter().hasPendingChanges())
            }
            assertFalse(before.contentEquals(source.readBytes()))
            for ((page, name) in listOf(1 to "terminal", 2 to "modules", 3 to "ansi")) {
                instrumentation.runOnMainSync {
                    preview().smoothScrollToPosition(page)
                }
                Thread.sleep(500)
                instrumentation.waitForIdleSync()
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    File(context.getExternalFilesDir(null), "settings-preview-$name.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
            instrumentation.runOnMainSync {
                settings().findViewById<EditText>(R.id.setting_input).setText("2")
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            assertEquals("7", LauncherSettings.get(Ui.dashed_border_stroke_width))
        } finally {
            instrumentation.runOnMainSync { activity.finish(); original.forEach { (item, value) -> LauncherSettings.set(item, value) } }
        }
    }

    @Test fun themeEditsDoNotPopulateLauncherPreviewCache() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        instrumentation.runOnMainSync {
            val source = File(Tuils.getFolder(), "theme.xml")
            val original = LauncherSettings.get(Theme.terminal_border_color)
            val adapter = TuixtAdapter(mutableListOf(TuixtAdapter.SettingsRow.setting(Theme.terminal_border_color, "Color")), source)
            val recycler = RecyclerView(context).apply { layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context) }
            val holder = adapter.onCreateViewHolder(recycler, adapter.getItemViewType(0))
            adapter.onBindViewHolder(holder, 0)
            holder.itemView.findViewById<EditText>(R.id.setting_input).setText("#FF123456")
            assertEquals("#FF123456", adapter.draftValue(Theme.terminal_border_color))
            assertEquals(original, LauncherSettings.get(Theme.terminal_border_color))
            assertFalse(ThemeColorResolver.hasPreview())
            holder.itemView.findViewById<EditText>(R.id.setting_input).setText("#bad-color")
            assertEquals("#bad-color", adapter.draftValue(Theme.terminal_border_color))
            org.junit.Assert.assertNotNull(holder.itemView.findViewById<EditText>(R.id.setting_input).error)
            assertEquals(original, LauncherSettings.get(Theme.terminal_border_color))
            assertFalse(ThemeColorResolver.hasPreview())
        }
    }
}
