package ohi.andre.consolelauncher.managers.music

import ohi.andre.consolelauncher.managers.media.MediaLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicExtensionsTest {
    @Test
    fun songsFolderUsesTheMediaLibraryFormats() {
        assertEquals(MediaLibrary.AUDIO.map { ".$it" }.toSet(), MusicManager2.MUSIC_EXTENSIONS.toSet())
        listOf(".mp3", ".m4a", ".aac", ".opus", ".flac").forEach { assertTrue(it, it in MusicManager2.MUSIC_EXTENSIONS) }
    }

    @Test
    fun wmaIsNotListedBecauseThePlayerCannotPlayIt() {
        assertFalse("wma" in MediaLibrary.AUDIO)
        assertFalse(".wma" in MusicManager2.MUSIC_EXTENSIONS)
    }
}
