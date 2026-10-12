package ohi.andre.consolelauncher.irc

import org.junit.Assert.*
import org.junit.Test

class IrcStateTest {
    private fun IrcState.event(line: String) = receive(IrcMessage.parse(line))
    @Test fun servicePasswordEchoNeverEntersScrollback() {
        val s = IrcState(); s.nickname = "dvil"
        s.event(":dvil!u@host PRIVMSG NickServ :REGISTER secret")
        assertEquals(1, s.buffers.size); assertTrue(s.active.lines.isEmpty())
        s.event(":NickServ!service@host NOTICE dvil :Registration successful")
        assertTrue(s.active.lines.single().text.contains("Registration successful"))
    }
    @Test fun rejectedChannelMessageIsVisibleAndNeverMarkedDelivered() {
        val s = IrcState(); s.nickname = "dvil"; s.local("#c", "hello", true); s.switch("#c")
        s.event(":server 404 dvil #c :Cannot send to channel")
        assertTrue(s.active.lines.last().text.contains("Server rejected")); assertFalse(s.active.lines.first().pending)
        assertTrue(s.active.lines.first().text.contains("not confirmed")); assertTrue(s.buffers.first().unread > 0)
    }
    @Test fun joinCreatesAndSelectsNumberedChannelWindow() {
        val s = IrcState(); s.nickname = "dvil"
        s.event(":dvil!ident@cloak JOIN :#retui")
        assertEquals(2, s.current); assertTrue(s.active.joined); assertEquals("#retui", s.active.name)
        s.beginJoin("#help")
        s.event(":dvil!ident@cloak JOIN #help")
        assertEquals(3, s.current); s.switch("2"); assertEquals("#retui", s.active.name); s.switch("next"); assertEquals("#help", s.active.name)
    }
    @Test fun remoteChannelsHaveUnreadAndHighlightsWithoutSwitching() {
        val s = IrcState(); s.nickname = "dvil"; s.buffer("#retui")
        s.event(":alice!secret@198.51.100.1 PRIVMSG #retui :hello dvil")
        assertEquals(1, s.current); val b = s.buffer("#retui"); assertEquals(1, b.unread); assertTrue(b.highlight)
        assertEquals("<alice> hello dvil", b.lines.single().text)
        s.switch("#retui"); assertEquals(0, b.unread); assertFalse(b.highlight)
    }
    @Test fun privateMessagesAndOwnServerEchoRouteAndDeduplicate() {
        val s = IrcState(); s.nickname = "dvil"
        s.local("alice", "hello", true)
        s.event(":dvil!u@host PRIVMSG alice :hello")
        assertEquals(1, s.buffer("alice").lines.size); assertFalse(s.buffer("alice").lines.single().pending)
        assertEquals("<dvil> hello", s.buffer("alice").lines.single().text)
        s.event(":alice!u@host PRIVMSG dvil :reply")
        assertEquals(2, s.buffer("alice").lines.size); assertTrue(s.buffer("alice").highlight)
    }
    @Test fun partKickAndNickMaintainMembership() {
        val s = IrcState(); s.nickname = "dvil"; s.event(":dvil!x JOIN #c"); s.event(":server 353 dvil = #c :@dvil +alice bob")
        assertTrue("alice" in s.active.names); s.event(":alice!x NICK :ally"); assertTrue("ally" in s.active.names)
        s.event(":bob!x QUIT :leaving"); assertFalse("bob" in s.active.names)
        s.event(":ally!x KICK #c dvil :bye"); assertFalse(s.active.joined); s.closeActive(); assertEquals(1, s.current)
    }
    @Test fun failureKeepsDraftAndNeverPretendsPendingWasDelivered() {
        val s = IrcState(); s.buffer("#c").draft = "draft"; s.local("#c", "message", true); s.disconnected()
        assertEquals("draft", s.buffer("#c").draft); assertTrue(s.buffer("#c").lines.single().text.contains("delivery uncertain"))
    }
    @Test fun buffersAndScrollbackAreBoundedAndCtcpIsNotExecuted() {
        val s = IrcState(); repeat(300) { s.status("line $it") }; assertEquals(200, s.active.lines.size)
        repeat(31) { s.buffer("nick$it") }; assertThrows(IllegalStateException::class.java) { s.buffer("extra") }
        val t = IrcState(); t.event(":alice!x PRIVMSG retui :\u0001DCC SEND payload\u0001"); assertEquals(1, t.buffers.size)
    }
    @Test fun rfcCaseEquivalentChannelsShareOneBufferAndHelpKeepsLineBreaks() {
        val s = IrcState(); s.buffer("#Foo["); s.buffer("#foo{"); assertEquals(2, s.buffers.size)
        s.status("one\ntwo"); assertEquals(2, s.buffers.first().lines.size)
    }

    @Test fun chatHistoryBatchRestoresMessagesWithoutUnreadOrDuplicates() {
        val s = IrcState(); s.nickname = "dvil"; s.buffer("#retui"); s.switch("#retui")
        assertTrue(s.beginHistory("#retui"))
        s.event(":server BATCH +h chathistory #retui")
        s.event("@batch=h;msgid=abc;time=2026-10-12T10:00:00Z :alice!u@host PRIVMSG #retui :hello")
        s.event(":server BATCH -h")
        val b = s.buffer("#retui")
        assertTrue(b.historyLoaded); assertFalse(b.historyLoading); assertEquals(1, b.lines.size); assertEquals(0, b.unread)
        s.event("@batch=h;msgid=abc;time=2026-10-12T10:00:00Z :alice!u@host PRIVMSG #retui :hello")
        assertEquals(1, b.lines.size)
    }

    @Test fun historyIsRequestedOncePerJoinedBufferAndDisconnectClearsLoading() {
        val s = IrcState(); s.nickname = "dvil"; s.buffer("#retui").joined = true
        assertTrue(s.beginHistory("#retui")); assertFalse(s.beginHistory("#retui"))
        s.disconnected(); assertFalse(s.buffer("#retui").historyLoading)
    }
}
