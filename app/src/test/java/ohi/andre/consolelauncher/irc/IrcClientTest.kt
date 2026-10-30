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
    @Test fun historyIsRequestedAfterConfirmedJoinAndOnlyWithAllCapabilities() {
        listOf("batch server-time message-tags draft/chathistory draft/message-redaction", "batch server-time draft/chathistory", "server-time").forEach { offered ->
            ServerSocket(0).use { server ->
                val frames = CopyOnWriteArrayList<String>(); val incoming = CopyOnWriteArrayList<IrcMessage>()
                val done = CountDownLatch(1); val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
                val history = offered.contains("message-tags")
                val fixture = thread {
                    try { server.accept().use { peer ->
                        peer.soTimeout = 5000
                        val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                        fun reply(line: String) { writer.write(line + "\r\n"); writer.flush() }
                        while (true) {
                            val line = reader.readLine() ?: break; frames.add(line)
                            val message = IrcMessage.parse(line)
                            when (message.command) {
                                "CAP" -> when (message.params[0]) {
                                    "LS" -> reply(":s CAP test LS :$offered")
                                    "REQ" -> reply(":s CAP test ACK :${message.params.last()}")
                                    "END" -> {
                                        reply(":s 001 canonical :Welcome")
                                        reply(":s 005 canonical CHATHISTORY=25 :Supported")
                                        reply(":alice!u@host JOIN #other")
                                        reply("PING :before-join")
                                    }
                                }
                                "PONG" -> when (message.params.last()) {
                                    "before-join" -> {
                                        assertFalse(frames.any { it.startsWith("CHATHISTORY") })
                                        reply(":canonical!u@host JOIN #retui")
                                        if (!history) reply("PING :finished")
                                    }
                                    "finished" -> { done.countDown(); while (reader.readLine() != null) { }; return@use }
                                }
                                "CHATHISTORY" -> {
                                    assertTrue(history); assertEquals(listOf("LATEST", "#retui", "*", "25"), message.params)
                                    reply(":s BATCH +h chathistory #retui")
                                    reply("@batch=h;msgid=one;time=2026-10-13T15:01:00Z :komi/discord!u@host PRIVMSG #retui :🤤🤤🤤")
                                    reply(":s BATCH -h"); reply("PING :finished")
                                }
                            }
                        }
                    } } catch (error: Throwable) { failure.set(error); done.countDown() }
                }
                val c = IrcClient("localhost", server.localPort, "test", receive = incoming::add, connection = { _, _ -> }, connector = { _, port -> Socket("127.0.0.1", port) })
                try {
                    c.start(); assertTrue(done.await(5, TimeUnit.SECONDS))
                    failure.get()?.let { throw AssertionError("History fixture", it) }
                    assertEquals(if (history) 1 else 0, frames.count { it.startsWith("CHATHISTORY") })
                    if (history) assertTrue(incoming.any { it.command == "PRIVMSG" && it.params.last() == "🤤🤤🤤" })
                    if (history) assertTrue(frames.any { it.startsWith("CAP REQ") && "draft/message-redaction" in it })
                    else assertFalse(frames.any { it.contains("draft/chathistory") && it.startsWith("CAP REQ") })
                } finally { c.close(); fixture.join(5000) }
                assertFalse(fixture.isAlive)
            }
        }
    }
    @Test fun closeReturnsImmediatelyEvenWhenTlsCloseBlocksOrThrows() {
        val connected = CountDownLatch(1); val closing = CountDownLatch(1); val release = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val fixture = object : Socket() {
            override fun getInputStream() = object : java.io.InputStream() {
                override fun read(): Int { release.await(5, TimeUnit.SECONDS); return -1 }
            }
            override fun getOutputStream(): java.io.OutputStream { connected.countDown(); return java.io.ByteArrayOutputStream() }
            override fun close() {
                closing.countDown(); release.await(5, TimeUnit.SECONDS); ended.countDown()
                throw java.io.IOException("Fixture TLS close failure")
            }
        }
        val client = IrcClient("localhost", 6697, "test", receive = {}, connection = { _, _ -> }, connector = { _, _ -> fixture })
        try {
            client.start(); assertTrue(connected.await(5, TimeUnit.SECONDS))
            val returned = CountDownLatch(1)
            thread { client.close(); returned.countDown() }
            assertTrue("Close must not wait for TLS close_notify", returned.await(1, TimeUnit.SECONDS))
            assertFalse(client.ready); assertTrue(closing.await(5, TimeUnit.SECONDS))
            client.close()
        } finally { release.countDown(); client.close() }
        assertTrue(ended.await(5, TimeUnit.SECONDS))
    }
    private fun registration(capability: String, response: String?, beforeConnect: Boolean = false, rejectCapability: Boolean = false): Pair<List<String>, String> {
        ServerSocket(0).use { server ->
            val frames = CopyOnWriteArrayList<String>(); val incoming = CopyOnWriteArrayList<IrcMessage>()
            val done = CountDownLatch(1); var result = ""; val ready = java.util.concurrent.atomic.AtomicBoolean(false)
            val workerFailure = java.util.concurrent.atomic.AtomicReference<Throwable>()
            val fixture = thread {
                try { server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    fun reply(line: String) { writer.write(line + "\r\n"); writer.flush() }
                    var registrationSent = false
                    while (true) {
                        val line = reader.readLine() ?: break; frames.add(line)
                        val m = IrcMessage.parse(line)
                        when (m.command) {
                            "CAP" -> when (m.params[0]) {
                                "LS" -> reply(":s CAP test LS :$capability")
                                "REQ" -> reply(":s CAP test ${if (rejectCapability) "NAK" else "ACK"} :${m.params.last()}")
                                "END" -> reply(":s 001 test :Welcome")
                            }
                            "REGISTER" -> {
                                registrationSent = true
                                assertFalse("Signup must not become a guest session", ready.get())
                                assertEquals("test", m.params[0]); assertEquals("*", m.params[1]); assertEquals("fixture password", m.params[2])
                                assertEquals(beforeConnect, frames.none { it == "CAP END" })
                                // Human text and user-origin success frames cannot authenticate signup.
                                reply(":NickServ!service@fixture NOTICE test :Account created")
                                reply(":alice!u@fixture REGISTER SUCCESS test :Not a server response")
                                reply("PING :confirmation-barrier")
                            }
                            "PONG" -> if (registrationSent) {
                                assertFalse(ready.get())
                                if (response == null) return@use else reply(response)
                            }
                            "JOIN" -> { done.countDown(); while (reader.readLine() != null) { }; return@use }
                        }
                    }
                } } catch (error: Throwable) { workerFailure.set(error); done.countDown() }
            }
            lateinit var c: IrcClient
            c = IrcClient("localhost", server.localPort, "test", "test", "fixture password".toCharArray(), incoming::add,
                { ok, text -> if (ok) { ready.set(true); result = "ready"; c.send("JOIN", listOf("#retui")) } else if (text.startsWith("Disconnected") && !ready.get()) { result = text; done.countDown() } },
                { _, port -> Socket("127.0.0.1", port) }, register = true)
            try {
                c.start(); assertTrue(done.await(5, TimeUnit.SECONDS))
                assertTrue(incoming.none { it.command == "REGISTER" || it.params.any { value -> value.contains("fixture password") } })
            } finally { c.close(); fixture.join(5000) }
            workerFailure.get()?.let { throw AssertionError("Registration peer", it) }
            assertFalse(fixture.isAlive)
            return frames.toList() to result
        }
    }
    @Test fun guidedRegistrationRequiresServerConfirmationBeforeJoining() {
        listOf(false, true).forEach { early ->
            val (frames, result) = registration("draft/account-registration${if (early) "=before-connect" else ""} echo-message", ":s REGISTER SUCCESS test :Created", early)
            assertEquals("ready", result); assertEquals(1, frames.count { it.startsWith("REGISTER ") }); assertEquals(1, frames.count { it.startsWith("JOIN ") })
            assertFalse(frames.any { it.startsWith("AUTHENTICATE") })
        }
    }
    @Test fun closedOrEmailOnlyRegistrationNeverReceivesPasswordOrJoins() {
        listOf("sasl=PLAIN echo-message", "draft/account-registration=email-required echo-message").forEach { capability ->
            val (frames, result) = registration(capability, null)
            assertTrue(result.startsWith("Disconnected")); assertFalse(frames.any { it.startsWith("REGISTER") || it.startsWith("AUTHENTICATE") || it.startsWith("JOIN") || it.contains("fixture password") })
        }
        val (frames, result) = registration("draft/account-registration", null, rejectCapability = true)
        assertTrue(result.contains("rejected")); assertFalse(frames.any { it.startsWith("REGISTER") || it == "CAP END" || it.startsWith("JOIN") })
    }
    @Test fun rejectedUnverifiedOrUncertainSignupNeverFallsBackOrRetries() {
        listOf(":s FAIL REGISTER ACCOUNT_EXISTS test :fixture password", ":s REGISTER VERIFICATION_REQUIRED test :Verify first", ":s REGISTER SUCCESS somebodyelse :Wrong account", null).forEach { response ->
            val (frames, result) = registration("draft/account-registration", response)
            assertTrue(result.startsWith("Disconnected")); assertFalse(result.contains("fixture password"))
            assertEquals(1, frames.count { it.startsWith("REGISTER ") }); assertFalse(frames.any { it.startsWith("JOIN") || it.startsWith("AUTHENTICATE") })
        }
    }
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
    /** A network that offers no SASL at all (OFTC) gets the password through NickServ, after the welcome. */
    @Test fun servicesFallbackIdentifiesOnlyWhenTheServerHasNoSasl() {
        for (offered in listOf("echo-message", "sasl=PLAIN echo-message")) ServerSocket(0).use { server ->
            val sent = CopyOnWriteArrayList<String>(); val outcome = CountDownLatch(1); val text = java.util.concurrent.atomic.AtomicReference("")
            val fixture = thread {
                server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    fun send(line: String) { writer.write(line + "\r\n"); writer.flush() }
                    repeat(3) { sent.add(reader.readLine()) }; send(":s CAP test LS :$offered")
                    sent.add(reader.readLine()); send(":s CAP test ACK :echo-message")
                    if (!offered.contains("sasl")) { sent.add(reader.readLine()); send(":s 001 test :Welcome") }
                    while (true) { val line = reader.readLine() ?: break; sent.add(line) }
                }
            }
            val c = IrcClient("localhost", server.localPort, "test", "test", "secret".toCharArray(), {}, { ok, t -> if (ok || t.startsWith("Disconnected")) { text.set(t); outcome.countDown() } },
                { _, port -> Socket("127.0.0.1", port) }, servicesFallback = true)
            try {
                c.start(); assertTrue(outcome.await(5, TimeUnit.SECONDS))
                if (offered.contains("sasl")) {
                    // SASL on offer is still required: the request is for sasl, never a NickServ password.
                    assertTrue(sent.any { it.startsWith("CAP REQ") && it.contains("sasl") })
                } else {
                    assertTrue(c.ready); assertTrue(text.get().contains("NickServ"))
                    val deadline = System.nanoTime() + 2_000_000_000
                    while (sent.none { it == "PRIVMSG NickServ :IDENTIFY secret" } && System.nanoTime() < deadline) Thread.sleep(20)
                    assertTrue(sent.contains("PRIVMSG NickServ :IDENTIFY secret"))
                    assertFalse(sent.any { it.startsWith("AUTHENTICATE") })
                }
            } finally { c.close(); fixture.join(5000) }
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

    @Test fun nicknameInUseFallsBackAndWrongPasswordSaysSo() {
        for (rejected in listOf(false, true)) ServerSocket(0).use { server ->
            val sent = CopyOnWriteArrayList<String>(); val outcome = CountDownLatch(1); val text = java.util.concurrent.atomic.AtomicReference("")
            val fixture = thread {
                server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    fun send(line: String) { writer.write(line + "\r\n"); writer.flush() }
                    repeat(3) { sent.add(reader.readLine()) }
                    send(":s 433 * test :Nickname is already in use"); sent.add(reader.readLine())
                    send(":s CAP * LS :sasl=PLAIN"); sent.add(reader.readLine()); send(":s CAP * ACK :sasl")
                    sent.add(reader.readLine()); send("AUTHENTICATE +"); sent.add(reader.readLine())
                    if (rejected) send(":s 904 test_ :SASL authentication failed")
                    else { send(":s 903 test_ :SASL authentication successful"); sent.add(reader.readLine()); send(":s 001 test_ :Welcome") }
                    while (true) { val line = reader.readLine() ?: break; sent.add(line) }
                }
            }
            val c = IrcClient("localhost", server.localPort, "test", "test", "secret".toCharArray(), {}, { ok, t -> if (ok || t.startsWith("Disconnected")) { text.set(t); outcome.countDown() } },
                { _, port -> Socket("127.0.0.1", port) })
            try {
                c.start(); assertTrue(outcome.await(5, TimeUnit.SECONDS))
                assertTrue(sent.contains("NICK test_"))
                if (rejected) { assertFalse(c.ready); assertTrue(text.get().contains("check the account name and password")) }
                else assertTrue(c.ready)
            } finally { c.close(); fixture.join(5000) }
        }
    }
    @Test fun silentConnectionIsPingedThenDroppedInsteadOfHangingForever() {
        val previous = IrcClient.keepAliveMs
        IrcClient.keepAliveMs = 300
        ServerSocket(0).use { server ->
            val sent = CopyOnWriteArrayList<String>(); val dropped = CountDownLatch(1); val text = java.util.concurrent.atomic.AtomicReference("")
            val fixture = thread {
                server.accept().use { peer ->
                    peer.soTimeout = 5000; val reader = peer.getInputStream().bufferedReader(); val writer = peer.getOutputStream().bufferedWriter()
                    repeat(3) { sent.add(reader.readLine()) }
                    writer.write(":s CAP * LS :\r\n:s 001 test :Welcome\r\n"); writer.flush()
                    // Then the network goes quiet: no PONG, no traffic.
                    while (true) { val line = runCatching { reader.readLine() }.getOrNull() ?: break; sent.add(line) }
                }
            }
            val c = IrcClient("localhost", server.localPort, "test", "", charArrayOf(), {}, { ok, t -> if (!ok && t.startsWith("Disconnected")) { text.set(t); dropped.countDown() } },
                { _, port -> Socket("127.0.0.1", port) })
            try {
                c.start(); assertTrue(dropped.await(5, TimeUnit.SECONDS))
                assertTrue(sent.any { it.startsWith("PING") })
                assertTrue(text.get().contains("No reply"))
            } finally { c.close(); IrcClient.keepAliveMs = previous; fixture.join(5000) }
        }
    }
}
