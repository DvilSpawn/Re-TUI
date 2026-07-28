package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    @Test
    fun acceptsCompletePersonalBackup() {
        assertTrue(
            BackupManager.validatePackage(
                "type=retui-backup\nschema=1\nprofile=personal\n",
                setOf(
                    "manifest.txt",
                    "theme.xml",
                    "ui.xml",
                    "suggestions.xml",
                    "shared_prefs/ui.properties"
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompletePersonalBackupBeforeRestore() {
        BackupManager.validatePackage(
            "type=retui-backup\nschema=1\nprofile=personal\n",
            setOf("manifest.txt")
        )
    }

    @Test
    fun acceptsExactShareableConfiguration() {
        assertFalse(
            BackupManager.validatePackage(
                "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,suggestions\n",
                setOf("manifest.txt", "theme.xml", "suggestions.xml")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExtraFilesInShareableConfiguration() {
        BackupManager.validatePackage(
            "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,suggestions\n",
            setOf("manifest.txt", "theme.xml", "suggestions.xml", "alias.xml")
        )
    }
}
