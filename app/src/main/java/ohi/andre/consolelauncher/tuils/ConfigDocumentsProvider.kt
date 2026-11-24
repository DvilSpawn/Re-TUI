package ohi.andre.consolelauncher.tuils

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/**
 * Shows the Re-T-UI folder (config XML, presets, ascii.txt, …) in the system file picker and
 * Files app, so the files can be opened in any editor. Needed for the Play Store build, whose
 * folder sits in app-owned storage that other apps can't reach without root.
 */
class ConfigDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    private fun root(): File {
        var folder = Tuils.getFolder()
        if (!folder.isDirectory) {
            context?.let { Tuils.init(it) }
            folder = Tuils.getFolder()
        }
        if (!folder.isDirectory) throw FileNotFoundException("Re-T-UI folder unavailable")
        return folder.canonicalFile
    }

    private fun idFor(file: File): String {
        val root = root().path
        val path = file.canonicalPath
        return if (path == root) ROOT_ID else ROOT_ID + path.substring(root.length)
    }

    private fun fileFor(documentId: String): File {
        if (!documentId.startsWith(ROOT_ID)) throw FileNotFoundException(documentId)
        val root = root()
        val file = File(root, documentId.substring(ROOT_ID.length)).canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) throw FileNotFoundException(documentId)
        return file
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: ROOT_COLUMNS)
        val ctx = context ?: return result
        result.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            add(Root.COLUMN_TITLE, ctx.applicationInfo.loadLabel(ctx.packageManager).toString())
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY)
            add(Root.COLUMN_ICON, ctx.applicationInfo.icon)
            add(Root.COLUMN_MIME_TYPES, "*/*")
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        addRow(result, fileFor(documentId))
        return result
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val result = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        fileFor(parentDocumentId).listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) }?.forEach { addRow(result, it) }
        return result
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = fileFor(documentId)
        if (file.isDirectory) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileFor(parentDocumentId)
        val clean = displayName.replace('/', '_').ifBlank { "untitled" }
        var file = File(parent, clean)
        var n = 2
        while (file.exists()) file = File(parent, "$clean ($n)").also { n++ }
        val ok = if (mimeType == Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()
        if (!ok) throw FileNotFoundException("Couldn't create $clean")
        return idFor(file)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileFor(documentId)
        if (file == root() || !file.deleteRecursively()) throw FileNotFoundException(documentId)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = fileFor(documentId)
        val target = File(file.parentFile, displayName.replace('/', '_'))
        if (file == root() || target.exists() || !file.renameTo(target)) throw FileNotFoundException(documentId)
        return idFor(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = fileFor(parentDocumentId).path
        val child = fileFor(documentId).path
        return child.startsWith(parent + File.separator)
    }

    override fun getDocumentType(documentId: String): String = mimeOf(fileFor(documentId))

    private fun addRow(cursor: MatrixCursor, file: File) {
        val writable = file.canWrite()
        var flags = 0
        if (file.isDirectory) {
            if (writable) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (writable) {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
        }
        if (writable && file != root()) flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, idFor(file))
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_MIME_TYPE, mimeOf(file))
            add(Document.COLUMN_SIZE, if (file.isDirectory) null else file.length())
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    private fun mimeOf(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "xml" -> "text/xml"
            "txt", "lua", "sh", "log" -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    companion object {
        private const val ROOT_ID = "retui:"
        private val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_MIME_TYPES
        )
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS
        )
    }
}
