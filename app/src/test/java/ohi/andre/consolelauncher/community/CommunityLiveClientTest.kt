package ohi.andre.consolelauncher.community

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CommunityLiveClientTest {
    @Test fun trustedServerClockKeepsNewMessagesDespiteDeviceSkewWithoutRelaxingExpiry() {
        val client = CommunityClient()
        val server = System.currentTimeMillis() + 5000L
        val date = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("GMT")
        }.format(java.util.Date(server))
        client.updateServerClock(date)
        val state = CommunityState(clock = client::now)
        fun record(id: String, created: Long) = JSONObject().put("id", id).put("created",
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'",java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(created)))
        state.merge(listOf(record("new", server), record("expired", server - 86_400_000L)), emptySet())
        assertEquals(listOf("new"), state.messages.map { it.getString("id") })
        val before = client.now(); client.updateServerClock("invalid")
        assertTrue(client.now() >= before)
    }
    @Test fun subscribesWithSameIdentityReceivesPointersAndCancelsAnIdleStream() {
        val pool = Executors.newCachedThreadPool()
        val subscribed = CountDownLatch(1); val received = CountDownLatch(1)
        val released = CountDownLatch(1); val connected = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { executor = pool }
        server.createContext("/api/realtime") { exchange ->
            assertEquals("fixture-token", exchange.requestHeaders.getFirst("Authorization"))
            if (exchange.requestMethod == "POST") {
                val body = JSONObject(exchange.requestBody.bufferedReader().readText())
                assertEquals("fixtureclient", body.getString("clientId"))
                assertEquals("[\"community\"]", body.getJSONArray("subscriptions").toString())
                exchange.sendResponseHeaders(204, -1); exchange.close(); subscribed.countDown()
            } else {
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                runCatching {
                    exchange.responseBody.use { output ->
                        output.write("event: PB_CONNECT\ndata: {\"clientId\":\"fixtureclient\"}\n\n".toByteArray()); output.flush()
                        assertTrue(subscribed.await(5, TimeUnit.SECONDS))
                        output.write("event: community\ndata: {\"action\":\"create\",\"collection\":\"messages\",\"id\":\"message00000001\"}\n\n".toByteArray()); output.flush()
                        released.await(5, TimeUnit.SECONDS)
                    }
                }
            }
        }
        server.start()
        val client = CommunityClient("http://127.0.0.1:${server.address.port}")
        try {
            val result = pool.submit {
                runCatching { client.live(CommunityClient.Account("member000000001", "alice", "fixture-token"),
                    connected = { connected.countDown() }, changed = {
                        assertEquals(setOf("action", "collection", "id"), it.keys().asSequence().toSet()); received.countDown()
                    }) }
            }
            assertTrue(connected.await(5, TimeUnit.SECONDS)); assertTrue(received.await(5, TimeUnit.SECONDS))
            client.cancel(); result.get(5, TimeUnit.SECONDS)
        } finally { client.cancel(); released.countDown(); server.stop(0); pool.shutdownNow() }
    }
    @Test fun conversationHeadersNeverRenderTransportIdentity() {
        val record = JSONObject().put("id", "backend_record_id").put("author", "backend_username")
            .put("author_handle", "alice").put("ip", "198.51.100.1").put("created", "2026-10-10 01:02:03.000Z")
        val header = CommunityMessagePresentation.header(record)
        assertTrue(header.startsWith("@alice · "))
        assertFalse(header.contains("backend")); assertFalse(header.contains("198.51")); assertFalse(header.contains(".000Z"))
        assertEquals("@?", CommunityMessagePresentation.header(JSONObject().put("author_handle", "alice!user@198.51.100.1")))
    }
}
