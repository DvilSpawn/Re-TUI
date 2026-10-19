package ohi.andre.consolelauncher.presentation

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.SettingsPreviewFixture
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuoPreviewLayoutDeviceTest {
    @Test fun leftDuoKeepsHeaderAndModulesWithTray() = verify("left", split = false, swapped = false, ime = 0)
    @Test fun rightSplitDuoMovesHeaderToTheOppositePane() = verify("right", split = true, swapped = false, ime = 0)
    @Test fun swappedLeftDuoKeepsHeaderOverTrayAndReservesIme() = verify("left", split = false, swapped = true, ime = 180)

    private fun verify(side:String, split:Boolean, swapped:Boolean, ime:Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        FrameManager.isEnabled(context)
        FrameManager.drawable(context)
        val persisted = SettingsPreviewFixture.appearanceState()
        instrumentation.runOnMainSync {
            val sceneContext = ContextThemeWrapper(context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }), context.theme)
            val values = mapOf<XMLPrefsSave, String>(Behavior.launcher_mode to "classic", Behavior.search_only_mode to "false",
                Behavior.orientation to "2", Behavior.duo_swap_top_panes to swapped.toString(),
                Ui.split_duo_launcher to split.toString(), SurfaceBorderOption.unified_status_border to "false",
                Ui.display_margin_top_section to "0,0,0,0", Ui.display_margin_bottom_section to "0,0,0,0",
                Ui.keyboard_visible_margin_override to "", Suggestions.show_suggestions to "true")
            val appearance = HomeAppearance.saved().overlay({ values[it] }, values::containsKey)
            val root = LauncherHomePresentation.inflate(sceneContext)
            val pager = root.findViewById<ViewGroup>(R.id.view_pager)
            pager.addView(HomeLayoutPresentation.inflateModulesPage(sceneContext, pager), FrameLayout.LayoutParams(-1, -1))
            val insets = LauncherHomeSnapshot.Insets(top = 12, bottom = 18)
            val state = TerminalTrayPresentation.RuntimeState(ime > 0, ime, 18, ime > 0, false, true, true, false,
                keyboardMarginsOverride = ime > 0, duoSide = side)
            fun layout() {
                root.measure(View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 1600, 900)
            }
            repeat(3) {
                HomeLayoutPresentation.bindScene(root, appearance, insets, state)
                layout()
                HomeLayoutPresentation.finishDuoLayout(root, appearance, insets, state)
                layout()
                val left = root.findViewById<ViewGroup>(R.id.landscape_left_pane)
                val right = root.findViewById<ViewGroup>(R.id.landscape_right_pane)
                val target = if(side == "left") left else right
                val opposite = if(side == "left") right else left
                val modulePane = if(swapped) opposite else target
                val main = root.findViewById<View>(R.id.main_container)
                val tray = root.findViewById<View>(R.id.terminal_tray_container)
                val header = root.findViewById<View>(R.id.header_container)
                val strip = root.findViewById<View>(R.id.module_suggestions_container)
                val controls = root.findViewById<View>(R.id.home_duo_controls)
                assertSame("Terminal tray must occupy the selected Duo pane", target, tray.parent)
                assertSame("Module pane placement must follow swap mode", modulePane, main.parent)
                assertSame("Module suggestions must follow their module pane", modulePane, strip.parent)
                assertSame("Controls must occupy the opposite pane", opposite, controls.parent)
                assertSame("Header must follow split/swap semantics", if(swapped) target else if(split) opposite else main, header.parent)
                assertEquals(1, SettingsPreviewFixture.views(root).count { view -> view.id == R.id.home_duo_controls })
                assertFalse("Passive Duo controls must retain no actions", SettingsPreviewFixture.views(controls).any { view -> view.hasOnClickListeners() || view.isLongClickable })
                val controlsMargins = controls.layoutParams as FrameLayout.LayoutParams
                assertEquals(Tuils.dpToPx(sceneContext, 18) + ime, controlsMargins.bottomMargin)
                val stripMargins = strip.layoutParams as FrameLayout.LayoutParams
                assertEquals(Tuils.dpToPx(sceneContext, 18) + Tuils.dpToPx(sceneContext, 56) + ime, stripMargins.bottomMargin)
                val trayMargins = tray.layoutParams as FrameLayout.LayoutParams
                assertEquals(ime, trayMargins.bottomMargin)
                assertEquals(insets.top + if(swapped) header.height else 0, trayMargins.topMargin)
                if(swapped) assertEquals(Tuils.dpToPx(sceneContext, 84) + ime, (main.layoutParams as FrameLayout.LayoutParams).bottomMargin)
                assertEquals(18, root.paddingBottom)
            }
        }
        persisted.assertUnchanged()
    }
}
