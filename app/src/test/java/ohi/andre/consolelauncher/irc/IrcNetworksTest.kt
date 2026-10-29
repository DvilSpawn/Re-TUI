package ohi.andre.consolelauncher.irc

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IrcNetworksTest {
    @Test fun savedNetworkRoundTripsWithoutAnyPassword() {
        val network = IrcNetwork("libera", "Libera.Chat", "irc.libera.chat", 6697, "dvil", "dvil", true, listOf("#linux", "#retui"), true)
        val json = network.json()
        assertEquals(network, IrcNetwork.parse(JSONObject(json.toString())))
        assertFalse(json.toString().contains("password", true))
    }
    @Test fun malformedOrUnsafeEntriesAreDropped() {
        assertNull(IrcNetwork.parse(JSONObject("""{"id":"x","name":"X","host":"bad host","port":6697}""")))
        assertNull(IrcNetwork.parse(JSONObject("""{"id":"x","name":"X","host":"irc.example.org","port":70000}""")))
        assertNull(IrcNetwork.parse(JSONObject("""{"id":"x","name":"X","host":"irc.example.org","nickname":"has space"}""")))
        assertNull(IrcNetwork.parse(JSONObject("""{"name":"X","host":"irc.example.org"}""")))
        // Channel names that are not channels never reach a JOIN line.
        val parsed = IrcNetwork.parse(JSONObject("""{"id":"x","name":"X","host":"irc.example.org","channels":["#ok","nick","#bad,join","&local"]}"""))
        assertEquals(listOf("#ok", "&local"), parsed!!.channels)
        assertEquals(6697, parsed.port)
    }
    @Test fun customNetworksGetDistinctIdsAndPresetsUseTls() {
        assertEquals("irc.example.org", IrcNetwork.custom("irc.example.org", 6697, emptyList()).id)
        assertEquals("irc.example.org-2", IrcNetwork.custom("irc.example.org", 6697, listOf("irc.example.org")).id)
        assertTrue(IrcNetwork.PRESETS.all { it.port == 6697 && IrcNetwork.validHost(it.host) })
        assertEquals(IrcNetwork.PRESETS.size, IrcNetwork.PRESETS.map { it.id }.toSet().size)
        assertTrue(IrcProtocol.nick(IrcNetwork.guestNickname()))
    }
}
