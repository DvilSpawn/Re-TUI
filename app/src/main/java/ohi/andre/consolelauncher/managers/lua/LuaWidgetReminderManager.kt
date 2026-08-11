package ohi.andre.consolelauncher.managers.lua

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object LuaWidgetReminderManager {
    const val EXTRA_KEY: String = "lua_widget_reminder_key"

    const val PREFS = "retui_lua_widget_reminders"
    private const val KEY_IDS = "ids"
    private const val KEY_WIDGET_PREFIX = "widget_"
    private const val KEY_REMINDER_PREFIX = "reminder_"
    private const val KEY_TITLE_PREFIX = "title_"
    private const val KEY_AT_PREFIX = "at_"
    private const val KEY_REPEAT_PREFIX = "repeat_"
    private const val REPEAT_DAILY = "daily"
    private const val REPEAT_ONCE = "once"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    @JvmStatic
    fun schedule(
        context: Context?,
        widgetId: String?,
        reminderId: String?,
        title: String?,
        atMillis: Long,
        repeatMode: String?
    ): Boolean {
        if (context == null || TextUtils.isEmpty(widgetId) || TextUtils.isEmpty(reminderId)) {
            return false
        }
        val widget = LuaWidgetManager.normalizeId(widgetId)
        val reminder = safeId(reminderId)
        if (TextUtils.isEmpty(widget) || TextUtils.isEmpty(reminder) || atMillis <= 0L) {
            return false
        }

        val repeat = if (REPEAT_DAILY.equals(repeatMode, ignoreCase = true)) REPEAT_DAILY else REPEAT_ONCE
        val target = if (REPEAT_DAILY == repeat) nextFutureDailyMillis(atMillis) else atMillis
        if (target <= System.currentTimeMillis()) {
            return false
        }

        val key = key(widget, reminder)
        val record = ReminderRecord(key, widget, reminder, safeTitle(title), target, repeat)
        save(context, record)
        scheduleAlarm(context, record)
        return true
    }

    @JvmStatic
    fun cancel(context: Context?, widgetId: String?, reminderId: String?) {
        if (context == null || TextUtils.isEmpty(widgetId) || TextUtils.isEmpty(reminderId)) {
            return
        }
        remove(context, key(LuaWidgetManager.normalizeId(widgetId), safeId(reminderId)))
    }

    @JvmStatic
    fun cancelPrefix(context: Context?, widgetId: String?, prefix: String?) {
        if (context == null || TextUtils.isEmpty(widgetId)) {
            return
        }
        val widget = LuaWidgetManager.normalizeId(widgetId)
        val cleanPrefix = safeId(prefix)
        val targets = ArrayList<String>()
        for (record in records(context)) {
            if (record.widgetId == widget && record.reminderId.startsWith(cleanPrefix)) {
                targets.add(record.key)
            }
        }
        for (target in targets) {
            remove(context, target)
        }
    }

    @JvmStatic
    fun list(context: Context?, widgetId: String?): JSONArray {
        val array = JSONArray()
        if (context == null) {
            return array
        }
        val widget = LuaWidgetManager.normalizeId(widgetId)
        for (record in records(context)) {
            if (!TextUtils.isEmpty(widget) && record.widgetId != widget) {
                continue
            }
            val item = JSONObject()
            item.put("id", record.reminderId)
            item.put("title", record.title)
            item.put("at_ms", record.atMillis)
            item.put("repeat", record.repeatMode)
            array.put(item)
        }
        return array
    }

    @JvmStatic
    fun fire(context: Context?, key: String?): ReminderRecord? {
        if (context == null || TextUtils.isEmpty(key)) {
            return null
        }
        val record = get(context, key) ?: return null
        if (REPEAT_DAILY == record.repeatMode) {
            val next = ReminderRecord(
                record.key,
                record.widgetId,
                record.reminderId,
                record.title,
                nextFutureDailyMillis(record.atMillis + DAY_MS),
                record.repeatMode
            )
            save(context, next)
            scheduleAlarm(context, next)
        } else {
            remove(context, record.key)
        }
        return record
    }

    @JvmStatic
    fun nextDailyMillis(time: String?): Long {
        val value = if (time == null) "" else time.trim { it <= ' ' }
        val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value) ?: return 0L
        val hour = match.groupValues[1].toIntOrNull() ?: return 0L
        val minute = match.groupValues[2].toIntOrNull() ?: return 0L
        if (hour !in 0..23 || minute !in 0..59) {
            return 0L
        }
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return nextFutureDailyMillis(calendar.timeInMillis)
    }

    @JvmStatic
    fun parseLocalDateTimeMillis(value: String?): Long {
        val text = if (value == null) "" else value.trim { it <= ' ' }
        if (TextUtils.isEmpty(text)) {
            return 0L
        }
        val patterns = arrayOf(
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "dd/MM/yyyy HH:mm",
            "dd-MM-yyyy HH:mm"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                val parsed: Date? = format.parse(text)
                if (parsed != null) {
                    return parsed.time
                }
            } catch (ignored: Exception) {
            }
        }
        return 0L
    }

    private fun save(context: Context, record: ReminderRecord) {
        val ids = ArrayList<String>(ids(context))
        if (!ids.contains(record.key)) {
            ids.add(record.key)
        }
        prefs(context).edit()
            .putString(KEY_IDS, TextUtils.join(",", ids))
            .putString(KEY_WIDGET_PREFIX + record.key, record.widgetId)
            .putString(KEY_REMINDER_PREFIX + record.key, record.reminderId)
            .putString(KEY_TITLE_PREFIX + record.key, record.title)
            .putLong(KEY_AT_PREFIX + record.key, record.atMillis)
            .putString(KEY_REPEAT_PREFIX + record.key, record.repeatMode)
            .apply()
    }

    private fun remove(context: Context, key: String?) {
        if (TextUtils.isEmpty(key)) {
            return
        }
        cancelAlarm(context, key!!)
        val ids = ArrayList<String>(ids(context))
        ids.remove(key)
        prefs(context).edit()
            .putString(KEY_IDS, TextUtils.join(",", ids))
            .remove(KEY_WIDGET_PREFIX + key)
            .remove(KEY_REMINDER_PREFIX + key)
            .remove(KEY_TITLE_PREFIX + key)
            .remove(KEY_AT_PREFIX + key)
            .remove(KEY_REPEAT_PREFIX + key)
            .apply()
    }

    private fun get(context: Context, key: String?): ReminderRecord? {
        if (TextUtils.isEmpty(key) || !ids(context).contains(key)) {
            return null
        }
        val prefs = prefs(context)
        return ReminderRecord(
            key!!,
            prefs.getString(KEY_WIDGET_PREFIX + key, "")!!,
            prefs.getString(KEY_REMINDER_PREFIX + key, "")!!,
            prefs.getString(KEY_TITLE_PREFIX + key, "")!!,
            prefs.getLong(KEY_AT_PREFIX + key, 0L),
            prefs.getString(KEY_REPEAT_PREFIX + key, REPEAT_ONCE)!!
        )
    }

    private fun records(context: Context): MutableList<ReminderRecord> {
        val records = ArrayList<ReminderRecord>()
        for (id in ids(context)) {
            val record = get(context, id)
            if (record != null) {
                records.add(record)
            }
        }
        return records
    }

    private fun scheduleAlarm(context: Context, record: ReminderRecord) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val pendingIntent = pendingIntent(context, record.key, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, record.atMillis, pendingIntent)
        } catch (e: SecurityException) {
            alarm.set(AlarmManager.RTC_WAKEUP, record.atMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context, key: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val pendingIntent = pendingIntent(context, key, PendingIntent.FLAG_NO_CREATE)
        if (pendingIntent != null) {
            alarm.cancel(pendingIntent)
        }
    }

    private fun pendingIntent(context: Context, key: String, flag: Int): PendingIntent? {
        val intent = Intent(context, LuaWidgetReminderReceiver::class.java)
        intent.putExtra(EXTRA_KEY, key)
        return PendingIntent.getBroadcast(
            context,
            abs(key.hashCode()),
            intent,
            PendingIntent.FLAG_IMMUTABLE or flag
        )
    }

    private fun ids(context: Context): MutableList<String> {
        val values = ArrayList<String>()
        val raw = prefs(context).getString(KEY_IDS, "")
        if (TextUtils.isEmpty(raw)) {
            return values
        }
        for (part in raw!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val clean = part.trim { it <= ' ' }
            if (!TextUtils.isEmpty(clean) && !values.contains(clean)) {
                values.add(clean)
            }
        }
        return values
    }

    private fun key(widgetId: String?, reminderId: String?): String {
        return safeId(widgetId) + "__" + safeId(reminderId)
    }

    private fun safeId(value: String?): String {
        if (value == null) {
            return ""
        }
        return value.trim { it <= ' ' }
            .lowercase(Locale.US)
            .replace("[^a-z0-9_.-]+".toRegex(), "_")
            .trim('_')
    }

    private fun safeTitle(value: String?): String {
        val clean = if (value == null) "" else value.trim { it <= ' ' }
        return if (TextUtils.isEmpty(clean)) "Habit reminder" else clean
    }

    private fun nextFutureDailyMillis(value: Long): Long {
        var target = value
        val now = System.currentTimeMillis()
        while (target <= now) {
            target += DAY_MS
        }
        return target
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    class ReminderRecord(
        @JvmField val key: String,
        @JvmField val widgetId: String,
        @JvmField val reminderId: String,
        @JvmField val title: String,
        @JvmField val atMillis: Long,
        @JvmField val repeatMode: String
    )
}
