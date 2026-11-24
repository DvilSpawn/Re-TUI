package ohi.andre.consolelauncher.commands.tuixt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetImportNameTest {
    @Test fun namesComeFromTheFile() {
        assertEquals("grand-theft", PresetImportActivity.presetNameFromFile("grand-theft.retui-backup.zip"))
        assertEquals("grand-theft", PresetImportActivity.presetNameFromFile("grand-theft.retui-backup (1).zip".replace(".retui-backup (1).zip", " (1).retui-backup.zip")))
        assertEquals("Mono", PresetImportActivity.presetNameFromFile("Mono.retui"))
        assertNull(PresetImportActivity.presetNameFromFile(".zip"))
    }
}
