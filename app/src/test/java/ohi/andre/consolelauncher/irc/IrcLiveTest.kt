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
    @Test fun certificateHostnameMismatchIsRejectedBeforeAuthentication() {
        credentials()
        assertThrows(SSLPeerUnverifiedException::class.java) { IrcClient.tlsSocket("72.61.124.153", 6697).close() }
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
    @Test fun guestCannotJoinRegisteredOnlyChannel() {
        val c = credentials(); val ready = CountDownLatch(1); val rejected = CountDownLatch(1)
        val client = IrcClient(c.getString("server"), 6697, "check" + System.nanoTime().toString().takeLast(8),
            receive = { if (it.command == "477" && "#retui" in it.params) rejected.countDown() },
            connection = { connected, _ -> if (connected) ready.countDown() })
        try {
            client.start(); assertTrue(ready.await(15, TimeUnit.SECONDS)); client.send("JOIN", listOf("#retui"))
            assertTrue("Unregistered JOIN rejected", rejected.await(10, TimeUnit.SECONDS))
        } finally { client.close() }
    }
    @Test fun privateTestChannelDeliversToTwoNativeSessionsWithoutPolling() {
        val c = credentials(); val channel = "#retui-test-" + System.nanoTime().toString().takeLast(8)
        val body = "native transport smoke " + System.nanoTime()
        val ready = CountDownLatch(1); val oper = CountDownLatch(1); val names = CountDownLatch(1); val privateMode = CountDownLatch(1); val parted = CountDownLatch(1)
        val echo = CountDownLatch(1); val other = CountDownLatch(1); val secondReady = CountDownLatch(1)
        val first = IrcClient(c.getString("server"), 6697, "dvil", "dvil", c.getString("password").toCharArray(), receive = {
            if (it.command == "381") oper.countDown()
            if (it.command == "400" && it.params.lastOrNull()?.contains("already opered-up") == true) oper.countDown()
            if (it.command == "PART" && channel in it.params) parted.countDown()
            if (it.command == "366" && channel in it.params) names.countDown()
            if (it.command == "324" && channel in it.params && it.params.getOrNull(2)?.contains('s') == true) privateMode.countDown()
            if (it.command == "PRIVMSG" && it.params == listOf(channel, body)) echo.countDown()
        }, connection = { ok, _ -> if (ok) ready.countDown() })
        val second = IrcClient(c.getString("server"), 6697, "dvil", "dvil", c.getString("password").toCharArray(), receive = {
            if (it.command == "PRIVMSG" && it.params == listOf(channel, body)) other.countDown()
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
        } finally {
            if (first.ready && names.count == 0L) {
                runCatching { first.send("PART", listOf(channel)) }
                parted.await(5, TimeUnit.SECONDS)
            }
            second.close(); first.close()
        }
    }
}
