package ohi.andre.consolelauncher.managers.mediaplayer

import android.content.Context
import ohi.andre.consolelauncher.managers.music.Song

/** Saved lists of songs and episodes, one preference per list. */
internal class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun names(): List<String> = prefs.all.keys.sortedBy { it.lowercase() }

    fun songs(name: String): List<Song> = QueueStore.decode(prefs.getString(name, null))

    fun save(name: String, songs: List<Song>) {
        prefs.edit().putString(name, QueueStore.encode(songs)).apply()
    }

    fun delete(name: String) {
        prefs.edit().remove(name).apply()
    }

    companion object {
        const val PREFS = "retui_media_playlists"
    }
}
