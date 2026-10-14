package ohi.andre.consolelauncher.managers.settings

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.*
import ohi.andre.consolelauncher.managers.settings.*
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SettingsSaveReliabilityTest {
    private val i get() = InstrumentationRegistry.getInstrumentation()
    private val c get() = i.targetContext
    private fun main(run: () -> Unit) = i.runOnMainSync(run)
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun reload() { XMLPrefsManager.dispose(); XMLPrefsManager.loadCommons(c); LauncherSettings.refreshFromLoadedPrefs(); ThemeColorResolver.clearPreview() }
    private fun fixture(run: () -> Unit) {
        check(c.packageName.endsWith(".palettedebug"))
        Tuils.init(c); reload()
        val old = Tuils.getFolder()
        val temporary = File(c.filesDir, "settings-audit-fixture").apply { deleteRecursively(); mkdirs() }
        old.listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }.forEach { it.copyTo(File(temporary, it.name)) }
        val field = Tuils::class.java.getDeclaredField("folder").apply { isAccessible = true }
        field.set(null, temporary)
        val clearFrames = ohi.andre.consolelauncher.tuils.FrameManager::class.java.getDeclaredMethod("clearCache").apply { isAccessible = true }
        clearFrames.invoke(ohi.andre.consolelauncher.tuils.FrameManager)
        try { reload(); run() } finally {
            temporary.walkTopDown().forEach { it.setWritable(true, true) }
            field.set(null, old); clearFrames.invoke(ohi.andre.consolelauncher.tuils.FrameManager); reload(); temporary.deleteRecursively()
        }
    }
    private fun adapter(item: XMLPrefsSave): Pair<TuixtAdapter, EditText> {
        val a = TuixtAdapter(mutableListOf(TuixtAdapter.SettingsRow.setting(item, "Audit")), null)
        val holder = a.onCreateViewHolder(RecyclerView(c).apply { layoutManager = androidx.recyclerview.widget.LinearLayoutManager(c) }, a.getItemViewType(0))
        a.onBindViewHolder(holder, 0)
        return a to holder.itemView.findViewById(R.id.setting_input)
    }
    private fun openAscii() = i.startActivitySync(Intent(c, TuixtActivity::class.java).putExtra(TuixtActivity.MODE, TuixtActivity.MODE_ASCII_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity
    private fun recreate(activity: android.app.Activity): android.app.Activity {
        val monitor = Instrumentation.ActivityMonitor(activity.javaClass.name, null, false)
        i.addMonitor(monitor)
        return try { main { activity.recreate() }; checkNotNull(i.waitForMonitorWithTimeout(monitor, 5000)).also { i.waitForIdleSync() } }
        finally { i.removeMonitor(monitor) }
    }
    @Test fun failedAtomicWriteKeepsFileCacheAndDraft() = fixture {
        val file = File(Tuils.getFolder(), "ui.xml")
        val before = file.readBytes()
        val original = LauncherSettings.get(Ui.input_output_size)
        val folder = Tuils.getFolder()
        var editor: Pair<TuixtAdapter, EditText>? = null
        main { editor = adapter(Ui.input_output_size) }
        assertTrue(folder.setWritable(false, false))
        try {
            main {
                val (a, input) = checkNotNull(editor)
                val edit = if (original == "23") "24" else "23"
                input.setText(edit)
                var failed = false
                try { a.saveAll(c) } catch (_: Exception) { failed = true }
                assertTrue("Write failure must reach the editor", failed)
                assertTrue("The edit must remain retryable", a.hasPendingChanges())
                assertEquals(original, LauncherSettings.get(Ui.input_output_size))
                assertArrayEquals(before, file.readBytes())
            }
        } finally { folder.setWritable(true, true) }
    }
    @Test fun dialogUsesItsOwnReadableTextAndHeaderColors() = fixture {
        LauncherSettings.set(Theme.settings_panel_background_color, "#FF000000")
        LauncherSettings.set(Theme.settings_panel_text_color, "#FFFFFFFF")
        LauncherSettings.set(Theme.dialog_panel_background_color, "#FFFFFFFF")
        LauncherSettings.set(Theme.dialog_panel_text_color, "#FF000000")
        LauncherSettings.set(Theme.dialog_header_background_color, "#FFFFFFFF")
        LauncherSettings.set(Theme.dialog_header_text_color, "#FF000000")
        val activity = i.startActivitySync(ThemerActivity.launchIntent(c, ThemerActivity.SECTION_HOME)) as ThemerActivity
        try {
            main { TuixtDialog.showConfirm(activity, "Audit dialog", "Audit message", "OK", "Cancel", TuixtDialog.ConfirmAction {}) }
            i.waitForIdleSync()
            main {
                val texts = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap(::views).filterIsInstance<TextView>()
                assertEquals(android.graphics.Color.BLACK, texts.first { it.text.toString() == "Audit message" }.currentTextColor)
                assertEquals(android.graphics.Color.BLACK, texts.first { it.text.toString() == "AUDIT DIALOG" }.currentTextColor)
                texts.first { it.text.toString() == "CANCEL" }.performClick()
            }
        } finally { main { activity.finish() } }
    }
    @Test fun previewUsesTheLauncherFontCalculationAtMinimumOffset() = fixture {
        LauncherSettings.set(Ui.font_size_offset, "-8")
        LauncherSettings.set(Ui.input_output_size, "14")
        val activity = i.startActivitySync(Intent(c, TuixtActivity::class.java).putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), "ui.xml").absolutePath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity
        try {
            val preview = TuixtActivity::class.java.getDeclaredField("appearancePreview").apply { isAccessible = true }.get(activity)!!
            val sample = preview.javaClass.getDeclaredMethod("sample", String::class.java, Theme::class.java, Ui::class.java).apply { isAccessible = true }
            main {
                val text = sample.invoke(preview, "font sample", Theme.output_text_color, Ui.input_output_size) as TextView
                assertEquals(6f, text.textSize / c.resources.displayMetrics.scaledDensity, .01f)
                assertEquals(-8, ohi.andre.consolelauncher.tuils.LauncherFontScale.offsetSp())
            }
        } finally { main { activity.finish() } }
    }
}
