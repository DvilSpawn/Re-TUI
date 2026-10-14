package ohi.andre.consolelauncher.irc

import android.content.Intent
import android.graphics.Bitmap
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.ScrollView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.help.WindowPreferencesRule
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Loopback-only transport. No production messages or account mutations. */
class IrcGuidedDeviceTest {
    @get:Rule val windows = WindowPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private class Peer(private val registrationReply: String? = null, private val historyBodies: List<String>? = null,
        private val redactionId: String? = null, private val manualRedaction: Boolean = false,
        private val rejectRedaction: Boolean = false) : AutoCloseable {
        val server = ServerSocket(0)
        val frames = CopyOnWriteArrayList<IrcMessage>()
        val mechanisms = CopyOnWriteArrayList<String>()
        private var socket: Socket? = null
        private val worker = thread {
            runCatching {
                server.accept().use { connection ->
                    socket = connection; val reader = connection.getInputStream().bufferedReader(); val writer = connection.getOutputStream().bufferedWriter()
                    var nick = "fixture"
                    fun reply(text: String) { writer.write(text + "\r\n"); writer.flush() }
                    while (true) {
                        val raw = reader.readLine() ?: break; val m = IrcMessage.parse(raw)
                        // Never retain authentication payloads, even fixture passwords.
                        if (m.command != "AUTHENTICATE") frames.add(if (m.command == "REGISTER") m.copy(params = m.params.take(2)) else m)
                        when (m.command) {
                            "NICK" -> nick = m.params[0]
                            "CAP" -> if ("LS" in m.params) reply(":s CAP $nick LS :sasl=PLAIN,EXTERNAL echo-message${if (registrationReply != null) " draft/account-registration" else ""}${if (historyBodies != null) " batch server-time message-tags draft/chathistory" else ""}${if (redactionId != null || manualRedaction) " draft/message-redaction" else ""}") else if ("REQ" in m.params) reply(":s CAP $nick ACK :${m.params.last()}") else if ("END" in m.params) reply(":s 001 $nick :Welcome")
                            "REGISTER" -> reply(":s $registrationReply".replace("<account>", nick))
                            "AUTHENTICATE" -> if (m.params[0] in listOf("PLAIN", "EXTERNAL")) { mechanisms.add(m.params[0]); reply("AUTHENTICATE +") } else reply(":s 903 $nick :Success")
                            "JOIN" -> { val channel = m.params[0]; reply(":$nick!u@fixture JOIN $channel"); reply(":s 353 $nick = $channel :$nick alice"); reply(":s 366 $nick $channel :End") }
                            "CHATHISTORY" -> {
                                val channel = m.params[1]
                                reply(":s BATCH +history chathistory $channel")
                                historyBodies.orEmpty().forEachIndexed { index, body ->
                                    reply("@batch=history;msgid=fixture-$index;time=2026-10-13T15:01:${index.toString().padStart(2, '0')}Z :alice/discord!u@fixture PRIVMSG $channel :$body")
                                }
                                reply(":s BATCH -history")
                                if (redactionId != null) reply(":mod!u@fixture REDACT $channel $redactionId")
                            }
                            "NAMES" -> { reply(":s 353 $nick = ${m.params[0]} :$nick alice"); reply(":s 366 $nick ${m.params[0]} :End") }
                            "REDACT" -> if (rejectRedaction) reply(":s FAIL REDACT REDACT_FORBIDDEN ${m.params[0]} ${m.params[1]} :Fixture permission denied")
                                else reply(":mod!u@fixture REDACT ${m.params[0]} ${m.params[1]}")
                            "LIST" -> { reply(":s 321 $nick :Start"); reply(":s 322 $nick #fixture 2 :A test channel"); reply(":s 323 $nick :End") }
                            "PRIVMSG" -> if (m.params[0] == "NickServ" && m.params[1].startsWith("CERT ADD ")) reply(":NickServ!service@fixture NOTICE $nick :Certificate fingerprint successfully added")
                                else if (m.params[0] == "NickServ" && m.params[1].startsWith("CERT DEL ")) reply(":NickServ!service@fixture NOTICE $nick :Certificate fingerprint successfully removed")
                                else reply(":$nick!u@fixture PRIVMSG ${m.params[0]} :${m.params[1]}")
                        }
                    }
                }
            }
        }
        override fun close() { socket?.close(); server.close(); worker.join(5000) }
    }
    private fun start(): IrcActivity {
        val a = instrumentation.startActivitySync(Intent(instrumentation.targetContext, IrcActivity::class.java).putExtra("irc_offline_fixture", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as IrcActivity
        waitFor { var resumed = false; main { resumed = a.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED && a.onBackPressedDispatcher.hasEnabledCallbacks() }; resumed }
        return a
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) { instrumentation.waitForIdleSync(); if (condition()) return; android.os.SystemClock.sleep(25) }
        assertTrue("Fixture state arrived", condition())
    }
    private fun input(a: IrcActivity) = a.findViewById<EditText>(R.id.irc_input)
    private fun capture(a: IrcActivity, name: String) {
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(200)
        instrumentation.waitForIdleSync()
        val shot = instrumentation.uiAutomation.takeScreenshot()
        try { File(a.getExternalFilesDir(null), "irc-guided-$name.png").outputStream().use { assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { shot.recycle() }
    }
    private fun submit(a: IrcActivity, text: String) { input(a).setText(text); a.findViewById<View>(R.id.irc_send).performClick() }
    private fun find(view: View, text: String): View? {
        if (view is TextView && view.text.toString().removePrefix("> ") == text) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), text)?.let { return it }
        return null
    }
    private fun click(a: IrcActivity, text: String) {
        var target = find(a.window.decorView, text); assertNotNull("Action $text", target)
        while (target != null && !target.isClickable) target = target.parent as? View
        assertNotNull("Clickable action $text", target); target!!.performClick()
    }
    @Test fun reopeningPaneLoadsMessagesSentWhileClosedWithRelayedNamesAndEmoji() {
        val bodies = listOf("Earlier conversation", "Hello @fixture", "Missed while closed 🤤🤤🤤")
        for (count in listOf(2, 3)) {
            val a = start()
            Peer(historyBodies = bodies.take(count)).use { peer ->
                try {
                    main {
                        a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }
                        a.session.account = "fixture"; a.session.state.nickname = "fixture"
                        a.session.connect("fixture-only-password".toCharArray())
                    }
                    waitFor { a.session.state.active.lines.count { it.body in bodies } == count }
                    main {
                        val transcript = a.findViewById<TextView>(R.id.irc_output).text.toString()
                        assertTrue(transcript.contains("<alice/discord> ${bodies[count - 1]}"))
                        assertEquals(count, a.session.state.active.lines.count { it.body in bodies })
                        assertFalse(a.session.state.active.highlight)
                    }
                    if (count == 3) capture(a, "history-reopen")
                    main { a.onBackPressedDispatcher.onBackPressed() }
                    assertNull(a.session.client)
                    assertEquals(1, peer.frames.count { it.command == "CHATHISTORY" })
                    assertFalse(peer.frames.any { it.command == "PRIVMSG" })
                } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
            }
        }
    }
    @Test fun moderatorDeletionUpdatesVisibleTranscriptWithoutReconnecting() {
        val a = start()
        Peer(historyBodies = listOf("keep this message", "fixture-secret"), redactionId = "fixture-1").use { peer ->
            try {
                main {
                    a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }
                    a.session.account = "fixture"; a.session.state.nickname = "fixture"
                    a.session.connect("fixture-only-password".toCharArray())
                }
                waitFor { a.findViewById<TextView>(R.id.irc_output).text.contains("Message deleted") }
                main {
                    val transcript = a.findViewById<TextView>(R.id.irc_output).text.toString()
                    assertTrue(transcript.contains("keep this message")); assertFalse(transcript.contains("fixture-secret"))
                    assertTrue(a.session.client?.ready == true)
                }
                capture(a, "message-deleted")
            } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
        }
    }
    @Test fun deleteMessageActionRequiresConfirmationAndWaitsForServerPermission() {
        for (rejected in listOf(false, true)) {
            val a = start()
            Peer(historyBodies = listOf("delete fixture", "keep fixture"), manualRedaction = true, rejectRedaction = rejected).use { peer ->
                try {
                    main {
                        a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }
                        a.session.account = "fixture"; a.session.state.nickname = "fixture"
                        a.session.connect("fixture-only-password".toCharArray())
                    }
                    waitFor { a.session.state.active.lines.any { it.body == "delete fixture" } }
                    main {
                        input(a).setText("preserved draft"); click(a, "More"); click(a, "Delete a message")
                        click(a, "<alice/discord> delete fixture")
                        assertEquals(IrcNavigation.Page.DELETE_CONFIRM, a.session.navigation.page)
                        assertFalse(peer.frames.any { it.command == "REDACT" })
                        a.onBackPressedDispatcher.onBackPressed(); assertEquals("preserved draft", input(a).text.toString())
                        click(a, "More"); click(a, "Delete a message"); click(a, "<alice/discord> delete fixture")
                        click(a, "Delete from IRC")
                    }
                    waitFor { a.findViewById<TextView>(R.id.irc_output).text.contains(if (rejected) "Fixture permission denied" else "Message deleted") }
                    main {
                        val transcript = a.findViewById<TextView>(R.id.irc_output).text.toString()
                        assertEquals(rejected, transcript.contains("delete fixture")); assertTrue(transcript.contains("keep fixture"))
                        assertEquals("preserved draft", input(a).text.toString())
                    }
                    assertEquals(listOf("#retui", "fixture-0"), peer.frames.single { it.command == "REDACT" }.params)
                    if (!rejected) capture(a, "delete-action")
                } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
            }
        }
    }
    @Test fun guidedLoginChannelsPeopleAndLocalHelpUseOneComposer() {
        val a = start()
        Peer().use { peer ->
            try {
                main {
                    a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }
                    input(a).setText("preserved draft"); click(a, "Sign in"); submit(a, "fixture")
                    assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, input(a).inputType and InputType.TYPE_MASK_VARIATION)
                    submit(a, "fixture-only-password")
                }
                waitFor { a.session.state.active.joined }
                capture(a, "chat")
                main {
                    assertEquals("#retui", a.session.state.active.name); assertEquals("preserved draft", a.session.state.buffers.first().draft)
                    input(a).setText("my channel draft"); click(a, "Help")
                    assertEquals(IrcNavigation.Page.HELP, a.session.navigation.page)
                    submit(a, "a local search, not a message")
                    a.onBackPressedDispatcher.onBackPressed(); assertEquals("my channel draft", input(a).text.toString())
                    submit(a, "/help"); assertEquals("#retui", a.session.state.active.name)
                    a.onBackPressedDispatcher.onBackPressed()
                    click(a, "Channels"); submit(a, "#help"); click(a, "#help")
                }
                waitFor { a.session.state.active.name == "#help" && a.session.state.active.joined }
                main { click(a, "People") }
                waitFor { a.session.state.active.names.contains("alice") && !a.session.state.active.namesLoading }
                capture(a, "people")
                main { click(a, "alice"); assertEquals("alice", a.session.state.active.name); assertTrue(a.findViewById<TextView>(R.id.irc_context).text.contains("Private conversation")) }
                assertFalse(peer.frames.any { it.command == "PRIVMSG" })
                assertFalse(a.session.state.buffers.any { b -> b.lines.any { it.text.contains("fixture-only-password") || it.text.contains("one TLS network") } })
            } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
        }
    }
    @Test fun historyReplayAndMentionSuggestionsWorkOnDevice() {
        val a = start()
        Peer(historyBodies = listOf("replayed fixture message")).use { peer ->
            try {
                main {
                    a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }
                    a.session.account = "fixture"; a.session.state.nickname = "fixture"
                    a.session.connect("fixture-password".toCharArray())
                }
                waitFor { a.session.state.active.lines.any { it.body == "replayed fixture message" } }
                assertTrue("CHATHISTORY was not requested: ${peer.frames}", peer.frames.any { it.command == "CHATHISTORY" })
                main {
                    assertTrue(a.session.state.active.lines.any { it.body == "replayed fixture message" })
                    assertTrue(peer.frames.any { it.command == "CHATHISTORY" && it.params[1] == "#retui" })
                    assertTrue(peer.frames.any { it.command == "NAMES" && it.params[0] == "#retui" })
                    a.session.state.receive(IrcMessage.parse(":s 353 fixture = #retui :fixture alice"))
                    input(a).setText("@al")
                    input(a).setSelection(input(a).length())
                    a.session.changed?.invoke()
                }
                val mentionDeadline = System.nanoTime() + 5_000_000_000
                while (System.nanoTime() < mentionDeadline && find(a.window.decorView, "@alice") == null) { instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(25) }
                val chipRow = a.findViewById<android.widget.LinearLayout>(R.id.irc_suggestions)
                val chipLabels = (0 until chipRow.childCount).mapNotNull { (chipRow.getChildAt(it) as? android.view.ViewGroup)?.let { group -> (0 until group.childCount).mapNotNull { group.getChildAt(it) as? TextView }.firstOrNull()?.text?.toString() } }
                assertNotNull("Mention chip missing: names=${a.session.state.active.names} ready=${a.session.client?.ready} input=${input(a).text} selection=${input(a).selectionStart} chips=$chipLabels", find(a.window.decorView, "@alice"))
                assertFalse(peer.frames.any { it.command == "PRIVMSG" && it.params.last() == "@al" })
            } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
        }
    }
    @Test fun navigationSurvivesRecreationAndPasswordStageCancels() {
        var a = start()
        try {
            main {
                val state = a.session.state; state.nickname = "fixture"; state.receive(IrcMessage.parse(":fixture!u@host JOIN #retui")); a.session.changed?.invoke()
                input(a).setText("keep this draft"); submit(a, "/help"); click(a, "All commands"); input(a).setText("join")
            }
            fun recreate() {
                val monitor = instrumentation.addMonitor(IrcActivity::class.java.name, null, false)
                try { main { a.recreate() }; a = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as IrcActivity; instrumentation.waitForIdleSync() }
                finally { instrumentation.removeMonitor(monitor) }
            }
            recreate()
            main {
                assertEquals(IrcNavigation.Page.COMMANDS, a.session.navigation.page); assertEquals("join", input(a).text.toString())
                a.onBackPressedDispatcher.onBackPressed(); assertEquals("keep this draft", input(a).text.toString())
                submit(a, "/login fixture"); input(a).setText("rotation-secret")
            }
            recreate()
            main { assertEquals("keep this draft", input(a).text.toString()); assertEquals(InputType.TYPE_TEXT_FLAG_MULTI_LINE, input(a).inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE); assertNull(a.session.client) }
            assertFalse(a.session.state.buffers.any { b -> b.lines.any { it.text.contains("rotation-secret") } })
            main { submit(a, "/register"); input(a).setText("fixture") }
            recreate()
            main {
                assertEquals(IrcNavigation.Page.REGISTER, a.session.navigation.page); assertEquals("fixture", input(a).text.toString())
                submit(a, "fixture"); input(a).setText("signup-rotation-secret")
            }
            recreate()
            main { assertEquals("keep this draft", input(a).text.toString()); assertNull(a.session.client); assertEquals(InputType.TYPE_TEXT_FLAG_MULTI_LINE, input(a).inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) }
            assertFalse(a.session.state.buffers.any { b -> b.lines.any { it.text.contains("signup-rotation-secret") } })
        } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
    }
    @Test fun browseDoesNotSendSearchAndEchoDeduplicatesImmediateLocalMessage() {
        val a = start()
        Peer().use { peer ->
            try {
                main { a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }; a.session.account = "fixture"; a.session.state.nickname = "fixture"; a.session.connect("fixture-password".toCharArray()) }
                waitFor { a.session.state.active.joined }
                main { click(a, "Channels"); click(a, "Browse network"); input(a).setText("fixture") }
                waitFor { a.session.state.directory.size == 1 && !a.session.state.directoryLoading }
                capture(a, "channels")
                main { a.onBackPressedDispatcher.onBackPressed(); submit(a, "hello fixture"); assertTrue(a.session.state.active.lines.any { it.body == "hello fixture" }) }
                waitFor { a.session.state.active.lines.any { it.body == "hello fixture" && !it.pending } }
                assertEquals(1, a.session.state.active.lines.count { it.body == "hello fixture" })
                assertEquals(1, peer.frames.count { it.command == "PRIVMSG" }); assertFalse(peer.frames.any { it.command == "PRIVMSG" && it.params.last() == "fixture" })
            } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
        }
    }
    @Test fun temporaryHelpPreservesReadingPositionAndHiddenUnreadAndCommandHints() {
        val a = start()
        try {
            main {
                val s = a.session.state; s.nickname = "fixture"; s.receive(IrcMessage.parse(":fixture!u@host JOIN #retui"))
                repeat(80) { s.receive(IrcMessage.parse(":alice!u@host PRIVMSG #retui :fixture line $it")) }
                a.session.changed?.invoke(); input(a).setText("unfinished reply")
            }
            instrumentation.waitForIdleSync()
            main { a.findViewById<ScrollView>(R.id.irc_scroll).scrollTo(0, 120) }
            instrumentation.waitForIdleSync()
            val position = a.session.state.active.scroll
            main {
                submit(a, "/help")
                a.session.state.receive(IrcMessage.parse(":alice!u@host PRIVMSG #retui :hello fixture")); a.session.changed?.invoke()
                assertEquals(1, a.session.state.active.unread)
            }
            capture(a, "help")
            main { a.onBackPressedDispatcher.onBackPressed(); assertEquals("unfinished reply", input(a).text.toString()); assertEquals(0, a.session.state.active.unread) }
            instrumentation.waitForIdleSync()
            main {
                assertEquals(position, a.findViewById<ScrollView>(R.id.irc_scroll).scrollY)
                input(a).setText("/join")
                assertEquals(View.VISIBLE, a.findViewById<View>(R.id.irc_guidance).visibility)
                assertTrue(a.findViewById<TextView>(R.id.irc_guidance).text.contains("#channel"))
            }
            capture(a, "command")
        } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
    }
    @Test fun deviceEnrollmentAutomaticReconnectAndRevocationUseFixtureOnly() {
        val context = instrumentation.targetContext; val record = File(context.noBackupFilesDir, "irc-device.json")
        val original = record.takeIf { it.exists() }?.readBytes()
        val identity = IrcDeviceKey.Identity("irc.kokodoko.cloud", 6697, "fixture" + System.nanoTime().toString().takeLast(8))
        val devices = IrcDeviceKey(context)
        var a = start()
        try {
            devices.ensure(identity)
            Peer().use { first ->
                main {
                    a.session.connector = { _, _ -> Socket("127.0.0.1", first.server.localPort) }
                    a.session.rememberDevice = true; a.session.account = identity.account; a.session.state.nickname = identity.account
                    a.session.connect("fixture-only-password".toCharArray())
                }
                waitFor { devices.remembered() == identity }
                assertEquals(listOf("PLAIN"), first.mechanisms)
                main { a.finish() }; instrumentation.waitForIdleSync()
            }
            a = start()
            Peer().use { second ->
                main {
                    a.session.connector = { _, _ -> Socket("127.0.0.1", second.server.localPort) }
                    a.session.initialized = false; a.session.restoreDevice()
                }
                waitFor { a.session.state.active.joined }
                assertEquals(listOf("EXTERNAL"), second.mechanisms)
                assertFalse(second.frames.any { it.command == "PRIVMSG" })
                main { a.session.forgetDevice() }
                waitFor { devices.remembered() == null && a.session.client == null }
                assertTrue(second.frames.any { it.command == "PRIVMSG" && it.params.last().startsWith("CERT DEL ") })
                assertEquals(context.getString(R.string.irc_device_revoked), a.session.deviceStatus)
            }
        } finally {
            main { a.finish() }; instrumentation.waitForIdleSync()
            devices.forget(identity)
            if (original != null) record.writeBytes(original) else record.delete()
        }
    }
    @Test fun usernamePasswordSignupEnrollsAndReconnectsWithoutPassword() {
        val context = instrumentation.targetContext; val record = File(context.noBackupFilesDir, "irc-device.json")
        val original = record.takeIf { it.exists() }?.readBytes()
        val identity = IrcDeviceKey.Identity("irc.kokodoko.cloud", 6697, "signup" + System.nanoTime().toString().takeLast(8))
        val devices = IrcDeviceKey(context)
        var a = start()
        try {
            Peer("REGISTER SUCCESS <account> :Account successfully registered").use { peer ->
                main {
                    a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }
                    input(a).setText("keep signup draft"); click(a, "Sign in"); click(a, "Create account")
                    assertTrue(a.session.rememberDevice)
                }
                capture(a, "signup")
                main {
                    submit(a, identity.account)
                    assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, input(a).inputType and InputType.TYPE_MASK_VARIATION)
                    assertTrue(a.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
                    submit(a, "signup fixture password")
                }
                waitFor { devices.remembered() == identity && a.session.state.active.joined }
                assertEquals(1, peer.frames.count { it.command == "REGISTER" }); assertTrue(peer.mechanisms.isEmpty())
                assertEquals(1, peer.frames.count { it.command == "JOIN" })
                assertTrue(peer.frames.any { it.command == "PRIVMSG" && it.params.last().startsWith("CERT ADD ") })
                assertFalse(a.session.state.buffers.any { b -> b.lines.any { it.text.contains("signup fixture password") } })
                assertEquals("keep signup draft", a.session.state.buffers.first().draft)
                capture(a, "signup-complete")
                main { a.finish() }; instrumentation.waitForIdleSync()
            }
            a = start()
            Peer().use { peer ->
                main { a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }; a.session.initialized = false; a.session.restoreDevice() }
                waitFor { a.session.state.active.joined }
                assertEquals(listOf("EXTERNAL"), peer.mechanisms)
                assertFalse(peer.frames.any { it.command == "REGISTER" || it.command == "PRIVMSG" })
            }
        } finally {
            main { a.finish() }; instrumentation.waitForIdleSync(); devices.forget(identity)
            if (original != null) record.writeBytes(original) else record.delete()
        }
    }
    @Test fun closedSignupReportsFailureWithoutJoiningOrSavingDevice() {
        val a = start(); val remembered = a.session.devices.remembered()
        Peer().use { peer ->
            try {
                main {
                    a.session.connector = { _, _ -> Socket("127.0.0.1", peer.server.localPort) }
                    submit(a, "/register"); assertEquals(IrcNavigation.Page.REGISTER, a.session.navigation.page)
                    submit(a, "closedfixture"); submit(a, "never-sent-fixture-password")
                }
                waitFor { a.session.connectionText.startsWith("Disconnected") }
                assertTrue(a.session.connectionText.contains("Registration is closed"))
                assertEquals(remembered, a.session.devices.remembered())
                assertFalse(peer.frames.any { it.command in listOf("REGISTER", "JOIN", "PRIVMSG") })
                assertFalse(a.session.state.buffers.any { b -> b.lines.any { it.text.contains("never-sent-fixture-password") } })
                capture(a, "signup-closed")
            } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
        }
    }
    @Test fun signupPasswordCancellationDoesNotCreateConnectionOrReplaceDraft() {
        val a = start(); val remembered = a.session.devices.remembered()
        try {
            main {
                input(a).setText("signup cancel draft"); submit(a, "/register"); submit(a, "fixture")
                input(a).setText("cancel-signup-secret"); a.onBackPressedDispatcher.onBackPressed()
                assertEquals("signup cancel draft", input(a).text.toString()); assertNull(a.session.client)
                assertEquals(remembered, a.session.devices.remembered())
                assertEquals(0, a.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        } finally { main { a.finish() }; instrumentation.waitForIdleSync() }
    }
    @Test fun largestLauncherFontInNarrowFloatingPaneKeepsAccessibleActionsAndComposer() {
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val original = LauncherSettings.get(Ui.font_size_offset)
        var a: IrcActivity? = null
        try {
            LauncherSettings.set(context, Ui.font_size_offset, "8")
            val activity = start(); a = activity
            main {
                val root = activity.window.decorView
                root.findViewWithTag<View>("irc-window-toggle").performClick()
                repeat(30) { root.findViewWithTag<View>("irc-resize-corner").performLongClick() }
                submit(activity, "/login fixture"); activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(Tuils.getTypeface(activity), input(activity).typeface)
                assertEquals(LauncherFontScale.terminalTextSizeSp() * activity.resources.displayMetrics.scaledDensity, input(activity).textSize, .1f)
            }
            instrumentation.waitForIdleSync()
            main {
                val minimum = Tuils.dpToPx(activity, 48)
                assertTrue(input(activity).height >= minimum)
                assertTrue(activity.findViewById<View>(R.id.irc_send).height >= minimum)
                assertTrue(activity.findViewById<View>(R.id.irc_scroll).height > 0)
                submit(activity, "/help")
            }
            instrumentation.waitForIdleSync()
            main {
                val minimum = Tuils.dpToPx(activity, 48)
                val all = find(activity.window.decorView, "All commands")!!
                assertTrue(all.height >= minimum); assertTrue(all.isFocusable); assertTrue(all.contentDescription.isNotBlank())
                assertFalse(input(activity).isEnabled)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.irc_prompt_row).visibility)
                assertTrue(activity.findViewById<View>(R.id.irc_scroll).height > 0)
            }
            capture(activity, "narrow-large-font")
        } finally { main { a?.finish(); LauncherSettings.set(context, Ui.font_size_offset, original) }; instrumentation.waitForIdleSync() }
    }
}
