package ohi.andre.consolelauncher.managers

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class BackupRestoreRecoveryTest {
    private class ProcessKilled : Error()

    @Test fun everyPrecommitKillWindowRecoversFilesAndPreferences() {
        listOf("preparing", "applying", "original-0", "installed-0", "original-1", "installed-1", "preferences").forEach { killedAt ->
            val root = Files.createTempDirectory("restore-kill").toFile().canonicalFile
            try {
                val live = File(root, "live").apply { mkdir() }
                val settings = File(live, "ui.xml").apply { writeText("original") }
                val added = File(live, "new.xml")
                val source = File(root, "incoming").apply { writeText("replacement") }
                val journal = File(root, "private/journal")
                var preference = "original preference"
                val allowed: (File) -> Boolean = { it.parentFile == live }
                try {
                    BackupRestoreTransaction.replace(linkedMapOf(settings to source, added to source), journal,
                        mapOf("ui" to preference), allowed, { preference = it.getValue("ui") },
                        checkpoint = { if (it == killedAt) throw ProcessKilled() },
                        commit = { preference = "replacement preference" })
                    fail("Expected process death at $killedAt")
                } catch (expected: ProcessKilled) { }
                assertTrue("Journal must survive $killedAt", journal.exists())
                BackupRestoreTransaction.recover(journal, allowed, { preference = it.getValue("ui") })
                BackupRestoreTransaction.recover(journal, allowed, { fail("Recovery must be idempotent") })
                assertEquals("original", settings.readText())
                assertFalse(added.exists())
                assertEquals("original preference", preference)
                assertFalse(journal.exists())
                assertFalse(live.list().orEmpty().any { it.startsWith(".restore-") })
            } finally { root.deleteRecursively() }
        }
    }

    @Test fun killAfterCommittedMarkerKeepsNewStateAndCleansOriginals() {
        val root = Files.createTempDirectory("restore-committed-kill").toFile().canonicalFile
        try {
            val target = File(root, "ui.xml").apply { writeText("original") }
            val source = File(root, "source").apply { writeText("replacement") }
            val journal = File(root, "private/journal")
            val allowed: (File) -> Boolean = { it == target }
            try {
                BackupRestoreTransaction.replace(mapOf(target to source), journal, mapOf("ui" to "original preference"),
                    allowed, { fail("Committed preferences must be retained") },
                    checkpoint = { if (it == "committed") throw ProcessKilled() }, commit = {})
                fail("Expected process death")
            } catch (expected: ProcessKilled) { }
            BackupRestoreTransaction.recover(journal, allowed, { fail("Must not roll back committed transaction") })
            assertEquals("replacement", target.readText())
            assertFalse(journal.exists())
            assertFalse(root.list().orEmpty().any { it.startsWith(".restore-") })
        } finally { root.deleteRecursively() }
    }

    @Test fun killingRecoveryAfterOriginalRenameCanBeRecoveredAgain() {
        val root = Files.createTempDirectory("restore-recovery-kill").toFile().canonicalFile
        try {
            val target = File(root, "ui.xml").apply { writeText("original") }
            val source = File(root, "source").apply { writeText("replacement") }
            val journal = File(root, "private/journal")
            val allowed: (File) -> Boolean = { it == target }
            try {
                BackupRestoreTransaction.replace(mapOf(target to source), journal, emptyMap(), allowed, {},
                    checkpoint = { if (it == "installed-0") throw ProcessKilled() }, commit = {})
            } catch (expected: ProcessKilled) { }
            try {
                BackupRestoreTransaction.recover(journal, allowed, {}, { throw ProcessKilled() })
                fail("Expected process death during recovery")
            } catch (expected: ProcessKilled) { }
            BackupRestoreTransaction.recover(journal, allowed, {})
            assertEquals("original", target.readText())
            assertFalse(journal.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationDuringPreparationLeavesCurrentSetupUntouchedAndNoJournal() {
        val root = Files.createTempDirectory("restore-cancel").toFile().canonicalFile
        try {
            val target = File(root, "ui.xml").apply { writeText("original") }
            val source = File(root, "source").apply { writeText("replacement") }
            val journal = File(root, "private/journal")
            val token = FileOperationCancellation.Token()
            try {
                FileOperationCancellation.run(token) {
                    BackupRestoreTransaction.replace(mapOf(target to source), journal, emptyMap(), { it == target }, {},
                        checkpoint = { if (it == "preparing") token.requestCancellation() }, commit = { fail("Cancelled restore must not commit") })
                }
                fail("Expected cancellation")
            } catch (expected: java.util.concurrent.CancellationException) { }
            assertEquals("original", target.readText())
            assertFalse(journal.exists())
            assertFalse(root.list().orEmpty().any { it.startsWith(".restore-") })
        } finally { root.deleteRecursively() }
    }

    @Test fun startupRecoveryRejectsTargetsOutsideAllowedRoots() {
        val root = Files.createTempDirectory("restore-untrusted-journal").toFile().canonicalFile
        try {
            val target = File(root, "ui.xml").apply { writeText("original") }
            val source = File(root, "source").apply { writeText("replacement") }
            val journal = File(root, "private/journal")
            try {
                BackupRestoreTransaction.replace(mapOf(target to source), journal, emptyMap(), { true }, {},
                    checkpoint = { if (it == "installed-0") throw ProcessKilled() }, commit = {})
            } catch (expected: ProcessKilled) { }
            try {
                BackupRestoreTransaction.recover(journal, { false }, { fail("Invalid journal must not touch preferences") })
                fail("Expected unsafe target rejection")
            } catch (expected: IllegalArgumentException) { }
            assertEquals("replacement", target.readText())
            assertTrue(journal.exists())
        } finally { root.deleteRecursively() }
    }
}
