package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.AsciiArtTextView
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Same viewport and the same frozen data, including real module content; no sample scenes. */
@RunWith(AndroidJUnit4::class)
class SettingsPreviewMatrixTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun classicPortrait() = verify("classic-portrait", false, false)

    @Test fun classicLandscapeWithCustomMargins() = verify("classic-landscape-margins", false, true, mapOf(
        Ui.display_margin_top_section to "1,2,1,2",
        Ui.display_margin_bottom_section to "2,1,2,1",
        Ui.display_margin_landscape_mm to "3,1,2,1"
    ))

    @Test fun searchPortraitWithScaledSystemFont() = verify("search-portrait-scaled", true, false, mapOf(
        Ui.system_font to "true", Ui.font_size_offset to "4"
    ))

    @Test fun classicPortraitWithScaledGroupedStatus() = verify("classic-portrait-scaled-grouped", false, false, mapOf(
        Ui.font_size_offset to "4", Ui.show_ram to "true", Ui.show_time to "true", Ui.ram_index to "0", Ui.time_index to "0.1"
    ))

    @Test fun searchLandscapeWithTransparencyAndCustomFrame() = verify("search-landscape-transparent-frame", true, true, mapOf(
        Ui.system_wallpaper to "true", Theme.background_color to "#00000000",
        Theme.wallpaper_overlay_color to "#620B1614", Theme.output_background_color to "#00000000",
        Theme.ram_status_background_color to "#30010203", Theme.module_panel_background_color to "#340B1110"
    ), customFrame = true)

    @Test fun duoLeft() = verify("duo-left",false,true,mapOf(Behavior.duo_mode to "true"),duoSide="left")
    @Test fun duoRightWithSplitHeader() = verify("duo-right-split",false,true,mapOf(Behavior.duo_mode to "true",Ui.split_duo_launcher to "true"),duoSide="right")
    @Test fun duoLeftWithSwappedHeader() = verify("duo-left-swapped",false,true,mapOf(Behavior.duo_mode to "true",Behavior.duo_swap_top_panes to "true"),duoSide="left")

    private fun verify(name: String, search: Boolean, landscape: Boolean,
                       overrides: Map<XMLPrefsSave, String> = emptyMap(), customFrame: Boolean = false,duoSide:String?=null) {
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        NotificationManager.create(context)
        val values = mapOf<XMLPrefsSave, String>(
            Behavior.launcher_mode to if (search) LauncherMode.SEARCH else LauncherMode.CLASSIC,
            Behavior.search_only_mode to search.toString(),
            Behavior.orientation to "0",Behavior.duo_mode to "false",Behavior.duo_swap_top_panes to "false",Ui.split_duo_launcher to "false",
            Notifications.terminal_notifications to "false", Notifications.show_notifications to "false",
            SurfaceBorderOption.unified_status_border to "false", Ui.system_font to "false", Ui.font_size_offset to "0",
            Ui.display_margin_top_section to "0,0,0,0", Ui.display_margin_bottom_section to "0,0,0,0",
            Ui.display_margin_landscape_mm to "0,0,0,0"
        ) + overrides
        val originals = values.keys.associateWith { LauncherSettings.get(it) }
        val originalModule = ModuleManager.getActiveModule(context)
        val duoPrefs=context.getSharedPreferences(UIManager.PREFS_NAME,0)
        val duoOriginal=listOf("duo_layout","duo_last_side").associateWith { duoPrefs.getString(it,null) }
        val frameBackup = File(context.cacheDir, "preview-matrix-frames-$name").apply { deleteRecursively(); mkdirs() }
        FrameManager.copyCurrentTo(context, frameBackup)
        val frameEnabled = FrameManager.isEnabled(context)
        context.getSharedPreferences("retui_startup", 0).edit().putBoolean("initialized", true).putBoolean("complete", true).putBoolean("active", false).commit()
        context.getSharedPreferences("changelogPrefs", 0).edit().putInt("last_seen_version_code", BuildConfig.VERSION_CODE).commit()
        try {
            values.forEach { (key, value) -> LauncherSettings.set(key, value) }
            ModuleManager.setActiveModule(context, ModuleManager.MUSIC)
            if (customFrame) installFrame() else FrameManager.setEnabled(context, false)
            ActivityScenario.launch<LauncherActivity>(Intent(context, LauncherActivity::class.java)).use { scenario ->
                scenario.onActivity { it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                var expectedOrientation = false
                repeat(50) {
                    scenario.onActivity { home -> expectedOrientation = home.resources.configuration.orientation ==
                        if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
                    if (!expectedOrientation) Thread.sleep(100)
                }
                assertTrue("$name did not reach the required orientation", expectedOrientation)
                Thread.sleep(500); instrumentation.waitForIdleSync()
                if(duoSide!=null) {
                    scenario.onActivity { it.uiManager!!.setDuoLayoutMode(duoSide) }
                    Thread.sleep(500);instrumentation.waitForIdleSync()
                }
                scenario.onActivity { SettingsPreviewFixture.steadyPresentation(it.findViewById(R.id.mainview)) }
                instrumentation.waitForIdleSync()
                val saved = SettingsPreviewFixture.appearanceState()
                scenario.onActivity { home ->
                    val actual = home.findViewById<ViewGroup>(R.id.mainview)
                    assertEquals("Actual Home uses the wrong launcher mode", search, LauncherMode.isSearch())
                    assertEquals("Actual Search results visibility disagrees with the fixture", if (search) View.VISIBLE else View.GONE,
                        actual.findViewById<View>(R.id.search_mode_results_scroll).visibility)
                    if (search) assertEquals("Search must hide the Classic content", View.GONE, actual.findViewById<View>(R.id.main_container).visibility)
                    val left = bitmap(actual) // Freeze the source frame before detached measurement can advance scrollbar fading.
                    val snapshot = checkNotNull(LauncherActivity.captureHomePreview())
                    assertEquals(actual.width, snapshot.width); assertEquals(actual.height, snapshot.height)
                    val preview = snapshot.createDraft(home, snapshot.saved)
                    val activityOrientation = home.resources.configuration.orientation
                    assertEquals("Actual Home configuration must match the requested matrix orientation",
                        if(landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT, activityOrientation)
                    assertEquals("Snapshot must use the current Activity configuration rather than a stale view context",
                        activityOrientation, preview.resources.configuration.orientation)
                    SettingsPreviewFixture.steadyPresentation(preview)
                    preview.measure(View.MeasureSpec.makeMeasureSpec(snapshot.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(snapshot.height, View.MeasureSpec.EXACTLY))
                    preview.layout(0, 0, snapshot.width, snapshot.height)
                    val output = File(context.getExternalFilesDir(null), "issue23/matrix/$name").apply { mkdirs() }
                    File(output, "configuration.txt").writeText("activityOrientation=$activityOrientation\nsourceViewOrientation=${actual.resources.configuration.orientation}\npreviewOrientation=${preview.resources.configuration.orientation}\nviewport=${snapshot.width}x${snapshot.height}\n")
                    val homeBounds = bounds(actual)
                    val previewBounds = bounds(preview)
                    File(output, "full-view-tree.txt").writeText("HOME\n${fullDump(actual)}\nPREVIEW\n${fullDump(preview)}")
                    File(output, "bounds.txt").writeText("HOME\n${homeBounds.joinToString("\n")}\nPREVIEW\n${previewBounds.joinToString("\n")}")
                    val right = bitmap(preview)
                    File(output, "home.png").outputStream().use { left.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(output, "preview.png").outputStream().use { right.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    val a = IntArray(left.width * left.height); val b = IntArray(right.width * right.height)
                    left.getPixels(a, 0, left.width, 0, 0, left.width, left.height)
                    right.getPixels(b, 0, right.width, 0, 0, right.width, right.height)
                    val differences = a.indices.count { a[it] != b[it] }
                    left.recycle(); right.recycle()
                    assertEquals("$name changed component bounds or text metrics", homeBounds, previewBounds)
                    assertEquals("$name differs in $differences pixels", 0, differences)
                    assertFalse(ThemeColorResolver.hasPreview())
                }
                saved.assertUnchanged()
            }
        } finally {
            duoPrefs.edit().apply { duoOriginal.forEach { (key,value) -> if(value==null) remove(key) else putString(key,value) } }.commit()
            originals.forEach { (key, value) -> LauncherSettings.set(key, value) }
            ModuleManager.setActiveModule(context, originalModule)
            FrameManager.applyPortableState(frameBackup)
            FrameManager.setEnabled(context, frameEnabled)
            frameBackup.deleteRecursively()
        }
    }

    private fun bitmap(view: View) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun bounds(root: View): List<String> {
        val rows = mutableListOf<String>()
        fun visit(view: View, left: Float, top: Float) {
            if (view.visibility == View.GONE) return
            // An empty overlay host is not launcher presentation. Pixel comparison still includes the entire root.
            if(view.id == R.id.profile_surface_host) return
            // Controller wrappers have generated IDs and no launcher presentation identity.
            val id = if(view.id == View.NO_ID) null else runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
            if (id != null) {
                val text = if (view is TextView) " text=${view.text} size=${view.textSize} color=${view.currentTextColor} lines=${if(view is AsciiArtTextView) "canvas" else view.layout?.lineCount} spacing=${view.letterSpacing} font=${view.typeface?.style} padding=${view.paddingLeft},${view.paddingTop},${view.paddingRight},${view.paddingBottom}" else ""
                rows += "$id $left,$top,${view.width},${view.height} visibility=${view.visibility}$text"
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                val child = view.getChildAt(index)
                visit(child, left - view.scrollX + child.left + child.translationX, top - view.scrollY + child.top + child.translationY)
            }
        }
        visit(root, 0f, 0f)
        return rows.sorted()
    }

    private fun fullDump(root: View): String {
        fun visit(view: View, depth: Int): String {
            val id = runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault(view.id.toString())
            val text = if(view is TextView) " text=${view.text} size=${view.textSize} paint=${view.paint.color}/${view.paint.alpha}/${view.paintFlags} lines=${view.layout?.lineCount} minmax=${view.minHeight}/${view.maxHeight}/${view.minLines}/${view.maxLines} tags=${view.getTag(R.id.retui_font_base_size_sp)}/${view.getTag(R.id.retui_font_scaled_size_sp)}" else ""
            val row = "${" ".repeat(depth)}$id ${view.javaClass.simpleName} ${view.left},${view.top},${view.width},${view.height} alpha=${view.alpha} visibility=${view.visibility} background=${view.background?.javaClass?.simpleName}$text\n"
            return row + if(view is ViewGroup) (0 until view.childCount).joinToString("") { visit(view.getChildAt(it),depth+1) } else ""
        }
        return visit(root,0)
    }

    private fun installFrame() {
        val image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.STROKE; strokeWidth = 6f }
        canvas.drawRect(3f, 3f, 45f, 45f, paint)
        val png = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        image.recycle()
        FrameManager.beginEdit(context).apply {
            applyToAll = true
            importFrame(null, "Preview matrix.png", ByteArrayInputStream(png))
            save()
        }
        FrameManager.setEnabled(context, true)
        assertTrue("Custom frame fixture must be active", FrameManager.isActive(context, FrameTarget.MUSIC))
    }
}
