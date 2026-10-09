package ohi.andre.consolelauncher.community

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class CommunityDiscoveryTest {
    @Test fun discoveryUsesDedicatedAuthenticatedEndpointsNotMemberCollection() {
        val calls = mutableListOf<String>(); val bodies = mutableListOf<JSONObject>(); val headers = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            calls.add(e.requestMethod + " " + e.requestURI.toString()); headers.add(e.requestHeaders.getFirst("Authorization"))
            if (e.requestMethod == "POST") bodies.add(JSONObject(e.requestBody.bufferedReader().readText()))
            val bytes = """{"page":1,"perPage":20,"hasMore":false,"items":[{"id":"member000000002","handle":"alice","online":true}],"online":true,"expires_at":"2026-10-09 12:00:00.000Z"}""".toByteArray()
            e.sendResponseHeaders(200, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val account = CommunityClient.Account("member000000001", "bob", "fixture-token")
            val users = CommunityClient::class.java.methods.firstOrNull { it.name == "users" }
            assertNotNull("Public authenticated handle discovery is missing", users)
            users!!.invoke(client, account, "al", 1)
            CommunityClient::class.java.getMethod("presence", CommunityClient.Account::class.java, Boolean::class.javaPrimitiveType).invoke(client, account, true)
            CommunityClient::class.java.getMethod("mentions", CommunityClient.Account::class.java, Int::class.javaPrimitiveType).invoke(client, account, 1)
            assertEquals(listOf("GET /api/community/users?q=al&page=1&perPage=20", "POST /api/community/presence", "GET /api/community/mentions?page=1&perPage=50"), calls)
            assertEquals(setOf("active"), bodies.single().keys().asSequence().toSet())
            assertTrue(bodies.single().getBoolean("active"))
            assertEquals(listOf("fixture-token", "fixture-token", "fixture-token"), headers)
        } finally { server.stop(0) }
    }
}
