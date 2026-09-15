package ohi.andre.consolelauncher.notes

import ohi.andre.consolelauncher.R

import android.content.Context
import android.util.AtomicFile
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.UUID

class NoteStore(private val context: Context) {
    companion object {
        // ponytail: serialize local file IO; use per-library locks if multiple libraries are added.
        val lock = Any()
        @Volatile var revision = 0L
            private set
    }
    class ReadException(cause: Throwable) : Exception("Unable to read notes; existing data was preserved.", cause)

    private val db = AtomicFile(File(context.filesDir, "remember-notes.xml"))
    private val libraryRoot = File(context.filesDir, "notes-library")

    fun hasDatabase(): Boolean = db.baseFile.exists() || File(db.baseFile.path + ".bak").exists()

    fun load(): NoteLibrary {
        synchronized(lock) {
            if (!hasDatabase()) {
                try {
                    return LauncherNotes.importLegacy(context).also(::save)
                } catch (e: Exception) {
                    throw ReadException(e)
                }
            }
            try {
                return db.openRead().use { input ->
                    val p = Xml.newPullParser().apply { setInput(input, "UTF-8") }
                    val folders = mutableListOf<NoteFolder>()
                    val notes = mutableListOf<Note>()
                    var version = 1
                    var rootSeen = false
                    var note: Note? = null
                    var checklist: Block.Checklist? = null
                    while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG) {
                        if (!rootSeen) {
                            require(p.name == "notes") { "Invalid note index" }
                            rootSeen = true
                        }
                        when (p.name) {
                        "notes" -> version = p.attr("version").toIntOrNull() ?: 1
                        "folder" -> folders.add(NoteFolder(p.attr("id"), p.attr("name")))
                        "note" -> note = Note(
                            id = p.attr("id"),
                            createdAt = p.long("createdAt"),
                            updatedAt = p.long("updatedAt"),
                            pinned = p.attr("pinned").equals("true", true),
                            locked = p.attr("locked").toBoolean(),
                            folderId = p.attr("folderId").takeIf(String::isNotBlank),
                            file = p.attr("file").takeIf(String::isNotBlank)
                        )
                        "title" -> note?.title = p.nextText()
                        "markdown" -> note?.markdown = p.nextText()
                        "text" -> note?.blocks?.add(Block.Text(p.attr("id"), p.nextText()))
                        "checklist" -> Block.Checklist(p.attr("id")).also { checklist = it; note?.blocks?.add(it) }
                        "item" -> checklist?.items?.add(Item(p.attr("id"), p.attr("checked").toBoolean(), p.nextText()))
                        "image" -> note?.blocks?.add(Block.Image(p.attr("id"), p.attr("file"), p.attr("alt")))
                        "location" -> note?.location = Place(p.attr("latitude").toDouble(), p.attr("longitude").toDouble(), p.attr("label"))
                        "tag" -> note?.tags?.add(p.nextText())
                        }
                    } else if (p.eventType == XmlPullParser.END_TAG) when (p.name) {
                        "checklist" -> checklist = null
                        "note" -> note?.let(notes::add)
                    }
                    require(rootSeen && version in 1..4) { "Unsupported note index" }
                    require(notes.map { it.id }.distinct().size == notes.size) { "Duplicate note IDs" }
                    val library = if (version < 2) NoteLibrary.fromLegacy(notes) else NoteLibrary(folders, notes).also {
                        val validFolders = folders.mapTo(HashSet(), NoteFolder::id)
                        notes.filter { item -> item.folderId != null && item.folderId !in validFolders }.forEach { item -> item.folderId = null }
                    }
                    if (version < 3) notes.forEach { MarkdownDocument.ensure(it) }
                    if (version >= 4) notes.forEach { item ->
                        val relative = item.file ?: error("Missing Markdown file for ${item.id}")
                        item.markdown = AtomicFile(ownedMarkdown(relative, mustExist = false)).openRead()
                            .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                    library
                }
            } catch (e: Exception) {
                throw ReadException(e)
            }
        }
    }

