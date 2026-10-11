package ohi.andre.consolelauncher.irc

import org.junit.Assert.*
import org.junit.Test

class IrcProtocolTest {
    @Test fun parsesTaggedMessageWithoutDisplayingTransportIdentity() {
        val p = IrcMessage.parse("@time=2026-10-11T01:02:03Z;label=a\\sb\\:c :alice!user@host PRIVMSG #retui :hello world")
        assertEquals("alice", p.nick); assertEquals("hello world", p.params.last()); assertEquals("a b;c", p.tags["label"])
    }
    @Test fun parsesPingNumericEmptyTrailingAndMultiLineCapabilities() {
        assertEquals(listOf("token"), IrcMessage.parse("PING :token").params)
        assertEquals("001", IrcMessage.parse(":server 001 nick :welcome").command)
        assertEquals(listOf("nick", "LS", "*", "sasl=PLAIN echo-message"), IrcMessage.parse(":server CAP nick LS * :sasl=PLAIN echo-message").params)
        assertEquals(listOf("#c", ""), IrcMessage.parse("TOPIC #c :").params)
    }
    @Test fun framingRejectsInjectionAndCountsUtf8Bytes() {
        assertEquals("PRIVMSG #retui :hello\r\n", IrcProtocol.line("PRIVMSG", listOf("#retui"), "hello"))
        for (bad in listOf("hello\r\nJOIN #evil", "a\u0000b")) assertThrows(IllegalArgumentException::class.java) { IrcProtocol.line("PRIVMSG", listOf("#retui"), bad) }
        assertThrows(IllegalArgumentException::class.java) { IrcProtocol.line("JOIN", listOf("#c #evil")) }
        assertThrows(IllegalArgumentException::class.java) { IrcProtocol.line("PRIVMSG", listOf("#retui"), "😀".repeat(125)) }
        assertFalse(IrcProtocol.validUnicode("\uD800")); assertTrue(IrcProtocol.validUnicode("😀"))
    }
    @Test fun rfc1459MappingAndDisplayAreSafe() {
        assertEquals("nick{|}~", IrcProtocol.fold("Nick[\\]^"))
        assertEquals("nick[\\]^", IrcProtocol.fold("Nick[\\]^", "ascii"))
        assertEquals("red text", IrcProtocol.display("\u000304red\u000f \u001b[31mtext\u001b[0m"))
    }
    @Test fun saslBase64IsStandardsCompatible() {
        for (count in 0..1024) {
            val bytes = ByteArray(count) { (it * 17).toByte() }
            assertEquals(java.util.Base64.getEncoder().encodeToString(bytes), IrcProtocol.base64(bytes))
        }
    }
    @Test fun boundedParserRejectsMalformedAndOversizeFrames() {
        for (bad in listOf("", "@x", ":prefix", "1BAD #c", "PRIVMSG #c :a\nb", "x".repeat(8193))) assertThrows(IllegalArgumentException::class.java) { IrcMessage.parse(bad) }
    }
}
