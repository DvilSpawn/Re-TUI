package ohi.andre.consolelauncher.community

import android.app.AlertDialog
import android.os.SystemClock
import android.view.WindowManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Local fixture only; never creates production accounts or touches the main Launcher package. */
class CommunityReleaseGatesIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun call(screen: CommunityActivity, name: String) = screen.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(screen)
    private fun dialog(screen: CommunityActivity, field: String) = screen.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(screen) as AlertDialog
    private fun await(check: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 8000
        while (!check() && SystemClock.uptimeMillis() < until) { instrumentation.waitForIdleSync(); SystemClock.sleep(50) }
        assertTrue(check())
    }
    private fun run(account: CommunityClient.Account, test: (ActivityScenario<CommunityActivity>, CommunityPaneHttpFixture, CommunityStore) -> Unit) {
        check(context.packageName.endsWith(".fetchdebug"))
        val store = CommunityStore(context); val saved = store.load(); val fixture = CommunityPaneHttpFixture()
        store.save(CommunityState().apply { signIn(account); draft = "my unchanged draft" }.toJson())
        CommunityActivity.debugBackend = fixture.url
        try { ActivityScenario.launch(CommunityActivity::class.java).use { scenario ->
            await { fixture.requests.any { it.method == "GET" && it.path.endsWith("messages/records") } }
            await {
                var busy = true
                scenario.onActivity { busy = it.javaClass.getDeclaredField("busy").apply { isAccessible = true }.getBoolean(it) }
                !busy
            }
            test(scenario, fixture, store)
        } } finally { fixture.close(); CommunityActivity.debugBackend = null; store.save(saved) }
    }
    @Test fun rulesAcceptanceDoesNotPostOrLoseTheDraft() = run(CommunityClient.Account("member000000001", "alice", "fixture-token")) { scenario, fixture, store ->
        scenario.onActivity { call(it, "submit") }
        assertFalse(fixture.requests.any { it.method == "POST" && it.path.endsWith("messages/records") })
        scenario.onActivity { dialog(it, "rulesDialog").getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
        instrumentation.waitForIdleSync()
        assertEquals("my unchanged draft", store.load().getString("draft"))
        scenario.onActivity { call(it, "submit") }
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(context.externalCacheDir, "community-rules.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        scenario.onActivity { dialog(it, "rulesDialog").getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
        await { fixture.requests.any { it.path.endsWith("/policy") } }
        await { store.load().getJSONObject("account").optString("policy_version") == CommunityClient.POLICY_VERSION }
        assertFalse(fixture.requests.any { it.method == "POST" && it.path.endsWith("messages/records") })
        scenario.onActivity { assertEquals("my unchanged draft", it.findViewById<EditText>(R.id.community_input).text.toString()); call(it, "submit") }
        await { fixture.messages.size == 1 }
    }
    @Test fun deletionMasksClearsSecretsAndOnlyClearsAccountAfterAcknowledgement() = run(CommunityClient.Account("member000000001", "alice", "fixture-token", policyVersion = CommunityClient.POLICY_VERSION)) { scenario, fixture, store ->
        var cancelledPassword: EditText? = null
        scenario.onActivity {
            call(it, "deleteAccount")
            val dialog = dialog(it, "deletionDialog"); val password = dialog.findViewById<android.widget.FrameLayout>(android.R.id.custom).getChildAt(0) as EditText
            assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(password.isSaveEnabled); assertNotNull(password.transformationMethod)
            cancelledPassword = password
            password.setText("fixture-secret"); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        val reads = fixture.requests.count { it.method == "GET" && it.path.endsWith("messages/records") }
        fixture.readGate = java.util.concurrent.CountDownLatch(1)
        scenario.onActivity { it.javaClass.getDeclaredMethod("refresh", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(it, false, false) }
        await { fixture.requests.count { it.method == "GET" && it.path.endsWith("messages/records") } > reads }
        scenario.onActivity {
            assertEquals("", cancelledPassword!!.text.toString()); assertTrue(store.load().has("account"))
            call(it, "deleteAccount")
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity {
            val next = dialog(it, "deletionDialog"); (next.findViewById<android.widget.FrameLayout>(android.R.id.custom).getChildAt(0) as EditText).setText("fixture-secret")
            next.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        fixture.readGate?.countDown(); fixture.readGate = null
        await { fixture.requests.any { it.path.endsWith("account-delete") } }
        await { fixture.requests.any { it.path.endsWith("account-delete") } && !store.load().has("account") }
    }
    @Test fun moderatorReviewResolvesWithConfirmationWithoutPersistingPrivateEvidence() = run(CommunityClient.Account("member000000001", "dvil", "fixture-token", true, CommunityClient.POLICY_VERSION)) { scenario, fixture, store ->
        fixture.moderationRows.add(JSONObject().put("id", "report000000001").put("queue", "moderator_requests")
            .put("body", "PRIVATE_EVIDENCE").put("author_handle", "alice").put("source_id", "message00000001").put("snapshot_body", "reported body"))
        scenario.onActivity { it.javaClass.getDeclaredMethod("reviewQueue", String::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(it, "moderator_requests", 1) }
        await {
            var showing = false
            scenario.onActivity { showing = (it.javaClass.getDeclaredField("moderationDialog").apply { isAccessible = true }.get(it) as? AlertDialog)?.isShowing == true }
            showing
        }
        scenario.onActivity {
            val list = dialog(it, "moderationDialog").listView; list.performItemClick(list.getChildAt(0), 0, 0)
            dialog(it, "moderationDialog").getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity {
            val actions = dialog(it, "moderationDialog").listView; actions.performItemClick(actions.getChildAt(0), 0, 0)
            assertFalse(fixture.requests.any { r -> r.path.endsWith("/moderation/action") })
            dialog(it, "moderationDialog").getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        await { fixture.moderationRows.isEmpty() }
        assertFalse(store.load().toString().contains("PRIVATE_EVIDENCE"))
    }
}
