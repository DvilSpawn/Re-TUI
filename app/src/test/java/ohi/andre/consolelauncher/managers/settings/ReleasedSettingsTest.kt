package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

class ReleasedSettingsTest {
    @Test fun shippedBuild507PalettesAreReadableWithoutRewritingTheSource() {
        for (root in listOf(XMLPrefsManager.XMLPrefsRoot.THEME,XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS)) {
            val original=checkNotNull(javaClass.getResourceAsStream("/settings-compat/build507/${root.path}")).use {it.readBytes()}
            val file=Files.createTempFile("released-settings", ".xml").toFile()
            try {
                file.writeBytes(original)
                val normalized=PresetManager.sanitizeShareableXml(file,root)
                val values=PresetManager.readAllowed(file,root,root.enums.toTypedArray())
                assertTrue(normalized.contains("<${root.name}>"))
                assertFalse(values.isEmpty())
                values.forEach { (key,value) -> assertNull("${key.label()}=$value",SettingsValidation.error(key,value)) }
                assertArrayEquals(original,file.readBytes())
                val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                XMLPrefsManager.migrateRootValues(doc,doc.documentElement,root)
                assertFalse(XMLPrefsManager.migrateRootValues(doc,doc.documentElement,root))
            } finally {file.delete()}
        }
    }
    @Test fun refreshClassificationKeepsAllColorsLiveAndLayoutChangesExplicit() {
        Theme.entries.forEach {assertTrue(it.name,SettingsRefreshCoordinator.appearanceOnly(it))}
        assertTrue(SettingsRefreshCoordinator.appearanceOnly(Ui.input_output_size))
        assertFalse(SettingsRefreshCoordinator.appearanceOnly(Behavior.launcher_mode))
        assertFalse(SettingsRefreshCoordinator.appearanceOnly(Ui.ram_index))
        assertEquals(SettingsRefreshCoordinator.Effect.HOME, SettingsRefreshCoordinator.effect(SurfaceBorderOption.unified_status_border))
        assertEquals(SettingsRefreshCoordinator.Effect.GEOMETRY, SettingsRefreshCoordinator.effect(Ui.display_margin_top_section))
        assertEquals(SettingsRefreshCoordinator.Effect.HOME, SettingsRefreshCoordinator.effect(Behavior.launcher_mode))
        assertEquals(SettingsRefreshCoordinator.Effect.SERVICES, SettingsRefreshCoordinator.effect(Notifications.show_notifications))
    }
    @Test fun sharedValidationRejectsInvalidNewEditsAndAcceptsSupportedLegacyMode() {
        assertNotNull(SettingsValidation.error(Ui.input_output_size,"-1"))
        assertNotNull(SettingsValidation.error(Ui.system_wallpaper,"yes"))
        assertNull(SettingsValidation.error(Behavior.launcher_mode,""))
        assertNull(SettingsValidation.error(Behavior.launcher_mode,"terminal"))
        assertNotNull(SettingsValidation.error(Behavior.launcher_mode,"unknown"))
        assertNull(SettingsValidation.error(Theme.workspace_panel_text_color,"auto"))
    }
    @Test fun presetsCanRestoreAnEmptyDefaultColor() {
        // Switching from a preset with a song color back to one that leaves it at "" must not be rejected.
        assertNull(SettingsValidation.error(Suggestions.song_text_color,""))
        assertNotNull(SettingsValidation.error(Suggestions.song_background_color,""))
    }
    @Test fun erasedBooleanAndNumberRenderWithTheSameDefaultsAsSavedSettings() {
        val appearance = ohi.andre.consolelauncher.presentation.HomeAppearance(mapOf(
            Ui.enable_dashed_border to "", Ui.input_output_size to ""))
        assertEquals(Ui.enable_dashed_border.defaultValue().toBoolean(), appearance.bool(Ui.enable_dashed_border))
        assertEquals(Ui.input_output_size.defaultValue()!!.toFloat(), appearance.number(Ui.input_output_size), 0f)
    }

}
