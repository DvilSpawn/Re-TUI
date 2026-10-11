package ohi.andre.consolelauncher.community

import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class CommunityReviewedClientTest {
    private val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
    private fun mention(n: Int) = JSONObject().put("id", "m" + n.toString().padStart(14, '0'))
        .put("source_id", "t" + n.toString().padStart(14, '0')).put("kind", "thread").put("channel", "bugs")
    private fun serve(handler: (String, String) -> Pair<Int, JSONObject>, test: (CommunityClient) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val (status, response) = handler(exchange.requestURI.path, exchange.requestURI.query.orEmpty())
            val bytes = response.toString().toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { test(CommunityClient("http://127.0.0.1:${server.address.port}")) } finally { server.stop(0) }
    }
    @Test fun latestReplyTailBeyondFiftySeedsAForwardCursor() {
        val rows = (1..250).map { n -> JSONObject().put("id", "r" + n.toString().padStart(14, '0'))
            .put("created", "2026-10-09 00:00:00.000Z").put("thread", "thread000000001") }.toMutableList()
        serve({ _, query ->
            val after = Regex("id > '([^']+)'").find(query)?.groupValues?.get(1)
            val ordered = if (query.contains("sort=-created,-id")) rows.reversed() else rows
            200 to JSONObject().put("items", JSONArray(ordered.filter { after == null || it.getString("id") > after }.take(50)))
        }) { client ->
            val latest = client.replies(account, "thread000000001", rows[49], latest = true)
            assertEquals(rows.takeLast(50).map { it.getString("id") }, latest.map { it.getString("id") })
            rows.add(JSONObject(rows.last().toString()).put("id", "r00000000000251"))
            assertEquals(listOf("r00000000000251"), client.replies(account, "thread000000001", latest.last()).map { it.getString("id") })
        }
    }
    @Test fun olderRepliesPageBackwardsWithoutLosingTheForwardOrLatestPath() {
        val rows = (1..250).map { n -> JSONObject().put("id", "r" + n.toString().padStart(14, '0'))
            .put("created", "2026-10-09 00:00:00.000Z").put("thread", "thread000000001") }
        serve({ _, query ->
            assertTrue(query.contains("thread = 'thread000000001'"))
            val before = Regex("id < '([^']+)'").find(query)?.groupValues?.get(1)
            val after = Regex("id > '([^']+)'").find(query)?.groupValues?.get(1)
            val ordered = if (query.contains("sort=-created,-id")) rows.reversed() else rows
            200 to JSONObject().put("items", JSONArray(ordered.filter {
                (before == null || it.getString("id") < before) && (after == null || it.getString("id") > after)
            }.take(50)))
        }) { client ->
            val tail = client.replies(account, "thread000000001", null, latest = true)
            val older = client.replies(account, "thread000000001", tail.first(), older = true)
            assertEquals(rows.subList(150, 200).map { it.getString("id") }, older.map { it.getString("id") })
            assertEquals(tail.map { it.getString("id") }, client.replies(account, "thread000000001", older.last()).map { it.getString("id") })
        }
    }
    @Test fun mentionWindowKeepsOlderUnreadPagesAndRejectsPartialErrorResults() {
        val rows = (1..100).map { mention(it) }
        var failing = false
        serve({ _, query ->
            val page = Regex("page=(\\d+)").find(query)!!.groupValues[1].toInt()
            (if (failing && page == 2) 400 else 200) to JSONObject().put("items", JSONArray(rows.drop((page - 1) * 50).take(50))).put("hasMore", page == 1)
        }) { client ->
            val state = CommunityMentionState(JSONObject()); state.replace(rows)
            rows.take(50).forEach { state.read(it.getString("id")) }
            val response = client.mentionWindow(account, state.items)
            val items = response.getJSONArray("items")
            state.replace((0 until items.length()).map { items.getJSONObject(it) }, keepUnread = true)
            assertEquals(100, state.items.size); assertEquals(50, state.unread("bugs"))
            failing = true
            try { client.mentionWindow(account, state.items); fail("Partial pagination was accepted as authoritative") }
            catch (error: CommunityClient.Failure) { assertEquals(400, error.status) }
            assertEquals(50, state.unread("bugs"))
        }
    }
    @Test fun boundedWindowRevalidatesMissingSourcesAndPrioritizesUnreadMetadata() {
        val rows = (1..200).map { mention(it) }
        val old = mention(201); val deleted = mention(202)
        var checks = 0
        serve({ path, query ->
            if (path.contains("/collections/")) {
                checks++
                200 to JSONObject().put("items", JSONArray(listOf(JSONObject().put("id", old.getString("source_id")))))
            } else {
                val page = Regex("page=(\\d+)").find(query)!!.groupValues[1].toInt()
                200 to JSONObject().put("items", JSONArray(rows.drop((page - 1) * 50).take(50))).put("hasMore", true)
            }
        }) { client ->
            val state = CommunityMentionState(JSONObject()); state.replace(rows); rows.forEach { state.read(it.getString("id")) }
            state.replace(listOf(old, deleted))
            val response = client.mentionWindow(account, state.items); val items = response.getJSONArray("items")
            state.replace((0 until items.length()).map { items.getJSONObject(it) }, keepUnread = true)
            assertEquals(4, response.getInt("page")); assertTrue(response.getBoolean("hasMore")); assertEquals(1, checks)
            assertEquals(200, state.items.size); assertEquals(1, state.unread("bugs"))
            assertTrue(state.items.any { it.getString("id") == old.getString("id") })
            assertFalse(state.items.any { it.getString("id") == deleted.getString("id") })
            assertFalse(state.toJson().toString().contains("body"))
        }
    }
}
