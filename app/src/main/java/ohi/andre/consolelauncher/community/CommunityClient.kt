package ohi.andre.consolelauncher.community

import ohi.andre.consolelauncher.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CommunityClient @JvmOverloads constructor(private val base: String = PRODUCTION) {
    companion object {
        const val PRODUCTION = "https://irc.kokodoko.cloud/community"
        const val POLICY_VERSION = "2026-10-10"
        const val DELETION_URL = "$PRODUCTION/account-deletion"
    }
    class Failure(val status: Int, val retrySeconds: Long, message: String) : IOException(message)
    class Cancelled : IOException("Community operation cancelled; delivery may be uncertain.")
    data class Account(val id: String, val handle: String, val token: String, val moderator: Boolean = false, val policyVersion: String = "")
    private val lock = Any()
    private var generation = 0L
    private val epoch = ThreadLocal<Long>()
    private val calls = mutableSetOf<okhttp3.Call>()
    @Volatile private var serverTime: Pair<Long, Long>? = null
    fun now(): Long = serverTime?.let { it.first + (System.nanoTime() - it.second) / 1_000_000L } ?: System.currentTimeMillis()
    internal fun updateServerClock(date: String?) {
        if (date == null) return
        val timestamp = runCatching { java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).apply {
            isLenient = false; timeZone = java.util.TimeZone.getTimeZone("GMT")
        }.parse(date)?.time }.getOrNull() ?: return
        // HTTP Date has second precision. Use its upper bound so slight device clock
        // skew cannot discard a just-committed message; expiry remains conservative.
        serverTime = timestamp + 999L to System.nanoTime()
    }
    fun <T> operation(task: () -> T): T {
        if (epoch.get() != null) return task()
        synchronized(lock) { epoch.set(generation) }
        return try { task() } finally { epoch.remove() }
    }
    private fun checkActive() {
        if (Thread.currentThread().isInterrupted || epoch.get()?.let { it != generation } == true) throw Cancelled()
    }
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).build()
    private val streamHttp = http.newBuilder().readTimeout(75, TimeUnit.SECONDS).build()
    init {
        require(base == PRODUCTION || (BuildConfig.DEBUG && base.toHttpUrl().scheme == "http" &&
            base.toHttpUrl().host in setOf("127.0.0.1", "localhost")))
    }
    fun register(handle: String, password: String, confirmation: String): Account = operation {
        require(handle.matches(Regex("[-_a-zA-Z0-9]{3,24}"))) { "Handle: 3–24 ASCII letters, digits, - or _." }
        require(password.isNotEmpty() && password == confirmation) { "Passwords must be nonempty and match." }
        val record = request("members/records", "", JSONObject().put("handle", handle).put("password", password).put("passwordConfirm", confirmation))
        val canonical = record.optString("handle")
        if (!canonical.matches(Regex("[-_a-z0-9]{3,24}"))) throw IOException("Invalid registration response.")
        login(canonical, password)
    }
    fun login(handle: String, password: String): Account {
        val result = request("members/auth-with-password", "", JSONObject().put("identity", handle).put("password", password))
        val record = result.getJSONObject("record")
        val token = result.optString("token")
        if (token.isEmpty() || !record.optString("id").matches(Regex("[a-zA-Z0-9]{15}")) ||
            !record.optString("handle").matches(Regex("[-_a-zA-Z0-9]{3,24}"))) throw IOException("Invalid authentication response.")
        return Account(record.getString("id"), record.getString("handle"), token,
            record.optBoolean("moderator") && record.optString("handle") == "dvil", record.optString("policy_version"))
    }
    fun acceptPolicy(account: Account): Account {
        val response = request("policy", account.token, JSONObject().put("version", POLICY_VERSION), "$base/api/community/policy")
        if (response.optString("version") != POLICY_VERSION) throw IOException("Invalid policy acknowledgement.")
        return account.copy(policyVersion = POLICY_VERSION)
    }
    fun reportSource(account: Account, collection: String, id: String, reason: String) {
        require(collection in setOf("messages", "threads", "thread_replies") && id.matches(Regex("[a-zA-Z0-9]{15}")))
        require(CommunityInput.message(reason) is CommunityInput.Message)
        request("report", account.token, JSONObject().put("collection", collection).put("id", id).put("reason", reason), "$base/api/community/report")
    }
    fun moderation(account: Account, queue: String, page: Int): JSONObject {
        require(queue in setOf("reports", "moderator_requests") && page in 1..1000)
        return request("moderation", account.token, url = "$base/api/community/moderation?collection=$queue&page=$page&perPage=50")
    }
    fun resolve(account: Account, queue: String, id: String, action: String): JSONObject {
        require(queue in setOf("reports", "moderator_requests") && id.matches(Regex("[a-zA-Z0-9]{15}")) && action in setOf("dismiss", "remove", "ban"))
        return request("moderation/action", account.token, JSONObject().put("queue", queue).put("id", id).put("action", action), "$base/api/community/moderation/action")
    }
    fun deleteAccount(account: Account, password: String): Boolean {
        require(password.isNotEmpty())
        return request("account-delete", account.token, JSONObject().put("password", password).put("confirm", account.handle), "$base/api/community/account-delete").optBoolean("deleted")
    }
    class Uncertain : IOException("Delivery uncertain; draft kept. Submit the same draft to reconcile/retry; do not send a second copy.")
    fun find(account: Account, clientId: String): JSONObject? = records(account,
        "author = ${quote(account.id)} && client_id = ${quote(clientId)}").firstOrNull {
        it.optString("author") == account.id && it.optString("client_id") == clientId
    }
    @JvmOverloads fun post(account: Account, body: String, clientId: String, uncertainAttempt: Boolean = false): JSONObject =
        deliver(account, "messages", JSONObject().put("author", account.id).put("body", body).put("client_id", clientId), uncertainAttempt)
    fun postThread(account: Account, channel: String, title: String, body: String, clientId: String, uncertainAttempt: Boolean): JSONObject {
        require(channel in setOf("bugs", "help", "features", "presets"))
        require(title.isNotBlank() && title.codePointCount(0, title.length) <= 120 && title.toByteArray(Charsets.UTF_8).size <= 480)
        return deliver(account, "threads", JSONObject().put("author", account.id).put("channel", channel)
            .put("title", title).put("body", body).put("client_id", clientId), uncertainAttempt)
    }
    fun postReply(account: Account, thread: String, body: String, clientId: String, uncertainAttempt: Boolean): JSONObject {
        require(thread.matches(Regex("[a-zA-Z0-9]{15}")))
        return deliver(account, "thread_replies", JSONObject().put("author", account.id).put("thread", thread)
            .put("body", body).put("client_id", clientId), uncertainAttempt)
    }
    private fun deliver(account: Account, collection: String, payload: JSONObject, uncertainAttempt: Boolean): JSONObject = operation {
        val body = payload.getString("body"); val clientId = payload.getString("client_id")
        require(CommunityInput.message(body) is CommunityInput.Message)
        require(java.util.UUID.fromString(clientId).toString() == clientId)
        fun matches(record: JSONObject) = record.optString("id").matches(Regex("[a-zA-Z0-9]{15}")) && payload.keys().asSequence().all { record.optString(it) == payload.getString(it) }
        fun reconcile(): JSONObject? {
            val match = try { records(account, "author = ${quote(account.id)} && client_id = ${quote(clientId)}", collection = collection).firstOrNull {
                it.optString("author") == account.id && it.optString("client_id") == clientId
            } } catch (lookup: Exception) {
                if (lookup is Failure && lookup.status in setOf(401, 403)) throw lookup
                throw Uncertain()
            }
            if (match != null && !matches(match)) throw Uncertain()
            return match
        }
        // A retained attempt may have committed, including after lifecycle cancellation.
        if (uncertainAttempt) reconcile()?.let { return@operation it }
        try {
            val record = request("$collection/records", account.token, payload)
            if (!matches(record))
                throw Uncertain()
            record
        } catch (error: Exception) {
            synchronized(lock) { checkActive() }
            if (error is Failure && error.status in setOf(401, 403, 428)) throw error
            if (error is Failure && error.status == 429 && !uncertainAttempt) throw error
            reconcile()?.let { return@operation it }
            if (error is Failure && error.status == 400 && !uncertainAttempt) throw error
            throw Uncertain()
        }
    }
    fun threads(account: Account, channel: String, older: JSONObject?): List<JSONObject> {
        require(channel in setOf("bugs", "help", "features", "presets"))
        val filter = "channel = ${quote(channel)}" + (older?.let { " && (${cursor(it, "<")})" } ?: "")
        return records(account, filter, "threads")
    }
    @JvmOverloads fun replies(account: Account, thread: String, after: JSONObject?, latest: Boolean = false, older: Boolean = false): List<JSONObject> {
        require(thread.matches(Regex("[a-zA-Z0-9]{15}")))
        val filter = "thread = ${quote(thread)}" + (after?.takeUnless { latest }?.let { " && (${cursor(it, if (older) "<" else ">")})" } ?: "")
        return records(account, filter, "thread_replies", if (latest || older) "-created,-id" else "created,id")
            .sortedWith(compareBy({ it.getString("created") }, { it.getString("id") }))
    }
    private fun cursor(record: JSONObject, direction: String): String {
        val created = quote(record.getString("created")); val id = quote(record.getString("id"))
        return "created $direction $created || (created = $created && id $direction $id)"
    }
    fun source(account: Account, collection: String, id: String): JSONObject {
        require(collection in setOf("messages", "threads", "thread_replies"))
        require(id.matches(Regex("[a-zA-Z0-9]{15}")))
        return request("$collection/records/$id", account.token)
    }
    fun moderate(account: Account, body: String) {
        require(CommunityInput.message(body) is CommunityInput.Message)
        request("moderator_requests/records", account.token, JSONObject().put("reporter", account.id).put("kind", "moderate").put("body", body))
    }
    fun users(account: Account, fragment: String, page: Int): JSONObject {
        require(fragment.length <= 24 && fragment.matches(Regex("[-_a-zA-Z0-9]*")) && page > 0)
        val url = "$base/api/community/users".toHttpUrl().newBuilder().addQueryParameter("q", fragment)
            .addQueryParameter("page", page.toString()).addQueryParameter("perPage", "20").build()
        return request("users", account.token, url = url.toString())
    }
    fun presence(account: Account, active: Boolean): JSONObject =
        request("presence", account.token, JSONObject().put("active", active), "$base/api/community/presence")
    fun mentions(account: Account, page: Int): JSONObject {
        require(page > 0)
        return request("mentions", account.token, url = "$base/api/community/mentions?page=$page&perPage=50")
    }
    fun mentionWindow(account: Account, cached: List<JSONObject>): JSONObject = operation {
        val items = mutableListOf<JSONObject>()
        var page = 0
        var more: Boolean
        do {
            val response = mentions(account, ++page)
            val rows = response.getJSONArray("items")
            items.addAll((0 until rows.length()).map { rows.getJSONObject(it) })
            more = response.getBoolean("hasMore")
        } while (more && page < 4)
        // A bounded page is not an authoritative absence. Validate retained sources
        // outside the window without storing another copy of their bodies.
        if (more) {
            val received = items.map { it.getString("id") }.toSet()
            val missing = cached.take(200).filter { it.optString("id") !in received }
            for ((kind, collection) in listOf("message" to "messages", "thread" to "threads", "thread_reply" to "thread_replies")) {
                val candidates = missing.filter { it.optString("kind") == kind }
                val valid = checked(account, candidates.map { it.getString("source_id") }, collection).map { it.getString("id") }.toSet()
                items.addAll(candidates.filter { it.optString("source_id") in valid })
            }
        }
        JSONObject().put("items", org.json.JSONArray(items.distinctBy { it.getString("id") }))
            .put("page", page).put("hasMore", more)
    }
    fun report(account: Account, messageId: String) {
        require(messageId.matches(Regex("[a-zA-Z0-9]{15}")))
        request("reports/records", account.token, JSONObject().put("reporter", account.id)
            .put("message", messageId).put("reason", "Reported from Re-TUI"))
    }
    @JvmOverloads fun checked(account: Account, ids: List<String>, collection: String = "messages"): List<JSONObject> = operation {
        require(collection in setOf("messages", "threads", "thread_replies"))
        ids.take(200).chunked(50).flatMap { batch -> records(account, batch.joinToString(" || ") { "id = ${quote(it)}" }, collection) }
    }
    private fun quote(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
    fun history(account: Account, older: JSONObject?): List<JSONObject> {
        val filter = older?.let {
            val created = quote(it.getString("created")); val id = quote(it.getString("id"))
            "created < $created || (created = $created && id < $id)"
        }
        return records(account, filter).sortedWith(compareBy({ it.getString("created") }, { it.getString("id") }))
    }
    private fun records(account: Account, filter: String?, collection: String = "messages", sort: String = "-created,-id"): List<JSONObject> {
        val query = ("$base/api/collections/$collection/records").toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort).addQueryParameter("perPage", "50").addQueryParameter("page", "1")
        if (filter != null) query.addQueryParameter("filter", filter)
        val items = request("$collection/records", account.token, url = query.build().toString()).getJSONArray("items")
        return (0 until items.length()).map { items.getJSONObject(it) }
    }
    private fun request(path: String, token: String, payload: JSONObject? = null, url: String = "$base/api/collections/$path", emptyResponse: Boolean = false): JSONObject {
        val builder = Request.Builder().url(url)
        if (token.isNotEmpty()) builder.header("Authorization", token)
        if (payload != null) builder.post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        val call = synchronized(lock) { checkActive(); http.newCall(builder.build()).also { calls.add(it) } }
        return try { call.execute().use { response ->
            updateServerClock(response.header("Date"))
            if (!response.isSuccessful) {
                val delay = response.header("Retry-After")?.toLongOrNull()?.coerceIn(15, 3600) ?: 30L
                val duplicateHandle = path == "members/records" && runCatching {
                    JSONObject(response.body?.string().orEmpty()).optJSONObject("data")?.optJSONObject("handle")
                        ?.optString("code") == "validation_not_unique"
                }.getOrDefault(false)
                val message = when {
                    response.code == 429 -> "Rate limited; retry after $delay seconds."
                    response.code == 401 || response.code == 403 -> "Session expired or account unavailable; sign in again."
                    duplicateHandle -> "Handle is already taken."
                    path == "members/records" && response.code == 400 -> "Registration rejected; check handle and password (at least 8 characters)."
                    else -> "Community request failed (${response.code})."
                }
                throw Failure(response.code, delay, message)
            }
            if (emptyResponse && response.code == 204) JSONObject()
            else JSONObject(response.body?.string() ?: throw IOException("Empty community response."))
        } } finally { synchronized(lock) { calls.remove(call) } }
    }
    /** Blocking foreground-only SSE. Reconnects are owned by the Activity, not hidden retries. */
    fun live(account: Account, connected: () -> Unit, changed: (JSONObject) -> Unit) = operation {
        val call = synchronized(lock) {
            checkActive()
            streamHttp.newCall(Request.Builder().url("$base/api/realtime")
                .header("Authorization", account.token).header("Accept", "text/event-stream").build()).also { calls.add(it) }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw Failure(response.code,
                    response.header("Retry-After")?.toLongOrNull()?.coerceIn(15, 3600) ?: 30L, "Live connection unavailable")
                if (response.header("Content-Type")?.startsWith("text/event-stream") != true) throw IOException("Invalid event stream")
                val source = response.body?.source() ?: throw IOException("Empty event stream")
                var event = ""; val data = StringBuilder(); var subscribed = false
                while (true) {
                    checkActive()
                    val line = source.readUtf8LineStrict(8192)
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) {
                                val payload = JSONObject(data.toString())
                                if (event == "PB_CONNECT") {
                                    val id = payload.getString("clientId")
                                    require(id.matches(Regex("[-_a-zA-Z0-9]{1,100}")))
                                    request("realtime", account.token, JSONObject().put("clientId", id)
                                        .put("subscriptions", org.json.JSONArray(listOf("community"))), "$base/api/realtime", emptyResponse = true)
                                    subscribed = true; connected()
                                } else if (event == "community" && subscribed) changed(payload)
                            }
                            event = ""; data.clear()
                        }
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            if (data.length + line.length > 8192) throw IOException("Event too large")
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").removePrefix(" "))
                        }
                    }
                }
            }
        } finally { synchronized(lock) { calls.remove(call) } }
    }
    fun cancel() { synchronized(lock) { generation++; calls.forEach { it.cancel() } } }
}
