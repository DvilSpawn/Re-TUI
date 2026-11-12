package ohi.andre.consolelauncher.managers.media

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The video player's arithmetic, kept apart from its views so it can be tested. */
internal object VideoMath {
    /** 4:12, or 1:04:12 past an hour. */
    fun clock(ms: Long): String {
        val total = abs(ms) / 1000
        val minutes = total / 60 % 60
        val seconds = (total % 60).toString().padStart(2, '0')
        return if (total >= 3600) "${total / 3600}:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
    }

    /** How far one tick of a held -10 or +10 moves: quicker the longer it is held, so long films stay reachable. */
    fun holdStepMs(heldMs: Long): Long = (4_000 + heldMs * 2).coerceAtMost(30_000)

    fun isDone(positionMs: Long, durationMs: Long): Boolean = durationMs > 0 && positionMs >= durationMs * 95 / 100

    /** Where to start a video: its saved place, or the beginning once it has been watched. */
    fun resumeAt(positionMs: Long, durationMs: Long): Long = if (isDone(positionMs, durationMs)) 0 else positionMs.coerceAtLeast(0)

    /** Past a few seconds and not finished: worth asking whether to carry on. */
    fun canResume(positionMs: Long, durationMs: Long): Boolean = positionMs >= 5_000 && !isDone(positionMs, durationMs)

    class ShowStart(val index: Int, val started: Boolean)

    /** Where a show picks up: the episode in progress, else the first unwatched, else the first of a finished show. */
    fun showStart(episodes: List<Pair<Long, Long>>): ShowStart {
        val inProgress = episodes.indexOfFirst { (p, d) -> canResume(p, d) }
        val unwatched = episodes.indexOfFirst { (p, d) -> !isDone(p, d) }
        val anyWatched = episodes.any { (p, d) -> isDone(p, d) }
        return when {
            inProgress >= 0 -> ShowStart(inProgress, true)
            // Between episodes there is no place to resume, but the show is still under way.
            anyWatched && unwatched >= 0 -> ShowStart(unwatched, true)
            else -> ShowStart(0, false)
        }
    }

    /** The episode autoplay should land on: the first unfinished one from [from] on, or -1 when none is left. */
    fun nextUnwatched(episodes: List<Pair<Long, Long>>, from: Int): Int =
        (from.coerceAtLeast(0) until episodes.size).firstOrNull { !isDone(episodes[it].first, episodes[it].second) } ?: -1

    /** Media3's type for a subtitle file, or null when [name] is not one it reads. */
    fun subtitleMime(name: String): String? = when (name.substringAfterLast('.').lowercase()) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> null
    }

    /** Subtitle files beside a video that belong to it: "ep1.srt" and "ep1.en.srt" for "ep1", but not "ep10.srt". */
    fun belongsTo(subtitleName: String, videoName: String): Boolean {
        val rest = subtitleName.substringBeforeLast('.')
        return rest.equals(videoName, ignoreCase = true) || rest.startsWith("$videoName.", ignoreCase = true)
    }

    /** A TextureView stretches video to its own shape; these x and y factors undo that, to fit or to crop. */
    fun scale(viewW: Int, viewH: Int, videoW: Float, videoH: Float, crop: Boolean): Pair<Float, Float> {
        val s = if (crop) max(viewW / videoW, viewH / videoH) else min(viewW / videoW, viewH / videoH)
        return videoW * s / viewW to videoH * s / viewH
    }

    /** Android refuses picture-in-picture windows beyond 2.39:1 either way. */
    fun pipRatio(w: Int, h: Int): Pair<Int, Int> = when {
        w <= 0 || h <= 0 -> 16 to 9
        w * 100 > h * 239 -> 239 to 100
        h * 100 > w * 239 -> 100 to 239
        else -> w to h
    }
}
