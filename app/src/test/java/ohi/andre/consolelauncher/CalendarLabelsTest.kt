package ohi.andre.consolelauncher

import ohi.andre.consolelauncher.tuils.CalendarLabels
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class CalendarLabelsTest {
    @Test fun septemberStartsUnderTuesdayInBothWeekOrders() {
        for ((locale, expectedOffset, firstName) in listOf(
            Triple(Locale.US, 2, "Sun"), Triple(Locale.GERMANY, 1, "Mo.")
        )) {
            val calendar = Calendar.getInstance(locale).apply { set(2026, Calendar.SEPTEMBER, 1) }
            assertEquals(expectedOffset, CalendarLabels.leadingCells(calendar))
            assertEquals(firstName, CalendarLabels.weekdays(calendar, locale).first())
            assertEquals(7, CalendarLabels.weekdays(calendar, locale).size)
        }
    }
}
