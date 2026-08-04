package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import ohi.andre.consolelauncher.managers.xml.options.Ui

class PresetManagerTest {
    @Test fun shareableUiExcludesPersonalAndNonportableSettings() {
        val xml = PresetManager.shareableUiXml()
        assertTrue(xml.contains("<UI>"))
        assertTrue(xml.contains("<system_font "))
        assertTrue(xml.contains("<module_corner_radius "))
        assertTrue(xml.contains("<enable_crt_vignette "))
        assertFalse(xml.contains("<username "))
        assertFalse(xml.contains("<deviceName "))
        assertFalse(xml.contains("<font_file "))
        assertFalse(xml.contains("<auto_color_pick "))
    }

    @Test fun crtVignetteDefaultsOn() {
        assertEquals("true", Ui.enable_crt_vignette.defaultValue())
    }
}
