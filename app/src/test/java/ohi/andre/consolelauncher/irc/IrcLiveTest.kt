package ohi.andre.consolelauncher.irc

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLPeerUnverifiedException

/** Explicit opt-in. Reads a private credential file, never logs it or posts in public channels. */
class IrcLiveTest {
    private fun credentials(): JSONObject {
        val path = System.getenv("RETUI_IRC_TEST_CREDENTIAL_FILE").orEmpty()
        assumeTrue("Live checks require a protected recovery file", path.isNotBlank())
        return JSONObject(File(path).readText())
    }
    @Test fun channelHistoryLoadsAfterReconnectWithoutPostingOrDuplicatingMessages() {
        val c = credentials(); val state = IrcState()
        repeat(2) {
            val ready = CountDownLatch(1); val history = CountDownLatch(1)
            var batch = ""
            val client = IrcClient(c.getString("server"), 6697, c.getString("account"), c.getString("account"), c.getString("password").toCharArray(),
                receive = { message -> synchronized(state) {
                    state.receive(message)
                    if (message.command == "BATCH" && message.params.getOrNull(1) == "chathistory" && message.params.getOrNull(2) == "#retui") batch = message.params[0].drop(1)
                    if (message.command == "BATCH" && batch.isNotEmpty() && message.params.firstOrNull() == "-$batch") history.countDown()
                } }, connection = { ok, _ -> if (ok) ready.countDown() })
            try {
                client.start(); assertTrue("Verified TLS and SASL", ready.await(15, TimeUnit.SECONDS))
                client.send("JOIN", listOf("#retui"))
                assertTrue("Server history batch arrived", history.await(15, TimeUnit.SECONDS))
                synchronized(state) {
                    val messages = state.buffer("#retui").lines.filter { it.messageId.isNotEmpty() }
                    assertTrue("Existing channel history is available", messages.isNotEmpty())
                    assertEquals("Overlapping history appears once", messages.size, messages.map { it.messageId }.distinct().size)
                }
            } finally { client.close(); synchronized(state) { state.disconnected() } }
        }
    }
    @Test fun certificateHostnameMismatchIsRejectedBeforeAuthentication() {
        val c = credentials()
        val address = java.net.InetAddress.getByName(c.getString("server")).hostAddress!!
        assertThrows(SSLPeerUnverifiedException::class.java) { IrcClient.tlsSocket(address, 6697).close() }
    }
    @Test fun nativeClientAuthenticatesAndJoinsBothOfficialChannels() {
        val c = credentials(); val ready = CountDownLatch(1); val retui = CountDownLatch(1); val help = CountDownLatch(1)
        val client = IrcClient(c.getString("server"), 6697, "dvil", "dvil", c.getString("password").toCharArray(),
            receive = { if (it.command == "366") { if ("#retui" in it.params) retui.countDown(); if ("#help" in it.params) help.countDown() } },
            connection = { connected, _ -> if (connected) ready.countDown() })
        try {
            client.start(); assertTrue("Native verified TLS + SASL", ready.await(15, TimeUnit.SECONDS)); assertTrue(client.echoMessages)
            client.send("JOIN", listOf("#retui")); client.send("JOIN", listOf("#help"))
            assertTrue(retui.await(10, TimeUnit.SECONDS)); assertTrue(help.await(10, TimeUnit.SECONDS))
        } finally { client.close() }
    }
    @Test fun guestCannotConnectOrJoinRegisteredOnlyChannel() {
        val c = credentials(); val ready = java.util.concurrent.atomic.AtomicBoolean(false); val rejected = CountDownLatch(1)
        val client = IrcClient(c.getString("server"), 6697, "check" + System.nanoTime().toString().takeLast(8),
            receive = { if (it.command == "477" && "#retui" in it.params) rejected.countDown() },
            connection = { connected, text -> if (connected) ready.set(true) else if (text.startsWith("Disconnected")) rejected.countDown() })
        try {
            client.start(); assertTrue("Guest connection is rejected", rejected.await(15, TimeUnit.SECONDS))
            assertFalse("No authenticated welcome for guest", ready.get())
            assertFalse(client.ready)
        } finally { client.close() }
    }
    @Test fun privateTestChannelDeliversAndRedactsOnTwoNativeSessionsWithoutPolling() {
        val c = credentials(); val channel = "#retui-test-" + System.nanoTime().toString().takeLast(8)
        val body = "native transport smoke " + System.nanoTime()
        val ready = CountDownLatch(1); val oper = CountDownLatch(1); val names = CountDownLatch(1); val privateMode = CountDownLatch(1); val parted = CountDownLatch(1)
        val echo = CountDownLatch(1); val other = CountDownLatch(1); val secondReady = CountDownLatch(1)
        val redacted = CountDownLatch(2); val messageId = java.util.concurrent.atomic.AtomicReference("")
        val firstState = IrcState(); val secondState = IrcState()
        val first = IrcClient(c.getString("server"), 6697, "dvil", "dvil", c.getString("password").toCharArray(), receive = {
            synchronized(firstState) { firstState.receive(it) }
            if (it.command == "381") oper.countDown()
            if (it.command == "400" && it.params.lastOrNull()?.contains("already opered-up") == true) oper.countDown()
            if (it.command == "PART" && channel in it.params) parted.countDown()
            if (it.command == "366" && channel in it.params) names.countDown()
            if (it.command == "324" && channel in it.params && it.params.getOrNull(2)?.contains('s') == true) privateMode.countDown()
            if (it.command == "PRIVMSG" && it.params == listOf(channel, body)) { messageId.set(it.tags["msgid"].orEmpty()); echo.countDown() }
            if (it.command == "REDACT" && it.params.take(2) == listOf(channel, messageId.get())) redacted.countDown()
        }, connection = { ok, _ -> if (ok) ready.countDown() })
        val second = IrcClient(c.getString("server"), 6697, "dvil", "dvil", c.getString("password").toCharArray(), receive = {
            synchronized(secondState) { secondState.receive(it) }
            if (it.command == "PRIVMSG" && it.params == listOf(channel, body)) other.countDown()
            if (it.command == "REDACT" && it.params.take(2) == listOf(channel, messageId.get())) redacted.countDown()
        }, connection = { ok, _ -> if (ok) secondReady.countDown() })
        try {
            first.start(); assertTrue(ready.await(15, TimeUnit.SECONDS))
            first.send("OPER", listOf("dvil", c.getString("operator_password"))); assertTrue(oper.await(10, TimeUnit.SECONDS))
            first.send("JOIN", listOf(channel)); assertTrue(names.await(10, TimeUnit.SECONDS))
            first.send("MODE", listOf(channel, "+si")); first.send("MODE", listOf(channel)); assertTrue(privateMode.await(10, TimeUnit.SECONDS))
            second.start(); assertTrue(secondReady.await(15, TimeUnit.SECONDS))
            first.send("PRIVMSG", listOf(channel), body)
            assertTrue("Server echo within five seconds", echo.await(5, TimeUnit.SECONDS))
            assertTrue("Other native session receives without HTTP polling", other.await(5, TimeUnit.SECONDS))
            assertTrue("Server supplied message ID", messageId.get().isNotEmpty())
            first.send("REDACT", listOf(channel, messageId.get()))
            assertTrue("Both native clients receive deletion immediately", redacted.await(5, TimeUnit.SECONDS))
            synchronized(firstState) { assertTrue(firstState.buffer(channel).lines.none { it.body == body || it.text.contains(body) }) }
            synchronized(secondState) { assertTrue(secondState.buffer(channel).lines.none { it.body == body || it.text.contains(body) }) }
        } finally {
            if (first.ready && names.count == 0L) {
                runCatching { first.send("PART", listOf(channel)) }
                parted.await(5, TimeUnit.SECONDS)
            }
            second.close(); first.close()
        }
    }
}
