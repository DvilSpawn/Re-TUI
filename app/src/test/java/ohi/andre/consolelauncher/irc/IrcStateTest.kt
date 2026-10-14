package ohi.andre.consolelauncher.irc

import org.junit.Assert.*
import org.junit.Test

class IrcStateTest {
    private fun IrcState.event(line: String) = receive(IrcMessage.parse(line))
    @Test fun channelHistoryRestoresRelayedMessagesAndMergesWithLiveMessagesInOrder() {
        val s = IrcState(); s.nickname = "dvil"; s.event(":dvil!u@host JOIN #retui")
        s.event("@msgid=live;time=2026-10-13T15:02:00.000Z :alice!u@host PRIVMSG #retui :newer")
        s.event(":server BATCH +history chathistory #retui")
        s.event("@batch=history;msgid=old;time=2026-10-13T15:01:00Z :komi-_-xd/discord!u@host PRIVMSG #retui :🤤🤤🤤")
        s.event("@batch=history;msgid=middle;time=2026-10-13T15:01:00.100Z :komi-_-xd/discord!u@host PRIVMSG #retui :@dvil hello")
        s.event("@batch=history;msgid=live;time=2026-10-13T15:02:00.000Z :alice!u@host PRIVMSG #retui :newer")
        s.event(":server BATCH -history")
        assertEquals(listOf("🤤🤤🤤", "@dvil hello", "newer"), s.active.lines.filter { it.body.isNotEmpty() }.map { it.body })
        assertEquals("<komi-_-xd/discord> 🤤🤤🤤", s.active.lines.first { it.body == "🤤🤤🤤" }.text)
        s.disconnected(); s.event(":dvil!u@host JOIN #retui")
        s.event(":server BATCH +again chathistory #retui")
        s.event("@batch=again;msgid=old;time=2026-10-13T15:01:00Z :komi-_-xd/discord!u@host PRIVMSG #retui :🤤🤤🤤")
        s.event("@batch=again;msgid=middle;time=2026-10-13T15:01:00.100Z :komi-_-xd/discord!u@host PRIVMSG #retui :@dvil hello")
        s.event("@batch=again;msgid=live;time=2026-10-13T15:02:00.000Z :alice!u@host PRIVMSG #retui :newer")
        s.event(":server BATCH -again")
        assertEquals(3, s.active.lines.count { it.body.isNotEmpty() })
    }
    @Test fun historyDoesNotChangeMembershipRaiseUnreadOrConfirmAnOldMatchingSend() {
        val s = IrcState(); s.nickname = "dvil"; val b = s.buffer("#retui")
        s.local("#retui", "same body", true)
        s.event(":server BATCH +h chathistory #retui")
        s.event("@batch=h :dvil!u@host JOIN #retui")
        s.event("@batch=h;msgid=old;time=2026-10-13T15:01:00Z :dvil!u@host PRIVMSG #retui :same body")
        s.event("@batch=h;msgid=other;time=2026-10-13T15:01:01Z :alice!u@host PRIVMSG #retui :hello dvil")
        s.event("@batch=h :alice!u@host PRIVMSG #other :wrong target")
        s.event(":server BATCH -h")
        assertFalse(b.joined); assertEquals(1, s.current); assertEquals(0, b.unread); assertFalse(b.highlight)
        assertTrue(b.lines.first().pending); assertEquals(3, b.lines.size); assertEquals(2, s.buffers.size)
    }
    @Test fun deduplicationUsesMessageIdentityAndTimeWithoutCollapsingRepeatedMessages() {
        val s = IrcState()
        s.event("@msgid=one;time=2026-10-13T15:01:00Z :alice!u@host PRIVMSG #c :same")
        s.event("@msgid=two;time=2026-10-13T15:01:00Z :alice!u@host PRIVMSG #c :same")
        s.event("@msgid=one;time=2026-10-13T15:01:00Z :alice!u@host PRIVMSG #c :same")
        s.event("@time=2026-10-13T15:01:01Z :alice!u@host PRIVMSG #c :without id")
        s.event("@time=2026-10-13T15:01:01.000Z :alice!u@host PRIVMSG #c :without id")
        repeat(2) { s.event(":alice!u@host PRIVMSG #c :without tags") }
        assertEquals(5, s.buffer("#c").lines.size)
    }
    @Test fun historyBeforeLiveEchoDoesNotLeaveDuplicatePendingRowsAndStaysBounded() {
        val s = IrcState(); s.nickname = "dvil"; s.local("#c", "hello", true)
        s.event(":server BATCH +h chathistory #c")
        s.event("@batch=h;msgid=echo;time=2026-10-13T15:01:00Z :dvil!u@host PRIVMSG #c :hello")
        s.event("@msgid=echo;time=2026-10-13T15:01:00Z :dvil!u@host PRIVMSG #c :hello")
        assertEquals(1, s.buffer("#c").lines.size); assertFalse(s.buffer("#c").lines.single().pending)
        repeat(250) { s.event("@batch=h;msgid=m$it;time=2026-10-13T15:02:00Z :alice!u@host PRIVMSG #c :row $it") }
        assertEquals(200, s.buffer("#c").lines.size)
        s.disconnected()
        s.event("@batch=h;msgid=after :alice!u@host PRIVMSG #c :hello dvil")
        assertTrue(s.buffer("#c").highlight)
    }
    @Test fun historyFailureIsVisibleWithoutDisconnectingTheChannel() {
        val s = IrcState(); s.nickname = "dvil"; s.event(":dvil!u@host JOIN #retui")
        s.event(":server FAIL CHATHISTORY MESSAGE_ERROR LATEST #retui :Try again later")
        assertTrue(s.active.joined); assertTrue(s.buffers.first().lines.last().text.contains("Try again later"))
    }
    @Test fun serverRedactionErasesContentAndPreventsOverlappingHistoryFromRestoringIt() {
        val s = IrcState(); s.nickname = "dvil"
        s.event(":dvil!u@host JOIN #retui")
        s.event("@msgid=secret;time=2026-10-13T15:01:00Z :alice!u@host PRIVMSG #retui :fixture-secret")
        s.event(":mod!u@host REDACT #retui secret")
        assertTrue(s.active.lines.none { it.body.contains("fixture-secret") || it.text.contains("fixture-secret") })
        assertEquals("-!- Message deleted", s.active.lines.last().text)
        s.event(":server BATCH +h chathistory #retui")
        s.event("@batch=h;msgid=secret;time=2026-10-13T15:01:00Z :alice!u@host PRIVMSG #retui :fixture-secret")
        s.event(":server BATCH -h")
        assertTrue(s.active.lines.none { it.text.contains("fixture-secret") })
        s.event("@msgid=other :alice!u@host PRIVMSG #help :keep me")
        s.event(":mod!u@host REDACT #retui other")
        assertEquals("keep me", s.buffer("#help").lines.single().body)
        assertFalse("Wrong-channel deletions are ignored", "other" in s.active.deletedMessageIds)
        val join = s.active.lines.first()
        s.event(":mod!u@host REDACT #retui :")
        assertEquals(join, s.active.lines.first())
    }
    @Test fun completedReconnectHistoryRemovesOfflineDeletionsButKeepsInterleavedLiveMessages() {
        val s = IrcState(); s.nickname = "dvil"
        s.event(":dvil!u@host JOIN #retui")
        s.event("@msgid=gone :alice!u@host PRIVMSG #retui :deleted while closed")
        s.event("@msgid=kept :alice!u@host PRIVMSG #retui :retained")
        s.disconnected(); s.event(":dvil!u@host JOIN #retui")
        s.event("@msgid=new :alice!u@host PRIVMSG #retui :arrived after join")
        s.event(":server BATCH +h chathistory #retui")
        s.event("@batch=h;msgid=kept :alice!u@host PRIVMSG #retui :retained")
        assertTrue(s.active.lines.any { it.messageId == "gone" })
        s.event(":server BATCH -h")
        assertEquals(listOf("retained", "arrived after join"), s.active.lines.filter { it.body.isNotEmpty() }.map { it.body })
        s.disconnected(); s.event(":dvil!u@host JOIN #retui")
        s.event(":server BATCH +empty chathistory #retui"); s.event(":server BATCH -empty")
        assertTrue(s.active.lines.none { it.body.isNotEmpty() })
    }
    @Test fun interruptedHistoryRefreshNeverDiscardsCachedMessagesAndTombstonesStayBounded() {
        val s = IrcState(); s.nickname = "dvil"; s.event(":dvil!u@host JOIN #retui")
        s.event("@msgid=kept :alice!u@host PRIVMSG #retui :retained")
        s.disconnected(); s.event(":dvil!u@host JOIN #retui")
        s.event(":server BATCH +h chathistory #retui"); s.disconnected()
        assertTrue(s.active.lines.any { it.body == "retained" })
        repeat(250) { s.event(":mod!u@host REDACT #retui absent$it") }
        assertEquals(200, s.active.deletedMessageIds.size)
    }
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
}
