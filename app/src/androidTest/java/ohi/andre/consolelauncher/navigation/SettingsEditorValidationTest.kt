package ohi.andre.consolelauncher.navigation

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtAdapter
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SettingsEditorValidationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(run: () -> Unit) = instrumentation.runOnMainSync(run)
    private fun reload() {
        XMLPrefsManager.dispose()
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        ThemeColorResolver.clearPreview()
    }
    private fun fixture(run: () -> Unit) {
        check(context.packageName.endsWith(".palettedebug"))
        Tuils.init(context)
        reload()
        val original = Tuils.getFolder()
        val temporary = File(context.filesDir, "settings-editor-validation-fixture").apply { deleteRecursively(); mkdirs() }
        original.listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }
            .forEach { it.copyTo(File(temporary, it.name)) }
        val folder = Tuils::class.java.getDeclaredField("folder").apply { isAccessible = true }
        folder.set(null, temporary)
        try { reload(); run() } finally {
            folder.set(null, original)
            reload()
            temporary.deleteRecursively()
        }
    }
    private fun editor(item: XMLPrefsSave): Pair<TuixtAdapter, EditText> {
        val adapter = TuixtAdapter(mutableListOf(TuixtAdapter.SettingsRow.setting(item, "Settings")), null)
        val parent = RecyclerView(context).apply { layoutManager = LinearLayoutManager(context) }
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        return adapter to holder.itemView.findViewById(R.id.setting_input)
    }
    private fun assertRejected(adapter: TuixtAdapter) {
        try { adapter.saveAll(context); fail("Invalid input must block Save") }
        catch (_: IllegalArgumentException) { assertTrue(adapter.hasPendingChanges()) }
    }
    @Test fun invalidNumbersAndColorsKeepDraftAndDiskUntilCorrected() = fixture {
        main {
            val ui = File(Tuils.getFolder(), "ui.xml")
            val theme = File(Tuils.getFolder(), "theme.xml")
            val uiBefore = ui.readBytes()
            val themeBefore = theme.readBytes()
            val (numbers, numberInput) = editor(Ui.input_output_size)
            numberInput.setText("0")
            assertNotNull(numberInput.error)
            assertRejected(numbers)
            assertEquals("0", numbers.draftValue(Ui.input_output_size))
            assertArrayEquals(uiBefore, ui.readBytes())
            numberInput.setText("23")
            assertNull(numberInput.error)
            numbers.saveAll(context)
            assertEquals("23", LauncherSettings.get(Ui.input_output_size))
            assertFalse(numbers.hasPendingChanges())

            val (colors, colorInput) = editor(Theme.output_text_color)
            colorInput.setText("#123")
            assertNotNull(colorInput.error)
            assertRejected(colors)
            assertEquals("#123", colors.draftValue(Theme.output_text_color))
            assertArrayEquals(themeBefore, theme.readBytes())
            val recovered = TuixtAdapter(mutableListOf(TuixtAdapter.SettingsRow.setting(Theme.output_text_color, "Colors")), null)
            recovered.restoreDraftState(colors.saveDraftState())
            assertEquals("#123", recovered.draftValue(Theme.output_text_color))
        }
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun recreate(activity: Activity): TuixtActivity {
        val monitor = Instrumentation.ActivityMonitor(activity.javaClass.name, null, false)
        instrumentation.addMonitor(monitor)
        return try {
            main { activity.recreate() }
            (checkNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 5000)) as TuixtActivity)
                .also { instrumentation.waitForIdleSync() }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun asciiDraftSurvivesActivityRecreation() = fixture {
        var activity = instrumentation.startActivitySync(Intent(context, TuixtActivity::class.java)
            .putExtra(TuixtActivity.MODE, TuixtActivity.MODE_ASCII_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity
        fun adapter() = views(activity.window.decorView).filterIsInstance<RecyclerView>().first().adapter as TuixtAdapter
        try {
            main { adapter().restoreDraftState(Bundle().apply { putString("ui.xml:ascii_pane_height_rows", "17") }) }
            activity = recreate(activity)
            main {
                assertEquals("17", adapter().draftValue(Ui.ascii_pane_height_rows))
                assertTrue(adapter().hasPendingChanges())
            }
        } finally { main { activity.finish() } }
    }
    @Test fun booleanControlExposesLabelAndChangingSwitchState() = fixture {
        val activity = instrumentation.startActivitySync(Intent(context, TuixtActivity::class.java)
            .putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), "ui.xml").absolutePath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity
        try {
            main { views(activity.window.decorView).filterIsInstance<EditText>()
                .first { it.hint == context.getString(R.string.editor_tuixtactivity_search_settings_d25ad) }
                .setText("show_enter_button") }
            instrumentation.waitForIdleSync()
            fun node(): AccessibilityNodeInfo {
                val deadline = android.os.SystemClock.uptimeMillis() + 3000
                do {
                    instrumentation.uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText("show_enter_button")?.firstOrNull { it.isCheckable }?.let { return it }
                    Thread.sleep(50)
                } while (android.os.SystemClock.uptimeMillis() < deadline)
                error("The switch accessibility node was not available")
            }
            val before = node()
            assertEquals("show_enter_button", before.contentDescription.toString())
            assertNotNull(before.labeledBy)
            assertEquals("android.widget.Switch", before.className.toString())
            val checked = before.isChecked
            main { activity.findViewById<TextView>(R.id.setting_switch).performClick() }
            instrumentation.waitForIdleSync()
            assertEquals(!checked, node().isChecked)
        } finally { main { activity.finish() } }
    }
}
