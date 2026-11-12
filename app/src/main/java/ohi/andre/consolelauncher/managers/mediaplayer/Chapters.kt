package ohi.andre.consolelauncher.managers.mediaplayer

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import org.json.JSONObject

internal data class Chapter(val startMs: Int, val title: String)

/** Podcast chapters, from a feed's chapters file or from the ID3 tags inside the audio. */
internal object Chapters {
    /** The Podcasting 2.0 JSON chapters format; entries marked "toc": false are not for the list. */
    fun parseJson(raw: String): List<Chapter> = runCatching {
        val array = JSONObject(raw).getJSONArray("chapters")
        (0 until array.length()).map(array::getJSONObject)
            .filter { it.optBoolean("toc", true) && it.has("startTime") }
            .map { Chapter((it.getDouble("startTime") * 1000).toInt(), it.optString("title")) }
            .sortedBy { it.startMs }
    }.getOrDefault(emptyList())

    fun indexAt(chapters: List<Chapter>, positionMs: Int): Int = chapters.indexOfLast { it.startMs <= positionMs }

    @OptIn(UnstableApi::class)
    fun fromMetadata(metadata: Metadata): List<Chapter> =
        (0 until metadata.length()).mapNotNull { metadata.get(it) as? ChapterFrame }.map { frame ->
            val title = (0 until frame.subFrameCount).map(frame::getSubFrame).filterIsInstance<TextInformationFrame>()
                .firstOrNull { it.id == "TIT2" }?.values?.firstOrNull().orEmpty()
            Chapter(frame.startTimeMs, title)
        }.sortedBy { it.startMs }
}
