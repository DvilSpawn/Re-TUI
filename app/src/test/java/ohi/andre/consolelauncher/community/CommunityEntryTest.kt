package ohi.andre.consolelauncher.community

import org.junit.Assert.*
import org.junit.Test

class CommunityEntryTest {
    @Test fun ircIsDiscoverableAliasForTheSameOptInPaneNotAnExternalClient() {
        val type = runCatching { Class.forName("ohi.andre.consolelauncher.commands.main.raw.irc") }.getOrNull()
        assertNotNull("IRC entry alias is missing", type)
        val entry = type!!.getConstructor().newInstance()
        assertTrue(entry is ohi.andre.consolelauncher.commands.main.raw.community)
        val fallback = java.io.File("src/main/java/ohi/andre/consolelauncher/commands/CommandGroup.kt").readText()
        assertTrue(fallback.contains("\"irc\""))
    }
}
