package ohi.andre.consolelauncher.navigation

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a wide landscape emulator; verifies actual parents, bounds and drawer interaction. */
@RunWith(AndroidJUnit4::class)
class DuoLayoutTest {
    @Test fun companionPanesKeepTerminalUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val home = instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClass(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        fun settle() {
            instrumentation.runOnMainSync { }
            android.os.SystemClock.sleep(800)
        }
        settle()
        val ui = home.uiManager!!
        val settings = listOf(Behavior.duo_mode, Behavior.duo_swap_top_panes,
            Behavior.swipe_up_apps_drawer, Behavior.show_android_widget_drawer_button,
            Behavior.orientation, Ui.split_duo_launcher)
        val saved = settings.associateWith { LauncherSettings.get(it) }
        val savedSide = ui.getDuoLayoutMode()
        fun view(id: Int): View = home.findViewById(id)
        fun hasAncestor(child: View, parent: View): Boolean {
            var node: View? = child
            while (node != null) {
                if (node === parent) return true
                node = node.parent as? View
            }
            return false
        }
        fun controls(pane: ViewGroup): ViewGroup = (0 until pane.childCount)
            .map { pane.getChildAt(it) }.filterIsInstance<ViewGroup>()
            .first { group -> (0 until group.childCount).any {
                group.getChildAt(it).contentDescription?.startsWith("Move Re:T-UI") == true
            } }
        fun bounds(view: View): Rect {
            val xy = IntArray(2)
            view.getLocationOnScreen(xy)
            return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
        }
        fun screenshot(name: String) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
            java.io.File(context.cacheDir, name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        try {
            instrumentation.runOnMainSync {
                assertTrue("Use a wide landscape AVD", UIManager.isResponsiveLandscapeConfiguration(home.resources.configuration))
                LauncherSettings.set(Behavior.orientation, "0")
                LauncherSettings.set(Behavior.duo_mode, "true")
                LauncherSettings.set(Ui.split_duo_launcher, "true")
                ui.setDuoLayoutMode(UIManager.DUO_LAYOUT_RIGHT)
            }
            settle()
            instrumentation.runOnMainSync {
                LocalBroadcastManager.getInstance(context).sendBroadcastSync(Intent(UIManager.ACTION_MODULE_COMMAND)
                    .putExtra(UIManager.EXTRA_MODULE_COMMAND, "show")
                    .putExtra(UIManager.EXTRA_MODULE_NAME, "calendar"))
            }
            settle()
            val modules = view(R.id.home_modules_container) as ViewGroup
            val module = modules.getChildAt(0)
            assertNotNull("Calendar module should be open", module)
            for (swapped in listOf(false, true)) for (side in listOf(UIManager.DUO_LAYOUT_RIGHT, UIManager.DUO_LAYOUT_LEFT)) {
                android.util.Log.i("DuoLayoutTest", "Checking swapped=$swapped side=$side")
                instrumentation.runOnMainSync {
                    LauncherSettings.set(Behavior.duo_swap_top_panes, swapped.toString())
                    ui.setDuoLayoutMode(side)
                }
                settle()
                val main = view(if (side == UIManager.DUO_LAYOUT_RIGHT) R.id.landscape_right_pane else R.id.landscape_left_pane)
                val companion = view(if (side == UIManager.DUO_LAYOUT_RIGHT) R.id.landscape_left_pane else R.id.landscape_right_pane) as ViewGroup
                instrumentation.runOnMainSync {
                    assertTrue(hasAncestor(view(R.id.terminal_tray_container), main))
                    assertTrue(hasAncestor(view(R.id.header_container), if (swapped) main else companion))
                    assertTrue(hasAncestor(modules, if (swapped) companion else main))
                    assertSame("Swapping must preserve the module instance", module, modules.getChildAt(0))
                }
                if (swapped && side == UIManager.DUO_LAYOUT_RIGHT) screenshot("duo-swapped.png")
                for ((apps, widgets) in listOf(false to false, true to false, false to true, true to true)) {
                    instrumentation.runOnMainSync {
                        LauncherSettings.set(Behavior.swipe_up_apps_drawer, apps.toString())
                        LauncherSettings.set(Behavior.show_android_widget_drawer_button, widgets.toString())
                        ui.refreshResponsiveLandscapeLayout()
                    }
                    settle()
                    instrumentation.runOnMainSync {
                        val bar = controls(companion)
                        assertEquals(1 + (if (apps) 1 else 0) + (if (widgets) 1 else 0), bar.childCount)
                        if (apps) bar.getChildAt(1).performClick()
                        if (apps) {
                            assertTrue(ui.isAppsDrawerOpen)
                            assertTrue(hasAncestor(view(R.id.apps_drawer_root), companion))
                            assertTrue(view(R.id.input_view).isShown)
                            ui.hideAppsDrawer()
                        }
                        if (widgets) bar.getChildAt(bar.childCount - 1).performClick()
                        if (widgets) {
                            assertTrue(ui.isAndroidWidgetDrawerOpen)
                            assertTrue(hasAncestor(view(R.id.android_widget_drawer_root), companion))
                            assertTrue(view(R.id.input_view).isShown)
                            ui.hideAndroidWidgetDrawer()
                        }
                    }
                }
                instrumentation.runOnMainSync { ui.showAppsDrawer(); ui.activateTerminalInput(true) }
                settle()
                android.os.SystemClock.sleep(600)
                if (swapped && side == UIManager.DUO_LAYOUT_RIGHT) screenshot("duo-apps-ime.png")
                instrumentation.runOnMainSync {
                    val drawer = view(R.id.apps_drawer_root)
                    assertTrue(ui.isAppsDrawerOpen)
                    assertTrue(bounds(companion).contains(bounds(drawer)))
                    val insets = ViewCompat.getRootWindowInsets(home.window.decorView)!!
                    assertTrue("Terminal IME should open beside drawer", insets.isVisible(WindowInsetsCompat.Type.ime()))
                    val keyboardTop = home.window.decorView.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    assertTrue("Input covered by IME", bounds(view(R.id.input_view)).bottom <= keyboardTop)
                    assertTrue("Companion buttons covered by IME", bounds(controls(companion)).bottom <= keyboardTop)
                    ui.hideAppsDrawer()
                    androidx.core.view.WindowInsetsControllerCompat(home.window, home.window.decorView)
                        .hide(WindowInsetsCompat.Type.ime())
                    controls(companion).getChildAt(0).performClick()
                    assertNotEquals(side, ui.getDuoLayoutMode())
                }
                settle()
            }
            instrumentation.runOnMainSync {
                ui.setDuoLayoutMode(UIManager.DUO_LAYOUT_OFF)
                assertTrue(hasAncestor(view(R.id.header_container), view(R.id.main_container)))
                assertFalse(hasAncestor(view(R.id.apps_drawer_root), view(R.id.landscape_split_container)))
                ui.showAppsDrawer()
                assertFalse("Normal drawer should still hide launcher chrome", view(R.id.input_view).isShown)
                ui.hideAppsDrawer()
            }
        } finally {
            instrumentation.runOnMainSync {
                saved.forEach { (setting, value) -> LauncherSettings.set(setting, value) }
                ui.setDuoLayoutMode(savedSide)
            }
        }
    }
}
