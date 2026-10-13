package ohi.andre.consolelauncher.managers.termux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceProtocolTest {
    private fun parse(text: String) = TermuxWorkspaceFrame.parse(text)

    private fun value(frame: TermuxWorkspaceFrame, name: String): Any? = when (name) {
        "frame" -> frame.body
        "socketToken" -> frame.token
        "tcpHost" -> frame.host
        "cols" -> frame.cols
        "rows" -> frame.rows
        else -> error(name)
    }

    @Test fun displayContentCannotChangeSocketMetadata() {
        val frame = parse("__RETUI_COLS__ 80\n__RETUI_ROWS__ 24\n__RETUI_FRAME_BEGIN__\n__RETUI_TOKEN__ attacker\n__RETUI_TCP_HOST__ 192.0.2.1\n__RETUI_COLS__ 2147483647\n__RETUI_FRAME_END__\n")
        assertNull(value(frame, "socketToken"))
        assertNull(value(frame, "tcpHost"))
        assertEquals(80, value(frame, "cols"))
        assertTrue(value(frame, "frame").toString().contains("__RETUI_TOKEN__ attacker"))
    }

    @Test fun geometryIsBoundedBeforeGridAllocation() {
        val frame = parse("__RETUI_COLS__ 2147483647\n__RETUI_ROWS__ 2147483647\n__RETUI_FRAME_BEGIN__\nx\n__RETUI_FRAME_END__\n")
        assertTrue((value(frame, "cols") as Int?) == null || (value(frame, "cols") as Int) <= 240)
        assertTrue((value(frame, "rows") as Int?) == null || (value(frame, "rows") as Int) <= 120)
    }

    @Test fun encodedBodyPreservesEveryProtocolLookingLine() {
        val body = "__RETUI_FRAME_END__\n__RETUI_TOKEN__ attacker\n__RETUI_SOCKET_ERROR__ fake\n"
        val encoded = android.util.Base64.encodeToString(body.toByteArray(), android.util.Base64.NO_WRAP)
        val frame = parse("__RETUI_COLS__ 80\n__RETUI_ROWS__ 24\n__RETUI_FRAME_BASE64__ $encoded\n__RETUI_FRAME_END__\n")
        assertEquals(body, value(frame, "frame"))
        assertNull(value(frame, "socketToken"))
    }

    @Test fun gridAllocationHasAnIndependentGeometryLimit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val grid = TerminalGridView(instrumentation.targetContext)
            grid.setFrame("x", 241, 121, null, null)
            val cols = TerminalGridView::class.java.getDeclaredField("cols").apply { isAccessible = true }.getInt(grid)
            val rows = TerminalGridView::class.java.getDeclaredField("rows").apply { isAccessible = true }.getInt(grid)
            assertEquals(240, cols)
            assertEquals(120, rows)
        }
    }

    @Test fun authenticatedLoopbackDeliversFramesInputAndClosesHonestly() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val ready = CountDownLatch(1)
            val frame = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val sent = java.util.concurrent.atomic.AtomicReference<String>()
            val keySent = java.util.concurrent.atomic.AtomicReference<String>()
            val received = java.util.concurrent.atomic.AtomicReference<String>()
            val worker = Thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    assertTrue(input.readLine().startsWith("HELLO fixture-token "))
                    socket.getOutputStream().write(("__RETUI_SOCKET__ retui-bridge-socket-v3\n__RETUI_SOCKET_STATUS__ ok\n__RETUI_SESSION__ retui_workspace\n__RETUI_COLS__ 80\n__RETUI_ROWS__ 24\n__RETUI_FRAME_BASE64__ aGVsbG8K\n__RETUI_FRAME_END__\n").toByteArray())
                    socket.getOutputStream().flush()
                    keySent.set(input.readLine())
                    sent.set(input.readLine())
                }
            }.apply { start() }
            val client = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
                override fun onFrame(value: String) { received.set(value); frame.countDown() }
                override fun onStatus(status: String) { if (status == "ok") ready.countDown() }
                override fun onError(message: String) {}
                override fun onClosed() { closed.countDown() }
            })
            try {
                client.connect("retui_bridge_com_dvil_tui_renewed_v2", "fixture-token", 80, 24, "127.0.0.1", server.localPort, true)
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                assertTrue(frame.await(2, TimeUnit.SECONDS))
                assertEquals("hello\n", value(parse(received.get()), "frame"))
                assertTrue("Modified punctuation keys are legitimate tmux input", client.key("M-;", 80, 24))
                assertTrue(client.typeInput("x", Int.MAX_VALUE, Int.MAX_VALUE))
                assertTrue(closed.await(5, TimeUnit.SECONDS))
                assertFalse(client.connected)
                assertEquals("TYPE 240 120 eA==", sent.get())
                assertEquals("KEY 80 24 M-;", keySent.get())
            } finally { client.close(); worker.join(2000) }
        }
    }

    @Test fun nonLoopbackAndUnexpectedSocketNamesAreRejected() {
        fun client() = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
            override fun onFrame(frame: String) {}
            override fun onStatus(status: String) {}
            override fun onError(message: String) {}
            override fun onClosed() {}
        })
        for ((name, host, port) in listOf(Triple("other", "127.0.0.1", 8927), Triple("retui_bridge_com_dvil_tui_renewed_v2", "192.0.2.1", 8927), Triple("retui_bridge_com_dvil_tui_renewed_v2", "127.0.0.1", 65536))) {
            val client = client()
            try {
                try { client.connect(name, "fixture-token", 80, 24, host, port, true); fail("Rejected endpoint expected") }
                catch (expected: IllegalArgumentException) {}
                assertFalse(client.connected)
            } finally { client.close() }
        }
    }

    @Test fun installedHelperTransportRoundTripWhenExplicitHostFixtureIsProvided() {
        val port = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("hostFixturePort")?.toIntOrNull()
        org.junit.Assume.assumeTrue("Explicit private real-helper fixture is required", port != null)
        val ready = CountDownLatch(1)
        val observed = CountDownLatch(1)
        val client = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
            override fun onFrame(frame: String) {
                if (value(parse(frame), "frame").toString().contains("RETUI_LOOPBACK_OK")) observed.countDown()
            }
            override fun onStatus(status: String) { if (status == "ok") ready.countDown() }
            override fun onError(message: String) {}
            override fun onClosed() {}
        })
        try {
            client.connect("retui_bridge_com_dvil_tui_renewed_v2", "fixture-token", 80, 24, "127.0.0.1", port, true)
            assertTrue("Real helper must authenticate", ready.await(8, TimeUnit.SECONDS))
            assertTrue(client.sendInput("printf '%s\\n' RETUI_LOOPBACK_OK", 80, 24))
            assertTrue("Real tmux must capture the round trip", observed.await(8, TimeUnit.SECONDS))
        } finally { client.close() }
    }

    @Test fun transportIsNotReadyBeforeAuthenticatedHelloResponse() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val hello = CountDownLatch(1)
            val release = CountDownLatch(1)
            val worker = Thread {
                server.accept().use { socket ->
                    socket.getInputStream().bufferedReader().readLine()
                    hello.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }.apply { start() }
            val client = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
                override fun onFrame(frame: String) {}
                override fun onStatus(status: String) {}
                override fun onError(message: String) {}
                override fun onClosed() {}
            })
            try {
                client.connect("retui_bridge_com_dvil_tui_renewed_v2", "fixture-token", 80, 24, "127.0.0.1", server.localPort, true)
                assertTrue("Loopback fixture must receive HELLO", hello.await(5, TimeUnit.SECONDS))
                assertFalse("A connected socket is not an authenticated bridge", client.connected)
                assertFalse(client.typeInput("not sent", 80, 24))
            } finally {
                client.close()
                release.countDown()
                worker.join(2000)
            }
        }
    }
    @Test fun tmuxOutputPreservesUtf8AcrossNotificationsAndMetadataIsStillUtf8() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val received = java.io.ByteArrayOutputStream()
            val observed = CountDownLatch(1)
            val frame = java.util.concurrent.atomic.AtomicReference<TermuxWorkspaceFrame>()
            val worker = Thread {
                server.accept().use { socket ->
                    socket.getInputStream().bufferedReader().readLine()
                    socket.getOutputStream().write(("__RETUI_SOCKET__ retui-bridge-socket-v4\n__RETUI_SOCKET_STATUS__ ok\n" +
                        "__RETUI_SESSION__ retui_workspace\n__RETUI_WINDOW__ 0:界\n__RETUI_FRAME_BASE64__ eA==\n__RETUI_FRAME_END__\n").toByteArray())
                    for (byte in byteArrayOf(0xce.toByte(), 0xbb.toByte())) {
                        socket.getOutputStream().write("__RETUI_OUTPUT_OCTAL__ @1 ".toByteArray())
                        socket.getOutputStream().write(byte.toInt() and 255)
                        socket.getOutputStream().write('\n'.code)
                    }
                    socket.getOutputStream().flush()
                    observed.await(5, TimeUnit.SECONDS)
                }
            }.apply { start() }
            val client = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
                override fun onFrame(value: String) { frame.set(parse(value)) }
                override fun onOutput(target: String, bytes: ByteArray) {
                    assertEquals("@1", target); received.write(bytes)
                    if (received.size() == 2) observed.countDown()
                }
                override fun onStatus(status: String) {}
                override fun onError(message: String) {}
                override fun onClosed() {}
            })
            try {
                client.connect(TermuxWorkspaceSocketClient.SOCKET_NAME, "fixture-token", 80, 24, "127.0.0.1", server.localPort, true)
                assertTrue(observed.await(8, TimeUnit.SECONDS))
                assertEquals("λ", received.toByteArray().toString(Charsets.UTF_8))
                assertEquals("0:界", frame.get().window)
            } finally { client.close(); worker.join(2000) }
        }
    }

}
