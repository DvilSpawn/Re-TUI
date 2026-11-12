package ohi.andre.consolelauncher.managers.mediaplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DitherTest {
    private fun dots(luma: Int) = (0..3).sumOf { y -> (0..3).count { x -> Dither.lit(luma, x, y) } }

    @Test fun blackHasNoDotsAndWhiteIsNearlyFull() {
        assertFalse(Dither.lit(0, 0, 0))
        assert(dots(0) == 0)
        assertTrue(dots(255) >= 15)
    }

    @Test fun brighterMeansMoreDots() {
        assertTrue(dots(64) < dots(128) && dots(128) < dots(200))
    }
}
