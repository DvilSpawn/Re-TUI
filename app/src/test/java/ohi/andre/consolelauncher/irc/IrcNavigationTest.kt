package ohi.andre.consolelauncher.irc

import org.junit.Assert.*
import org.junit.Test

class IrcNavigationTest {
    @Test fun messageDeletionUsesServerIdWithoutOptimisticallyRemovingContent() {
        val s = IrcState(); s.nickname = "me"; s.receive(IrcMessage.parse(":me!u@host JOIN #retui"))
        s.receive(IrcMessage.parse("@msgid=message-1 :alice!u@host PRIVMSG #retui :original"))
        val frames = mutableListOf<String>(); val a = IrcActions(s) { c, p, t -> frames.add(IrcProtocol.line(c, p, t)) }
        a.deleteMessage("#retui", "message-1")
        assertEquals(listOf("REDACT #retui message-1\r\n"), frames)
        assertTrue(s.active.lines.any { it.body == "original" })
        s.receive(IrcMessage.parse(":s FAIL REDACT REDACT_FORBIDDEN #retui message-1 :Permission denied"))
        assertTrue(s.active.lines.any { it.body == "original" }); assertTrue(s.active.lines.last().text.contains("Permission denied"))
        assertThrows(IllegalStateException::class.java) { a.deleteMessage("#retui", "missing") }
        assertThrows(IllegalArgumentException::class.java) { a.deleteMessage("#retui", "bad id") }
    }
    @Test fun localViewsPreserveDraftPositionAndTrackHiddenMessages() {
        val s = IrcState(); s.nickname = "me"; val b = s.buffer("#retui"); s.switch("#retui")
        b.draft = "unfinished"; b.scroll = 144
        val n = IrcNavigation(s); n.open(IrcNavigation.Page.CHANNELS); n.text = "#help"
        s.receive(IrcMessage.parse(":alice!u@host PRIVMSG #retui :hello me"))
        assertEquals("unfinished", b.draft); assertEquals(144, b.scroll); assertEquals(1, b.unread); assertTrue(b.highlight)
        n.open(IrcNavigation.Page.HELP); assertEquals(1, b.unread)
        n.close(); assertEquals(0, b.unread); assertEquals("unfinished", b.draft); assertEquals(144, b.scroll)
    }
    @Test fun navigationSearchUsesIrcCaseMappingAndNeverCreatesBuffers() {
        val s = IrcState(); val n = IrcNavigation(s); n.open(IrcNavigation.Page.DIRECTORY); n.text = "#FOO["
        assertTrue(n.matches("#foo{")); assertEquals(1, s.buffers.size); assertTrue(s.active.lines.isEmpty()); assertEquals("", s.active.draft)
    }
    @Test fun sharedJoinActionsWaitForServerAndAlreadyJoinedSwitchesWithoutSending() {
        val s = IrcState(); s.nickname = "me"; val frames = mutableListOf<String>()
        val a = IrcActions(s) { command, params, body -> frames.add(IrcProtocol.line(command, params, body)) }
        a.join("#retui"); assertEquals("#retui", s.joining); assertFalse(s.buffer("#retui").joined); assertEquals(1, s.current)
        s.receive(IrcMessage.parse(":me!u@host JOIN #retui")); assertTrue(s.active.joined); assertEquals("", s.joining)
        s.switch("1"); a.join("#retui"); assertEquals(1, frames.size); assertEquals("#retui", s.active.name)
    }
    @Test fun failedJoinLeavesConversationAndAllowsRetry() {
        val s = IrcState(); val a = IrcActions(s) { _, _, _ -> }
        a.join("#private"); s.receive(IrcMessage.parse(":server 477 retui #private :Registered account required"))
        assertEquals("", s.joining); assertTrue(s.joinError.contains("Registered")); assertFalse(s.buffer("#private").joined)
        a.join("#private"); assertEquals("", s.joinError)
    }
    @Test fun directoryIsBoundedDeduplicatesAndReportsEndAndRejection() {
        val s = IrcState(); s.beginDirectory()
        repeat(1001) { s.receive(IrcMessage.parse(":server 322 retui #c$it 12 :topic")) }
        assertEquals(1000, s.directory.size); assertTrue(s.directoryTruncated); assertTrue(s.directoryLoading)
        s.receive(IrcMessage.parse(":server 322 retui #C0 14 :new topic")); assertEquals(14, s.directory.first().users)
        s.receive(IrcMessage.parse(":server 323 retui :End of list")); assertFalse(s.directoryLoading)
        s.beginDirectory(); s.receive(IrcMessage.parse(":server 263 retui LIST :Try again later"))
        assertFalse(s.directoryLoading); assertTrue(s.directoryError.contains("Try again")); assertTrue(s.directory.isEmpty())
    }
    @Test fun disconnectedDirectoryStopsLoadingAndQueryNeverSends() {
        val s = IrcState(); val frames = mutableListOf<String>(); val a = IrcActions(s) { command, _, _ -> frames.add(command) }
        a.directory(); a.directory(); assertEquals(listOf("LIST"), frames)
        s.disconnected(); assertFalse(s.directoryLoading); assertTrue(s.directoryError.isNotBlank())
        a.query("alice"); assertEquals("alice", s.active.name); assertEquals(listOf("LIST"), frames)
        assertThrows(IllegalArgumentException::class.java) { a.query("NickServ") }
    }
    @Test fun authenticatedDefaultNetworkJoinsMainChannelOnlyAndNoPendingResend() {
        val s = IrcState(); val frames = mutableListOf<String>(); val a = IrcActions(s) { c, p, t -> frames.add(IrcProtocol.line(c, p, t)) }
        a.authenticated(false); assertTrue(frames.isEmpty())
        s.local("#retui", "old message", true); s.disconnected(); a.authenticated(true)
        assertEquals(listOf("JOIN #retui\r\n"), frames); assertFalse(s.buffer("#retui").lines.single().pending)
    }
    @Test fun restoredMembershipDoesNotStealRequestedConversationOrReadHiddenMessages() {
        for (channels in listOf(listOf("#retui", "#help"), listOf("#help", "#retui"))) {
            val s = IrcState(); s.nickname = "me"
            val a = IrcActions(s) { _, _, _ -> }
            a.authenticated(true)
            channels.forEach { s.receive(IrcMessage.parse(":me!u@host JOIN $it")) }
            assertEquals("#retui", s.active.name)
            assertTrue(s.buffer("#help").joined)
            s.receive(IrcMessage.parse(":alice!u@host PRIVMSG #help :hello"))
            s.receive(IrcMessage.parse(":me!u@host JOIN #help"))
            assertEquals("#retui", s.active.name)
            assertEquals(1, s.buffer("#help").unread)
            a.join("#help")
            assertEquals("#help", s.active.name)
            assertEquals(0, s.active.unread)
        }
    }
}
