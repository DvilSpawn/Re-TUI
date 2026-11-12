package ohi.andre.consolelauncher.managers.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMathNextTest {
    private val done = 100L to 100L
    private val fresh = 0L to 0L

    @Test fun skipsWatchedEpisodes() = assertEquals(2, VideoMath.nextUnwatched(listOf(done, done, fresh), 0))
    @Test fun aHalfWatchedEpisodeCounts() = assertEquals(1, VideoMath.nextUnwatched(listOf(done, 50L to 100L), 1))
    @Test fun nothingLeftIsMinusOne() = assertEquals(-1, VideoMath.nextUnwatched(listOf(fresh, done), 1))
}
