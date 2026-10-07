package ohi.andre.consolelauncher.managers.termux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.UIManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceProtocolTest {
    private fun parse(text: String): Any {
        // Parser has no UI dependencies; allocate without constructing launcher services.
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val instance = type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), UIManager::class.java)
        return UIManager::class.java.getDeclaredMethod("parseTermuxWorkspaceFrame", String::class.java)
            .apply { isAccessible = true }.invoke(instance, text)!!
    }

    private fun value(frame: Any, name: String): Any? = frame.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(frame)

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

    @Test fun failedHelperMutationIsNeverReplayed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val manager = type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), UIManager::class.java)
        instrumentation.runOnMainSync {
            UIManager::class.java.getDeclaredField("termuxWorkspaceOutput").apply { isAccessible = true }
                .set(manager, android.widget.TextView(instrumentation.targetContext))
        }
        val script = UIManager::class.java.getDeclaredMethod("buildTermuxWorkspaceBridgeScript", String::class.java)
            .apply { isAccessible = true }.invoke(manager, "send").toString()
        val fixture = "retui() { if [ \"\$2\" = capabilities ]; then printf '%s\\n' send; return 0; fi; printf '%s\\n' mutation; return 9; };\n" +
            script + "\nprintf '%s\\n' replay\n"
        val process = ProcessBuilder("/system/bin/sh", "-c", fixture).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals("mutation\n", output)
        assertEquals(9, process.waitFor())
    }

    @Test fun encodedBodyPreservesEveryProtocolLookingLine() {
        val body = "__RETUI_FRAME_END__\n__RETUI_TOKEN__ attacker\n__RETUI_SOCKET_ERROR__ fake\n"
        val encoded = android.util.Base64.encodeToString(body.toByteArray(), android.util.Base64.NO_WRAP)
        val frame = parse("__RETUI_COLS__ 80\n__RETUI_ROWS__ 24\n__RETUI_FRAME_BASE64__ $encoded\n__RETUI_FRAME_END__\n")
        assertEquals(body, value(frame, "frame"))
        assertNull(value(frame, "socketToken"))
    }

    @Test fun pauseClosesOnlyTheClientAndKeepsLocalDraft() {
        val type = Class.forName("sun.misc.Unsafe")
        val unsafe = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val manager = type.getMethod("allocateInstance", Class::class.java).invoke(unsafe, UIManager::class.java)
        val client = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
            override fun onFrame(frame: String) {}
            override fun onStatus(status: String) {}
            override fun onError(message: String) {}
            override fun onClosed() {}
        })
        fun set(name: String, value: Any) = UIManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
        val draft = StringBuilder("switch 2")
        set("termuxWorkspaceSocketClient", client)
        set("termuxWorkspaceLocalCommandBuffer", draft)
        set("termuxWorkspaceLocalCommandMode", true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { (manager as UIManager).pause() }
        assertNull(UIManager::class.java.getDeclaredField("termuxWorkspaceSocketClient").apply { isAccessible = true }.get(manager))
        assertEquals("switch 2", draft.toString())
        assertEquals(true, UIManager::class.java.getDeclaredField("termuxWorkspaceLocalCommandMode").apply { isAccessible = true }.get(manager))
        client.close()
    }

    @Test fun transientStatusDoesNotReplaceRunningTerminalContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val type = Class.forName("sun.misc.Unsafe")
        val unsafe = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val manager = type.getMethod("allocateInstance", Class::class.java).invoke(unsafe, UIManager::class.java)
        val buffer = StringBuilder("running editor content")
        instrumentation.runOnMainSync {
            UIManager::class.java.getDeclaredField("mContext").apply { isAccessible = true }.set(manager, instrumentation.targetContext)
            UIManager::class.java.getDeclaredField("termuxWorkspaceBuffer").apply { isAccessible = true }.set(manager, buffer)
            UIManager::class.java.getDeclaredField("termuxWorkspaceCurrentWindow").apply { isAccessible = true }.set(manager, "0:editor")
            UIManager::class.java.getDeclaredField("termuxWorkspaceOutput").apply { isAccessible = true }.set(manager, android.widget.TextView(instrumentation.targetContext))
            UIManager::class.java.getDeclaredMethod("renderTermuxWorkspaceStatus", String::class.java).apply { isAccessible = true }.invoke(manager, "typing")
        }
        assertEquals("running editor content", buffer.toString())
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

    @Test fun windowHeaderOffersSelectionForEachIndex() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val type = Class.forName("sun.misc.Unsafe")
        val unsafe = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val manager = type.getMethod("allocateInstance", Class::class.java).invoke(unsafe, UIManager::class.java)
        UIManager::class.java.getDeclaredField("mContext").apply { isAccessible = true }.set(manager, instrumentation.targetContext)
        val rendered = UIManager::class.java.getDeclaredMethod("formatTermuxWorkspaceWindowList", String::class.java)
            .apply { isAccessible = true }.invoke(manager, "0:shell*\t1:editor with spaces\t2:logs\t") as android.text.Spanned
        assertEquals(3, rendered.getSpans(0, rendered.length, android.text.style.ClickableSpan::class.java).size)
        assertTrue(rendered.toString().contains("editor with spaces"))
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

    @Test fun completeBoundedFrameIsNotCroppedFromItsBeginning() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val type = Class.forName("sun.misc.Unsafe")
        val unsafe = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val manager = type.getMethod("allocateInstance", Class::class.java).invoke(unsafe, UIManager::class.java)
        val body = List(120) { "x".repeat(240) }.joinToString("\n").replaceRange(0, 1, "A")
        val buffer = StringBuilder()
        instrumentation.runOnMainSync {
            fun set(name: String, value: Any) = UIManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
            set("mContext", instrumentation.targetContext)
            set("termuxWorkspaceBuffer", buffer)
            set("termuxWorkspaceGrid", TerminalGridView(instrumentation.targetContext))
            UIManager::class.java.declaredMethods.single { it.name == "updateTermuxWorkspaceOutput" }.apply { isAccessible = true }
                .invoke(manager, body, null, null, 240, 120)
        }
        assertEquals(body, buffer.toString())
    }

    @Test fun explicitWindowNamesKeepSpacesAndLiteralSuffixes() {
        val type = Class.forName("sun.misc.Unsafe")
        val unsafe = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val manager = type.getMethod("allocateInstance", Class::class.java).invoke(unsafe, UIManager::class.java)
        val method = UIManager::class.java.getDeclaredMethod("normalizeTermuxWorkspaceWindowTarget", String::class.java).apply { isAccessible = true }
        assertEquals("editor with spaces", method.invoke(manager, "editor with spaces"))
        assertEquals("literal*", method.invoke(manager, "literal*"))
        assertEquals("2", method.invoke(manager, "2:editor with spaces*"))
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
}
