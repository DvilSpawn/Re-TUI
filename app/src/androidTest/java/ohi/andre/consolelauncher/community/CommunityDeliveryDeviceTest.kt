package ohi.andre.consolelauncher.community

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch

/** Opt-in tests preserve the current test-build account/drafts and never clear its keystore. */
class CommunityDeliveryDeviceTest {
    @get:org.junit.Rule val windowPreferences = ohi.andre.consolelauncher.help.WindowPreferencesRule(fresh = false)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun isolatedBuild() = assumeTrue("Separate test build only", context.packageName.endsWith(".fetchdebug"))
    private fun await(timeout: Long = 15000, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            var result = false; instrumentation.runOnMainSync { result = check() }
            if (result) return
            Thread.sleep(25)
        }
        fail("Community delivery check timed out; no credentials logged")
    }
    private fun open() = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
    private fun field(activity: CommunityActivity, key: String) = activity.javaClass.getDeclaredField(key).apply { isAccessible = true }.get(activity)
    private fun output(activity: CommunityActivity) = activity.window.decorView.findViewWithTag<TextView>("community_output").text.toString()
    private fun submit(activity: CommunityActivity, text: String) = instrumentation.runOnMainSync {
        activity.window.decorView.findViewWithTag<EditText>("community_input").setText(text)
        activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
    }
    private fun emit(key: String, value: String) = instrumentation.sendStatus(2, Bundle().apply { putString(key,value) })

    @Test fun postingPreemptsSlowHistoryAndShowsPendingBeforeAcknowledgement() {
        isolatedBuild()
        val store = CommunityStore(context); val original = store.load()
        val fixture = CommunityHttpFixture(); val historyGate = CountDownLatch(1); val postGate = CountDownLatch(1)
        fixture.gateGetNumber = 1; fixture.getResponseGate = historyGate; fixture.postResponseGate = postGate
        CommunityActivity.debugBackend = fixture.url
        store.save(CommunityState().apply { signIn(CommunityClient.Account("member000000001", "alice", "fixture-token", policyVersion = CommunityClient.POLICY_VERSION)) }.toJson())
        var activity: CommunityActivity? = null
        try {
            val screen = open(); activity = screen
            await { fixture.getCount.get() > 0 && field(screen,"busy") == true }
            submit(screen,"phone delivery fixture")
            await { fixture.posts.isNotEmpty() }
            instrumentation.runOnMainSync {
                assertTrue(output(screen).contains("@alice · sending"))
                assertTrue(output(screen).contains("phone delivery fixture"))
            }
            val start = SystemClock.uptimeMillis(); postGate.countDown()
            await(3000) { (field(screen,"state") as CommunityState).pendingId == null && output(screen).contains("phone delivery fixture") }
            emit("community_ack_ms",(SystemClock.uptimeMillis()-start).toString())
            assertEquals("History gate stayed blocked while send completed",1L,historyGate.count)
        } finally {
            historyGate.countDown(); postGate.countDown()
            activity?.let { instrumentation.runOnMainSync { it.finish() }; instrumentation.waitForIdleSync() }
            CommunityActivity.debugBackend = null; fixture.close(); store.save(original)
        }
    }

    @Test fun productionTlsDeliversPeerMessageWithoutRefreshAndRendersOwnSendOnce() {
        isolatedBuild(); assumeTrue(InstrumentationRegistry.getArguments().getString("liveCommunity") == "true")
        val store = CommunityStore(context); val original = store.load()
        CommunityActivity.debugBackend = null
        val client = CommunityClient()
        val accounts = mutableListOf<CommunityClient.Account>()
        var activity: CommunityActivity? = null
        try {
            repeat(2) {
                val handle = "phone_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
                emit("community_fixture_username",handle)
                val password = java.util.UUID.randomUUID().toString()
                val account = client.register(handle,password,password); accounts.add(account)
                emit("community_fixture_member_id",account.id)
            }
            store.save(CommunityState().apply { signIn(accounts[0]) }.toJson())
            val screen = open(); activity = screen
            await(30000) { field(screen,"liveConnected") == true && field(screen,"busy") == false }
            val peer = "live phone proof " + java.util.UUID.randomUUID().toString().take(8)
            val start = SystemClock.uptimeMillis()
            val peerRecord = client.post(accounts[1],peer,java.util.UUID.randomUUID().toString())
            try { await(5000) { output(screen).contains(peer) } } catch (error: AssertionError) {
                instrumentation.runOnMainSync {
                    val state = field(screen,"state") as CommunityState
                    emit("community_live_diagnostic", "connected=${field(screen,"liveConnected")};busy=${field(screen,"busy")};channel=${state.pane.channel};mentions=${field(screen,"mentionsView")};cached=${state.messages.any { it.optString("id") == peerRecord.getString("id") }};queue=${(field(screen,"liveChanges") as Map<*,*>).size};clock=${System.currentTimeMillis()};created=${peerRecord.optString("created")}")
                }
                throw error
            }
            emit("community_peer_delivery_ms",(SystemClock.uptimeMillis()-start).toString())
            await { field(screen,"busy") == false }
            val own = "phone send proof " + java.util.UUID.randomUUID().toString().take(8)
            submit(screen,own)
            instrumentation.runOnMainSync { assertTrue(output(screen).contains("sending")) }
            await(5000) { (field(screen,"state") as CommunityState).pendingId == null && output(screen).contains(own) }
            instrumentation.runOnMainSync {
                val text = output(screen)
                assertEquals(1,Regex(Regex.escape(own)).findAll(text).count())
                assertTrue(text.contains("@"+accounts[1].handle))
                assertFalse(text.contains(accounts[1].id))
                assertFalse(text.contains(".000Z"))
            }
            instrumentation.waitForIdleSync(); Thread.sleep(200)
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null),"community-live-upgrade.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() }; instrumentation.waitForIdleSync() }
            client.cancel(); store.save(original); CommunityActivity.debugBackend = null
        }
    }
}
