package ohi.andre.consolelauncher.managers.podcast

import android.content.Context
import android.os.Handler
import android.os.Looper
import ohi.andre.consolelauncher.managers.music.MusicManager2
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.music.Song
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.min

class PodcastManager(
    context: Context,
    private val client: OkHttpClient,
    private val player: MusicManager2?,
    private val ownsPlayer: Boolean = false
) : MusicService.PlaybackListener {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var shows: List<PodcastShow> = loadCachedShows()

    fun feeds(): List<String> = prefs.getStringSet(KEY_FEEDS, emptySet())?.toList() ?: emptyList()

    fun shows(): List<PodcastShow> = shows

    fun tags(): List<String> =
        shows.flatMap { it.tags }
            .distinctBy { it.lowercase() }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun setTags(show: PodcastShow, rawTags: String): String {
        val tags = parseTags(rawTags)
        shows = shows.map { if (it.id == show.id) it.copy(tags = tags) else it }
        cacheShows()
        return if (tags.isEmpty()) "Tags cleared: " + show.title
        else "Tags: " + tags.joinToString(", ")
    }

    fun isNewestFirst(show: PodcastShow): Boolean = prefs.getBoolean(sortNewestKey(show.id), false)

    fun toggleNewestFirst(show: PodcastShow): Boolean {
        val next = !isNewestFirst(show)
        prefs.edit().putBoolean(sortNewestKey(show.id), next).apply()
        return next
    }

    fun episodesFor(show: PodcastShow): List<PodcastEpisode> =
        orderedEpisodes(show.episodes, isNewestFirst(show))

    fun recents(): List<PodcastRecent> =
        shows.mapNotNull { show ->
            val key = prefs.getString(recentEpisodeKey(show.id), null)
                ?: if (prefs.getString(KEY_ACTIVE_SHOW, null) == show.id) prefs.getString(KEY_ACTIVE_EPISODE, null) else null
            val episode = show.episodes.firstOrNull { it.key == key } ?: return@mapNotNull null
            PodcastRecent(show, episode, progress(episode), prefs.getLong(recentUpdatedKey(show.id), 0L))
        }.sortedByDescending { it.updatedAtMs }

    fun selectedShow(): PodcastShow? {
        val selected = prefs.getString(KEY_SELECTED_SHOW, null)
        return shows.firstOrNull { it.id == selected } ?: shows.firstOrNull()
    }

    fun selectShow(showId: String) {
        prefs.edit().putString(KEY_SELECTED_SHOW, showId).apply()
    }

    fun removeShow(show: PodcastShow): String {
        val nextFeeds = LinkedHashSet(feeds())
        if (!nextFeeds.remove(show.feedUrl)) return "Podcast not found."

        shows = shows.filterNot { it.id == show.id }
        val editor = prefs.edit().putStringSet(KEY_FEEDS, nextFeeds)
        if (prefs.getString(KEY_SELECTED_SHOW, null) == show.id) {
            val nextSelected = shows.firstOrNull()?.id
            if (nextSelected == null) editor.remove(KEY_SELECTED_SHOW)
            else editor.putString(KEY_SELECTED_SHOW, nextSelected)
        }
        if (prefs.getString(KEY_ACTIVE_SHOW, null) == show.id) {
            editor.remove(KEY_ACTIVE_SHOW).remove(KEY_ACTIVE_EPISODE)
        }
        editor.remove(recentEpisodeKey(show.id)).remove(recentUpdatedKey(show.id)).remove(sortNewestKey(show.id))
        editor.apply()

        val current = player?.currentSong()
        if (current?.getSource() == MusicService.SOURCE_PODCAST && current.getPodcastShowId() == show.id) {
            player.stop()
        }
        cacheShows()
        return "Removed: " + show.title
    }

    fun subscribe(feedUrl: String, done: (String?) -> Unit) {
        val clean = feedUrl.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            done("Podcast URL must start with http:// or https://.")
            return
        }
        val next = LinkedHashSet(feeds())
        next.add(clean)
        prefs.edit().putStringSet(KEY_FEEDS, next).apply()
        refreshFeed(clean, done)
    }

    fun refresh(done: (String?) -> Unit) {
        val feedUrls = feeds()
        if (feedUrls.isEmpty()) {
            shows = emptyList()
            cacheShows()
            done("No podcast feeds yet. Use podcast add <feed-url>.")
            return
        }

        Thread {
            val nextShows = ArrayList<PodcastShow>()
            var lastError: String? = null
            for (url in feedUrls) {
                try {
                    nextShows.add(mergeStoredTags(fetch(url)))
                } catch (e: Exception) {
                    lastError = "Podcast refresh failed: " + (e.message ?: url)
                }
            }
            shows = nextShows
            cacheShows()
            if (prefs.getString(KEY_SELECTED_SHOW, null) == null && nextShows.isNotEmpty()) {
                prefs.edit().putString(KEY_SELECTED_SHOW, nextShows[0].id).apply()
            }
            handler.post { done(lastError) }
        }.start()
    }

    fun refreshShow(show: PodcastShow, done: (String?) -> Unit) {
        refreshFeed(show.feedUrl, done)
    }

    fun refreshSelectedShow(done: (String?) -> Unit) {
        val show = selectedShow()
        if (show != null) {
            refreshShow(show, done)
            return
        }
        val first = feeds().firstOrNull()
        if (first == null) done("No podcast feeds yet. Use podcast add <feed-url>.")
        else refreshFeed(first, done)
    }

    fun play(episode: PodcastEpisode? = null): String {
        val show = selectedShow() ?: return "No podcast selected."
        val target = episode ?: currentEpisode(show) ?: nextUnplayed(show) ?: episodesFor(show).firstOrNull()
            ?: return "No playable episodes."
        return play(show, target)
    }

    fun play(show: PodcastShow, episode: PodcastEpisode): String {
        val manager = player ?: return "Podcast playback is unavailable."
        val episodes = episodesFor(show)
        val index = episodes.indexOfFirst { it.key == episode.key }
        if (index == -1) return "Episode not found."
        val songs = episodes.map {
            Song(
                it.title,
                show.title,
                it.audioUrl,
                MusicService.SOURCE_PODCAST,
                show.id,
                it.key,
                progress(it)
            )
        }.toMutableList()
        prefs.edit()
            .putString(KEY_SELECTED_SHOW, show.id)
            .putString(KEY_ACTIVE_SHOW, show.id)
            .putString(KEY_ACTIVE_EPISODE, episode.key)
            .putString(recentEpisodeKey(show.id), episode.key)
            .putLong(recentUpdatedKey(show.id), System.currentTimeMillis())
            .apply()
        val title = manager.playPodcast(songs, index, this)
        return if (title == null) "Starting podcast..." else "Playing: " + title
    }

    fun toggle(): String {
        val manager = player ?: return "Podcast playback is unavailable."
        val current = manager.currentSong()
        return if (current?.getSource() == MusicService.SOURCE_PODCAST && manager.isPlaying()) {
            saveProgress(current, manager.getCurrentPosition(), manager.getDuration())
            manager.pause()
            "Podcast paused."
        } else {
            play()
        }
    }

    fun next(): String {
        val manager = player ?: return "Podcast playback is unavailable."
        val current = manager.currentSong()
        if (current?.getSource() == MusicService.SOURCE_PODCAST && manager.isPlaying()) {
            saveProgress(current, manager.getCurrentPosition(), manager.getDuration())
            return manager.playNext() ?: "Next podcast episode."
        }
        val show = selectedShow() ?: return "No podcast selected."
        val episodes = episodesFor(show)
        if (episodes.isEmpty()) return "No playable episodes."
        val currentKey = prefs.getString(KEY_ACTIVE_EPISODE, null)
        val index = episodes.indexOfFirst { it.key == currentKey }
        val nextIndex = if (index == -1) 0 else min(index + 1, episodes.lastIndex)
        return play(show, episodes[nextIndex])
    }

    fun previous(): String {
        val manager = player ?: return "Podcast playback is unavailable."
        val current = manager.currentSong()
        if (current?.getSource() == MusicService.SOURCE_PODCAST && manager.isPlaying()) {
            saveProgress(current, manager.getCurrentPosition(), manager.getDuration())
            return manager.playPrev() ?: "Previous podcast episode."
        }
        val show = selectedShow() ?: return "No podcast selected."
        val episodes = episodesFor(show)
        if (episodes.isEmpty()) return "No playable episodes."
        val currentKey = prefs.getString(KEY_ACTIVE_EPISODE, null)
        val index = episodes.indexOfFirst { it.key == currentKey }
        val previousIndex = if (index == -1) 0 else max(index - 1, 0)
        return play(show, episodes[previousIndex])
    }

    fun seekBy(deltaMs: Int): String {
        val manager = player ?: return "Podcast playback is unavailable."
        val current = manager.currentSong() ?: return "No podcast is loaded."
        if (current.getSource() != MusicService.SOURCE_PODCAST) return "No podcast is loaded."
        val duration = manager.getDuration()
        val currentPos = manager.getCurrentPosition()
        val target = if (duration > 0) min(max(0, currentPos + deltaMs), duration) else max(0, currentPos + deltaMs)
        return seekTo(target)
    }

    fun seekTo(positionMs: Int): String {
        val manager = player ?: return "Podcast playback is unavailable."
        val current = manager.currentSong() ?: return "No podcast is loaded."
        if (current.getSource() != MusicService.SOURCE_PODCAST) return "No podcast is loaded."
        val duration = manager.getDuration()
        val target = if (duration > 0) min(max(0, positionMs), duration) else max(0, positionMs)
        manager.seekTo(target)
        saveProgress(current, target, duration)
        return formatMillis(target)
    }

    fun currentSong(): Song? {
        val song = player?.currentSong()
        return if (song?.getSource() == MusicService.SOURCE_PODCAST) song else null
    }

    fun isPlaying(): Boolean = currentSong() != null && player?.isPlaying() == true

    fun currentPosition(): Int = if (currentSong() != null) player?.getCurrentPosition() ?: -1 else -1

    fun duration(): Int = if (currentSong() != null) player?.getDuration() ?: -1 else -1

    fun saveCurrentProgress() {
        val song = currentSong() ?: return
        saveProgress(song, currentPosition(), duration())
    }

    fun destroy() {
        if (ownsPlayer) player?.destroy()
    }

    fun activeEpisode(): PodcastEpisode? {
        val showId = prefs.getString(KEY_ACTIVE_SHOW, null)
        val key = prefs.getString(KEY_ACTIVE_EPISODE, null)
        return shows.firstOrNull { it.id == showId }?.episodes?.firstOrNull { it.key == key }
    }

    fun isPlayed(episode: PodcastEpisode): Boolean = prefs.getBoolean(playedKey(episode.key), false)

    fun progress(episode: PodcastEpisode): Int = prefs.getInt(progressKey(episode.key), 0)

    fun markPlayed(episode: PodcastEpisode, played: Boolean) {
        prefs.edit().putBoolean(playedKey(episode.key), played).apply()
    }

    override fun onTrackChanged(song: Song?) {
        if (song?.getSource() != MusicService.SOURCE_PODCAST) return
        prefs.edit()
            .putString(KEY_ACTIVE_SHOW, song.getPodcastShowId())
            .putString(KEY_ACTIVE_EPISODE, song.getPodcastEpisodeKey())
            .apply()
        val showId = song.getPodcastShowId()
        val episodeKey = song.getPodcastEpisodeKey()
        if (showId != null && episodeKey != null) {
            prefs.edit()
                .putString(recentEpisodeKey(showId), episodeKey)
                .putLong(recentUpdatedKey(showId), System.currentTimeMillis())
                .apply()
        }
    }

    override fun onTrackCompleted(song: Song?, positionMs: Int, durationMs: Int) {
        if (song?.getSource() != MusicService.SOURCE_PODCAST) return
        val key = song.getPodcastEpisodeKey() ?: return
        prefs.edit()
            .putBoolean(playedKey(key), true)
            .putInt(progressKey(key), 0)
            .apply()
    }

    override fun onProgress(song: Song?, positionMs: Int, durationMs: Int) {
        if (song?.getSource() == MusicService.SOURCE_PODCAST) saveProgress(song, positionMs, durationMs)
    }

    private fun fetch(feedUrl: String): PodcastShow {
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "ReTUI/1.0 Android Podcast")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException(response.code.toString())
            val body = response.body ?: throw IllegalStateException("empty feed")
            return PodcastParser.parse(body.byteStream(), feedUrl)
        }
    }

    private fun refreshFeed(feedUrl: String, done: (String?) -> Unit) {
        Thread {
            try {
                val show = mergeStoredTags(fetch(feedUrl))
                shows = sortedShows(shows.filterNot { it.feedUrl == feedUrl || it.id == show.id } + show)
                cacheShows()
                if (prefs.getString(KEY_SELECTED_SHOW, null) == null) {
                    prefs.edit().putString(KEY_SELECTED_SHOW, show.id).apply()
                }
                handler.post { done(null) }
            } catch (e: Exception) {
                handler.post { done("Podcast refresh failed: " + (e.message ?: feedUrl)) }
            }
        }.start()
    }

    private fun sortedShows(input: List<PodcastShow>): List<PodcastShow> {
        val order = feeds()
        return input.sortedBy {
            val index = order.indexOf(it.feedUrl)
            if (index == -1) Int.MAX_VALUE else index
        }
    }

    private fun loadCachedShows(): List<PodcastShow> {
        val cached = ArrayList<PodcastShow>()
        val raw = prefs.getString(KEY_SHOW_CACHE, null)
        if (!raw.isNullOrEmpty()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    cached.add(readShow(array.getJSONObject(i)))
                }
            } catch (_: Exception) {
                cached.clear()
            }
        }

        val existingUrls = cached.map { it.feedUrl }.toSet()
        val placeholders = feeds()
            .filterNot { existingUrls.contains(it) }
            .map { PodcastShow(PodcastParser.stableId(it), it, it, "", null, emptyList()) }
        return sortedShows(cached + placeholders)
    }

    private fun cacheShows() {
        val array = JSONArray()
        for (show in shows) array.put(writeShow(show))
        prefs.edit().putString(KEY_SHOW_CACHE, array.toString()).apply()
    }

    private fun writeShow(show: PodcastShow): JSONObject {
        val episodes = JSONArray()
        for (episode in show.episodes) {
            episodes.put(JSONObject()
                .put("showId", episode.showId)
                .put("key", episode.key)
                .put("title", episode.title)
                .put("audioUrl", episode.audioUrl)
                .put("link", episode.link)
                .put("description", episode.description)
                .put("duration", episode.duration)
                .put("publishedAt", episode.publishedAt)
                .put("feedOrder", episode.feedOrder))
        }
        return JSONObject()
            .put("id", show.id)
            .put("feedUrl", show.feedUrl)
            .put("title", show.title)
            .put("description", show.description)
            .put("imageUrl", show.imageUrl)
            .put("tags", JSONArray(show.tags))
            .put("episodes", episodes)
    }

    private fun readShow(obj: JSONObject): PodcastShow {
        val episodes = ArrayList<PodcastEpisode>()
        val array = obj.optJSONArray("episodes") ?: JSONArray()
        for (i in 0 until array.length()) {
            val episode = array.getJSONObject(i)
            episodes.add(PodcastEpisode(
                showId = episode.optString("showId", obj.optString("id")),
                key = episode.optString("key"),
                title = episode.optString("title"),
                audioUrl = episode.optString("audioUrl"),
                link = episode.optString("link").ifBlank { null },
                description = episode.optString("description"),
                duration = episode.optString("duration").ifBlank { null },
                publishedAt = if (episode.has("publishedAt") && !episode.isNull("publishedAt")) episode.optLong("publishedAt") else null,
                feedOrder = episode.optInt("feedOrder")
            ))
        }
        val feedUrl = obj.optString("feedUrl")
        val tags = ArrayList<String>()
        val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
        for (i in 0 until tagsArray.length()) {
            tags.add(tagsArray.optString(i))
        }
        return PodcastShow(
            id = obj.optString("id", PodcastParser.stableId(feedUrl)),
            feedUrl = feedUrl,
            title = obj.optString("title", feedUrl),
            description = obj.optString("description"),
            imageUrl = obj.optString("imageUrl").ifBlank { null },
            episodes = episodes,
            tags = parseTags(tags.joinToString(","))
        )
    }

    private fun mergeStoredTags(show: PodcastShow): PodcastShow {
        val existing = shows.firstOrNull { it.id == show.id || it.feedUrl == show.feedUrl }
        return if (existing == null || existing.tags.isEmpty()) show else show.copy(tags = existing.tags)
    }

    private fun currentEpisode(show: PodcastShow): PodcastEpisode? {
        val key = prefs.getString(KEY_ACTIVE_EPISODE, null) ?: return null
        return episodesFor(show).firstOrNull { it.key == key && !isPlayed(it) }
    }

    private fun nextUnplayed(show: PodcastShow): PodcastEpisode? =
        episodesFor(show).firstOrNull { !isPlayed(it) }

    private fun saveProgress(song: Song, positionMs: Int, durationMs: Int) {
        val key = song.getPodcastEpisodeKey() ?: return
        val editor = prefs.edit().putInt(progressKey(key), max(0, positionMs))
        song.getPodcastShowId()?.let { showId ->
            editor.putString(recentEpisodeKey(showId), key)
                .putLong(recentUpdatedKey(showId), System.currentTimeMillis())
        }
        if (durationMs > 0 && positionMs >= durationMs - 3000) {
            editor.putBoolean(playedKey(key), true).putInt(progressKey(key), 0)
        }
        editor.apply()
    }

    private fun progressKey(key: String) = "progress:" + key

    private fun playedKey(key: String) = "played:" + key

    private fun recentEpisodeKey(showId: String) = "recent_episode:" + showId

    private fun recentUpdatedKey(showId: String) = "recent_updated:" + showId

    private fun sortNewestKey(showId: String) = "sort_newest:" + showId

    companion object {
        const val PREFS = "retui_podcasts"
        private const val KEY_FEEDS = "feeds"
        private const val KEY_SELECTED_SHOW = "selected_show"
        private const val KEY_ACTIVE_SHOW = "active_show"
        private const val KEY_ACTIVE_EPISODE = "active_episode"
        private const val KEY_SHOW_CACHE = "show_cache"

        fun orderedEpisodes(episodes: List<PodcastEpisode>, newestFirst: Boolean): List<PodcastEpisode> {
            if (!newestFirst) return episodes
            return episodes.sortedWith(
                compareBy<PodcastEpisode> { it.publishedAt == null }
                    .thenByDescending { it.publishedAt ?: Long.MIN_VALUE }
                    .thenBy { it.feedOrder }
            )
        }

        fun formatMillis(ms: Int): String {
            val total = max(0, ms / 1000)
            val seconds = total % 60
            val minutes = (total / 60) % 60
            val hours = total / 3600
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%d:%02d".format(minutes, seconds)
        }

        fun parseTags(raw: String?): List<String> =
            raw.orEmpty()
                .split(',', '#')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
    }
}
