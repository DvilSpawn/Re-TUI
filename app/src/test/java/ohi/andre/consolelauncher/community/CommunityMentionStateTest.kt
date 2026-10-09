package ohi.andre.consolelauncher.community

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class CommunityMentionStateTest {
    @Test fun earliestMentionDeadlineBoundsForegroundExpiryEvenWhenNetworkIsBusy() {
        val type = CommunityMentionState::class.java
        val method = type.methods.firstOrNull { it.name == "expiryDelay" }
        assertNotNull("Mention expiry timer is missing", method)
        val now = System.currentTimeMillis()
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(now + 5000))
        val state = CommunityMentionState(JSONObject().put("items", JSONArray(listOf(JSONObject().put("id", "soon").put("channel", "general").put("expires_at", date)))))
        val delay = method!!.invoke(state) as Long
        assertTrue(delay in 1..5000)
    }
    @Test fun readIdsSurvivePagingButGeneralSeenMetadataExpires() {
        val record = JSONObject().put("id", "forum").put("channel", "bugs").put("expires_at", "")
        val state = CommunityMentionState(JSONObject())
        state.replace(listOf(record)); state.read("forum")
        state.replace(emptyList())
        val restored = CommunityMentionState(state.toJson()); restored.replace(listOf(record))
        assertEquals("Paging must not resurrect read indicators", 0, restored.unread("bugs"))
    }
    @Test fun metadataExpiresWithoutErasingForumMentionsOrLeakingBetweenAccounts() {
        val now = System.currentTimeMillis()
        fun date(time: Long) = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(time))
        fun record(id: String, channel: String, expires: String) = JSONObject().put("id", id).put("channel", channel).put("expires_at", expires)
        val type = runCatching { Class.forName("ohi.andre.consolelauncher.community.CommunityMentionState") }.getOrNull()
        assertNotNull("Bounded mention state is missing", type)
        val saved = JSONObject().put("items", JSONArray(listOf(record("expired", "general", date(now - 1)), record("alive", "general", date(now + 60000)), record("forum", "bugs", ""))))
        val state = type!!.getConstructor(JSONObject::class.java).newInstance(saved)
        val json = type.getMethod("toJson").invoke(state) as JSONObject
        assertEquals(listOf("alive", "forum"), (0 until json.getJSONArray("items").length()).map { json.getJSONArray("items").getJSONObject(it).getString("id") })
        type.getMethod("read", String::class.java).invoke(state, "forum")
        assertEquals(1, type.getMethod("unread", String::class.java).invoke(state, "general"))
        assertEquals(0, type.getMethod("unread", String::class.java).invoke(state, "bugs"))
        val many = (0..250).map { record("m$it", "bugs", "") }
        type.getMethod("replace", List::class.java).invoke(state, many)
        assertEquals(200, (type.getMethod("toJson").invoke(state) as JSONObject).getJSONArray("items").length())
    }
}
