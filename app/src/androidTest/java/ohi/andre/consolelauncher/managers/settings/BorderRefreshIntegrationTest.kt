package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.widget.EditText
import ohi.andre.consolelauncher.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.raw.config
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BorderRefreshIntegrationTest {
    private fun borderPixels(view: android.view.View): List<Int> {
        if (!view.isShown) return emptyList()
        val result = mutableListOf<Int>()
        val background = view.background
        if (view.id != android.view.View.NO_ID &&
            (background is ohi.andre.consolelauncher.tuils.TerminalBorderDrawable ||
                background is android.graphics.drawable.GradientDrawable)
        ) {
            val bitmap = android.graphics.Bitmap.createBitmap(320, 160, android.graphics.Bitmap.Config.ARGB_8888)
            val bounds = android.graphics.Rect(background.bounds)
            try {
                background.setBounds(0, 0, bitmap.width, bitmap.height)
                background.draw(android.graphics.Canvas(bitmap))
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                result.add(view.id)
                result.add(pixels.contentHashCode())
            } finally {
                background.bounds = bounds
                bitmap.recycle()
            }
        }
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) result.addAll(borderPixels(view.getChildAt(index)))
        }
        return result
    }

    private fun awaitVisibleOutput(launcher: LauncherActivity) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var visible = false
            instrumentation.runOnMainSync {
                val output = launcher.findViewById<android.view.View>(R.id.terminal_output_border)
                visible = output != null && output.isShown && output.width > 0 && output.height > 0
            }
            if (visible) return
            Thread.sleep(50)
        }
        org.junit.Assert.fail("Launcher output never became visible")
    }

    @Test fun borderChangesRebuildRunningUiWithoutRestartingActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !"playstore".equals(ohi.andre.consolelauncher.BuildConfig.FLAVOR, ignoreCase = true)
        ) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
                )
            ).use { it.readBytes() }
            org.junit.Assert.assertTrue("Fixture requires all-files access", android.os.Environment.isExternalStorageManager())
        }
        ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.loadCommons(context)
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val fixture: List<Pair<XMLPrefsSave, String>> = listOf(
            Ui.show_weather to "false",
            Behavior.tui_notification to "false",
            ohi.andre.consolelauncher.managers.xml.options.Notifications.show_notifications to "false",
            ohi.andre.consolelauncher.managers.xml.options.Notifications.terminal_notifications to "false",
            Behavior.auto_hide_output to "false",
            Behavior.output_tray_mode to "native",
            Behavior.enable_cyberdeck_mode to "false",
            Ui.enable_dashed_border to "true",
            Ui.dashed_border_dash_length to "5",
            Ui.dashed_border_gap_length to "5",
            Ui.dashed_border_stroke_width to "1.5",
            Ui.dashed_border_corner_radius to "0",
            Ui.output_corner_radius to Ui.output_corner_radius.defaultValue()!!,
            Theme.terminal_border_color to "#FFFFFFFF",
            Theme.terminal_header_border_color to "#FFFFFFFF",
            SurfaceBorderOption.output_border_enabled to "true",
            SurfaceBorderOption.output_border_color to "auto",
            SurfaceBorderOption.unified_status_border to "false"
        )
        val original = fixture.associate { it.first to LauncherSettings.get(it.first) }
        fixture.forEach { (setting, value) -> LauncherSettings.set(setting, value) }
        val startup = context.getSharedPreferences("retui_startup", android.content.Context.MODE_PRIVATE)
        val startupComplete = startup.getBoolean("complete", false)
        val startupActive = startup.getBoolean("active", false)
        startup.edit().putBoolean("complete", true).putBoolean("active", false).commit()
        val activeModule = ohi.andre.consolelauncher.managers.modules.ModuleManager.getActiveModule(context)
        ohi.andre.consolelauncher.managers.modules.ModuleManager.setActiveModule(context, "")
        val accessMonitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                return if (intent.action == "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") {
                    android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                } else null
            }
        }
        instrumentation.addMonitor(accessMonitor)
        val launcher = instrumentation.startActivitySync(
            Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as LauncherActivity
        try {
            instrumentation.waitForIdleSync()
            org.junit.Assert.assertNotNull("Launcher must finish initialization before testing live refresh", launcher.uiManager)
            awaitVisibleOutput(launcher)
            val changes: List<Pair<XMLPrefsSave, String>> = listOf(
                Ui.enable_dashed_border to "false",
                Ui.enable_dashed_border to "true",
                Ui.enable_dashed_border to "",
                Ui.enable_dashed_border to "true",
                Ui.dashed_border_dash_length to "8",
                Ui.dashed_border_gap_length to "3",
                Ui.dashed_border_stroke_width to "2",
                Ui.dashed_border_corner_radius to "4",
                Theme.terminal_border_color to "#FF123456",
                Theme.terminal_header_border_color to "#FF654321",
                SurfaceBorderOption.output_border_enabled to "false",
                SurfaceBorderOption.output_border_enabled to "true",
                SurfaceBorderOption.output_border_color to "#FFABCDEF",
                SurfaceBorderOption.unified_status_border to "true"
            )
            instrumentation.runOnMainSync {
                launcher.uiManager!!.restoreTerminalState(
                    ohi.andre.consolelauncher.managers.TerminalManager.State(
                        "unfinished command", "retained output\nsecond line", listOf("echo first", "pwd"), 1, false
                    )
                )
            }
            for ((setting, value) in changes) {
                val oldManager = launcher.uiManager
                val state = oldManager!!.snapshotTerminalState()!!
                var oldPixels: List<Int> = emptyList()
                val oldDashed = LauncherSettings.getBoolean(Ui.enable_dashed_border)
                instrumentation.runOnMainSync {
                    oldPixels = borderPixels(launcher.window.decorView)
                    if (setting == Ui.enable_dashed_border) {
                        val command = config()
                        val parameter = when {
                            value.isEmpty() -> "-erase"
                            LauncherSettings.get(setting).isNullOrEmpty() && value == "true" -> "-append"
                            else -> "-set"
                        }
                        val pack = object : ExecutePack(CommandGroup("")) {}.apply {
                            this.context = launcher
                            args = arrayOf(setting, value)
                        }
                        command.getParam(launcher.uiManager!!.pack!!, parameter).value!!.exec(pack)
                    } else {
                        LauncherSettings.set(context, setting, value)
                    }
                }
                instrumentation.waitForIdleSync()
                awaitVisibleOutput(launcher)
                assertNotSame("Live border refresh missing for ${setting.label()}", oldManager, launcher.uiManager)
                val restored = launcher.uiManager!!.snapshotTerminalState()!!
                assertEquals(state.input, restored.input)
                assertEquals(state.output.toString(), restored.output.toString())
                assertEquals(state.commands, restored.commands)
                assertEquals(state.historyPosition, restored.historyPosition)
                assertEquals(state.root, restored.root)
                instrumentation.runOnMainSync {
                    val output = launcher.findViewById<android.view.View>(R.id.terminal_output_border)
                    val ancestry = generateSequence(output) { it.parent as? android.view.View }
                        .joinToString { "${it.id}:${it.visibility}:${it.width}x${it.height}:attached=${it.isAttachedToWindow}" }
                    org.junit.Assert.assertTrue("Output surface must be visible: $ancestry", output.isShown)
                    val pixels = borderPixels(launcher.window.decorView)
                    if (setting == Ui.enable_dashed_border && oldDashed == LauncherSettings.getBoolean(Ui.enable_dashed_border)) {
                        assertEquals("Erasing/appending the default must retain the same border", oldPixels, pixels)
                    } else {
                        org.junit.Assert.assertNotEquals("Rendered borders unchanged for ${setting.label()}=$value", oldPixels, pixels)
                    }
                    assertEquals("unfinished command", launcher.findViewById<EditText>(R.id.input_view).text.toString())
                }
                assertSame("Refresh must retain the running activity", launcher, LauncherActivity.instance)
                assertEquals(value, LauncherSettings.get(setting))
                org.junit.Assert.assertFalse("Refresh must not finish the activity", launcher.isFinishing)
            }
            val oldManager = launcher.uiManager
            instrumentation.runOnMainSync {
                LauncherSettings.set(context, Behavior.preferred_music_app, LauncherSettings.get(Behavior.preferred_music_app))
            }
            instrumentation.waitForIdleSync()
            assertSame("Unrelated settings must not rebuild the UI", oldManager, launcher.uiManager)
        } finally {
            instrumentation.removeMonitor(accessMonitor)
            ohi.andre.consolelauncher.managers.modules.ModuleManager.setActiveModule(context, activeModule)
            startup.edit().putBoolean("complete", startupComplete).putBoolean("active", startupActive).commit()
            instrumentation.runOnMainSync {
                original.forEach { (setting, value) -> LauncherSettings.set(setting, value) }
                launcher.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }
}
