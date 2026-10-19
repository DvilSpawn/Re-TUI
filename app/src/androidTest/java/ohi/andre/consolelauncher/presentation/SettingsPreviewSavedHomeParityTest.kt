package ohi.andre.consolelauncher.presentation

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.SettingsPreviewFixture
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Draft rendering must agree with a newly constructed Home after those same values are saved. */
@RunWith(AndroidJUnit4::class)
class SettingsPreviewSavedHomeParityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val components = linkedMapOf(
        "full-scene" to R.id.mainview, "static-status" to R.id.header_container,
        "input" to R.id.input_view, "prefix" to R.id.prefix_view, "output" to R.id.terminal_view,
        "header" to R.id.terminal_tray_toggle, "input-panel" to R.id.input_group,
        "output-panel" to R.id.terminal_output_border, "dock" to R.id.module_dock
    )

    @Test fun savedFontColorAndMarginsMatchTheDraftComponents() {
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        NotificationManager.create(context)
        val defaults = StatusPresentation.descriptors.associate { it.visible as XMLPrefsSave to "false" } + mapOf<XMLPrefsSave, String>(
            Ui.show_device_name to "true", Ui.show_ascii_landscape to "false", Ui.show_session_info to "false",
            Ui.show_restart_message to "false", Ui.fullscreen to "true", Ui.system_wallpaper to "false", Ui.system_font to "false", Ui.font_size_offset to "0", Ui.input_output_size to "12",
            Ui.output_header_text_size to "12", Ui.module_header_text_size to "12",
            Ui.input_field_margins to "0,0,0,0", Ui.input_area_margins to "0,0,0,0", Ui.output_field_margins to "0,0,0,0",
            Behavior.breath_out to "false", Behavior.auto_show_keyboard to "false", Behavior.launcher_mode to "classic", Behavior.search_only_mode to "false", Behavior.show_module_dock to "true",
            Behavior.output_header_mode to "normal", Behavior.output_tray_mode to "native", Behavior.auto_hide_output to "false",
            SurfaceBorderOption.unified_status_border to "false", Suggestions.show_suggestions to "false",
            Notifications.terminal_notifications to "false", Notifications.show_notifications to "false"
        )
        val changes = mapOf<XMLPrefsSave, String>(
            Ui.system_font to "true", Ui.font_size_offset to "3", Ui.input_output_size to "17",
            Ui.output_header_text_size to "15", Ui.module_header_text_size to "16",
            Theme.input_text_color to "#FF32AA77", Theme.output_text_color to "#FF54ABC8",
            Ui.input_field_margins to "2,3,4,5", Ui.input_area_margins to "3,2,4,2", Ui.output_field_margins to "2,3,4,5"
        )
        val original = (defaults.keys + changes.keys).associateWith { LauncherSettings.get(it) }
        val frameEnabled = FrameManager.isEnabled(context)
        context.getSharedPreferences("retui_startup", 0).edit().putBoolean("initialized", true).putBoolean("complete", true).putBoolean("active", false).commit()
        context.getSharedPreferences("changelogPrefs", 0).edit().putInt("last_seen_version_code", BuildConfig.VERSION_CODE).commit()
        val draftImages = linkedMapOf<String, Bitmap>()
        val draftMetrics = linkedMapOf<String, List<String>>()
        val output = File(context.getExternalFilesDir(null), "issue23/saved-home-parity").apply { mkdirs() }
        try {
            defaults.forEach { (key, value) -> LauncherSettings.set(key, value) }
            FrameManager.setEnabled(context, false)
            ActivityScenario.launch<LauncherActivity>(Intent(context, LauncherActivity::class.java)).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                val viewport = waitForStableHome(scenario, "initial-home", output)
                // Only fixture text changes here; neither commands nor media/module actions execute.
                scenario.onActivity { freezeContent(it.findViewById(R.id.mainview)) }
                waitForStableHome(scenario, "initial-frozen-content", output, viewport)
                lateinit var sourceRoot:ViewGroup
                val persisted = SettingsPreviewFixture.appearanceState()
                scenario.onActivity { home ->
                    sourceRoot=home.findViewById(R.id.mainview)
                    val snapshot = checkNotNull(LauncherActivity.captureHomePreview())
                    val draft = snapshot.createDraft(home, snapshot.saved.overlay({ changes[it] }, changes::containsKey))
                    SettingsPreviewFixture.steadyPresentation(draft)
                    measure(draft, snapshot.width, snapshot.height)
                    components.forEach { (name, id) ->
                        val view = checkNotNull(draft.findViewById<View>(id)) { "Draft $name missing" }
                        assertTrue("Draft $name has no size", view.width > 0 && view.height > 0)
                        draftImages[name] = bitmap(view)
                        draftMetrics[name] = metrics(view)
                        File(output, "$name-draft.png").outputStream().use { draftImages.getValue(name).compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                persisted.assertUnchanged()
                changes.forEach { (key, value) -> LauncherSettings.set(key, value) }
                // Save refreshes Home in place. Assert that its actual new presentation was constructed.
                var fresh=false
                repeat(50) {
                    scenario.onActivity { fresh=it.findViewById<View>(R.id.mainview)!==sourceRoot }
                    if(!fresh) Thread.sleep(100)
                }
                assertTrue("Save must construct a fresh Home presentation",fresh)
                waitForStableHome(scenario, "saved-new-home", output, viewport)
                scenario.onActivity { freezeContent(it.findViewById(R.id.mainview)) }
                waitForStableHome(scenario, "saved-frozen-content", output, viewport)
                scenario.onActivity { home ->
                    val actual = home.findViewById<ViewGroup>(R.id.mainview)
                    val metricsLog = StringBuilder()
                    val savedImages = linkedMapOf<String, Bitmap>()
                    val savedMetrics = linkedMapOf<String, List<String>>()
                    val pixelDifferences = linkedMapOf<String, Int?>()
                    try {
                        // Capture every component before any comparison can stop the test.
                        components.forEach { (name, id) ->
                            val view = checkNotNull(actual.findViewById<View>(id)) { "Saved Home $name missing" }
                            val image = bitmap(view)
                            savedImages[name] = image
                            savedMetrics[name] = metrics(view)
                            File(output, "$name-saved-home.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            val expected = draftImages.getValue(name)
                            pixelDifferences[name] = if(expected.width == image.width && expected.height == image.height) {
                                val a = IntArray(expected.width * expected.height)
                                val b = IntArray(image.width * image.height)
                                expected.getPixels(a, 0, expected.width, 0, 0, expected.width, expected.height)
                                image.getPixels(b, 0, image.width, 0, 0, image.width, image.height)
                                a.indices.count { a[it] != b[it] }
                            } else null
                            metricsLog.append("$name DRAFT ${draftMetrics.getValue(name)}\nSAVED ${savedMetrics.getValue(name)}\nPIXEL DIFFERENCES ${pixelDifferences[name] ?: "different dimensions"}\n")
                        }
                        File(output, "metrics.txt").writeText(metricsLog.toString())
                        // Keep each strict assertion after the complete diagnostic set is on disk.
                        components.keys.forEach { name ->
                            val expected = draftImages.getValue(name)
                            val image = savedImages.getValue(name)
                            assertEquals("$name width after Save", expected.width, image.width)
                            assertEquals("$name height after Save", expected.height, image.height)
                            assertEquals("$name metrics after Save", draftMetrics.getValue(name), savedMetrics.getValue(name))
                            assertEquals("$name changed pixels after Save", 0, checkNotNull(pixelDifferences.getValue(name)))
                        }
                    } finally { savedImages.values.forEach { it.recycle() } }
                }
            }
        } finally {
            draftImages.values.forEach { it.recycle() }
            original.forEach { (key, value) -> LauncherSettings.set(key, value) }
            FrameManager.setEnabled(context, frameEnabled)
        }
    }

    private data class Viewport(val decorWidth:Int, val decorHeight:Int, val width:Int, val height:Int,
                                val orientation:Int, val insets:String, val imeVisible:Boolean)

    private fun waitForStableHome(scenario:ActivityScenario<LauncherActivity>, label:String, output:File,
                                  expected:Viewport?=null):Viewport {
        val deadline = SystemClock.uptimeMillis() + 7000
        var previous:String? = null
        var stableSince = SystemClock.uptimeMillis()
        var current:Viewport? = null
        val log = StringBuilder()
        while(SystemClock.uptimeMillis() < deadline) {
            var signature = ""
            var ready = false
            scenario.onActivity { home ->
                val root = home.findViewById<ViewGroup>(R.id.mainview)
                val decor = home.window.decorView
                val windowInsets = ViewCompat.getRootWindowInsets(root)
                val safe = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                current = Viewport(decor.width, decor.height, root.width, root.height, home.resources.configuration.orientation,
                    "${safe?.left},${safe?.top},${safe?.right},${safe?.bottom}", windowInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                signature = "$current root=${System.identityHashCode(root)} padding=${root.paddingLeft},${root.paddingTop},${root.paddingRight},${root.paddingBottom}"
                var node:View?=root
                while(node!=null) {
                    val v=node
                    signature+=" parent=${v.javaClass.simpleName}:${v.id} bounds=${v.left},${v.top},${v.width},${v.height} padding=${v.paddingLeft},${v.paddingTop},${v.paddingRight},${v.paddingBottom} fit=${v.fitsSystemWindows} lp=${v.layoutParams?.width},${v.layoutParams?.height}"
                    node=v.parent as? View
                }
                signature+=" sysUi=${decor.systemUiVisibility} flags=${home.window.attributes.flags}"

                ready = decor.hasWindowFocus() && root.width > 0 && root.height > 0 && windowInsets != null &&
                    !root.isLayoutRequested && current?.imeVisible == false && !windowInsets.isVisible(WindowInsetsCompat.Type.statusBars())
            }
            val now = SystemClock.uptimeMillis()
            log.append("$now ready=$ready $signature\n")
            if(signature != previous || !ready) stableSince = now
            previous = signature
            if(ready && now - stableSince >= 500 && (expected == null || current == expected)) {
                File(output, "$label-viewport.txt").writeText(log.toString())
                return checkNotNull(current)
            }
            Thread.sleep(100)
        }
        File(output, "$label-viewport.txt").writeText(log.toString())
        error("$label viewport/insets did not settle to the real Home state: expected=$expected actual=$current")
    }

    private fun freezeContent(root: ViewGroup) {
        SettingsPreviewFixture.steadyPresentation(root)
        root.findViewById<TextView>(R.id.input_view).apply {
            text = "printf frozen launcher preview"
            if (this is EditText) isCursorVisible = false
            clearFocus()
        }
        root.findViewById<TextView>(R.id.terminal_view).text = "Frozen output: 12345\nSame content before and after Save."
    }
    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
    private fun bitmap(view: View) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    private fun metrics(root: View): List<String> {
        val result = mutableListOf<String>()
        var originX = 0f
        var originY = 0f
        var ancestor = root
        while(ancestor.id != R.id.mainview) {
            originX += ancestor.left + ancestor.translationX
            originY += ancestor.top + ancestor.translationY
            val parent = ancestor.parent as? View ?: break
            originX -= parent.scrollX
            originY -= parent.scrollY
            ancestor = parent
        }
        fun visit(view: View, left: Float, top: Float) {
            if(view.visibility == View.GONE) return
            // Empty overlay scaffolding is outside the native launcher scene; all pixels remain compared.
            if(view.id == R.id.profile_surface_host) return
            val id = if(view.id == View.NO_ID) null else runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
            if(id != null) {
                val margin = view.layoutParams as? ViewGroup.MarginLayoutParams
                result += "$id position=$left,$top size=${view.width},${view.height} visibility=${view.visibility} padding=${view.paddingLeft},${view.paddingTop},${view.paddingRight},${view.paddingBottom} margins=${margin?.leftMargin ?: 0},${margin?.topMargin ?: 0},${margin?.rightMargin ?: 0},${margin?.bottomMargin ?: 0}" +
                    if(view is TextView) " text=${view.text} px=${view.textSize} color=${view.currentTextColor} typefaceStyle=${view.typeface?.style} spacing=${view.letterSpacing} lines=${view.layout?.lineCount}" else ""
            }
            if(view is ViewGroup) for(index in 0 until view.childCount) {
                val child = view.getChildAt(index)
                visit(child, left - view.scrollX + child.left + child.translationX, top - view.scrollY + child.top + child.translationY)
            }
        }
        visit(root, originX, originY)
        return result.sorted()
    }
}
