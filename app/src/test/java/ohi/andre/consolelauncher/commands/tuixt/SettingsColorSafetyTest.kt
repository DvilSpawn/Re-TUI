package ohi.andre.consolelauncher.commands.tuixt

import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class SettingsColorSafetyTest {
    private val pairs = listOf(
        Theme.settings_panel_text_color to Theme.settings_panel_background_color,
        Theme.settings_header_text_color to Theme.settings_header_background_color,
        Theme.settings_row_text_color to Theme.settings_row_background_color,
        Theme.settings_input_text_color to Theme.settings_input_background_color,
        Theme.settings_input_hint_color to Theme.settings_input_background_color,
        Theme.button_text_color to Theme.button_background_color,
        Theme.primary_button_text_color to Theme.primary_button_background_color,
        Theme.pressed_text_color to Theme.pressed_background_color,
        Theme.disabled_text_color to Theme.disabled_background_color,
        Theme.toggle_on_text_color to Theme.toggle_on_background_color,
        Theme.toggle_off_text_color to Theme.toggle_off_background_color,
        Theme.list_selected_text_color to Theme.list_selected_background_color
    )

    @Test fun issue20PresetsRenderReadableSettingsWithoutChangingTheirValues() {
        for (preset in listOf("modern", "earthy", "kois")) {
            val stream = checkNotNull(javaClass.getResourceAsStream("/issue20/$preset.xml"))
            val root = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it).documentElement }
            val values = buildMap<Theme, String> {
                for (i in 0 until root.childNodes.length) {
                    val node = root.childNodes.item(i)
                    val value = node.attributes?.getNamedItem("value")?.nodeValue ?: continue
                    put(Theme.valueOf(node.nodeName), value)
                }
            }
            val before = values.toMap()
            fun raw(role: Theme) = ThemeColorResolver.resolve(role, values::get, emptyMap())
            fun safe(role: Theme) = SettingsColorSafety.color(role, ::raw)
            for ((textRole, backgroundRole) in pairs) {
                val background = safe(backgroundRole)
                assertEquals("$preset $backgroundRole must shield wallpaper", 255, background ushr 24)
                assertTrue("$preset $textRole", SettingsColorSafety.contrast(
                    SettingsColorSafety.composite(safe(textRole), background), background) >= 4.5)
            }
            val row = safe(Theme.settings_row_background_color)
            val title = SettingsColorSafety.readable(safe(Theme.settings_header_text_color), row)
            assertTrue("$preset row headings", SettingsColorSafety.contrast(
                SettingsColorSafety.composite(title, row), row) >= 4.5)
            assertEquals(before, values)
        }
    }

    @Test fun readableCustomColorsAndUnrelatedRolesStayExact() {
        val values = mapOf(Theme.settings_panel_background_color to "#FF102030",
            Theme.settings_panel_text_color to "#FFE0EEEE",
            Theme.terminal_border_color to "#00112233")
        fun raw(role: Theme) = ThemeColorResolver.resolve(role, values::get, emptyMap())
        assertEquals(raw(Theme.settings_panel_text_color), SettingsColorSafety.color(Theme.settings_panel_text_color, ::raw))
        assertEquals(0x00112233, SettingsColorSafety.color(Theme.terminal_border_color, ::raw))
    }

    @Test fun transparentForegroundKeepsItsHueWhenOpacityAloneIsEnough() {
        assertEquals(0xFFAACCFF.toInt(), SettingsColorSafety.readable(0x00AACCFF, 0xFF1F1E2D.toInt()))
        assertEquals(-0x1000000, SettingsColorSafety.readable(0xFFF8F6EE.toInt(), 0xFFD9C9B2.toInt()))
    }

    @Test fun fallbackWorksAcrossEveryGreyBackground() {
        for (grey in 0..255) {
            val background = -0x1000000 or (grey shl 16) or (grey shl 8) or grey
            val foreground = SettingsColorSafety.readable(background, background)
            assertTrue(SettingsColorSafety.contrast(foreground, background) >= 4.5)
        }
    }
}
