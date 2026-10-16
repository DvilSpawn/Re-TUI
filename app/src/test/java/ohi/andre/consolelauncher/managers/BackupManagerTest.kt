package ohi.andre.consolelauncher.managers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {
    @Test
    fun portableExportUsesPresetNameAndDistinctExtension() {
        assertEquals("Black_Dawn.retui", BackupManager.defaultShareableConfigurationName("../Black Dawn"))
        assertTrue(BackupManager.defaultShareableConfigurationName().endsWith(".retui"))
        assertTrue(BackupManager.defaultBackupName().endsWith(".retui-backup"))
    }

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
    fun personalBackupKeepsCalculatorHistoryAndOptions() {
        assertTrue(BackupManager.personalSharedPreferenceNames().contains("calculator"))
    }

    @Test
    fun personalBackupExcludesDownloadedEnginesButKeepsCalculatorUserFiles() {
        val root = Files.createTempDirectory("calculator-backup").toFile()
        try {
            val downloads = File(root, "calculator-engines").apply { mkdir() }
            val archive = File(downloads, "qalculate-5.8.2.retui.calc.engine").apply { writeText("engine") }
            val note = File(root, "calculator-notes.txt").apply { writeText("my calculations") }
            assertFalse(BackupManager.isBackupCandidate(downloads, "calculator-engines"))
            assertFalse(BackupManager.isBackupCandidate(archive, "calculator-engines/${archive.name}"))
            val wallpapers = File(root, "wallpaper-downloads").apply { mkdir() }
            val wallpaper = File(wallpapers, "clouds.retui-wallpaper").apply { writeText("wallpaper") }
            assertFalse(BackupManager.isBackupCandidate(wallpapers, wallpapers.name))
            assertFalse(BackupManager.isBackupCandidate(wallpaper, "${wallpapers.name}/${wallpaper.name}"))
            assertTrue(BackupManager.isBackupCandidate(note, note.name))
            assertTrue(BackupManager.personalSharedPreferenceNames().contains("calculator"))
            assertTrue(BackupManager.personalSharedPreferenceNames().contains("retui_wallpaper"))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun personalBackupIncludesFramePreferenceState() {
        assertTrue(BackupManager.personalSharedPreferenceNames().contains("retui_frames"))
    }

    @Test
    fun personalBackupKeepsStartupCompletionButNotPreviewDrafts() {
        assertTrue(BackupManager.personalSharedPreferenceNames().contains("retui_startup"))
        assertFalse(BackupManager.personalSharedPreferenceNames().contains("retui_startup_preview"))
    }

    @Test
    fun personalBackupIncludesPrivateNotesImagesLanguageAndThemeState() {
        assertTrue(BackupManager.personalPrivateFileNames.containsAll(listOf(
            "remember-notes.xml", "notes-library", "images", "launcher-language-packs"
        )))
        assertTrue(BackupManager.personalSharedPreferenceNames().containsAll(listOf("launcher-language", "remember-theme")))
    }

    @Test
    fun personalBackupIncludesModuleLibraryInstallProvenance() {
        assertTrue(BackupManager.personalSharedPreferenceNames().contains("retui_module_library_installs"))
    }

    @Test
    fun acceptsCompressedBackupWithinCumulativeExpandedLimit() {
        val archive = compressibleArchive(2, 400)
        assertTrue(archive.size < 1024)
        assertEquals(800L, BackupManager.validateExpandedBackupSize(archive, 1024))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSmallCompressedBackupThatRestoreCannotExpandWithinLimit() {
        // Every source file fits, and the compressed archive fits; only the expanded total is too big.
        val archive = compressibleArchive(3, 400)
        assertTrue(archive.size < 1024)
        BackupManager.validateExpandedBackupSize(archive, 1024)
    }

    private fun compressibleArchive(entries: Int, bytesPerEntry: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            repeat(entries) { index ->
                zip.putNextEntry(ZipEntry("file-$index.txt"))
                zip.write(ByteArray(bytesPerEntry) { 'x'.code.toByte() })
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun privateNotesAndLanguageFilesSurviveArchiveRoundTrip() {
        val root = Files.createTempDirectory("private-backup-roundtrip").toFile()
        try {
            val original = File(root, "original").apply { mkdir() }
            val contents = mapOf(
                "remember-notes.xml" to "<notes version=\"4\"/>",
                "notes-library/note.md" to "My note",
                "notes-library/.assets/note/image.png" to "image bytes",
                "images/legacy/photo.png" to "legacy image",
                "launcher-language-packs/ta.retui-launcher-lang" to "language pack"
            )
            contents.forEach { (path, text) ->
                File(original, path).apply { parentFile!!.mkdirs(); writeText(text) }
            }
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                BackupManager.personalPrivateFileNames.forEach { name ->
                    File(original, name).takeIf { it.exists() }?.let { BackupManager.addPrivateFiles(zip, original, it) }
                }
            }
            val extracted = File(root, "extracted").apply { mkdir() }
            ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    assertTrue(entry.name.startsWith("app_files/"))
                    File(extracted, entry.name).apply { parentFile!!.mkdirs(); outputStream().use { zip.copyTo(it) } }
                }
            }
            val restored = File(root, "restored").apply { mkdir() }
            val replacement = BackupManager.personalPrivateFileNames.associate { name ->
                File(restored, name) to File(extracted, "app_files/$name").takeIf { it.exists() }
            }
            BackupRestoreTransaction.replace(replacement, {}, { throw AssertionError("unexpected rollback") })
            contents.forEach { (path, text) -> assertEquals(text, File(restored, path).readText()) }
        } finally { root.deleteRecursively() }
    }

    @Test
    fun acceptsVersionTwoPersonalBackupWithNestedPrivateAssets() {
        assertTrue(BackupManager.validatePackage(
            "type=retui-backup\nschema=2\nprofile=personal\n",
            setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml", "shared_prefs/ui.properties",
                "app_files/notes-library/.assets/123/photo.png", "app_files/launcher-language-packs/ta.retui-launcher-lang")
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownPrivateFileStore() {
        BackupManager.validatePackage(
            "type=retui-backup\nschema=2\nprofile=personal\n",
            setOf("manifest.txt", "theme.xml", "ui.xml", "suggestions.xml", "shared_prefs/ui.properties", "app_files/unknown/secrets")
        )
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
