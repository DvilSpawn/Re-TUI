package ohi.andre.consolelauncher.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtAdapter
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AnsiPaletteVisibilityTest {
    @Test fun hidingPalettePreservesSavedAndAutoColors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            XMLPrefsManager.loadCommons(context)
            LauncherSettings.refreshFromLoadedPrefs()
            val original = listOf(Behavior.show_tmux_workspace_button, Theme.ansi_red, Theme.ansi_white)
                .associateWith { LauncherSettings.get(it) }
            val source = File.createTempFile("ansi-theme", ".xml", context.cacheDir)
            val buildRows = TuixtActivity::class.java.getDeclaredMethod("buildRows", XMLPrefsRoot::class.java, File::class.java)
                .apply { isAccessible = true }
            try {
                LauncherSettings.set(Theme.ansi_red, "#FF123456")
                LauncherSettings.set(Theme.ansi_white, "auto")
                val expectedWhite = ThemeColorResolver.color(Theme.output_text_color)
                // Exercise both legacy XML and the section-comment format.
                for (section in listOf("", "<!-- #Advanced — ANSI Palette -->")) {
                    source.writeText("<theme>$section<ansi_red value=\"#FF123456\"/><ansi_white value=\"auto\"/></theme>")
                    for (enabled in listOf(false, true, false)) {
                        LauncherSettings.set(Behavior.show_tmux_workspace_button, enabled.toString())
                        @Suppress("UNCHECKED_CAST")
                        val rows = buildRows.invoke(TuixtActivity(), XMLPrefsRoot.THEME, source) as MutableList<TuixtAdapter.SettingsRow>
                        assertEquals(if (enabled) 16 else 0, rows.count { it.item is Theme && it.item!!.label()!!.startsWith("ansi_") })
                        if (!enabled) assertFalse(rows.any { it.section == "Advanced — ANSI Palette" })
                        TuixtAdapter(rows, source).saveAll(context)
                        XMLPrefsManager.dispose()
                        XMLPrefsManager.loadCommons(context)
                        LauncherSettings.refreshFromLoadedPrefs()
                        assertEquals("#FF123456", LauncherSettings.get(Theme.ansi_red))
                        assertEquals("auto", LauncherSettings.get(Theme.ansi_white))
                        assertEquals(0xFF123456.toInt(), ThemeColorResolver.ansi(1, false))
                        assertEquals(expectedWhite, ThemeColorResolver.ansi(7, false))
                    }
                }
            } finally {
                original.forEach { (setting, value) -> LauncherSettings.set(setting, value) }
                source.delete()
            }
        }
    }
}
