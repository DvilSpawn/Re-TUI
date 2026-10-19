package ohi.andre.consolelauncher.managers.settings

import android.content.res.Configuration
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.presentation.HomeLayoutPresentation
import ohi.andre.consolelauncher.presentation.LauncherHomeSnapshot
import ohi.andre.consolelauncher.presentation.TerminalTrayPresentation
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreviewKeyboardMarginsTest {
    @Test fun keyboardOverrideDraftUsesFrozenSourceMarginsAndKeepsInsets() = with(SettingsPreviewFixture) {
        withHome(mapOf(Ui.display_margin_bottom_section to "2,3,4,5", Ui.display_margin_top_section to "0,0,0,0",
            Behavior.launcher_mode to "classic", Behavior.search_only_mode to "false",
            SurfaceBorderOption.unified_status_border to "false")) {
            val before = appearanceState()
            instrumentation.runOnMainSync {
                val portrait = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                    orientation = Configuration.ORIENTATION_PORTRAIT
                })
                val base = HomeAppearance.saved()
                fun checkTransition(oldOverride: String, newOverride: String, active: Boolean) {
                    val key = Ui.keyboard_visible_margin_override
                    val saved = base.overlay({ if(it == key) oldOverride else null }, { it == key })
                    val draft = saved.overlay({ if(it == key) newOverride else null }, { it == key })
                    val root = FrameLayout(portrait)
                    val main = FrameLayout(portrait).apply { id = R.id.main_container }
                    val panel = FrameLayout(portrait).apply { id = R.id.terminal_tray_container }
                    root.addView(main, FrameLayout.LayoutParams(-1, -1))
                    root.addView(panel, FrameLayout.LayoutParams(-1, -2))
                    val insets = LauncherHomeSnapshot.Insets(left = 7, top = 11, right = 13, bottom = 24)
                    val imeBottom = if(active) 400 else 0
                    val runtime = TerminalTrayPresentation.RuntimeState(active, imeBottom, 24, active, false, false, false, false,
                        keyboardMarginsOverride = active)
                    fun assertMargins(value: String) {
                        val effective = if(active && value.isNotEmpty()) value else "2,3,4,5"
                        val expected = effective.split(',').map { Tuils.mmToPx(portrait.resources.displayMetrics, it.toInt()) }
                        val params = panel.layoutParams as FrameLayout.LayoutParams
                        assertEquals(expected[0], params.leftMargin)
                        assertEquals(expected[1], params.topMargin)
                        assertEquals(expected[2], params.rightMargin)
                        assertEquals(expected[3] + imeBottom, params.bottomMargin)
                        assertEquals(insets.left, root.paddingLeft)
                        assertEquals(insets.right, root.paddingRight)
                        assertEquals(insets.bottom, root.paddingBottom)
                        assertEquals(insets.top, (main.layoutParams as FrameLayout.LayoutParams).topMargin)
                        // Navigation padding and the IME margin remain distinct and combine at the screen edge.
                        assertEquals(expected[3] + imeBottom + insets.bottom, params.bottomMargin + root.paddingBottom)
                    }
                    HomeLayoutPresentation.bindScene(root, saved, insets, runtime)
                    assertMargins(oldOverride)
                    repeat(3) {
                        HomeLayoutPresentation.bindScene(root, draft, insets, runtime)
                        assertMargins(newOverride)
                    }
                    HomeLayoutPresentation.bindScene(root, saved, insets, runtime)
                    assertMargins(oldOverride)
                }
                checkTransition("1,2,3,4", "3,4,5,6", true)
                checkTransition("1,2,3,4", "", true)
                checkTransition("", "3,4,5,6", true)
                checkTransition("1,2,3,4", "3,4,5,6", false)
            }
            before.assertUnchanged()
        }
    }
}
