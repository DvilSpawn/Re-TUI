package ohi.andre.consolelauncher.community

import org.json.JSONArray
import org.json.JSONObject

/** Stores recipient-only routing metadata, not additional copies of mentioned text. */
class CommunityMentionState(saved: JSONObject) {
    private fun rows(json: JSONObject, key: String): List<JSONObject> = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.take(200) } ?: emptyList()
    var items = rows(saved, "items")
        private set
    private var seenMetadata = rows(saved, "seen_metadata")
    private val seen = saved.optJSONArray("seen")?.let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() } ?: mutableSetOf()
    init {
        if (seenMetadata.isEmpty()) seenMetadata = items.filter { it.optString("id") in seen }.map { metadata(it) }
        expire()
    }
    private fun metadata(item: JSONObject) = JSONObject().put("id", item.optString("id")).put("channel", item.optString("channel")).put("expires_at", item.optString("expires_at"))
    private fun fresh(item: JSONObject, now: Long): Boolean {
        val expiry = item.optString("expires_at")
        if (expiry.isEmpty()) return item.optString("channel") in setOf("bugs", "help", "features", "presets")
        return runCatching {
            val value = expiry.replace(' ', 'T')
            if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"))) return@runCatching false
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply {
                isLenient = false; timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(value)!!.time > now
        }.getOrDefault(false)
    }
    fun expire(): Boolean {
        val now = System.currentTimeMillis()
        val retained = items.filter { fresh(it, now) }
        val changed = retained.size != items.size
        items = retained; seenMetadata = seenMetadata.filter { fresh(it, now) }.takeLast(200)
        seen.clear(); seen.addAll(seenMetadata.map { it.optString("id") })
        return changed
    }
    fun expiryDelay(): Long {
        val now = System.currentTimeMillis()
        return items.mapNotNull { item ->
            runCatching { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply {
                isLenient = false; timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(item.optString("expires_at").replace(' ', 'T'))?.time }.getOrNull()
        }.minOfOrNull { (it - now).coerceIn(1L, 15_000L) } ?: 15_000L
    }
    @JvmOverloads fun replace(next: List<JSONObject>, keepUnread: Boolean = false) {
        expire()
        val fresh = next.distinctBy { it.optString("id") }.filter { fresh(it, System.currentTimeMillis()) }
        val retained = if (keepUnread) {
            (fresh.filter { it.optString("id") !in seen } + fresh.filter { it.optString("id") in seen })
                .take(200).map { it.optString("id") }.toSet()
        } else fresh.take(200).map { it.optString("id") }.toSet()
        items = fresh.filter { it.optString("id") in retained }
    }
    fun read(id: String): Boolean {
        val item = items.firstOrNull { it.optString("id") == id } ?: return false
        if (!seen.add(id)) return false
        seenMetadata = (seenMetadata + metadata(item)).takeLast(200)
        seen.retainAll(seenMetadata.map { it.optString("id") }.toSet())
        return true
    }
    fun unread(channel: String): Int { expire(); return items.count { it.optString("channel") == channel && it.optString("id") !in seen } }
    fun toJson(): JSONObject { expire(); return JSONObject().put("items", JSONArray(items)).put("seen_metadata", JSONArray(seenMetadata)).put("seen", JSONArray(seen.toList())) }
}
