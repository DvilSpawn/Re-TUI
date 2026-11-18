package ohi.andre.consolelauncher.managers.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.AtomicFile
import ohi.andre.consolelauncher.managers.media.MediaLibrary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * The books the user chose, and how far they have read each. Folders and single files are granted through the system
 * pickers, so the reader needs no storage permission. A scan walks the folders once and caches what it finds.
 */
object Books {
    enum class Format { EPUB, PDF, TEXT, MARKDOWN, COMIC }

    data class Item(val uri: String, val name: String, val folder: String, val format: Format)

    /** Where a reader stopped: a chapter and a character in it for flowing books, a page for fixed ones. */
    data class Place(val chapter: Int = 0, val offset: Int = 0, val fraction: Float = 0f, val title: String = "",
        val chapterTitle: String = "", val page: Int = 0, val pages: Int = 0, val openedAt: Long = 0L,
        val marks: List<Pair<Int, Int>> = emptyList(), val pins: List<Pin> = emptyList())

    /** A pinned passage of a flowing book: characters [start] to [end] of a chapter, with the words kept to list it. */
    data class Pin(val chapter: Int, val start: Int, val end: Int, val text: String)

    private const val PREFS = "book_library"
    private const val FOLDERS = "folders"
    private const val FILES = "files"
    private const val INDEX = "book-index.json"
    private const val PLACES = "book_places"
    private const val MAX_ITEMS = 5_000
    private const val MAX_DEPTH = 12

    private val TYPES = mapOf("epub" to Format.EPUB, "pdf" to Format.PDF, "txt" to Format.TEXT, "md" to Format.MARKDOWN,
        "markdown" to Format.MARKDOWN, "cbz" to Format.COMIC)

    @Volatile private var cached: List<Item>? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun places(context: Context) = context.getSharedPreferences(PLACES, Context.MODE_PRIVATE)

    fun formatOf(name: String): Format? = TYPES[name.substringAfterLast('.', "").lowercase(Locale.ROOT)]

    private fun list(context: Context, key: String): List<String> = runCatching {
        val array = JSONArray(prefs(context).getString(key, "[]"))
        (0 until array.length()).map(array::getString)
    }.getOrDefault(emptyList())

    private fun save(context: Context, key: String, values: List<String>) =
        prefs(context).edit().putString(key, JSONArray(values).toString()).apply()

    fun folders(context: Context): List<Uri> = list(context, FOLDERS).map(Uri::parse)

