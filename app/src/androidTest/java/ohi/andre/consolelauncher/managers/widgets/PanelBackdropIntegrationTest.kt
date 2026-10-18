package ohi.andre.consolelauncher.managers.widgets

import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PanelBackdropIntegrationTest {
    @Test fun backGestureClosesCommandsBeforeClosingTheWidgetWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 33 && shell("settings get secure navigation_mode") == "2")
        WidgetPaneIntegrationTest().fixture(systemBarsVisible = true) { home, drawer ->
            val root = drawer.javaClass.getDeclaredField("drawerRoot").apply { isAccessible = true }.get(drawer) as View
            instrumentation.runOnMainSync { root.findViewById<View>(R.id.android_widget_command_toggle).performClick() }
            fun gesture() {
                val metrics = home.resources.displayMetrics
                shell("input swipe 1 ${metrics.heightPixels / 2} ${metrics.widthPixels / 2} ${metrics.heightPixels / 2} 350")
                instrumentation.waitForIdleSync(); Thread.sleep(500)
            }
            gesture()
            instrumentation.runOnMainSync {
                assertTrue("Back first dismisses widget commands", drawer.isOpen)
                assertEquals(View.GONE, root.findViewById<View>(R.id.android_widget_commands).visibility)
            }
            gesture()
            instrumentation.runOnMainSync { assertFalse("The next Back gesture closes the widget window", drawer.isOpen) }
        }
    }

    @Test fun widgetWindowBlursBehindVisibleSystemBarsWithoutChangingTheBoardFill() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        WidgetPaneIntegrationTest().fixture(systemBarsVisible = true) { home, drawer ->
            val dialogField = drawer.javaClass.getDeclaredField("paneDialog").apply { isAccessible = true }
            val rootField = drawer.javaClass.getDeclaredField("drawerRoot").apply { isAccessible = true }
            instrumentation.runOnMainSync {
                drawer.hide()
                ThemeColorResolver.preview(Theme.widget_panel_background_color, "#FF102030")
            }
            instrumentation.waitForIdleSync(); Thread.sleep(100)
            val root = home.findViewById<View>(R.id.mainview)
            val background = root.background
            val attributes = WindowManager.LayoutParams().apply { copyFrom(home.window.attributes) }
            try {
                instrumentation.runOnMainSync { drawer.show() }
                instrumentation.waitForIdleSync(); Thread.sleep(300)
                instrumentation.runOnMainSync {
                    val dialog = dialogField.get(drawer) as android.app.Dialog
                    val window = dialog.window!!
                    val pane = rootField.get(drawer) as View
                    val board = pane.findViewById<View>(R.id.android_widget_drawer_container)
                    assertSame("The entire launcher stays underneath the window, just like Settings", background, root.background)
                    assertTrue(home.findViewById<View>(R.id.main_container).isShown)
                    val image = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                    val bounds = android.graphics.Rect(board.background.bounds)
                    try {
                        board.background.setBounds(0, 0, 80, 80)
                        board.background.draw(android.graphics.Canvas(image))
                        assertEquals("The widget board must retain its configured fill", 0xFF102030.toInt(), image.getPixel(40, 40))
                    } finally { board.background.bounds = bounds; image.recycle() }
                    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView)!!
                    assertTrue("Exercise visible system bars", insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()))
                    val barHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
                    val location = IntArray(2); window.decorView.getLocationOnScreen(location)
                    assertEquals("The blur window starts above the status-bar inset", 0, location[1])
                    pane.findViewById<View>(R.id.android_widget_viewport).getLocationOnScreen(location)
                    assertTrue("Only pane content stays below the status bar", location[1] >= barHeight)
                    assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
                    if (Build.VERSION.SDK_INT >= 31) {
                        assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0)
                        if (home.windowManager.isCrossWindowBlurEnabled) assertTrue(window.attributes.blurBehindRadius > 0)
                        else assertTrue(window.attributes.dimAmount >= .38f)
                    }
                    assertEquals("The launcher window's flags stay unchanged", attributes.flags, home.window.attributes.flags)
                }
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    File(instrumentation.targetContext.filesDir, "widgets-statusbar-blur.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
                instrumentation.waitForIdleSync(); Thread.sleep(100)
                instrumentation.runOnMainSync {
                    assertFalse("System Back closes the expanded pane", drawer.isOpen)
                    assertNull("Closing removes the pane window", dialogField.get(drawer))
                    assertSame("Closing keeps the original activity backing", background, root.background)
                    assertEquals(attributes.flags, home.window.attributes.flags)
                    assertEquals(attributes.format, home.window.attributes.format)
                    assertEquals(attributes.dimAmount, home.window.attributes.dimAmount)
                }
            } finally {
                instrumentation.runOnMainSync { drawer.hide(); ThemeColorResolver.clearPreview() }
            }
        }
    }

    @Test fun embeddedBlurClearsWhenPanelsHideOrDetach() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        WidgetPaneIntegrationTest().fixture { home, drawer ->
            val parent = home.findViewById<android.view.ViewGroup>(android.R.id.content)
            val ownersField = ohi.andre.consolelauncher.help.PanelBackdrop::class.java.getDeclaredField("owners").apply { isAccessible = true }
            fun owners() = ownersField.get(ohi.andre.consolelauncher.help.PanelBackdrop) as Map<*, *>
            val panel = android.widget.FrameLayout(home)
            instrumentation.runOnMainSync {
                drawer.hide()
                parent.addView(panel, android.view.ViewGroup.LayoutParams(-1, -1))
                ohi.andre.consolelauncher.help.PanelBackdrop.installEmbedded(panel)
            }
            try {
                instrumentation.waitForIdleSync(); Thread.sleep(100)
                instrumentation.runOnMainSync {
                    assertTrue("An embedded pane blurs its visible backdrop", owners().isNotEmpty())
                    panel.visibility = View.GONE
                }
                instrumentation.waitForIdleSync(); Thread.sleep(100)
                instrumentation.runOnMainSync {
                    assertTrue("Hiding the pane must restore the launcher", owners().isEmpty())
                    panel.visibility = View.VISIBLE
                }
                instrumentation.waitForIdleSync(); Thread.sleep(100)
                instrumentation.runOnMainSync {
                    assertTrue(owners().isNotEmpty())
                    parent.removeView(panel)
                    assertTrue("Removing the pane must release every blur layer", owners().isEmpty())
                }
            } finally { instrumentation.runOnMainSync { parent.removeView(panel) } }
        }
    }

    @Test fun transparentSettingsKeepsLauncherVisibleAndBlursBehindThePane() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        WidgetPaneIntegrationTest().fixture(systemBarsVisible = true) { home, drawer ->
            instrumentation.runOnMainSync {
                drawer.hide()
                ThemeColorResolver.preview(Theme.settings_panel_background_color, "#44102030")
                assertEquals("Panel transparency must remain a display preference", 0x44, android.graphics.Color.alpha(TuixtTheme.surfaceColor()))
            }
            val monitor = Instrumentation.ActivityMonitor(ThemerActivity::class.java.name, null, false)
            instrumentation.addMonitor(monitor)
            var settings: ThemerActivity? = null
            try {
                instrumentation.runOnMainSync { home.uiManager!!.openSettingsSurface(ThemerActivity.SECTION_HOME) }
                settings = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as ThemerActivity
                instrumentation.waitForIdleSync(); Thread.sleep(500)
                val activity = settings
                instrumentation.runOnMainSync {
                    assertFalse(home.isFinishing)
                    assertTrue("Launcher remains visible below translucent Settings", home.window.decorView.visibility == View.VISIBLE)
                    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
                    if (Build.VERSION.SDK_INT >= 31) {
                        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0)
                        if (activity.windowManager.isCrossWindowBlurEnabled) assertTrue(activity.window.attributes.blurBehindRadius > 0)
                        else assertTrue(activity.window.attributes.dimAmount >= .38f)
                    }
                    assertEquals(0x44, android.graphics.Color.alpha(TuixtTheme.surfaceColor()))
                    assertTrue(activity.findViewById<View>(R.id.settings_panel).isShown)
                }
                instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                    File(context.filesDir, "settings-transparent-blur.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            } finally {
                settings?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
                instrumentation.removeMonitor(monitor)
                ThemeColorResolver.clearPreview()
                instrumentation.waitForIdleSync()
            }
        }
    }
}
