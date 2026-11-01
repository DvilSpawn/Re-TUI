package ohi.andre.consolelauncher.commands.tuixt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private fun entry(title: String, description: String = "", place: String = "Behavior › behavior.xml") =
        SettingsSearch.Entry(title, place, description, "", title)

    private val entries = listOf(
        entry("status_time_format", "Format of the status clock"),
        entry("output_time_format", "Timestamp format for output lines"),
        entry("time_size", "Text size of the time line", "Appearance › ui.xml › Status Lines"),
        entry("ascii_effect", "Animates the ASCII art each time Home appears"),
        entry("weather_location", "City or coordinates for the weather"),
        entry("ASCII Settings", "", "Personalization")
    )

    private fun titles(query: String) = SettingsSearch.search(entries, query).map { it.title }

    @Test
    fun wordsMatchSnakeCaseNamesAndRankNameHitsFirst() {
        assertEquals(listOf("status_time_format", "output_time_format"), titles("time format").take(2))
        assertEquals("ascii_effect", titles("ascii anim").first())
        assertTrue("weather_location" in titles("city"))
    }

    @Test
    fun everyWordMustMatchAndBlankFindsNothing() {
        assertTrue(titles("weather clock").isEmpty())
        assertTrue(titles("  ").isEmpty())
        assertEquals(listOf("ascii_effect", "ASCII Settings"), titles("ascii"))
    }

    @Test
    fun placeAndPunctuationAreSearchedToo() {
        assertEquals(listOf("time_size"), titles("status lines size"))
        assertTrue("status_time_format" in titles("status-time"))
    }
}
