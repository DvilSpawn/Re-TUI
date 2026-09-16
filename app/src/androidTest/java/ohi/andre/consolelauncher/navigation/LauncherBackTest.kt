package ohi.andre.consolelauncher.navigation

import android.content.Intent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherBackTest {
    @Test fun systemBackClosesDrawerAndKeepsHomeInstance() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val home = instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(1500) // Let the configured startup keyboard settle.
        val task = home.taskId
        fun pressBack() {
            if (InstrumentationRegistry.getArguments().getString("backGesture") == "true") {
                val bounds = android.graphics.Rect()
                instrumentation.runOnMainSync { home.window.decorView.getWindowVisibleDisplayFrame(bounds) }
                val y = bounds.centerY()
                instrumentation.uiAutomation.executeShellCommand("input swipe 1 $y ${bounds.width() / 2} $y 300").use {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
                }
            } else instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            android.os.SystemClock.sleep(400)
            instrumentation.waitForIdleSync()
        }
        instrumentation.runOnMainSync {
            assertTrue(home.onBackPressedDispatcher.hasEnabledCallbacks())
            androidx.core.view.WindowInsetsControllerCompat(home.window, home.window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        android.os.SystemClock.sleep(700)
        instrumentation.runOnMainSync {
            assertFalse("Keyboard remained visible before drawer test", androidx.core.view.ViewCompat.getRootWindowInsets(home.window.decorView)!!
                .isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
            home.uiManager!!.showAppsDrawer()
            assertTrue(home.uiManager!!.isAppsDrawerOpen)
        }
        android.os.SystemClock.sleep(400)
        pressBack()
        instrumentation.runOnMainSync { assertFalse(home.uiManager!!.isAppsDrawerOpen) }
        repeat(3) { pressBack() }
        instrumentation.runOnMainSync { home.uiManager!!.activateTerminalInput(true) }
        android.os.SystemClock.sleep(700)
        instrumentation.runOnMainSync {
            assertTrue("Keyboard did not open", androidx.core.view.ViewCompat.getRootWindowInsets(home.window.decorView)!!
                .isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
        }
        pressBack()
        instrumentation.runOnMainSync {
            assertFalse("Back did not dismiss keyboard", androidx.core.view.ViewCompat.getRootWindowInsets(home.window.decorView)!!
                .isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
        }
        instrumentation.runOnMainSync {
            assertSame(home, LauncherActivity.instance)
            assertFalse(home.isFinishing)
            assertFalse(home.isDestroyed)
            assertEquals(task, home.taskId)
        }
    }
}
