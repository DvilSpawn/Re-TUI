package ohi.andre.consolelauncher.community

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Transport IDs and hosts belong to delivery/reporting, not conversation text. */
object CommunityMessagePresentation {
    private fun date(record: JSONObject) = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
        }.parse(record.optString("created"))
    }.getOrNull()
    fun day(record: JSONObject): String = date(record)?.let {
        SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(it)
    } ?: ""
    fun time(record: JSONObject): String = date(record)?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
    } ?: ""
    fun grouped(previous: JSONObject?, record: JSONObject): Boolean {
        if (previous == null || previous.optString("author_handle").isEmpty() || previous.optString("author_handle") != record.optString("author_handle")) return false
        val before = date(previous)?.time ?: return false
        val after = date(record)?.time ?: return false
        return after - before in 0..300_000L && day(previous) == day(record)
    }
    fun preview(record: JSONObject): String = record.optString("body").replace(Regex("\\s+"), " ").trim().let {
        val end = it.offsetByCodePoints(0, minOf(100, it.codePointCount(0, it.length)))
        it.substring(0, end) + if (end < it.length) "…" else ""
    }
    fun header(record: JSONObject): String {
        val handle = record.optString("author_handle").takeIf { it.matches(Regex("[-_a-zA-Z0-9]{3,24}")) } ?: "?"
        val time = date(record)?.let { SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(it) }
        return "@$handle" + (time?.let { " · $it" } ?: "")
    }
}
