package ohi.andre.consolelauncher.managers.mediaplayer

import android.content.Context
import ohi.andre.consolelauncher.managers.music.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the queue across restarts. The list and the place in it are separate files because the place is written
 * while playing and the list can run to thousands of songs.
 */
internal class QueueStore(context: Context) {
    data class Saved(val songs: List<Song>, val index: Int, val positionMs: Int, val repeat: PlayQueue.Repeat)

    private val list = context.getSharedPreferences(PREFS_LIST, Context.MODE_PRIVATE)
    private val place = context.getSharedPreferences(PREFS_PLACE, Context.MODE_PRIVATE)

    fun saveList(songs: List<Song>) {
        list.edit().putString(KEY_SONGS, encode(songs)).apply()
    }

    fun savePlace(index: Int, positionMs: Int, repeat: PlayQueue.Repeat) {
        place.edit().putInt(KEY_INDEX, index).putInt(KEY_POSITION, positionMs).putString(KEY_REPEAT, repeat.name).apply()
    }

    fun load(): Saved? {
        val songs = decode(list.getString(KEY_SONGS, null))
        if (songs.isEmpty()) return null
        val repeat = PlayQueue.Repeat.entries.firstOrNull { it.name == place.getString(KEY_REPEAT, null) } ?: PlayQueue.Repeat.ALL
        return Saved(songs, place.getInt(KEY_INDEX, 0), place.getInt(KEY_POSITION, 0), repeat)
    }

    companion object {
        const val PREFS_LIST = "retui_media_queue"
        const val PREFS_PLACE = "retui_media_queue_place"
        private const val KEY_SONGS = "songs"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_REPEAT = "repeat"

        fun encode(songs: List<Song>): String {
            val array = JSONArray()
            songs.forEach { song ->
                array.put(JSONObject()
                    .put("id", song.getID())
                    .put("title", song.getTitle())
                    .put("singer", song.getSinger())
                    .put("path", song.getPath())
                    .put("source", song.getSource())
                    .put("show", song.getPodcastShowId())
                    .put("episode", song.getPodcastEpisodeKey())
                    .put("start", song.getStartPositionMs())
                    .put("album", song.album)
                    .put("folder", song.folder))
            }
            return array.toString()
        }

        /** Whatever cannot be read is dropped, so a damaged file costs the queue and nothing else. */
        fun decode(raw: String?): List<Song> {
            if (raw.isNullOrEmpty()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    val id = o.getLong("id")
                    val song = if (id != -1L) Song(id, o.getString("title"), o.getString("singer"))
                    else Song(o.getString("title"), o.getString("singer"), o.getString("path"), o.getString("source"),
                        o.optString("show").ifEmpty { null }, o.optString("episode").ifEmpty { null }, o.optInt("start"))
                    song.album = o.optString("album")
                    song.folder = o.optString("folder")
                    song
                }
            }.getOrDefault(emptyList())
        }
    }
}
