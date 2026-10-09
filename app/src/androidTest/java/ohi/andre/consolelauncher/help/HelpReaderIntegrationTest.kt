package ohi.andre.consolelauncher.help

import android.content.Intent
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import ohi.andre.consolelauncher.R

class HelpReaderIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun explicitFormattingUsesNativeStylesAndNonExecutableBlocks() {
        instrumentation.runOnMainSync {
            val text = android.text.SpannableStringBuilder()
            HelpText.body(text, "## 見出し\n\n**bold** *emphasis* `help`\n\n- One\n1. Two\n\n```\ncalc 2 * (3 + 4)\n```\n\n>! Warning", 0xff303030.toInt(), 12)
            assertTrue(text.getSpans(0, text.length, android.text.style.StyleSpan::class.java).size >= 4)
            assertTrue(text.getSpans(0, text.length, android.text.style.TypefaceSpan::class.java).size >= 2)
            assertEquals("Code and warning are full-width native blocks", 2,
                text.getSpans(0, text.length, android.text.style.LineBackgroundSpan::class.java).size)
            assertTrue(text.toString().contains("• One"))
            assertTrue(text.toString().contains("1. Two"))
            assertTrue("Math inside code stays literal", text.toString().contains("calc 2 * (3 + 4)"))
            assertEquals("Examples cannot execute", 0, text.getSpans(0, text.length, android.text.style.ClickableSpan::class.java).size)
        }
    }
    @Test fun titleAndExplicitMarkupHaveNativeSpans() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, OfflineHelpActivity::class.java)
            .putExtra("topic", "terminal-basics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val text = activity.findViewById<TextView>(R.id.offline_help_output).text as Spanned
                assertTrue("Article title is bold", text.getSpans(0, 15, StyleSpan::class.java).isNotEmpty())
                assertTrue("Article title is larger", text.getSpans(0, 15, RelativeSizeSpan::class.java).isNotEmpty())
                assertFalse("Markup is not displayed", text.toString().contains("## "))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun windowControlsAndAccessibleResizeHandlesSurviveRecreation() {
        var activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, OfflineHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        val monitor = instrumentation.addMonitor(OfflineHelpActivity::class.java.name, null, false)
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val control = activity.window.decorView.findViewWithTag<View>("help-window-toggle")
                assertNotNull("Window toggle is available", control)
                val panel = activity.window.decorView.findViewWithTag<View>("help-resize-corner").parent as View
                val host = panel.parent as View
                assertEquals("Corners exposed on first launch", View.VISIBLE,
                    activity.window.decorView.findViewWithTag<View>("help-resize-corner").visibility)
                assertTrue("Symmetric outer horizontal inset", panel.left > 0 && kotlin.math.abs(panel.left - (host.width - panel.right)) <= 1)
                assertTrue("Symmetric outer vertical inset", panel.top > 0 && kotlin.math.abs(panel.top - (host.height - panel.bottom)) <= 1)
                listOf("top-left", "top-right", "bottom-left").forEach {
                    assertEquals(View.VISIBLE, activity.window.decorView.findViewWithTag<View>("help-resize-$it").visibility)
                }
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val handle = activity.window.decorView.findViewWithTag<View>("help-resize-corner")
                assertNotNull("Visible resize corner", handle)
                assertEquals(View.VISIBLE, handle.visibility)
                assertTrue("Resize is accessible", handle.isFocusable)
                assertTrue(handle.contentDescription.isNotEmpty())
                handle.performClick()
                activity.recreate()
            }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as OfflineHelpActivity
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(View.VISIBLE, activity.window.decorView.findViewWithTag<View>("help-resize-corner").visibility)
                activity.window.decorView.findViewWithTag<View>("help-window-toggle").performClick()
                assertEquals(View.GONE, activity.window.decorView.findViewWithTag<View>("help-resize-corner").visibility)
            }
        } finally { instrumentation.removeMonitor(monitor); instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun touchLongPressOnCornerShrinksBothAxes() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, OfflineHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val handle = activity.window.decorView.findViewWithTag<View>("help-resize-corner")
                val panel = handle.parent as View
                val before = panel.layoutParams.width
                val height = panel.layoutParams.height
                val now = android.os.SystemClock.uptimeMillis()
                listOf(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 10f, 10f, 0),
                    android.view.MotionEvent.obtain(now, now + 1000, android.view.MotionEvent.ACTION_UP, 10f, 10f, 0)).forEach {
                    handle.dispatchTouchEvent(it); it.recycle()
                }
                assertTrue("Long press shrinks width", panel.layoutParams.width < before)
                assertTrue("Long press shrinks height", panel.layoutParams.height < height)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun dragResizeAndRotationStayInSafeViewportAndReflowText() {
        var activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, OfflineHelpActivity::class.java)
            .putExtra("topic", "calculator-help").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        val monitor = instrumentation.addMonitor(OfflineHelpActivity::class.java.name, null, false)
        fun screenshot(name: String) {
            instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(300)
            java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        fun drag(view: View, dx: Float, dy: Float) {
            val now = android.os.SystemClock.uptimeMillis()
            listOf(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 20f, 20f, 0),
                android.view.MotionEvent.obtain(now, now + 100, android.view.MotionEvent.ACTION_MOVE, 20f + dx, 20f + dy, 0),
                android.view.MotionEvent.obtain(now, now + 120, android.view.MotionEvent.ACTION_UP, 20f + dx, 20f + dy, 0)).forEach {
                view.dispatchTouchEvent(it); it.recycle()
            }
        }
        var textSize = 0f
        try {
            screenshot("help-reader-default-floating.png")
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<View>("help-window-toggle").performClick() }
            screenshot("help-reader-maximized.png")
            instrumentation.runOnMainSync { activity.window.decorView.findViewWithTag<View>("help-window-toggle").performClick() }
            instrumentation.runOnMainSync {
                val output = activity.findViewById<TextView>(R.id.offline_help_output)
                val offset = output.text.toString().indexOf("Expressions and operators")
                activity.findViewById<android.widget.ScrollView>(R.id.offline_help_scroll)
                    .scrollTo(0, output.layout.getLineTop(output.layout.getLineForOffset(offset)))
            }
            screenshot("help-reader-examples.png")
            instrumentation.runOnMainSync {
                activity.findViewById<android.widget.ScrollView>(R.id.offline_help_scroll).scrollTo(0, 0)
                textSize = activity.findViewById<TextView>(R.id.offline_help_output).textSize
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val edge = activity.window.decorView.findViewWithTag<View>("help-resize-corner")
                val panel = edge.parent as View
                val oldHeight = panel.layoutParams.height
                drag(edge, -10000f, 0f)
                assertEquals(oldHeight, panel.layoutParams.height)
                assertEquals(minOf(ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 320),
                    kotlin.math.round((panel.parent as View).width * .9f).toInt()), panel.layoutParams.width)
                drag(edge, 0f, -10000f)
                assertEquals(minOf(ohi.andre.consolelauncher.tuils.Tuils.dpToPx(activity, 360),
                    kotlin.math.round((panel.parent as View).height * .9f).toInt()), panel.layoutParams.height)
                drag(activity.findViewById(R.id.offline_help_header), -10000f, -10000f)
                val params = panel.layoutParams as android.widget.FrameLayout.LayoutParams
                assertEquals(0, params.leftMargin); assertEquals(0, params.topMargin)
                drag(activity.window.decorView.findViewWithTag("help-resize-corner"), 10000f, 10000f)
                assertEquals((panel.parent as View).width, panel.layoutParams.width)
                assertEquals((panel.parent as View).height, panel.layoutParams.height)
                activity.window.decorView.findViewWithTag<View>("help-resize-corner").performLongClick()
                assertEquals(textSize, activity.findViewById<TextView>(R.id.offline_help_output).textSize, .01f)
            }
            screenshot("help-reader-windowed.png")
            instrumentation.runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as OfflineHelpActivity
            instrumentation.waitForIdleSync(); android.os.SystemClock.sleep(300)
            instrumentation.runOnMainSync {
                val panel = activity.window.decorView.findViewWithTag<View>("help-resize-corner").parent as View
                val host = panel.parent as View
                assertTrue(panel.width <= host.width); assertTrue(panel.height <= host.height)
                assertTrue(panel.left >= 0 && panel.top >= 0)
                assertTrue(panel.right <= host.width && panel.bottom <= host.height)
                assertEquals(textSize, activity.findViewById<TextView>(R.id.offline_help_output).textSize, .01f)
                val field = activity.findViewById<View>(R.id.offline_help_input)
                val visible = android.graphics.Rect()
                assertTrue(field.getGlobalVisibleRect(visible)); assertEquals(field.height, visible.height())
                assertTrue(activity.findViewById<View>(R.id.offline_help_scroll).height > 0)
            }
            screenshot("help-reader-landscape.png")
        } finally { instrumentation.removeMonitor(monitor); instrumentation.runOnMainSync { activity.finish() } }
    }
}
