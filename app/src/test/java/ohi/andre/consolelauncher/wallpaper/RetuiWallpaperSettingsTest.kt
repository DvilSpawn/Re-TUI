package ohi.andre.consolelauncher.wallpaper

import org.junit.Assert.assertEquals
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
}
