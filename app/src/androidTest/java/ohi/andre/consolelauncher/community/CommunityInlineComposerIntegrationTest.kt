package ohi.andre.consolelauncher.community

import android.content.Intent
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ohi.andre.consolelauncher.R

/** Store-clearing fixture: run only on a disposable emulator, never a user's persistent debug app. */
class CommunityInlineComposerIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun call(activity: CommunityActivity, name: String, vararg args: Any) {
        activity.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(activity, *args)
    }
    private fun submit(activity: CommunityActivity, value: String) {
        activity.findViewById<EditText>(R.id.community_input).setText(value)
        activity.findViewById<View>(R.id.community_send).performClick()
    }
    private fun run(seed: CommunityState = CommunityState(), test: (CommunityActivity) -> Unit) {
        assumeTrue("Disposable emulator only", Build.MODEL.startsWith("sdk_gphone"))
        val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear(); store.save(seed.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try { test(activity) } finally {
            instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync()
            fixture.close(); CommunityActivity.debugBackend = null; store.clear()
        }
    }
    @Test fun reservedDockAndViewportDoNotJumpAcrossGeneralForumsThreadsAndMentions() {
        run(CommunityState().apply { draft = "general draft" }) { activity ->
            instrumentation.runOnMainSync {
                val root = activity.findViewById<View>(R.id.community_panel).rootView
                fun measure() {
                    repeat(3) {
                        root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    }
                }
                measure()
                val viewport = activity.findViewById<View>(R.id.community_scroll)
                val prompt = activity.findViewById<View>(R.id.community_prompt_row)
                val input = activity.findViewById<EditText>(R.id.community_input)
                val height = viewport.height; val dockHeight = prompt.height
                assertTrue(height > 0)
                for (channel in listOf("bugs", "help", "features", "presets", "general")) {
                    call(activity, "selectChannel", channel); measure()
                    assertEquals(height, viewport.height); assertEquals(dockHeight, prompt.height)
                }
                call(activity, "showMentions"); measure()
                assertEquals(height, viewport.height); assertEquals(dockHeight, prompt.height)
                assertEquals(View.INVISIBLE, prompt.visibility); assertFalse(input.isEnabled); assertFalse(input.hasFocus())
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, prompt.importantForAccessibility)
                val now = android.os.SystemClock.uptimeMillis()
                val event = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, input.x + 2, input.y + 2, 0)
                try { prompt.dispatchTouchEvent(event) } finally { event.recycle() }
                assertFalse(input.hasFocus())
                call(activity, "back"); measure()
                assertEquals(height, viewport.height); assertEquals("general draft", input.text.toString())
            }
        }
    }
    @Test fun sameComposerMasksSecretsAndCancelRestoresContextWithoutSerialization() {
        run(CommunityState().apply { draft = "general kept"; pane.channel = "help"; pane.composing = true; pane.saveDraft("help", "title kept", "forum kept") }) { activity ->
            instrumentation.runOnMainSync {
                val input = activity.findViewById<EditText>(R.id.community_input)
                submit(activity, "/register"); submit(activity, "alice")
                assertSame(input, activity.findViewById<EditText>(R.id.community_input))
                assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
                assertFalse(input.isSaveEnabled)
                assertTrue(input.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                input.setText("/exit123")
                call(activity, "saveCurrentDraft"); call(activity, "persist")
                assertFalse(CommunityStore(context).load().toString().contains("/exit123"))
                activity.findViewById<View>(R.id.community_auth_cancel).performClick()
                assertEquals("forum kept", input.text.toString())
                assertEquals("title kept", activity.findViewById<EditText>(R.id.community_thread_title).text.toString())
                assertNull(input.transformationMethod)
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                assertEquals("general kept", CommunityState(CommunityStore(context).load()).draft)
            }
        }
    }
    @Test fun keyboardEnterAdvancesUsernameAndRecreationDropsPasswordNotDraft() {
        run(CommunityState().apply { draft = "draft kept" }) { activity ->
            instrumentation.runOnMainSync { submit(activity, "/login"); activity.findViewById<EditText>(R.id.community_input).apply { setText("alice"); requestFocus() } }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
            instrumentation.waitForIdleSync()
            var replacement: CommunityActivity? = null
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync {
                    val input = activity.findViewById<EditText>(R.id.community_input)
                    assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
                    input.setText("rotation-secret"); activity.recreate()
                }
                replacement = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals("draft kept", replacement!!.findViewById<EditText>(R.id.community_input).text.toString())
                    assertFalse(CommunityStore(context).load().toString().contains("rotation-secret"))
                    assertEquals(View.INVISIBLE, replacement!!.findViewById<View>(R.id.community_auth_cancel).visibility)
                }
            } finally { instrumentation.removeMonitor(monitor); replacement?.let { next -> instrumentation.runOnMainSync { next.finish() } } }
        }
    }
    @Test fun commandChipOnlyInsertsAndUsernameErrorStaysInline() {
        run { activity ->
            instrumentation.runOnMainSync {
                activity.findViewById<View>(R.id.community_completions).findViewWithTag<View>("community_command_/register").performClick()
                val input = activity.findViewById<EditText>(R.id.community_input)
                assertEquals("/register", input.text.toString())
                assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.community_auth_cancel).visibility)
                activity.findViewById<View>(R.id.community_send).performClick(); submit(activity, "bad username")
                assertEquals("bad username", input.text.toString())
                assertTrue(activity.findViewById<TextView>(R.id.community_auth_guidance).text.contains("ASCII"))
                submit(activity, "/cancel"); assertFalse(activity.isFinishing)
                assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.community_auth_cancel).visibility)
            }
        }
    }
    @Test fun newThreadCancelsPasswordAndConfirmationWithoutSubmittingPreservedDraft() {
        for (registering in listOf(false, true)) {
            run(CommunityState().apply { pane.channel = "help"; pane.saveDraft("help", "title kept", "forum kept") }) { activity ->
                instrumentation.runOnMainSync {
                    val input = activity.findViewById<EditText>(R.id.community_input)
                    submit(activity, if (registering) "/register" else "/login")
                    val guidance = activity.findViewById<TextView>(R.id.community_auth_guidance)
                    assertTrue(guidance.text.contains("3–24")); assertTrue(guidance.text.contains("No email"))
                    submit(activity, "alice")
                    assertTrue(guidance.text.contains("8–71")); assertTrue(guidance.text.contains("recovery"))
                    if (registering) submit(activity, "secret123")
                    activity.findViewById<View>(R.id.community_actions).findViewWithTag<View>("community_new_thread").performClick()
                    assertNull(input.transformationMethod)
                    assertEquals("forum kept", input.text.toString())
                    assertEquals("title kept", activity.findViewById<EditText>(R.id.community_thread_title).text.toString())
                    assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.community_auth_cancel).visibility)
                    activity.findViewById<View>(R.id.community_send).performClick()
                    call(activity, "persist")
                    val restored = CommunityState(CommunityStore(context).load())
                    assertNull(restored.account)
                    assertEquals("forum kept", restored.pane.draft("help").optString("body"))
                    assertFalse(restored.toJson().toString().contains("secret123"))
                }
            }
        }
    }
    @Test fun suggestionFontFitsReservedStripAcrossAuthentication() {
        run { activity ->
            instrumentation.runOnMainSync {
                val rows = activity.findViewById<android.widget.LinearLayout>(R.id.community_completions)
                val chip = rows.getChildAt(0) as TextView
                val metrics = chip.paint.fontMetricsInt
                val strip = activity.findViewById<View>(R.id.community_completion_scroll)
                assertTrue(strip.layoutParams.height >= metrics.bottom - metrics.top + ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 12))
                val height = strip.layoutParams.height
                submit(activity, "/login"); submit(activity, "alice")
                assertEquals(height, strip.layoutParams.height)
                activity.findViewById<View>(R.id.community_auth_cancel).performClick()
                assertEquals(height, strip.layoutParams.height)
            }
        }
    }
}
