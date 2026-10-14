package ohi.andre.consolelauncher.managers

import java.io.File
import java.util.UUID

/** Originals stay on the same filesystem until every replacement and preference write succeeds. */
internal object BackupRestoreTransaction {
    private data class Replacement(val target: File, val staged: File?, val original: File) {
        var movedOriginal = false
        var installed = false
    }

    fun replace(replacements: Map<File, File?>, commit: () -> Unit, rollback: () -> Unit) {
        val id = UUID.randomUUID().toString()
        val prepared = mutableListOf<Replacement>()
        var succeeded = false
        try {
            replacements.forEach { (target, source) ->
                val parent = requireNotNull(target.parentFile)
                check(parent.isDirectory || parent.mkdirs()) { "Unable to prepare restore folder" }
                val staged = source?.let { File(parent, ".restore-new-$id-${target.name}") }
                val item = Replacement(target, staged, File(parent, ".restore-old-$id-${target.name}"))
                prepared.add(item)
                if (source != null) {
                    check(source.exists()) { "Missing staged backup file" }
                    source.copyRecursively(requireNotNull(staged), overwrite = false)
                }
            }
            prepared.forEach { item ->
                if (item.target.exists()) {
                    check(item.target.renameTo(item.original)) { "Unable to preserve current setup" }
                    item.movedOriginal = true
                }
                if (item.staged != null) {
                    check(item.staged.renameTo(item.target)) { "Unable to install backup file" }
                    item.installed = true
                }
            }
            commit()
            succeeded = true
        } catch (failure: Exception) {
            prepared.asReversed().forEach { item ->
                try {
                    if (item.installed) check(item.target.deleteRecursively()) { "Unable to remove failed restore" }
                    if (item.movedOriginal) check(item.original.renameTo(item.target)) {
                        "Unable to roll back ${item.target.name}; original retained at ${item.original}"
                    }
                } catch (restoreFailure: Exception) { failure.addSuppressed(restoreFailure) }
            }
            try { rollback() } catch (restoreFailure: Exception) { failure.addSuppressed(restoreFailure) }
            throw failure
        } finally {
            prepared.forEach { item ->
                item.staged?.deleteRecursively()
                // A failed rollback must retain its recovery copy.
                if (succeeded) item.original.deleteRecursively()
            }
        }
    }
}
