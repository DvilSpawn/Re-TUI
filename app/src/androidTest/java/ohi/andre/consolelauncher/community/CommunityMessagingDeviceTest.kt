package ohi.andre.consolelauncher.community

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Test-package only; restores the encrypted account and geometry without clearing its key. */
class CommunityMessagingDeviceTest {
    @get:org.junit.Rule val windowPreferences = ohi.andre.consolelauncher.help.WindowPreferencesRule(fresh = false)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun await(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < end) {
            var done = false; instrumentation.runOnMainSync { done = check() }
            if (done) return
            Thread.sleep(25)
        }
        fail("Messaging UI did not reach the expected state")
    }
    private fun field(activity: CommunityActivity, name: String) = activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun ready(activity: CommunityActivity) = field(activity, "busy") == false && field(activity, "restoringViewport") == false
    private fun state(activity: CommunityActivity) = field(activity, "state") as CommunityState
    private fun open(): CommunityActivity {
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        if (activity.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            } finally { instrumentation.removeMonitor(monitor) }
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            if (activity.window.decorView.findViewWithTag<View>("community-resize-corner").visibility == View.VISIBLE)
                activity.window.decorView.findViewWithTag<View>("community-window-toggle").performClick()
        }
        return activity
    }
    private fun capture(activity: CommunityActivity, name: String) {
        instrumentation.waitForIdleSync(); Thread.sleep(150)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(activity.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private fun record(number: Int, body: String = "Community message $number") = JSONObject()
        .put("id", "m" + number.toString().padStart(14, '0')).put("author", "member000000002").put("author_handle", "bob")
        .put("created", CommunityHttpFixture.created(System.currentTimeMillis() - 3600000 + number * 1000)).put("body", body)

    @Test fun discussionUsesOneComposerAndRestoresBothStepsWithoutPostingOnNext() {
        assumeTrue(context.packageName.endsWith(".fetchdebug"))
        val store = CommunityStore(context); val original = store.load(); val fixture = CommunityPaneHttpFixture()
        store.save(CommunityState().apply {
            signIn(CommunityClient.Account("member000000001", "alice", "fixture-token", policyVersion = CommunityClient.POLICY_VERSION)); pane.channel = "help"
        }.toJson())
        CommunityActivity.debugBackend = fixture.url
        var activity: CommunityActivity? = null
        try {
            var screen = open(); activity = screen
            await { ready(screen) }
            instrumentation.runOnMainSync {
                screen.window.decorView.findViewWithTag<View>("community_new_thread").performClick()
                val input = screen.findViewById<EditText>(R.id.community_input)
                assertTrue(state(screen).pane.editingTitle)
                assertEquals(screen.getString(R.string.community_thread_title), input.hint)
                for (invalid in listOf(" ", "too\nlong", "😀".repeat(121))) {
                    input.setText(invalid); screen.findViewById<View>(R.id.community_send).performClick()
                    assertNotNull(input.error); assertTrue(state(screen).pane.editingTitle)
                }
                input.setText("A discussion from one composer")
                assertTrue("Typing a title must not advance the step", state(screen).pane.editingTitle)
                input.requestFocus()
                (screen.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            capture(screen, "community-discussion-title.png")
            instrumentation.runOnMainSync {
                val input = screen.findViewById<EditText>(R.id.community_input)
                screen.findViewById<View>(R.id.community_send).performClick()
                assertFalse(state(screen).pane.editingTitle)
                assertEquals(screen.getString(R.string.community_discussion_hint), input.hint)
                assertEquals("", input.text.toString())
                assertEquals("A discussion from one composer", screen.findViewById<TextView>(R.id.community_thread_summary).text.toString())
                input.setText("exit; settings stays literal")
                screen.window.decorView.findViewWithTag<View>("community_back").performClick()
                assertTrue(state(screen).pane.editingTitle)
                assertEquals("A discussion from one composer", input.text.toString())
                input.setText("Revised discussion title")
            }
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync { screen.recreate() }
                screen = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity; activity = screen
            } finally { instrumentation.removeMonitor(monitor) }
            await { ready(screen) }
            instrumentation.runOnMainSync {
                val input = screen.findViewById<EditText>(R.id.community_input)
                assertTrue(state(screen).pane.editingTitle)
                assertEquals("Revised discussion title", input.text.toString())
                screen.findViewById<View>(R.id.community_send).performClick()
                assertEquals("exit; settings stays literal", input.text.toString())
                assertEquals(0, fixture.requests.count { it.method == "POST" && it.path.contains("/collections/") })
                screen.findViewById<View>(R.id.community_thread_summary).performClick()
                assertTrue(state(screen).pane.editingTitle)
                screen.findViewById<View>(R.id.community_send).performClick()
                assertEquals("exit; settings stays literal", input.text.toString())
            }
            capture(screen, "community-discussion-body.png")
            val bodyMonitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync { screen.recreate() }
                screen = instrumentation.waitForMonitorWithTimeout(bodyMonitor, 5000) as CommunityActivity; activity = screen
            } finally { instrumentation.removeMonitor(bodyMonitor) }
            await { ready(screen) }
            instrumentation.runOnMainSync {
                assertFalse(state(screen).pane.editingTitle)
                assertEquals("exit; settings stays literal", screen.findViewById<EditText>(R.id.community_input).text.toString())
                screen.findViewById<View>(R.id.community_send).performClick()
            }
            await { ready(screen) && fixture.threads.size == 1 && state(screen).pane.thread.isNotEmpty() }
            assertEquals("Revised discussion title", fixture.threads.single().getString("title"))
            assertEquals("exit; settings stays literal", fixture.threads.single().getString("body"))
            assertEquals(1, fixture.requests.count { it.method == "POST" && it.path.endsWith("threads/records") })
        } finally {
            activity?.let { screen -> instrumentation.runOnMainSync { screen.finish() }; instrumentation.waitForIdleSync() }
            fixture.close(); CommunityActivity.debugBackend = null; store.save(original)
        }
    }

    @Test fun liveFollowStopsForReadingAndUnreadReturnsToLatestWithPositionRestored() {
        assumeTrue(context.packageName.endsWith(".fetchdebug"))
        val store = CommunityStore(context); val original = store.load(); val fixture = CommunityHttpFixture()
        fixture.posts.addAll((1..70).map { record(it) })
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token", policyVersion = CommunityClient.POLICY_VERSION)); merge(fixture.posts.toList(), emptySet()) }.toJson())
        CommunityActivity.debugBackend = fixture.url
        var activity: CommunityActivity? = null
        try {
            var screen = open(); activity = screen
            await { ready(screen) && screen.findViewById<TextView>(R.id.community_output).text.contains("Community message 70") }
            await { val scroll = screen.findViewById<ScrollView>(R.id.community_scroll); scroll.scrollY + scroll.height >= scroll.getChildAt(0).height - 2 }
            assertTrue(state(screen).following)
            assertNull(screen.window.decorView.findViewWithTag<View>("community-window-reset"))
            fun receive(number: Int) {
                fixture.posts.add(record(number))
                instrumentation.runOnMainSync {
                    @Suppress("UNCHECKED_CAST")
                    (field(screen, "liveChanges") as MutableMap<String, String>)["messages/" + fixture.posts.last().getString("id")] = "messages"
                    (field(screen, "receiveLive") as Runnable).run()
                }
                await { ready(screen) && screen.findViewById<TextView>(R.id.community_output).text.contains("Community message $number") }
            }
            receive(71)
            await { val scroll = screen.findViewById<ScrollView>(R.id.community_scroll); scroll.scrollY + scroll.height >= scroll.getChildAt(0).height - 2 }
            instrumentation.runOnMainSync { screen.findViewById<ScrollView>(R.id.community_scroll).scrollTo(0, 200) }
            await { !state(screen).following }
            val anchor = state(screen).viewAnchor; val offset = state(screen).viewOffset
            receive(72)
            instrumentation.runOnMainSync {
                assertEquals("New posts must preserve the reading anchor", anchor, state(screen).viewAnchor)
                assertEquals(offset, state(screen).viewOffset)
                assertEquals(View.VISIBLE, screen.findViewById<View>(R.id.community_unread).visibility)
                assertTrue(screen.findViewById<TextView>(R.id.community_room).text.contains("#general"))
            }
            capture(screen, "community-messaging-reading.png")
            instrumentation.runOnMainSync { screen.finish() }; instrumentation.waitForIdleSync()
            screen = open(); activity = screen
            await { ready(screen) }
            instrumentation.runOnMainSync {
                assertFalse(state(screen).following); assertEquals(anchor, state(screen).viewAnchor); assertEquals(offset, state(screen).viewOffset)
                screen.findViewById<View>(R.id.community_unread).performClick()
            }
            await { ready(screen) && state(screen).following }
            await { val scroll = screen.findViewById<ScrollView>(R.id.community_scroll); scroll.scrollY + scroll.height >= scroll.getChildAt(0).height - 2 }
            assertEquals(View.GONE, screen.findViewById<View>(R.id.community_unread).visibility)
            capture(screen, "community-messaging-latest.png")
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() }; instrumentation.waitForIdleSync() }
            CommunityActivity.debugBackend = null; fixture.close(); store.save(original)
        }
    }

    @Test fun checkDeliveryUsesOriginalPayloadAndPreservesNewDraft() {
        assumeTrue(context.packageName.endsWith(".fetchdebug"))
        val store = CommunityStore(context); val original = store.load(); val fixture = CommunityHttpFixture()
        val saved = CommunityState().apply {
            signIn(CommunityClient.Account("member000000001", "alice", "fixture-token", policyVersion = CommunityClient.POLICY_VERSION))
            draft = "original message"; beginPost(draft); draft = "newer unsent draft"
        }
        fixture.posts.add(record(1, saved.pendingBody).put("client_id", saved.pendingId).put("author", "member000000001").put("author_handle", "alice"))
        store.save(saved.toJson()); CommunityActivity.debugBackend = fixture.url
        var activity: CommunityActivity? = null
        try {
            val screen = open(); activity = screen
            await { ready(screen) }
            instrumentation.runOnMainSync { screen.window.decorView.findViewWithTag<View>("community_retry").performClick() }
            await { ready(screen) && state(screen).pendingId == null }
            instrumentation.runOnMainSync {
                assertEquals("newer unsent draft", screen.findViewById<EditText>(R.id.community_input).text.toString())
                assertEquals("newer unsent draft", state(screen).draft)
                assertEquals(1, Regex("original message").findAll(screen.findViewById<TextView>(R.id.community_output).text).count())
            }
            assertTrue("Checking an already-delivered message must not send a POST", fixture.requests.none { it.method == "POST" })
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() }; instrumentation.waitForIdleSync() }
            CommunityActivity.debugBackend = null; fixture.close(); store.save(original)
        }
    }

    @Test fun welcomeStartsInlineOnboardingWithoutSendingOrLosingDraft() {
        assumeTrue(context.packageName.endsWith(".fetchdebug"))
        val store = CommunityStore(context); val original = store.load(); val fixture = CommunityHttpFixture()
        store.save(CommunityState().apply { draft = "kept draft" }.toJson()); CommunityActivity.debugBackend = fixture.url
        var activity: CommunityActivity? = null
        try {
            val screen = open(); activity = screen
            await { ready(screen) }
            capture(screen, "community-messaging-welcome.png")
            instrumentation.runOnMainSync {
                assertTrue(screen.findViewById<TextView>(R.id.community_output).text.contains("Welcome"))
                screen.window.decorView.findViewWithTag<View>("community_join").performClick()
                assertEquals("Username", screen.findViewById<EditText>(R.id.community_input).hint)
                assertEquals("Next", screen.findViewById<TextView>(R.id.community_send).text)
                screen.findViewById<View>(R.id.community_auth_cancel).performClick()
                assertEquals("kept draft", screen.findViewById<EditText>(R.id.community_input).text.toString())
            }
            assertTrue(fixture.requests.none { it.method == "POST" })
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() }; instrumentation.waitForIdleSync() }
            CommunityActivity.debugBackend = null; fixture.close(); store.save(original)
        }
    }

    @Test fun discussionOlderRepliesAreReachableAndKeepAReadingAnchor() {
        assumeTrue(context.packageName.endsWith(".fetchdebug"))
        val store = CommunityStore(context); val original = store.load(); val fixture = CommunityPaneHttpFixture()
        val thread = record(1).put("id", "thread000000001").put("channel", "help").put("title", "A longer conversation")
        fixture.threads.add(thread)
        fixture.replies.addAll((1..250).map { record(it, "Reply number $it").put("id", "r" + it.toString().padStart(14, '0')).put("thread", thread.getString("id")) })
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token", policyVersion = CommunityClient.POLICY_VERSION)); pane.channel = "help"; pane.thread = thread.getString("id") }.toJson())
        CommunityActivity.debugBackend = fixture.url
        var activity: CommunityActivity? = null
        try {
            val screen = open(); activity = screen
            await { ready(screen) && state(screen).pane.replies.lastOrNull()?.optString("body") == "Reply number 250" }
            assertEquals("Reply number 201", state(screen).pane.replies.first().getString("body"))
            instrumentation.runOnMainSync {
                screen.findViewById<ScrollView>(R.id.community_scroll).scrollTo(0, 250)
                screen.findViewById<View>(R.id.community_history).performClick()
            }
            await { ready(screen) && state(screen).pane.replies.firstOrNull()?.optString("body") == "Reply number 151" }
            assertTrue(state(screen).pane.replyHistory); assertFalse(state(screen).pane.replyFollowing)
            repeat(3) {
                instrumentation.runOnMainSync { screen.findViewById<View>(R.id.community_history).performClick() }
                await { ready(screen) }
            }
            assertEquals("Reply number 1", state(screen).pane.replies.first().getString("body"))
            assertEquals(200, state(screen).pane.replies.size)
            instrumentation.runOnMainSync { screen.window.decorView.findViewWithTag<View>("community_latest").performClick() }
            await { ready(screen) && state(screen).pane.replies.lastOrNull()?.optString("body") == "Reply number 250" }
            assertFalse(state(screen).pane.replyHistory); assertTrue(state(screen).pane.replyFollowing)
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() }; instrumentation.waitForIdleSync() }
            CommunityActivity.debugBackend = null; fixture.close(); store.save(original)
        }
    }

    @Test fun discussionPreviewsContextAndMentionActionKeepTheComposerDraft() {
        assumeTrue(context.packageName.endsWith(".fetchdebug"))
        val store = CommunityStore(context); val original = store.load(); val fixture = CommunityPaneHttpFixture()
        val thread = record(1, "A persistent discussion with a useful preview.").put("id", "thread000000001")
            .put("channel", "help").put("title", "How do you use Re-TUI?")
        fixture.threads.add(thread)
        store.save(CommunityState().apply {
            signIn(CommunityClient.Account("member000000001", "alice", "fixture-token", policyVersion = CommunityClient.POLICY_VERSION)); pane.channel = "help"
            pane.saveDraft("reply:thread000000001", "", "My reply")
        }.toJson())
        CommunityActivity.debugBackend = fixture.url
        var activity: CommunityActivity? = null
        try {
            val screen = open(); activity = screen
            await { ready(screen) && screen.window.decorView.findViewWithTag<View>("community_thread_thread000000001") != null }
            instrumentation.runOnMainSync {
                val row = screen.window.decorView.findViewWithTag<TextView>("community_thread_thread000000001")
                assertTrue(row.text.contains("useful preview")); assertFalse(row.text.contains("thread000000001"))
                row.performClick()
            }
            await { ready(screen) && state(screen).pane.root != null }
            instrumentation.runOnMainSync {
                assertTrue(screen.findViewById<TextView>(R.id.community_room).text.contains("#help › How do you use Re-TUI?"))
                assertEquals("Reply to this discussion", screen.findViewById<EditText>(R.id.community_input).hint)
                assertEquals("My reply", screen.findViewById<EditText>(R.id.community_input).text.toString())
                val rail = screen.findViewById<View>(R.id.community_rail)
                assertEquals(if (rail.visibility == View.VISIBLE) View.GONE else View.VISIBLE, screen.window.decorView.findViewWithTag<View>("community_channels").visibility)
            }
            capture(screen, "community-messaging-discussion.png")
            instrumentation.runOnMainSync {
                screen.javaClass.getDeclaredMethod("messageActions", JSONObject::class.java).apply { isAccessible = true }.invoke(screen, thread)
            }
            instrumentation.waitForIdleSync()
            fun click(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.text?.toString() == "Mention author") return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                for (i in 0 until node.childCount) if (click(node.getChild(i))) return true
                return false
            }
            assertTrue(click(instrumentation.uiAutomation.rootInActiveWindow))
            await { screen.findViewById<EditText>(R.id.community_input).text.toString() == "My reply @bob " }
            assertTrue("Mentioning only inserts text, never posts", fixture.requests.none { it.method == "POST" && it.path.contains("/collections/") })
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() }; instrumentation.waitForIdleSync() }
            CommunityActivity.debugBackend = null; fixture.close(); store.save(original)
        }
    }
}
