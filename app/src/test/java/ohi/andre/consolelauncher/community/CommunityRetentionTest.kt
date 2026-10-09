package ohi.andre.consolelauncher.community

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CommunityRetentionTest {
    private fun record(id: String, created: Long) = JSONObject().put("id", id)
        .put("created", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.ofEpochMilli(created)))
        .put("updated", Instant.now().toString()).put("author", "peer").put("author_handle", "peer").put("body", "cached $id")

    @Test fun cutoffFutureInvalidAndArrivalExpiryApplyOfflineAtServerCreationTime() {
        var now = Instant.parse("2026-10-09T12:00:00.000Z").toEpochMilli()
        val state = CommunityState(JSONObject()) { now }
        state.signIn(CommunityClient.Account("me", "me", "token"))
        val fresh = record("fresh", now - 86400000 + 1)
        state.merge(listOf(fresh, record("cutoff", now - 86400000), record("future", now + 1), record("invalid", now).put("created", "2026-02-30 12:00:00.000Z")), emptySet())
        assertEquals(listOf("fresh"), state.messages.map { it.getString("id") })
        state.observeLatest(listOf(fresh))
        val arrival = record("arrival", now - 86400000 + 2)
        state.observeLatest(listOf(fresh, arrival))
        assertEquals(setOf("arrival"), state.newArrivalIds)
        now += 2
        assertTrue(state.expire())
        assertTrue(state.messages.isEmpty()); assertTrue(state.newArrivalIds.isEmpty())
        val saved = state.toJson()
        assertEquals(0, saved.getJSONArray("arrivals").length())
        assertFalse(saved.has("newest_observed"))
        assertFalse(state.expire())
    }

    @Test fun foregroundExpiryDeadlineIsIndependentOfNetworkBackoffAndBusyRequests() {
        var now = System.currentTimeMillis()
        val state = CommunityState(JSONObject()) { now }
        state.merge(listOf(record("last-ms", now - 86400000 + 1)), emptySet())
        assertEquals(1L, state.expiryDelay())
        now++
        state.expire()
        assertEquals(15000L, state.expiryDelay())
    }

    @Test fun recreationAndPersistencePruneExpiredHistoryButKeepLiveCursorReadMarkersAndBlocks() {
        var now = System.currentTimeMillis()
        val state = CommunityState(JSONObject()) { now }
        state.signIn(CommunityClient.Account("me", "me", "token"))
        val nearCutoff = record("old", now - 86400000 + 1)
        val live = record("live", now - 1000)
        state.merge(listOf(live), emptySet()); state.observeLatest(listOf(live))
        state.merge(listOf(nearCutoff), emptySet()); state.seen.addAll(listOf("old", "live")); state.blocked.add("peer")
        val saved = state.toJson()
        now++
        val restored = CommunityState(saved) { now }
        assertEquals(listOf("live"), restored.messages.map { it.getString("id") })
        assertEquals(setOf("live"), restored.seen); assertEquals(setOf("peer"), restored.blocked)
        assertTrue(restored.toJson().getBoolean("reading_history"))
        assertEquals(live.getString("created"), restored.toJson().getJSONObject("newest_observed").getString("created"))
        now += 86400000
        assertEquals(0, restored.toJson().getJSONArray("messages").length())
        assertTrue(restored.messages.isEmpty())
    }

    @Test fun legacyArrivalWithoutCreationCannotRemainUnreadIndefinitely() {
        val saved = JSONObject().put("arrivals", JSONArray(listOf(JSONObject().put("id", "legacy").put("author", "peer"))))
        val restored = CommunityState(saved)
        assertTrue(restored.newArrivalIds.isEmpty())
        assertEquals(0, restored.toJson().getJSONArray("arrivals").length())
    }

    @Test fun missingCreationTimestampIsDiscardedWithoutMakingTheWholeAccountUnreadable() {
        val saved = CommunityState().apply { signIn(CommunityClient.Account("me", "me", "token")); draft = "kept" }.toJson()
            .put("messages", JSONArray(listOf(JSONObject().put("id", "invalid").put("body", "no timestamp"))))
        val restored = CommunityState(saved)
        assertTrue(restored.messages.isEmpty()); assertEquals("me", restored.account!!.id); assertEquals("kept", restored.draft)
    }

    @Test fun restoredExpiredBodiesAreRemovedWithoutReleasingUncertainPostOrDraft() {
        val state = CommunityState().apply {
            signIn(CommunityClient.Account("me", "me", "token"))
            draft = "original"; beginPost("original"); draft = "new draft"; blocked.add("peer")
        }
        val saved = state.toJson().put("messages", JSONArray(listOf(record("expired", System.currentTimeMillis() - 86400001))))
            .put("reading_history", true).put("seen", JSONArray(listOf("expired")))
        val restored = CommunityState(saved)
        assertTrue("Restoration must not render expired cached bodies offline", restored.messages.isEmpty())
        assertEquals(state.pendingId, restored.pendingId); assertEquals("original", restored.pendingBody)
        assertEquals("new draft", restored.draft); assertEquals(setOf("peer"), restored.blocked)
        assertTrue(restored.toJson().getBoolean("reading_history")); assertTrue(restored.seen.isEmpty())
    }
}
