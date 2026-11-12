package ohi.andre.consolelauncher.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class JournalFormatTest {
    @Test
    fun entriesRoundTripThroughTheDayFile() {
        val entries = listOf(
            JournalEntry("2026-11-04", "09:02", "standup #retui\n- [ ] ask about keys"),
            JournalEntry("2026-11-04", "13:15", "lunch\n\nwith a blank line inside")
        )
        val markdown = JournalFormat.render("2026-11-04", entries)
        assertEquals(entries, JournalFormat.parse("2026-11-04", markdown))
    }

    @Test
    fun tagsSkipHeadingsAndWordInnerHashes() {
        assertEquals(listOf("retui", "people"), JournalFormat.tags("shipped #retui with a#b and #people."))
    }

    @Test
    fun shiftCrossesMonths() {
        assertEquals("2026-10-31", JournalFormat.shift("2026-11-01", -1))
    }

    @Test
    fun quickNotesStayOneLine() {
        assertEquals("pick up laundry today", QuickNoteFormat.clean("  pick up laundry\n today "))
    }
}
