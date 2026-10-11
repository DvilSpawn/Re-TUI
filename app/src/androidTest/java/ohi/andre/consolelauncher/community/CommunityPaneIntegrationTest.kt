package ohi.andre.consolelauncher.community

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Clears private fixture state: disposable emulator only, never a data-bearing phone. */
class CommunityPaneIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadline) { instrumentation.waitForIdleSync(); if (condition()) return; Thread.sleep(40) }
        fail("Community pane did not settle before the bounded deadline")
    }
    private fun start(fixture: CommunityPaneHttpFixture): CommunityActivity {
        assumeTrue("Disposable emulator only", Build.MODEL.startsWith("sdk_gphone"))
        CommunityActivity.debugBackend = fixture.url
        CommunityStore(context).clear()
        CommunityStore(context).save(CommunityState().apply {
            signIn(CommunityClient.Account("member000000001", "alice", "fixture-token", policyVersion = CommunityClient.POLICY_VERSION)); pane.channel = "bugs"
        }.toJson())
        return instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
    }
    private fun view(activity: CommunityActivity, tag: String) = activity.window.decorView.findViewWithTag<View>(tag)
    private fun busy(activity: CommunityActivity) = activity.javaClass.getDeclaredField("busy").apply { isAccessible = true }.getBoolean(activity)
    private fun finish(activity: CommunityActivity, fixture: CommunityPaneHttpFixture) {
        instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync()
        fixture.close(); CommunityActivity.debugBackend = null; CommunityStore(context).clear()
    }
    @Test fun touchNewThreadShowsRootRepliesAndBackReturnsToTheList() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone")); val fixture = CommunityPaneHttpFixture(); val activity = start(fixture)
        try {
            await { !busy(activity) }
            instrumentation.runOnMainSync { view(activity, "community_new_thread").performClick() }
            instrumentation.runOnMainSync {
                (view(activity, "community_input") as EditText).setText("literal title")
                view(activity, "community_submit").performClick()
                (view(activity, "community_input") as EditText).setText("exit; settings")
                view(activity, "community_submit").performClick()
            }
            await { fixture.threads.size == 1 && !busy(activity) && (view(activity, "community_output") as TextView).text.contains("exit; settings") }
            assertEquals("literal title", fixture.threads.single().getString("title"))
            assertEquals("bugs", fixture.threads.single().getString("channel"))
            instrumentation.runOnMainSync {
                (view(activity, "community_input") as EditText).setText("reply literal")
                view(activity, "community_submit").performClick()
            }
            await { fixture.replies.size == 1 && !busy(activity) }
            assertEquals(fixture.threads.single().getString("id"), fixture.replies.single().getString("thread"))
            instrumentation.runOnMainSync { view(activity, "community_back").performClick() }
            await { !busy(activity) && view(activity, "community_thread_${fixture.threads.single().getString("id")}") != null }
            assertEquals(1, fixture.requests.count { it.method == "POST" && it.path.endsWith("threads/records") })
        } finally { finish(activity, fixture) }
    }
    @Test fun recreationRetainsNewDraftWhileReconcilingTheOriginalImmutableThread() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone")); val fixture = CommunityPaneHttpFixture(); var activity = start(fixture)
        val gate = java.util.concurrent.CountDownLatch(1); fixture.postGate = gate
        try {
            await { !busy(activity) }
            instrumentation.runOnMainSync {
                view(activity, "community_new_thread").performClick()
                (view(activity, "community_input") as EditText).setText("original title")
                view(activity, "community_submit").performClick()
                (view(activity, "community_input") as EditText).setText("original body")
                view(activity, "community_submit").performClick()
            }
            await { fixture.threads.size == 1 }
            instrumentation.runOnMainSync {
                view(activity, "community_back").performClick()
                (view(activity, "community_input") as EditText).setText("new title")
                view(activity, "community_submit").performClick()
                (view(activity, "community_input") as EditText).setText("new body")
            }
            val uuid = CommunityPaneState(CommunityStore(context).load().getJSONObject("pane")).pendingId
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
            gate.countDown(); instrumentation.waitForIdleSync()
            val stored = CommunityPaneState(CommunityStore(context).load().getJSONObject("pane"))
            assertEquals(uuid, stored.pendingId); assertEquals("original body", stored.pending.getString("body"))
            assertEquals("new body", stored.draft("bugs").getString("body"))
            instrumentation.runOnMainSync { view(activity, "community_submit").performClick() }
            await { !busy(activity) && CommunityPaneState(CommunityStore(context).load().getJSONObject("pane")).pendingId == null }
            assertEquals(1, fixture.requests.count { it.method == "POST" && it.path.endsWith("threads/records") })
            assertEquals("new body", CommunityPaneState(CommunityStore(context).load().getJSONObject("pane")).draft("bugs").getString("body"))
        } finally { gate.countDown(); finish(activity, fixture) }
    }
    @Test fun caretCompletionPreservesTextAndPresenceRenewsWhilePostingIsBusy() {
        assumeTrue(Build.MODEL.startsWith("sdk_gphone")); val fixture = CommunityPaneHttpFixture(); val activity = start(fixture)
        val gate = java.util.concurrent.CountDownLatch(1)
        try {
            await { !busy(activity) && fixture.requests.any { it.path.endsWith("/presence") } }
            instrumentation.runOnMainSync {
                view(activity, "community_new_thread").performClick()
                val input = view(activity, "community_input") as EditText
                input.setText("title"); view(activity, "community_submit").performClick()
                input.setText("hi @al rest"); input.setSelection(6)
            }
            await { view(activity, "community_complete_alice") != null }
            instrumentation.runOnMainSync {
                view(activity, "community_complete_alice").performClick()
                val input = view(activity, "community_input") as EditText
                assertEquals("hi @alice rest", input.text.toString()); assertEquals(9, input.selectionStart)
                fixture.postGate = gate; view(activity, "community_submit").performClick()
            }
            await { busy(activity) && fixture.threads.size == 1 }
            val before = fixture.requests.count { it.path.endsWith("/presence") }
            instrumentation.runOnMainSync { (activity.javaClass.getDeclaredField("presencePoll").apply { isAccessible = true }.get(activity) as Runnable).run() }
            await { fixture.requests.count { it.path.endsWith("/presence") } > before }
            instrumentation.runOnMainSync { activity.moveTaskToBack(true) }
            gate.countDown(); val requests = fixture.requests.size; Thread.sleep(500)
            assertEquals("Pause must not start chained work", requests, fixture.requests.size)
        } finally { gate.countDown(); finish(activity, fixture) }
    }
}
