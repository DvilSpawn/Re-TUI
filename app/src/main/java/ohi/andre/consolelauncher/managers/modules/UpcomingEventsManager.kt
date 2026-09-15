package ohi.andre.consolelauncher.managers.modules

import ohi.andre.consolelauncher.R
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.TextUtils
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.getInt
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import android.database.Cursor
import android.net.Uri
import java.util.HashSet
import java.util.Set
import ohi.andre.consolelauncher.managers.settings.LauncherSettings

object UpcomingEventsManager {
    private const val MAX_EVENTS = 20
    const val MAX_LOOKAHEAD_DAYS: Int = 366

    private val PROJECTION = arrayOf<String?>(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_LOCATION
    )

    fun hasCalendarPermission(context: Context): Boolean {
        return (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED)
    }

    fun formatUpcoming(context: Context): String {
        val lookaheadDays: Int = lookaheadDays
        val heading = formatHeading(lookaheadDays)
        if (!hasCalendarPermission(context)) {
            return context.getString(R.string.manager_upcomingeventsmanager_calendar_access_is_required_run_events_acc_cfe8f, heading)
        }

        val now = System.currentTimeMillis()
        val end = endOfLookaheadDay(now, lookaheadDays)

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, end)

        val out = StringBuilder(heading)
        val seen: MutableSet<String?> = HashSet<String?>()
        var count = 0

        try {
            context.getContentResolver().query(
                builder.build(),
                PROJECTION,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            ).use { cursor ->
                if (cursor != null) {
                    while (cursor.moveToNext() && count < MAX_EVENTS) {
                        val title = cursor.getString(0)
                        val begin = cursor.getLong(1)
                        val allDay = cursor.getInt(2) == 1
                        val location = cursor.getString(3)
                        val safeTitle =
                            if (TextUtils.isEmpty(title)) context.getString(R.string.manager_upcomingeventsmanager_untitled_event_ef8d2) else title!!.trim { it <= ' ' }
                        val key = begin.toString() + "|" + allDay + "|" + safeTitle.lowercase()
                        if (!seen.add(key)) {
                            continue
                        }

                        out.append('\n')
                            .append(formatWhen(begin, allDay))
                            .append(" - ")
                            .append(safeTitle)
                        if (!TextUtils.isEmpty(location)) {
                            out.append(" @ ").append(location)
                        }
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            return context.getString(R.string.manager_upcomingeventsmanager_unable_to_read_android_calendar_c129e, heading)
        }

        if (count == 0) {
            out.append('\n').append(formatEmptyMessage(lookaheadDays))
        }
        return out.toString()
    }

    fun formatModulePayload(context: Context): String {
        val now = System.currentTimeMillis()
        val out = StringBuilder()
        out.append("::title Events\n")
        for (line in formatUpcoming(context).split("\\r?\\n".toRegex()).toTypedArray()) {
            out.append("::body ").append(line).append('\n')
        }
        out.append("::suggest refresh | command | module -refresh events\n")
        out.append("::suggest access | command | events -access\n")
        out.append("::suggest open | command | intent -view content://com.android.calendar/time/")
            .append(now)
            .append('\n')
        return out.toString().trim { it <= ' ' }
    }

    fun formatUpcomingTsv(context: Context): String {
        if (!hasCalendarPermission(context)) {
            return ""
        }

        val now = System.currentTimeMillis()
        val end = endOfCurrentMonth(now)

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, end)

        val out = StringBuilder()
        val seen: MutableSet<String?> = HashSet<String?>()
        var count = 0

        try {
            context.getContentResolver().query(
                builder.build(),
                PROJECTION,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            ).use { cursor ->
                if (cursor != null) {
                    while (cursor.moveToNext() && count < MAX_EVENTS) {
                        val title = cursor.getString(0)
                        val begin = cursor.getLong(1)
                        val allDay = cursor.getInt(2) == 1
                        val location = cursor.getString(3)
                        val safeTitle =
                            if (TextUtils.isEmpty(title)) context.getString(R.string.manager_upcomingeventsmanager_untitled_event_ef8d2) else title!!.trim { it <= ' ' }
                        val key = begin.toString() + "|" + allDay + "|" + safeTitle.lowercase()
                        if (!seen.add(key)) {
                            continue
                        }

                        if (out.length > 0) {
                            out.append('\n')
                        }
                        out.append(formatDate(begin))
                            .append('\t')
                            .append(if (allDay) "" else formatTime(begin))
                            .append('\t')
                            .append(safeTsv(safeTitle))
                            .append('\t')
                            .append(safeTsv(location))
                        count++
                    }
                }
            }
        } catch (ignored: Exception) {
            return ""
        }
        return out.toString()
    }

    val lookaheadDays: Int
        get() = sanitizeLookaheadDays(getInt(Behavior.events_lookahead_days))

    fun sanitizeLookaheadDays(days: Int): Int {
        if (days < 0) {
            return 0
        }
        return min(days, MAX_LOOKAHEAD_DAYS)
    }

    private fun endOfLookaheadDay(now: Long, lookaheadDays: Int): Long {
        val end = Calendar.getInstance()
        end.setTimeInMillis(now)
        end.add(Calendar.DAY_OF_YEAR, sanitizeLookaheadDays(lookaheadDays))
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        end.set(Calendar.MILLISECOND, 999)
        return end.getTimeInMillis()
    }

    private fun endOfCurrentMonth(now: Long): Long {
        val end = Calendar.getInstance()
        end.setTimeInMillis(now)
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        end.set(Calendar.MILLISECOND, 999)
        return end.getTimeInMillis()
    }

    private fun formatHeading(lookaheadDays: Int): String {
        if (lookaheadDays <= 0) {
            return "[Upcoming events today]"
        }
        if (lookaheadDays == 1) {
            return "[Upcoming events today + 1 day]"
        }
        return "[Upcoming events today + " + lookaheadDays + " days]"
    }

    private fun formatEmptyMessage(lookaheadDays: Int): String {
        if (lookaheadDays <= 0) {
            return "No upcoming events today."
        }
        if (lookaheadDays == 1) {
            return "No upcoming events today or tomorrow."
        }
        return "No upcoming events through today + " + lookaheadDays + " days."
    }

    private fun formatWhen(millis: Long, allDay: Boolean): String {
        val calendar = Calendar.getInstance()
        calendar.setTimeInMillis(millis)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val pattern = if (allDay) " MMM yyyy" else " MMM yyyy hh:mma"
        val suffix = SimpleDateFormat(pattern, Locale.US)
        return day.toString() + ordinal(day) + suffix.format(calendar.getTime())
    }

    private fun formatDate(millis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.setTimeInMillis(millis)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val suffix = SimpleDateFormat(" MMM yyyy", Locale.US)
        return day.toString() + ordinal(day) + suffix.format(calendar.getTime())
    }

    private fun formatTime(millis: Long): String? {
        return SimpleDateFormat("hh:mma", Locale.US).format(millis)
    }

    private fun safeTsv(value: String?): String {
        return if (value == null) "" else value.replace('\t', ' ').replace('\n', ' ')
            .trim { it <= ' ' }
    }

    private fun ordinal(day: Int): String {
        if (day >= 11 && day <= 13) {
            return "th"
        }
        when (day % 10) {
            1 -> return "st"
            2 -> return "nd"
            3 -> return "rd"
            else -> return "th"
        }
    }
}
