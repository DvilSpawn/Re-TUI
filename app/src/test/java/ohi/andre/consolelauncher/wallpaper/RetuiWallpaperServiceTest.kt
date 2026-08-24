package ohi.andre.consolelauncher.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetuiWallpaperServiceTest {
    @Test fun visibleAnimatedWallpaperKeepsRetryingUntilAndroidCanDraw() {
        assertTrue(shouldScheduleWallpaperFrame(visible = true, fullRedrawPending = false, animated = true))
        assertTrue(shouldScheduleWallpaperFrame(visible = true, fullRedrawPending = true, animated = false))
        assertFalse(shouldScheduleWallpaperFrame(visible = true, fullRedrawPending = false, animated = false))
        assertFalse(shouldScheduleWallpaperFrame(visible = false, fullRedrawPending = true, animated = true))
    }
}
