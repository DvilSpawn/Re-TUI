package ohi.andre.consolelauncher.community

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommunityStorageBudgetTest {
    @Test fun explicitMentionNavigationCanFocusASourceOutsideTheBoundedWindow() {
        val state = CommunityState()
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val records = (1..200).map { JSONObject().put("id", "m" + it.toString().padStart(14, '0')).put("created", date).put("body", "body") }
        state.merge(records, emptySet()); state.draft = "keep"; val uuid = state.beginPost("keep")
        val target = JSONObject().put("id", "m99999999999999").put("created", date).put("body", "target")
        val method = CommunityState::class.java.methods.firstOrNull { it.name == "focusMessage" }
        assertNotNull("Specific mention source navigation is missing", method)
        method!!.invoke(state, target)
        assertEquals(listOf("target"), state.messages.map { it.getString("body") })
        assertEquals("keep", state.draft); assertEquals(uuid, state.pendingId)
    }
    @Test fun secureStoreAcceptsFullBoundedGeneralAndForumUnicodeWindows() {
        val field = CommunityStore::class.java.fields.firstOrNull { it.name == "MAX_STATE_BYTES" }
        assertNotNull("Expanded private state needs a bounded compatible storage budget", field)
        val body = "😀".repeat(500)
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        val records = (1..200).map { JSONObject().put("id", "m" + it.toString().padStart(14, '0')).put("created", date).put("body", body).put("title", "😀".repeat(120)) }
        val state = CommunityState().apply { merge(records, emptySet()); pane.threads = records; pane.replies = records }
        val bytes = state.toJson().toString().toByteArray(Charsets.UTF_8).size
        assertTrue("The full valid cache must be restorable", bytes + 28 <= field!!.getLong(null))
        assertTrue(field.getLong(null) <= 4_000_000)
    }
}
