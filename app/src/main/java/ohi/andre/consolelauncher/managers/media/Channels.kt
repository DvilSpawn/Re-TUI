package ohi.andre.consolelauncher.managers.media

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live TV: channels imported from M3U playlists. They play in the video player like any video, but nothing about
 * them is remembered except which one was watched last.
 */
object Channels {
    data class Channel(val name: String, val url: String, val group: String = "")

    const val FOLDER = "\u0000channels"
    private const val FILE = "channels.json"
    private const val PREFS = "media_channels"
    private const val LAST = "last"
    private const val PLAIN_ACCEPTED = "plain_http_accepted"
    private const val MAX_CHANNELS = 5_000
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val TIMEOUT_MS = 15_000

    @Volatile private var cached: List<Channel>? = null

    /** Streams come from the network; library videos are content URIs. */
    fun isStream(uri: String?) = uri != null && Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(uri) &&
        !uri.startsWith("content://") && !uri.startsWith("file://")

    /**
     * Reads an M3U or M3U8 playlist: `#EXTINF` lines name the stream on the next line, with its group from
     * `group-title` or a `#EXTGRP` line. A bare list of stream addresses works too.
     */
    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var group = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    name = titleOf(line)
                    group = attribute(line, "group-title").orEmpty()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                isStream(line) -> {
                    out += Channel(name?.takeIf { it.isNotBlank() } ?: fallbackName(line), line, group)
                    name = null
                    group = ""
                    if (out.size >= MAX_CHANNELS) break
                }
            }
        }
        return out.distinctBy { it.url }
    }

    /** The title after the attributes: the first comma outside quotes. */
    private fun titleOf(line: String): String {
        var quoted = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return line.substring(i + 1).trim()
            }
        }
        return ""
    }

    private fun attribute(line: String, key: String): String? =
        Regex("""\b${Regex.escape(key)}="([^"]*)"""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.trim()

    private fun fallbackName(url: String) = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        .substringBeforeLast('.').ifBlank { url }

    fun list(context: Context): List<Channel> {
        cached?.let { return it }
        return read(context).also { cached = it }
    }

    fun find(context: Context, url: String) = list(context).firstOrNull { it.url == url }

    /** Adds [found] after the channels already there; a channel already known keeps its place. Returns how many were new. */
    fun add(context: Context, found: List<Channel>): Int {
        val current = list(context)
        val known = current.mapTo(HashSet()) { it.url }
        val fresh = found.filter { known.add(it.url) }
        if (fresh.isNotEmpty()) write(context, (current + fresh).take(MAX_CHANNELS))
        return fresh.size
    }

    fun clear(context: Context) {
        write(context, emptyList())
        prefs(context).edit().remove(LAST).apply()
    }

    /** An unencrypted stream: the network can see and change it. */
    fun isPlain(url: String) = url.startsWith("http://", ignoreCase = true)

    /** Whether the user accepted the risks of unencrypted channels; until then they are never added. */
    fun plainAccepted(context: Context) = prefs(context).getBoolean(PLAIN_ACCEPTED, false)

    fun acceptPlain(context: Context) = prefs(context).edit().putBoolean(PLAIN_ACCEPTED, true).apply()

    fun remember(context: Context, url: String) = prefs(context).edit().putString(LAST, url).apply()

    fun last(context: Context): Channel? = prefs(context).getString(LAST, null)?.let { find(context, it) }

    /**
     * Where a channel was left, so the home panel and the full-screen player hand it to each other in place.
     * Live streams keep 0: they rejoin at the live edge. Kept only while the process lives.
     */
    private val spots = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val live = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun spot(url: String) = spots[url] ?: 0L

    /** Whether [url] last played as a live broadcast rather than a video at a link. */
    fun wasLive(url: String) = url in live

    /** Keeps where [player] is on [url]; once it has started, also whether the stream is live. */
    fun keepSpot(url: String, player: Player) {
        spots[url] = spotOf(player).coerceAtLeast(0L)
        if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED) {
            if (player.isCurrentMediaItemLive) live.add(url) else live.remove(url)
        }
    }

    /** Where to pick [player] up again: its position, or 0 for a live stream, which rejoins at the edge. */
    fun spotOf(player: Player) =
        if (player.isCurrentMediaItemLive || !player.isCurrentMediaItemSeekable) 0L else player.currentPosition

    /** A channel as the video player's playlist entry. */
    fun item(channel: Channel) = MediaLibrary.Item(channel.url, channel.name, FOLDER, true)

    fun items(context: Context) = list(context).map(::item)

    /** Reads a playlist file the user picked. */
    fun readDocument(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { readCapped(it) } ?: error("Cannot open $uri")

    /** Downloads a playlist. Network: call it off the main thread. */
    fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.use { readCapped(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): String {
        val bytes = input.readNBytesCompat(MAX_BYTES + 1)
        require(bytes.size <= MAX_BYTES) { "Playlist is larger than ${MAX_BYTES / 1024 / 1024} MB" }
        return String(bytes, Charsets.UTF_8)
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun file(context: Context) = AtomicFile(File(context.filesDir, FILE))

    private fun read(context: Context): List<Channel> = runCatching {
        val file = file(context)
        if (!file.baseFile.exists()) return emptyList()
        val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Channel(o.getString("name"), o.getString("url"), o.optString("group"))
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, channels: List<Channel>) {
        val file = file(context)
        val out = file.startWrite()
        try {
            val array = JSONArray()
            channels.forEach { array.put(JSONObject().put("name", it.name).put("url", it.url).put("group", it.group)) }
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            cached = channels
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
