package ohi.andre.consolelauncher.modules

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.managers.lua.*
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ModuleCompletionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun init() { assertTrue(context.packageName.endsWith(".librarydebug")); Tuils.init(context) }
    private fun id() = "defaults_test_" + UUID.randomUUID().toString().replace("-", "")
    @Test fun defaultsPreserveEditsDeletionAndInterruptedUpdates() {
        init()
        val ids = (0..3).map { id() }
        val registry = File(LuaWidgetManager.widgetsDir(), ".defaults.json")
        val original = registry.takeIf { it.exists() }?.readBytes()
        val one = "-- retui = \"1\"\n-- version = \"1\"\nfunction on_load() ui:show_text('one') end"
        val two = one.replace("one", "two").replace("version = \"1\"", "version = \"2\"")
        fun update(id: String, code: String) = LuaDefaultUpdater.update(id, code) { LuaWidgetManager.save(id, id, it) }
        try {
            update(ids[0], one)
            val prefs = File(LuaWidgetManager.widgetDir(ids[0]), "prefs.json").apply { writeText("{\"custom\":true}") }
            update(ids[0], two); assertEquals(two, LuaWidgetManager.readScript(ids[0])); assertTrue(prefs.readText().contains("custom"))
            LuaWidgetManager.save(ids[0], ids[0], two + "\n-- user edit")
            update(ids[0], one); assertTrue(LuaWidgetManager.readScript(ids[0])!!.contains("user edit"))
            update(ids[1], one); LuaWidgetManager.delete(ids[1]); update(ids[1], two); assertFalse(LuaWidgetManager.exists(ids[1]))
            update(ids[2], one)
            try { LuaDefaultUpdater.update(ids[2], two) { LuaWidgetManager.scriptFile(ids[2]).writeText(it); error("Interrupted after source") }; fail() } catch (_: IllegalStateException) {}
            update(ids[2], two)
            assertEquals(two, LuaWidgetManager.readScript(ids[2]))
            assertEquals("2", LuaWidgetManager.metadata(LuaWidgetManager.readScript(ids[2]))["version"])
            update(ids[3], one)
            try { LuaDefaultUpdater.update(ids[3], two) { error("Interrupted before source") }; fail() } catch (_: IllegalStateException) {}
            update(ids[3], two); assertEquals(two, LuaWidgetManager.readScript(ids[3]))
            LuaWidgetManager.save(ids[3], ids[3], two + "\n-- local edit after interrupted download")
            update(ids[3], one); assertTrue(LuaWidgetManager.readScript(ids[3])!!.contains("local edit"))
        } finally {
            ids.forEach { LuaWidgetManager.delete(it) }
            if (original == null) registry.delete() else registry.writeBytes(original)
        }
    }
    @Test(timeout=60000) fun hostedScreenshotsAndRequirementsDownloadOnPhone() {
        init()
        val ready = CountDownLatch(1)
        var catalogue: ohi.andre.consolelauncher.managers.modules.ModuleLibrary.Catalogue? = null
        ohi.andre.consolelauncher.managers.modules.ModuleLibrary.refresh(context) { catalogue=it.getOrThrow(); ready.countDown() }
        assertTrue(ready.await(20, TimeUnit.SECONDS)); assertFalse(catalogue!!.cached)
        for (entry in catalogue!!.entries) {
            assertTrue(entry.requirements.isNotBlank()); assertTrue(entry.screenshot.endsWith(".png"))
            val latch = CountDownLatch(1)
            var success = false
            ohi.andre.consolelauncher.managers.modules.ModuleLibrary.screenshot(entry) { result ->
                result.onSuccess { success=it.width > 0 && it.height > 0; it.recycle() }; latch.countDown()
            }
            assertTrue(latch.await(20, TimeUnit.SECONDS)); assertTrue("Hosted screenshot ${entry.key}", success)
        }
    }
    @Test fun createCatalogueScreenshotsOnPhone() {
        init()
        XMLPrefsManager.loadCommons(context)
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        LauncherSettings.refreshFromLoadedPrefs()
        LauncherSettings.set(Notifications.show_notifications, "false"); LauncherSettings.set(Notifications.terminal_notifications, "false")
        LauncherSettings.set(Behavior.tui_notification, "false")
        ChangelogManager.markSeen(context)
        context.getSharedPreferences("retui_startup", 0).edit().putBoolean("complete", true).putBoolean("active", false).commit()
        val activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        instrumentation.waitForIdleSync()
        try {
            for (key in listOf("todo", "habits", "focus", "bookmarks", "year_progress")) {
                val source = instrumentation.context.assets.open("module-library/$key.lua").bufferedReader().readText()
                val id = id()
                LuaWidgetManager.save(id, key, source); LuaWidgetManager.approve(id)
                try {
                    val sample = org.json.JSONObject()
                    if (key == "todo") sample.put("items", "Buy milk\nRead a chapter")
                    if (key == "bookmarks") sample.put("links", "Example | https://example.com")
                    if (key == "focus") sample.put("minutes", 25).put("ends", System.currentTimeMillis()/1000 + 1500)
                    File(LuaWidgetManager.widgetDir(id), "prefs.json").writeText(sample.toString())
                    val result = LuaWidgetPreview.render(context, id, source, false)
                    assertTrue("Screenshot $key: ${result.error}", result.error.isNullOrBlank())
                    instrumentation.runOnMainSync {
                        val panel = activity.uiManager!!.buildLuaPreview(id, result)
                        val width = (360 * context.resources.displayMetrics.density).toInt()
                        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        panel.layout(0,0,width,panel.measuredHeight)
                        val bitmap = Bitmap.createBitmap(width,panel.height,Bitmap.Config.ARGB_8888)
                        panel.draw(Canvas(bitmap))
                        File(context.getExternalFilesDir(null), "module-screenshot-$key.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                        bitmap.recycle()
                    }
                } finally { LuaWidgetManager.delete(id) }
            }
            val focusId = id()
            val source = instrumentation.context.assets.open("module-library/focus.lua").bufferedReader().readText()
            LuaWidgetManager.save(focusId, "Focus measurement", source); LuaWidgetManager.approve(focusId)
            File(LuaWidgetManager.widgetDir(focusId), "prefs.json").writeText(org.json.JSONObject().put("minutes",25).put("ends",System.currentTimeMillis()/1000+1500).toString())
            val pack = object : ohi.andre.consolelauncher.commands.ExecutePack(ohi.andre.consolelauncher.commands.CommandGroup("")) {}.apply { this.context=activity }
            fun command(value: String) { instrumentation.runOnMainSync { pack.set(arrayOf(value)); ohi.andre.consolelauncher.commands.main.raw.module().exec(pack) }; instrumentation.waitForIdleSync() }
            try {
                command("-show $focusId")
                android.os.SystemClock.sleep(5200)
                var activeTicks = 0L
                var closedTicks = 0L
                instrumentation.runOnMainSync {
                    val field = ohi.andre.consolelauncher.UIManager::class.java.getDeclaredField("luaWidgetEngines").apply { isAccessible=true }
                    val engine = (field.get(activity.uiManager) as Map<*, *>)[focusId]!!
                    val ticks = LuaWidgetEngine::class.java.getDeclaredField("tickCount").apply { isAccessible=true }
                    activeTicks=ticks.getLong(engine)
                }
                command("-close")
                android.os.SystemClock.sleep(2200)
                instrumentation.runOnMainSync {
                    val field = ohi.andre.consolelauncher.UIManager::class.java.getDeclaredField("luaWidgetEngines").apply { isAccessible=true }
                    val engine = (field.get(activity.uiManager) as Map<*, *>)[focusId]!!
                    closedTicks=LuaWidgetEngine::class.java.getDeclaredField("tickCount").apply { isAccessible=true }.getLong(engine)
                }
                assertTrue("Focus ticks while visible", activeTicks >= 3)
                assertEquals("No ticks after closing", activeTicks, closedTicks)
                File(context.getExternalFilesDir(null), "module-polling-measurement.json").writeText(org.json.JSONObject().put("visibleMillis",5200).put("visibleTicks",activeTicks).put("closedMillis",2200).put("closedTicks",closedTicks-activeTicks).toString())
            } finally { LuaWidgetManager.delete(focusId); ohi.andre.consolelauncher.managers.modules.ModuleManager.removeScriptModule(context,focusId) }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
