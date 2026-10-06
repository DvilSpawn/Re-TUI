package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.os.Build
import android.widget.TextView
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.dvil.retui.contract.RetuiVisualContract
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import ohi.andre.consolelauncher.calculator.CalculatorActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.xml.AutoColorManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AutoThemeIntegrationTest {
    @Test fun calculatorControlsIgnoreAnsiAndRespectWorkspaceOverrides() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val settings = listOf(Theme.ansi_green, Theme.workspace_control_text_color)
        val saved = settings.associateWith { LauncherSettings.get(it) }
        var activity: CalculatorActivity? = null
        try {
            LauncherSettings.set(context, Theme.ansi_green, "#FFCC00DD")
            LauncherSettings.set(context, Theme.workspace_control_text_color, "#FF445566")
            activity = instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
            instrumentation.runOnMainSync {
                assertEquals(0xFF445566.toInt(), activity!!.findViewById<TextView>(R.id.calculator_numbers_tab).currentTextColor)
                val symbols = activity!!.findViewById<ViewGroup>(R.id.calculator_symbols)
                assertEquals(0xFF445566.toInt(), (symbols.getChildAt(0) as TextView).currentTextColor)
                activity!!.findViewById<TextView>(R.id.calculator_functions_tab).performClick()
                assertEquals(0xFF445566.toInt(), activity!!.findViewById<TextView>(R.id.calculator_functions_tab).currentTextColor)
                assertEquals(0xFF445566.toInt(), (symbols.getChildAt(0) as TextView).currentTextColor)
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            saved.forEach { (setting, value) -> LauncherSettings.set(context, setting, value) }
        }
    }

    @Test fun actualDeviceExtractionProducesAPalette() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = AutoColorManager.currentWallpaperColorSnapshot(context)
        assertTrue("${result.source}: ${result.failureReason}", result.succeeded)
        assertTrue(result.source in listOf(AutoColorManager.Source.WALLPAPER, AutoColorManager.Source.SYSTEM))
    }
    @Test fun fallbackPaletteAppliesAndExplicitOverridesSurviveReload() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val settings = Theme.entries + Suggestions.entries + listOf(Ui.system_wallpaper, Ui.auto_color_pick)
        val saved = settings.associateWith { LauncherSettings.get(it) }
        try {
            LauncherSettings.set(context, Theme.widget_row_background_color, "auto")
            LauncherSettings.set(context, Theme.button_background_color, "#80112233")
            LauncherSettings.set(context, Theme.workspace_panel_text_color, "#FF445566")
            LauncherSettings.set(context, Theme.module_text_color, "#FF55AA66")
            LauncherSettings.set(context, Theme.ansi_black, "#FFCC00DD")
            val seeds = AutoColorManager.selectSeedColors(Build.VERSION.SDK_INT, { null }, {
                intArrayOf(context.getColor(android.R.color.system_accent1_500),
                    context.getColor(android.R.color.system_accent2_500), context.getColor(android.R.color.system_neutral1_900))
            })
            val palette = AutoColorManager.snapshotFromSeeds(seeds)
            assertEquals(AutoColorManager.Source.SYSTEM, palette.source)
            assertTrue(palette.succeeded)
            assertTrue(palette.colors.keys.none { it is Theme && it.advanced })
            assertTrue(palette.message(context).contains("system palette"))
            val wallpaper = AutoColorManager.snapshotFromSeeds(seeds.copy(source = AutoColorManager.Source.WALLPAPER, failureReason = null))
            assertEquals(palette.colors, wallpaper.colors)
            LauncherSettings.applyWallpaperSnapshot(context, palette)
            XMLPrefsManager.dispose()
            XMLPrefsManager.loadCommons(context)
            LauncherSettings.refreshFromLoadedPrefs()
            assertEquals("#80112233", LauncherSettings.get(Theme.button_background_color))
            assertEquals("#FF445566", LauncherSettings.get(Theme.workspace_panel_text_color))
            assertEquals("#FFCC00DD", LauncherSettings.get(Theme.ansi_black))
            assertEquals("auto", LauncherSettings.get(Theme.widget_row_background_color))
            assertEquals(palette.colors[Theme.terminal_window_background_color], LauncherSettings.getColor(Theme.terminal_window_background_color))
            instrumentation.runOnMainSync {
                val control = TextView(context)
                TuixtTheme.styleButton(context, control, false)
                assertEquals(LauncherSettings.getColor(Theme.output_text_color), control.currentTextColor)
                val intent = Intent()
                RetuiThemeBridge.putLauncherThemeExtras(intent, context)
                assertEquals(0xFF445566.toInt(), intent.getIntExtra(RetuiVisualContract.PANEL_TEXT, 0))
                assertEquals(0xFF445566.toInt(), intent.getIntExtra(RetuiVisualContract.HEADER_TEXT, 0))
                assertEquals(0xFF445566.toInt(), intent.getIntExtra(RetuiVisualContract.BUTTON_TEXT, 0))
                assertEquals(0xFF445566.toInt(), intent.getIntExtra(RetuiVisualContract.OUTPUT_TEXT, 0))
            }
            assertTrue(AutoColorManager.debugSummary().contains("auto_theme_source: wallpaper"))
        } finally {
            ThemeColorResolver.clearPreview()
            saved.forEach { (setting, value) -> LauncherSettings.set(context, setting, value) }
        }
    }

    @Test fun failedExtractionDoesNotChangeThemeOrWallpaperSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val settings: List<XMLPrefsSave> = Theme.entries + Suggestions.entries + listOf(Ui.system_wallpaper, Ui.auto_color_pick)
        val before = settings.associateWith { LauncherSettings.get(it) }
        val failed = AutoColorManager.snapshotFromSeeds(AutoColorManager.SeedColors(null,
            AutoColorManager.Source.UNAVAILABLE, "wallpaper_colors_unavailable;system_palette_unavailable"))
        assertFalse(LauncherSettings.applyWallpaperSnapshot(context, failed).succeeded)
        assertEquals(before, settings.associateWith { LauncherSettings.get(it) })
        assertTrue(failed.message(context).contains("current theme was kept"))
    }
}
