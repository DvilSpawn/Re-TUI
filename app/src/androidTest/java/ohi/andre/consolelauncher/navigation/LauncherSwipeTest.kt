package ohi.andre.consolelauncher.navigation

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Test

class LauncherSwipeTest {
    @Test fun outputScrollDoesNotOpenDrawerButStatusSwipeDoes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var home = instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClass(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        SystemClock.sleep(1200)
        val option = ohi.andre.consolelauncher.managers.xml.options.Behavior.swipe_up_apps_drawer
        val saved = ohi.andre.consolelauncher.managers.settings.LauncherSettings.get(option)
        instrumentation.runOnMainSync {
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.set(option, "true")
            home.recreate()
        }
        SystemClock.sleep(1800)
        home = LauncherActivity.instance!!
        try {
        instrumentation.runOnMainSync {
            val ui = home.uiManager!!
            assertTrue("Test requires swipe-up enabled", ohi.andre.consolelauncher.managers.settings.LauncherSettings
                .getBoolean(ohi.andre.consolelauncher.managers.xml.options.Behavior.swipe_up_apps_drawer))
            assertFalse(ui.isAppsDrawerOpen)
            fun swipe(view: View) {
                val start = SystemClock.uptimeMillis()
                for (i in 0..5) {
                    val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; 5 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                    MotionEvent.obtain(start, start + i * 20L, action, 30f, 300f-i*50, 0).also {
                        view.dispatchTouchEvent(it)
                        it.recycle()
                    }
                }
            }
            swipe(home.findViewById(R.id.terminal_view))
            assertFalse("Output scrolling must not open drawer", ui.isAppsDrawerOpen)
            swipe(home.findViewById(R.id.tv0))
            assertTrue("Non-scrolling status swipe must open drawer", ui.isAppsDrawerOpen)
        }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        } finally {
            instrumentation.runOnMainSync {
                ohi.andre.consolelauncher.managers.settings.LauncherSettings.set(option, saved)
                home.recreate()
            }
        }
    }
}
