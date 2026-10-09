package ohi.andre.consolelauncher.community

import org.junit.Assert.*
import org.junit.Test

class CommunityMentionInputTest {
    @Test fun punctuationBoundariesMatchTheServerMentionGrammar() {
        assertEquals("al", CommunityMentionInput.fragment("hi,@al", 6))
        assertNull(CommunityMentionInput.fragment("@@al", 4))
        assertNull(CommunityMentionInput.fragment("name.al@al", 10))
        assertEquals(listOf(3..8), CommunityMentionInput.ranges("hi,@alice", "alice"))
    }
    @Test fun highlightsOnlyCurrentRecipientHandlesNotEmailsOrLongerTokens() {
        val type = Class.forName("ohi.andre.consolelauncher.community.CommunityMentionInput")
        val method = type.methods.firstOrNull { it.name == "ranges" }
        assertNotNull("Recipient highlighting is missing", method)
        @Suppress("UNCHECKED_CAST")
        val ranges = method!!.invoke(type.getField("INSTANCE").get(null), "@alice hi mail@alice (@ALICE) @alice_more @bob", "alice") as List<IntRange>
        assertEquals(listOf(0..5, 22..27), ranges)
    }
    @Test fun completesOnlyTheCaretTokenWithoutChangingSurroundingText() {
        val type = runCatching { Class.forName("ohi.andre.consolelauncher.community.CommunityMentionInput") }.getOrNull()
        assertNotNull("Caret mention completion is missing", type)
        val instance = type!!.getField("INSTANCE").get(null)
        val fragment = type.getMethod("fragment", String::class.java, Int::class.javaPrimitiveType)
        assertEquals("al", fragment.invoke(instance, "hi @al rest", 6))
        assertNull(fragment.invoke(instance, "mail@al", 7))
        assertNull(fragment.invoke(instance, "@" + "a".repeat(25), 26))
        assertEquals("", fragment.invoke(instance, "hi @", 4))
        val replace = type.getMethod("insert", String::class.java, Int::class.javaPrimitiveType, String::class.java)
        assertEquals("hi @alice rest", replace.invoke(instance, "hi @al rest", 6, "alice"))
        assertEquals("hi @alice!", replace.invoke(instance, "hi @al!", 6, "alice"))
        assertEquals("mail@al", replace.invoke(instance, "mail@al", 7, "alice"))
    }
}
