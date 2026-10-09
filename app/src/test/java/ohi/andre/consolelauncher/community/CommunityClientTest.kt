package ohi.andre.consolelauncher.community

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class CommunityClientTest {
    @Test fun cancelledPostNeverStartsReconciliation() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls.incrementAndGet(); entered.countDown()
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            val bytes = "{\"items\":[]}".toByteArray()
            runCatching { exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) } }
        }; server.start()
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        val client = CommunityClient("http://127.0.0.1:${server.address.port}")
        try {
            val result = worker.submit<java.io.IOException?> {
                try { client.post(CommunityClient.Account("member000000001", "alice", "fixture-token"), "literal", "00000000-0000-4000-8000-000000000001"); null }
                catch (error: java.io.IOException) { error }
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            client.cancel(); release.countDown()
            assertNotNull(result.get(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("Cancellation must not start a reconciliation GET", 1, calls.get())
        } finally { release.countDown(); worker.shutdownNow(); server.stop(0) }
    }

    @Test fun cancellationBetweenHistoryAndChecksCannotBeResetByReentry() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls.incrementAndGet()
            val bytes = "{\"items\":[]}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
            client.operation {
                client.history(account, null)
                client.cancel()
                // A resumed operation on another worker must not revive the cancelled one.
                val fresh = Thread { client.operation { client.history(account, null) } }
                fresh.start(); fresh.join(5000); assertFalse(fresh.isAlive)
                try { client.checked(account, listOf("message00000001")); fail("Cancelled chain issued a new request") }
                catch (_: CommunityClient.Cancelled) { }
            }
            assertEquals(2, calls.get())
        } finally { server.stop(0) }
    }

    @Test fun interruptedChecksNeverStartAnotherChunk() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls.incrementAndGet()
            val bytes = "{\"items\":[]}".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            Thread.currentThread().interrupt()
            try { CommunityClient("http://127.0.0.1:${server.address.port}").checked(CommunityClient.Account("member000000001", "alice", "fixture-token"), (1..200).map { "m" + it.toString().padStart(14, '0') }); fail("Interrupted operation issued a request") }
            catch (_: CommunityClient.Cancelled) { }
            assertEquals(0, calls.get())
        } finally { Thread.interrupted(); server.stop(0) }
    }

    @Test fun uncertainRetryReconcilesBeforePostingAndRetainsIdentityAfterRejection() {
        var lookupStatus = 200; var postStatus = 429; var posts = 0; var canonical: JSONObject? = null
        val id = "00000000-0000-4000-8000-000000000001"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val posting = exchange.requestMethod == "POST"
            if (posting) posts++
            val bytes = (if (posting) "{}" else JSONObject().put("items", org.json.JSONArray(canonical?.let { listOf(it) } ?: emptyList<JSONObject>())).toString()).toByteArray()
            exchange.sendResponseHeaders(if (posting) postStatus else lookupStatus, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
            for (status in listOf(400, 429, 500, 502, 504)) {
                postStatus = status
                try { client.post(account, "literal", id, true); fail("Retry rejection released prior uncertainty") }
                catch (error: java.io.IOException) { assertTrue(error is CommunityClient.Uncertain) }
            }
            assertEquals(5, posts)
            lookupStatus = 503
            try { client.post(account, "literal", id, true); fail("Failed preflight lookup accepted") }
            catch (error: java.io.IOException) { assertTrue(error is CommunityClient.Uncertain) }
            assertEquals(5, posts)
            canonical = JSONObject().put("id", "message00000001").put("author", account.id).put("client_id", id).put("body", "literal")
            lookupStatus = 200
            assertEquals("message00000001", client.post(account, "literal", id, true).getString("id"))
            assertEquals("Canonical recovery must not POST again", 5, posts)
        } finally { server.stop(0) }
    }

    @Test fun serverFailureWithAbsentLookupIsStillUncertain() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val posting = exchange.requestMethod == "POST"
            val bytes = (if (posting) "{}" else "{\"items\":[]}").toByteArray()
            exchange.sendResponseHeaders(if (posting) 504 else 200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            try { CommunityClient("http://127.0.0.1:${server.address.port}").post(CommunityClient.Account("member000000001", "alice", "fixture-token"), "literal", "00000000-0000-4000-8000-000000000001"); fail("Gateway failure was definitive") }
            catch (error: java.io.IOException) { assertTrue(error is CommunityClient.Uncertain) }
        } finally { server.stop(0) }
    }

    @Test fun committedGatewayFailureReconcilesCanonicalRecord() {
        val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
        val id = "00000000-0000-4000-8000-000000000001"
        var committed: JSONObject? = null
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val posting = exchange.requestMethod == "POST"
            if (posting) committed = JSONObject(exchange.requestBody.bufferedReader().readText()).put("id", "message00000001")
            val bytes = (if (posting) "{}" else JSONObject().put("items", org.json.JSONArray(listOf(committed))).toString()).toByteArray()
            exchange.sendResponseHeaders(if (posting) 502 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            assertEquals("message00000001", CommunityClient("http://127.0.0.1:${server.address.port}").post(account, "literal", id).getString("id"))
        } finally { server.stop(0) }
    }

    @Test fun reconciliationAuthFailureIsNotHiddenAsUncertainOrRetried() {
        var posts = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            if (exchange.requestMethod == "POST") posts++
            val bytes = "{}".toByteArray()
            exchange.sendResponseHeaders(if (exchange.requestMethod == "POST") 400 else 403, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            try { client.post(CommunityClient.Account("member000000001", "alice", "fixture-token"), "literal", "00000000-0000-4000-8000-000000000001"); fail("Revoked lookup accepted") }
            catch (error: java.io.IOException) { assertTrue("Auth failure must reach logout", error is CommunityClient.Failure); assertEquals(403, (error as CommunityClient.Failure).status) }
            assertEquals(1, posts)
        } finally { server.stop(0) }
    }

    @Test fun escapedUnknownSlashBodyPostsLiterally() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/") { exchange ->
            val record = JSONObject(exchange.requestBody.bufferedReader().readText()).put("id","message00000001")
            val bytes = record.toString().toByteArray(); exchange.sendResponseHeaders(200,bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            assertEquals("/unknown",client.post(CommunityClient.Account("member000000001","alice","fixture-token"),"/unknown","00000000-0000-4000-8000-000000000001").getString("body"))
        } finally { server.stop(0) }
    }

    @Test fun authenticationRequiresNonemptyTokenBeforeSavingAnAccount() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = """{"token":"","record":{"id":"member000000001","handle":"alice"}}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            try { CommunityClient("http://127.0.0.1:${server.address.port}").login("alice", "fixture-password"); fail("Empty token accepted") }
            catch (_: java.io.IOException) { }
        } finally { server.stop(0) }
    }

    @Test fun reportFailureIsHonestAndCachedModerationChecksAreBounded() {
        val requests = mutableListOf<JSONObject>()
        val filters = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val reporting = exchange.requestMethod == "POST"
            if (reporting) requests.add(JSONObject(exchange.requestBody.bufferedReader().readText())) else filters.add(exchange.requestURI.query)
            val bytes = (if (reporting) "{}" else "{\"items\":[]}").toByteArray()
            exchange.sendResponseHeaders(if (reporting) 503 else 200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val report = CommunityClient::class.java.methods.firstOrNull { it.name == "report" }
            assertNotNull("Reports are missing", report)
            val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
            try { report!!.invoke(client, account, "message00000001"); fail("Failed report was accepted") }
            catch (e: java.lang.reflect.InvocationTargetException) { assertTrue(e.cause is CommunityClient.Failure) }
            assertEquals(setOf("reporter", "message", "reason"), requests.single().keys().asSequence().toSet())
            assertEquals("Reported from Re-TUI", requests.single().getString("reason"))
            val checked = CommunityClient::class.java.getMethod("checked", CommunityClient.Account::class.java, List::class.java)
            checked.invoke(client, account, (1..200).map { "m" + it.toString().padStart(14, '0') })
            assertEquals(4, filters.size)
            assertTrue(filters.all { it.contains("perPage=50") })
        } finally { server.stop(0) }
    }

    @Test fun postReconcilesOnlyOwnClientIdAfterLostOrDuplicateResponse() {
        val uuid = "00000000-0000-4000-8000-000000000001"
        var author = "member000000001"
        var lookupFails = false
        val bodies = mutableListOf<JSONObject>()
        val filters = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            if (exchange.requestMethod == "POST") {
                bodies.add(JSONObject(exchange.requestBody.bufferedReader().readText()))
                val bytes = "{}".toByteArray(); exchange.sendResponseHeaders(400, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            } else {
                filters.add(exchange.requestURI.query)
                val bytes = """{"items":[{"id":"message00000001","created":"2026-10-08 10:00:00.000Z","author":"$author","client_id":"$uuid","body":"settings; reboot"}]}""".toByteArray()
                exchange.sendResponseHeaders(if (lookupFails) 503 else 200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val method = CommunityClient::class.java.methods.firstOrNull { it.name == "post" && it.parameterCount == 3 }
            assertNotNull("Idempotent posting is missing", method)
            val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
            val message = method!!.invoke(client, account, "settings; reboot", uuid) as JSONObject
            assertEquals("message00000001", message.getString("id"))
            author = "another00000001"
            try { method.invoke(client, account, "settings; reboot", uuid); fail("Wrong author accepted") }
            catch (_: java.lang.reflect.InvocationTargetException) { }
            lookupFails = true
            try { method.invoke(client, account, "settings; reboot", uuid); fail("Failed reconciliation accepted") }
            catch (error: java.lang.reflect.InvocationTargetException) { assertEquals("Uncertain", error.cause!!.javaClass.simpleName) }
            assertEquals(3, bodies.size)
            assertTrue(bodies.all { it.getString("client_id") == uuid && it.keys().asSequence().toSet() == setOf("author", "body", "client_id") })
            assertTrue(filters.all { it.contains("author = 'member000000001' && client_id = '$uuid'") })
        } finally { server.stop(0) }
    }

    @Test fun historyUsesServerCursorAndRawToken() {
        val queries = mutableListOf<String>()
        val auth = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            queries.add(exchange.requestURI.query); auth.add(exchange.requestHeaders.getFirst("Authorization"))
            val bytes = """{"items":[{"id":"zzzzzzzzzzzzzzz","created":"2026-10-08 10:00:00.000Z","author":"member000000001","body":"new"},{"id":"aaaaaaaaaaaaaaa","created":"2026-10-08 09:00:00.000Z","author":"member000000001","body":"old"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val method = CommunityClient::class.java.methods.firstOrNull { it.name == "history" }
            assertNotNull("History pagination is missing", method)
            val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
            @Suppress("UNCHECKED_CAST")
            val messages = method!!.invoke(client, account, null) as List<JSONObject>
            assertEquals(listOf("old", "new"), messages.map { it.getString("body") })
            method.invoke(client, account, messages.first())
            assertEquals(listOf("fixture-token", "fixture-token"), auth)
            assertTrue(queries[0].contains("sort=-created,-id")); assertTrue(queries[0].contains("perPage=50"))
            assertTrue(queries[1].contains("created < '2026-10-08 09:00:00.000Z'"))
            assertTrue(queries[1].contains("id < 'aaaaaaaaaaaaaaa'"))
        } finally { server.stop(0) }
    }

    @Test fun registrationErrorsNeverAuthenticateOrExposeBackendSecrets() {
        var status = 400
        var calls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls++
            val bytes = """{"message":"secret fixture-password","data":{"handle":{"code":"validation_not_unique","message":"secret"}}}""".toByteArray()
            exchange.responseHeaders.add("Retry-After", "30")
            exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }; server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            for (code in listOf(400, 429, 403)) {
                status = code
                try { client.register("alice", "fixture-password", "fixture-password"); fail("Registration error treated as success") }
                catch (e: java.io.IOException) {
                    assertFalse(e.message!!.contains("secret"))
                    if (code == 400) assertEquals("Handle is already taken.", e.message)
                    if (code == 429) assertEquals("Rate limited; retry after 30 seconds.", e.message)
                }
            }
            assertEquals(3, calls)
        } finally { server.stop(0) }
    }

    @Test fun registrationValidatesConfirmationThenAuthenticatesWithoutPrivileges() {
        val paths = mutableListOf<String>()
        val bodies = mutableListOf<JSONObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths.add(exchange.requestURI.path); bodies.add(JSONObject(exchange.requestBody.bufferedReader().readText()))
            val body = if (exchange.requestURI.path.endsWith("records")) """{"id":"member000000001","handle":"alice"}"""
                else """{"token":"fixture-token","record":{"id":"member000000001","handle":"alice"}}"""
            val bytes = body.toByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = CommunityClient("http://127.0.0.1:${server.address.port}")
            val method = CommunityClient::class.java.methods.firstOrNull { it.name == "register" }
            assertNotNull("Public registration is missing", method)
            for ((handle, confirm) in listOf("ab" to "fixture-password", "bad handle" to "fixture-password", "alice" to "different")) {
                try { method!!.invoke(client, handle, "fixture-password", confirm); fail("Invalid registration accepted") }
                catch (error: java.lang.reflect.InvocationTargetException) { assertTrue(error.cause is IllegalArgumentException) }
            }
            assertTrue(paths.isEmpty())
            val account = method!!.invoke(client, "Alice", "fixture-password", "fixture-password") as CommunityClient.Account
            assertEquals("alice", bodies[1].getString("identity"))
            assertEquals("fixture-token", account.token)
            assertEquals(listOf("/api/collections/members/records", "/api/collections/members/auth-with-password"), paths)
            assertEquals(setOf("handle", "password", "passwordConfirm"), bodies[0].keys().asSequence().toSet())
        } finally { server.stop(0) }
    }

    @Test fun nativePasswordAuthUsesOnlyIdentityAndPassword() {
        val requests = mutableListOf<JSONObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/collections/members/auth-with-password") { exchange ->
            requests.add(JSONObject(exchange.requestBody.bufferedReader().readText()))
            val bytes = """{"token":"fixture-token","record":{"id":"member000000001","handle":"alice"}}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val type = try { Class.forName("ohi.andre.consolelauncher.community.CommunityClient") } catch (_: ClassNotFoundException) { null }
            assertNotNull("Native PocketBase client is missing", type)
            val client = type!!.getConstructor(String::class.java).newInstance("http://127.0.0.1:${server.address.port}")
            val account = type.getMethod("login", String::class.java, String::class.java).invoke(client, "alice", "fixture-password")
            assertEquals("alice", account.javaClass.getMethod("getHandle").invoke(account))
            assertEquals(setOf("identity", "password"), requests.single().keys().asSequence().toSet())
            assertEquals("alice", requests.single().getString("identity"))
        } finally { server.stop(0) }
    }
}