    fun save(library: NoteLibrary) {
        synchronized(lock) {
            val paths = library.notes.associate { it.id to desiredPath(it) }
            library.notes.forEach { note -> writeMarkdown(paths.getValue(note.id), MarkdownDocument.ensure(note)) }
            val out = db.startWrite()
            try {
                val x = Xml.newSerializer().apply {
                    setOutput(out, "UTF-8")
                    startDocument("UTF-8", true)
                    startTag(null, "notes")
                    attribute(null, "version", "4")
                }
                x.startTag(null, "folders")
                library.folders.forEach { folder ->
                    x.startTag(null, "folder"); x.attr("id", folder.id); x.attr("name", folder.name); x.endTag(null, "folder")
                }
                x.endTag(null, "folders")
                library.notes.forEach { item ->
                    MarkdownDocument.title(item.markdown)?.let { item.title = it }
                    item.blocks.clear()
                    x.startTag(null, "note")
                    x.attr("id", item.id)
                    x.attr("createdAt", item.createdAt)
                    x.attr("updatedAt", item.updatedAt)
                    x.attr("pinned", item.pinned)
                    x.attr("locked", item.locked)
                    x.attr("file", paths.getValue(item.id))
                    item.folderId?.let { x.attr("folderId", it) }
                    x.tag("title", item.title)
                    item.location?.let {
                        x.startTag(null, "location"); x.attr("latitude", it.latitude); x.attr("longitude", it.longitude); x.attr("label", it.label); x.endTag(null, "location")
                    }
                    x.startTag(null, "tags"); item.tags.forEach { x.tag("tag", it) }; x.endTag(null, "tags")
                    x.endTag(null, "note")
                }
                x.endTag(null, "notes")
                x.endDocument()
                db.finishWrite(out)
                revision++
            } catch (e: Exception) {
                db.failWrite(out)
                throw e
            }
            library.notes.forEach { note ->
                val relative = paths.getValue(note.id)
                note.file?.takeIf { it != relative }?.let { runCatching { ownedMarkdown(it).delete() } }
                note.file = relative
            }
            removeEmptyDirectories(libraryRoot)
        }
    }

    fun delete(note: Note, library: NoteLibrary) {
        synchronized(lock) {
            check(!note.locked) { context.getString(R.string.notes_storage_error_0) }
            library.notes.remove(note)
            save(library)
            note.file?.let { runCatching { ownedMarkdown(it).delete() } }
            (MarkdownDocument.imagePaths(note.markdown) + note.blocks.filterIsInstance<Block.Image>().map { it.file })
                .distinct()
                .forEach { runCatching { ownedAsset(it)?.delete() } }
            File(libraryRoot, ".assets/${note.id}").deleteRecursively()
            File(context.filesDir, "images/${note.id}").deleteRecursively()
            removeEmptyDirectories(libraryRoot)
        }
    }

    private fun desiredPath(note: Note): String {
        UUID.fromString(note.id)
        note.folderId?.let(UUID::fromString)
        return "notes-library/${note.folderId ?: "unfiled"}/${note.id}.md"
    }

    private fun writeMarkdown(relative: String, value: String) {
        val file = ownedMarkdown(relative, mustExist = false)
        val parent = requireNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { context.getString(R.string.notes_storage_error_1) }
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(value.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    private fun ownedMarkdown(relative: String, mustExist: Boolean = true): File {
        require(relative.endsWith(".md")) { context.getString(R.string.notes_storage_error_2) }
        val root = libraryRoot.canonicalFile
        val file = File(context.filesDir, relative).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) { context.getString(R.string.notes_storage_error_3) }
        if (mustExist) require(file.isFile) { context.getString(R.string.notes_storage_error_4) }
        return file
    }

    private fun ownedAsset(relative: String): File? = runCatching {
        val file = File(context.filesDir, relative).canonicalFile
        val roots = listOf(File(context.filesDir, "images"), File(libraryRoot, ".assets")).map(File::getCanonicalFile)
        file.takeIf { candidate -> roots.any { root -> candidate.path.startsWith(root.path + File.separator) } }
    }.getOrNull()

    private fun removeEmptyDirectories(directory: File) {
        directory.listFiles()?.filter(File::isDirectory)?.forEach(::removeEmptyDirectories)
        if (directory != libraryRoot && directory.listFiles()?.isEmpty() == true) directory.delete()
    }

    private fun XmlPullParser.attr(name: String) = getAttributeValue(null, name) ?: ""
    private fun XmlPullParser.long(name: String) = attr(name).toLong()
    private fun org.xmlpull.v1.XmlSerializer.attr(name: String, value: Any) { attribute(null, name, value.toString()) }
    private fun org.xmlpull.v1.XmlSerializer.tag(name: String, value: String) { startTag(null, name); text(value); endTag(null, name) }
}
