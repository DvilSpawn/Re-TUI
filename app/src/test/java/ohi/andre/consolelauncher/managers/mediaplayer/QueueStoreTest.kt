package ohi.andre.consolelauncher.managers.mediaplayer

import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.music.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStoreTest {
    @Test fun storeSongsAndEpisodesSurviveARoundTrip() {
        val songs = listOf(
            Song(42L, "Local", "Artist"),
            Song("File", "Folder", "content://x/y.mp3", MusicService.SOURCE_INTERNAL, null, null, 0),
            Song("Episode", "Show", "https://x/e.mp3", MusicService.SOURCE_PODCAST, "show-1", "ep-1", 9000),
        )
        val back = QueueStore.decode(QueueStore.encode(songs))
        assertEquals(42L, back[0].getID())
        assertEquals("Local", back[0].getTitle())
        assertEquals("content://x/y.mp3", back[1].getPath())
        assertNull(back[1].getPodcastShowId())
        assertEquals(MusicService.SOURCE_PODCAST, back[2].getSource())
        assertEquals("ep-1", back[2].getPodcastEpisodeKey())
        assertEquals(9000, back[2].getStartPositionMs())
    }

    @Test fun aDamagedQueueIsDroppedQuietly() {
        assertTrue(QueueStore.decode("not json").isEmpty())
        assertTrue(QueueStore.decode(null).isEmpty())
    }
}
