package ohi.andre.consolelauncher.managers.modules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.text.TextUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.Calendar
import kotlin.math.abs
import java.util.ArrayList

object ReminderManager {
    const val EXTRA_ID: String = "reminder_id"
    const val EXTRA_TITLE: String = "reminder_title"
    const val EXTRA_AT: String = "reminder_at"

    private const val PREFS = "retui_reminders"
    private const val KEY_IDS = "ids"
    private const val KEY_TITLE_PREFIX = "title_"
    private const val KEY_AT_PREFIX = "at_"

    @JvmStatic
    fun add(context: Context, title: String?, atMillis: Long): Reminder {
        val id = System.currentTimeMillis().toString()
        val reminder = Reminder(id, title, atMillis)
        save(context, reminder)
        schedule(context, reminder)
        return reminder
    }

    @JvmStatic
    fun save(context: Context, reminder: Reminder?) {
        if (reminder == null || TextUtils.isEmpty(reminder.id)) return
        val ids = ArrayList<String?>(ids(context))
        if (!ids.contains(reminder.id)) {
            ids.add(reminder.id)
        }
        prefs(context).edit()
            .putString(KEY_IDS, TextUtils.join(",", ids))
            .putString(KEY_TITLE_PREFIX + reminder.id, safe(reminder.title))
            .putLong(KEY_AT_PREFIX + reminder.id, reminder.atMillis)
            .apply()
        schedule(context, reminder)
    }

    @JvmStatic
    fun remove(context: Context, id: String?) {
        val clean = safe(id)
        if (TextUtils.isEmpty(clean)) return
        val ids = ArrayList<String?>(ids(context))
        ids.remove(clean)
        cancel(context, clean)
        prefs(context).edit()
            .putString(KEY_IDS, TextUtils.join(",", ids))
            .remove(KEY_TITLE_PREFIX + clean)
            .remove(KEY_AT_PREFIX + clean)
            .apply()
    }

    @JvmStatic
    fun get(context: Context, idOrIndex: String?): Reminder? {
        val key = resolveId(context, idOrIndex)
        if (TextUtils.isEmpty(key)) return null
        val prefs = prefs(context)
        if (!ids(context).contains(key)) return null
        return Reminder(
            key!!,
            prefs.getString(KEY_TITLE_PREFIX + key, ""),
            prefs.getLong(KEY_AT_PREFIX + key, 0L)
        )
    }

    @JvmStatic
    fun list(context: Context): MutableList<Reminder> {
        val reminders = ArrayList<Reminder>()
        val prefs = prefs(context)
        for (id in ids(context)) {
            reminders.add(
                Reminder(
                    id,
                    prefs.getString(KEY_TITLE_PREFIX + id, ""),
                    prefs.getLong(KEY_AT_PREFIX + id, 0L)
                )
            )
        }
        Collections.sort<Reminder?>(
            reminders,
            Comparator { left: Reminder?, right: Reminder? ->
                left!!.atMillis.compareTo(right!!.atMillis)
            })
        return reminders
    }

    @JvmStatic
    fun formatList(context: Context): String {
        val reminders = list(context)
        if (reminders.isEmpty()) {
            return "No reminders."
        }
        val out = StringBuilder()
        var index = 1
        for (reminder in reminders) {
            if (out.length > 0) out.append('\n')
            out.append("ID ").append(index++)
                .append(": ")
                .append(reminder.title)
                .append(" @ ")
                .append(formatWhen(reminder.atMillis))
        }
        return out.toString()
    }

    @JvmStatic
    fun formatPreview(context: Context): String {
        val reminders = list(context)
        if (reminders.isEmpty()) return "No reminders."
        val out = StringBuilder()
        val limit = minOf(reminders.size, 3)
        for (index in 0 until limit) {
            if (out.isNotEmpty()) out.append('\n')
            val reminder = reminders[index]
            out.append(index + 1).append(". ").append(reminder.title)
                .append(" @ ").append(formatWhen(reminder.atMillis))
        }
        if (reminders.size > limit) out.append("\n+").append(reminders.size - limit).append(" more")
        return out.toString()
    }

    @JvmStatic
    fun parseCliDateTime(date: String?, time: String?): Long? {
        if (!safe(date).matches(Regex("\\d{2}/\\d{2}/\\d{2}")) ||
            !safe(time).matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d"))) return null
        val (day, month, year) = safe(date).split('/').map(String::toInt)
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                set(2000 + year, month - 1, day, safe(time).substring(0, 2).toInt(), safe(time).substring(3).toInt(), 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @JvmStatic
    fun resolveId(context: Context, idOrIndex: String?): String? {
        val raw = safe(idOrIndex)
        if (TextUtils.isEmpty(raw)) return ""
        val reminders = list(context)
        try {
            val index = raw.toInt()
            if (index >= 1 && index <= reminders.size) {
                return reminders.get(index - 1).id
            }
        } catch (ignored: Exception) {
        }
        for (reminder in reminders) {
            if (reminder.id == raw) return reminder.id
        }
        return ""
    }

    @JvmStatic
    fun parseDateTime(date: String?, time: String?): kotlin.Long? {
        val value = safe(date) + " " + safe(time).uppercase().replace(".", "")
        val patterns = arrayOf<String?>(
            "dd/MM/yyyy h:mma",
            "dd/MM/yyyy hh:mma",
            "dd/MM/yyyy HH:mm",
            "dd-MM-yyyy h:mma",
            "dd-MM-yyyy HH:mm",
            "yyyy-MM-dd h:mma",
            "yyyy-MM-dd HH:mm"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.setLenient(false)
                val parsed = format.parse(value)
                if (parsed != null) return parsed.getTime()
            } catch (ignored: ParseException) {
            }
        }
        return null
    }

    @JvmStatic
    fun formatWhen(atMillis: kotlin.Long): String {
        if (atMillis <= 0L) return "unscheduled"
        return SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US).format(Date(atMillis))
    }

    private fun schedule(context: Context, reminder: Reminder?) {
        if (reminder == null || reminder.atMillis <= System.currentTimeMillis()) return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        if (alarm == null) return
        val pendingIntent = pendingIntent(
            context,
            reminder.id,
            reminder.title,
            reminder.atMillis,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, pendingIntent)
    }

    private fun cancel(context: Context, id: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        if (alarm == null) return
        val pendingIntent = pendingIntent(context, id, "", 0L, PendingIntent.FLAG_NO_CREATE)
        if (pendingIntent != null) {
            alarm.cancel(pendingIntent)
        }
    }

    private fun pendingIntent(
        context: Context?,
        id: String,
        title: String?,
        atMillis: kotlin.Long,
        flag: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        intent.putExtra(EXTRA_ID, id)
        intent.putExtra(EXTRA_TITLE, title)
        intent.putExtra(EXTRA_AT, atMillis)
        val requestCode = abs(id.hashCode())
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or flag
        )
    }

    private fun ids(context: Context): MutableList<String> {
        return parseIds(prefs(context).getString(KEY_IDS, ""))
    }

    private fun parseIds(raw: String?): MutableList<String> {
        val ids = ArrayList<String>()
        if (TextUtils.isEmpty(raw)) return ids
        for (part in raw!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val id = safe(part)
            if (!TextUtils.isEmpty(id) && !ids.contains(id)) ids.add(id)
        }
        return ids
    }

    private fun safe(value: String?): String {
        return if (value == null) "" else value.trim { it <= ' ' }
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    class Reminder internal constructor(
        @JvmField val id: String,
        @JvmField val title: String?,
        @JvmField val atMillis: kotlin.Long
    )
}
