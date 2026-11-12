package ohi.andre.consolelauncher.notes

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One timestamped block in a day of the journal. */
data class JournalEntry(val day: String, val time: String, val text: String)

/**
 * A day of the journal is a Markdown file, `2026-11-04.md`, with one `## HH:MM` section per entry. Days are
 * plain files so they stay readable and easy to export.
 */
object JournalFormat {
    private val heading = Regex("^## (\\d{2}:\\d{2})\\s*$")
    val dayPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    val tag = Regex("(?<![\\p{L}\\p{N}_#])#([\\p{L}\\p{N}_-]+)")

    fun parse(day: String, markdown: String): List<JournalEntry> {
        val entries = mutableListOf<JournalEntry>()
        var time: String? = null
        val body = StringBuilder()
        fun flush() {
            time?.let { entries.add(JournalEntry(day, it, body.toString().trim('\n').trimEnd())) }
            body.setLength(0)
        }
        markdown.lines().forEach { line ->
            val match = heading.find(line)
            when {
                match != null -> { flush(); time = match.groupValues[1] }
                time != null -> body.append(line).append('\n')
            }
        }
        flush()
        return entries
    }

    fun render(day: String, entries: List<JournalEntry>): String = buildString {
        append("# ").append(day).append("\n")
        entries.forEach { append("\n## ").append(it.time).append("\n").append(it.text.trimEnd()).append("\n") }
    }

    fun day(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))
    fun clock(time: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(time))

    fun shift(day: String, days: Int): String {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day) ?: return day
        return Calendar.getInstance().run {
            this.time = parsed
            add(Calendar.DAY_OF_MONTH, days)
            day(timeInMillis)
        }
    }

    /** "wed 04 nov" */
    fun label(day: String): String {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day) ?: return day
        return SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(parsed).lowercase(Locale.getDefault())
    }

    fun tags(text: String): List<String> = tag.findAll(text).map { it.groupValues[1] }.toList()
}

class JournalStore(context: Context) {
    companion object {
        val lock = Any()
        @Volatile var revision = 0L
            private set
    }

    private val root = File(context.filesDir, "journal")

    fun days(): List<String> = root.list()?.filter { it.endsWith(".md") }?.map { it.removeSuffix(".md") }
        ?.filter { JournalFormat.dayPattern.matches(it) }?.sorted().orEmpty()

    fun load(day: String): List<JournalEntry> = synchronized(lock) {
        val file = file(day)
        if (!file.baseFile.exists()) return emptyList()
        JournalFormat.parse(day, file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    fun append(time: Long, text: String) {
        if (text.isBlank()) return
        val day = JournalFormat.day(time)
        synchronized(lock) { write(day, (load(day) + JournalEntry(day, JournalFormat.clock(time), text.trimEnd()))
            .sortedBy { it.time }) }
    }

    fun replace(entry: JournalEntry, text: String?) = synchronized(lock) {
        val entries = load(entry.day).toMutableList()
        val index = entries.indexOf(entry)
        if (index < 0) return
        if (text.isNullOrBlank()) entries.removeAt(index) else entries[index] = entry.copy(text = text.trimEnd())
        write(entry.day, entries)
    }

    fun search(query: String): List<JournalEntry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return days().reversed().flatMap { day -> load(day).filter { JournalImages.strip(it.text).contains(q, ignoreCase = true) }.reversed() }
    }

    private fun write(day: String, entries: List<JournalEntry>) {
        val file = file(day)
        if (entries.isEmpty()) {
            file.delete()
            revision++
            return
        }
        check(root.isDirectory || root.mkdirs())
        val out = file.startWrite()
        try {
            out.write(JournalFormat.render(day, entries).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            revision++
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun file(day: String): AtomicFile {
        require(JournalFormat.dayPattern.matches(day)) { "Invalid journal day" }
        return AtomicFile(File(root, "$day.md"))
    }
}

/**
 * Images attached to the journal live next to the day files, under `journal/assets/<day>/`, and entries point at
 * them with a relative Markdown line, `![](assets/2026-11-04/<id>.jpg)`. Images moved over from the old notes
 * library keep their library paths.
 */
object JournalImages {
    val line = Regex("^!\\[([^]]*)]\\(([^)\\s]+)\\)\\s*$")
    private val any = Regex("!\\[[^]]*]\\(([^)\\s]+)\\)")
    private const val MAX_SIDE = 2048

    fun paths(text: String): Set<String> = any.findAll(text).map { it.groupValues[1] }.toSet()

    fun strip(text: String): String = any.replace(text, "")

    /** The private file behind a path, only inside the journal's assets or the old library's images. */
    fun file(context: Context, path: String): File? = runCatching {
        val files = context.filesDir
        val candidate = (if (path.startsWith("assets/")) File(files, "journal/$path") else File(files, path)).canonicalFile
        val roots = listOf(File(files, "journal/assets"), File(files, "notes-library/.assets"), File(files, "images"))
            .map(File::getCanonicalFile)
        candidate.takeIf { file -> file.isFile && roots.any { file.path.startsWith(it.path + File.separator) } }
    }.getOrNull()

    /** Copies a picked or captured image into today's assets: upright, at most 2048px on its long side, as JPEG. */
    fun import(context: Context, source: android.net.Uri, day: String): String? {
        require(JournalFormat.dayPattern.matches(day))
        val dir = File(context.filesDir, "journal/assets/$day").apply { mkdirs() }
        val name = "${java.util.UUID.randomUUID()}.jpg"
        val temp = File(context.cacheDir, "journal-import-$name")
        return try {
            context.contentResolver.openInputStream(source)?.use { input -> temp.outputStream().use(input::copyTo) } ?: return null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            var bitmap = android.graphics.BitmapFactory.decodeFile(temp.path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val matrix = android.graphics.Matrix()
            if (scale < 1f) matrix.postScale(scale, scale)
            val degrees = when (android.media.ExifInterface(temp.path).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) matrix.postRotate(degrees)
            if (!matrix.isIdentity) bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            File(dir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            "assets/$day/$name"
        } catch (e: Exception) {
            File(dir, name).delete()
            null
        } finally {
            temp.delete()
        }
    }

    /** Deletes journal images no entry points at any more; [keep] guards ones a draft still holds. */
    fun prune(context: Context, store: JournalStore, keep: Set<String> = emptySet()) {
        val root = File(context.filesDir, "journal/assets")
        root.listFiles()?.filter(File::isDirectory)?.forEach { dir ->
            val day = dir.name
            if (!JournalFormat.dayPattern.matches(day)) return@forEach
            val used = store.load(day).flatMap { paths(it.text) }.toSet() + keep
            dir.listFiles()?.forEach { file -> if ("assets/$day/${file.name}" !in used) file.delete() }
            if (dir.listFiles()?.isEmpty() == true) dir.delete()
        }
    }
}
