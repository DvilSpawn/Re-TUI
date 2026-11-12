package ohi.andre.consolelauncher.managers.podcast

import android.content.Context
import android.os.Environment
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Episodes kept for offline listening, in the app's own folder so they need no storage permission. */
class EpisodeDownloads(context: Context, private val client: OkHttpClient) {
    private class Running(@Volatile var percent: Int = 0, @Volatile var cancelled: Boolean = false)

    private val dir = (context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "podcasts")).apply { mkdirs() }
    private val running = ConcurrentHashMap<String, Running>()

    /** Told about progress and endings, with a message when something went wrong. Called off the main thread. */
    @Volatile var onChanged: ((String?) -> Unit)? = null

    private fun fileFor(key: String) = File(dir, PodcastParser.stableId(key) + ".audio")

    fun file(key: String): File? = fileFor(key).takeIf(File::exists)

    fun isDownloaded(episode: PodcastEpisode) = file(episode.key) != null

    /** How far along an active download is, or null when none is running. */
    fun percent(episode: PodcastEpisode): Int? = running[episode.key]?.percent

    fun delete(episode: PodcastEpisode) {
        running[episode.key]?.cancelled = true
        fileFor(episode.key).delete()
        onChanged?.invoke(null)
    }

    fun start(episode: PodcastEpisode, failed: String) {
        val job = Running()
        if (running.putIfAbsent(episode.key, job) != null) return
        onChanged?.invoke(null)
        Thread {
            val target = fileFor(episode.key)
            val part = File(target.path + ".part")
            var problem: String? = null
            try {
                fetch(episode, part, job)
                if (!job.cancelled && !part.renameTo(target)) throw IOException("rename")
            } catch (_: Exception) {
                if (!job.cancelled) problem = failed
            } finally {
                part.delete()
                running.remove(episode.key)
                onChanged?.invoke(problem)
            }
        }.start()
    }

    private fun fetch(episode: PodcastEpisode, part: File, job: Running) {
        val request = Request.Builder().url(episode.audioUrl).header("User-Agent", "ReTUI/1.0 Android Podcast").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty")
            val total = body.contentLength()
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (!job.cancelled) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        done += read
                        val percent = if (total > 0) (done * 100 / total).toInt() else 0
                        if (percent != job.percent) { job.percent = percent; onChanged?.invoke(null) }
                    }
                }
            }
        }
    }
}
