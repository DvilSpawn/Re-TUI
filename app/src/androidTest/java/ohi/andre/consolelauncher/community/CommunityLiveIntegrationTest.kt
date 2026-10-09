package ohi.andre.consolelauncher.community

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in production HTTPS check. The operator must remove the emitted exact fixtures. */
class CommunityLiveIntegrationTest {
    @Test fun twoAccountsRegisterPostReadAndExitLocallyOverProductionTls() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveCommunity") == "true")
        assumeTrue("Disposable emulator only", Build.MODEL.startsWith("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        CommunityActivity.debugBackend = null
        val store = CommunityStore(context); store.clear()
        val names = (1..2).map { "and_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16) }
        val passwords = (1..2).map { java.util.UUID.randomUUID().toString() }
        fun emit(name: String, id: String = "") {
            instrumentation.sendStatus(2, Bundle().apply { putString("community_fixture_username", name); if (id.isNotEmpty()) putString("community_fixture_member_id", id) })
        }
        names.forEach { emit(it) }
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        fun await(timeout: Long = 30000, check: () -> Boolean) {
            val end = android.os.SystemClock.uptimeMillis() + timeout
            while (android.os.SystemClock.uptimeMillis() < end) {
                var passed = false; instrumentation.runOnMainSync { passed = check() }
                if (passed) return
                Thread.sleep(100)
            }
            fail("Production community acceptance timed out (no credentials logged)")
        }
        fun idle() = await { !(activity.javaClass.getDeclaredField("busy").apply { isAccessible = true }.get(activity) as Boolean) }
        fun submit(text: String) {
            idle()
            instrumentation.runOnMainSync {
                activity.window.decorView.findViewWithTag<EditText>("community_input").setText(text)
                activity.window.decorView.findViewWithTag<android.view.View>("community_submit").performClick()
            }
        }
        fun register(index: Int): String {
            submit("/register")
            val deadline = android.os.SystemClock.uptimeMillis() + 180000
            while (true) {
                for (value in listOf(names[index], passwords[index], passwords[index])) submit(value)
                idle()
                val account = CommunityState(store.load()).account
                if (account != null) { emit(names[index], account.id); return account.id }
                var delay = 0L
                var safeStatus = ""
                instrumentation.runOnMainSync {
                    val status = activity.window.decorView.findViewWithTag<TextView>("community_status").text.toString()
                    safeStatus = status
                    if (status.contains("Rate limited")) delay = Regex("[0-9]+").find(status)?.value?.toLong() ?: 60L
                }
                assertTrue("Production registration rejected: $safeStatus (safe fixture names already emitted)", delay > 0 && android.os.SystemClock.uptimeMillis() + delay * 1000 < deadline)
                Thread.sleep(delay * 1000)
            }
        }
        try {
            val first = register(0)
            val marker = "android_https_" + java.util.UUID.randomUUID().toString()
            for (body in listOf(marker, "exit")) {
                submit(body)
                await { activity.window.decorView.findViewWithTag<TextView>("community_status").text.contains("Posted") }
                assertNull(CommunityState(store.load()).pendingId)
            }
            submit("/logout")
            val second = register(1); assertNotEquals(first, second)
            await { CommunityState(store.load()).messages.any { it.optString("author") == first && it.optString("body") == marker } }
            val received = CommunityState(store.load()).messages
            assertTrue(received.any { it.optString("author") == first && it.optString("body") == "exit" })
            instrumentation.runOnMainSync { assertTrue(activity.window.decorView.findViewWithTag<TextView>("community_output").text.contains(marker)) }
            submit("/exit"); await { activity.isFinishing }
            assertFalse(CommunityState(store.load()).messages.any { it.optString("author") == second })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync()
            store.clear(); CommunityActivity.debugBackend = null
        }
    }
}
