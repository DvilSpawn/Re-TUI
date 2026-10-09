package ohi.andre.consolelauncher.community

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class CommunityForumReadTest {
    @Test fun persistentCachesRevalidateOnTheirOwnCollectionsInBoundedChunks() {
        val paths = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            paths.add(e.requestURI.path + "?" + e.requestURI.query)
            val bytes = "{\"items\":[]}".toByteArray(); e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val method = CommunityClient::class.java.methods.firstOrNull { it.name == "checked" && it.parameterCount == 3 }
            assertNotNull("Persistent cache validation is missing", method)
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            method!!.invoke(client, CommunityClient.Account("member000000001", "alice", "token"), (1..250).map { "t" + it.toString().padStart(14, '0') }, "threads")
            assertEquals(4, paths.size)
            assertTrue(paths.all { it.startsWith("/api/collections/threads/records?") && it.contains("perPage=50") })
        } finally { server.stop(0) }
    }
    @Test fun forumReadsUseExplicitFiltersAndDirectionalCursorsWithoutExpansion() {
        val paths = mutableListOf<String>(); val bodies = mutableListOf<JSONObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            paths.add(e.requestMethod + " " + e.requestURI.path + "?" + e.requestURI.query)
            if (e.requestMethod == "POST") bodies.add(JSONObject(e.requestBody.bufferedReader().readText()))
            val bytes = "{\"items\":[]}".toByteArray(); e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
            val type = CommunityClient::class.java
            val list = type.methods.firstOrNull { it.name == "threads" }
            assertNotNull("Thread listing is missing", list)
            val cursor = JSONObject().put("id", "thread000000001").put("created", "2026-10-09 10:00:00.000Z")
            list!!.invoke(client, account, "bugs", cursor)
            type.getMethod("replies", CommunityClient.Account::class.java, String::class.java, JSONObject::class.java).invoke(client, account, "thread000000001", cursor)
            type.getMethod("source", CommunityClient.Account::class.java, String::class.java, String::class.java).invoke(client, account, "messages", "message00000001")
            type.getMethod("moderate", CommunityClient.Account::class.java, String::class.java).invoke(client, account, "private text")
            assertTrue(paths[0].contains("channel = 'bugs'") && paths[0].contains("created <") && paths[0].contains("sort=-created,-id"))
            assertTrue(paths[1].contains("thread = 'thread000000001'") && paths[1].contains("created >") && paths[1].contains("sort=created,id"))
            assertTrue(paths[2].contains("/messages/records/message00000001"))
            assertTrue(paths[3].startsWith("POST /api/collections/moderator_requests/records"))
            assertEquals(setOf("reporter", "kind", "body"), bodies.single().keys().asSequence().toSet())
            assertTrue(paths.none { it.contains("expand") })
        } finally { server.stop(0) }
    }
}
