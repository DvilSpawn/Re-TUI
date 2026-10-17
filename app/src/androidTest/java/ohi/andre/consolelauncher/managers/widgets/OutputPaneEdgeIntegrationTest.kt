package ohi.andre.consolelauncher.managers.widgets

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test

class OutputPaneEdgeIntegrationTest {
    @Test fun rotationRemovesOnlyLandscapeChromeGapAndKeepsUserAndSystemMargins() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun main(run: () -> Unit) = instrumentation.runOnMainSync(run)
        WidgetPaneIntegrationTest().fixture { home, drawer ->
            val ui = home.uiManager!!
            val container = home.findViewById<View>(R.id.terminal_container)
            val border = home.findViewById<View>(R.id.terminal_output_border)
            val label = home.findViewById<View>(R.id.terminal_tray_toggle)
            main {
                drawer.hide()
                LauncherSettings.set(home, Behavior.orientation, "0")
                LauncherSettings.set(home, Behavior.duo_mode, "false")
                LauncherSettings.set(home, Ui.display_margin_top_section, "0,2,0,0")
                LauncherSettings.set(home, Ui.display_margin_bottom_section, "0,2,0,0")
                // The output-field margin has already been resolved by the theme renderer.
                (border.layoutParams as ViewGroup.MarginLayoutParams).apply { topMargin = 13 }.also { border.layoutParams = it }
            }
            fun rotate(orientation: Int) {
                main { home.requestedOrientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                val deadline = SystemClock.uptimeMillis() + 10000
                while (home.resources.configuration.orientation != orientation && SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
                assertEquals(orientation, home.resources.configuration.orientation)
                main { ui.refreshResponsiveLandscapeLayout() }
                instrumentation.waitForIdleSync(); Thread.sleep(200)
            }
            fun checkChrome(landscape: Boolean) = main {
                assertEquals(if (landscape) 0 else Tuils.dpToPx(home, 8), container.paddingTop)
                assertEquals(if (landscape) 0 else -Tuils.dpToPx(home, 10), (label.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                assertEquals("Configured output margins survive rotation", 13, (border.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                assertEquals("The border has no extra unconfigured landscape offset", container.paddingTop + 13, border.top)
                if (landscape && label.isShown) {
                    assertTrue("The output label stays inside the available edge", label.top >= 0)
                    assertTrue("Output text clears its label", border.paddingTop >= label.height)
                }
            }
            rotate(Configuration.ORIENTATION_PORTRAIT); checkChrome(false)
            rotate(Configuration.ORIENTATION_LANDSCAPE); checkChrome(true)
            main {
                ui.applyWindowInsets(0, 27, 0, 0, 0, false)
                val expected = 27 + Tuils.mmToPx(home.resources.displayMetrics, 2)
                assertEquals(expected, (home.findViewById<View>(R.id.main_container).layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                assertEquals("Output honors system and user section margins", expected,
                    (home.findViewById<View>(R.id.terminal_tray_container).layoutParams as ViewGroup.MarginLayoutParams).topMargin)
                ui.applyWindowInsets(0, 0, 0, 0, 0, false)
            }
            rotate(Configuration.ORIENTATION_PORTRAIT); checkChrome(false)
        }
    }
}
