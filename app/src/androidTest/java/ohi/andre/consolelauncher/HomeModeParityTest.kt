package ohi.andre.consolelauncher

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.settings.LauncherMode
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.termux.TerminalHomeView
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.mono.MonoHomeView
import ohi.andre.consolelauncher.wallpaper.pixel.DotHomeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every launcher mode shows its own home and only that one, and keeps it through an in-place refresh. */
class HomeModeParityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** The home view each mode owns; null for the modes that show the launcher's shared layout. */
    private val homes: Map<String, Class<out View>?> = mapOf(
        LauncherMode.CLASSIC to null,
        LauncherMode.SEARCH to null,
        LauncherMode.TERMINAL to TerminalHomeView::class.java,
        LauncherMode.DOT to DotHomeView::class.java,
        LauncherMode.MONO to MonoHomeView::class.java
    )

    private val allHomes = homes.values.filterNotNull()

    private fun withLauncher(test: (LauncherActivity) -> Unit) {
        val context = instrumentation.targetContext
        check(context.packageName == "com.dvil.tui_renewed")
        check(android.os.Build.MODEL.contains("sdk_gphone")) { "Disposable emulator fixture only" }
        if (BuildConfig.FLAVOR == "fdroid") {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"))
                .use { it.readBytes() }
        }
        XMLPrefsManager.loadCommons(context)
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val fixture: List<Pair<XMLPrefsSave, String>> = listOf(
            Ui.show_weather to "false", Behavior.tui_notification to "false",
            Notifications.show_notifications to "false", Notifications.terminal_notifications to "false",
            Behavior.auto_show_keyboard to "false", Ui.system_wallpaper to "false",
            Behavior.breath_out to "false", Behavior.launcher_mode to "classic", Behavior.duo_mode to "false",
            Behavior.enable_cyberdeck_mode to "false"
        )
        val original = fixture.associate { it.first to LauncherSettings.get(it.first) }
        fixture.forEach { (key, value) -> LauncherSettings.set(key, value) }
        val startup = context.getSharedPreferences("retui_startup", 0)
        val complete = startup.getBoolean("complete", false)
        val active = startup.getBoolean("active", false)
        startup.edit().putBoolean("complete", true).putBoolean("active", false).commit()
        val launcher = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        try {
            instrumentation.waitForIdleSync()
            assertNotNull("Initialized launcher", launcher.uiManager)
            test(launcher)
        } finally {
            instrumentation.runOnMainSync { launcher.finish() }
            instrumentation.waitForIdleSync()
            original.forEach { (key, value) -> LauncherSettings.set(key, value) }
            startup.edit().putBoolean("complete", complete).putBoolean("active", active).commit()
        }
    }

    private fun collect(view: View, out: MutableList<View>) {
        out += view
        if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i), out)
    }

    /** Refreshes in place and waits for the new layout to be attached and laid out. */
    private fun refresh(launcher: LauncherActivity) {
        instrumentation.runOnMainSync { launcher.refreshUiInPlace() }
        instrumentation.waitForIdleSync()
        val deadline = SystemClock.uptimeMillis() + 10000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                val root = launcher.findViewById<FrameLayout>(R.id.mainview)
                ready = root != null && root.isAttachedToWindow && root.width > 0
            }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Launcher layout did not come back after refresh", ready)
        instrumentation.waitForIdleSync()
    }

    private fun assertHome(launcher: LauncherActivity, mode: String, stage: String) {
        instrumentation.runOnMainSync {
            val root = launcher.findViewById<FrameLayout>(R.id.mainview)
            val tree = ArrayList<View>().also { collect(root, it) }
            val expected = homes.getValue(mode)
            for (home in allHomes) {
                val shown = tree.any { home.isInstance(it) && it.visibility == View.VISIBLE }
                assertEquals("$mode ($stage): ${home.simpleName} visible", home == expected, shown)
            }
            val results = root.findViewById<View>(R.id.search_mode_results_scroll)
            when (mode) {
                LauncherMode.CLASSIC -> assertEquals("$mode ($stage): search results", View.GONE, results?.visibility ?: View.GONE)
                LauncherMode.SEARCH -> assertEquals("$mode ($stage): search results", View.VISIBLE, results?.visibility)
            }
            // A home that takes the whole screen must be the top of the root, or something covers it.
            if (mode == LauncherMode.DOT || mode == LauncherMode.MONO) {
                assertTrue("$mode ($stage): home is on top", expected!!.isInstance(root.getChildAt(root.childCount - 1)))
            }
        }
    }

    @Test
    fun everyModeHasAHomeRowInThisTest() {
        assertEquals("Add the new mode's home to this test", LauncherMode.values.toSet(), homes.keys)
    }

    @Test
    fun eachModeShowsOnlyItsOwnHomeAndKeepsItThroughRefresh() = withLauncher { launcher ->
        for (mode in LauncherMode.values) {
            instrumentation.runOnMainSync { LauncherSettings.set(Behavior.launcher_mode, mode) }
            refresh(launcher)
            assertHome(launcher, mode, "after switching")
            refresh(launcher)
            assertHome(launcher, mode, "after refreshing again")
        }
    }

    @Test
    fun appDrawerOpensOverMonoHome() = withLauncher { launcher ->
        instrumentation.runOnMainSync { LauncherSettings.set(Behavior.launcher_mode, LauncherMode.MONO) }
        refresh(launcher)
        instrumentation.runOnMainSync {
            launcher.uiManager!!.showAppsDrawer()
            val root = launcher.findViewById<FrameLayout>(R.id.mainview)
            val drawer = root.findViewById<View>(R.id.apps_drawer_root)
            val monoIndex = (0 until root.childCount).first { root.getChildAt(it) is MonoHomeView }
            assertTrue("Drawer must be drawn above the opaque mono home", root.indexOfChild(drawer) > monoIndex)
            assertEquals(View.VISIBLE, drawer.visibility)
            launcher.uiManager!!.hideAppsDrawer()
        }
    }
}
