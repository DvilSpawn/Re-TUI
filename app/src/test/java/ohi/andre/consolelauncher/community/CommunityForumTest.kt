package ohi.andre.consolelauncher.community

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class CommunityForumTest {
    @Test fun malformedCanonicalThreadNeverClaimsDelivery() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val uuid = "00000000-0000-4000-8000-000000000001"
        val payload = JSONObject().put("author", "member000000001").put("channel", "bugs").put("title", "title").put("body", "body").put("client_id", uuid)
        server.createContext("/") { e ->
            val bytes = (if (e.requestMethod == "POST") payload else JSONObject().put("items", org.json.JSONArray(listOf(payload)))).toString().toByteArray()
            e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            try { client.postThread(CommunityClient.Account("member000000001", "alice", "token"), "bugs", "title", "body", uuid, false); fail("Acknowledgement without native ID accepted") }
            catch (_: CommunityClient.Uncertain) {}
        } finally { server.stop(0) }
    }
    @Test fun threadAndReplyReconcileExactContextBeforeRetry() {
        val calls = mutableListOf<String>(); val payloads = mutableListOf<JSONObject>()
        val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
        val uuid = "00000000-0000-4000-8000-000000000001"
        var canonical: JSONObject? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            calls.add(e.requestMethod + " " + e.requestURI.path + "?" + e.requestURI.query)
            val posting = e.requestMethod == "POST"
            if (posting) { canonical = JSONObject(e.requestBody.bufferedReader().readText()).put("id", "thread000000001"); payloads.add(canonical!!) }
            val bytes = (if (posting) "{}" else JSONObject().put("items", org.json.JSONArray(listOfNotNull(canonical))).toString()).toByteArray()
            e.sendResponseHeaders(if (posting) 502 else 200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val method = CommunityClient::class.java.methods.firstOrNull { it.name == "postThread" }
            assertNotNull("Persistent thread posting is missing", method)
            val record = method!!.invoke(client, account, "features", "literal title", "settings; reboot", uuid, false) as JSONObject
            assertEquals("features", record.getString("channel"))
            assertEquals(setOf("author", "channel", "title", "body", "client_id", "id"), payloads.single().keys().asSequence().toSet())
            method.invoke(client, account, "features", "literal title", "settings; reboot", uuid, true)
            assertEquals(1, payloads.size)
            try { method.invoke(client, account, "bugs", "literal title", "settings; reboot", uuid, true); fail("Changed context accepted under old UUID") }
            catch (e: java.lang.reflect.InvocationTargetException) { assertTrue(e.cause is CommunityClient.Uncertain) }
            canonical = null
            val reply = CommunityClient::class.java.methods.first { it.name == "postReply" }
            reply.invoke(client, account, "thread000000001", "reply", uuid, false)
            assertEquals(setOf("author", "thread", "body", "client_id", "id"), payloads.last().keys().asSequence().toSet())
            assertTrue(calls.any { it.contains("/threads/records?sort=-created,-id") && it.contains("author") && it.contains("client_id") })
            assertTrue(calls.none { it.contains("expand") })
        } finally { server.stop(0) }
    }
}
