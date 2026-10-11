package ohi.andre.consolelauncher.community

import org.junit.Assert.*
import org.junit.Test

class CommunityEntryTest {
    @Test fun ircIsDiscoverableNativeClientAndCommunityRemainsSeparate() {
        val type = runCatching { Class.forName("ohi.andre.consolelauncher.commands.main.raw.irc") }.getOrNull()
        assertNotNull("Native IRC entry is missing", type)
        val entry = type!!.getConstructor().newInstance()
        assertFalse(entry is ohi.andre.consolelauncher.commands.main.raw.community)
        assertEquals(ohi.andre.consolelauncher.R.string.help_irc, (entry as ohi.andre.consolelauncher.commands.main.raw.irc).helpRes())
        assertEquals(ohi.andre.consolelauncher.R.string.help_community, ohi.andre.consolelauncher.commands.main.raw.community().helpRes())
        val source = java.io.File("src/main/java/ohi/andre/consolelauncher/commands/main/raw/irc.kt").readText()
        assertTrue(source.contains("IrcActivity.open(pack.context)")); assertFalse(source.contains("CommunityActivity"))
        val fallback = java.io.File("src/main/java/ohi/andre/consolelauncher/commands/CommandGroup.kt").readText()
        assertTrue(fallback.contains("\"irc\""))
        assertTrue(fallback.contains("\"community\""))
    }
}
