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
        WidgetPaneIntegrationTest().fixture { home, drawer ->
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
