package ohi.andre.consolelauncher.community

import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Loopback fixture exercises real HTTP; it is never shipped in the app APK. */
internal class CommunityHttpFixture : AutoCloseable {
    companion object {
        private val historyTime = System.currentTimeMillis() - 3_600_000L
        fun created(time: Long = historyTime): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(time))
    }
    data class Request(val path: String, val method: String, val query: String)
    val requests = CopyOnWriteArrayList<Request>()
    val posts = CopyOnWriteArrayList<JSONObject>()
    private val socket = ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${socket.localPort}"
    @Volatile var status = 200
    @Volatile var lookupStatus = 200
    @Volatile var dropPostResponse = false
    @Volatile var postResponseStatus = 200
    @Volatile var postResponseGate: java.util.concurrent.CountDownLatch? = null
    @Volatile var getResponseGate: java.util.concurrent.CountDownLatch? = null
    @Volatile var gateGetNumber = -1
    var getCount = java.util.concurrent.atomic.AtomicInteger()
    private val worker = thread(name = "community-http-fixture") {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (_: Exception) { break }
            try { client.use { serve(it) } } catch (_: Exception) { }
        }
    }
    private fun serve(client: Socket) {
        client.soTimeout = 5000
        val input = client.getInputStream()
        fun line(): String {
            val bytes = java.io.ByteArrayOutputStream()
            while (true) {
                val next = input.read(); if (next == -1 || next == 10) break
                if (next != 13) bytes.write(next)
            }
            return bytes.toString("UTF-8")
        }
        val start = line().split(" "); if (start.size < 2) return
        val headers = mutableMapOf<String, String>()
        while (true) { val value = line(); if (value.isEmpty()) break; val pair = value.split(":", limit=2); if (pair.size==2) headers[pair[0].lowercase()] = pair[1].trim() }
        val length = headers["content-length"]?.toInt() ?: 0
        val bytes = ByteArray(length); var offset = 0
        while (offset < length) { val read = input.read(bytes, offset, length-offset); if (read < 0) break; offset += read }
        val body = if (length > 0) JSONObject(String(bytes, Charsets.UTF_8)) else JSONObject()
        val path = start[1].substringBefore("?"); requests.add(Request(path, start[0], java.net.URLDecoder.decode(start[1], "UTF-8")))
        if (start[0] == "GET" && getCount.incrementAndGet() == gateGetNumber)
            getResponseGate?.await(10, java.util.concurrent.TimeUnit.SECONDS)
        var code = if (start[0] == "GET" && status == 200) lookupStatus else status
        val result = when {
            code != 200 -> JSONObject().put("message", "Fixture rejection")
            path.endsWith("auth-with-password") -> JSONObject().put("token", "fixture-token").put("record", JSONObject().put("id", "member000000001").put("handle", body.getString("identity")))
            path.endsWith("members/records") -> JSONObject().put("id", "member000000001").put("handle", body.getString("handle"))
            path.endsWith("reports/records") -> JSONObject().put("id", "report000000001")
            start[0] == "POST" -> {
                val prior = posts.firstOrNull { it.optString("client_id") == body.getString("client_id") }
                if (prior != null) { code = 400; JSONObject() }
                else body.put("id", "m"+(posts.size+1).toString().padStart(14,'0'))
                    .put("created", created(System.currentTimeMillis()))
                    .put("updated", created(System.currentTimeMillis())).put("author_handle", "alice").also { posts.add(it) }
            }
            else -> {
                val query = java.net.URLDecoder.decode(start[1], "UTF-8")
                val items = when {
                    query.contains("client_id") -> posts.filter { query.contains(it.getString("client_id")) && query.contains(it.getString("author")) }
                    query.contains("filter=") && query.contains("id =") -> posts.filter { query.contains(it.getString("id")) }
                    query.contains("created <") -> {
                        val created = Regex("created < '([^']+)'").find(query)!!.groupValues[1]
                        val id = Regex("id < '([^']+)'").find(query)!!.groupValues[1]
                        posts.filter { it.getString("created") < created || (it.getString("created") == created && it.getString("id") < id) }
                            .sortedWith(compareBy({ it.getString("created") }, { it.getString("id") })).takeLast(50).reversed()
                    }
                    else -> posts.toList().takeLast(50).reversed()
                }
                JSONObject().put("items", JSONArray(items)).put("page",1).put("perPage",50).put("totalItems",items.size).put("totalPages",1)
            }
        }
        if (start[0] == "POST" && path.endsWith("messages/records")) postResponseGate?.await(10, java.util.concurrent.TimeUnit.SECONDS)
        if (dropPostResponse && start[0] == "POST" && path.endsWith("messages/records")) return
        if (start[0] == "POST" && path.endsWith("messages/records") && code == 200) code = postResponseStatus
        val output = result.toString().toByteArray(Charsets.UTF_8)
        client.getOutputStream().apply {
            write("HTTP/1.1 $code Fixture\r\nContent-Type: application/json\r\nContent-Length: ${output.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(output); flush()
        }
    }
    override fun close() { socket.close(); worker.join(6000) }
}
