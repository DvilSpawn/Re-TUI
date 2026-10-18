package ohi.andre.consolelauncher.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.BaseAdapter
import android.widget.ListView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ui.AppDrawerPaneManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior

class PrivateSpaceLaunchIntegrationTest {
    private var previousPrivateCommandEnabled: String? = null

    @After fun restorePrivateCommandPreference() {
        previousPrivateCommandEnabled?.let {
            LauncherSettings.set(InstrumentationRegistry.getInstrumentation().targetContext,
                Behavior.enable_private_command, it)
        }
    }

    @Test
    fun emulatorPrivateSpaceUnlockLaunchCancelAndRelock() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // This test enters a fixture PIN; it must never run against a person's phone.
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 35 && Build.HARDWARE == "ranchu" &&
            InstrumentationRegistry.getArguments().getString("privateEmulatorFlow") == "true")
        val fixturePackage = "dev.retui.privatespacetest"
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val users = context.getSystemService(Context.USER_SERVICE) as UserManager
        val profile = launcherApps.profiles.first {
            launcherApps.getLauncherUserInfo(it)?.userType == "android.os.usertype.profile.PRIVATE"
        }
        val profileId = InstrumentationRegistry.getArguments().getString("privateUserId")!!.toInt()
        assertEquals(android.os.UserHandle.getUserHandleForUid(profileId * 100000), profile)
        instrumentation.runOnMainSync {
            ohi.andre.consolelauncher.tuils.Tuils.init(context)
            ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.dispose()
            ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.loadCommons(context)
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.refreshFromLoadedPrefs()
            previousPrivateCommandEnabled = XMLPrefsManager.get(Behavior.enable_private_command)
            LauncherSettings.set(context, Behavior.enable_private_command, "true")
            ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.set(context,
                ohi.andre.consolelauncher.managers.xml.options.Notifications.show_notifications, "false")
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.set(context,
                ohi.andre.consolelauncher.managers.xml.options.Notifications.terminal_notifications, "false")
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.set(context,
                ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification, "false")
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.set(context,
                ohi.andre.consolelauncher.managers.xml.options.Behavior.auto_show_keyboard, "false")
            context.getSharedPreferences("retui_startup", Context.MODE_PRIVATE).edit()
                .putBoolean("initialized", true).putBoolean("complete", true).putBoolean("active", false).commit()
            ohi.andre.consolelauncher.managers.ChangelogManager.markSeen(context)
        }
        fun waitFor(label: String, ready: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15000
            while (!ready() && SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
            assertTrue(label, ready())
        }
        fun home(): LauncherActivity {
            instrumentation.runOnMainSync {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            waitFor("Launcher ready") { LauncherActivity.instance?.uiManager != null }
            instrumentation.waitForIdleSync()
            return LauncherActivity.instance!!
        }
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        fun pinField(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.className?.toString() == "android.widget.EditText" && node.isPassword && node.isFocused) return node
            for (i in 0 until node.childCount) pinField(node.getChild(i))?.let { return it }
            return null
        }
        var activity = home()
        waitFor("App manager initialized") { (AppsManager.instance as? AppsManager)?.hasPrivateSpace() == true }
        val manager = AppsManager.instance as AppsManager
        fun request(quiet: Boolean) {
            instrumentation.runOnMainSync {
                val message = manager.requestPrivateSpaceQuietMode(quiet)
                instrumentation.sendStatus(0, Bundle().apply { putString("stream", "quiet=$quiet: $message\n") })
            }
        }
        fun unlockWithFixturePin() {
            request(false)
            waitFor("Android PIN entry is focused") { pinField(instrumentation.uiAutomation.rootInActiveWindow) != null }
            // SystemUI can focus the field before the authentication prompt finishes animating.
            instrumentation.uiAutomation.waitForIdle(500, 5000)
            val input = pinField(instrumentation.uiAutomation.rootInActiveWindow)!!
            assertTrue("Enter the emulator fixture PIN", input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "2468") }))
            assertTrue("Submit the emulator fixture PIN",
                input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id))
            waitFor("Android unlocked the private profile") {
                !users.isQuietModeEnabled(profile) && users.isUserUnlocked(profile)
            }
            waitFor("Launcher received private app availability") {
                manager.privateApps().any { it.componentName?.packageName == fixturePackage }
            }
            activity = home()
        }
        fun launchFromPrivateDrawer() {
            instrumentation.runOnMainSync {
                activity.uiManager!!.showAppsDrawer()
                val tabs = activity.findViewById<LinearLayout>(R.id.apps_group_tabs)
                val privateTab = (0 until tabs.childCount).map { tabs.getChildAt(it) }
                    .filterIsInstance<TextView>().first { it.text.toString() == "PRI" }
                assertTrue(privateTab.performClick())
                val list = activity.findViewById<ListView>(R.id.apps_list)
                val position = (0 until list.adapter.count).first {
                    (list.adapter.getView(it, null, list) as? TextView)?.text?.toString() == "Private Profile Test"
                }
                list.performItemClick(list.adapter.getView(position, null, list), position, position.toLong())
            }
            waitFor("Fixture launched in private user $profileId") {
                shell("dumpsys window").lineSequence().any {
                    it.contains("mCurrentFocus=") && it.contains("u$profileId ") && it.contains(fixturePackage)
                }
            }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PRI tap launched fixture in user $profileId\n") })
        }
        assertTrue("Fixture must not be installed in personal profile",
            launcherApps.getActivityList(fixturePackage, android.os.Process.myUserHandle()).isEmpty())
        request(true)
        waitFor("Profile locked") { users.isQuietModeEnabled(profile) && manager.isPrivateSpaceLocked() }
        assertTrue(manager.privateApps().isEmpty())
        assertFalse(manager.isPrivateSpaceTabVisible())
        unlockWithFixturePin()
        LauncherSettings.set(context, Behavior.enable_private_command, "false")
        assertFalse(manager.hasPrivateSpace())
        assertFalse(manager.isPrivateSpaceTabVisible())
        assertTrue(manager.privateApps().isEmpty())
        assertTrue("Disabling Re:TUI access must not lock the Android profile", users.isUserUnlocked(profile))
        LauncherSettings.set(context, Behavior.enable_private_command, "true")
        launchFromPrivateDrawer()
        activity = home()
        request(true)
        waitFor("Relocked profile") { users.isQuietModeEnabled(profile) && manager.isPrivateSpaceLocked() }
        request(false)
        waitFor("Authentication displayed for cancellation") { pinField(instrumentation.uiAutomation.rootInActiveWindow) != null }
        shell("input keyevent 4")
        waitFor("Android dismissed cancelled authentication") {
            pinField(instrumentation.uiAutomation.rootInActiveWindow) == null
        }
        instrumentation.uiAutomation.waitForIdle(500, 5000)
        assertTrue("Cancel keeps quiet mode on", users.isQuietModeEnabled(profile))
        assertTrue(manager.privateApps().isEmpty())
        activity = home()
        unlockWithFixturePin()
        launchFromPrivateDrawer()
        activity = home()
        request(true)
        waitFor("Final profile lock") { users.isQuietModeEnabled(profile) && manager.isPrivateSpaceLocked() }
    }

    @Test
    fun failedAppLaunchKeepsDrawerOpenWithoutRecordingALaunch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val home = instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME).setClass(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val ui = home.uiManager!!
            ui.showAppsDrawer()
            val drawer = ui.javaClass.getDeclaredField("appDrawerPaneManager")
                .apply { isAccessible = true }.get(ui) as AppDrawerPaneManager
            @Suppress("UNCHECKED_CAST")
            val entries = drawer.javaClass.getDeclaredField("entries")
                .apply { isAccessible = true }.get(drawer) as MutableList<Any>
            val app = AppsManager.LaunchInfo("invalid.retui.missing", "invalid.retui.Missing", "Missing test app")
            val entryClass = drawer.javaClass.declaredClasses.first { it.simpleName == "AppEntry" }
            val entry = entryClass.getDeclaredConstructor(AppsManager.LaunchInfo::class.java)
                .apply { isAccessible = true }.newInstance(app)
            val list = home.findViewById<ListView>(R.id.apps_list)
            try {
                entries.clear()
                entries.add(entry)
                (list.adapter as BaseAdapter).notifyDataSetChanged()
                list.performItemClick(null, 0, 0)
                assertTrue("Failed launch must keep the drawer visible", drawer.isOpen)
                assertEquals(0, app.launchedTimes)
            } finally {
                drawer.show()
                drawer.hide()
            }
        }
    }

    @Test
    fun privateReadinessMatchesAndroidWithoutLaunchingPrivateApps() {
        if (Build.VERSION.SDK_INT < 35) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.startActivitySync(Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME).setClass(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val users = context.getSystemService(Context.USER_SERVICE) as UserManager
        val profile = launcherApps.profiles.firstOrNull {
            launcherApps.getLauncherUserInfo(it)?.userType == "android.os.usertype.profile.PRIVATE"
        } ?: return
        val quiet = users.isQuietModeEnabled(profile)
        val unlocked = users.isUserUnlocked(profile)
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "Private profile: quietMode=$quiet, userUnlocked=$unlocked\n")
        })
        val manager = AppsManager.instance as AppsManager
        assertEquals("Private Space access follows the saved opt-in",
            XMLPrefsManager.getBoolean(Behavior.enable_private_command), manager.hasPrivateSpace())
        assertEquals(quiet || !unlocked, manager.isPrivateSpaceLocked())
    }
}
