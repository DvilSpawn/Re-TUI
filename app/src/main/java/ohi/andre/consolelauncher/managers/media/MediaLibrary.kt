package ohi.andre.consolelauncher.managers.media

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import ohi.andre.consolelauncher.managers.mediaplayer.Lyrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * The folders the user chose for media, and what is in them. Folders are granted through the system folder picker,
 * so the launcher reads exactly those trees and nothing else, with no storage permission. A scan walks them once
 * and caches the result, so the library is instant until the next scan.
 */
object MediaLibrary {
    /** One playable file: its URI, a display name, the folder inside its tree, its kind, and subtitle or lyric files. */
    data class Item(val uri: String, val name: String, val folder: String, val video: Boolean,
        val subs: List<String> = emptyList(), val artist: String = "", val durationMs: Long = 0,
        /** Null until the file's tags have been read for an album. */ val album: String? = null)

    private const val PREFS = "media_library"
    private const val FOLDERS = "folders"
    private const val INDEX = "media-index.json"
    private const val MAX_ITEMS = 20_000
    private const val MAX_DEPTH = 12

    private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "mka", "amr", "3ga")
    private val VIDEO = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "ts", "m2ts", "wmv", "flv")

    @Volatile private var cached: List<Item>? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folders(context: Context): List<Uri> = runCatching {
        val array = JSONArray(prefs(context).getString(FOLDERS, "[]"))
        (0 until array.length()).map { Uri.parse(array.getString(it)) }
    }.getOrDefault(emptyList())

    /** "Music" for primary:Music, "SD/Podcasts" for 1234-5678:Podcasts. */
    fun label(uri: Uri): String = runCatching {
        val id = DocumentsContract.getTreeDocumentId(uri)
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':').ifEmpty { "/" }
        if (volume == "primary") path else "$volume/$path"
    }.getOrDefault(uri.toString())

    /** Keeps read access to [uri] across reboots and adds it to the library. */
    fun addFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val current = folders(context).map(Uri::toString)
        if (uri.toString() in current) return
        save(context, current + uri.toString())
    }

    /** Removes the folder at [index] and lets its access go; returns its label, or null when there is none. */
    fun removeFolder(context: Context, index: Int): String? {
        val current = folders(context)
        val uri = current.getOrNull(index) ?: return null
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        save(context, current.filterIndexed { i, _ -> i != index }.map(Uri::toString))
        return label(uri)
    }

    private fun save(context: Context, folders: List<String>) {
        prefs(context).edit().putString(FOLDERS, JSONArray(folders).toString()).apply()
    }

    /** The last scan's result; empty until the first scan. */
    fun items(context: Context): List<Item> {
        cached?.let { return it }
        return read(context).also { cached = it }
    }

    /** Walks every chosen folder and caches what it finds. Slow on large trees: call it off the main thread. */
    fun scan(context: Context): List<Item> {
        val found = ArrayList<Item>()
        folders(context).forEach { tree ->
            runCatching {
                walk(context, tree, DocumentsContract.getTreeDocumentId(tree), label(tree), 0, found)
            }
        }
        val known = read(context).associateBy { it.uri }
        val sorted = found.distinctBy { it.uri }.map { withTags(context, it, known[it.uri]) }.sortedWith(compareBy({ it.folder.lowercase(Locale.ROOT) }, { it.name.lowercase(Locale.ROOT) }))
        write(context, sorted)
        cached = sorted
        return sorted
    }

    /** Reads a file's tags once; later scans reuse them, so only new files cost anything. */
    private fun withTags(context: Context, item: Item, known: Item?): Item {
        if (known != null && known.durationMs > 0 && known.album != null) return item.copy(name = known.name, artist = known.artist, durationMs = known.durationMs, album = known.album)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(item.uri))
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            item.copy(
                // Video files keep their file names: tags there are often leftovers, and episodes are named by file.
                name = if (!item.video && title.isNotBlank()) title else item.name,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            )
        } catch (e: Exception) {
            item
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun walk(context: Context, tree: Uri, documentId: String, folder: String, depth: Int, out: MutableList<Item>) {
        if (depth > MAX_DEPTH || out.size >= MAX_ITEMS) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val here = ArrayList<Item>()
        val subtitles = ArrayList<Pair<String, String>>()
        val lyrics = ArrayList<Pair<String, String>>()
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && out.size + here.size < MAX_ITEMS) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2).orEmpty()
                if (name.startsWith(".")) continue
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(context, tree, id, "$folder/$name", depth + 1, out)
                    continue
                }
                if (Lyrics.isLyricFile(name)) {
                    lyrics += name to DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                    continue
                }
                if (VideoMath.subtitleMime(name) != null) {
                    subtitles += name to DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                    continue
                }
                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val video = mime.startsWith("video/") || ext in VIDEO
                val audio = mime.startsWith("audio/") || ext in AUDIO
                if (!audio && !video) continue
                here += Item(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                    name.substringBeforeLast('.'), folder, video)
            }
        }
        here.mapTo(out) { item ->
            val beside = if (item.video) subtitles else lyrics.sortedBy { !it.first.endsWith(".lrc", ignoreCase = true) }
            item.copy(subs = beside.filter { VideoMath.belongsTo(it.first, item.name) }.map { it.second })
        }
    }

    private fun indexFile(context: Context) = AtomicFile(File(context.filesDir, INDEX))

    private fun read(context: Context): List<Item> = runCatching {
        val file = indexFile(context)
        if (!file.baseFile.exists()) return emptyList()
        val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val subs = o.optJSONArray("subs")
            Item(o.getString("uri"), o.getString("name"), o.optString("folder"), o.optBoolean("video"),
                if (subs == null) emptyList() else (0 until subs.length()).map(subs::getString),
                o.optString("artist"), o.optLong("duration"), if (o.has("album")) o.getString("album") else null)
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, items: List<Item>) {
        val file = indexFile(context)
        val out = file.startWrite()
        try {
            val array = JSONArray()
            items.forEach {
                array.put(JSONObject().put("uri", it.uri).put("name", it.name).put("folder", it.folder).put("video", it.video)
                    .put("subs", JSONArray(it.subs)).put("artist", it.artist).put("duration", it.durationMs).put("album", it.album))
            }
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    fun clearCache() { cached = null }
}
