package ohi.andre.consolelauncher.community

import org.json.JSONObject

class CommunityState @JvmOverloads constructor(saved: JSONObject = JSONObject(), private val clock: () -> Long = System::currentTimeMillis) {
    var pane = CommunityPaneState(saved.optJSONObject("pane") ?: JSONObject())
    var mentions = CommunityMentionState(saved.optJSONObject("mentions") ?: JSONObject())
    var draft = saved.optString("draft")
    var pendingId: String? = saved.optString("pending_id").takeIf { it.isNotEmpty() }
        private set
    var pendingBody = saved.optString("pending_body")
        private set
    fun completePost() {
        if ((CommunityInput.parse(draft) as? CommunityInput.Message)?.body == pendingBody) draft = ""
        pendingId = null; pendingBody = ""
    }
    fun rejectPost() { pendingId = null; pendingBody = "" }
    fun beginPost(body: String): String {
        check(pendingId == null || pendingBody == body) { "Reconcile the previous uncertain draft before changing it." }
        if (pendingId == null) { pendingId = java.util.UUID.randomUUID().toString(); pendingBody = body }
        return pendingId!!
    }
    var account: CommunityClient.Account? = saved.optJSONObject("account")?.let {
        CommunityClient.Account(it.getString("id"), it.getString("handle"), it.getString("token"), it.optBoolean("moderator"), it.optString("policy_version"))
    }
        private set
    val blocked = strings(saved, "blocked").toMutableSet()
    val seen = strings(saved, "seen").toMutableSet()
    private fun strings(json: JSONObject, key: String): List<String> = json.optJSONArray(key)?.let { array ->
        (0 until array.length()).map { array.getString(it) }
    } ?: emptyList()
    fun signIn(next: CommunityClient.Account) {
        if (account?.id != next.id) clear()
        account = next
    }
    fun clear() { pane = CommunityPaneState(JSONObject()); mentions = CommunityMentionState(JSONObject()); account = null; draft = ""; pendingId = null; pendingBody = ""; messages = emptyList(); readingHistory = false; following = true; viewAnchor = ""; viewOffset = 0; newestObserved = null; arrivals = emptyList(); blocked.clear(); seen.clear() }
    fun toJson(): JSONObject {
        expire()
        return JSONObject().put("pane", pane.toJson()).put("mentions", mentions.toJson()).put("draft", draft).put("pending_id", pendingId ?: "").put("pending_body", pendingBody)
        .put("account", account?.let { JSONObject().put("id", it.id).put("handle", it.handle).put("token", it.token).put("moderator", it.moderator).put("policy_version", it.policyVersion) })
        .put("blocked", org.json.JSONArray(blocked.toList())).put("seen", org.json.JSONArray(seen.toList()))
        .put("messages", org.json.JSONArray(messages)).put("reading_history", readingHistory)
        .put("newest_observed", newestObserved).put("arrivals", org.json.JSONArray(arrivals))
        .put("following", following).put("view_anchor", viewAnchor).put("view_offset", viewOffset)
    }

