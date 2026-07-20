package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeManagerTest {
    @Test
    fun clockWordsUseClockStyleEnglish() {
        assertEquals("Six Fifty Seven", TimeManager.clockWords(6, 57))
        assertEquals("Six Oh Five", TimeManager.clockWords(6, 5))
        assertEquals("Six O' Clock", TimeManager.clockWords(6, 0))
        assertEquals("Twelve Oh One", TimeManager.clockWords(0, 1))
        assertEquals("Twelve Thirty", TimeManager.clockWords(12, 30))
        assertEquals("Eleven Fifty Nine", TimeManager.clockWords(23, 59))
    }
}
