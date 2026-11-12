package ohi.andre.consolelauncher.managers.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoMathTest {
    @Test fun clockShowsHoursOnlyWhenNeeded() {
        assertEquals("4:12", VideoMath.clock(252_000))
        assertEquals("1:04:12", VideoMath.clock(3_852_000))
        assertEquals("0:00", VideoMath.clock(0))
    }

    @Test fun holdingSpeedsUpAndLevelsOff() {
        assertEquals(4_000, VideoMath.holdStepMs(0))
        assertTrue(VideoMath.holdStepMs(3_000) > VideoMath.holdStepMs(1_000))
        assertEquals(30_000, VideoMath.holdStepMs(60_000))
    }

    @Test fun watchedVideosStartOverAndOthersResume() {
        assertEquals(252_000, VideoMath.resumeAt(252_000, 840_000))
        assertEquals(0, VideoMath.resumeAt(830_000, 840_000))
        assertFalse(VideoMath.isDone(5_000, 0))
        assertEquals(0, VideoMath.resumeAt(-1, 840_000))
    }

    @Test fun newShowsStartAndUnderwayShowsCarryOn() {
        val fresh = 0L to 0L
        val done = 100_000L to 100_000L
        val half = 40_000L to 100_000L
        VideoMath.showStart(listOf(fresh, fresh)).let { assertEquals(0, it.index); assertFalse(it.started) }
        VideoMath.showStart(listOf(done, half, fresh)).let { assertEquals(1, it.index); assertTrue(it.started) }
        VideoMath.showStart(listOf(done, fresh)).let { assertEquals(1, it.index); assertTrue(it.started) }
        VideoMath.showStart(listOf(done, done)).let { assertEquals(0, it.index); assertFalse(it.started) }
        assertFalse(VideoMath.canResume(2_000, 100_000))
    }

    @Test fun subtitlesMatchTheirVideoOnly() {
        assertTrue(VideoMath.belongsTo("ep1.srt", "ep1"))
        assertTrue(VideoMath.belongsTo("EP1.en.srt", "ep1"))
        assertFalse(VideoMath.belongsTo("ep10.srt", "ep1"))
        assertEquals("application/x-subrip", VideoMath.subtitleMime("a.SRT"))
        assertEquals(null, VideoMath.subtitleMime("a.txt"))
    }

    @Test fun fitLeavesBarsAndCropOverflows() {
        val (fitX, fitY) = VideoMath.scale(2000, 1000, 1000f, 1000f, false)
        assertEquals(0.5f, fitX, 0.001f)
        assertEquals(1f, fitY, 0.001f)
        val (cropX, cropY) = VideoMath.scale(2000, 1000, 1000f, 1000f, true)
        assertEquals(1f, cropX, 0.001f)
        assertEquals(2f, cropY, 0.001f)
    }

    @Test fun pipRatioStaysInsideAndroidsLimits() {
        assertEquals(1920 to 1080, VideoMath.pipRatio(1920, 1080))
        assertEquals(239 to 100, VideoMath.pipRatio(4000, 1000))
        assertEquals(100 to 239, VideoMath.pipRatio(1000, 4000))
        assertEquals(16 to 9, VideoMath.pipRatio(0, 0))
    }
}
