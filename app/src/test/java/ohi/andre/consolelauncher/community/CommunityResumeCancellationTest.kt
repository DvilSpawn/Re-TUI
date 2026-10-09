package ohi.andre.consolelauncher.community

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Real loopback transport probe, not proof of Android resume or production HTTPS. */
class CommunityResumeCancellationTest {
    @Test fun reusedPresenceWorkerStartsANewEpochWhileIndependentDirectoryRemainsUsable() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val serving = Executors.newCachedThreadPool()
        val worker = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
        val requests = java.util.concurrent.atomic.AtomicInteger()
        server.executor = serving
        server.createContext("/") { exchange ->
            try {
                val presence = exchange.requestURI.path.endsWith("/presence")
                if (presence && requests.incrementAndGet() == 1) { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
                val bytes = (if (presence) "{\"active\":true}" else "{\"items\":[],\"hasMore\":false}").toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            } catch (_: java.io.IOException) { /* Cancellation can close the first response socket. */ }
            finally { exchange.close() }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val presence = CommunityClient(base); val directory = CommunityClient(base)
            val account = CommunityClient.Account("member000000001", "alice", "fixture-token")
            var firstThread = -1L
            val first = worker.submit {
                firstThread = Thread.currentThread().id
                try { presence.operation { presence.presence(account, true) } }
                catch (_: java.io.IOException) { }
                finally { finished.countDown() }
            }
            assertTrue("First operation reached real transport", entered.await(5, TimeUnit.SECONDS))
            first.cancel(true); presence.cancel()
            assertEquals(0, directory.users(account, "alice", 1).getJSONArray("items").length())
            assertTrue("Cancelled operation exited", finished.await(5, TimeUnit.SECONDS))
            release.countDown()
            val resumed = worker.submit<JSONObject> {
                assertEquals("Same executor thread is reused", firstThread, Thread.currentThread().id)
                assertFalse("Executor clears the prior task interrupt", Thread.currentThread().isInterrupted)
                presence.operation { presence.presence(account, true) }
            }.get(5, TimeUnit.SECONDS)
            assertTrue(resumed.getBoolean("active")); assertEquals(2, requests.get())
            assertEquals(0, directory.users(account, "alice", 1).getJSONArray("items").length())
        } finally {
            release.countDown(); worker.shutdownNow(); server.stop(0); serving.shutdownNow()
        }
    }
}
