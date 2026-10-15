package ohi.andre.consolelauncher.managers

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/** Durable intent precedes each set of live changes. Recovery is safe to repeat after another kill. */
internal object BackupRestoreTransaction {
    private const val MAGIC = 0x52535432
    private const val MAX_JOURNAL_BYTES = 32 * 1024 * 1024
    private data class Replacement(val target: File, val staged: File?, val original: File, val hadOriginal: Boolean)
    private data class Journal(val phase: String, val entries: List<Replacement>, val preferences: Map<String, String>)

    // Kept for filesystem-only callers and tests; app restores use the private durable journal below.
    fun replace(replacements: Map<File, File?>, commit: () -> Unit, rollback: () -> Unit) {
        val targets = replacements.keys.map { File(requireNotNull(it.parentFile).canonicalFile, it.name) }.toSet()
        val journal = File(requireNotNull(targets.first().parentFile), ".restore-journal")
        replace(replacements, journal, emptyMap(), { it in targets }, { rollback() }, commit = commit)
    }

    @Synchronized
    fun replace(
        replacements: Map<File, File?>,
        journalFile: File,
        preferenceSnapshots: Map<String, String>,
        validateTarget: (File) -> Boolean,
        restorePreferences: (Map<String, String>) -> Unit,
        syncDirectory: (File) -> Unit = {},
        checkpoint: (String) -> Unit = {},
        commit: () -> Unit
    ) {
        check(!journalFile.exists()) { "An interrupted restore must be recovered first" }
        val id = UUID.randomUUID().toString()
        FileOperationCancellation.check()
        val entries = replacements.map { (targetFile, source) ->
            // Permit platform storage aliases in parent paths, while rejecting a symlink at the target itself.
            val target = File(requireNotNull(targetFile.parentFile).canonicalFile, targetFile.name)
            require(validateTarget(target) && target.canonicalFile == target) { "Unsafe restore target" }
            val parent = requireNotNull(target.parentFile)
            check(parent.isDirectory || parent.mkdirs()) { "Unable to prepare restore folder" }
            Replacement(target, source?.let { File(parent, ".restore-new-$id-${target.name}") },
                File(parent, ".restore-old-$id-${target.name}"), target.exists())
        }
        require(entries.size <= 4096 && preferenceSnapshots.size <= 256) { "Restore journal has too many entries" }
        var state = Journal("PREPARING", entries, preferenceSnapshots)
        writeJournal(journalFile, state, syncDirectory)
        checkpoint("preparing")
        try {
            entries.zip(replacements.values).forEach { (item, source) ->
                if (source != null) copyDurably(source, requireNotNull(item.staged), syncDirectory)
            }
            // Cancellation is cooperative during staging; once promotion starts it must finish or recover.
            FileOperationCancellation.beginCommit()
            state = state.copy(phase = "APPLYING")
            writeJournal(journalFile, state, syncDirectory)
            checkpoint("applying")
            entries.forEachIndexed { index, item ->
                if (item.hadOriginal) {
                    check(item.target.renameTo(item.original)) { "Unable to preserve current setup" }
                    syncDirectory(item.target.parentFile!!)
                }
                checkpoint("original-$index")
                if (item.staged != null) {
                    check(item.staged.renameTo(item.target)) { "Unable to install backup file" }
                    syncDirectory(item.target.parentFile!!)
                }
                checkpoint("installed-$index")
            }
            commit()
            checkpoint("preferences")
            state = state.copy(phase = "COMMITTED")
            writeJournal(journalFile, state, syncDirectory)
            checkpoint("committed")
        } catch (failure: Exception) {
            try { recover(journalFile, validateTarget, restorePreferences, syncDirectory) }
            catch (recoveryFailure: Exception) { failure.addSuppressed(recoveryFailure) }
            throw failure
        }
        // Error/process death deliberately leaves the durable journal and originals for next startup.
        recover(journalFile, validateTarget, restorePreferences, syncDirectory)
    }

