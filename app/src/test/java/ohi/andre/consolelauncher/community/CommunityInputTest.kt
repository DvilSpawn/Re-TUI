package ohi.andre.consolelauncher.community

import org.junit.Assert.*
import org.junit.Test

class CommunityInputTest {
    private fun parse(text: String): Any? = try {
        Class.forName("ohi.andre.consolelauncher.community.CommunityInput")
            .getMethod("parse", String::class.java).invoke(null, text)
    } catch (_: ClassNotFoundException) { null }

    @Test fun moderateIsPrivateLocalCommandWithValidatedBody() {
        assertEquals(CommunityInput.Command("moderate", "private; text"), CommunityInput.parse("/moderate private; text"))
        assertTrue(CommunityInput.parse("/moderate") is CommunityInput.Rejected)
        assertTrue(CommunityInput.parse("/moderate " + "x".repeat(501)) is CommunityInput.Rejected)
        assertEquals(CommunityInput.Message("/moderate literal"), CommunityInput.parse("//moderate literal"))
    }
    @Test fun slashInputNeverFallsThroughToPosting() {
        assertEquals("Command(name=exit, argument=)", parse("/exit").toString())
        assertEquals("Message(body=exit)", parse("exit").toString())
        assertEquals("Message(body=/exit)", parse("//exit").toString())
        assertEquals("Rejected(reason=Unknown slash command; use /help.)", parse("/say hello").toString())
        assertEquals("Rejected(reason=Start slash commands at the beginning of input.)", parse(" /exit").toString())
        assertEquals("Command(name=report, argument=abc123)", parse("/report abc123").toString())
        assertTrue(parse("/login secret")!!.toString().startsWith("Rejected"))
    }

    @Test fun escapedSlashCountsLiteralBodyNotEscapePrefix() {
        assertTrue(parse("//" + "x".repeat(499)) is CommunityInput.Message)
        assertTrue(parse("//" + "x".repeat(500)) is CommunityInput.Rejected)
    }

    @Test fun bodyBoundsUseUnicodeAndRejectEmptyOrMalformedInput() {
        for (text in listOf("", " \n", "x".repeat(501), "\uD800"))
            assertTrue(text.length.toString(), parse(text) is CommunityInput.Rejected)
        assertTrue(parse("😀".repeat(500)) is CommunityInput.Message)
        assertTrue(parse("  literal\ntext  ") is CommunityInput.Message)
    }

    @Test fun plainTextIsLiteralNotLauncherSyntax() {
        val result = parse("settings; reboot")
        assertNotNull("Isolated community parser is missing", result)
        assertEquals("Message(body=settings; reboot)", result.toString())
    }
}
