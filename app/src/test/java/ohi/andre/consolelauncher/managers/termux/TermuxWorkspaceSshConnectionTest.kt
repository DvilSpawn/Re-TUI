package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.*
import org.junit.Test
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.HostKeyRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

class TermuxWorkspaceSshConnectionTest {
    @Test fun delayedDaemonReadinessReturnsTheOnlyAcceptedSocketForAuthentication() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val port = ServerSocket(0, 1, loopback).use { it.localPort }
        val worker = Executors.newSingleThreadExecutor()
        try {
            val accepted = worker.submit<Int> {
                Thread.sleep(125)
                ServerSocket(port, 1, loopback).use { server ->
                    server.soTimeout = 1000
                    server.accept().use { socket ->
                        socket.soTimeout = 1000
                        assertEquals(55, socket.getInputStream().read())
                        socket.getOutputStream().write(56)
                    }
                    server.soTimeout = 150
                    try { server.accept().use { fail("Readiness must not create a second connection") } }
                    catch (_: SocketTimeoutException) { }
                    1
                }
            }
            TermuxWorkspaceSshConnection.connectSocket("127.0.0.1", port, 1500) { true }.use { socket ->
                socket.soTimeout = 1000
                socket.getOutputStream().write(55)
                assertEquals(56, socket.getInputStream().read())
            }
            assertEquals(1, accepted.get(3, TimeUnit.SECONDS).toInt())
        } finally { worker.shutdownNow() }
    }

    @Test fun refusedDaemonHasABoundedDeadlineAndCancellationDoesNotOpenASocket() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val port = ServerSocket(0, 1, loopback).use { it.localPort }
        val started = System.nanoTime()
        try {
            TermuxWorkspaceSshConnection.connectSocket("127.0.0.1", port, 100) { true }
            fail("A missing daemon must time out")
        } catch (error: IllegalStateException) { assertTrue(error.cause is java.net.ConnectException) }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000)
        ServerSocket(0, 1, loopback).use { server ->
            try {
                TermuxWorkspaceSshConnection.connectSocket("127.0.0.1", server.localPort, 100) { false }
                fail("A closed workspace must not connect")
            } catch (error: IllegalStateException) { assertEquals("SSH connection closed", error.message) }
            server.soTimeout = 50
            try { server.accept().use { fail("Cancellation opened a socket") } }
            catch (_: SocketTimeoutException) { }
        }
        for (host in listOf("localhost", "192.0.2.1", "::1")) {
            try {
                TermuxWorkspaceSshConnection.connectSocket(host, port, 100) { true }
                fail("Only the dedicated IPv4 loopback endpoint is allowed")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun preservesWindowNamesContainingColons() {
        assertEquals(listOf("@1" to "0:editor: project", "@9" to "3:shell"),
            TermuxWorkspaceSshConnection.parseWindows("@1:0:editor: project\n@9:3:shell\n"))
        assertTrue(TermuxWorkspaceSshConnection.parseWindows("").isEmpty())
    }
    @Test fun rejectsMalformedOrDuplicateWindowTargets() {
        listOf("1:0:shell", "@1:-1:shell", "@1:zero:shell", "@1:0:shell\n@1:1:duplicate", "@1:0").forEach {
            try { TermuxWorkspaceSshConnection.parseWindows(it); fail(it) } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun jschAcceptsOnlyTheProvisionedKeyAndEndpoint() {
        val trusted = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, 256)
        val stranger = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, 256)
        try {
            val publicKey = ByteArrayOutputStream().also { trusted.writePublicKey(it, "") }.toString("UTF-8").trim()
            val ssh = JSch()
            ssh.setKnownHosts(ByteArrayInputStream(TermuxWorkspaceSshConnection.knownHosts(8928, publicKey).toByteArray()))
            val repository = ssh.hostKeyRepository
            assertEquals(HostKeyRepository.OK, repository.check("[127.0.0.1]:8928", trusted.publicKeyBlob))
            assertEquals(HostKeyRepository.CHANGED, repository.check("[127.0.0.1]:8928", stranger.publicKeyBlob))
            assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check("[127.0.0.1]:8929", trusted.publicKeyBlob))
            assertEquals(HostKeyRepository.NOT_INCLUDED, repository.check("[192.168.1.2]:8928", trusted.publicKeyBlob))
        } finally { trusted.dispose(); stranger.dispose() }
    }
    @Test fun pinsOnlyTheDedicatedLoopbackEndpoint() {
        assertEquals("[127.0.0.1]:8023 ecdsa-sha2-nistp256 AAAABBBB==\n",
            TermuxWorkspaceSshConnection.knownHosts(8023, "ecdsa-sha2-nistp256 AAAABBBB=="))
        listOf("ssh-rsa AAAABBBB", "host ecdsa-sha2-nistp256 AAAA", "ecdsa-sha2-nistp256 AAAA\nmalicious").forEach {
            try { TermuxWorkspaceSshConnection.knownHosts(8023, it); fail(it) } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun commandResponseTimesOutWithoutBlockingReadAndBoundsOutput() {
        val stalled = object : java.io.InputStream() {
            override fun available() = 0
            override fun read(): Int = error("Must not block on a stalled SSH channel")
        }
        val started = System.nanoTime()
        try {
            TermuxWorkspaceSshConnection.readBounded(stalled, 32, 20) { false }
            fail("Stalled channel must time out")
        } catch (expected: IllegalStateException) { assertEquals("Termux command timed out", expected.message) }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000)
        val response = "finished".toByteArray()
        assertArrayEquals(response, TermuxWorkspaceSshConnection.readBounded(ByteArrayInputStream(response), 32, 1000) { true })
        try {
            TermuxWorkspaceSshConnection.readBounded(ByteArrayInputStream(ByteArray(33)), 32, 1000) { true }
            fail("Oversized response must fail")
        } catch (expected: IllegalStateException) { assertEquals("Termux response too large", expected.message) }
    }

}
