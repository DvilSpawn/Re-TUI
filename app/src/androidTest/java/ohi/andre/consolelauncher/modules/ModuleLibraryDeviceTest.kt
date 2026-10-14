package ohi.andre.consolelauncher.modules

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import ohi.andre.consolelauncher.commands.tuixt.WidgetConfigActivity
import android.view.accessibility.AccessibilityNodeInfo
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.Tuils
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.raw.module
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.modules.ModuleLibrary
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

class ModuleLibraryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var offline = false
    private var corruptDownload = false
    private var catalogueText = ""
    private var scriptOverride: String? = null
    private val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
    private fun refresh(): ModuleLibrary.Catalogue {
        val latch = CountDownLatch(1)
        var result: Result<ModuleLibrary.Catalogue>? = null
        ModuleLibrary.refresh(context) { result = it; latch.countDown() }
        assertTrue("Catalogue fetch completed", latch.await(20, TimeUnit.SECONDS))
        return result!!.getOrThrow()
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
        assertTrue("Expected UI state", condition())
    }
    private fun checked(result: LuaWidgetEngine.RenderResult): LuaWidgetEngine.RenderResult {
        assertTrue("Lua error: ${result.error}", result.error.isNullOrEmpty())
        return result
    }
    private fun fixture(key: String, overrideParts: String? = null, test: (LuaWidgetEngine, String) -> Unit) {
        val entry = ModuleLibrary.entries.single { it.key == key }
        val id = "library_test_${UUID.randomUUID().toString().replace("-", "")}"
        val base = ModuleLibrary.script(context, entry)
        val script = if (overrideParts == null) base else base.replace("local function render()", "date.parts = function() return $overrideParts end\nlocal function render()")
        LuaWidgetManager.save(id, key, script)
        if (LuaWidgetManager.requiredPermissions(script).isNotEmpty()) assertFalse(LuaWidgetManager.isTrusted(id))
        LuaWidgetManager.approve(id)
        val engine = LuaWidgetEngine(context, id, script, LuaWidgetManager.version(id), null)
        try { checked(engine.render()); test(engine, id) }
        finally { engine.dispose(); LuaWidgetManager.delete(id) }
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun click(activity: android.app.Activity, label: String) {
        instrumentation.runOnMainSync {
            val control = descendants(activity.window.decorView).filterIsInstance<TextView>()
                .first { it.isShown && it.isClickable && it.text.toString().equals(label, true) }
            assertTrue(control.performClick())
        }
        instrumentation.waitForIdleSync()
    }
    private fun popup(label: String) {
        fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
            (0 until node.childCount).flatMap { node.getChild(it)?.let { child -> nodes(child) } ?: emptyList() }
        waitFor { nodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.isClickable && it.text?.toString()?.equals(label, true) == true } }
        val node = nodes(instrumentation.uiAutomation.rootInActiveWindow).first { it.isClickable && it.text?.toString()?.equals(label, true) == true }
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        SystemClock.sleep(150)
    }
    private fun show(pack: ExecutePack, command: String) {
        instrumentation.runOnMainSync { pack.set(arrayOf(command)); module().exec(pack) }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
    }
    private fun configure(id: String, text: String, save: Boolean = true) {
        val activity = instrumentation.startActivitySync(Intent(context, WidgetConfigActivity::class.java)
            .putExtra(WidgetConfigActivity.EXTRA_WIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WidgetConfigActivity
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            descendants(activity.window.decorView).filterIsInstance<EditText>().single().setText(text)
        }
        capture("config-${id.removePrefix("oma_")}")
        if (save) click(activity, "Save") else {
            click(activity, "Cancel")
            val discard = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("DISCARD")
                .first { it.isClickable }
            assertTrue(discard.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        instrumentation.waitForIdleSync()
    }
    private fun visibleTexts(view: View): List<String> {
        val own = if (view is TextView && view.isShown) listOf(view.text.toString()) else emptyList()
        return own + if (view is ViewGroup) (0 until view.childCount).flatMap { visibleTexts(view.getChildAt(it)) } else emptyList()
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(context.getExternalFilesDir(null), "module-library-$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    @Test fun catalogueModulesWorkAndRenderOnPhone() {
        // Use the existing test package, preserving the everyday launcher setup.
        assertTrue(context.packageName.endsWith(".librarydebug"))
        catalogueText = instrumentation.context.assets.open("module-library/index.json").bufferedReader().use { it.readText() }
        val previousClient = ModuleLibrary.client
        ModuleLibrary.client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            requests.add(path)
            if (offline) throw IOException("Offline fixture")
            val bytes = if (path.endsWith("index.json")) catalogueText.toByteArray() else {
                val key = path.removePrefix("/modules/").substringBefore('/')
                scriptOverride?.toByteArray() ?: instrumentation.context.assets.open("module-library/$key.lua").use { it.readBytes() }
            }
            val responseBytes = if (corruptDownload && path.endsWith("main.lua")) "tampered".toByteArray() else bytes
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(responseBytes.toResponseBody("text/plain".toMediaType())).build()
        }.build()
        assertFalse(refresh().cached)
        assertEquals(5, ModuleLibrary.entries.size)
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        LauncherSettings.refreshFromLoadedPrefs()
        LauncherSettings.set(Notifications.show_notifications, "false")
        LauncherSettings.set(Notifications.terminal_notifications, "false")
        LauncherSettings.set(Behavior.tui_notification, "false")
        LauncherSettings.set(Behavior.show_module_dock, "true")
        ChangelogManager.markSeen(context)
        context.getSharedPreferences("retui_startup", 0).edit()
            .putBoolean("complete", true).putBoolean("active", false).commit()
        val activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        instrumentation.waitForIdleSync()
        assertNotNull(activity.uiManager)
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!activity.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue("Launcher must be foreground", activity.hasWindowFocus())
        try {
            val tracking = ModuleLibrary.entries.first().copy(id = "flow_test_" + UUID.randomUUID().toString().replace("-", ""))
            val original = ModuleLibrary.script(context, tracking)
            try {
                ModuleLibrary.install(context, tracking)
                assertFalse(ModuleLibrary.status(context, tracking).contains("Edited"))
                val saved = File(LuaWidgetManager.widgetDir(tracking.id), "prefs.json")
                saved.writeText("{\"task\":\"keep me\"}")
                assertFalse(ModuleLibrary.status(context, tracking).contains("Edited"))
                LuaWidgetManager.save(tracking.id, tracking.title, original + "\n-- local edit")
                assertTrue(ModuleLibrary.status(context, tracking).contains("Edited locally"))
                scriptOverride = original + "\n-- publisher update"
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(scriptOverride!!.toByteArray())
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                val updated = tracking.copy(sha256=digest, version="1.1")
                assertTrue(ModuleLibrary.status(context, updated).contains("Update available"))
                corruptDownload = true
                try { ModuleLibrary.install(context, updated, true); fail("Corrupt update accepted") } catch (_: IllegalArgumentException) {}
                assertTrue(LuaWidgetManager.readScript(tracking.id)!!.contains("local edit"))
                corruptDownload = false
                ModuleLibrary.install(context, updated, true)
                assertEquals(scriptOverride, LuaWidgetManager.readScript(tracking.id))
                assertTrue(saved.readText().contains("keep me"))
                assertEquals("Installed", ModuleLibrary.status(context, updated))
            } finally { scriptOverride=null; corruptDownload=false; ModuleLibrary.remove(context, tracking.id) }
            offline = true
            assertTrue("Saved catalogue available offline", refresh().cached)
            offline = false
            val badCatalogue = JSONObject(catalogueText)
            badCatalogue.getJSONArray("modules").getJSONObject(0).put("path", "https://example.com/evil.lua")
            try { ModuleLibrary.parse(badCatalogue.toString()); fail("Foreign download URL accepted") } catch (_: IllegalArgumentException) {}
            val traversalCatalogue = JSONObject(catalogueText)
            traversalCatalogue.getJSONArray("modules").getJSONObject(0).put("path", "%2e%2e/outside.lua")
            try { ModuleLibrary.parse(traversalCatalogue.toString()); fail("Encoded traversal accepted") } catch (_: IllegalArgumentException) {}
            val aliasCatalogue = JSONObject(catalogueText)
            aliasCatalogue.getJSONArray("modules").getJSONObject(1).put("key", "todo")
            try { ModuleLibrary.parse(aliasCatalogue.toString()); fail("Duplicate alias accepted") } catch (_: IllegalArgumentException) {}
            val duplicateCatalogue = JSONObject(catalogueText)
            duplicateCatalogue.getJSONArray("modules").put(duplicateCatalogue.getJSONArray("modules").getJSONObject(0))
            try { ModuleLibrary.parse(duplicateCatalogue.toString()); fail("Duplicate ID accepted") } catch (_: IllegalArgumentException) {}
            val freshEntry = ModuleLibrary.entries.first().copy(id = "download_test_${UUID.randomUUID().toString().replace("-", "")}")
            corruptDownload = true
            try { ModuleLibrary.install(context, freshEntry); fail("Corrupt module accepted") } catch (_: IllegalArgumentException) {}
            assertFalse("Failed download leaves no installed module", LuaWidgetManager.exists(freshEntry.id))
            corruptDownload = false
            ModuleLibrary.install(context, freshEntry)
            assertTrue("Verified download installed", LuaWidgetManager.exists(freshEntry.id))
            LuaWidgetManager.delete(freshEntry.id)
            ohi.andre.consolelauncher.managers.modules.ModuleManager.removeScriptModule(context, freshEntry.id)
            fixture("todo") { engine, id ->
                checked(engine.submitConfig("save", JSONObject().put("items", "Buy milk\nRead book")))
                assertTrue(checked(engine.action("done:1")).body!!.contains("Read book"))
                assertFalse(checked(engine.render()).body!!.contains("Buy milk"))
                engine.dispose()
                val reopened = LuaWidgetEngine(context, id, LuaWidgetManager.readScript(id), LuaWidgetManager.version(id), null)
                try {
                    assertTrue(checked(reopened.render()).body!!.contains("Read book"))
                    assertTrue(checked(reopened.action("undo")).body!!.contains("Buy milk"))
                } finally { reopened.dispose() }
            }
            fixture("habits") { engine, id ->
                assertTrue(checked(engine.action("toggle:Read")).body!!.contains("[x] Read"))
                assertTrue(checked(engine.action("toggle:Read")).body!!.contains("[ ] Read"))
                engine.dispose()
                File(LuaWidgetManager.widgetDir(id), "prefs.json").writeText("{\"day\":\"2000-01-01\",\"done\":{\"Read\":true}}")
                val reopened = LuaWidgetEngine(context, id, LuaWidgetManager.readScript(id), LuaWidgetManager.version(id), null)
                try { assertTrue(checked(reopened.render()).body!!.contains("[ ] Read")) } finally { reopened.dispose() }
            }
            fixture("focus") { engine, id ->
                assertTrue(checked(engine.config()).configJson!!.contains("minutes"))
                checked(engine.submitConfig("save", JSONObject().put("minutes", 10)))
                assertEquals(1000L, checked(engine.action("start")).tickIntervalMs)
                assertTrue(checked(engine.render()).body!!.contains("remaining"))
                engine.dispose()
                val reopened = LuaWidgetEngine(context, id, LuaWidgetManager.readScript(id), LuaWidgetManager.version(id), null)
                try {
                    assertTrue(checked(reopened.render()).body!!.let { Regex("\\d{2}:\\d{2} remaining").containsMatchIn(it) })
                    assertEquals(-1L, checked(reopened.action("stop")).tickIntervalMs)
                } finally { reopened.dispose() }
            }
            fixture("bookmarks") { engine, _ ->
                val result = checked(engine.submitConfig("save", JSONObject().put("links", "Example | HTTPS://example.com\nBad | javascript:alert(1)\nEmpty | https://\nBroken | https://foo..bar\nPort | https://example.com:99999\nLabel | https://good.-bad.com")))
                assertEquals(1, result.suggestions.size)
                assertTrue(result.suggestions.single()!!.command!!.startsWith("intent -view "))
                assertTrue(result.body!!.contains("Invalid link"))
                val cancelled = checked(engine.submitConfig("cancel", JSONObject().put("links", "")))
                assertEquals(1, cancelled.suggestions.size)
            }
            fixture("year_progress") { engine, _ ->
                val result = checked(engine.render())
                for (label in listOf("Day", "Week", "Month", "Year")) assertTrue(result.body!!.contains(label))
                assertEquals(60000L, result.tickIntervalMs)
                assertEquals(4, Regex("\\d{1,3}\\.\\d%").findAll(result.body!!).count())
                assertFalse(Regex("\\d+\\.\\d{2,}%").containsMatchIn(result.body!!))
            }
            fixture("year_progress", "{year=2024,month=2,day=29,hour=12,minute=0,second=0,weekday=5}") { engine, _ ->
                val body = checked(engine.render()).body!!
                assertTrue("Leap-day month progress", body.contains("98.3%"))
                assertTrue("Leap-year progress", body.contains("16.3%"))
            }
            fixture("year_progress", "{year=2100,month=3,day=1,hour=0,minute=0,second=0,weekday=2}") { engine, _ ->
                assertTrue("Century is not a leap year", checked(engine.render()).body!!.contains("16.2%"))
            }
            val pack = object : ExecutePack(CommandGroup("")) {}.apply { context = activity }
            for (entry in ModuleLibrary.entries) {
                val id = ModuleLibrary.install(context, entry)
                // Fresh assets in this dedicated test package; normal installs keep edited copies.
                val original = ModuleLibrary.script(context, entry)
                LuaWidgetManager.save(id, entry.title, original)
                try {
                    LuaWidgetManager.save(id, entry.title, original + "\n-- user edit\n")
                    ModuleLibrary.install(context, entry)
                    assertTrue(LuaWidgetManager.readScript(id)!!.endsWith("-- user edit\n"))
                } finally { LuaWidgetManager.save(id, entry.title, original) }
                if (LuaWidgetManager.requiredPermissions(original).isNotEmpty()) {
                    show(pack, "-library ${entry.key}")
                    instrumentation.runOnMainSync {
                        assertTrue("Approval surface", visibleTexts(activity.window.decorView).any { it.contains("approval required") })
                    }
                    capture("approval-${entry.key}")
                    click(activity, "approve")
                    assertTrue(LuaWidgetManager.isTrusted(id))
                }
                instrumentation.runOnMainSync {
                    pack.set(arrayOf("-library ${entry.key}"))
                    module().exec(pack)
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(200)
                instrumentation.runOnMainSync {
                    assertTrue("Visible module ${entry.key}", visibleTexts(activity.window.decorView).any {
                        it.contains(entry.title, ignoreCase=true)
                    })
                }
                capture(entry.key)
            }
            show(pack, "-library todo")
            configure("oma_todo", "Buy milk\nRead book")
            show(pack, "-show oma_todo")
            click(activity, "Done 1")
            instrumentation.runOnMainSync { assertFalse(visibleTexts(activity.window.decorView).any { it.contains("Buy milk") }) }
            click(activity, "Undo last")
            instrumentation.runOnMainSync { assertTrue(visibleTexts(activity.window.decorView).any { it.contains("Buy milk") }) }
            configure("oma_todo", "Discard this draft", save=false)
            show(pack, "-show oma_todo")
            instrumentation.runOnMainSync { assertFalse(visibleTexts(activity.window.decorView).any { it.contains("Discard this draft") }) }
            capture("todo-interaction")
            configure("oma_habits", "Read\nStretch")
            show(pack, "-show oma_habits")
            var alreadyDone = false
            instrumentation.runOnMainSync { alreadyDone = visibleTexts(activity.window.decorView).any { it.equals("Undo Read", true) } }
            if (alreadyDone) click(activity, "Undo Read")
            click(activity, "Done Read")
            instrumentation.runOnMainSync { assertTrue(visibleTexts(activity.window.decorView).any { it.contains("[x] Read") }) }
            capture("habits-interaction")
            configure("oma_focus", "10")
            show(pack, "-show oma_focus")
            click(activity, "Start")
            SystemClock.sleep(1200)
            instrumentation.runOnMainSync { assertTrue(visibleTexts(activity.window.decorView).any { Regex("\\d{2}:\\d{2} remaining").containsMatchIn(it) }) }
            capture("focus-running")
            click(activity, "Stop")
            configure("oma_bookmarks", "Example | https://example.com\nInvalid | https://")
            show(pack, "-show oma_bookmarks")
            instrumentation.runOnMainSync { assertTrue(visibleTexts(activity.window.decorView).any { it.contains("Invalid link") }) }
            capture("bookmarks-configured")
            LauncherSettings.set(context, Behavior.show_module_dock, "false")
            show(pack, "-library YEAR_PROGRESS")
            instrumentation.runOnMainSync {
                assertTrue("Module opens with dock hidden", visibleTexts(activity.window.decorView).any { it.contains("Year Progress") })
            }
            capture("dock-hidden")
            LauncherSettings.set(context, Behavior.show_module_dock, "true")
            LuaWidgetManager.delete("oma_todo")
            ohi.andre.consolelauncher.managers.modules.ModuleManager.removeScriptModule(context, "oma_todo")
            show(pack, "-library")
            waitFor { instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("NOT INSTALLED").isNotEmpty() }
            capture("picker")
            val row = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("TODO").first { it.isClickable }
            assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            waitFor { instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Preview").isNotEmpty() }
            capture("details")
            val requestCount = requests.count { it.endsWith("main.lua") }
            popup("Cancel")
            assertFalse(LuaWidgetManager.exists("oma_todo"))
            assertEquals(requestCount, requests.count { it.endsWith("main.lua") })
            show(pack, "-library todo")
            popup("Install")
            waitFor { LuaWidgetManager.exists("oma_todo") }
            capture("picker-selection")
            fun details() {
                show(pack, "-library")
                waitFor { instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("TODO").any { it.isClickable } }
                instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("TODO").first { it.isClickable }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                instrumentation.waitForIdleSync()
            }
            details(); popup("Remove"); popup("Cancel")
            assertTrue(LuaWidgetManager.exists("oma_todo"))
            details(); popup("Remove"); popup("Remove")
            waitFor { !LuaWidgetManager.exists("oma_todo") }
            assertFalse(LuaWidgetManager.widgetDir("oma_todo").exists())
            instrumentation.runOnMainSync {
                val active = ohi.andre.consolelauncher.UIManager::class.java.getDeclaredField("activeModule").apply { isAccessible=true }
                assertNotEquals("Removed module surface must close", "oma_todo", active.get(activity.uiManager))
            }
            assertFalse(ohi.andre.consolelauncher.managers.modules.ModuleManager.getDock(context).contains("oma_todo"))
            show(pack, "-library todo"); popup("Install")
            waitFor { LuaWidgetManager.exists("oma_todo") }
            assertTrue("Catalogue requested over HTTP pipeline", requests.any { it.endsWith("index.json") })
            assertTrue("Lua scripts requested separately", requests.any { it.endsWith("main.lua") })
        } finally { ModuleLibrary.client = previousClient; instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test(timeout = 90000) fun hostedCatalogueDownloadsOnPhone() {
        assertTrue(context.packageName.endsWith(".librarydebug"))
        Tuils.init(context)
        val catalogue = refresh()
        assertFalse("Must fetch the live site, not cached data", catalogue.cached)
        assertEquals(5, catalogue.entries.size)
        for (entry in catalogue.entries) {
            val id = "live_test_${UUID.randomUUID().toString().replace("-", "")}"
            try {
                ModuleLibrary.install(context, entry.copy(id=id))
                assertTrue("Downloaded ${entry.title}", LuaWidgetManager.exists(id))
                assertEquals("1", LuaWidgetManager.apiVersion(id))
                assertEquals("module", LuaWidgetManager.getScriptType(id))
            } finally {
                LuaWidgetManager.delete(id)
                ohi.andre.consolelauncher.managers.modules.ModuleManager.removeScriptModule(context, id)
            }
        }
    }

}
