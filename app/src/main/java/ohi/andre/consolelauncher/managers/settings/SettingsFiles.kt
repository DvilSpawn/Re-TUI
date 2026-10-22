package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.BackupRestoreTransaction
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File

/** Settings and restores share a lock so staging can never observe half a restore. */
internal object SettingsFiles {
    val lock: Any get() = BackupRestoreTransaction
    private fun journal(context: Context?, root: File) = File(
        context?.noBackupFilesDir ?: root, "settings-transaction/journal")

    private fun allowed(root: File, target: File): Boolean =
        target.parentFile == root.canonicalFile &&
            (target.name in XMLPrefsManager.XMLPrefsRoot.entries.map { it.path } || target.name == "frames")

    fun recover(context: Context?, root: File = Tuils.getFolder()) = synchronized(lock) {
        BackupRestoreTransaction.recover(journal(context, root), { allowed(root, it) }, {},
            { if (context != null) BackupManager.syncRestoreDirectory(it) })
    }

    fun replace(context: Context?, replacements: Map<File, File?>, root: File = Tuils.getFolder(),
                publish: () -> Unit = {}) = synchronized(lock) {
        if (replacements.isEmpty()) { publish(); return@synchronized }
        recover(context, root)
        BackupRestoreTransaction.replace(replacements, journal(context, root), emptyMap(),
            { allowed(root, it) }, {},
            { if (context != null) BackupManager.syncRestoreDirectory(it) }, commit = {})
        // A failed transaction never changes the in-memory configuration.
        publish()
    }
}
