package ohi.andre.consolelauncher.navigation

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Host installs the old APK, seeds it, replaces it with adb install -r, then invokes verification. */
class UpgradeCompatibilityDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val expectedFile get() = File(context.filesDir, "upgradecheck-expected.json")
    private val dock = listOf("timer", "upgrade_termux", "retui_counter", "oma_todo", "upgrade_lua", "notes")
    private fun guard() {
        assumeTrue("Run only as an explicit host-driven upgrade", InstrumentationRegistry.getArguments().getString("hostUpgrade") == "true")
        check(context.packageName.endsWith(".upgradecheck")) { "This fixture must never touch an everyday launcher package" }
    }
    private fun load() {
        Tuils.init(context)
        XMLPrefsManager.dispose()
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
    }
    private fun markNoticesSeen() {
        ChangelogManager.markSeen(context)
        context.getSharedPreferences("retui_startup", 0).edit().putBoolean("initialized", true)
            .putBoolean("complete", true).putBoolean("active", false).commit()
    }
    private fun startLauncher(): LauncherActivity {
        markNoticesSeen()
        val activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertNotNull("Launcher UI was constructed", activity.uiManager)
            assertNotNull("Terminal input exists", activity.findViewById<EditText>(R.id.input_view))
            assertNotNull("Terminal output exists", activity.findViewById<TextView>(R.id.terminal_view))
            Tuils.sendOutput(context, "UPGRADECHECK launcher started")
        }
        instrumentation.waitForIdleSync()
        return activity
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val image = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(context.getExternalFilesDir(null), "upgradecheck-$name.png").outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { image.recycle() }
    }
    private fun script(label: String) = """-- name = "$label"
-- type = "module"
-- retui = "1"
-- description = "User-owned upgrade sentinel"
function on_resume()
    ui:set_title("$label")
    ui:show_text("UPGRADECHECK retained local module")
end
"""
    private fun assertFiles(expected: JSONObject) {
        val root = Tuils.getFolder()
        val files = expected.getJSONObject("files")
        for (relative in files.keys()) {
            val actual = File(root, relative)
            assertTrue("Retained file: $relative", actual.isFile)
            assertArrayEquals("Unchanged user bytes: $relative", Base64.decode(files.getString(relative), Base64.NO_WRAP), actual.readBytes())
        }
    }

    @Test fun seedLegacyConfiguration() {
        guard()
        assertFalse("Use a new dedicated package for each upgrade run", expectedFile.exists())
        load()
        LauncherSettings.set(context, Notifications.show_notifications, "false")
        LauncherSettings.set(context, Notifications.terminal_notifications, "false")
        LauncherSettings.set(context, Behavior.tui_notification, "false")
        LauncherSettings.set(context, Behavior.show_module_dock, "true")
        val baseline = startLauncher()
        try { screenshot("legacy-launch") }
        finally { instrumentation.runOnMainSync { baseline.finish() }; instrumentation.waitForIdleSync() }

        LauncherSettings.set(context, Ui.input_output_size, "23")
        LauncherSettings.set(context, Theme.output_text_color, "#FF36A9E1")
        val root = Tuils.getFolder()
        val watched = mutableListOf<String>()
        for (id in listOf("upgrade_lua", "retui_counter", "oma_todo")) {
            LuaWidgetManager.save(id, "Local $id", script("Local $id"))
            val directory = LuaWidgetManager.widgetDir(id)
            File(directory, "prefs.json").writeText("""{"count":73,"label":"keep-$id"}""")
            File(directory, "user-data.txt").writeText("user data survives upgrade: $id\n")
            ModuleManager.setScriptModule(context, id, "lua:$id")
            watched.add("widgets/$id/main.lua")
            watched.add("widgets/$id/prefs.json")
            watched.add("widgets/$id/user-data.txt")
        }
        File(root, "upgrade-legacy-script.sh").writeText("#!/bin/sh\nprintf 'UPGRADECHECK termux sentinel\\n'\n")
        ModuleManager.setScriptModule(context, "upgrade_termux", "termux:${File(root, "upgrade-legacy-script.sh").absolutePath}")
        ModuleManager.setDock(context, dock.map { it as String? }.toMutableList())
        File(root, "alias.txt").appendText("\nupgradecheck=echo retained-upgrade-alias\n")
        File(root, "upgrade-user-sentinel.txt").writeText("Unrelated user file; preserve exact bytes.\n")
        watched.addAll(listOf("upgrade-legacy-script.sh", "alias.txt", "upgrade-user-sentinel.txt"))
        val files = JSONObject()
        watched.forEach { files.put(it, Base64.encodeToString(File(root, it).readBytes(), Base64.NO_WRAP)) }
        val expected = JSONObject().put("files", files).put("dock", JSONArray(dock))
            .put("termux", ModuleManager.getModuleSource(context, "upgrade_termux"))
            .put("fontSize", "23").put("color", "#FF36A9E1")
        expectedFile.writeText(expected.toString(2))
        assertFiles(expected)
        assertEquals(dock, ModuleManager.getDock(context))
        val legacyBackup = File(context.filesDir, "pre-upgrade.retui-backup")
        BackupManager.exportBackup(context, Uri.fromFile(legacyBackup))
        assertTrue("Legacy backup was exported", legacyBackup.length() > 0L)
        check(context.getSharedPreferences("retui_modules", 0).edit().commit())
        assertEquals(dock, ModuleManager.getDock(context))
        // Deliberately leave the data in place for replacement of the APK by the host.
    }

    @Test fun hiddenAndEmptyDockSurviveLauncherRestart() {
        guard()
        load()
        val previousDock = ModuleManager.getDock(context)
        val previousVisibility = LauncherSettings.get(Behavior.show_module_dock)
        var activity: LauncherActivity? = null
        try {
            ModuleManager.setDock(context, mutableListOf())
            LauncherSettings.set(context, Behavior.show_module_dock, "false")
            activity = startLauncher()
            assertTrue("An intentionally empty dock stays empty", ModuleManager.getDock(context).isEmpty())
            assertFalse("A hidden dock stays hidden", LauncherSettings.getBoolean(Behavior.show_module_dock))
            val current = checkNotNull(activity)
            instrumentation.runOnMainSync {
                current.findViewById<android.view.View>(R.id.module_dock_scroll)?.let {
                    assertEquals("Dock surface remains hidden", android.view.View.GONE, it.visibility)
                }
            }
            screenshot("hidden-empty-dock")
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            ModuleManager.setDock(context, previousDock)
            LauncherSettings.set(context, Behavior.show_module_dock, previousVisibility)
        }
    }

    @Test fun restoreLegacyBackupWithoutLaunching() {
        guard()
        load()
        val backup = File(context.filesDir, "pre-upgrade.retui-backup")
        assertTrue(backup.isFile)
        BackupManager.importBackup(context, Uri.fromFile(backup))
        load()
        assertFiles(JSONObject(expectedFile.readText()))
    }

    @Test fun reseedLegacyConfigurationForRepeatRun() {
        guard()
        assertTrue(expectedFile.delete())
        seedLegacyConfiguration()
    }

    @Test fun verifyUpgradedConfiguration() {
        guard()
        assertTrue("Seed the old installed APK before upgrading", expectedFile.isFile)
        load()
        val expected = JSONObject(expectedFile.readText())
        assertFiles(expected)
        assertEquals(expected.getString("fontSize"), LauncherSettings.get(Ui.input_output_size))
        assertEquals(expected.getString("color"), LauncherSettings.get(Theme.output_text_color))
        val upgraded = startLauncher()
        try {
            screenshot("upgraded-launch")
            assertFiles(expected)
            val expectedDock = expected.getJSONArray("dock").let { values -> (0 until values.length()).map { values.getString(it) } }
            assertEquals("Upgrade preserves exact dock order", expectedDock, ModuleManager.getDock(context))
            assertEquals(expected.getString("termux"), ModuleManager.getModuleSource(context, "upgrade_termux"))
            assertEquals("lua:oma_todo", ModuleManager.getModuleSource(context, "oma_todo"))
            assertEquals("lua:retui_counter", ModuleManager.getModuleSource(context, "retui_counter"))
            assertEquals("lua:upgrade_lua", ModuleManager.getModuleSource(context, "upgrade_lua"))
            assertEquals("23", LauncherSettings.get(Ui.input_output_size))
            assertEquals("#FF36A9E1", LauncherSettings.get(Theme.output_text_color))
            instrumentation.runOnMainSync {
                assertFalse("Launcher remains alive after migration", upgraded.isFinishing || upgraded.isDestroyed)
                assertTrue("Terminal output is usable", upgraded.findViewById<TextView>(R.id.terminal_view).text.contains("UPGRADECHECK"))
            }
        } finally { instrumentation.runOnMainSync { upgraded.finish() }; instrumentation.waitForIdleSync() }
    }
}
