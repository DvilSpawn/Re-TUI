package ohi.andre.consolelauncher.navigation

import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.commands.tuixt.TuixtAdapter
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.StatusRowResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusOrderingTest {
    @Test fun commandAndSettingsEditorPersistTheSameInsertedRow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            XMLPrefsManager.loadCommons(context)
            LauncherSettings.refreshFromLoadedPrefs()
            val original = StatusRowResolver.settings.associateWith { LauncherSettings.get(it) }
            val baseline = StatusRowResolver.settings.associateWith { it.defaultValue() }.toMutableMap().apply {
                put(Ui.time_index, "1")
                put(Ui.notes_index, "1.1")
            }
            fun reload() {
                XMLPrefsManager.dispose()
                XMLPrefsManager.loadCommons(context)
                LauncherSettings.refreshFromLoadedPrefs()
            }
            try {
                for (useEditor in listOf(false, true)) {
                    baseline.forEach { (setting, value) -> LauncherSettings.set(setting, value) }
                    if (useEditor) {
                        val adapter = TuixtAdapter(mutableListOf(
                            TuixtAdapter.SettingsRow.setting(Ui.ascii_index, "Layout")
                        ), null)
                        val holder = adapter.onCreateViewHolder(FrameLayout(context), adapter.getItemViewType(0))
                        adapter.onBindViewHolder(holder, 0)
                        (holder as TuixtAdapter.ViewHolder).input.setText("1")
                        adapter.saveAll(context)
                        // A subsequent Save must not insert the same row again.
                        adapter.saveAll(context)
                    } else {
                        LauncherSettings.setStatusIndex(context, Ui.ascii_index, "1")
                    }
                    reload()
                    assertEquals("1", LauncherSettings.get(Ui.ascii_index))
                    assertEquals("2", LauncherSettings.get(Ui.time_index))
                    assertEquals("2.1", LauncherSettings.get(Ui.notes_index))
                    assertEquals("5", LauncherSettings.get(Ui.storage_index))
                }
            } finally {
                original.forEach { (setting, value) -> LauncherSettings.set(setting, value) }
                reload()
            }
        }
    }
}
