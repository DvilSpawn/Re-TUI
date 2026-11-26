package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Test
import ohi.andre.consolelauncher.managers.time.TimeManager

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

    @Test
    fun colourTagsPassThroughTheDateFormatter() {
        val format = java.text.SimpleDateFormat(TimeManager.protectColorTags("[#FF0088]d MMM [/]'at' [#00bfff80]HH"), java.util.Locale.ROOT)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        assertEquals("[#FF0088]1 Jan [/]at [#00bfff80]00", TimeManager.restoreColorTags(format.format(java.util.Date(0))))
    }
}
