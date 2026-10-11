package ohi.andre.consolelauncher.calculator

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.descendants
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test

class TerminalPaneStyleIntegrationTest {
    @get:org.junit.Rule val readerPreferences = ohi.andre.consolelauncher.help.HelpReaderPreferencesRule()
    @get:org.junit.Rule val windowPreferences = ohi.andre.consolelauncher.help.WindowPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun calculatorControlsUsePlainTextInsteadOfFramedCards() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CalculatorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val keys = activity.findViewById<android.view.ViewGroup>(R.id.calculator_symbols)
                for (key in keys.descendants.filterIsInstance<android.widget.Button>()) {
                    assertTrue("Keys share Termux's transparent text controls", key.background == null || (key.background as? ColorDrawable)?.color == Color.TRANSPARENT)
                }
                val help = activity.findViewById<View>(R.id.calculator_help_tab)
                assertTrue("Help uses a raised 20dp tab inside its accessible hit target", help.background is android.graphics.drawable.InsetDrawable)
                assertTrue(help.height >= Tuils.dpToPx(activity, 48))
            }
        } finally { instrumentation.runOnMainSync { activity.finishAndRemoveTask() } }
    }
    @Test fun helpUsesIntegratedFrameAndAccessibleCorners() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ohi.andre.consolelauncher.help.OfflineHelpActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val toggle = activity.window.decorView.findViewWithTag<View>("help-window-toggle")
                if (toggle.visibility == View.VISIBLE) toggle.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val title = activity.findViewById<TextView>(R.id.offline_help_header)
                val border = activity.findViewById<View>(R.id.offline_help_panel)
                val titlePosition = IntArray(2); val borderPosition = IntArray(2)
                title.getLocationOnScreen(titlePosition); border.getLocationOnScreen(borderPosition)
                assertTrue("Help title intersects the top border", titlePosition[1] <= borderPosition[1] && titlePosition[1] + title.height > borderPosition[1])
                val root = activity.findViewById<View>(R.id.offline_help_root)
                assertEquals(Color.TRANSPARENT, (root.background as? ColorDrawable)?.color ?: Color.TRANSPARENT)
                listOf(R.id.offline_help_output, R.id.offline_help_input, R.id.offline_help_prompt).forEach {
                    val text = activity.findViewById<TextView>(it)
                    assertEquals(LauncherFontScale.terminalTextSizeSp() * activity.resources.displayMetrics.scaledDensity, text.textSize, .1f)
                    assertEquals(Tuils.getTypeface(activity), text.typeface)
                }
                val keys = activity.findViewById<android.view.ViewGroup>(R.id.offline_help_keys)
                val prompt = activity.findViewById<View>(R.id.offline_help_prompt)
                assertSame(activity.findViewById<View>(R.id.offline_help_main_pane), (prompt.parent as View).parent)
                assertEquals(0, activity.findViewById<View>(R.id.offline_help_scroll).paddingLeft)
                assertFalse("Root does not repeat the chrome title", activity.findViewById<TextView>(R.id.offline_help_output).text.startsWith(activity.getString(R.string.offline_help_title)))
                val toggle = activity.window.decorView.findViewWithTag<View>("help-window-toggle")
                assertTrue(toggle.background is android.graphics.drawable.InsetDrawable)
                for (key in keys.descendants.filterIsInstance<android.widget.Button>()) {
                    assertTrue("Help uses the pane's transparent text controls", key.background == null || (key.background as? ColorDrawable)?.color == Color.TRANSPARENT)
                }
                listOf("top-left", "top-right", "bottom-left", "corner").forEach {
                    val handle = activity.window.decorView.findViewWithTag<View>("help-resize-$it")
                    val landscape = activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    assertEquals(if (landscape) View.GONE else View.VISIBLE, handle.visibility)
                    if (!landscape) {
                        assertTrue("Accessible corner width", handle.width >= Tuils.dpToPx(activity, 48))
                        assertTrue("Accessible corner height", handle.height >= Tuils.dpToPx(activity, 48))
                    }
                }
            }
        } finally { instrumentation.runOnMainSync { activity.finishAndRemoveTask() } }
    }
    @Test fun calculatorUsesIntegratedTextChromeAndTerminalTypography() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, CalculatorActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val root = activity.findViewById<View>(R.id.calculator_overlay)
                val main = activity.findViewById<View>(R.id.calculator_main_tab)
                assertTrue("Calculator title is text, not an icon tab", main is TextView)
                val title = main as TextView
                assertTrue(title.text.isNotBlank())
                val border = activity.findViewById<View>(R.id.calculator_window_border)
                assertEquals(Tuils.dpToPx(activity, 8), (border.parent as View).paddingLeft)
                assertEquals(0, activity.findViewById<View>(R.id.calculator_main_pane).paddingLeft)
                assertEquals(0, title.minWidth)
                val titlePosition = IntArray(2); val borderPosition = IntArray(2)
                title.getLocationOnScreen(titlePosition); border.getLocationOnScreen(borderPosition)
                assertTrue("Title intersects the top border", titlePosition[1] <= borderPosition[1] && titlePosition[1] + title.height > borderPosition[1])
                assertEquals("No opaque rectangular outer fill", Color.TRANSPARENT, (root.background as? ColorDrawable)?.color ?: Color.TRANSPARENT)
                listOf(R.id.calculator_expression, R.id.calculator_result, R.id.calculator_prompt).forEach {
                    val text = activity.findViewById<TextView>(it)
                    assertEquals(LauncherFontScale.terminalTextSizeSp() * activity.resources.displayMetrics.scaledDensity, text.textSize, .1f)
                    assertEquals(Tuils.getTypeface(activity), text.typeface)
                }
                assertTrue("Accessible help action", activity.findViewById<View>(R.id.calculator_help_tab).height >= Tuils.dpToPx(activity, 48))
            }
        } finally { instrumentation.runOnMainSync { activity.finishAndRemoveTask() } }
    }
}
