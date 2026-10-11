package ohi.andre.consolelauncher.irc

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class IrcClientTest {
    @Test fun externalAuthenticatesWithAccountOnlyAndNoPasswordFallback() {
        ServerSocket(0).use { server ->
            val frames = CopyOnWriteArrayList<String>(); val ready = CountDownLatch(1)
            val fixture = thread {
                server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    fun reply(line: String) { writer.write(line + "\r\n"); writer.flush() }
                    repeat(3) { frames.add(reader.readLine()) }; reply(":s CAP test LS :sasl=PLAIN,EXTERNAL echo-message")
                    frames.add(reader.readLine()); reply(":s CAP test ACK :sasl echo-message")
                    frames.add(reader.readLine()); reply("AUTHENTICATE +")
                    frames.add(reader.readLine()); reply(":s 903 test :Success")
                    frames.add(reader.readLine()); reply(":s 001 test :Welcome")
                    while (reader.readLine() != null) { }
                }
            }
            val c = IrcClient("localhost", server.localPort, "test", "test", receive = {}, connection = { ok, _ -> if (ok) ready.countDown() }, connector = { _, port -> Socket("127.0.0.1", port) }, external = true)
            try { c.start(); assertTrue(ready.await(5, TimeUnit.SECONDS)); assertTrue(frames.contains("AUTHENTICATE EXTERNAL")); assertFalse(frames.contains("AUTHENTICATE PLAIN")); assertTrue(frames.contains("AUTHENTICATE dGVzdA==")) }
            finally { c.close(); fixture.join(5000) }
        }
    }
    @Test fun externalRejectsPasswordOnlyNetworkWithoutDowngrade() {
        ServerSocket(0).use { server ->
            val stopped = CountDownLatch(1); val frames = CopyOnWriteArrayList<String>()
            val fixture = thread {
                server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    repeat(3) { frames.add(reader.readLine()) }; writer.write(":s CAP test LS :sasl=PLAIN\r\n"); writer.flush()
                    while (true) { val line = reader.readLine() ?: break; frames.add(line) }
                }
            }
            val c = IrcClient("localhost", server.localPort, "test", "test", receive = {}, connection = { _, text -> if (text.startsWith("Disconnected")) stopped.countDown() }, connector = { _, port -> Socket("127.0.0.1", port) }, external = true)
            try { c.start(); assertTrue(stopped.await(5, TimeUnit.SECONDS)); assertFalse(c.ready); assertFalse(frames.any { it.startsWith("AUTHENTICATE") || it == "CAP END" || it.startsWith("JOIN") }) }
            finally { c.close(); fixture.join(5000) }
        }
    }
    @Test fun negotiatesSaslBeforeWelcomeAnswersPingAndNeverExposesSecrets() {
        ServerSocket(0).use { server ->
            val frames = CopyOnWriteArrayList<String>(); val incoming = CopyOnWriteArrayList<IrcMessage>()
            val ready = CountDownLatch(1); val pong = CountDownLatch(1)
            val fixture = thread {
                server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    fun send(line: String) { writer.write(line + "\r\n"); writer.flush() }
                    repeat(3) { frames.add(reader.readLine()) }
                    send(":s CAP test LS * :server-time multi-prefix")
                    send(":s CAP test LS :sasl=PLAIN echo-message")
                    frames.add(reader.readLine()); send(":s CAP test ACK :server-time multi-prefix sasl echo-message")
                    frames.add(reader.readLine()); send("AUTHENTICATE +")
                    frames.add(reader.readLine()); send(":s 903 test :Authentication successful")
                    frames.add(reader.readLine()); send(":s 001 test :Welcome")
                    send("PING :proof"); frames.add(reader.readLine()); pong.countDown()
                    while (reader.readLine() != null) { }
                }
            }
            val password = "secret".toCharArray()
            val client = IrcClient("localhost", server.localPort, "test", "test", password, incoming::add, { ok, _ -> if (ok) ready.countDown() }, { _, port -> Socket("127.0.0.1", port) })
            try {
                client.start(); assertTrue(ready.await(5, TimeUnit.SECONDS)); assertTrue(pong.await(5, TimeUnit.SECONDS)); assertTrue(client.echoMessages)
                assertEquals("CAP END", frames[6]); assertEquals("PONG :proof", frames.last())
                val sasl = frames.first { it.startsWith("AUTHENTICATE ") && it != "AUTHENTICATE PLAIN" }.substringAfter(' ')
                assertEquals("\u0000test\u0000secret", String(java.util.Base64.getDecoder().decode(sasl)))
                assertTrue(incoming.none { it.command in setOf("AUTHENTICATE", "CAP", "903") }); assertTrue(incoming.none { it.params.any { p -> p.contains("secret") } })
            } finally { client.close(); fixture.join(5000) }
            assertFalse(fixture.isAlive)
        }
    }
    @Test fun rejectsSaslDowngradeRatherThanJoiningAsGuest() {
        ServerSocket(0).use { server ->
            val disconnected = CountDownLatch(1); val sent = CopyOnWriteArrayList<String>()
            val fixture = thread {
                server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    repeat(3) { sent.add(reader.readLine()) }; writer.write(":s CAP test LS :echo-message\r\n"); writer.flush()
                    while (true) { val line = reader.readLine() ?: break; sent.add(line) }
                }
            }
            val c = IrcClient("localhost", server.localPort, "test", "test", "secret".toCharArray(), {}, { _, text -> if (text.startsWith("Disconnected")) disconnected.countDown() }, { _, port -> Socket("127.0.0.1", port) })
            try { c.start(); assertTrue(disconnected.await(5, TimeUnit.SECONDS)); assertFalse(c.ready) } finally { c.close(); fixture.join(5000) }
            assertFalse(sent.any { it == "CAP END" || it.startsWith("JOIN") || it.contains("secret") })
        }
    }
    @Test fun outgoingMessagesCannotBeQueuedBeforeRegistration() {
        val c = IrcClient("localhost", 6697, "test", receive = {}, connection = { _, _ -> })
        assertThrows(IllegalStateException::class.java) { c.send("PRIVMSG", listOf("#c"), "hello") }; c.close()
    }
    @Test fun welcomeBeforeSaslSuccessCannotTurnAccountLoginIntoGuestLogin() {
        ServerSocket(0).use { server ->
            val disconnected = CountDownLatch(1); val ready = java.util.concurrent.atomic.AtomicBoolean(false)
            val fixture = thread {
                server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    fun send(line: String) { writer.write(line + "\r\n"); writer.flush() }
                    repeat(3) { reader.readLine() }; send(":s CAP test LS :sasl")
                    reader.readLine(); send(":s CAP test ACK :sasl")
                    reader.readLine(); send(":s 001 test :Welcome without authentication")
                    while (reader.readLine() != null) { }
                }
            }
            val c = IrcClient("localhost", server.localPort, "test", "test", "secret".toCharArray(), {}, { ok, text -> if (ok) ready.set(true); if (text.startsWith("Disconnected")) disconnected.countDown() }, { _, port -> Socket("127.0.0.1", port) })
            try { c.start(); assertTrue(disconnected.await(5, TimeUnit.SECONDS)); assertFalse(ready.get()); assertFalse(c.ready) }
            finally { c.close(); fixture.join(5000) }
        }
    }
}
