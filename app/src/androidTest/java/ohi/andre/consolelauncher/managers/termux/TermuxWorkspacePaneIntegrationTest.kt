package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Requires the explicit private host-helper fixture; never impersonates an installed Termux package. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspacePaneIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun field(manager: UIManager, name: String): Any? = UIManager::class.java.getDeclaredField(name).apply { isAccessible = true }.get(manager)
    private fun call(manager: UIManager, name: String, vararg args: Any): Any? = UIManager::class.java.declaredMethods
        .single { it.name == name && it.parameterTypes.size == args.size }.apply { isAccessible = true }.invoke(manager, *args)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var passed = false
            instrumentation.runOnMainSync { passed = condition() }
            if (passed) return
            Thread.sleep(50)
        }
        fail(message)
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(1500) // Wait for rotation animation and the existing delayed geometry recovery.
        val file = File(instrumentation.targetContext.cacheDir, "termux-pane-fixture-$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun paneNavigationImeRotationAndPausePreserveDraftAndHomeState() {
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")?.toIntOrNull()
        assumeTrue("Explicit private real-helper fixture is required", port != null)
        val context = instrumentation.targetContext
        if (android.os.Build.VERSION.SDK_INT >= 30 && ohi.andre.consolelauncher.BuildConfig.FLAVOR == "fdroid") {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")).use { it.readBytes() }
        }
        XMLPrefsManager.loadCommons(context)
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val config = Tuils.getFolder()
        val originalFiles = config.listFiles()?.filter { it.isFile && it.extension == "xml" }?.associate { it.name to it.readBytes() } ?: emptyMap()
        val startup = context.getSharedPreferences("retui_startup", Context.MODE_PRIVATE)
        val startupOriginal = startup.all.toMap()
        val activeModule = ohi.andre.consolelauncher.managers.modules.ModuleManager.getActiveModule(context)
        val fixtureSettings = listOf(Ui.show_weather to "false", Behavior.tui_notification to "false")
        val originalSettings = fixtureSettings.associate { it.first to LauncherSettings.get(it.first) }
        fixtureSettings.forEach { (key, value) -> LauncherSettings.set(key, value) }
        startup.edit().putBoolean("complete", true).putBoolean("active", false).commit()
        ohi.andre.consolelauncher.managers.modules.ModuleManager.setActiveModule(context, "")
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? =
                if (intent.action == "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") android.app.Instrumentation.ActivityResult(0, null) else null
        }
        instrumentation.addMonitor(monitor)
        var activity: LauncherActivity? = null
        try {
            activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
            val launcher = activity
            await("Launcher must initialize") { launcher.uiManager != null && launcher.findViewById<android.view.View>(R.id.view_pager).width > 0 }
            val manager = launcher.uiManager!!
            val orientation = launcher.requestedOrientation
            fun connectFixture() {
                val bootstrap = "__RETUI_SOCKET__ retui-bridge-socket-v3\n__RETUI_SOCKET_NAME__ retui_bridge_com_dvil_tui_renewed_v2\n__RETUI_TOKEN__ fixture-token\n__RETUI_TCP_HOST__ 127.0.0.1\n__RETUI_TCP_PORT__ $port\n"
                val frame = call(manager, "parseTermuxWorkspaceFrame", bootstrap)!!
                call(manager, "startTermuxWorkspaceSocketClient", frame)
            }
            instrumentation.runOnMainSync {
                manager.restoreTerminalState(TerminalManager.State("home draft", "home history", listOf("pwd"), 0, false))
                call(manager, "openTermuxWorkspacePage", false)
                connectFixture()
            }
            await("Pane must receive authenticated real-helper stream") { (field(manager, "termuxWorkspaceSocketClient") as? TermuxWorkspaceSocketClient)?.connected == true }
            instrumentation.runOnMainSync { call(manager, "newTermuxWorkspaceWindow", "fixture-nav", "") }
            await("NEW must select the real tmux window") { field(manager, "termuxWorkspaceCurrentWindow").toString().contains("fixture-nav") }
            instrumentation.runOnMainSync { call(manager, "handleTermuxWorkspaceCommand", "switch 0") }
            await("Local :switch must use the shared live selector") { field(manager, "termuxWorkspaceCurrentWindow").toString().startsWith("0:") }
            instrumentation.runOnMainSync {
                val input = launcher.findViewById<TermuxWorkspaceInputEditText>(R.id.termux_workspace_input)
                val connection = input.onCreateInputConnection(EditorInfo())!!
                assertTrue(connection.commitText(":switch 0x", 1))
                assertTrue(connection.deleteSurroundingText(1, 0))
                assertEquals("switch 0", field(manager, "termuxWorkspaceLocalCommandBuffer").toString())
                launcher.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
            await("Landscape must render") { launcher.window.decorView.width > launcher.window.decorView.height }
            screenshot("landscape")
            instrumentation.runOnMainSync {
                assertSame(manager, launcher.uiManager)
                assertEquals("switch 0", field(manager, "termuxWorkspaceLocalCommandBuffer").toString())
                assertEquals("home draft", manager.snapshotTerminalState()!!.input)
                assertEquals("home history", manager.snapshotTerminalState()!!.output.toString())
                manager.pause()
                assertNull(field(manager, "termuxWorkspaceSocketClient"))
                assertEquals("switch 0", field(manager, "termuxWorkspaceLocalCommandBuffer").toString())
                manager.resume()
                connectFixture()
            }
            await("Returning to pane must reconnect") { (field(manager, "termuxWorkspaceSocketClient") as? TermuxWorkspaceSocketClient)?.connected == true }
            instrumentation.runOnMainSync {
                assertEquals("switch 0", field(manager, "termuxWorkspaceLocalCommandBuffer").toString())
                launcher.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_0)
            await("Portrait must render") { launcher.window.decorView.height > launcher.window.decorView.width }
            screenshot("portrait")
            instrumentation.runOnMainSync {
                assertEquals("switch 0", field(manager, "termuxWorkspaceLocalCommandBuffer").toString())
                call(manager, "openHomePage")
                assertNull(field(manager, "termuxWorkspaceSocketClient"))
                assertEquals("home draft", manager.snapshotTerminalState()!!.input)
                launcher.requestedOrientation = orientation
            }
        } finally {
            instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            originalSettings.forEach { (key, value) -> LauncherSettings.set(key, value) }
            originalFiles.forEach { (name, bytes) -> File(config, name).writeBytes(bytes) }
            XMLPrefsManager.loadCommons(context)
            LauncherSettings.refreshFromLoadedPrefs()
            val edit = startup.edit()
            for (key in listOf("complete", "active")) {
                if (startupOriginal.containsKey(key)) edit.putBoolean(key, startupOriginal[key] as Boolean) else edit.remove(key)
            }
            edit.commit()
            ohi.andre.consolelauncher.managers.modules.ModuleManager.setActiveModule(context, activeModule)
            for ((name, bytes) in originalFiles) assertArrayEquals("Configuration bytes changed: $name", bytes, File(config, name).readBytes())
        }
    }
}
