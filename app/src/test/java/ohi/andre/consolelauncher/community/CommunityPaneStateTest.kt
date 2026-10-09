package ohi.andre.consolelauncher.community

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommunityPaneStateTest {
    @Test fun replyMentionFocusKeepsItsSourceVisibleBeyondTheFirstPage() {
        val pane = CommunityPaneState(JSONObject())
        pane.saveDraft("bugs", "title", "keep")
        val reply = JSONObject().put("id", "reply0000000001").put("thread", "thread000000001").put("created", "created").put("body", "mentioned reply")
        val method = CommunityPaneState::class.java.methods.firstOrNull { it.name == "focusReply" }
        assertNotNull("Specific reply focus is missing", method)
        method!!.invoke(pane, reply)
        assertEquals("thread000000001", pane.thread)
        assertEquals(listOf("mentioned reply"), pane.replies.map { it.getString("body") })
        assertEquals(reply.toString(), pane.cursor.toString())
        assertEquals("keep", pane.draft("bugs").getString("body"))
    }
    @Test fun replyCursorDoesNotOverwriteThreadListPosition() {
        val pane = CommunityPaneState(JSONObject())
        val listCursor = JSONObject().put("id", "thread000000001").put("created", "old")
        pane.cursor = listCursor; pane.history = true
        pane.thread = "thread000000001"
        pane.cursor = JSONObject().put("id", "reply0000000001").put("created", "new")
        val restored = CommunityPaneState(pane.toJson())
        restored.thread = ""
        assertEquals(listCursor.toString(), restored.cursor.toString())
        assertTrue(restored.history)
    }
    @Test fun successfulEscapedReplyClearsOnlyItsMatchingDraft() {
        val pane = CommunityPaneState(JSONObject())
        pane.saveDraft("reply:thread000000001", "", "//exit")
        pane.begin("reply:thread000000001", JSONObject().put("thread", "thread000000001").put("body", "/exit"))
        pane.complete()
        assertEquals("", pane.draft("reply:thread000000001").optString("body"))
    }
    @Test fun forumHistoryAdvancesPastBoundAndPollingDoesNotReplaceIt() {
        val type = Class.forName("ohi.andre.consolelauncher.community.CommunityPaneState")
        val pane = type.getConstructor(JSONObject::class.java).newInstance(JSONObject())
        val merge = type.methods.firstOrNull { it.name == "mergeThreads" }
        assertNotNull("Forum pagination window is missing", merge)
        fun page(start: Int, end: Int) = (start..end).reversed().map { JSONObject().put("id", "t" + it.toString().padStart(14, '0')).put("created", it.toString().padStart(8, '0')).put("body", "persistent") }
        merge!!.invoke(pane, page(251, 300), false, false)
        for (i in 4 downTo 0) merge.invoke(pane, page(i * 50 + 1, (i + 1) * 50), true, false)
        val before = type.getMethod("toJson").invoke(pane) as JSONObject
        assertEquals(200, before.getJSONArray("threads").length())
        assertEquals("t00000000000001", before.getJSONObject("cursor").getString("id"))
        merge.invoke(pane, page(252, 301), false, false)
        val polled = type.getMethod("toJson").invoke(pane) as JSONObject
        assertEquals("t00000000000001", polled.getJSONObject("cursor").getString("id"))
        assertEquals(before.getJSONArray("threads").toString(), polled.getJSONArray("threads").toString())
        merge.invoke(pane, page(252, 301), false, true)
        val latest = type.getMethod("toJson").invoke(pane) as JSONObject
        assertEquals("t00000000000301", latest.getJSONArray("threads").getJSONObject(0).getString("id"))
        assertFalse(latest.getBoolean("history"))
    }
    @Test fun forumAttemptIsImmutableAndSeparateFromEditableContextDrafts() {
        val type = runCatching { Class.forName("ohi.andre.consolelauncher.community.CommunityPaneState") }.getOrNull()
        assertNotNull("Forum context persistence is missing", type)
        val pane = type!!.getConstructor(JSONObject::class.java).newInstance(JSONObject())
        val draft = type.getMethod("saveDraft", String::class.java, String::class.java, String::class.java)
        draft.invoke(pane, "features", "old title", "old body")
        val payload = JSONObject().put("channel", "features").put("title", "old title").put("body", "old body")
        val begin = type.getMethod("begin", String::class.java, JSONObject::class.java)
        val id = begin.invoke(pane, "features", payload) as String
        draft.invoke(pane, "features", "new title", "new body")
        draft.invoke(pane, "bugs", "bug", "other draft")
        val json = type.getMethod("toJson").invoke(pane) as JSONObject
        val restored = type.getConstructor(JSONObject::class.java).newInstance(json)
        assertEquals(id, begin.invoke(restored, "features", payload))
        try { begin.invoke(restored, "bugs", payload); fail("Pending UUID moved to a different context") }
        catch (e: java.lang.reflect.InvocationTargetException) { assertTrue(e.cause is IllegalStateException) }
        type.getMethod("complete").invoke(restored)
        val result = type.getMethod("toJson").invoke(restored) as JSONObject
        assertEquals("new body", result.getJSONObject("drafts").getJSONObject("features").getString("body"))
        assertEquals("other draft", result.getJSONObject("drafts").getJSONObject("bugs").getString("body"))
        assertEquals("", result.optString("pending_id"))
    }
}
