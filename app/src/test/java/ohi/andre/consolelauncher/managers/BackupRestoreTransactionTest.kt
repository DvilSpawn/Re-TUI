package ohi.andre.consolelauncher.managers

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class BackupRestoreTransactionTest {
    @Test fun restoresNestedStoresAndDeletesSupersededFiles() {
        val root = Files.createTempDirectory("restore-success").toFile()
        try {
            val live = File(root, "live").apply { mkdir() }
            val notes = File(live, "notes-library").apply { mkdir() }
            File(notes, "old.md").writeText("old")
            val incoming = File(root, "incoming").apply { mkdir() }
            File(incoming, ".assets").mkdir()
            File(incoming, ".assets/picture.png").writeBytes(byteArrayOf(1, 2, 3))
            File(incoming, "note.md").writeText("restored note")
            var committed = false
            BackupRestoreTransaction.replace(mapOf(notes to incoming), { committed = true }, { fail("unexpected rollback") })
            assertTrue(committed)
            assertFalse(File(notes, "old.md").exists())
            assertEquals("restored note", File(notes, "note.md").readText())
            assertArrayEquals(byteArrayOf(1, 2, 3), File(notes, ".assets/picture.png").readBytes())
            assertFalse(live.list().orEmpty().any { it.startsWith(".restore-") })
        } finally { root.deleteRecursively() }
    }

    @Test fun failedPreferenceCommitRestoresEveryOriginalAndCallsRollback() {
        val root = Files.createTempDirectory("restore-failure").toFile()
        try {
            val original = File(root, "ui.xml").apply { writeText("current settings") }
            val deleted = File(root, "obsolete.xml").apply { writeText("keep if failed") }
            val created = File(root, "new.xml")
            val incoming = File(root, "source").apply { writeText("backup settings") }
            var rolledBack = false
            try {
                BackupRestoreTransaction.replace(linkedMapOf(original to incoming, deleted to null, created to incoming), {
                    assertEquals("backup settings", original.readText())
                    assertFalse(deleted.exists())
                    error("Preference storage is full")
                }, { rolledBack = true })
                fail("Restore must fail")
            } catch (expected: IllegalStateException) {
                assertEquals("Preference storage is full", expected.message)
            }
            assertTrue(rolledBack)
            assertEquals("current settings", original.readText())
            assertEquals("keep if failed", deleted.readText())
            assertFalse(created.exists())
            assertFalse(root.list().orEmpty().any { it.startsWith(".restore-") })
        } finally { root.deleteRecursively() }
    }

    @Test fun stagingFailureNeverTouchesLiveFiles() {
        val root = Files.createTempDirectory("restore-stage-failure").toFile()
        try {
            val original = File(root, "ui.xml").apply { writeText("current settings") }
            try {
                BackupRestoreTransaction.replace(mapOf(original to File(root, "missing")), { fail("must not commit") }, {})
                fail("Missing source must fail")
            } catch (expected: IllegalStateException) { }
            assertEquals("current settings", original.readText())
        } finally { root.deleteRecursively() }
    }
}
