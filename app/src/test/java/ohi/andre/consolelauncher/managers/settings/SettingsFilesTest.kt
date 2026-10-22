package ohi.andre.consolelauncher.managers.settings

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SettingsFilesTest {
    @Test fun failedBatchNeverPublishesOrChangesAnEarlierFile() {
        val root = Files.createTempDirectory("settings-batch").toFile().canonicalFile
        try {
            val theme = File(root,"theme.xml").apply { writeText("old theme") }
            val ui = File(root,"ui.xml").apply { writeText("old ui") }
            val staged = File(root,"staged").apply { writeText("new theme") }
            var published = false
            assertThrows(Exception::class.java) {
                SettingsFiles.replace(null, linkedMapOf(theme to staged,ui to File(root,"missing")),root) { published=true }
            }
            assertFalse(published)
            assertEquals("old theme",theme.readText())
            assertEquals("old ui",ui.readText())
            SettingsFiles.recover(null,root)
            SettingsFiles.recover(null,root)
        } finally { root.deleteRecursively() }
    }
    @Test fun successfulBatchPublishesAfterEveryFileAndPreservesUnknownContent() {
        val root = Files.createTempDirectory("settings-commit").toFile().canonicalFile
        try {
            val target=File(root,"theme.xml").apply { writeText("old") }
            val next=File(root,"staged").apply { writeText("<THEME><custom value=\"exact\"/></THEME>") }
            var publications=0
            SettingsFiles.replace(null,mapOf(target to next),root) {
                publications++
                assertEquals(next.readText(),target.readText())
            }
            assertEquals(1,publications)
            assertFalse(File(root,"settings-transaction/journal").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun targetsCannotEscapeTheSettingsRoot() {
        val root=Files.createTempDirectory("settings-target").toFile().canonicalFile
        try {
            val staged=File(root,"staged").apply {writeText("new")}
            assertThrows(IllegalArgumentException::class.java) {
                SettingsFiles.replace(null,mapOf(File(root,"unrelated.txt") to staged),root) {}
            }
            assertFalse(File(root,"unrelated.txt").exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun interruptedSettingsJournalRecoversRepeatedlyBeforeAndAfterCommit() {
        class Killed : Error()
        for (phase in listOf("preparing", "applying", "original-0", "installed-0", "committed")) {
            val root = Files.createTempDirectory("settings-recovery").toFile().canonicalFile
            try {
                val target = File(root, "ui.xml").apply { writeText("old") }
                val source = File(root, "incoming").apply { writeText("new") }
                val journal = File(root, "settings-transaction/journal")
                try {
                    ohi.andre.consolelauncher.managers.BackupRestoreTransaction.replace(mapOf(target to source),
                        journal, emptyMap(), { it == target }, {},
                        checkpoint = { if (it == phase) throw Killed() }, commit = {})
                    fail("Expected interruption at $phase")
                } catch (_: Killed) { }
                SettingsFiles.recover(null, root)
                SettingsFiles.recover(null, root)
                assertEquals(if (phase == "committed") "new" else "old", target.readText())
                assertFalse(journal.exists())
            } finally { root.deleteRecursively() }
        }
    }

}
