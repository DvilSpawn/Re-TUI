package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorResolverTest {
    @Test
    fun advancedRolesDefaultToAutoAndResolve() {
        Theme.entries.filter { it.advanced }.forEach { role ->
            assertEquals(XMLPrefsSave.AUTO_COLOR, role.type())
            assertEquals("auto", role.defaultValue())
            ThemeColorResolver.resolve(role, { null }, emptyMap())
        }
    }

    @Test
    fun explicitTransparentIsNotInheritance() {
        val values = mapOf(
            Theme.module_button_background_color to "#FF112233",
            Theme.button_background_color to "#00000000"
        )
        assertEquals(
            0x00000000,
            ThemeColorResolver.resolve(Theme.button_background_color, values::get, emptyMap())
        )
    }

    @Test
    fun autoInheritsAndPreviewWins() {
        val values = mapOf(
            Theme.module_button_background_color to "#FF112233",
            Theme.button_background_color to "auto"
        )
        assertEquals(
            0xFF112233.toInt(),
            ThemeColorResolver.resolve(Theme.button_background_color, values::get, emptyMap())
        )
        assertEquals(
            0x80445566.toInt(),
            ThemeColorResolver.resolve(
                Theme.button_background_color,
                values::get,
                mapOf(Theme.button_background_color to "#80445566")
            )
        )
    }

    @Test
    fun moduleHeaderControlsCanOverrideTerminalHeaderWithoutChangingIt() {
        val values = mapOf(Theme.terminal_header_background_color to "#FF112233")
        assertEquals(0xFF112233.toInt(), ThemeColorResolver.resolve(Theme.module_header_control_background_color, values::get, emptyMap()))
        assertEquals(0xFF445566.toInt(), ThemeColorResolver.resolve(Theme.module_header_control_background_color, values::get, mapOf(Theme.module_header_control_background_color to "#FF445566")))
        assertEquals(0xFF112233.toInt(), ThemeColorResolver.resolve(Theme.terminal_header_background_color, values::get, emptyMap()))
    }

    @Test
    fun derivedColorsNeverIncreaseAlpha() {
        val transparent = ThemeColorResolver.blendPreservingAlpha(0x00112233, 0xFFFFFFFF.toInt(), 0.8f)
        val translucent = ThemeColorResolver.blendPreservingAlpha(0x40112233, 0xFFFFFFFF.toInt(), 0.8f)
        assertEquals(0, transparent ushr 24)
        assertTrue(translucent ushr 24 <= 0x40)
    }

    @Test
    fun parserAcceptsSixAndEightDigitHexOnly() {
        assertEquals(0xFF09121C.toInt(), ThemeColorResolver.parse("#09121c"))
        assertEquals(0x8009121C.toInt(), ThemeColorResolver.parse("#8009121c"))
        assertEquals(null, ThemeColorResolver.parse("auto"))
        assertEquals(null, ThemeColorResolver.parse("#12345"))
    }

    @Test
    fun invalidDraftsNeverEnterPreviewAndClearDropsValidDrafts() {
        ThemeColorResolver.clearPreview()
        assertTrue(!ThemeColorResolver.preview(Theme.button_background_color, "#12345"))
        assertTrue(!ThemeColorResolver.hasPreview())
        assertTrue(ThemeColorResolver.preview(Theme.button_background_color, "#80112233"))
        assertTrue(ThemeColorResolver.hasPreview())
        ThemeColorResolver.clearPreview()
        assertTrue(!ThemeColorResolver.hasPreview())
    }
}
