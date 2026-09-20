package ohi.andre.consolelauncher.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetuiWallpaperSettingsTest {
    @Test
    fun themePaletteKeepsUniqueHexesInFileOrder() {
        val xml = """<THEME>
            <one value="#aa00ff"/>
            <two value="#80AA00FF"/>
            <three value="#AA00FF"/>
            <ignored value="red"/>
        </THEME>"""

        assertEquals(
            listOf("#AA00FF", "#80AA00FF"),
            RetuiWallpaperSettings.uniqueThemeColors(xml)
        )
    }

    @Test
    fun parseColorValueAcceptsArgbAndExtraHash() {
        assertEquals(0xFF09121C.toInt(), RetuiWallpaperSettings.parseColorValue("##FF09121c"))
        assertEquals(0x80010203.toInt(), RetuiWallpaperSettings.parseColorValue("#80010203"))
        assertNull(RetuiWallpaperSettings.parseColorValue("#GG010203"))
    }
}
