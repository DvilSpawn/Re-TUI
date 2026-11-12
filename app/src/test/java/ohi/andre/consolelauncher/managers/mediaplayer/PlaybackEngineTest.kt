package ohi.andre.consolelauncher.managers.mediaplayer

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackEngineTest {
    @Test fun playersAreOnlyBuiltByTheEngine() {
        listOf("MusicService.kt", "VideoPlayerActivity.kt").forEach {
            assertFalse("$it builds its own player", SourceFiles.kotlin(it).readText().contains("ExoPlayer.Builder"))
        }
    }
}
