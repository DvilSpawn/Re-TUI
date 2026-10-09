package ohi.andre.consolelauncher.community

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommunityStateTest {
    private val baseTime = System.currentTimeMillis() - 3_600_000L
    private fun created(n: Int): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", java.util.Locale.ROOT).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(baseTime + n * 1000L))
    @Test fun latestArrivalMetadataSurvivesHistoryRecreationWithoutCachingExtraBodies() {
        fun message(n: Int, author: String = "peer") = JSONObject().put("id", "id$n").put("created", created(n))
            .put("author", author).put("author_handle", author).put("body", "body$n")
        val state = CommunityState(); state.signIn(CommunityClient.Account("me", "me", "token"))
        state.merge((201..400).map { message(it) }, emptySet()); state.observeLatest((351..400).map { message(it) })
        state.merge((101..200).map { message(it) }, emptySet())
        state.observeLatest(listOf(message(400), message(401), message(402, "me")))
        state.merge(listOf(message(401), message(402, "me")), emptySet())
        assertEquals(setOf("id401"), state.newArrivalIds)
        assertEquals("id101", state.messages.first().getString("id")); assertEquals(200, state.messages.size)
        val saved = state.toJson()
        assertFalse(saved.getJSONArray("arrivals").getJSONObject(0).has("body"))
        val restored = CommunityState(saved)
        assertEquals(setOf("id401"), restored.newArrivalIds)
        restored.observeLatest(listOf(message(402, "me")))
        assertTrue("Deleted arrival must no longer be announced", restored.newArrivalIds.isEmpty())
        restored.merge(listOf(message(402, "me")), emptySet(), true)
        assertEquals("id402", restored.messages.last().getString("id"))
        assertFalse(CommunityState(restored.toJson()).toJson().getBoolean("reading_history"))
        restored.observeLatest((403..503).map { message(it) })
        assertEquals(50, restored.toJson().getJSONArray("arrivals").length())
        restored.clear()
        assertTrue(restored.newArrivalIds.isEmpty()); assertFalse(restored.toJson().has("newest_observed"))
    }

    @Test fun olderPagesMoveBoundedWindowAndLatestRefreshPreservesHistory() {
        fun message(n: Int) = JSONObject().put("id", "id$n").put("created", created(n))
        val state = CommunityState()
        state.merge((201..400).map(::message), emptySet())
        state.merge((151..200).map(::message), emptySet())
        assertEquals("id151", state.messages.first().getString("id"))
        assertEquals(200, state.messages.size)
        state.merge((101..150).map(::message), emptySet())
        assertEquals("id101", state.messages.first().getString("id"))
        assertEquals(200, state.messages.map { it.getString("id") }.toSet().size)
        state.merge((351..400).map(::message), emptySet())
        assertEquals("id101", state.messages.first().getString("id"))
        val restored = CommunityState(state.toJson())
        restored.merge((51..100).map(::message), emptySet())
        assertEquals("id51", restored.messages.first().getString("id"))
        assertEquals(200, restored.toJson().getJSONArray("messages").length())
    }

    @Test fun seenMetadataIsBoundedByTheRetainedCache() {
        val state=CommunityState(); state.seen.addAll((0..220).map { "id$it" })
        state.merge((0..220).map { JSONObject().put("id","id$it").put("created",created(it)) },emptySet())
        assertEquals(200,state.seen.size); assertFalse(state.seen.contains("id0"))
    }

    @Test fun confirmedAttemptClearsOnlyPendingDraft() {
        val state = CommunityState(); state.draft = "literal"; state.beginPost("literal")
        val method = CommunityState::class.java.methods.firstOrNull { it.name == "completePost" }
        assertNotNull("Confirmed draft completion is missing", method)
        method!!.invoke(state)
        assertNull(state.pendingId); assertEquals("", state.pendingBody); assertEquals("", state.draft)
    }

    @Test fun accountSwitchAndLogoutClearAllPrivateStateButSameAccountRestores() {
        val state = CommunityState()
        val signIn = CommunityState::class.java.methods.firstOrNull { it.name == "signIn" }
        assertNotNull("Account-scoped private state is missing", signIn)
        val alice = CommunityClient.Account("member000000001", "alice", "fixture-token")
        signIn!!.invoke(state, alice)
        val saved = state.toJson().put("draft", "private").put("blocked", org.json.JSONArray(listOf("bob")))
            .put("seen", org.json.JSONArray(listOf("id1"))).put("messages", org.json.JSONArray(listOf(JSONObject().put("id","id1").put("created",created(1)))))
        val restored = CommunityState(saved)
        signIn.invoke(restored, alice)
        assertEquals("private", restored.draft)
        assertEquals(1, restored.messages.size)
        signIn.invoke(restored, CommunityClient.Account("member000000002", "bob", "other-token"))
        assertEquals("", restored.draft); assertTrue(restored.messages.isEmpty())
        assertEquals(0, restored.toJson().getJSONArray("blocked").length())
        CommunityState::class.java.getMethod("clear").invoke(restored)
        assertFalse(restored.toJson().has("account")); assertEquals("", restored.draft)
    }

    @Test fun pendingDraftRetainsUuidAcrossPersistenceAndCannotBeSilentlyReplaced() {
        val state = CommunityState()
        val begin = CommunityState::class.java.methods.firstOrNull { it.name == "beginPost" }
        assertNotNull("Recoverable pending attempt is missing", begin)
        val id = begin!!.invoke(state, "literal") as String
        assertEquals(id, begin.invoke(state, "literal"))
        try { begin.invoke(state, "different"); fail("Uncertain attempt was replaced") }
        catch (e: java.lang.reflect.InvocationTargetException) { assertTrue(e.cause is IllegalStateException) }
        val json = CommunityState::class.java.getMethod("toJson").invoke(state) as JSONObject
        val restored = CommunityState::class.java.getConstructor(JSONObject::class.java).newInstance(json)
        assertEquals(id, begin.invoke(restored, "literal"))
    }

    @Test fun cacheMergesChronologicallyWithoutDeletingUnqueriedHistoryAndBoundsAt200() {
        val type = try { Class.forName("ohi.andre.consolelauncher.community.CommunityState") } catch (_: ClassNotFoundException) { null }
        assertNotNull("Private community state is missing", type)
        val state = type!!.getConstructor().newInstance()
        val merge = type.getMethod("merge", List::class.java, Set::class.java)
        val messages = (0..220).map { JSONObject().put("id", "id$it").put("created", created(it)).put("body", "inert") }
        merge.invoke(state, messages, emptySet<String>())
        @Suppress("UNCHECKED_CAST")
        val cached = type.getMethod("getMessages").invoke(state) as List<JSONObject>
        assertEquals(200, cached.size); assertEquals("id21", cached.first().getString("id"))
        merge.invoke(state, messages.takeLast(50), emptySet<String>())
        assertEquals(200, (type.getMethod("getMessages").invoke(state) as List<*>).size)
        merge.invoke(state, emptyList<JSONObject>(), setOf("id21"))
        assertEquals(199, (type.getMethod("getMessages").invoke(state) as List<*>).size)
    }
}
