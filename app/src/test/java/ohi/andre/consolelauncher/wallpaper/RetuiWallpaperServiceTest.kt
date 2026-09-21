package ohi.andre.consolelauncher.wallpaper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetuiWallpaperServiceTest {
    @Test fun visibleAnimatedWallpaperKeepsRetryingUntilAndroidCanDraw() {
        assertTrue(shouldScheduleWallpaperFrame(visible = true, fullRedrawPending = false, animated = true))
        assertTrue(shouldScheduleWallpaperFrame(visible = true, fullRedrawPending = true, animated = false))
        assertFalse(shouldScheduleWallpaperFrame(visible = true, fullRedrawPending = false, animated = false))
        assertFalse(shouldScheduleWallpaperFrame(visible = false, fullRedrawPending = true, animated = true))
    }

    @Test fun lockscreenKeepsWallpaperAnimatingWithoutTouchInteraction() {
        assertTrue(shouldRenderWallpaper(visible = true, interactionEnabled = false, screenInteractive = true, keyguardLocked = true))
        assertFalse(shouldRenderWallpaper(visible = true, interactionEnabled = false, screenInteractive = true, keyguardLocked = false))
        assertFalse(shouldRenderWallpaper(visible = true, interactionEnabled = true, screenInteractive = false, keyguardLocked = true))
    }

    @Test fun wallpaperPickerPreviewAnimatesWithoutEnablingBackgroundRendering() {
        assertTrue(shouldRenderWallpaper(visible = true, interactionEnabled = false, screenInteractive = true, keyguardLocked = false, wallpaperPreview = true))
        assertFalse(shouldRenderWallpaper(visible = false, interactionEnabled = false, screenInteractive = true, keyguardLocked = false, wallpaperPreview = true))
        assertFalse(shouldRenderWallpaper(visible = true, interactionEnabled = false, screenInteractive = false, keyguardLocked = false, wallpaperPreview = true))
    }

    @Test fun failedSurfaceLocksRetryMoreSlowlyThanAnimationFrames() {
        assertEquals(50L, wallpaperFrameDelay(drawSucceeded = true, normalDelayMs = 50L))
        assertEquals(1000L, wallpaperFrameDelay(drawSucceeded = false, normalDelayMs = 50L))
        assertEquals(1000L, wallpaperFrameDelay(drawSucceeded = false, normalDelayMs = 1000L))
    }
}