    var messages: List<JSONObject> = saved.optJSONArray("messages")?.let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }.takeLast(200)
    } ?: emptyList()
        private set
    private var readingHistory = saved.optBoolean("reading_history")
    val readingOlder: Boolean get() = readingHistory
    var following = saved.optBoolean("following", !readingHistory)
    var viewAnchor = saved.optString("view_anchor")
    var viewOffset = saved.optInt("view_offset").coerceIn(-10000, 10000)
    private var newestObserved = saved.optJSONObject("newest_observed") ?: messages.lastOrNull()?.let {
        JSONObject().put("id", it.optString("id")).put("created", it.optString("created"))
    }
    private var arrivals: List<JSONObject> = saved.optJSONArray("arrivals")?.let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }.takeLast(50)
    } ?: emptyList()
    init { expire() }
    private fun createdMillis(record: JSONObject): Long? {
        val value = record.optString("created").replace(' ', 'T')
        if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"))) return null
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply {
                isLenient = false; timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(value)!!.time
        }.getOrNull()
    }
    fun expire(): Boolean {
        val now = clock()
        val retained = messages.filter { fresh(it, now) }
        val retainedArrivals = arrivals.filter { fresh(it, now) }
        val cursor = newestObserved?.takeIf { fresh(it, now) }
        val changed = retained.size != messages.size || retainedArrivals.size != arrivals.size || cursor !== newestObserved
        seen.removeAll(messages.filterNot { it in retained }.map { it.getString("id") }.toSet())
        messages = retained; arrivals = retainedArrivals; newestObserved = cursor
        if (messages.none { it.optString("id") == viewAnchor }) { viewAnchor = ""; viewOffset = 0 }
        return changed
    }
    fun expiryDelay(): Long {
        val now = clock()
        return (messages + arrivals + listOfNotNull(newestObserved)).mapNotNull { createdMillis(it) }
            .minOfOrNull { (it + 86_400_000L - now).coerceIn(1L, 15_000L) } ?: 15_000L
    }
    private fun fresh(record: JSONObject, now: Long): Boolean = createdMillis(record)?.let {
        it <= now && it > now - 86_400_000L
    } == true
    val newArrivalIds: Set<String> get() = arrivals.filterNot {
        it.getString("id") in seen || it.optString("author") == account?.id || it.optString("author_handle") in blocked
    }.map { it.getString("id") }.toSet()
    fun observeLatest(incoming: List<JSONObject>) {
        expire()
        val now = clock()
        val latest = incoming.filter { fresh(it, now) }
        val order = compareBy<JSONObject>({ it.getString("created") }, { it.getString("id") })
        val ids = latest.map { it.getString("id") }.toSet()
        val new = latest.filter { record -> newestObserved?.let { order.compare(record, it) > 0 } ?: false }
        arrivals = (arrivals.filter { it.getString("id") in ids && it.getString("id") !in seen } + new.map {
            JSONObject().put("id", it.getString("id")).put("created", it.getString("created")).put("author", it.optString("author")).put("author_handle", it.optString("author_handle"))
        }).distinctBy { it.getString("id") }.takeLast(50)
        latest.maxWithOrNull(order)?.let { record ->
            if (newestObserved == null || order.compare(record, newestObserved!!) > 0)
                newestObserved = JSONObject().put("id", record.getString("id")).put("created", record.getString("created"))
        }
    }
    fun observeRealtime(record: JSONObject) {
        // Historical windows may not contain the latest arrivals; keep their read metadata.
        observeLatest(messages + arrivals + record)
    }
    fun focusMessage(record: JSONObject) {
        expire(); messages = listOf(record).filter { fresh(it, clock()) }; readingHistory = true; following = false; viewAnchor = ""; viewOffset = 0
        seen.retainAll(messages.map { it.getString("id") }.toSet())
    }
    @JvmOverloads fun merge(incoming: List<JSONObject>, checkedIds: Set<String>, returnLatest: Boolean = false) {
        expire()
        val now = clock()
        val freshIncoming = incoming.filter { fresh(it, now) }
        val order = compareBy<JSONObject>({ it.getString("created") }, { it.getString("id") })
        if (returnLatest) readingHistory = false
        else messages.firstOrNull()?.let { first -> if (freshIncoming.any { order.compare(it, first) < 0 }) readingHistory = true }
        if (returnLatest) following = true else if (readingHistory) following = false
        val records = messages.filterNot { it.getString("id") in checkedIds }.associateBy { it.getString("id") }.toMutableMap()
        freshIncoming.forEach { records[it.getString("id")] = it }
        val sorted = records.values.sortedWith(order)
        messages = if (readingHistory) sorted.take(200) else sorted.takeLast(200)
        arrivals = arrivals.filterNot { it.getString("id") in seen }
        seen.retainAll(messages.map { it.getString("id") }.toSet())
    }
}
