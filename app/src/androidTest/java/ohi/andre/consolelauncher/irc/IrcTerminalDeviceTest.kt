package ohi.andre.consolelauncher.irc

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.help.WindowPreferencesRule
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Offline UI fixtures: never register an account, send a real message, or clear user data. */
class IrcTerminalDeviceTest {
    @get:Rule val windows = WindowPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun check(block: (IrcActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, IrcActivity::class.java)
            .putExtra("irc_offline_fixture", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as IrcActivity
        try {
            val deadline = System.nanoTime() + 5_000_000_000
            var resumed = false
            while (!resumed && System.nanoTime() < deadline) {
                instrumentation.runOnMainSync { resumed = activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED && activity.onBackPressedDispatcher.hasEnabledCallbacks() }
                if (!resumed) android.os.SystemClock.sleep(25)
            }
            assertTrue("Wait for the foreground lifecycle before testing Back", resumed)
            instrumentation.waitForIdleSync(); block(activity)
        }
        finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync() }
    }
    @Test fun terminalRoutesWindowsRestoresDraftsAndHidesWireIdentity() = check { activity ->
        instrumentation.runOnMainSync {
            assertNull("No automatic connection", activity.session.client)
            assertEquals(View.GONE, activity.window.decorView.findViewWithTag<View>("irc-resize-corner").visibility)
            val s = activity.session.state; s.nickname = "dvil"
            s.receive(IrcMessage.parse(":dvil!private@198.51.100.1 JOIN #retui"))
            s.receive(IrcMessage.parse(":alice!private@198.51.100.2 PRIVMSG #retui :hello dvil"))
            activity.session.changed?.invoke()
            val input = activity.findViewById<EditText>(R.id.irc_input)
            input.setText("my unfinished reply")
            s.buffer("#help").draft = "help draft"
            input.setText("/window #help"); activity.findViewById<View>(R.id.irc_send).performClick()
            assertEquals("help draft", input.text.toString())
            input.setText("/window #retui"); activity.findViewById<View>(R.id.irc_send).performClick()
            assertEquals("my unfinished reply", input.text.toString())
            val transcript = activity.findViewById<TextView>(R.id.irc_output).text.toString()
            assertTrue(transcript.contains("<alice> hello dvil")); assertFalse(transcript.contains("198.51")); assertFalse(transcript.contains("private@"))
            input.setText("/notacommand secret"); activity.findViewById<View>(R.id.irc_send).performClick()
            assertNotNull(input.error); assertFalse(s.buffers.any { b -> b.lines.any { it.text.contains("/notacommand") } })
        }
    }
    @Test fun sharedComposerMasksCredentialsAndBackCancelsWithoutConnecting() = check { activity ->
        instrumentation.runOnMainSync {
            val input = activity.findViewById<EditText>(R.id.irc_input)
            input.setText("unsent draft"); input.setText("/login dvil")
            activity.findViewById<View>(R.id.irc_send).performClick()
            assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, input.inputType and InputType.TYPE_MASK_VARIATION)
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            input.setText("fixture-only-secret")
            activity.onBackPressedDispatcher.onBackPressed()
            assertNull(activity.session.client); assertEquals("unsent draft", input.text.toString())
            assertFalse(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertFalse(activity.session.state.buffers.any { b -> b.lines.any { it.text.contains("fixture-only-secret") } })
        }
    }
    @Test fun visibleChipsMatchLauncherHeightWithLargerTapTargetsAndCapture() = check { activity ->
        instrumentation.runOnMainSync {
            val rows = activity.findViewById<LinearLayout>(R.id.irc_suggestions)
            assertTrue(rows.childCount > 0)
            for (i in 0 until rows.childCount) {
                val hit = rows.getChildAt(i) as FrameLayout; val chip = hit.getChildAt(0) as TextView
                val home = SuggestionChipStyler.createView(activity).apply { text = chip.text }
                val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                home.measure(unspecified, unspecified)
                assertEquals(home.measuredHeight, chip.height)
                assertTrue(hit.height >= Tuils.dpToPx(activity, 48))
            }
            val panel = activity.findViewById<View>(R.id.irc_panel)
            assertEquals("No blank bottom band", panel.height, activity.findViewById<View>(R.id.irc_suggestion_scroll).bottom)
        }
        val shot = instrumentation.uiAutomation.takeScreenshot()
        try { File(activity.getExternalFilesDir(null), "irc-native-terminal.png").outputStream().use { assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { shot.recycle() }
    }
    @Test fun innerPaneAndHeaderMatchSharedChromeInExpandedAndFloatingModes() = check { activity ->
        fun bounds(view: View): Rect {
            val xy = IntArray(2); view.getLocationOnScreen(xy)
            return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
        }
        instrumentation.runOnMainSync {
            val state = activity.session.state; state.nickname = "fixture"
            state.receive(IrcMessage.parse(":fixture!u@fixture JOIN #retui"))
            state.receive(IrcMessage.parse(":alice!u@fixture PRIVMSG #retui :An offline pane fixture"))
            activity.session.changed?.invoke()
        }
        for (mode in listOf("expanded", "floating")) {
            instrumentation.waitForIdleSync()
            // Let the rendered frame catch up before capturing the new geometry.
            android.os.SystemClock.sleep(200)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val outer = activity.findViewById<View>(R.id.irc_panel)
                val inner = activity.findViewById<ViewGroup>(R.id.irc_main_pane)
                val header = activity.findViewById<View>(R.id.irc_header)
                val toggle = activity.window.decorView.findViewWithTag<View>("irc-window-toggle")
                val headerBounds = bounds(header); val outerBounds = bounds(outer)
                assertEquals("Header straddles top border", outerBounds.top.toFloat(), headerBounds.exactCenterY(), 1f)
                assertEquals("Unclipped header face", Tuils.dpToPx(activity, 20), header.height)
                assertEquals("Toggle face aligns with header", headerBounds.top, bounds(toggle).top)
                assertTrue("Toggle retains accessible hit target", toggle.width >= Tuils.dpToPx(activity, 48) && toggle.height >= Tuils.dpToPx(activity, 48))
                assertSame(outer, inner.parent); assertNotNull("Separately themed inner pane", inner.background)
                assertSame(inner, activity.findViewById<View>(R.id.irc_context).parent)
                assertSame(inner, activity.findViewById<View>(R.id.irc_scroll).parent)
                assertSame(inner, activity.findViewById<View>(R.id.irc_prompt_row).parent)
                val actions = activity.findViewById<View>(R.id.irc_suggestion_scroll)
                assertSame(outer, actions.parent)
                assertEquals("Actions below inner pane", inner.bottom + Tuils.dpToPx(activity, 2), actions.top)
                assertEquals("No blank bottom band", outer.height, actions.bottom)
                assertTrue(inner.height > 0 && activity.findViewById<View>(R.id.irc_scroll).height > 0)
            }
            val shot = instrumentation.uiAutomation.takeScreenshot()
            try { File(activity.getExternalFilesDir(null), "irc-pane-parity-$mode.png").outputStream().use { assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { shot.recycle() }
            if (mode == "expanded") instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<View>("irc-window-toggle").performClick() }
        }
    }
}
