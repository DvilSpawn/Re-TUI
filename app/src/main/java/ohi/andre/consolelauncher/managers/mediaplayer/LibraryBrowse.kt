package ohi.andre.consolelauncher.managers.mediaplayer

import ohi.andre.consolelauncher.managers.music.Song

/** Sorts the song library into what the library screen lists. */
internal object LibraryBrowse {
    enum class By { ALBUMS, ARTISTS, SONGS, FOLDERS }

    data class Group(val name: String, val songs: List<Song>)

    private const val UNKNOWN = "Unknown"

    private fun folderName(song: Song) = song.folder.trimEnd('/').substringAfterLast('/')

    /** Untagged music is usually filed one album to a folder, so the folder stands in for a missing album. */
    fun albumOf(song: Song) = song.album.ifBlank { folderName(song) }.ifBlank { UNKNOWN }

    private fun keyOf(song: Song, by: By) = when (by) {
        By.ALBUMS -> albumOf(song)
        By.ARTISTS -> song.getSinger().ifBlank { UNKNOWN }
        By.FOLDERS -> folderName(song).ifBlank { UNKNOWN }
        By.SONGS -> ""
    }

    fun groups(songs: List<Song>, by: By): List<Group> =
        songs.groupBy { keyOf(it, by) }.map { (name, inside) -> Group(name, inside) }.sortedBy { it.name.lowercase() }

    fun matches(song: Song, query: String) = query.isBlank() ||
        listOf(song.getTitle(), song.getSinger(), albumOf(song)).any { it.contains(query.trim(), ignoreCase = true) }
}
