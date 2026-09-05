package ohi.andre.consolelauncher.managers

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    @Test
    fun shareablePresetManifestPreservesItsName() {
        val manifest = BackupManager.shareableManifest(
            "preset",
            "theme,ui,suggestions,behavior",
            "Black Dawn"
        )

        assertTrue(manifest.contains("presetName=Black Dawn\n"))
        assertEquals(
            1,
            manifest.lineSequence().count { it.startsWith("presetName=") }
        )
    }

    @Test
    fun currentLookManifestHasNoPresetName() {
        val manifest = BackupManager.shareableManifest(
            "current",
            "theme,ui,suggestions,behavior",
            null
        )

        assertFalse(manifest.contains("presetName="))
    }

    @Test
    fun personalBackupIncludesSharedFrameLibraryAndSpaceSelections() {
        val file = Files.createTempFile("retui-frame", ".retui-frame").toFile()
        assertTrue(BackupManager.isBackupCandidate(file, "frames/library-${"a".repeat(64)}.retui-frame"))
        assertTrue(BackupManager.isBackupCandidate(file, "spaces/space-1/frames/state.json"))
    }

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
    fun acceptsPiSafeShareableConfiguration() {
        assertFalse(
            BackupManager.validatePackage(
                "type=retui-shareable-config\nschema=2\nprofile=shareable\nprivacy=pi-safe\nsections=theme,ui,suggestions,behavior\n",
                setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml", "behavior.xml")
            )
        )
    }

    @Test
    fun userSelectedBehaviorManifestRecordsExactConsent() {
        val manifest = BackupManager.shareableManifest(
            "current",
            "theme,ui,suggestions,behavior",
            null,
            setOf("status_time_format", "double_tap_cmd")
        )

        assertTrue(manifest.contains("schema=3\n"))
        assertTrue(manifest.contains("privacy=user-selected\n"))
        assertTrue(manifest.contains("behaviorFields=double_tap_cmd,status_time_format\n"))
        assertFalse(
            BackupManager.validatePackage(
                manifest,
                setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml", "behavior.xml")
            )
        )
        assertEquals(setOf("double_tap_cmd", "status_time_format"), BackupManager.behaviorFields(manifest))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownUserSelectedBehaviorField() {
        BackupManager.validatePackage(
            "type=retui-shareable-config\nschema=3\nprofile=shareable\nprivacy=user-selected\n" +
                "sections=theme,ui,suggestions,behavior\nbehaviorFields=unknown_setting\n",
            setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml", "behavior.xml")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSchemaTwoShareableConfigurationWithoutPrivacyMarker() {
        BackupManager.validatePackage(
            "type=retui-shareable-config\nschema=2\nprofile=shareable\nsections=theme,ui,suggestions,behavior\n",
            setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml", "behavior.xml")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCustomFramesFromPiSafeShareableConfiguration() {
        BackupManager.validatePackage(
            "type=retui-shareable-config\nschema=2\nprofile=shareable\nprivacy=pi-safe\nsections=theme,ui,suggestions,behavior,frames\n",
            setOf(
                "manifest.txt",
                "theme.xml",
                "ui.xml",
                "suggestions.xml",
                "behavior.xml",
                "frames/state.json",
                "frames/library-${"a".repeat(64)}.retui-frame"
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
    fun acceptsShareableConfigurationWithBehaviorAndFrames() {
        assertFalse(
            BackupManager.validatePackage(
                "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,ui,suggestions,behavior,frames\n",
                setOf(
                    "manifest.txt",
                    "theme.xml",
                    "ui.xml",
                    "suggestions.xml",
                    "behavior.xml",
                    "frames/state.json",
                    "frames/output.retui-frame"
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShareableConfigurationMissingDeclaredBehavior() {
        BackupManager.validatePackage(
            "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,ui,suggestions,behavior\n",
            setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml")
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
