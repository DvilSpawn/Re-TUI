package ohi.andre.consolelauncher.community

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ohi.andre.consolelauncher.R

/** Destructive private-store fixture: disposable emulator only. */
class CommunityReviewedActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun state(activity: CommunityActivity) = activity.javaClass.getDeclaredField("state").apply { isAccessible = true }.get(activity) as CommunityState
    private fun busy(activity: CommunityActivity) = activity.javaClass.getDeclaredField("busy").apply { isAccessible = true }.getBoolean(activity)
    private fun call(activity: CommunityActivity, name: String, vararg args: Any) {
        val method = activity.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
        method.isAccessible = true; method.invoke(activity, *args)
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadline) { instrumentation.waitForIdleSync(); if (condition()) return; Thread.sleep(40) }
        fail("Community review fixture did not settle")
    }
    private fun signedIn() = CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")) }
    private fun root(channel: String = "help") = JSONObject().put("id", "thread000000001").put("channel", channel)
        .put("title", "fixture root").put("body", "root body").put("author_handle", "bob").put("created", CommunityHttpFixture.created())
    private fun run(fixture: CommunityPaneHttpFixture, seed: CommunityState, test: (CommunityActivity) -> Unit) {
        assumeTrue("Disposable emulator only", Build.MODEL.startsWith("sdk_gphone"))
        CommunityActivity.debugBackend = fixture.url
        CommunityStore(context).clear(); CommunityStore(context).save(seed.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        try { await { !busy(activity) }; test(activity) }
        finally {
            instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync()
            fixture.close(); CommunityActivity.debugBackend = null; CommunityStore(context).clear()
        }
    }
    @Test fun renderedHeaderAndDockTargetsClearCornersAcrossHostSizesAndSendDispatchesRegistration() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone"))
        run(CommunityPaneHttpFixture(), CommunityState()) { activity ->
            instrumentation.runOnMainSync {
                val root = activity.findViewById<View>(R.id.community_panel).parent as android.view.ViewGroup
                val panel = root.parent as android.view.ViewGroup
                val host = panel.parent as android.widget.FrameLayout
                fun px(dp: Int) = ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, dp)
                fun bounds(view: View) = android.graphics.Rect(0, 0, view.width, view.height).also {
                    panel.offsetDescendantRectToMyCoords(view, it)
                }
                fun descendants(view: View): List<View> = listOf(view) + if (view is android.view.ViewGroup)
                    (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                val toggle = panel.findViewWithTag<View>("community-window-toggle")
                for (maximized in listOf(false, true)) {
                    if (maximized) toggle.performClick()
                    for ((width, height) in listOf(360 to 640, 640 to 360, 320 to 360)) {
                        repeat(3) {
                            host.measure(View.MeasureSpec.makeMeasureSpec(px(width), View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(px(height), View.MeasureSpec.EXACTLY))
                            host.layout(0, 0, px(width), px(height))
                        }
                        val corners = descendants(panel).filter { it.tag?.toString()?.startsWith("community-resize-") == true && it.visibility == View.VISIBLE }
                        assertEquals(if (maximized) 0 else 4, corners.size)
                        val controls = descendants(panel).filter { it is android.widget.Button && it.isShown && it !in corners }
                        assertTrue(controls.any { it.id == R.id.community_send })
                        for (control in controls) {
                            assertTrue("${control.tag} width in $width x $height", control.width >= px(48))
                            assertTrue("${control.tag} height in $width x $height", control.height >= px(48))
                            for (corner in corners) assertFalse("${control.tag} overlaps ${corner.tag} in $width x $height",
                                android.graphics.Rect.intersects(bounds(control), bounds(corner)))
                        }
                    }
                }
                toggle.performClick() // Registration must work in floating mode, not only maximized.
                activity.findViewById<EditText>(R.id.community_input).setText("/register")
            }
            instrumentation.waitForIdleSync()
            val send = activity.findViewById<View>(R.id.community_send)
            val location = IntArray(2); instrumentation.runOnMainSync { send.getLocationOnScreen(location) }
            val now = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(now, now + action * 20L, action,
                    location[0] + send.width / 2f, location[1] + send.height / 2f, 0)
                try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
            }
            instrumentation.waitForIdleSync()
            assertTrue("Floating center touch must start inline registration, not resize",
                activity.findViewById<TextView>(R.id.community_auth_guidance).text.toString().contains("Register · Username"))
            assertTrue(activity.findViewById<View>(R.id.community_auth_cancel).isShown)
            assertTrue(activity.findViewById<EditText>(R.id.community_input).text.isEmpty())
        }
    }
    @Test fun siblingCommandsRestoreThreadAndReplyDraftsWithoutTextWatcherOverwrite() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone"))
        val fixture = CommunityPaneHttpFixture(); fixture.threads.add(root())
        run(fixture, signedIn().apply { draft = "general draft"; pane.channel = "help" }) { activity ->
            for (reply in listOf(false, true)) for (command in listOf("/help", "/block bob", "/report message00000001")) {
                instrumentation.runOnMainSync {
                    val s = state(activity); s.pane.channel = "help"; s.pane.thread = if (reply) "thread000000001" else ""; s.pane.composing = !reply
                    s.pane.saveDraft(s.pane.context, if (reply) "" else "forum title", "forum draft")
                    call(activity, "restoreDraft")
                    activity.findViewById<EditText>(R.id.community_input).setText(command); call(activity, "submit")
                    assertEquals("forum draft", activity.findViewById<EditText>(R.id.community_input).text.toString())
                    assertEquals("forum draft", s.pane.draft(s.pane.context).getString("body")); assertEquals("general draft", s.draft)
                    assertEquals(if (reply) "" else "forum title", s.pane.draft(s.pane.context).optString("title"))
                }
                await { !busy(activity) }
            }
        }
    }
    @Test fun retryFromGeneralUsesAuthoritativeParentChannelAndImmutableAttempt() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone"))
        val fixture = CommunityPaneHttpFixture(); fixture.threads.add(root())
        val seed = signedIn().apply {
            draft = "general draft"
            pane.begin("reply:thread000000001", JSONObject().put("thread", "thread000000001").put("body", "immutable body"))
            pane.saveDraft("reply:thread000000001", "", "newer reply draft")
        }
        val uuid = seed.pane.pendingId
        run(fixture, seed) { activity ->
            instrumentation.runOnMainSync { call(activity, "retryForum") }
            await { !busy(activity) && state(activity).pane.thread == "thread000000001" }
            instrumentation.runOnMainSync {
                val s = state(activity)
                assertEquals("help", s.pane.channel); assertEquals("reply:thread000000001", s.pane.context)
                assertEquals(uuid, s.pane.pendingId); assertEquals("immutable body", s.pane.pending.getString("body"))
                assertEquals("newer reply draft", activity.findViewById<EditText>(R.id.community_input).text.toString()); assertEquals("general draft", s.draft)
            }
            assertFalse(fixture.requests.any { it.method == "POST" && it.path.endsWith("messages/records") })
        }
    }
    @Test fun latestBeyondFiftyRepliesReplacesOldViewAndFollowingPollDoesNotJump() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone"))
        val fixture = CommunityPaneHttpFixture(); val thread = root(); fixture.threads.add(thread)
        for (n in 1..250) fixture.replies.add(JSONObject().put("id", "r" + n.toString().padStart(14, '0')).put("thread", thread.getString("id"))
            .put("created", thread.getString("created")).put("body", "reply $n").put("author_handle", "bob"))
        run(fixture, signedIn().apply { pane.channel = "help"; pane.thread = thread.getString("id") }) { activity ->
            await { state(activity).pane.replies.size == 50 }
            instrumentation.runOnMainSync { call(activity, "refreshForum", false, true) }
            await { !busy(activity) && state(activity).pane.replies.lastOrNull()?.getString("id") == "r00000000000250" }
            assertEquals("r00000000000201", state(activity).pane.replies.first().getString("id"))
            val next = JSONObject(fixture.replies.last().toString()).put("id", "r00000000000251").put("body", "reply 251"); fixture.replies.add(next)
            var y = 0
            instrumentation.runOnMainSync { val scroll = activity.findViewById<ScrollView>(R.id.community_scroll); scroll.scrollTo(0, 40); y = scroll.scrollY; call(activity, "refreshForum", false, false) }
            await { !busy(activity) && state(activity).pane.replies.lastOrNull()?.getString("id") == next.getString("id") }
            instrumentation.waitForIdleSync()
            assertEquals(51, state(activity).pane.replies.size); assertEquals(y, activity.findViewById<ScrollView>(R.id.community_scroll).scrollY)
        }
    }
    @Test fun notificationPollingKeepsOlderUnreadMetadataAndErrorsDoNotReplaceIt() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone"))
        val fixture = CommunityPaneHttpFixture()
        for (n in 1..100) fixture.mentions.add(JSONObject().put("id", "m" + n.toString().padStart(14, '0')).put("kind", "thread")
            .put("source_id", "t" + n.toString().padStart(14, '0')).put("channel", "help"))
        val seed = signedIn().apply { mentions.replace(fixture.mentions); fixture.mentions.take(50).forEach { mentions.read(it.getString("id")) } }
        run(fixture, seed) { activity ->
            await { fixture.requests.any { it.path.endsWith("/mentions") && it.query.contains("page=2") } }
            assertEquals(50, state(activity).mentions.unread("help")); assertEquals(100, state(activity).mentions.items.size)
            fixture.failures["/api/community/mentions"] = 400
            val before = fixture.requests.size
            instrumentation.runOnMainSync { call(activity, "pollMentions") }
            await { fixture.requests.size > before }
            assertEquals(50, state(activity).mentions.unread("help")); assertEquals(100, state(activity).mentions.items.size)
        }
    }
    @Test fun failedParentAndOffscreenReplyRemainUnreadUntilViewportDisplaysTheSource() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone"))
        val fixture = CommunityPaneHttpFixture(); val thread = root().put("body", (1..60).joinToString("\n") { "root line $it" }); fixture.threads.add(thread)
        val reply = JSONObject().put("id", "reply0000000001").put("thread", thread.getString("id")).put("body", "peer @alice")
            .put("author_handle", "bob").put("created", thread.getString("created")); fixture.replies.add(reply)
        val mention = JSONObject().put("id", "mention00000001").put("kind", "thread_reply").put("source_id", reply.getString("id")).put("channel", "help")
        fixture.mentions.add(mention)
        run(fixture, signedIn().apply { mentions.replace(listOf(mention)) }) { activity ->
            fixture.failures["/api/collections/threads/records/thread000000001"] = 503
            instrumentation.runOnMainSync { call(activity, "showMentions") }; await { !busy(activity) }
            instrumentation.runOnMainSync { call(activity, "openMention", mention) }; await { !busy(activity) }
            assertEquals(1, state(activity).mentions.unread("help")); assertEquals("", state(activity).pane.thread)
            fixture.failures.clear()
            instrumentation.runOnMainSync { call(activity, "openMention", mention) }
            await { !busy(activity) && activity.findViewById<TextView>(R.id.community_output).text.contains("peer @alice") }
            instrumentation.runOnMainSync { call(activity, "markDisplayed") }
            assertEquals(1, state(activity).mentions.unread("help"))
            instrumentation.runOnMainSync { activity.findViewById<ScrollView>(R.id.community_scroll).fullScroll(View.FOCUS_DOWN) }
            await { state(activity).mentions.unread("help") == 0 }
        }
    }
}
