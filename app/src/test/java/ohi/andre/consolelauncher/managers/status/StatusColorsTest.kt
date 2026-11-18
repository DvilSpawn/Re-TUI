package ohi.andre.consolelauncher.managers.status

import ohi.andre.consolelauncher.managers.status.StatusColors.Run
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusColorsTest {
    @Test
    fun eachTagColoursUpToTheNext() {
        val (text, runs) = StatusColors.parse("[#FF0088]RAM [#00BFFF]2.1 GB / 8 GB")
        assertEquals("RAM 2.1 GB / 8 GB", text)
        assertEquals(listOf(Run(0, 4, 0xFFFF0088.toInt()), Run(4, 17, 0xFF00BFFF.toInt())), runs)
    }

    @Test
    fun aClosingTagGoesBackToTheLinesColour() {
        val (text, runs) = StatusColors.parse("Battery: [#00FF00]80%[/] charging")
        assertEquals("Battery: 80% charging", text)
        assertEquals(listOf(Run(9, 12, 0xFF00FF00.toInt())), runs)
    }

    @Test
    fun eightDigitsKeepTheirTransparency() {
        assertEquals(listOf(Run(0, 2, 0x8000BFFF.toInt())), StatusColors.parse("[#8000BFFF]hi").second)
    }

    @Test
    fun otherBracketsAndEmptyRunsAreLeftAlone() {
        val (text, runs) = StatusColors.parse("[wifi] [#12345] [#FF0000][#00FF00]x")
        assertEquals("[wifi] [#12345] x", text)
        assertEquals(listOf(Run(16, 17, 0xFF00FF00.toInt())), runs)
    }

    @Test
    fun plainTextHasNoRuns() {
        assertEquals("Available RAM: 2 GB" to emptyList<Run>(), StatusColors.parse("Available RAM: 2 GB"))
    }
}
