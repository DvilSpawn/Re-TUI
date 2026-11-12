package ohi.andre.consolelauncher.notes

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A one-line note shown on the status pane: tick it off, pin it, or hide it behind ▓. */
data class QuickNote(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    val createdAt: Long = System.currentTimeMillis(),
    var pinned: Boolean = false,
    var done: Boolean = false,
    var hidden: Boolean = false
)

/** Pinned first, then open lines newest first, then done lines. */
fun List<QuickNote>.ordered(): List<QuickNote> =
    sortedWith(compareBy<QuickNote>({ !it.pinned }, { it.done }).thenByDescending { it.createdAt })

object QuickNoteFormat {
    /** Keeps quick notes to one line; anything longer belongs in the journal. */
    fun clean(text: String): String = text.replace(Regex("\\s*\\n\\s*"), " ").trim()

    fun encode(notes: List<QuickNote>): String = JSONArray().apply {
        notes.forEach { note ->
            put(JSONObject()
                .put("id", note.id).put("text", note.text).put("createdAt", note.createdAt)
                .put("pinned", note.pinned).put("done", note.done).put("hidden", note.hidden))
        }
    }.toString(1)

    fun decode(raw: String): MutableList<QuickNote> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            QuickNote(
                id = item.getString("id"),
                text = item.getString("text"),
                createdAt = item.optLong("createdAt"),
                pinned = item.optBoolean("pinned"),
                done = item.optBoolean("done"),
                hidden = item.optBoolean("hidden")
            )
        }.toMutableList()
    }
}

class QuickNoteStore(private val context: Context) {
    companion object {
        val lock = Any()
        @Volatile var revision = 0L
            private set
    }

    private val file = AtomicFile(File(context.filesDir, "quick-notes.json"))

    fun load(): MutableList<QuickNote> = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            NotesSplit.migrate(context)
        }
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return mutableListOf()
        QuickNoteFormat.decode(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    fun save(notes: List<QuickNote>) = synchronized(lock) {
        val out = file.startWrite()
        try {
            out.write(QuickNoteFormat.encode(notes).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            revision++
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    fun mutate(action: (MutableList<QuickNote>) -> Unit): MutableList<QuickNote> = synchronized(lock) {
        load().also { action(it); save(it) }
    }

    fun add(text: String, pinned: Boolean = false) = QuickNoteFormat.clean(text).takeIf(String::isNotEmpty)?.let { line ->
        mutate { it.add(QuickNote(text = line, pinned = pinned)) }
    }
}

/**
 * Splits the old notes library once: short notes become quick notes, longer ones become journal entries on the
 * day they were written. The library itself is left on disk untouched, as a recovery copy.
 */
object NotesSplit {
    private const val PREFS = "notes-split"
    private const val DONE = "migrated"

    fun migrate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (prefs.getBoolean(DONE, false)) return
        val library = runCatching { NoteStore(context).load() }.getOrNull()
        val quick = mutableListOf<QuickNote>()
        val journal = JournalStore(context)
        library?.notes?.sortedBy { it.createdAt }?.forEach { note ->
            val body = body(note)
            if (isQuick(note, body)) {
                quick.add(QuickNote(id = note.id, text = QuickNoteFormat.clean(body.ifBlank { note.title }),
                    createdAt = note.createdAt, pinned = note.pinned || note.locked))
            } else {
                val title = note.title.takeIf { it.isNotBlank() && it != "Untitled note" }
                journal.append(note.createdAt, listOfNotNull(title?.let { "**$it**" }, body.trim().ifEmpty { null })
                    .joinToString("\n"))
            }
        }
        QuickNoteStore(context).save(quick)
        prefs.edit().putBoolean(DONE, true).apply()
    }

    /** The note's text without the "# title" line the library adds. */
    internal fun body(note: Note): String {
        val lines = note.markdown.lines()
        val rest = if (lines.firstOrNull()?.startsWith("# ") == true) lines.drop(1) else lines
        return rest.joinToString("\n").trim()
    }

    internal fun isQuick(note: Note, body: String): Boolean {
        val text = body.ifBlank { note.title }
        return text.isNotBlank() && !text.contains('\n') && text.length <= 120 &&
            !text.contains("![") && (body.isBlank() || note.title.isBlank() || note.title == "Untitled note" ||
                note.title == LauncherNotes.legacyTitle(body))
    }
}
