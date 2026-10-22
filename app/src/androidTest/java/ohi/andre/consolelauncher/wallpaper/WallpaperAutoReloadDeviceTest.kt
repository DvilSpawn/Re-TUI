package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.onboarding.StartupMenuActivity
import ohi.andre.consolelauncher.managers.onboarding.StartupMenuManager
import ohi.andre.consolelauncher.managers.xml.AutoColorManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in end-to-end wallpaper test. Run after clearing only the isolated test package. */
class WallpaperAutoReloadDeviceTest {
    private val i get() = InstrumentationRegistry.getInstrumentation()
    private val c get() = i.targetContext
    private fun await(message: String, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < end) {
            i.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }
    private fun screenshot(name: String) {
        val bitmap = checkNotNull(i.uiAutomation.takeScreenshot())
        try { File(c.filesDir,"wallpaper-auto-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        } } finally {bitmap.recycle()}
    }
    private fun clickSystem(vararg labels: String): Boolean {
        val root=i.uiAutomation.rootInActiveWindow ?: return false
        for (label in labels) for (node in root.findAccessibilityNodeInfosByText(label)) {
            if (!label.equals(node.text?.toString(),true)) continue
            var target: android.view.accessibility.AccessibilityNodeInfo?=node
            while(target!=null && !target.isClickable) target=target.parent
            if (target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)==true) return true
        }
        return false
    }
    @Test fun firstInstallSolidWallpaperConfirmationUpdatesColorsWithoutRestart() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wallpaperAutoReload")=="true")
        assumeTrue(Build.VERSION.SDK_INT>=27)
        check(c.packageName.endsWith(".palettedebug"))
        assertTrue("Use fresh isolated application data",StartupMenuManager.isFirstInstallPending(c))
        ohi.andre.consolelauncher.managers.ChangelogManager.markSeen(c)
        ActivityScenario.launch<LauncherActivity>(Intent(c,LauncherActivity::class.java)).use {scenario ->
            var startup: StartupMenuActivity?=null
            await("Setup did not open") {
                i.runOnMainSync {
                    startup=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<StartupMenuActivity>().firstOrNull()
                };startup!=null
            }
            assertTrue("Auto color must be enabled",StartupMenuManager.autoTheme(c))
            repeat(3) {
                i.runOnMainSync {startup!!.findViewById<View>(R.id.startup_next).performClick()}
                i.waitForIdleSync();SystemClock.sleep(200)
            }
            await("Launcher did not finish first-install setup") {
                var ready=false
                scenario.onActivity {ready=it.uiManager!=null && !StartupMenuManager.isFirstInstallPending(c)}
                ready
            }
            RetuiWallpaperSettings.saveScene(c,"solid")
            RetuiWallpaperSettings.saveSolidColor(c,"#FFBC3248")
            val component=ComponentName(c,RetuiWallpaperService::class.java)
            scenario.onActivity {it.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,component))}
            await("System wallpaper confirmation was unavailable") {clickSystem("Set wallpaper","Apply")}
            await("Solid wallpaper was not applied") {
                clickSystem("Home screen","Home screen and lock screen","Home and lock screens")
                WallpaperManager.getInstance(c).wallpaperInfo?.component==component
            }
            c.startActivity(Intent(c,LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            i.waitForIdleSync()
            val expected=AutoColorManager.currentWallpaperColorSnapshot(c).colors.getValue(Theme.input_text_color)
            var manager: Any?=null
            scenario.onActivity {a ->
                manager=a.uiManager
                assertNotEquals("Wallpaper must visibly differ from initial auto theme",expected,
                    a.findViewById<EditText>(R.id.input_view).currentTextColor)
                a.findViewById<EditText>(R.id.input_view).setText("wallpaper -auto")
                a.findViewById<View>(R.id.submit_tv).performClick()
            }
            await("Command did not request confirmation") {
                var requested=false
                scenario.onActivity {requested=it.findViewById<EditText>(R.id.input_view).hint.toString()==c.getString(R.string.hint_wallpaper_auto_confirm)}
                requested
            }
            screenshot("before-confirmation")
            scenario.onActivity {a ->
                a.findViewById<EditText>(R.id.input_view).setText("yes")
                a.findViewById<View>(R.id.submit_tv).performClick()
            }
            await("Colors did not update live") {
                var updated=false
                scenario.onActivity {a ->updated=a.findViewById<EditText>(R.id.input_view).currentTextColor==expected}
                updated
            }
            scenario.onActivity {assertSame("Color changes must retain the live UI manager",manager,it.uiManager)}
            screenshot("after-confirmation")
            val saved=LauncherSettings.get(Theme.input_text_color)
            scenario.recreate()
            i.waitForIdleSync()
            scenario.onActivity {
                assertEquals(saved,LauncherSettings.get(Theme.input_text_color))
                assertEquals(expected,it.findViewById<EditText>(R.id.input_view).currentTextColor)
            }
            screenshot("after-recreation")
        }
    }
}
