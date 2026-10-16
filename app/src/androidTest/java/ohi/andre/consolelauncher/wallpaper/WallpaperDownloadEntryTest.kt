package ohi.andre.consolelauncher.wallpaper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException

/** Only the disposable app is reset; the installed release and system wallpaper are untouched. */
@SdkSuppress(minSdkVersion = 29)
class WallpaperDownloadEntryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("retui_wallpaper", Context.MODE_PRIVATE)
    private fun field(activity: RetuiWallpaperActivity, name: String): Any = RetuiWallpaperActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)!!
    @Before fun fresh() {
        check(context.packageName.endsWith(".calculatorpackdebug"))
        File(context.filesDir, "wallpaper-packs").deleteRecursively()
        prefs.edit().clear().putString("scene", "solid").commit()
    }
    @Test fun freshPickerContainsOnlyBuiltins() {
        ActivityScenario.launch<RetuiWallpaperActivity>(Intent(context, RetuiWallpaperActivity::class.java)).use { scenario ->
            scenario.onActivity { assertEquals(3, (field(it, "sceneSpinner") as Spinner).count) }
        }
    }
    @Test fun missingSelectedPackRetainsSettingsAndCannotSaveFallbackAfterRecreation() {
        prefs.edit().putString("scene", "clouds").putInt("cloud_scene", 6).putFloat("offset_x", 41f)
            .putInt("tree_seed", 12345).commit()
        val saved = prefs.all.toMap()
        ActivityScenario.launch<RetuiWallpaperActivity>(Intent(context, RetuiWallpaperActivity::class.java)).use { scenario ->
            fun checkMissing(screen: RetuiWallpaperActivity) {
                assertEquals("clouds", field(screen, "scene"))
                assertFalse(field(screen, "preview") is CloudsView)
                assertFalse((field(screen, "applyButton") as Button).isEnabled)
                try {
                    RetuiWallpaperActivity::class.java.getDeclaredMethod("save").apply { isAccessible = true }.invoke(screen)
                    fail("Missing artwork cannot replace the saved selection")
                } catch (error: InvocationTargetException) { assertTrue(error.cause is IllegalStateException) }
                assertEquals(saved, prefs.all)
            }
            scenario.onActivity(::checkMissing)
            scenario.recreate()
            scenario.onActivity(::checkMissing)
        }
        assertEquals(saved, prefs.all)
    }
    @Test fun liveGalleryInstallsAllPacksAndOpensRealCloudsPreviewWithoutApplying() {
        val gallery = instrumentation.startActivitySync(Intent(context, WallpaperGalleryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var preview: Activity? = null
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        fun views() = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
        fun await(check: () -> Boolean) {
            val until = SystemClock.uptimeMillis() + 35000
            while (true) {
                var passed = false
                instrumentation.runOnMainSync { passed = check() }
                if (passed) return
                if (SystemClock.uptimeMillis() >= until) {
                    var status = ""
                    instrumentation.runOnMainSync {
                        status = views().filterIsInstance<TextView>().joinToString(" | ") { it.text.toString() }
                    }
                    fail("Wallpaper flow timed out: $status")
                }
                SystemClock.sleep(40)
            }
        }
        fun click(tag: String) {
            await { views().any { it.tag == tag && it.isShown } }
            instrumentation.runOnMainSync { views().single { it.tag == tag && it.isShown }.performClick() }
        }
        try {
            for (id in WallpaperPackStore.SCENES.keys) {
                click("wallpaper-download-$id")
                instrumentation.runOnMainSync {
                    val dialog = WindowInspector.getGlobalWindowViews().last()
                    descendants(dialog).filterIsInstance<TextView>().single {
                        it.text.toString().equals(context.getString(R.string.wallpaper_library_download), true)
                    }.performClick()
                }
                await { views().any { it.tag == "wallpaper-use-$id" } }
                assertTrue(WallpaperLibrary.store(context).installed(id))
                assertEquals("solid", RetuiWallpaperSettings.scene(context))
            }
            File(context.getExternalFilesDir(null), "native-wallpaper-gallery.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val monitor = instrumentation.addMonitor(RetuiWallpaperActivity::class.java.name, null, false)
            try {
                click("wallpaper-use-clouds")
                preview = instrumentation.waitForMonitorWithTimeout(monitor, 10000)
                assertTrue(preview is RetuiWallpaperActivity)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    val screen = preview as RetuiWallpaperActivity
                    assertTrue(field(screen, "preview") is CloudsView)
                    assertEquals("clouds", field(screen, "scene"))
                    assertEquals(7, (field(screen, "sceneSpinner") as Spinner).count)
                    assertTrue((field(screen, "applyButton") as Button).isEnabled)
                }
                assertEquals("solid", RetuiWallpaperSettings.scene(context))
                SystemClock.sleep(400)
                File(context.getExternalFilesDir(null), "downloaded-clouds-preview.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { instrumentation.removeMonitor(monitor) }
        } finally {
            instrumentation.runOnMainSync { preview?.finish(); gallery.finish() }
            instrumentation.waitForIdleSync()
        }
    }
}
