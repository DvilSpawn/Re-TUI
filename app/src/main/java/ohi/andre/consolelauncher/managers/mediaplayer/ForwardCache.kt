package ohi.andre.consolelauncher.managers.mediaplayer

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException

/** The disk cache behind streamed audio. One per process: the cache refuses a second owner of its folder. */
@OptIn(UnstableApi::class)
internal object StreamCache {
    private const val MAX_BYTES = 300L * 1024 * 1024

    @Volatile private var cache: SimpleCache? = null

    @Synchronized private fun cache(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.cacheDir, "media_stream"),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context.applicationContext),
    ).also { cache = it }

    private fun dataSources(context: Context): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(context))
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun dataSource(context: Context): CacheDataSource = dataSources(context).createDataSourceForDownloading()

    /**
     * The player only reads the cache. If it wrote too, it would hold a lock on the unread part of the file while it
     * buffers, and the forward cache, which waits for that lock, would never get to run.
     */
    private fun playbackSources(context: Context) = dataSources(context).setCacheWriteDataSinkFactory(null)

    fun isStream(uri: Uri?) = uri?.scheme == "http" || uri?.scheme == "https"

    /** Streams go through the cache; local files are read directly. */
    fun mediaSources(context: Context): MediaSource.Factory {
        val streams = DefaultMediaSourceFactory(playbackSources(context))
        val local = DefaultMediaSourceFactory(context)
        return object : MediaSource.Factory by streams {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource =
                (if (isStream(mediaItem.localConfiguration?.uri)) streams else local).createMediaSource(mediaItem)
        }
    }
}

/** Saves the next stretch of streamed audio to disk, so a patch of bad signal does not stop playback. */
@OptIn(UnstableApi::class)
internal class ForwardCache(private val context: Context) {
    @Volatile private var run: Run? = null

    private inner class Run(val uris: List<Uri>, val firstOffset: Long, val budget: Long) : Thread("forward-cache") {
        @Volatile var cancelled = false
        @Volatile var writer: CacheWriter? = null

        override fun run() {
            var left = budget
            uris.forEachIndexed { i, uri ->
                if (cancelled || left <= 0) return
                var saved = 0L
                val budgetHere = left
                // Open-ended on purpose: only then does the cache record the file's full length. Without it a later
                // play cannot tell an MP3's duration or seek in it, so a saved place would restart from the top.
                val spec = DataSpec.Builder().setUri(uri).setPosition(if (i == 0) firstOffset else 0).build()
                lateinit var next: CacheWriter
                next = CacheWriter(StreamCache.dataSource(context), spec, null) { _, bytesCached, _ ->
                    saved = bytesCached
                    if (bytesCached >= budgetHere) next.cancel()
                }
                writer = next
                try {
                    if (!cancelled) next.cache()
                } catch (_: InterruptedIOException) {
                    // Stopped at the budget, or by cancel().
                } catch (_: IOException) {
                    // The end of a file or a lost connection: move on, and the next refresh picks up what is missing.
                } catch (_: InterruptedException) {
                    return
                }
                left -= saved
            }
        }
    }

    /** Replaces whatever is being saved. [firstOffset] applies to the first of [uris]; [budget] spans them all. */
    fun refresh(uris: List<Uri>, firstOffset: Long, budget: Long) {
        cancel()
        if (uris.isEmpty()) return
        run = Run(uris, firstOffset, budget).also { it.isDaemon = true; it.start() }
    }

    fun cancel() {
        run?.let { it.cancelled = true; it.writer?.cancel() }
        run = null
    }

    companion object {
        const val FALLBACK_BITRATE = 128_000
        private const val PLAYER_LEAD_SECONDS = 60L

        fun budgetBytes(bitrate: Int, minutes: Int): Long = usable(bitrate) / 8L * minutes * 60

        /** Where to start in the current file: past what the player itself has already buffered. */
        fun startOffset(positionMs: Long, bitrate: Int): Long = usable(bitrate) / 8L * (positionMs / 1000 + PLAYER_LEAD_SECONDS)

        private fun usable(bitrate: Int) = if (bitrate > 0) bitrate else FALLBACK_BITRATE
    }
}
