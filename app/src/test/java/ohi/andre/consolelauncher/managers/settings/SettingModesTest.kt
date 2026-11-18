package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Cmd
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Reply
import ohi.andre.consolelauncher.managers.xml.options.Rss
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingModesTest {
    private val every: List<XMLPrefsSave> = listOf<Array<out XMLPrefsSave>>(Apps.values(), Behavior.values(), Cmd.values(),
        Notifications.values(), Reply.values(), Rss.values(), Suggestions.values(), SurfaceBorderOption.values(), Theme.values(),
        Toolbar.values(), Ui.values()).flatMap { it.toList() }

    @Test fun everySettingNamesRealModesAndAtLeastOne() {
        for (setting in every) {
            val modes = SettingModes.of(setting)
            assertTrue("$setting has no mode", modes.isNotEmpty())
            assertTrue("$setting names an unknown mode: $modes", LauncherMode.values.containsAll(modes))
        }
    }

    @Test fun groupsFollowWhatEachHomeDraws() {
        val all = LauncherMode.values.toSet()
        assertEquals(setOf(LauncherMode.MONO), SettingModes.of(Behavior.mono_layout))
        assertEquals(setOf(LauncherMode.DOT), SettingModes.of(Behavior.dot_apps))
        assertEquals(setOf(LauncherMode.CLASSIC), SettingModes.of(Ui.show_battery))
        assertEquals(setOf(LauncherMode.CLASSIC), SettingModes.of(Ui.ram_index))
        assertEquals(setOf(LauncherMode.CLASSIC), SettingModes.of(Behavior.ram_format))
        assertEquals(setOf(LauncherMode.CLASSIC), SettingModes.of(Theme.battery_text_low))
        assertEquals(setOf(LauncherMode.CLASSIC), SettingModes.of(SurfaceBorderOption.ram_border_color))
        assertEquals(setOf(LauncherMode.CLASSIC), SettingModes.of(Toolbar.show_toolbar))
        assertEquals(all - LauncherMode.MONO, SettingModes.of(Theme.accent_color))
        assertEquals(all - LauncherMode.MONO, SettingModes.of(Suggestions.apps_text_color))
        assertEquals(all - LauncherMode.MONO, SettingModes.of(Ui.font_file))
        assertEquals(all, SettingModes.of(Ui.font_size_offset))
        assertEquals(all, SettingModes.of(Behavior.edge_sidebar))
        assertEquals(all, SettingModes.of(Behavior.launcher_mode))
        assertEquals(setOf(LauncherMode.CLASSIC, LauncherMode.MONO, LauncherMode.DOT), SettingModes.of(Behavior.duo_mode))
        assertEquals(setOf(LauncherMode.CLASSIC, LauncherMode.MONO, LauncherMode.DOT), SettingModes.of(Behavior.weather_location))
    }
}
