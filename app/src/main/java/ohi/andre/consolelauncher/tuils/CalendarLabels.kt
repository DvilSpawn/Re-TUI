package ohi.andre.consolelauncher.tuils

import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

object CalendarLabels {
    fun weekdays(calendar: Calendar, locale: Locale): List<String> {
        val names = DateFormatSymbols(locale).shortWeekdays
        return (0..6).map { names[(calendar.firstDayOfWeek - 1 + it) % 7 + 1] }
    }

    fun leadingCells(calendar: Calendar): Int =
        (calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + 7) % 7
}
