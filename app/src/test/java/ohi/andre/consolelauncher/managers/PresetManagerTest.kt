package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui

class PresetManagerTest {
    @Test fun shareableUiExcludesPersonalAndNonportableSettings() {
        val xml = PresetManager.shareableUiXml()
        assertTrue(xml.contains("<UI>"))
        assertTrue(xml.contains("<system_font "))
        assertTrue(xml.contains("<module_corner_radius "))
        assertTrue(xml.contains("<enable_crt_vignette "))
        assertTrue(xml.contains("<ram_border_enabled "))
        assertTrue(xml.contains("<unified_status_border "))
        assertTrue(xml.contains("<suggestions_border_color "))
        assertFalse(xml.contains("<username "))
        assertFalse(xml.contains("<deviceName "))
        assertFalse(xml.contains("<font_file "))
        assertFalse(xml.contains("<auto_color_pick "))
    }

    @Test fun crtVignetteDefaultsOn() {
        assertEquals("true", Ui.enable_crt_vignette.defaultValue())
    }

    @Test fun unifiedStatusBorderPreservesIndividualFramesByDefault() {
        assertEquals("false", ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption.unified_status_border.defaultValue())
    }

    @Test fun unifiedStatusPaneHasItsOwnTransparentThemeColor() {
        assertEquals("#00000000", Theme.unified_status_background_color.defaultValue())
        assertEquals("Status Lines", XMLPrefsManager.sectionFor(Theme.unified_status_background_color))
    }

    @Test fun wallpaperDefaultsToSystemWithNoOverlay() {
        assertEquals("true", Ui.system_wallpaper.defaultValue())
        assertEquals("#00000000", Theme.wallpaper_overlay_color.defaultValue())
    }
}
