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
    companion object { const val PRODUCTION = "https://irc.kokodoko.cloud/community" }
    class Failure(val status: Int, val retrySeconds: Long, message: String) : IOException(message)
    class Cancelled : IOException("Community operation cancelled; delivery may be uncertain.")
    data class Account(val id: String, val handle: String, val token: String)
    private val lock = Any()
    private var generation = 0L
    private val epoch = ThreadLocal<Long>()
    private val calls = mutableSetOf<okhttp3.Call>()
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
        return Account(record.getString("id"), record.getString("handle"), token)
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
            if (error is Failure && error.status in setOf(401, 403)) throw error
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
    @JvmOverloads fun replies(account: Account, thread: String, after: JSONObject?, latest: Boolean = false): List<JSONObject> {
        require(thread.matches(Regex("[a-zA-Z0-9]{15}")))
        val filter = "thread = ${quote(thread)}" + (after?.takeUnless { latest }?.let { " && (${cursor(it, ">")})" } ?: "")
        return records(account, filter, "thread_replies", if (latest) "-created,-id" else "created,id")
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
    private fun request(path: String, token: String, payload: JSONObject? = null, url: String = "$base/api/collections/$path"): JSONObject {
        val builder = Request.Builder().url(url)
        if (token.isNotEmpty()) builder.header("Authorization", token)
        if (payload != null) builder.post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        val call = synchronized(lock) { checkActive(); http.newCall(builder.build()).also { calls.add(it) } }
        return try { call.execute().use { response ->
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
            JSONObject(response.body?.string() ?: throw IOException("Empty community response."))
        } } finally { synchronized(lock) { calls.remove(call) } }
    }
    fun cancel() { synchronized(lock) { generation++; calls.forEach { it.cancel() } } }
}
