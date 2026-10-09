package ohi.andre.consolelauncher.community

import org.json.JSONObject

/** Forum drafts and the immutable delivery attempt live in the encrypted account store. */
class CommunityPaneState(saved: JSONObject) {
    var channel = saved.optString("channel", "general").takeIf { it in CHANNELS } ?: "general"
    var thread = saved.optString("thread")
    var composing = saved.optBoolean("composing")
    private val drafts = saved.optJSONObject("drafts") ?: JSONObject()
    private fun records(json: JSONObject, key: String): List<JSONObject> = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.takeLast(200) } ?: emptyList()
    var threads = records(saved, "threads")
    var replies = records(saved, "replies")
    var root: JSONObject? = saved.optJSONObject("root")
    private var threadCursor = saved.optJSONObject("thread_cursor") ?: saved.optJSONObject("cursor").takeIf { thread.isEmpty() }
    private var replyCursor = saved.optJSONObject("reply_cursor") ?: saved.optJSONObject("cursor").takeIf { thread.isNotEmpty() }
    var cursor: JSONObject?
        get() = if (thread.isEmpty()) threadCursor else replyCursor
        set(value) { if (thread.isEmpty()) threadCursor = value else replyCursor = value }
    var history = saved.optBoolean("history")
    fun focusReply(record: JSONObject) {
        thread = record.getString("thread"); composing = false; root = null; replies = listOf(record); cursor = record
    }
    fun mergeThreads(page: List<JSONObject>, older: Boolean, latest: Boolean) {
        if (latest) history = false
        if (older) history = true
        if (!older && history) return
        if (page.isNotEmpty() || latest) cursor = page.lastOrNull()
        threads = (if (older) threads + page else page).distinctBy { it.getString("id") }
            .sortedWith(compareByDescending<JSONObject> { it.getString("created") }.thenByDescending { it.getString("id") }).takeLast(200)
    }
    var pendingId: String? = saved.optString("pending_id").takeIf { it.isNotEmpty() }
        private set
    var pendingContext = saved.optString("pending_context")
        private set
    var pending = saved.optJSONObject("pending") ?: JSONObject()
        private set
    val context: String get() = if (thread.isEmpty()) channel else "reply:$thread"
    fun draft(context: String) = drafts.optJSONObject(context) ?: JSONObject()
    fun saveDraft(context: String, title: String, body: String) {
        if (title.isEmpty() && body.isEmpty()) drafts.remove(context)
        else drafts.put(context, JSONObject().put("title", title).put("body", body))
    }
    fun begin(context: String, payload: JSONObject): String {
        check(pendingId == null || (pendingContext == context && pending.toString() == payload.toString()))
        if (pendingId == null) {
            pendingId = java.util.UUID.randomUUID().toString(); pendingContext = context
            pending = JSONObject(payload.toString())
        }
        return pendingId!!
    }
    fun complete() {
        val editable = draft(pendingContext)
        if ((CommunityInput.parse(editable.optString("body")) as? CommunityInput.Message)?.body == pending.optString("body") && editable.optString("title") == pending.optString("title")) drafts.remove(pendingContext)
        reject()
    }
    fun reject() { pendingId = null; pendingContext = ""; pending = JSONObject() }
    fun toJson() = JSONObject().put("channel", channel).put("thread", thread).put("composing", composing)
        .put("threads", org.json.JSONArray(threads)).put("replies", org.json.JSONArray(replies)).put("root", root).put("cursor", cursor).put("thread_cursor", threadCursor).put("reply_cursor", replyCursor).put("history", history)
        .put("drafts", drafts).put("pending_id", pendingId ?: "").put("pending_context", pendingContext).put("pending", pending)
    companion object { val CHANNELS = listOf("general", "bugs", "help", "features", "presets") }
}
