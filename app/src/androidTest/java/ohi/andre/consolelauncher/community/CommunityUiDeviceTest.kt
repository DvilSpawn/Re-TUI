package ohi.andre.consolelauncher.community

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Uses current settings and drafts without clearing storage, changing themes, or submitting input. */
class CommunityUiDeviceTest {
    @get:org.junit.Rule val windowPreferences = ohi.andre.consolelauncher.help.WindowPreferencesRule(fresh = false)
    @Test fun expandedPaneMatchesChipHeightAndCapturesCurrentDeviceUi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CommunityActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                if (activity.window.decorView.findViewWithTag<View>("community-resize-corner").visibility == View.VISIBLE)
                    activity.window.decorView.findViewWithTag<View>("community-window-toggle").performClick()
                val input = activity.findViewById<EditText>(R.id.community_input)
                input.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            android.os.SystemClock.sleep(750)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val header = activity.findViewById<TextView>(R.id.community_header)
                val panel = (activity.findViewById<View>(R.id.community_panel).parent as View).parent as View
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, panel.layoutParams.width)
                assertEquals(View.GONE, header.rootView.findViewWithTag<View>("community-resize-corner").visibility)
                assertEquals("Header isn't truncated on this device", 0, header.layout.getEllipsisCount(0))
                val border = activity.findViewById<View>(R.id.community_panel)
                val dock = activity.findViewById<View>(R.id.community_completion_scroll)
                assertEquals("No bottom blank band", 0, border.paddingBottom)
                assertEquals(border.height, dock.bottom)
                val dockParams = dock.layoutParams as ViewGroup.MarginLayoutParams
                assertEquals("Expanded chips use the full row", 0, dockParams.marginStart)
                assertEquals(0, dockParams.marginEnd)
                val guidance = activity.findViewById<View>(R.id.community_guidance_row)
                if (guidance.visibility == View.GONE) {
                    val actions = activity.findViewById<View>(R.id.community_actions).parent as View
                    assertEquals(actions.bottom, activity.findViewById<View>(R.id.community_prompt_row).top)
                }
                val rows = activity.findViewById<LinearLayout>(R.id.community_completions)
                for (index in 0 until rows.childCount) {
                    val target = rows.getChildAt(index) as FrameLayout
                    val chip = target.getChildAt(0) as TextView
                    val home = SuggestionChipStyler.createView(activity).apply { text = chip.text }
                    val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    home.measure(unspecified, unspecified)
                    assertEquals("Visible IRC chip matches home-screen height", home.measuredHeight, chip.height)
                    assertTrue(target.height >= Tuils.dpToPx(activity, 48))
                }
            }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                File(activity.getExternalFilesDir(null), "irc-expanded-ui.png").outputStream().use {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { screenshot.recycle() }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
