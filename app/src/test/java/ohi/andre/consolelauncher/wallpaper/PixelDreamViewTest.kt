package ohi.andre.consolelauncher.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelDreamViewTest {
    @Test fun rippleAcceptsTapsAndRejectsScrolls() {
        assertFalse(movedBeyondTouchSlop(10f, 10f, 13f, 14f, 5f))
        assertTrue(movedBeyondTouchSlop(10f, 10f, 16f, 10f, 5f))
    }
}
