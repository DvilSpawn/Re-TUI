package ohi.andre.consolelauncher.navigation

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SettingsHubRecreationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(run: () -> Unit) = instrumentation.runOnMainSync(run)
    private fun reload() {
        XMLPrefsManager.dispose()
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
    }
    private fun fixture(run: () -> Unit) {
        check(context.packageName.endsWith(".palettedebug") || context.packageName.endsWith(".termuxsshdebug"))
        Tuils.init(context)
        reload()
        val original = Tuils.getFolder()
        val temporary = File(context.filesDir, "settings-hub-recreation-fixture-${java.util.UUID.randomUUID()}").apply { deleteRecursively(); mkdirs() }
        original.listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }
            .forEach { it.copyTo(File(temporary, it.name)) }
        val folder = Tuils::class.java.getDeclaredField("folder").apply { isAccessible = true }
        val clearFrames = FrameManager::class.java.getDeclaredMethod("clearCache").apply { isAccessible = true }
        folder.set(null, temporary)
        clearFrames.invoke(FrameManager)
        val windows = context.getSharedPreferences("terminal_windows", android.content.Context.MODE_PRIVATE)
        val geometry = windows.all.filterKeys { it.startsWith("settings_") }
        fun clearGeometry() { windows.edit().apply {
            windows.all.keys.filter { it.startsWith("settings_") }.forEach { remove(it) }
        }.commit() }
        clearGeometry()
        try { reload(); run() } finally {
            clearGeometry()
            windows.edit().apply { geometry.forEach { (key, value) -> when (value) {
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
            } } }.commit()
            folder.set(null, original)
            clearFrames.invoke(FrameManager)
            reload()
            temporary.deleteRecursively()
        }
    }
    private fun field(name: String) = ThemerActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun open(activity: ThemerActivity, section: String) {
        ThemerActivity::class.java.getDeclaredMethod("openSection", String::class.java, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, section, true)
    }
    private fun recreate(activity: ThemerActivity): ThemerActivity {
        val monitor = Instrumentation.ActivityMonitor(ThemerActivity::class.java.name, null, false)
        instrumentation.addMonitor(monitor)
        return try {
            main { activity.recreate() }
            (checkNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10000)) as ThemerActivity)
                .also { instrumentation.waitForIdleSync() }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    private fun checkPane(activity: android.app.Activity, prefix: String) {
        val surface = activity.findViewById<View>(R.id.settings_surface)
        assertEquals(android.graphics.Color.TRANSPARENT, (surface.background as ColorDrawable).color)
        val host = surface.parent as View
        assertTrue("Pane must be measured", surface.width > 0 && surface.height > 0)
        assertEquals("Settings always fills its safe viewport", host.width, surface.width)
        assertEquals("Settings always fills its safe viewport", host.height, surface.height)
        val header = activity.findViewById<TextView>(R.id.settings_header)
        val title = Rect(); val border = Rect()
        assertTrue(header.getGlobalVisibleRect(title))
        assertTrue(activity.findViewById<View>(R.id.settings_panel).getGlobalVisibleRect(border))
        assertTrue("Raised title intersects the pane border", title.top <= border.top && title.bottom > border.top)
        assertEquals(Tuils.getTypeface(activity), header.typeface)
        for (tag in listOf("window-toggle", "window-reset", "resize-corner", "resize-top-left", "resize-top-right", "resize-bottom-left", "header-actions")) {
            assertNull("Settings has no floating-window controls: $tag", activity.window.decorView.findViewWithTag<View>("$prefix-$tag"))
        }
    }

    private fun capture(name: String) {
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.filesDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun paneAlwaysExpandsAndIgnoresOldFloatingGeometryAcrossRecreation() = fixture {
        val windows = context.getSharedPreferences("terminal_windows", android.content.Context.MODE_PRIVATE)
        windows.edit().putBoolean("settings_maximized", false).putFloat("settings_left", .25f)
            .putFloat("settings_top", .25f).putFloat("settings_width", .5f).putFloat("settings_height", .5f).commit()
        val before = windows.all.toMap()
        var activity = instrumentation.startActivitySync(ThemerActivity.launchIntent(context, ThemerActivity.SECTION_HOME)) as ThemerActivity
        try {
            instrumentation.waitForIdleSync()
            main {
                checkPane(activity, "settings")
                open(activity, ThemerActivity.SECTION_APPEARANCE)
                val header = activity.findViewById<View>(R.id.settings_header)
                val time = android.os.SystemClock.uptimeMillis()
                for ((action, offset) in listOf(android.view.MotionEvent.ACTION_DOWN to 0f,
                    android.view.MotionEvent.ACTION_MOVE to 100f, android.view.MotionEvent.ACTION_UP to 100f)) {
                    android.view.MotionEvent.obtain(time, time + offset.toLong(), action, offset, offset, 0).also {
                        header.dispatchTouchEvent(it); it.recycle()
                    }
                }
            }
            instrumentation.waitForIdleSync()
            activity = recreate(activity)
            main {
                checkPane(activity, "settings")
                assertEquals(ThemerActivity.SECTION_APPEARANCE, field("section").get(activity))
            }
            assertEquals("Settings no longer writes floating-window geometry", before, windows.all)
            capture("settings-pane-expanded")
        } finally { main { activity.finish() }; instrumentation.waitForIdleSync() }
    }

    @Test fun editorUsesTheSameToolPane() = fixture {
        context.getSharedPreferences("terminal_windows", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("settings_editor_maximized", false).putFloat("settings_editor_width", .5f)
            .putFloat("settings_editor_height", .5f).commit()
        val source = File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.UI.path())
        val before = source.readBytes()
        val activity = instrumentation.startActivitySync(android.content.Intent(context, TuixtActivity::class.java)
            .putExtra(TuixtActivity.PATH, source.absolutePath).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity
        try {
            instrumentation.waitForIdleSync()
            main { checkPane(activity, "settings_editor") }
            capture("settings-pane-editor")
            assertArrayEquals("Opening the pane must not save settings", before, source.readBytes())
        } finally { main { activity.finish() }; instrumentation.waitForIdleSync() }
    }

    @Test fun navigationAndTypographyDraftSurviveActualActivityRecreation() = fixture {
        var activity = instrumentation.startActivitySync(ThemerActivity.launchIntent(context, ThemerActivity.SECTION_HOME)) as ThemerActivity
        try {
            main {
                open(activity, ThemerActivity.SECTION_APPEARANCE)
                open(activity, ThemerActivity.SECTION_TYPOGRAPHY)
                field("pendingFontSizeOffset").set(activity, 7)
                field("pendingUseSystemFont").set(activity, true)
                @Suppress("UNCHECKED_CAST")
                val sizes = field("pendingTypographySizes").get(activity) as MutableMap<XMLPrefsSave, Int>
                sizes[Ui.input_output_size] = 23
            }
            activity = recreate(activity)
            main {
                assertEquals(ThemerActivity.SECTION_TYPOGRAPHY, field("section").get(activity))
                assertEquals(listOf(ThemerActivity.SECTION_HOME, ThemerActivity.SECTION_APPEARANCE),
                    (field("sectionBackStack").get(activity) as Collection<*>).toList())
                assertEquals(7, field("pendingFontSizeOffset").get(activity))
                assertEquals(true, field("pendingUseSystemFont").get(activity))
                assertEquals(23, (field("pendingTypographySizes").get(activity) as Map<*, *>)[Ui.input_output_size])
            }
        } finally { main { activity.finish() }; instrumentation.waitForIdleSync() }
    }

    @Test fun frameEditSessionAndUnsavedSelectionSurviveActualActivityRecreation() = fixture {
        var activity = instrumentation.startActivitySync(ThemerActivity.launchIntent(context, ThemerActivity.SECTION_FRAMES)) as ThemerActivity
        var session: FrameManager.EditSession? = null
        try {
            instrumentation.waitForIdleSync()
            main {
                session = ThemerActivity::class.java.getDeclaredMethod("frameSession").apply { isAccessible = true }
                    .invoke(activity) as FrameManager.EditSession
                session!!.applyToAll = !session!!.applyToAll
                assertTrue(session!!.hasChanges())
            }
            activity = recreate(activity)
            main {
                val restored = ThemerActivity::class.java.getDeclaredMethod("frameSession").apply { isAccessible = true }
                    .invoke(activity) as FrameManager.EditSession
                assertSame(session, restored)
                assertTrue(restored.hasChanges())
            }
        } finally {
            main {
                ThemerActivity::class.java.getDeclaredMethod("discardFrameChanges").apply { isAccessible = true }.invoke(activity)
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }
}
