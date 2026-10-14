package ohi.andre.consolelauncher.modules

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetPreview
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class ModulePreviewDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun id() = "preview_test_" + UUID.randomUUID().toString().replace("-", "")
    private fun init() {
        assertTrue("Only the existing test package", context.packageName.endsWith(".librarydebug"))
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        LauncherSettings.refreshFromLoadedPrefs()
        LauncherSettings.set(Notifications.show_notifications, "false")
        LauncherSettings.set(Notifications.terminal_notifications, "false")
        LauncherSettings.set(Behavior.tui_notification, "false")
        ChangelogManager.markSeen(context)
        context.getSharedPreferences("retui_startup", 0).edit().putBoolean("complete", true).putBoolean("active", false).commit()
    }
    private fun script(api: String = "1") = """
        -- retui = "$api"
        function on_load()
            prefs.executed = true
            ui:show_text("version one works")
        end
    """.trimIndent()

    @Test fun compatibilityBlocksBeforeSavingAndEveryExecution() {
        init()
        assertNull(LuaWidgetManager.compatibilityError("function on_load() end"))
        val id = id()
        try {
            LuaWidgetManager.save(id, "Original", script())
            val original = LuaWidgetManager.readScript(id)
            for (api in listOf("2", "1.1", "banana")) {
                try { LuaWidgetManager.save(id, "Bad", script(api)); fail("Saved API $api") } catch (expected: IllegalArgumentException) {
                    assertTrue(expected.message!!.contains("supports API 1"))
                }
                assertEquals(original, LuaWidgetManager.readScript(id))
                val engine = LuaWidgetEngine(context, id, script(api), 0, null)
                try {
                    for (result in listOf(engine.render(), engine.action("test"), engine.config(), engine.suggest("test"))) {
                        assertTrue(result.error.orEmpty().contains("supports API 1"))
                    }
                } finally { engine.dispose() }
                assertFalse("Future API must not run on_load", File(LuaWidgetManager.widgetDir(id), "prefs.json").exists())
            }
            val engine = LuaWidgetEngine(context, id, original, 0, null)
            try { assertTrue(engine.render().body.orEmpty().contains("version one works")) } finally { engine.dispose() }
            assertTrue(JSONObject(File(LuaWidgetManager.widgetDir(id), "prefs.json").readText()).getBoolean("executed"))
        } finally { LuaWidgetManager.delete(id) }
    }

    @Test fun previewIsIsolatedShowsErrorsFitsAndExports() {
        init()
        val id = id()
        val source = """
            -- retui = "1"
            function on_load()
                prefs.count = (prefs.count or 0) + 1
                ui:set_title("Preview fixture")
                ui:show_text("Count: " .. prefs.count .. "\n" .. string.rep("Long text wraps safely across narrow screens. ", 12))
                ui:show_action("Do not execute", "change")
            end
            function on_action()
                prefs.count = 99
            end
        """.trimIndent()
        LuaWidgetManager.save(id, "Preview fixture", source)
        val prefs = File(LuaWidgetManager.widgetDir(id), "prefs.json").apply { writeText("{\"count\":7}") }
        val original = prefs.readText()
        val activity = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        instrumentation.waitForIdleSync()
        try {
            val saved = LuaWidgetPreview.render(context, id, source, false)
            assertTrue(saved.error.isNullOrEmpty())
            assertTrue(saved.body.orEmpty().startsWith("Count: 8"))
            val empty = LuaWidgetPreview.render(context, id, source, true)
            assertTrue(empty.body.orEmpty().startsWith("Count: 1"))
            assertEquals(original, prefs.readText())
            val broken = source + "\nfunction on_resume() ui:show_text('Partial output'); error('fixture failure') end"
            val result = LuaWidgetPreview.render(context, id, broken, false)
            assertTrue(result.error.orEmpty().contains("fixture failure"))
            assertEquals("Partial output", result.body)
            assertEquals("on_resume", result.errorStage)
            assertEquals(original, prefs.readText())
            assertTrue(LuaWidgetManager.lastError(id).isNullOrBlank())
            val external = "-- retui = \"1\"\nfunction on_load() system:open_url('https://example.com') end"
            val blocked = LuaWidgetEngine(context, id, external, 0, null, preview=true)
            try { assertTrue(blocked.render().error.orEmpty().contains("Preview blocked intents")) } finally { blocked.dispose() }
            for ((permission, call) in listOf("network" to "http:get('https://example.com', 'test')", "local-files" to "files:write('side-effect.txt', 'bad')")) {
                val sensitive = "-- retui = \"1\"\n-- permissions = \"$permission\"\nfunction on_load() $call end"
                LuaWidgetManager.save(id, "Sensitive fixture", sensitive)
                assertTrue(LuaWidgetPreview.render(context, id, sensitive, false).error.orEmpty().contains("Save and approve"))
                LuaWidgetManager.approve(id)
                assertTrue(LuaWidgetPreview.render(context, id, sensitive, false).error.orEmpty().contains("Preview blocked $permission"))
                assertFalse(File(LuaWidgetManager.widgetDir(id), "side-effect.txt").exists())
                assertEquals(original, prefs.readText())
            }
            LuaWidgetManager.save(id, "Preview fixture", source)
            var panel: View? = null
            instrumentation.runOnMainSync {
                for (dp in listOf(240, 360, 600)) {
                    panel = activity.uiManager!!.buildLuaPreview(id, saved)
                    val width = (dp * context.resources.displayMetrics.density).toInt()
                    panel!!.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    panel!!.layout(0, 0, width, panel!!.measuredHeight)
                    assertTrue(panel!!.height > 0)
                    fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
                    assertFalse(all(panel!!).any { it.isClickable })
                    all(panel!!).filterIsInstance<TextView>().forEach { text ->
                        assertTrue("Text width fits preview", text.width <= width)
                        assertTrue("Long text wraps", text.layout != null)
                    }
                }
                val zip = LuaWidgetPreview.export(context, panel!!, "{\"test\":true}")
                ZipFile(zip).use {
                    assertNotNull(it.getEntry("preview.png"))
                    val image = android.graphics.BitmapFactory.decodeStream(it.getInputStream(it.getEntry("preview.png")))
                    assertEquals(panel!!.width, image.width); image.recycle()
                    assertEquals("{\"test\":true}", it.getInputStream(it.getEntry("diagnostics.json")).bufferedReader().readText())
                }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".FILE_PROVIDER", zip)
                context.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read() >= 0) }
                zip.delete()
                val pack = object : ohi.andre.consolelauncher.commands.ExecutePack(ohi.andre.consolelauncher.commands.CommandGroup("")) {}.apply { this.context = activity }
                pack.set(arrayOf("-preview $id"))
                ohi.andre.consolelauncher.commands.main.raw.module().exec(pack)
            }
            fun waitFor(text: String) {
                val deadline = SystemClock.uptimeMillis() + 8000
                while (SystemClock.uptimeMillis() < deadline) {
                    if (instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true) return
                    SystemClock.sleep(50)
                }
                fail("Preview text missing: $text")
            }
            waitFor("Check passed")
            instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Close").first { it.isClickable }.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val editor = instrumentation.startActivitySync(Intent(context, ohi.andre.consolelauncher.commands.tuixt.WidgetEditorActivity::class.java)
                .putExtra(ohi.andre.consolelauncher.commands.tuixt.WidgetEditorActivity.EXTRA_WIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val field = editor.javaClass.getDeclaredField("codeEditor").apply { isAccessible=true }
                (field.get(editor) as android.widget.EditText).setText(broken)
            }
            val previewButton = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Preview draft").first { it.isClickable }
            previewButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor("fixture failure")
            instrumentation.waitForIdleSync()
            SystemClock.sleep(400)
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "module-preview-errors.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val width = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Width: device").first { it.isClickable }
            width.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitFor("Width: 240dp")
            waitFor("fixture failure")
            val close = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Close").first { it.isClickable }
            close.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            assertEquals(original, prefs.readText())
            assertEquals(source, LuaWidgetManager.readScript(id))
            instrumentation.runOnMainSync { editor.finish() }
        } finally { instrumentation.runOnMainSync { activity.finish() }; LuaWidgetManager.delete(id) }
    }
}
