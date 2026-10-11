package ohi.andre.consolelauncher.help

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Synthetic overlapping controls in a disposable scene; never opens or clears community storage. */
class ResizeCornerDispatchIntegrationTest {
    @get:org.junit.Rule val readerPreferences = ohi.andre.consolelauncher.help.HelpReaderPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun bounds(view: View): Rect {
        val xy = IntArray(2); view.getLocationOnScreen(xy)
        return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }
    private fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        for ((index, action) in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).withIndex()) {
            val event = MotionEvent.obtain(now, now + index * 50L, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event); event.recycle()
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun sendCenterYieldsToContentWhileUnoccupiedCornerStillResizes() {
        assumeTrue("Disposable emulator only", Build.MODEL.startsWith("sdk_gphone"))
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, OfflineHelpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        try {
            lateinit var host: FrameLayout; lateinit var content: FrameLayout; lateinit var send: Button
            var submitted = 0
            instrumentation.runOnMainSync {
                host = FrameLayout(activity)
                content = FrameLayout(activity)
                val row = FrameLayout(activity)
                val title = TextView(activity).apply { text = "Touch fixture" }
                val titleRow = FrameLayout(activity).apply { addView(title) }
                row.addView(titleRow)
                content.addView(row, FrameLayout.LayoutParams(-1, Tuils.dpToPx(activity, 48)))
                send = Button(activity).apply { text = "SEND"; setOnClickListener { submitted++ } }
                content.addView(send, FrameLayout.LayoutParams(Tuils.dpToPx(activity, 48), Tuils.dpToPx(activity, 48),
                    android.view.Gravity.BOTTOM or android.view.Gravity.RIGHT).apply {
                    rightMargin = Tuils.dpToPx(activity, 8); bottomMargin = Tuils.dpToPx(activity, 16)
                })
                HelpWindowPanel(host, content, null, "touch-fixture", title, row)
                activity.setContentView(host)
            }
            instrumentation.waitForIdleSync(); SystemClock.sleep(250)
            val corner = host.findViewWithTag<View>("touch-fixture-resize-corner")
            lateinit var sendBounds: Rect; lateinit var cornerBounds: Rect; lateinit var panelBounds: Rect
            instrumentation.runOnMainSync {
                sendBounds = bounds(send); cornerBounds = bounds(corner); panelBounds = bounds(corner.parent as View)
                assertTrue("Fixture must reproduce real overlap", cornerBounds.contains(sendBounds.centerX(), sendBounds.centerY()))
                assertEquals(Tuils.dpToPx(activity, 48), corner.width)
                assertEquals(Tuils.dpToPx(activity, 48), corner.height)
                assertTrue("Resize remains accessible", corner.isFocusable)
            }
            tap(sendBounds.exactCenterX(), sendBounds.exactCenterY())
            instrumentation.runOnMainSync {
                assertEquals("SEND receives a touchscreen sequence", 1, submitted)
                assertEquals("SEND does not grow the window", panelBounds, bounds(corner.parent as View))
                content.removeView(send)
            }
            instrumentation.waitForIdleSync()
            tap(cornerBounds.exactCenterX(), cornerBounds.exactCenterY())
            instrumentation.runOnMainSync {
                assertNotEquals("Unoccupied corner center still resizes", panelBounds, bounds(corner.parent as View))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
