package ohi.andre.consolelauncher.community

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommunityMessagingTest {
    private fun record(id: String = "m00000000000001", author: String = "alice", time: Long = System.currentTimeMillis() - 60000) = JSONObject()
        .put("id", id).put("author_handle", author).put("created", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(time))).put("body", "message")

    @Test fun nearbyMessagesGroupOnlyForSameAuthorAndLocalDay() {
        val now = System.currentTimeMillis() - 60000
        val first = record(time = now)
        assertTrue(CommunityMessagePresentation.grouped(first, record(time = now + 1000)))
        assertFalse(CommunityMessagePresentation.grouped(first, record(author = "bob", time = now + 1000)))
        assertFalse(CommunityMessagePresentation.grouped(first, record(time = now + 300001)))
        assertFalse(CommunityMessagePresentation.grouped(first, record(time = now - 1)))
        assertFalse(CommunityMessagePresentation.grouped(null, first))
        assertFalse(CommunityMessagePresentation.grouped(first, JSONObject().put("author_handle", "alice")))
        val beforeMidnight = JSONObject().put("author_handle", "alice").put("created", "2026-10-10 23:59:59.000Z")
        val afterMidnight = JSONObject().put("author_handle", "alice").put("created", "2026-10-11 00:00:01.000Z")
        val original = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            assertFalse(CommunityMessagePresentation.grouped(beforeMidnight, afterMidnight))
        } finally { java.util.TimeZone.setDefault(original) }
    }
    @Test fun previewIsBoundedWithoutSplittingUnicodeOrLeakingMetadata() {
        val message = record().put("body", "😀".repeat(101)).put("ip", "198.51.100.1")
        assertEquals("😀".repeat(100) + "…", CommunityMessagePresentation.preview(message))
        assertEquals("hello world", CommunityMessagePresentation.preview(record().put("body", "hello\n  world")))
    }
    @Test fun readingPositionPersistsButLatestAndLogoutResetFollowMode() {
        val message = record()
        val state = CommunityState().apply {
            merge(listOf(message), emptySet()); following = false; viewAnchor = message.getString("id"); viewOffset = 12
        }
        val restored = CommunityState(state.toJson())
        assertFalse(restored.following); assertEquals(state.viewAnchor, restored.viewAnchor); assertEquals(12, restored.viewOffset)
        restored.merge(listOf(message), emptySet(), true)
        assertTrue(restored.following); assertFalse(restored.readingOlder)
        restored.focusMessage(message)
        assertFalse(restored.following); assertTrue(restored.readingOlder)
        restored.clear()
        assertTrue(restored.following); assertEquals("", restored.viewAnchor); assertEquals(0, restored.viewOffset)
    }
    @Test fun expiredAnchorsCannotKeepADeletedBodyOrDeliveryIdentity() {
        val message = record(time = System.currentTimeMillis() - 86_400_001L)
        val saved = JSONObject().put("messages", org.json.JSONArray().put(message))
            .put("view_anchor", message.getString("id")).put("view_offset", 900000)
        val state = CommunityState(saved)
        assertTrue(state.messages.isEmpty()); assertEquals("", state.viewAnchor); assertEquals(0, state.viewOffset)
    }
}
