package ohi.andre.consolelauncher.tuils

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.notes.RememberActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarGestureButtonIntegrationTest {
    @Test fun holdDragReleaseExecutesOnceAndCancellationNeverExecutes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, RememberActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RememberActivity
        val executed = mutableListOf<ToolbarGestureDirection>()
        lateinit var button: ToolbarGestureButton
        fun touch(action: Int, x: Float = 100f, y: Float = 100f) {
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                MotionEvent.obtain(now, now, action, x, y, 0).also {
                    button.dispatchTouchEvent(it)
                    it.recycle()
                }
            }
        }
        fun hold() {
            touch(MotionEvent.ACTION_DOWN)
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 100)
            instrumentation.waitForIdleSync()
        }
        try {
            instrumentation.runOnMainSync {
                button = ToolbarGestureButton(activity) { executed.add(it) }
                (activity.window.decorView as android.view.ViewGroup).addView(button, FrameLayout.LayoutParams(200, 200))
            }
            val delta = 80 * activity.resources.displayMetrics.density
            val ends = listOf(100f to 100f-delta, 100f to 100f+delta, 100f-delta to 100f, 100f+delta to 100f)
            for ((x, y) in ends) {
                hold()
                touch(MotionEvent.ACTION_MOVE, x, y)
                touch(MotionEvent.ACTION_UP, x, y)
            }
            assertEquals(ToolbarGestureDirection.entries.toList(), executed)
            hold()
            touch(MotionEvent.ACTION_MOVE, 100f, 100f-delta)
            touch(MotionEvent.ACTION_CANCEL)
            hold()
            touch(MotionEvent.ACTION_MOVE, 100f, 100f-delta)
            touch(MotionEvent.ACTION_MOVE)
            touch(MotionEvent.ACTION_UP)
            touch(MotionEvent.ACTION_DOWN)
            touch(MotionEvent.ACTION_MOVE, 100f+delta, 100f)
            touch(MotionEvent.ACTION_UP, 100f+delta, 100f)
            assertEquals(4, executed.size)
        } finally {
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
        }
    }
}
