package ohi.andre.consolelauncher.managers.mediaplayer

import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.music.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBrowseTest {
    private fun song(title: String, singer: String = "A", album: String = "", folder: String = "") =
        Song(title, singer, "/x/$title", MusicService.SOURCE_INTERNAL, null, null, 0).also { it.album = album; it.folder = folder }

    @Test fun groupsByAlbumSortedByName() {
        val groups = LibraryBrowse.groups(listOf(song("1", album = "Zed"), song("2", album = "abc"), song("3", album = "Zed")), LibraryBrowse.By.ALBUMS)
        assertEquals(listOf("abc", "Zed"), groups.map { it.name })
        assertEquals(2, groups[1].songs.size)
    }

    @Test fun anUntaggedSongUsesItsFolderAsTheAlbum() {
        assertEquals("Live", LibraryBrowse.albumOf(song("1", folder = "Music/Live")))
    }

    @Test fun aSongWithNothingToGoOnIsUnknown() {
        assertEquals("Unknown", LibraryBrowse.albumOf(song("1")))
    }

    @Test fun searchLooksAtTitleArtistAndAlbum() {
        val s = song("Tide", "Harbour", "Ferry")
        listOf("tide", "HARB", " ferry ", "").forEach { assertTrue(it, LibraryBrowse.matches(s, it)) }
        assertFalse(LibraryBrowse.matches(s, "zzz"))
    }
}
