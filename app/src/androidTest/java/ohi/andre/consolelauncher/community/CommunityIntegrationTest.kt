package ohi.andre.consolelauncher.community

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class CommunityIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun emulatorOnly() { assumeTrue("Disposable emulator only", Build.MODEL.startsWith("sdk_gphone")) }
    @Test fun landscapeKeyboardKeepsInlinePromptVisibleAndEnterExitsLocally() {
        emulatorOnly(); CommunityStore(context).clear()
        var activity=instrumentation.startActivitySync(Intent(context,CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            val monitor=instrumentation.addMonitor(CommunityActivity::class.java.name,null,false)
            instrumentation.runOnMainSync { activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            activity=instrumentation.waitForMonitorWithTimeout(monitor,5000) as CommunityActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val input=activity.window.decorView.findViewWithTag<EditText>("community_input"); input.requestFocus(); input.setText("/exit")
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            Thread.sleep(1000); instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val input=activity.window.decorView.findViewWithTag<EditText>("community_input")
                val rect=android.graphics.Rect(); assertTrue(input.getGlobalVisibleRect(rect)); assertEquals(input.height,rect.height()); assertNull(input.background)
                assertTrue("Landscape IME must not replace or obscure the inline prompt", input.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
                assertTrue(activity.window.decorView.width>activity.window.decorView.height)
            }
            instrumentation.uiAutomation.takeScreenshot().let { image -> java.io.File(context.cacheDir,"community-landscape.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; image.recycle() }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
            await { activity.isFinishing }
            assertFalse(CommunityState(CommunityStore(context).load()).toJson().has("account"))
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityStore(context).clear() }
    }

    @Test fun offlineRecreationExpiresVisibleCacheWithoutSuccessfulRefreshOrReleasingPendingAttempt() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); fixture.status = 503; CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear()
        val state = CommunityState().apply {
            signIn(CommunityClient.Account("member000000001", "alice", "fixture-token"))
            draft = "original uncertain"; beginPost(draft); draft = "new independent draft"
            merge(listOf(org.json.JSONObject().put("id", "m00000000000001")
                .put("created", CommunityHttpFixture.created(System.currentTimeMillis() - 86_400_000L + 8000L))
                .put("author", "member000000002").put("author_handle", "bob").put("body", "near cutoff cached body")), emptySet())
        }
        val pending = state.pendingId
        val saved = state.toJson()
        saved.getJSONArray("messages").put(org.json.JSONObject().put("id", "m00000000000002")
            .put("created", CommunityHttpFixture.created(System.currentTimeMillis() - 86_400_001L)).put("body", "already expired body"))
        store.save(saved)
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("failed") }
            instrumentation.runOnMainSync {
                val text = activity.window.decorView.findViewWithTag<TextView>("community_output").text
                assertTrue(text.contains("near cutoff cached body")); assertFalse(text.contains("already expired body"))
            }
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            instrumentation.waitForIdleSync()
            await { !activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("near cutoff cached body") }
            val restored = CommunityState(store.load())
            assertTrue(restored.messages.isEmpty()); assertEquals(pending, restored.pendingId)
            assertEquals("original uncertain", restored.pendingBody); assertEquals("new independent draft", restored.draft)
            assertTrue("Expiry must not send a post", fixture.requests.none { it.method == "POST" })
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun pauseDuringHistoryStopsChecksAndResumePollsAgain() = pausedRefresh(1)
    @Test fun pauseDuringCheckedChunkStopsRemainingChunksAndResumePollsAgain() = pausedRefresh(2)
    private fun pausedRefresh(gatedRequest: Int) {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val gate = java.util.concurrent.CountDownLatch(1)
        val store = CommunityStore(context); store.clear()
        val records = (1..200).map { org.json.JSONObject().put("id", "m" + it.toString().padStart(14, '0'))
            .put("created", CommunityHttpFixture.created()).put("author", "member000000002").put("author_handle", "bob").put("body", "pause item $it") }
        fixture.posts.addAll(records)
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")); merge(records, emptySet()); draft = "kept during pause" }.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { !(activity.javaClass.getDeclaredField("busy").apply { isAccessible = true }.get(activity) as Boolean) }
            val before = fixture.requests.size
            fixture.gateGetNumber = fixture.getCount.get() + gatedRequest; fixture.getResponseGate = gate
            instrumentation.runOnMainSync { (activity.javaClass.getDeclaredField("poll").apply { isAccessible = true }.get(activity) as Runnable).run() }
            await { fixture.requests.size == before + gatedRequest }
            instrumentation.runOnMainSync { activity.moveTaskToBack(true) }
            await { !(activity.javaClass.getDeclaredField("visible").apply { isAccessible = true }.get(activity) as Boolean) }
            gate.countDown(); Thread.sleep(500)
            assertEquals("Pause must not start a subsequent HTTP request", before + gatedRequest, fixture.requests.size)
            assertEquals("kept during pause", CommunityState(store.load()).draft)
            assertNull(CommunityState(store.load()).pendingId)
            instrumentation.runOnMainSync { context.startActivity(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            await { fixture.requests.size > before + gatedRequest && !(activity.javaClass.getDeclaredField("busy").apply { isAccessible = true }.get(activity) as Boolean) }
            assertTrue(fixture.posts.none { it.optString("author") == "member000000001" })
        } finally { gate.countDown(); instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun latestFetchFailureDoesNotUndoCanonicalPostOrResendNewDraft() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val gate = java.util.concurrent.CountDownLatch(1)
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")) }.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            fixture.postResponseGate = gate
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("canonical delivered")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
            }
            await { fixture.posts.size == 1 }
            fixture.lookupStatus = 503
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<EditText>("community_input").setText("new independent draft") }
            gate.countDown()
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            instrumentation.runOnMainSync {
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("stale"))
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("canonical delivered"))
            }
            val saved = CommunityState(store.load())
            assertNull(saved.pendingId); assertEquals("new independent draft", saved.draft)
            assertEquals(1, fixture.posts.size)
        } finally { gate.countDown(); instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun registrationBackendRejectionStaysSignedOutWithVisibleSafeError() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); fixture.status=429; CommunityActivity.debugBackend=fixture.url
        val store=CommunityStore(context); store.clear()
        val activity=instrumentation.startActivitySync(Intent(context,CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("/register"); activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
                val input = activity.findViewById<EditText>(ohi.andre.consolelauncher.R.id.community_input)
                val send = activity.findViewById<android.view.View>(ohi.andre.consolelauncher.R.id.community_send)
                for (value in listOf("alice", "fixture-password", "fixture-password")) { input.setText(value); send.performClick() }
            }
            await {
                activity.findViewById<TextView>(ohi.andre.consolelauncher.R.id.community_auth_guidance).text.contains("Rate limited")
            }
            assertFalse(CommunityState(store.load()).toJson().has("account"))
            assertFalse(fixture.requests.any { it.path.endsWith("auth-with-password") })
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend=null; store.clear(); fixture.close() }
    }

    @Test fun delayedPostCompletionPreservesNewDraftAcrossRecreation() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val gate = java.util.concurrent.CountDownLatch(1)
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")) }.toJson())
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            fixture.postResponseGate = gate
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("original literal")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
            }
            await { fixture.posts.size == 1 }
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<EditText>("community_input").setText("new editable draft") }
            gate.countDown()
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            assertEquals("new editable draft", CommunityState(store.load()).draft)
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertEquals("new editable draft", activity.window.decorView.findViewWithTag<EditText>("community_input").text.toString()) }
            assertEquals("original literal", fixture.posts.single().getString("body"))
            assertNull(CommunityState(store.load()).pendingId)
        } finally { gate.countDown(); instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun recreationDuringDelayedPostRetainsOriginalAttemptAndNewDraft() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val gate = java.util.concurrent.CountDownLatch(1)
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")) }.toJson())
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            fixture.postResponseGate = gate
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("inflight original")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
            }
            await { fixture.posts.size == 1 }
            val id = fixture.posts.single().getString("client_id")
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("draft during request")
                activity.recreate()
            }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            gate.countDown(); instrumentation.waitForIdleSync()
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            val restored = CommunityState(store.load())
            assertEquals(id, restored.pendingId); assertEquals("inflight original", restored.pendingBody); assertEquals("draft during request", restored.draft)
            assertEquals(1, fixture.requests.count { it.method == "POST" && it.path.endsWith("messages/records") })
            assertEquals("Paused POST must not initiate reconciliation", 0, fixture.requests.count { it.query.contains("client_id") })
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick() }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            assertEquals(1, fixture.posts.size); assertNull(CommunityState(store.load()).pendingId)
            assertEquals("draft during request", CommunityState(store.load()).draft)
        } finally { gate.countDown(); instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun uncertainOriginalReconcilesWithoutPostingNewDraftAfterRecreation() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")) }.toJson())
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            fixture.dropPostResponse = true; fixture.lookupStatus = 503
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("original uncertain")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
            }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("uncertain") }
            val pending = CommunityState(store.load()).pendingId
            fixture.dropPostResponse = false; fixture.status = 429
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick() }
            await { !(activity.javaClass.getDeclaredField("busy").apply { isAccessible = true }.get(activity) as Boolean) }
            assertEquals("Retry rejection cannot disprove the original commit", pending, CommunityState(store.load()).pendingId)
            assertEquals("Failed lookup must not send a retry POST", 1, fixture.requests.count { it.method == "POST" && it.path.endsWith("messages/records") })
            fixture.status = 200
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<EditText>("community_input").setText("new draft"); activity.finish() }
            instrumentation.waitForIdleSync()
            val saved = CommunityState(store.load())
            assertEquals(pending, saved.pendingId); assertEquals("original uncertain", saved.pendingBody); assertEquals("new draft", saved.draft)
            fixture.dropPostResponse = false; fixture.lookupStatus = 200
            activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
            instrumentation.waitForIdleSync()
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick() }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            assertEquals(1, fixture.posts.size); assertEquals(pending, fixture.posts.single().getString("client_id"))
            assertNull(CommunityState(store.load()).pendingId); assertEquals("new draft", CommunityState(store.load()).draft)
            instrumentation.runOnMainSync { assertEquals("new draft", activity.window.decorView.findViewWithTag<EditText>("community_input").text.toString()) }
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun committedGatewayResponsesRecoverOrPersistOriginalIdentityThroughReload() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")) }.toJson())
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        fun submit(text: String) = instrumentation.runOnMainSync {
            activity.window.decorView.findViewWithTag<EditText>("community_input").setText(text)
            activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
        }
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            fixture.postResponseStatus = 502
            submit("gateway recovered")
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            assertNull(CommunityState(store.load()).pendingId); assertEquals(1, fixture.posts.size)
            fixture.postResponseStatus = 504; fixture.lookupStatus = 503
            submit("gateway uncertain")
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("uncertain") }
            val id = CommunityState(store.load()).pendingId
            assertEquals(fixture.posts.last().getString("client_id"), id)
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<EditText>("community_input").setText("newer independent draft"); activity.finish() }
            instrumentation.waitForIdleSync()
            activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
            instrumentation.waitForIdleSync()
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("failed") }
            assertEquals(id, CommunityState(store.load()).pendingId)
            assertEquals("gateway uncertain", CommunityState(store.load()).pendingBody)
            fixture.lookupStatus = 200; fixture.postResponseStatus = 200
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick() }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            assertEquals(2, fixture.requests.count { it.method == "POST" && it.path.endsWith("messages/records") })
            assertEquals(2, fixture.posts.size); assertNull(CommunityState(store.load()).pendingId)
            assertEquals("newer independent draft", CommunityState(store.load()).draft)
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun definitivePostRejectionClearsAttemptButKeepsEditableDraft() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")) }.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            fixture.status = 429
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("rejected draft")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
            }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Rate limited") }
            assertNull("Definitive rejection must release the attempt", CommunityState(store.load()).pendingId)
            assertEquals("rejected draft", CommunityState(store.load()).draft)
            assertEquals(1, fixture.requests.count { it.method == "POST" && it.path.endsWith("messages/records") })
            fixture.status = 200
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("replacement draft")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
            }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            assertEquals("replacement draft", fixture.posts.single().getString("body"))
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun mixedCaseBlockUsesCanonicalHandleEvenInTurkishLocale() {
        emulatorOnly(); val locale = java.util.Locale.getDefault(); java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
        val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        fixture.posts.add(org.json.JSONObject().put("id", "m00000000000001").put("created", CommunityHttpFixture.created())
            .put("author", "member000000002").put("author_handle", "billy").put("body", "block fixture body"))
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")) }.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("block fixture body") }
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("/block BILLY")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
                assertFalse(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("block fixture body"))
            }
            assertEquals(setOf("billy"), CommunityState(store.load()).blocked)
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText("/block billy")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("block fixture body"))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close(); java.util.Locale.setDefault(locale) }
    }

    @Test fun successfulPostFromFullHistoryReturnsToLatestAndStaysVisibleAfterRecreation() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        fun message(n: Int) = org.json.JSONObject().put("id", "m" + n.toString().padStart(14, '0'))
            .put("created", CommunityHttpFixture.created()).put("author", "member000000002").put("author_handle", "bob").put("body", "history item $n")
        fixture.posts.addAll((1..400).map(::message))
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")); merge((201..400).map(::message), emptySet()) }.toJson())
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        fun submit(text: String) = instrumentation.runOnMainSync {
            activity.window.decorView.findViewWithTag<EditText>("community_input").setText(text)
            activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
        }
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            submit("/older")
            await { activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("history item 151\n") }
            assertFalse(CommunityState(store.load()).messages.any { it.getString("id") == message(400).getString("id") })
            submit("own latest post")
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            instrumentation.runOnMainSync {
                val text = activity.window.decorView.findViewWithTag<TextView>("community_output").text
                assertTrue("Successful post must not disappear in an old bounded window", text.contains("own latest post"))
                assertTrue("Posting from history must fetch evicted latest peer bodies immediately", text.contains("history item 400\n"))
            }
            val posted = fixture.posts.last().getString("id")
            assertTrue(CommunityState(store.load()).seen.contains(posted))
            instrumentation.waitForIdleSync(); Thread.sleep(300)
            instrumentation.runOnMainSync {
                val scroll = activity.window.decorView.findViewWithTag<android.widget.ScrollView>("community_scroll")
                val output = activity.window.decorView.findViewWithTag<TextView>("community_output")
                assertTrue("Own successful post must be in the visible latest viewport", scroll.scrollY + scroll.height >= output.height)
            }
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            instrumentation.waitForIdleSync()
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            instrumentation.runOnMainSync { assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("own latest post")) }
            assertFalse(CommunityState(store.load()).toJson().getBoolean("reading_history"))
            assertEquals(200, CommunityState(store.load()).messages.size)
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun fullCacheOlderPagesAdvanceCursorAndReturnToLatestWithNewArrivals() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        fun message(n: Int) = org.json.JSONObject().put("id", "m" + n.toString().padStart(14, '0'))
            .put("created", CommunityHttpFixture.created()).put("author", "member000000002").put("author_handle", "bob").put("body", "history item $n")
        fixture.posts.addAll((1..400).map(::message))
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")); merge((201..400).map(::message), emptySet()) }.toJson())
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        fun submit(command: String) = instrumentation.runOnMainSync {
            activity.window.decorView.findViewWithTag<EditText>("community_input").setText(command)
            activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
        }
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            submit("/older")
            await { activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("history item 151\n") }
            submit("/older")
            await { activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("history item 101\n") }
            val cursors = fixture.requests.filter { it.query.contains("created <") }.map { Regex("id < '([^']+)'").find(it.query)!!.groupValues[1] }
            assertEquals(listOf("m00000000000201", "m00000000000151"), cursors)
            instrumentation.waitForIdleSync(); Thread.sleep(500)
            val beforeUnread = activity.window.decorView.findViewWithTag<TextView>("community_unread").text.toString()
            val beforeY = activity.window.decorView.findViewWithTag<android.widget.ScrollView>("community_scroll").scrollY
            fixture.posts.add(message(401))
            instrumentation.runOnMainSync {
                (activity.javaClass.getDeclaredField("poll").apply { isAccessible = true }.get(activity) as Runnable).run()
            }
            await { !(activity.javaClass.getDeclaredField("busy").apply { isAccessible = true }.get(activity) as Boolean) }
            instrumentation.runOnMainSync {
                assertEquals(beforeY, activity.window.decorView.findViewWithTag<android.widget.ScrollView>("community_scroll").scrollY)
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("history item 101\n"))
                assertFalse(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("history item 401\n"))
                fun count(text: String) = Regex("[0-9]+").find(text)?.value?.toInt() ?: 0
                assertEquals("New arrival outside the history cache must be announced", count(beforeUnread) + 1, count(activity.window.decorView.findViewWithTag<TextView>("community_unread").text.toString()))
            }
            val before = fixture.requests.size
            submit("/refresh")
            await { fixture.requests.size >= before + 5 && !(activity.javaClass.getDeclaredField("busy").apply { isAccessible = true }.get(activity) as Boolean) }
            val saved = CommunityState(store.load())
            assertTrue("Explicit refresh must return to latest", saved.messages.any { it.getString("id") == "m00000000000401" })
            assertEquals(200, saved.messages.size); assertEquals(200, saved.messages.map { it.getString("id") }.toSet().size)
            instrumentation.waitForIdleSync(); Thread.sleep(300)
            instrumentation.runOnMainSync {
                val scroll = activity.window.decorView.findViewWithTag<android.widget.ScrollView>("community_scroll")
                assertTrue(scroll.scrollY + scroll.height >= activity.window.decorView.findViewWithTag<TextView>("community_output").height)
            }
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            instrumentation.waitForIdleSync()
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            assertFalse(CommunityState(store.load()).toJson().getBoolean("reading_history"))
            instrumentation.runOnMainSync { assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("history item 401\n")) }
            val first = CommunityState(store.load()).messages.first().getString("id")
            submit("/older")
            await { CommunityState(store.load()).messages.first().getString("id") < first }
            val deleted = CommunityState(store.load()).messages.first().getString("id")
            fixture.posts.removeAll { it.getString("id") == deleted }
            instrumentation.runOnMainSync { (activity.javaClass.getDeclaredField("poll").apply { isAccessible = true }.get(activity) as Runnable).run() }
            await { CommunityState(store.load()).messages.none { it.getString("id") == deleted } }
            assertTrue(CommunityState(store.load()).messages.size <= 200)
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun lostPostResponseRemainsRecoverableAndRetryUsesSameUuidWithoutDuplicate() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear(); store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001","alice","fixture-token")) }.toJson())
        var activity = instrumentation.startActivitySync(Intent(context,CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            fixture.dropPostResponse = true; fixture.lookupStatus = 503
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<EditText>("community_input").setText("recover uncertain literal"); activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick() }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("uncertain") }
            val pending = CommunityState(store.load()).pendingId
            assertNotNull(pending); assertEquals(pending, fixture.posts.single().getString("client_id"))
            instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync()
            activity = instrumentation.startActivitySync(Intent(context,CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
            instrumentation.waitForIdleSync()
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("failed") }
            assertEquals(1, fixture.posts.size); assertEquals(pending, CommunityState(store.load()).pendingId)
            fixture.lookupStatus = 200; fixture.dropPostResponse = false
            instrumentation.runOnMainSync { assertEquals("recover uncertain literal",activity.window.decorView.findViewWithTag<EditText>("community_input").text.toString()); activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick() }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
            assertEquals(1,fixture.posts.size); assertNull(CommunityState(store.load()).pendingId)
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend=null; store.clear(); fixture.close() }
    }

    @Test fun newMessagesStayUnreadWhileReadingOlderHistoryWithoutForcedScroll() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        fun message(n: Int) = org.json.JSONObject().put("id","m"+n.toString().padStart(14,'0')).put("created",CommunityHttpFixture.created())
            .put("author","member000000002").put("author_handle","bob").put("body","message $n\nsecond line\nthird line")
        (1..30).forEach { fixture.posts.add(message(it)) }
        val store = CommunityStore(context); store.clear(); store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001","alice","fixture-token")) }.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("message 30") }
            instrumentation.runOnMainSync {
                assertNotNull("Unread indicator is missing", activity.window.decorView.findViewWithTag<TextView>("community_unread"))
                activity.window.decorView.findViewWithTag<android.widget.ScrollView>("community_scroll").scrollTo(0,0)
            }
            fixture.posts.add(message(31))
            instrumentation.runOnMainSync { (activity.javaClass.getDeclaredField("poll").apply { isAccessible = true }.get(activity) as Runnable).run() }
            await { activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("message 31") }
            instrumentation.runOnMainSync { assertEquals(0,activity.window.decorView.findViewWithTag<android.widget.ScrollView>("community_scroll").scrollY) }
            assertFalse("Undisplayed arrival was marked seen", CommunityState(store.load()).seen.contains("m00000000000031"))
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun revokedSessionClearsPrivateCacheDraftAndToken() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); fixture.status = 403; CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001","alice","fixture-token")); draft = "private pending draft"; blocked.add("bob") }.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("expired") }
            assertFalse("Revoked token remained stored", CommunityState(store.load()).toJson().has("account"))
            assertEquals("", CommunityState(store.load()).draft)
            assertTrue(CommunityState(store.load()).blocked.isEmpty())
            instrumentation.runOnMainSync { assertEquals("", activity.window.decorView.findViewWithTag<EditText>("community_input").text.toString()) }
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun launcherCommunityEntryIsParamCommandWithOfflineHelpAndOpensOnlyOnSubmission() {
        emulatorOnly(); CommunityStore(context).clear()
        val group = ohi.andre.consolelauncher.commands.CommandGroup(context, "ohi.andre.consolelauncher.commands.main.raw")
        val command = group.getCommandByName("community")
        assertNotNull("Community launcher entry is missing", command)
        assertTrue(command is ohi.andre.consolelauncher.commands.main.specific.ParamCommand)
        assertTrue(group.commandNames.contains("community"))
        assertTrue(context.getString(command!!.helpRes()).contains("experimental"))
        val unsafeType = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val pack = unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, ohi.andre.consolelauncher.commands.main.MainPack::class.java) as ohi.andre.consolelauncher.commands.main.MainPack
        pack.context = context; pack.res = context.resources; pack.commandGroup = group
        pack.cmdPrefs = ohi.andre.consolelauncher.commands.CommandsPreferences(); pack.currentDirectory = context.cacheDir
        val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
        try {
            val parsed = ohi.andre.consolelauncher.commands.CommandTuils.parse("community", pack)
            assertNotNull(parsed); assertEquals(0, monitor.hits)
            assertTrue(parsed!!.exec(pack).isNullOrEmpty())
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            instrumentation.waitForIdleSync(); instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync()
        } finally { instrumentation.removeMonitor(monitor); CommunityStore(context).clear() }
    }

    @Test fun exitAndBackRequireKeepOrDiscardForRecoverableDraft() {
        emulatorOnly(); CommunityStore(context).clear()
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync {
                val input = activity.window.decorView.findViewWithTag<EditText>("community_input")
                input.setText("unsent private draft"); input.setText("/exit")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
                assertFalse("Exit discarded draft without asking", activity.isFinishing)
                val dialog = activity.javaClass.getDeclaredField("leaveDialog").apply { isAccessible = true }.get(activity) as android.app.AlertDialog
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            assertEquals("unsent private draft", CommunityState(CommunityStore(context).load()).draft)
            activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                activity.onBackPressedDispatcher.onBackPressed()
                val dialog = activity.javaClass.getDeclaredField("leaveDialog").apply { isAccessible = true }.get(activity) as android.app.AlertDialog
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync(); assertEquals("", CommunityState(CommunityStore(context).load()).draft)
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityStore(context).clear() }
    }

    @Test fun localCommunityCommandsNeverPostAndLogoutClearsPrivateAccountState() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        fixture.posts.add(org.json.JSONObject().put("id","message00000001").put("created",CommunityHttpFixture.created()).put("author","member000000002").put("author_handle","bob").put("body","inert remote /exit https://example.org"))
        val store = CommunityStore(context); store.clear()
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001","alice","fixture-token")) }.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        fun submit(command: String) = instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<EditText>("community_input").setText(command); activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick() }
        try {
            await { activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("inert remote") }
            submit("/block bob")
            instrumentation.runOnMainSync {
                assertFalse("Local block was not applied", activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("inert remote"))
                assertTrue(CommunityState(store.load()).blocked.contains("bob"))
            }
            submit("/block bob"); submit("/help")
            instrumentation.runOnMainSync { assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains("forgotten-password")) }
            submit("/report message00000001")
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Report accepted") }
            submit("/older"); await { fixture.requests.count { it.path.endsWith("messages/records") } >= 2 }
            await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("current") }
            submit("/logout")
            instrumentation.runOnMainSync { assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Signed out")) }
            assertFalse(CommunityState(store.load()).toJson().has("account"))
            assertTrue(CommunityState(store.load()).messages.isEmpty()); assertTrue(CommunityState(store.load()).blocked.isEmpty())
            assertEquals(1, fixture.posts.size)
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync(); CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun visibleOnlyPollingAndRecreationRecoverDraftWithoutAutomaticPosting() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear()
        val state = CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token")); draft = "recover this draft" }
        store.save(state.toJson())
        var activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            await { fixture.requests.any { it.path.endsWith("messages/records") } }
            instrumentation.runOnMainSync { assertEquals("recover this draft", activity.window.decorView.findViewWithTag<EditText>("community_input").text.toString()) }
            val before = fixture.requests.size
            Thread.sleep(16000)
            assertTrue("No visible 15-second refresh", fixture.requests.size > before)
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<EditText>("community_input").setText("changed private draft"); activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertEquals("changed private draft", activity.window.decorView.findViewWithTag<EditText>("community_input").text.toString()); activity.moveTaskToBack(true) }
            Thread.sleep(1000)
            val paused = fixture.requests.size; Thread.sleep(16000)
            assertEquals("Polling continued in background", paused, fixture.requests.size)
            assertTrue("Draft automatically posted", fixture.posts.isEmpty())
        } finally { instrumentation.runOnMainSync { activity.finish() }; CommunityActivity.debugBackend = null; store.clear(); fixture.close() }
    }

    @Test fun publicRegistrationUsesMaskedConfirmationAndAuthenticatesAfterCreation() {
        emulatorOnly(); val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        CommunityStore(context).clear()
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync {
                val input = activity.findViewById<EditText>(ohi.andre.consolelauncher.R.id.community_input)
                val send = activity.findViewById<android.view.View>(ohi.andre.consolelauncher.R.id.community_send)
                input.setText("/register"); send.performClick()
                input.setText("alice"); send.performClick()
                assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
                assertFalse(input.isSaveEnabled)
                input.setText("fixture-password"); send.performClick()
                assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
                input.setText("different"); send.performClick()
                assertTrue(activity.findViewById<TextView>(ohi.andre.consolelauncher.R.id.community_auth_guidance).text.contains("Passwords differ"))
                assertTrue(input.text.isEmpty()); assertNull(input.transformationMethod)
                assertFalse(CommunityStore(context).load().toString().contains("fixture-password"))
            }
            assertEquals(0, fixture.requests.size)
            instrumentation.runOnMainSync {
                val input = activity.findViewById<EditText>(ohi.andre.consolelauncher.R.id.community_input)
                val send = activity.findViewById<android.view.View>(ohi.andre.consolelauncher.R.id.community_send)
                for (value in listOf("alice", "fixture-password", "fixture-password")) { input.setText(value); send.performClick() }
            }
            await { fixture.requests.any { it.path.endsWith("messages/records") } }
            assertEquals(listOf("/api/collections/members/records", "/api/collections/members/auth-with-password"), fixture.requests.take(2).map { it.path })
        } finally { instrumentation.runOnMainSync { activity.finish() }; CommunityActivity.debugBackend = null; CommunityStore(context).clear(); fixture.close() }
    }

    @Test fun dedicatedUsernameLoginPostsLiteralMessagesWithoutLauncherDispatch() {
        emulatorOnly()
        val fixture = CommunityHttpFixture()
        try {
            val activityType = Class.forName("ohi.andre.consolelauncher.community.CommunityActivity")
            val debug = activityType.declaredFields.firstOrNull { it.name == "debugBackend" }
            assertNotNull("Debug-only local HTTP fixture injection is missing", debug)
            debug!!.isAccessible = true; debug.set(null, fixture.url)
            CommunityStore(context).clear()
            val activity = instrumentation.startActivitySync(Intent(context, activityType).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as Activity
            instrumentation.waitForIdleSync()
            fun submit(text: String) = instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText(text)
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
            }
            try {
                assertEquals(0, fixture.requests.size)
                submit("/login")
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val input = activity.window.decorView.findViewWithTag<EditText>("community_input")
                    assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_auth_error").text.contains("Username"))
                    val send = activity.window.decorView.findViewWithTag<android.view.View>("community_submit")
                    input.setText("alice"); send.performClick()
                    assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
                    assertFalse(input.isSaveEnabled)
                    input.setText("fixture-password"); send.performClick()
                }
                await { fixture.requests.any { it.path.endsWith("messages/records") } }
                await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("alice") }
                for ((typed, expected) in listOf("settings; reboot" to "settings; reboot", "exit" to "exit", "//exit" to "/exit")) {
                    submit(typed)
                    await { fixture.posts.any { it.optString("body") == expected } }
                    await { activity.window.decorView.findViewWithTag<EditText>("community_input").text.isEmpty() }
                }
                instrumentation.runOnMainSync { assertTrue("Room/account identity must remain visible after posting", activity.window.decorView.findViewWithTag<TextView>("community_header").text.contains("alice")) }
                val before = fixture.posts.size
                submit("/not-a-command")
                instrumentation.runOnMainSync { assertEquals("/not-a-command", activity.window.decorView.findViewWithTag<EditText>("community_input").text.toString()) }
                assertEquals(before, fixture.posts.size); assertFalse(activity.isFinishing)
                submit("/exit")
                await { activity.isFinishing }; assertEquals(before, fixture.posts.size)
            } finally { instrumentation.runOnMainSync { activity.finish() }; debug.set(null, null); CommunityStore(context).clear() }
        } finally { fixture.close() }
    }
    private fun await(check: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + 10000
        while (android.os.SystemClock.uptimeMillis() < end) {
            var passed = false; instrumentation.runOnMainSync { passed = check() }
            if (passed) return
            Thread.sleep(50)
        }
        fail("Timed out awaiting community fixture state")
    }

    @Test fun privateStoreEncryptsTokenCacheAndDraftOutsideExportsAndClearsOnLogout() {
        emulatorOnly()
        val type = try { Class.forName("ohi.andre.consolelauncher.community.CommunityStore") } catch (_: ClassNotFoundException) { null }
        assertNotNull("Keystore-backed no-backup store is missing", type)
        val store = type!!.getConstructor(android.content.Context::class.java).newInstance(context)
        val json = org.json.JSONObject().put("token", "fixture-secret-token").put("draft", "fixture-private-draft")
        type.getMethod("save", org.json.JSONObject::class.java).invoke(store, json)
        val file = java.io.File(context.noBackupFilesDir, "community/state.bin")
        assertTrue(file.isFile)
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("fixture-secret-token"))
        assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("fixture-private-draft"))
        assertEquals(json.toString(), (type.getMethod("load").invoke(store) as org.json.JSONObject).toString())
        assertFalse(file.canonicalPath.startsWith(ohi.andre.consolelauncher.tuils.Tuils.getFolder().canonicalPath + "/"))
        type.getMethod("clear").invoke(store)
        assertFalse(file.exists())
        assertEquals(0, (type.getMethod("load").invoke(store) as org.json.JSONObject).length())
    }

    @Test fun signedOutSurfaceHasOwnUnframedInputAndUnknownSlashNeverLeaves() {
        emulatorOnly()
        CommunityStore(context).clear()
        CommunityActivity.debugBackend = null
        val type = try { Class.forName("ohi.andre.consolelauncher.community.CommunityActivity") } catch (_: ClassNotFoundException) { null }
        assertNotNull("Dedicated opt-in community Activity is missing", type)
        val activity = instrumentation.startActivitySync(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as Activity
        instrumentation.waitForIdleSync()
        try {
            instrumentation.runOnMainSync {
                val input = activity.window.decorView.findViewWithTag<EditText>("community_input")
                val status = activity.window.decorView.findViewWithTag<TextView>("community_status")
                assertNotNull(input); assertNull(input.background)
                assertEquals(ohi.andre.consolelauncher.managers.settings.ThemeColorResolver.color(ohi.andre.consolelauncher.managers.xml.options.Theme.output_text_color), status.currentTextColor)
                assertEquals(ohi.andre.consolelauncher.managers.settings.ThemeColorResolver.color(ohi.andre.consolelauncher.managers.xml.options.Theme.input_text_color), input.currentTextColor)
                assertTrue(status.text.contains("Signed out"))
                input.setText("/unknown")
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
                assertEquals("/unknown", input.text.toString()); assertFalse(activity.isFinishing)
                assertNotNull(input.error)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
