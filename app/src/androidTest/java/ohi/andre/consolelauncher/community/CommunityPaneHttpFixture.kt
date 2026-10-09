package ohi.andre.consolelauncher.community

import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Concurrent loopback fixture: a held delivery must not serialize presence behind it. */
internal class CommunityPaneHttpFixture : AutoCloseable {
    data class Request(val path: String, val method: String, val query: String, val body: JSONObject)
    val requests = CopyOnWriteArrayList<Request>()
    val messages = CopyOnWriteArrayList<JSONObject>()
    val threads = CopyOnWriteArrayList<JSONObject>()
    val replies = CopyOnWriteArrayList<JSONObject>()
    val mentions = CopyOnWriteArrayList<JSONObject>()
    val failures = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile var postGate: CountDownLatch? = null
    @Volatile var lookupGate: CountDownLatch? = null
    private val socket = ServerSocket(0, 20, java.net.InetAddress.getByName("127.0.0.1"))
    private val clients = CopyOnWriteArrayList<Socket>()
    private val pool = Executors.newCachedThreadPool()
    val url = "http://127.0.0.1:${socket.localPort}"
    private val accept = thread(name = "community-pane-fixture") {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (_: Exception) { break }
            clients.add(client)
            pool.submit { try { client.use { serve(it) } } catch (_: Exception) {} finally { clients.remove(client) } }
        }
    }
    private fun serve(socket: Socket) {
        socket.soTimeout = 5000
        val input = socket.getInputStream()
        fun line(): String {
            val bytes = java.io.ByteArrayOutputStream()
            while (true) { val b = input.read(); if (b < 0 || b == 10) break; if (b != 13) bytes.write(b) }
            return bytes.toString("UTF-8")
        }
        val first = line().split(" "); if (first.size < 2) return
        val headers = mutableMapOf<String, String>()
        while (true) { val next = line(); if (next.isEmpty()) break; val pair = next.split(":", limit = 2); if (pair.size == 2) headers[pair[0].lowercase()] = pair[1].trim() }
        val bytes = ByteArray(headers["content-length"]?.toInt() ?: 0); var offset = 0
        while (offset < bytes.size) { val read = input.read(bytes, offset, bytes.size - offset); if (read < 0) break; offset += read }
        val body = if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
        val path = first[1].substringBefore('?'); val query = java.net.URLDecoder.decode(first[1], "UTF-8")
        requests.add(Request(path, first[0], query, body))
        val collection = when { path.contains("thread_replies") -> replies; path.contains("/threads/") -> threads; else -> messages }
        var code = failures[path] ?: 200
        val result = when {
            code != 200 -> JSONObject()
            path.endsWith("/presence") -> JSONObject().put("online", body.optBoolean("active")).put("expires_at", CommunityHttpFixture.created(System.currentTimeMillis() + 90000))
            path.endsWith("/users") -> {
                lookupGate?.await(5, TimeUnit.SECONDS)
                JSONObject().put("page", 1).put("perPage", 20).put("hasMore", false).put("items", JSONArray(listOf(JSONObject().put("id", "member000000001").put("handle", "alice").put("online", true))))
            }
            path.endsWith("/mentions") -> {
                val page = Regex("[?&]page=(\\d+)").find(query)?.groupValues?.get(1)?.toInt() ?: 1
                JSONObject().put("page", page).put("perPage", 50).put("hasMore", mentions.size > page * 50)
                    .put("items", JSONArray(mentions.drop((page - 1) * 50).take(50)))
            }
            path.endsWith("moderator_requests/records") -> body.put("id", "private00000001")
            first[0] == "POST" -> {
                val prior = collection.firstOrNull { it.optString("author") == body.optString("author") && it.optString("client_id") == body.optString("client_id") }
                if (prior != null) { code = 400; JSONObject() }
                else body.put("id", "t" + (collection.size + 1).toString().padStart(14, '0')).put("created", CommunityHttpFixture.created())
                    .put("author_handle", "alice").also { collection.add(it); postGate?.await(5, TimeUnit.SECONDS) }
            }
            Regex(".*/records/[a-zA-Z0-9]{15}").matches(path) -> collection.firstOrNull { it.optString("id") == path.substringAfterLast('/') } ?: JSONObject().also { code = 404 }
            else -> {
                val items = collection.filter { row ->
                    when {
                        query.contains("client_id") -> query.contains(row.optString("client_id")) && query.contains(row.optString("author"))
                        query.contains("id =") -> query.contains(row.optString("id"))
                        path.contains("thread_replies") -> query.contains(row.optString("thread"))
                        else -> query.contains(row.optString("channel"))
                    }
                }.filter { row ->
                    val after = Regex("id > '([^']+)'").find(query)?.groupValues?.get(1)
                    val before = Regex("id < '([^']+)'").find(query)?.groupValues?.get(1)
                    (after == null || row.getString("id") > after) && (before == null || row.getString("id") < before)
                }.sortedWith(compareBy<JSONObject>({ it.optString("created") }, { it.getString("id") })).let {
                    if (query.contains("sort=-created,-id")) it.reversed() else it
                }.take(50)
                JSONObject().put("page", 1).put("perPage", 50).put("items", JSONArray(items))
            }
        }
        val output = result.toString().toByteArray(Charsets.UTF_8)
        socket.getOutputStream().apply {
            write("HTTP/1.1 $code Fixture\r\nContent-Type: application/json\r\nContent-Length: ${output.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(output); flush()
        }
    }
    override fun close() { postGate?.countDown(); lookupGate?.countDown(); socket.close(); clients.forEach { runCatching { it.close() } }; pool.shutdownNow(); accept.join(5000) }
}
