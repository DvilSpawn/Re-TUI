package ohi.andre.consolelauncher.navigation

import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
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
        check(context.packageName.endsWith(".palettedebug"))
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
        try { reload(); run() } finally {
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
