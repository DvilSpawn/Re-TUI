package ohi.andre.consolelauncher.wallpaper.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelWallpaperTest {
    @Test
    fun aDropSpreadsThenTheSurfaceSettlesQuiet() {
        val field = RippleField(60, 40)
        assertTrue(field.quiet)
        field.drop(30, 20, 3, 6f)
        assertFalse(field.quiet)
        repeat(15) { field.step() }
        // The wave has travelled outward from the centre.
        assertTrue(kotlin.math.abs(field.height(30, 28)) + kotlin.math.abs(field.height(38, 20)) > 0.01f)
        var steps = 0
        while (!field.quiet && steps < 5000) { field.step(); steps++ }
        assertTrue("never settled", field.quiet)
        assertEquals(0f, field.height(30, 20), 0f)
    }

    @Test
    fun ditherOnlyUsesPaletteColoursAndKeepsTheExtremesPure() {
        val palette = PixelLook.GAMEBOY.colors(0)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(palette.first(), PixelDither.pick(0f, x, y, palette))
            assertEquals(palette.last(), PixelDither.pick(1f, x, y, palette))
            assertTrue(PixelDither.pick(0.4f, x, y, palette) in palette)
        }
        // Mid grey in black and white is half on, half off over the 4x4 pattern.
        val mono = PixelLook.MONO.colors(0)
        val white = (0 until 16).count { PixelDither.pick(0.5f, it % 4, it / 4, mono) == mono.last() }
        assertEquals(8, white)
    }

    @Test
    fun themeLookRunsFromBlackToTheInk() {
        val ink = 0xFF33FF66.toInt()
        val colors = PixelLook.THEME.colors(ink)
        assertEquals(0xFF000000.toInt(), colors.first())
        assertEquals(ink, colors.last())
        assertEquals(PixelLook.MONO, PixelLook.of("nope"))
        assertEquals(PixelLook.GAMEBOY, PixelLook.of("gameboy"))
    }

    @Test
    fun customFireRampHitsEachPickedColourAtItsStop() {
        val picked = intArrayOf(0xFF000000.toInt(), 0xFF0033FF.toInt(), 0xFF00FFCC.toInt(), 0xFFFFFFFF.toInt())
        val ramp = DoomFireView.ramp(picked)
        assertEquals(37, ramp.size)
        assertEquals(picked[0], ramp[0])
        assertEquals(picked[1], ramp[12])
        assertEquals(picked[2], ramp[24])
        assertEquals(picked[3], ramp[36])
        assertTrue(DoomFireView.ramp(DoomFireView.CLASSIC_COLORS).let { it[0] == DoomFireView.CLASSIC_COLORS[0] && it[36] == 0xFFFFFFFF.toInt() })
    }
}
