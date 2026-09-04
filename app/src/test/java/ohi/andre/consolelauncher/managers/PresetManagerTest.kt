package ohi.andre.consolelauncher.managers

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test fun shareableBehaviorKeepsTypedConfigurationWithoutFreeformPersonalData() {
        val xml = PresetManager.shareableBehaviorXml()
        assertTrue(xml.contains("<BEHAVIOR>"))
        assertTrue(xml.contains("<enable_cyberdeck_mode "))
        assertTrue(xml.contains("<output_tray_mode "))
        assertTrue(xml.contains("<launcher_sounds "))
        assertFalse(xml.contains("<double_tap_cmd "))
        assertFalse(xml.contains("<home_path "))
        assertFalse(xml.contains("<songs_folder "))
        assertFalse(xml.contains("<tui_notification_click_cmd "))
        assertFalse(xml.contains("<weather_key "))
        assertFalse(xml.contains("<weather_location "))
        assertFalse(xml.contains("<weather_format "))
        assertFalse(xml.contains("<status_time_format "))
        assertFalse(xml.contains("<preferred_music_app "))
    }

    @Test fun sanitizerRemovesPersonalTextUnknownFieldsAndInvalidValues() {
        val behavior = Files.createTempFile("preset-behavior", ".xml").toFile()
        behavior.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
                <BEHAVIOR>
                    <enable_cyberdeck_mode value="true" />
                    <output_tray_mode value="toggled" />
                    <weather_location value="Mako's home" />
                    <status_time_format value="Mako HH:mm" />
                    <double_tap_cmd value="contact mako@example.com" />
                    <unknown value="private" />
                </BEHAVIOR>
            """.trimIndent()
        )

        val xml = PresetManager.sanitizeShareableXml(behavior, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR)
        assertTrue(xml.contains("<enable_cyberdeck_mode value=\"true\""))
        assertTrue(xml.contains("<output_tray_mode value=\"toggled\""))
        assertFalse(xml.contains("Mako"))
        assertFalse(xml.contains("double_tap_cmd"))
        assertFalse(xml.contains("unknown"))
    }

    @Test fun sanitizerKeepsConstrainedLayoutAndSuggestionValuesOnly() {
        val ui = Files.createTempFile("preset-ui", ".xml").toFile()
        ui.writeText(
            """<UI>
                    <username value="Mako" />
                    <input_prefix value="${'$'}" />
                    <notes_header value="Mako's notes" />
                    <status_lines_margins value="3,3,0,0" />
                </UI>""".trimIndent()
        )
        val suggestions = Files.createTempFile("preset-suggestions", ".xml").toFile()
        suggestions.writeText(
            """<SUGGESTIONS>
                    <apps_background_color value="#00897B" />
                    <suggestions_order value="2(2)0(5)1(5)3(3)" />
                    <hide_suggestions_when_empty value="always" />
                    <default_text_color value="Mako" />
                </SUGGESTIONS>""".trimIndent()
        )

        val uiXml = PresetManager.sanitizeShareableXml(ui, XMLPrefsManager.XMLPrefsRoot.UI)
        val suggestionsXml = PresetManager.sanitizeShareableXml(
            suggestions,
            XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS
        )
        assertTrue(uiXml.contains("<input_prefix value=\"\$\""))
        assertTrue(uiXml.contains("<status_lines_margins value=\"3,3,0,0\""))
        assertFalse(uiXml.contains("Mako"))
        assertTrue(suggestionsXml.contains("<apps_background_color value=\"#00897B\""))
        assertTrue(suggestionsXml.contains("<suggestions_order value=\"2(2)0(5)1(5)3(3)\""))
        assertFalse(suggestionsXml.contains("Mako"))
    }

    @Test fun sanitizerMigratesLegacyPresetColors() {
        val theme = Files.createTempFile("legacy-theme", ".xml").toFile()
        theme.writeText("<THEME><input_color value=\"#112233\" /></THEME>")
        val suggestions = Files.createTempFile("legacy-suggestions", ".xml").toFile()
        suggestions.writeText("<SUGGESTIONS><default_bg_color value=\"#445566\" /></SUGGESTIONS>")

        assertTrue(PresetManager.sanitizeShareableXml(theme, XMLPrefsManager.XMLPrefsRoot.THEME)
            .contains("<input_text_color value=\"#112233\""))
        assertTrue(PresetManager.sanitizeShareableXml(suggestions, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS)
            .contains("<default_background_color value=\"#445566\""))
    }

    @Test fun sanitizerRejectsDoctypeWithoutParserFeatureSupport() {
        val theme = Files.createTempFile("unsafe-theme", ".xml").toFile()
        theme.writeText("<!DOCTYPE THEME SYSTEM \"file:///tmp/nope\"><THEME />")

        assertThrows(IllegalArgumentException::class.java) {
            PresetManager.sanitizeShareableXml(theme, XMLPrefsManager.XMLPrefsRoot.THEME)
        }
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
