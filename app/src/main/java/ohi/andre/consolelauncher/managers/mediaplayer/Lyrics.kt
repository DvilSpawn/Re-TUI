package ohi.andre.consolelauncher.managers.mediaplayer

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import ohi.andre.consolelauncher.managers.media.MediaLibrary
import ohi.andre.consolelauncher.managers.music.Song
import java.io.File

/** One lyric line; [ms] is null in a plain text file, which has no timing. */
internal data class LyricLine(val ms: Int?, val text: String)

/** Lyrics from a .lrc or .txt file beside the song. */
internal object Lyrics {
    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()
    private val STAMP = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val TAG = Regex("""^\[[a-zA-Z]+:.*]$""")
    private val OFFSET = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<LyricLine> {
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()
        var offset = 0
        raw.removePrefix(BYTE_ORDER_MARK).lineSequence().map(String::trim).forEach { line ->
            OFFSET.find(line)?.let { offset = it.groupValues[1].toInt() }
            var rest = line
            val stamps = ArrayList<Int>()
            while (true) {
                val match = STAMP.find(rest) ?: break
                stamps += toMs(match)
                rest = rest.substring(match.range.last + 1)
            }
            when {
                stamps.isNotEmpty() -> stamps.mapTo(timed) { LyricLine(it, rest.trim()) }
                !TAG.matches(line) -> plain += LyricLine(null, line)
            }
        }
        // A positive offset means the words come sooner.
        if (timed.isNotEmpty()) return timed.map { it.copy(ms = (it.ms!! - offset).coerceAtLeast(0)) }.sortedBy { it.ms }
        return plain.dropWhile { it.text.isEmpty() }.dropLastWhile { it.text.isEmpty() }
    }

    private fun toMs(match: MatchResult): Int {
        val (minutes, seconds, fraction) = match.destructured
        val ms = fraction.padEnd(3, '0').take(3).toInt()
        return minutes.toInt() * 60_000 + seconds.toInt() * 1000 + ms
    }

    /** The line being sung at [positionMs], or -1 before the first or for untimed lyrics. */
    fun indexAt(lines: List<LyricLine>, positionMs: Int): Int = lines.indexOfLast { it.ms != null && it.ms <= positionMs }

    /** Reads the song's lyric file, if it has one. Call it off the main thread. */
    fun load(context: Context, song: Song): List<LyricLine> = runCatching {
        val path = song.getPath() ?: dataPath(context, song.getID())
        val text = when {
            path == null -> null
            path.startsWith("content:") -> MediaLibrary.items(context).firstOrNull { it.uri == path }?.subs?.firstOrNull()
                ?.let { uri -> context.contentResolver.openInputStream(Uri.parse(uri))?.bufferedReader()?.use { it.readText() } }
            path.contains("://") -> null
            else -> besideFile(File(path))?.readText()
        }
        text?.let(::parse).orEmpty()
    }.getOrDefault(emptyList())

    fun isLyricFile(name: String) = name.substringAfterLast('.').lowercase() in setOf("lrc", "txt")

    private fun besideFile(audio: File): File? = listOf("lrc", "txt")
        .map { File(audio.parentFile, audio.nameWithoutExtension + "." + it) }.firstOrNull(File::canRead)

    private fun dataPath(context: Context, id: Long): String? {
        if (id == -1L) return null
        @Suppress("DEPRECATION") val column = MediaStore.Audio.Media.DATA
        return context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(column),
            "${MediaStore.Audio.Media._ID}=?", arrayOf(id.toString()), null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }
}
