package ohi.andre.consolelauncher.managers

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class PortablePresetTest {
    private fun archive(manifest: String, extras: Map<String, String> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            (mapOf("manifest.txt" to manifest, "theme.xml" to "<THEME/>",
                "suggestions.xml" to "<SUGGESTIONS/>") + extras).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val legacy = "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,suggestions\npresetName=Legacy\n"

    private fun rejects(bytes: ByteArray) {
        val root = Files.createTempDirectory("portable-rejection").toFile()
        try {
            assertThrows(Exception::class.java) { BackupManager.inspectPreset(bytes.inputStream(), root).close() }
            assertEquals("Failed inspection must clean private staging", 0, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun personalManifestIsRejectedEvenInPortableContainer() {
        rejects(archive("type=retui-backup\nschema=1\nprofile=personal\n"))
    }
    @Test fun unsupportedSchemaIsRejected() { rejects(archive(legacy.replace("schema=1", "schema=99"))) }
    @Test fun missingManifestIsRejected() { rejects(archive("")) }
    @Test fun duplicateManifestFieldsAreRejected() { rejects(archive(legacy + "schema=1\n")) }
    @Test fun unexpectedEntryIsRejected() { rejects(archive(legacy, mapOf("alias.txt" to "commands"))) }
    @Test fun traversalIsRejected() { rejects(archive(legacy, mapOf("../outside.xml" to "bad"))) }
    @Test fun oversizedManifestIsRejected() { rejects(archive(legacy + "padding=" + "x".repeat(16384))) }
    @Test fun expandedXmlLimitIsEnforcedDuringExtraction() { rejects(archive(legacy, mapOf("theme.xml" to "x".repeat(256 * 1024 + 1)))) }
    @Test fun invalidXmlIsRejectedBeforeConfirmation() { rejects(archive(legacy, mapOf("theme.xml" to "<UI/>"))) }
    @Test fun xmlEntitiesAreRejectedBeforeConfirmation() { rejects(archive(legacy, mapOf("theme.xml" to "<!DOCTYPE THEME [<!ENTITY x SYSTEM 'file:///private'>]><THEME/>"))) }
    @Test fun duplicateXmlSettingsAreRejected() { rejects(archive(legacy, mapOf("theme.xml" to "<THEME><input_text_color value=\"#112233\"/><input_text_color value=\"#445566\"/></THEME>"))) }
    @Test fun truncatedArchiveIsRejected() { rejects(archive(legacy).dropLast(30).toByteArray()) }
    @Test fun compressedInputLimitIncludesTrailingData() {
        rejects(archive(legacy) + ByteArray(32 * 1024 * 1024))
    }
    @Test fun duplicateArchiveNamesAreRejected() {
        val bytes = archive(legacy, mapOf("other.xml" to "<THEME/>"))
        val old = "other.xml".toByteArray()
        val replacement = "theme.xml".toByteArray()
        for (index in 0..bytes.size - old.size) if (old.indices.all { bytes[index + it] == old[it] })
            replacement.copyInto(bytes, index)
        rejects(bytes)
    }
    @Test fun archiveEntryCountIsBounded() {
        val frames = (0..256).associate { "frames/library-${it.toString(16).padStart(64, '0')}.retui-frame" to "" }
        rejects(archive(legacy, frames))
    }
    @Test fun legacySchemasAndSelectedBehaviorAreInspectedWithoutSaving() {
        val root = Files.createTempDirectory("portable-schemas").toFile()
        try {
            for (schema in 1..3) {
                val manifest = legacy.replace("schema=1", "schema=$schema") + when(schema) {
                    2 -> "privacy=pi-safe\n"
                    3 -> "privacy=user-selected\nbehaviorFields=double_tap_cmd\n"
                    else -> ""
                }
                BackupManager.inspectPreset(archive(manifest).inputStream(), root).use {
                    assertEquals(schema.toString(), it.schema)
                    assertEquals("Legacy", it.name)
                }
            }
            assertEquals(0, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun confirmationRevalidatesPrivateSnapshotBeforeSaving() {
        val root = Files.createTempDirectory("portable-revalidation").toFile()
        val field = ohi.andre.consolelauncher.tuils.Tuils.javaClass.getDeclaredField("folder").apply { isAccessible = true }
        val previous = field.get(ohi.andre.consolelauncher.tuils.Tuils)
        try {
            field.set(ohi.andre.consolelauncher.tuils.Tuils, root)
            BackupManager.inspectPreset(archive(legacy).inputStream(), File(root, "stage")).use { staged ->
                File(staged.folder.parentFile, "package.retui").writeBytes(archive("type=retui-backup\nschema=1\nprofile=personal\n"))
                assertThrows(IllegalArgumentException::class.java) { staged.save() }
                assertTrue(PresetManager.listPresets().isEmpty())
            }
            assertEquals(0, File(root, "stage").listFiles()!!.size)
        } finally { field.set(ohi.andre.consolelauncher.tuils.Tuils, previous); root.deleteRecursively() }
    }

    @Test fun aggregateExtractedBytesAreBounded() {
        val frames = (0..6).associate { "frames/library-${it.toString(16).padStart(64, '0')}.retui-frame" to "x".repeat(5 * 1024 * 1024) }
        rejects(archive(legacy, frames))
    }

    @Test
    fun importedNamesRemainUniqueIgnoringCase() {
        val root = Files.createTempDirectory("portable-names").toFile()
        val field = ohi.andre.consolelauncher.tuils.Tuils.javaClass.getDeclaredField("folder").apply { isAccessible = true }
        val previous = field.get(ohi.andre.consolelauncher.tuils.Tuils)
        try {
            field.set(ohi.andre.consolelauncher.tuils.Tuils, root)
            val source = File(root, "source").apply { mkdir() }
            File(source, "theme.xml").writeText("<THEME/>")
            File(source, "suggestions.xml").writeText("<SUGGESTIONS/>")
            assertEquals("blue (2)", PresetManager.importExtractedFolder("blue", source))
            assertEquals("My preset", PresetManager.importExtractedFolder("My preset", source))
            assertEquals("my PRESET (2)", PresetManager.importExtractedFolder("my PRESET", source))
        } finally { field.set(ohi.andre.consolelauncher.tuils.Tuils, previous); root.deleteRecursively() }
    }

    @Test
    fun inspectionUsesPrivateStagingWithoutSavingAndCloseCleansUp() {
        val root = Files.createTempDirectory("portable-preset-test").toFile()
        try {
            val method = BackupManager.javaClass.methods.firstOrNull { it.name == "inspectPreset" }
            assertNotNull("A preset-only inspection pipeline must exist", method)
            val staged = method!!.invoke(BackupManager, ByteArrayInputStream(archive(
                "type=retui-shareable-config\nschema=1\nprofile=shareable\nsections=theme,suggestions\npresetName=Legacy\n"
            )), root) as AutoCloseable
            assertEquals(1, root.listFiles()!!.size)
            staged.close()
            assertEquals(0, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
}
