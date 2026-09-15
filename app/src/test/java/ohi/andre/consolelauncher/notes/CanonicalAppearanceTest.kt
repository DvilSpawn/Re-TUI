package ohi.andre.consolelauncher.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CanonicalAppearanceTest {
    @Test fun acceptsZeroDashAndGapWithoutKeepingAnOldValue() {
        val dash = PreviewContract.Visual.DASH
        val gap = PreviewContract.Visual.GAP
        assertEquals(mapOf(dash to 12, gap to 0), CanonicalAppearance.accepted(
            true, mapOf(dash to 8, gap to 6), mapOf(dash to 12, gap to 0)
        ))
        assertEquals(mapOf(dash to 0, gap to 0), CanonicalAppearance.valid(mapOf(dash to 0, gap to 0)))
        assertFalse(CanonicalAppearance.valid(mapOf(gap to -1)).containsKey(gap))
        assertFalse(CanonicalAppearance.valid(mapOf(dash to 257)).containsKey(dash))
    }
}