    /** Keeps read access to [uri] across reboots and adds it to the books. */
    fun addFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val current = list(context, FOLDERS)
        if (uri.toString() !in current) save(context, FOLDERS, current + uri.toString())
    }

    /** Adds one picked file; returns it, or null when it is not a kind of book the reader opens. */
    fun addFile(context: Context, uri: Uri): Item? {
        val name = displayName(context, uri) ?: return null
        val format = formatOf(name) ?: return null
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val current = list(context, FILES)
        if (uri.toString() !in current) save(context, FILES, current + uri.toString())
        val item = Item(uri.toString(), name.substringBeforeLast('.'), "", format)
        cached = (items(context).filter { it.uri != item.uri } + item)
        return item
    }

    /** Drops a book from the list, and its folder grant when it came alone. Its place is forgotten too. */
    fun remove(context: Context, uri: String) {
        val files = list(context, FILES)
        if (uri in files) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            save(context, FILES, files - uri)
        }
        places(context).edit().remove(uri).apply()
        cached = items(context).filter { it.uri != uri }
        write(context, cached.orEmpty())
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    fun items(context: Context): List<Item> {
        cached?.let { return it }
        return read(context).also { cached = it }
    }

    fun find(context: Context, uri: String): Item? = items(context).firstOrNull { it.uri == uri }

    /** Walks every folder and adds the single files. Slow on large trees: call it off the main thread. */
    fun scan(context: Context): List<Item> {
        val found = ArrayList<Item>()
        folders(context).forEach { tree ->
            runCatching { walk(context, tree, DocumentsContract.getTreeDocumentId(tree), MediaLibrary.label(tree), 0, found) }
        }
        list(context, FILES).forEach { raw ->
            val uri = Uri.parse(raw)
            val name = displayName(context, uri) ?: return@forEach
            formatOf(name)?.let { found += Item(raw, name.substringBeforeLast('.'), "", it) }
        }
        val sorted = found.distinctBy { it.uri }.sortedBy { it.name.lowercase(Locale.ROOT) }
        write(context, sorted)
        cached = sorted
        return sorted
    }

    private fun walk(context: Context, tree: Uri, documentId: String, folder: String, depth: Int, out: MutableList<Item>) {
        if (depth > MAX_DEPTH || out.size >= MAX_ITEMS) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && out.size < MAX_ITEMS) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(".")) continue
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(context, tree, id, "$folder/$name", depth + 1, out)
                    continue
                }
                val format = formatOf(name) ?: continue
                out += Item(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), name.substringBeforeLast('.'), folder, format)
            }
        }
    }

    private fun indexFile(context: Context) = AtomicFile(File(context.filesDir, INDEX))

    private fun read(context: Context): List<Item> = runCatching {
        val file = indexFile(context)
        if (!file.baseFile.exists()) return emptyList()
        val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val format = runCatching { Format.valueOf(o.getString("format")) }.getOrNull() ?: return@mapNotNull null
            Item(o.getString("uri"), o.getString("name"), o.optString("folder"), format)
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, items: List<Item>) {
        val file = indexFile(context)
        val out = file.startWrite()
        try {
            val array = JSONArray()
            items.forEach { array.put(JSONObject().put("uri", it.uri).put("name", it.name).put("folder", it.folder).put("format", it.format.name)) }
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    // ---- places ----

    fun place(context: Context, uri: String): Place? = places(context).getString(uri, null)?.let { raw ->
        runCatching {
            val o = JSONObject(raw)
            val marks = o.optJSONArray("marks")
            val pins = o.optJSONArray("pins")
            Place(o.optInt("chapter"), o.optInt("offset"), o.optDouble("fraction", 0.0).toFloat(), o.optString("title"),
                o.optString("chapterTitle"), o.optInt("page"), o.optInt("pages"), o.optLong("openedAt"),
                if (marks == null) emptyList() else (0 until marks.length()).map { marks.getJSONArray(it).let { m -> m.getInt(0) to m.getInt(1) } },
                if (pins == null) emptyList() else (0 until pins.length()).map { pins.getJSONObject(it).let { p ->
                    Pin(p.getInt("chapter"), p.getInt("start"), p.getInt("end"), p.optString("text")) } })
        }.getOrNull()
    }

    fun keep(context: Context, uri: String, place: Place) {
        val o = JSONObject().put("chapter", place.chapter).put("offset", place.offset).put("fraction", place.fraction.toDouble())
            .put("title", place.title).put("chapterTitle", place.chapterTitle).put("page", place.page).put("pages", place.pages)
            .put("openedAt", place.openedAt).put("marks", JSONArray(place.marks.map { JSONArray().put(it.first).put(it.second) }))
            .put("pins", JSONArray(place.pins.map { JSONObject().put("chapter", it.chapter).put("start", it.start).put("end", it.end).put("text", it.text) }))
        places(context).edit().putString(uri, o.toString()).apply()
    }

    /** The book read most recently, with where it stopped; null before any book was opened. */
    /** Every book still in the library that has been opened, the most recent first. */
    private fun opened(context: Context): List<Pair<Item, Place>> {
        val known = items(context).associateBy { it.uri }
        return places(context).all.keys.mapNotNull { uri -> known[uri]?.let { item -> place(context, uri)?.let { item to it } } }
            .sortedByDescending { it.second.openedAt }
    }

    fun last(context: Context): Pair<Item, Place>? = opened(context).firstOrNull()

    /** The kind a book is shelved as: Markdown sits with plain text. */
    fun kindOf(format: Format): Format = if (format == Format.MARKDOWN) Format.TEXT else format

    /** The book read last of each kind, the most recent first. */
    fun lastByKind(context: Context): List<Pair<Item, Place>> = opened(context).distinctBy { kindOf(it.first.format) }

    /** The kind named by [word] (epub, pdf, text, txt, md, comic, cbz), or null. */
    fun kindNamed(word: String): Format? = when (word.lowercase(Locale.ROOT)) {
        "epub" -> Format.EPUB
        "pdf" -> Format.PDF
        "text", "txt", "md", "markdown" -> Format.TEXT
        "comic", "comics", "cbz" -> Format.COMIC
        else -> null
    }

    /** A private copy of the book, so zips and PDFs can be read at random; copied again only when the size changes. */
    fun localCopy(context: Context, uri: Uri): File {
        val dir = File(context.cacheDir, "books").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(dir, hash)
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        }.getOrNull() ?: -1L
        if (file.exists() && (size < 0 || file.length() == size)) return file
        val temp = File(dir, "$hash.part")
        context.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
        temp.renameTo(file)
        // Only the few most recent books are kept.
        dir.listFiles()?.filter { !it.name.endsWith(".part") }?.sortedByDescending { it.lastModified() }?.drop(CACHED_BOOKS)?.forEach { it.delete() }
        return file.apply { setLastModified(System.currentTimeMillis()) }
    }

    private const val CACHED_BOOKS = 6
}
