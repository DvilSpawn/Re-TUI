package ohi.andre.consolelauncher.managers

import java.io.ByteArrayInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    @Test
    fun acceptsExactWrittenBackup() {
        BackupManager.verifyExport(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf(1, 2, 3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyOrTruncatedWrittenBackup() {
        BackupManager.verifyExport(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf()))
    }

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
                "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,ui,suggestions\n",
                setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml")
            )
        )
    }

    @Test
    fun acceptsLegacyShareableConfiguration() {
        assertFalse(
            BackupManager.validatePackage(
                "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,suggestions\n",
                setOf("manifest.txt", "theme.xml", "suggestions.xml")
            )
        )
    }

    @Test
    fun acceptsShareableConfigurationWithFrames() {
        assertFalse(
            BackupManager.validatePackage(
                "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,ui,suggestions,frames\n",
                setOf(
                    "manifest.txt",
                    "theme.xml",
                    "ui.xml",
                    "suggestions.xml",
                    "frames/state.json",
                    "frames/output.retui-frame"
                )
            )
        )
    }

    @Test
    fun acceptsShareableConfigurationWithFrameLibrary() {
        assertFalse(
            BackupManager.validatePackage(
                "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,ui,suggestions,frames\n",
                setOf(
                    "manifest.txt",
                    "theme.xml",
                    "ui.xml",
                    "suggestions.xml",
                    "frames/state.json",
                    "frames/library-${"a".repeat(64)}.retui-frame"
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExtraFilesInShareableConfiguration() {
        BackupManager.validatePackage(
            "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,ui,suggestions\n",
            setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml", "alias.xml")
        )
    }
}
