package ohi.andre.consolelauncher.wallpaper

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperLifecycleTest {
    @Test(timeout = 60000) fun homeAndSettingsTransitionsKeepMainThreadResponsive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        repeat(3) {
            // Home is singleTask: startActivitySync waits for a new instance that may never exist.
            instrumentation.runOnMainSync {
                context.startActivity(Intent(context, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            instrumentation.waitForIdleSync()
            val settings = instrumentation.startActivitySync(Intent(context, ThemerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { settings.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    @Test fun disabledInteractionRejectsTouchesAndCanResume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = PixelDreamView(instrumentation.targetContext)
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
            try {
                view.setInteractionEnabled(true)
                assertTrue(view.handleTouch(event))
                view.setInteractionEnabled(false)
                assertFalse(view.handleTouch(event))
                view.setInteractionEnabled(true)
                assertTrue(view.handleTouch(event))
            } finally {
                event.recycle()
            }
        }
    }
}
