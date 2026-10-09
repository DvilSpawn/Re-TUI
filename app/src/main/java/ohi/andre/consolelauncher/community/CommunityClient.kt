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
    @JvmOverloads fun post(account: Account, body: String, clientId: String, uncertainAttempt: Boolean = false): JSONObject = operation {
        require(CommunityInput.message(body) is CommunityInput.Message)
        require(java.util.UUID.fromString(clientId).toString() == clientId)
        fun reconcile(): JSONObject? {
            val match = try { find(account, clientId) } catch (lookup: Exception) {
                if (lookup is Failure && lookup.status in setOf(401, 403)) throw lookup
                throw Uncertain()
            }
            if (match != null && match.optString("body") != body) throw Uncertain()
            return match
        }
        // A retained attempt may have committed, including after lifecycle cancellation.
        if (uncertainAttempt) reconcile()?.let { return@operation it }
        try {
            val record = request("messages/records", account.token, JSONObject().put("author", account.id)
                .put("body", body).put("client_id", clientId))
            if (record.optString("author") != account.id || record.optString("client_id") != clientId || record.optString("body") != body)
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
    fun report(account: Account, messageId: String) {
        require(messageId.matches(Regex("[a-zA-Z0-9]{15}")))
        request("reports/records", account.token, JSONObject().put("reporter", account.id)
            .put("message", messageId).put("reason", "Reported from Re-TUI"))
    }
    fun checked(account: Account, ids: List<String>): List<JSONObject> = operation {
        ids.take(200).chunked(50).flatMap { batch -> records(account, batch.joinToString(" || ") { "id = ${quote(it)}" }) }
    }
    private fun quote(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
    fun history(account: Account, older: JSONObject?): List<JSONObject> {
        val filter = older?.let {
            val created = quote(it.getString("created")); val id = quote(it.getString("id"))
            "created < $created || (created = $created && id < $id)"
        }
        return records(account, filter).sortedWith(compareBy({ it.getString("created") }, { it.getString("id") }))
    }
    private fun records(account: Account, filter: String?): List<JSONObject> {
        val query = ("$base/api/collections/messages/records").toHttpUrl().newBuilder()
            .addQueryParameter("sort", "-created,-id").addQueryParameter("perPage", "50").addQueryParameter("page", "1")
        if (filter != null) query.addQueryParameter("filter", filter)
        val items = request("messages/records", account.token, url = query.build().toString()).getJSONArray("items")
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
