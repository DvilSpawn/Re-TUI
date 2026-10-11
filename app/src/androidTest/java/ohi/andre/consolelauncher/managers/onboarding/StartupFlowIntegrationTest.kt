package ohi.andre.consolelauncher.managers.onboarding

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.TerminalBorderDrawable
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Run on the isolated debug app; restore both onboarding and launcher configuration. */
class StartupFlowIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val real = context.getSharedPreferences(StartupMenuManager.PREFS, Context.MODE_PRIVATE)
    private val preview = context.getSharedPreferences(StartupMenuManager.PREVIEW_PREFS, Context.MODE_PRIVATE)
    private var savedPrefs = emptyMap<SharedPreferences, Map<String, *>>()
    private var savedFiles = emptyMap<File, ByteArray?>()

    @Before fun save() {
        XMLPrefsManager.loadCommons(context)
        savedPrefs = listOf(real, preview, context.getSharedPreferences("tasker_integration", Context.MODE_PRIVATE))
            .associateWith { it.all.toMap() }
        savedFiles = (XMLPrefsManager.XMLPrefsRoot.entries.map { it.path } + "notifications.xml").associate {
            val file = File(Tuils.getFolder(), it)
            file to if (file.isFile) file.readBytes() else null
        }
        real.edit().clear().putBoolean("initialized", true).putBoolean("complete", false).commit()
        preview.edit().clear().commit()
    }

    @After fun restore() {
        instrumentation.runOnMainSync {
            LauncherActivity.instance?.finish()
            Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .filterIsInstance<StartupMenuActivity>().distinct().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
        savedPrefs.forEach { (preferences, values) ->
            val editor = preferences.edit().clear()
            values.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            editor.commit()
        }
        savedFiles.forEach { (file, bytes) -> if (bytes != null) file.writeBytes(bytes) else file.delete() }
        XMLPrefsManager.dispose()
        XMLPrefsManager.loadCommons(context)
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.instance?.dispose()
        if (File(Tuils.getFolder(), "notifications.xml").isFile) {
            ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        }
        LauncherSettings.refreshFromLoadedPrefs()
    }

    private fun open(dryRun: Boolean = false): ActivityScenario<StartupMenuActivity> {
        StartupMenuManager.prepare(if (dryRun) preview else real)
        return ActivityScenario.launch<StartupMenuActivity>(Intent(context, StartupMenuActivity::class.java)
            .putExtra(StartupMenuManager.EXTRA_PREVIEW, dryRun))
    }
    private fun idle() { instrumentation.waitForIdleSync(); SystemClock.sleep(120) }
    private fun next(scenario: ActivityScenario<StartupMenuActivity>) {
        scenario.onActivity { it.findViewById<View>(R.id.startup_next).performClick() }; idle()
    }
    private fun texts(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun previewBorders(view: View): List<TerminalBorderDrawable> {
        val background = view.background
        val borders = when (background) {
            is TerminalBorderDrawable -> listOf(background)
            is android.graphics.drawable.StateListDrawable -> (0 until background.stateCount)
                .mapNotNull { background.getStateDrawable(it) as? TerminalBorderDrawable }
            else -> emptyList()
        }
        return borders + if (view is ViewGroup) (0 until view.childCount).flatMap { previewBorders(view.getChildAt(it)) } else emptyList()
    }
    private fun await(message: String, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 8000
        while (!condition() && SystemClock.uptimeMillis() < until) { idle() }
        assertTrue(message, condition())
    }
    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun migrationDistinguishesExistingUsersFromUnfinishedAndUnopenedInstalls() {
        real.edit().clear().commit()
        StartupMenuManager.initialize(context, existingConfiguration = false)
        assertTrue("An updated but unopened install is still pending", StartupMenuManager.isFirstInstallPending(context))
        StartupMenuManager.initialize(context, existingConfiguration = true)
        assertTrue("Default files created on first launch cannot complete startup", StartupMenuManager.isFirstInstallPending(context))
        real.edit().clear().commit()
        StartupMenuManager.initialize(context, existingConfiguration = true)
        assertFalse("Legacy configured users must not be reset", StartupMenuManager.isFirstInstallPending(context))
        real.edit().clear().putInt("menu_version", 3).putBoolean("active", false)
            .putString("border", "dashed").putString("level", "advanced").commit()
        StartupMenuManager.initialize(context, existingConfiguration = true)
        StartupMenuManager.prepare(real)
        assertTrue("Old Later state remains unfinished", StartupMenuManager.isFirstInstallPending(context))
        assertEquals("dashed", StartupMenuManager.border(context))
        assertEquals("advanced", StartupMenuManager.level(context))
        assertEquals(StartupMenuManager.WELCOME, StartupMenuManager.page(context))
        real.edit().clear().putBoolean("complete", true).commit()
        StartupMenuManager.initialize(context, existingConfiguration = false)
        assertFalse(StartupMenuManager.isFirstInstallPending(context))
    }

    @Test fun reopeningKeepsChoicesPageAndScrollAndCancelCannotCompleteStartup() {
        StartupMenuManager.prepare(real)
        StartupMenuManager.setLevel(context, StartupMenuManager.ADVANCED)
        StartupMenuManager.setBorder(context, StartupMenuManager.BORDER_NONE)
        StartupMenuManager.toggleCrt(context)
        StartupMenuManager.setPage(context, StartupMenuManager.CONFIGURE)
        StartupMenuManager.setConfigScroll(context, 240)
        StartupMenuManager.prepare(real)
        assertEquals("advanced", StartupMenuManager.level(context))
        assertEquals("none", StartupMenuManager.border(context))
        assertTrue(StartupMenuManager.crt(context))
        assertEquals(1, StartupMenuManager.page(context))
        assertEquals(240, StartupMenuManager.configScroll(context))
        StartupMenuManager.cancel(context)
        assertTrue(StartupMenuManager.isActive(context))
        assertTrue(StartupMenuManager.isFirstInstallPending(context))
        try { StartupMenuManager.finish(context); fail("An earlier page cannot apply settings") }
        catch (_: IllegalStateException) { }
    }

    @Test fun backCannotDismissWelcomeAndRecreationKeepsConfigurationScroll() {
        open().use { scenario ->
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            idle()
            scenario.onActivity {
                assertNull(it.findViewById<View>(R.id.startup_back))
                it.onBackPressedDispatcher.onBackPressed()
                assertFalse(it.isFinishing)
                assertEquals(0, StartupMenuManager.page(it))
            }
            next(scenario)
            scenario.onActivity {
                texts(it.window.decorView).first { text -> text.text.toString() == it.getString(R.string.startup_advanced) }.performClick()
            }
            idle()
            var offset = 0
            var toggledLabel = ""
            scenario.onActivity {
                val scroll = it.findViewById<ScrollView>(R.id.startup_scroll)
                scroll.scrollTo(0, 330); offset = scroll.scrollY
                assertTrue("Use a real scrolled configuration", offset > 0)
                val toggle = texts(scroll).first { text -> text.text.toString().startsWith(it.getString(R.string.startup_tasker) + "\n") }
                (toggle.parent as View).performClick()
                assertSame("Toggling does not rebuild the scroll view", scroll, it.findViewById<ScrollView>(R.id.startup_scroll))
                assertEquals(offset, scroll.scrollY)
                toggledLabel = it.getString(R.string.startup_tasker)
            }
            scenario.recreate(); idle()
            scenario.onActivity {
                assertEquals(1, StartupMenuManager.page(it))
                assertEquals(offset, it.findViewById<ScrollView>(R.id.startup_scroll).scrollY)
                assertTrue(StartupMenuManager.toggles(it).first { toggle -> toggle.label == toggledLabel }.enabled)
                it.onBackPressedDispatcher.onBackPressed()
                assertFalse(it.isFinishing)
                assertEquals(0, StartupMenuManager.page(it))
            }
        }
    }

    @Test fun landscapeKeepsAllPagesScrollableAndNavigationReachable() {
        open(dryRun = true).use { scenario ->
            scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            idle()
            for (page in 0..2) {
                scenario.onActivity {
                    assertEquals(android.content.res.Configuration.ORIENTATION_LANDSCAPE, it.resources.configuration.orientation)
                    assertEquals(page, StartupMenuManager.page(it))
                    val scroll = it.findViewById<ScrollView>(R.id.startup_scroll)
                    val next = it.findViewById<View>(R.id.startup_next)
                    assertTrue("Content remains readable by scrolling", scroll.height > 0)
                    assertTrue("Navigation retains a full touch target", next.height >= (48 * it.resources.displayMetrics.density).toInt())
                    val bounds = android.graphics.Rect()
                    assertTrue(next.getGlobalVisibleRect(bounds))
                    assertEquals("The action is not clipped by short landscape screens", next.height, bounds.height())
                }
                if (page == 0) screenshot("startup-welcome-landscape.png")
                if (page < 2) next(scenario)
            }
            scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            idle()
            scenario.onActivity { assertEquals(2, StartupMenuManager.page(it)) }
        }
    }

    @Test fun previewBuildAndRecreationNeverChangeRealProgressOrLauncherSettings() {
        StartupMenuManager.prepare(real)
        StartupMenuManager.setPage(context, 1)
        StartupMenuManager.setBorder(context, "dashed")
        val progress = real.all.toMap()
        val files = savedFiles.keys.associateWith { if (it.exists()) it.readBytes().toList() else null }
        open(dryRun = true).use { scenario ->
            next(scenario)
            scenario.onActivity {
                StartupMenuManager.setBorder(it, "dashed")
                StartupMenuManager.setBorderStroke(it, 3.5f)
                StartupMenuManager.setBorderGap(it, 9)
            }
            next(scenario)
            scenario.onActivity {
                assertTrue(StartupMenuManager.isDryRun(it))
                it.findViewById<View>(R.id.startup_next).performClick()
                assertTrue(StartupMenuManager.isBuilding(it))
            }
            scenario.recreate()
            await("Preview must finish after recreation") { !preview.getBoolean("active", false) }
        }
        assertEquals(progress, real.all)
        files.forEach { (file, bytes) -> assertEquals("Preview cannot write ${file.name}", bytes, if (file.exists()) file.readBytes().toList() else null) }
    }

    @Test fun borderControlsPreviewInPlacePersistAndApplyOnlyAtFinish() {
        val files = savedFiles.keys.associateWith { if (it.exists()) it.readBytes().toList() else null }
        open().use { scenario ->
            next(scenario)
            scenario.onActivity { screen ->
                val scroll = screen.findViewById<ScrollView>(R.id.startup_scroll)
                val controls = screen.findViewById<View>(R.id.startup_dash_controls)
                val pane = screen.findViewById<View>(R.id.startup_preview_panel)
                fun select(label: Int) = texts(scroll).first { it.text.toString().removePrefix("✓ ") == screen.getString(label) }.performClick()
                assertEquals(View.GONE, controls.visibility)
                select(R.string.startup_dashed)
                assertEquals(View.VISIBLE, controls.visibility)
                screen.findViewById<SeekBar>(R.id.startup_border_stroke).progress = 15
                val header = texts(screen.window.decorView).first { it.text.toString() == screen.getString(R.string.startup_startup) }
                assertTrue("Maximum stroke must leave room for the header text", header.paddingTop > (header.background as TerminalBorderDrawable).strokeWidthPx)
                screen.findViewById<SeekBar>(R.id.startup_border_stroke).progress = 6
                screen.findViewById<SeekBar>(R.id.startup_border_gap).progress = 8
                assertSame(scroll, screen.findViewById<ScrollView>(R.id.startup_scroll))
                assertEquals(3.5f, StartupMenuManager.borderStroke(screen), 0f)
                assertEquals(9, StartupMenuManager.borderGap(screen))
                assertEquals((3.5f * screen.resources.displayMetrics.density).toInt(), (pane.background as TerminalBorderDrawable).strokeWidthPx)
                select(R.string.startup_none)
                assertEquals(0, (pane.background as TerminalBorderDrawable).strokeWidthPx)
                assertEquals(View.GONE, controls.visibility)
                select(R.string.startup_solid)
                assertTrue((pane.background as TerminalBorderDrawable).strokeWidthPx > 0)
                select(R.string.startup_dashed)
                assertEquals(3.5f, StartupMenuManager.borderStroke(screen), 0f)
                assertEquals(9, StartupMenuManager.borderGap(screen))
            }
            idle(); screenshot("startup-dashed-preview.png")
            scenario.recreate(); idle()
            scenario.onActivity {
                assertEquals(6, it.findViewById<SeekBar>(R.id.startup_border_stroke).progress)
                assertEquals(8, it.findViewById<SeekBar>(R.id.startup_border_gap).progress)
                assertFalse("Staging cannot show Android input", it.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true)
            }
            files.forEach { (file, bytes) -> assertEquals("Staging cannot write ${file.name}", bytes, if (file.exists()) file.readBytes().toList() else null) }
        }
        StartupMenuManager.toggleAutoTheme(context)
        StartupMenuManager.setPage(context, StartupMenuManager.THANKS)
        StartupMenuManager.setBuilding(context, true)
        StartupMenuManager.finish(context)
        assertEquals("3.5", LauncherSettings.get(Ui.dashed_border_stroke_width))
        assertEquals(9, LauncherSettings.getInt(Ui.dashed_border_gap_length))
        assertTrue(LauncherSettings.getBoolean(Ui.enable_dashed_border))
        LauncherSettings.refreshFromLoadedPrefs()
        assertFalse("Refreshing must read the notification owner, not stale XML defaults", LauncherSettings.getBoolean(Notifications.terminal_notifications))
        StartupMenuManager.setBorderStroke(context, Float.NaN)
        assertEquals(1.5f, StartupMenuManager.borderStroke(context), 0f)
        StartupMenuManager.setBorderStroke(context, Float.POSITIVE_INFINITY)
        assertEquals(1.5f, StartupMenuManager.borderStroke(context), 0f)
        StartupMenuManager.setBorderStroke(context, 50f)
        assertEquals(8f, StartupMenuManager.borderStroke(context), 0f)
        StartupMenuManager.setBorderStroke(context, -1f)
        assertEquals(0.5f, StartupMenuManager.borderStroke(context), 0f)
        StartupMenuManager.setBorderGap(context, 0)
        assertEquals(1, StartupMenuManager.borderGap(context))
        StartupMenuManager.setBorderGap(context, 100)
        assertEquals(24, StartupMenuManager.borderGap(context))
    }

    @Test fun buildPrintsChosenPseudoCodeIncrementallyAlongsideProgress() {
        open(dryRun = true).use { scenario ->
            scenario.onActivity {
                StartupMenuManager.setBorder(it, "dashed")
                StartupMenuManager.setBorderStroke(it, 3.5f)
                StartupMenuManager.setBorderGap(it, 9)
                StartupMenuManager.toggleAutoTheme(it)
            }
            next(scenario); next(scenario)
            lateinit var screen: StartupMenuActivity
            scenario.onActivity { screen = it }
            var initial = ""
            instrumentation.runOnMainSync {
                screen.findViewById<View>(R.id.startup_next).performClick()
            }
            SystemClock.sleep(150)
            instrumentation.runOnMainSync {
                initial = screen.findViewById<TextView>(R.id.startup_build_output).text.toString()
                assertTrue(initial.startsWith("launcher.configure {"))
                assertTrue(preview.getBoolean("active", false))
            }
            SystemClock.sleep(1100)
            instrumentation.runOnMainSync {
                val output = screen.findViewById<TextView>(R.id.startup_build_output).text.toString()
                assertTrue(output.length > initial.length)
                assertTrue(output.contains("palette = theme.keep_current"))
                assertTrue(output.contains("border = dashed"))
                assertTrue(output.contains("stroke = 3.5 dp"))
                assertTrue(output.contains("gap = 9 dp"))
                val bar = screen.findViewById<TextView>(R.id.startup_build_progress)
                assertTrue(bar.text.contains('#'))
                assertNotEquals("[##########]", bar.text.toString())
                val bounds = android.graphics.Rect()
                assertTrue("Progress remains visible while the code scrolls", bar.getGlobalVisibleRect(bounds))
            }
            screenshot("startup-pseudo-code.png")
            await("The illustrated configuration finishes") { !preview.getBoolean("active", false) }
        }
    }

    @Test fun everySetupSurfaceFollowsTheDraftIncludingPressedAndToggleStates() {
        val files = savedFiles.keys.associateWith { if (it.exists()) it.readBytes().toList() else null }
        open(dryRun = true).use { scenario ->
            next(scenario)
            scenario.onActivity { screen ->
                texts(screen.window.decorView).first { it.text.toString() == screen.getString(R.string.startup_advanced) }.performClick()
                val scroll = screen.findViewById<ScrollView>(R.id.startup_scroll)
                fun choose(label: Int) = texts(scroll).first { it.text.toString().removePrefix("✓ ") == screen.getString(label) }.performClick()
                choose(R.string.startup_none)
                val borders = previewBorders(screen.window.decorView)
                assertTrue("Include pane, header, all cards, buttons, switches and pressed states", borders.size >= 30)
                assertTrue("None cannot leave borders on child surfaces", borders.all { it.strokeWidthPx == 0 })
                val none = texts(scroll).first { it.text.toString() == "✓ " + screen.getString(R.string.startup_none) }
                assertTrue("Keep the selected state and checkmark without an outline", none.isSelected)
                val toggle = texts(scroll).first { it.text.toString().startsWith(screen.getString(R.string.startup_app_drawer) + "\n") }
                (toggle.parent as View).performClick()
                assertTrue("Toggling must not restore a saved-theme border", previewBorders(screen.window.decorView).all { it.strokeWidthPx == 0 })
                assertSame("Border choices must not rebuild the form", scroll, screen.findViewById<ScrollView>(R.id.startup_scroll))
            }
            idle(); screenshot("startup-none-all-surfaces.png")
            scenario.onActivity { screen ->
                val scroll = screen.findViewById<ScrollView>(R.id.startup_scroll)
                texts(scroll).first { it.text.toString() == screen.getString(R.string.startup_solid) }.performClick()
                val borders = previewBorders(screen.window.decorView)
                assertTrue(borders.all { it.strokeWidthPx > 0 })
                val dashed = TerminalBorderDrawable::class.java.getDeclaredField("dashed").apply { isAccessible = true }
                assertTrue("Solid must also reach pressed and child surfaces", borders.all { dashed.get(it) == false })
                texts(scroll).first { it.text.toString() == screen.getString(R.string.startup_dashed) }.performClick()
                screen.findViewById<SeekBar>(R.id.startup_border_stroke).progress = 6
                screen.findViewById<SeekBar>(R.id.startup_border_gap).progress = 8
                val gap = TerminalBorderDrawable::class.java.getDeclaredField("dashGapPx").apply { isAccessible = true }
                val expectedWidth = (3.5f * screen.resources.displayMetrics.density).toInt()
                val expectedGap = (9f * screen.resources.displayMetrics.density).toInt().toFloat()
                assertTrue("Dashed geometry reaches every surface", previewBorders(screen.window.decorView).all {
                    it.strokeWidthPx == expectedWidth && dashed.get(it) == true && gap.getFloat(it) == expectedGap
                })
            }
            idle(); screenshot("startup-dashed-all-surfaces.png")
            scenario.onActivity { screen ->
                texts(screen.window.decorView).first { it.text.toString() == screen.getString(R.string.startup_none) }.performClick()
            }
            next(scenario)
            scenario.onActivity { assertTrue(previewBorders(it.window.decorView).all { border -> border.strokeWidthPx == 0 }) }
            scenario.onActivity { it.findViewById<View>(R.id.startup_back).performClick() }
            scenario.recreate(); idle()
            scenario.onActivity { assertTrue(previewBorders(it.window.decorView).all { border -> border.strokeWidthPx == 0 }) }
            scenario.onActivity { it.findViewById<View>(R.id.startup_back).performClick() }
            scenario.onActivity { assertTrue("Welcome cards follow the retained draft too", previewBorders(it.window.decorView).all { border -> border.strokeWidthPx == 0 }) }
        }
        files.forEach { (file, bytes) -> assertEquals("Preview cannot write ${file.name}", bytes, if (file.exists()) file.readBytes().toList() else null) }
    }

    @Test fun requiredFlowStagesThenAppliesChoicesBeforeRevealingLauncher() {
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.instance?.dispose()
        open().use { scenario ->
            idle(); screenshot("startup-welcome.png")
            next(scenario)
            scenario.onActivity {
                StartupMenuManager.setLevel(it, StartupMenuManager.ADVANCED)
                StartupMenuManager.setBorder(it, StartupMenuManager.BORDER_NONE)
                StartupMenuManager.toggleAutoTheme(it)
                StartupMenuManager.toggle(it, StartupMenuManager.APP_DRAWER)
                assertTrue(StartupMenuManager.isFirstInstallPending(it))
            }
            scenario.recreate(); idle(); screenshot("startup-configure.png")
            next(scenario); screenshot("startup-thanks.png")
            scenario.onActivity {
                assertEquals(it.getString(R.string.startup_thanks_title), it.findViewById<TextView>(R.id.startup_page_title).text.toString())
                assertFalse("No settings applied before Build", real.getBoolean("complete", false))
                it.findViewById<View>(R.id.startup_next).performClick()
                assertTrue(StartupMenuManager.isBuilding(it))
                it.onBackPressedDispatcher.onBackPressed()
                assertFalse("Back cannot interrupt applying settings", it.isFinishing)
            }
            SystemClock.sleep(550); screenshot("startup-building.png")
            await("The final build must complete") { real.getBoolean("complete", false) }
            assertFalse(StartupMenuManager.isFirstInstallPending(context))
            assertFalse(LauncherSettings.getBoolean(Ui.enable_dashed_border))
            assertTrue(LauncherSettings.getBoolean(Behavior.swipe_up_apps_drawer))
            assertFalse("Restored startup initializes and applies notification settings", LauncherSettings.getBoolean(Notifications.terminal_notifications))
            await("The completed setup must reveal a ready launcher") {
                var ready = false
                instrumentation.runOnMainSync {
                    ready = LauncherActivity.instance?.let { it.uiManager != null && it.hasWindowFocus() && !it.isFinishing } == true
                }
                ready
            }
            assertFalse("The revealed launcher keeps the applied border choice", LauncherSettings.getBoolean(Ui.enable_dashed_border))
            assertTrue("The revealed launcher keeps the applied feature choice", LauncherSettings.getBoolean(Behavior.swipe_up_apps_drawer))
            assertFalse(LauncherSettings.getBoolean(Notifications.terminal_notifications))
            screenshot("startup-launcher-ready.png")
        }
    }

    @Test fun launcherReentryCannotBypassIncompleteStartup() {
        StartupMenuManager.prepare(real)
        StartupMenuManager.setPage(context, StartupMenuManager.THANKS)
        val monitor = instrumentation.addMonitor(StartupMenuActivity::class.java.name, null, false)
        var startup: StartupMenuActivity? = null
        var launcher: LauncherActivity? = null
        try {
            launcher = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
            startup = instrumentation.waitForMonitorWithTimeout(monitor, 6000) as? StartupMenuActivity
            assertNotNull("Launcher must reopen pending startup", startup)
            instrumentation.runOnMainSync {
                assertEquals(StartupMenuManager.THANKS, StartupMenuManager.page(checkNotNull(startup)))
                checkNotNull(startup).onBackPressedDispatcher.onBackPressed()
                assertFalse(checkNotNull(startup).isFinishing)
                assertEquals(StartupMenuManager.CONFIGURE, StartupMenuManager.page(checkNotNull(startup)))
            }
            // Simulate Home/task-stack removal; returning to Launcher must reopen the saved page.
            instrumentation.runOnMainSync { checkNotNull(startup).finish() }
            startup = null
            await("Returning to Launcher must not bypass required setup") {
                instrumentation.runOnMainSync {
                    startup = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<StartupMenuActivity>().firstOrNull { !it.isFinishing }
                }
                startup != null
            }
            instrumentation.runOnMainSync {
                val screen = checkNotNull(startup)
                assertEquals(StartupMenuManager.CONFIGURE, StartupMenuManager.page(screen))
                assertEquals(screen.getString(R.string.startup_set_up_your_launcher), screen.findViewById<TextView>(R.id.startup_page_title).text.toString())
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { launcher?.finish(); startup?.finish() }
            idle()
        }
    }
}