    @Synchronized
    fun recover(
        journalFile: File,
        validateTarget: (File) -> Boolean,
        restorePreferences: (Map<String, String>) -> Unit,
        syncDirectory: (File) -> Unit = {}
    ) {
        if (!journalFile.exists()) return
        val journal = readJournal(journalFile, validateTarget)
        if (journal.phase == "APPLYING") {
            journal.entries.asReversed().forEach { item ->
                if (item.hadOriginal) {
                    if (item.original.exists()) {
                        if (item.target.exists()) check(item.target.deleteRecursively()) { "Unable to remove interrupted restore" }
                        check(item.original.renameTo(item.target)) { "Unable to recover original ${item.target.name}" }
                        syncDirectory(item.target.parentFile!!)
                    } else {
                        check(item.target.exists()) { "Original restore file is missing: ${item.target.name}" }
                    }
                } else if (item.staged != null && !item.staged.exists()) {
                    if (item.target.exists()) check(item.target.deleteRecursively()) { "Unable to remove interrupted restore" }
                    syncDirectory(item.target.parentFile!!)
                }
            }
            restorePreferences(journal.preferences)
        }
        journal.entries.forEach { item ->
            item.staged?.let { if (it.exists()) check(it.deleteRecursively()) { "Unable to clean restore staging" } }
            if (journal.phase == "COMMITTED" && item.original.exists()) {
                check(item.original.deleteRecursively()) { "Unable to clean completed restore" }
            }
            syncDirectory(item.target.parentFile!!)
        }
        check(journalFile.delete()) { "Unable to finish restore recovery" }
        syncDirectory(requireNotNull(journalFile.parentFile))
    }

    private fun copyDurably(source: File, target: File, syncDirectory: (File) -> Unit) {
        FileOperationCancellation.check()
        check(source.exists()) { "Missing staged backup file" }
        if (source.isDirectory) {
            check(target.mkdir()) { "Unable to stage restore folder" }
            requireNotNull(source.listFiles()).forEach { copyDurably(it, File(target, it.name), syncDirectory) }
            syncDirectory(target)
        } else {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output -> FileOperationCancellation.copy(input, output); output.fd.sync() }
            }
        }
        syncDirectory(requireNotNull(target.parentFile))
    }

    private fun writeJournal(file: File, journal: Journal, syncDirectory: (File) -> Unit) {
        val parent = requireNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Unable to create restore journal" }
        val pending = File(parent, file.name + ".new")
        FileOutputStream(pending).use { raw ->
            val output = DataOutputStream(raw)
            output.writeInt(MAGIC)
            output.writeUTF(journal.phase)
            output.writeInt(journal.entries.size)
            journal.entries.forEach { item ->
                output.writeUTF(item.target.path)
                output.writeUTF(item.staged?.name.orEmpty())
                output.writeUTF(item.original.name)
                output.writeBoolean(item.hadOriginal)
            }
            output.writeInt(journal.preferences.size)
            journal.preferences.forEach { (name, value) ->
                output.writeUTF(name)
                val bytes = value.toByteArray(Charsets.UTF_8)
                output.writeInt(bytes.size)
                output.write(bytes)
            }
            output.flush()
            raw.fd.sync()
        }
        require(pending.length() <= MAX_JOURNAL_BYTES) { "Restore journal is too large" }
        check(pending.renameTo(file)) { "Unable to save restore journal" }
        syncDirectory(parent)
    }

    private fun readJournal(file: File, validateTarget: (File) -> Boolean): Journal {
        require(file.length() in 1..MAX_JOURNAL_BYTES.toLong()) { "Invalid restore journal size" }
        DataInputStream(FileInputStream(file)).use { input ->
            require(input.readInt() == MAGIC) { "Invalid restore journal" }
            val phase = input.readUTF()
            require(phase in setOf("PREPARING", "APPLYING", "COMMITTED")) { "Invalid restore journal stage" }
            val count = input.readInt().also { require(it in 0..4096) }
            val entries = (0 until count).map {
                val target = File(input.readUTF())
                require(target.isAbsolute && target.canonicalFile == target && validateTarget(target)) { "Unsafe restore recovery target" }
                val stagedName = input.readUTF()
                val originalName = input.readUTF()
                fun safeSibling(name: String, prefix: String): File {
                    require(name.startsWith(prefix) && name.endsWith("-" + target.name) && '/' !in name && '\\' !in name) {
                        "Unsafe restore recovery staging"
                    }
                    val sibling = File(target.parentFile, name)
                    require(sibling.canonicalFile == sibling) { "Unsafe restore recovery symlink" }
                    return sibling
                }
                Replacement(target, stagedName.takeIf { it.isNotEmpty() }?.let { safeSibling(it, ".restore-new-") },
                    safeSibling(originalName, ".restore-old-"), input.readBoolean())
            }
            require(entries.map { it.target }.toSet().size == entries.size) { "Duplicate restore recovery target" }
            val prefCount = input.readInt().also { require(it in 0..256) }
            val preferences = linkedMapOf<String, String>()
            repeat(prefCount) {
                val name = input.readUTF()
                require(name !in preferences) { "Duplicate restore preference snapshot" }
                val size = input.readInt().also { require(it in 0..MAX_JOURNAL_BYTES) }
                require(size <= input.available()) { "Incomplete restore preference snapshot" }
                val value = ByteArray(size)
                input.readFully(value)
                preferences[name] = String(value, Charsets.UTF_8)
            }
            require(input.read() == -1) { "Unexpected restore journal contents" }
            return Journal(phase, entries, preferences)
        }
    }
}
