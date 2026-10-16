package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit opt-in: changes only a disposable emulator's system wallpaper. */
@RunWith(AndroidJUnit4::class)
class WallpaperSystemApplyIntegrationTest {
    @Test(timeout = 120000) fun realDownloadedCloudsApplyThroughAndroidPicker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("wallpaperSystemApply") == "true")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.startsWith("sdk_")) { "Disposable emulator only" }
        val context = instrumentation.targetContext
        check(context.packageName.endsWith("debug"))
        val entry = WallpaperLibrary.loadCatalog(context).single { it.id == "clouds" }
        val archive = WallpaperLibrary.install(context, entry)
        assertTrue(archive.isFile)
        assertTrue(WallpaperLibrary.store(context).installed("clouds"))
        assertTrue(WallpaperLibrary.store(context).asset("clouds", "clouds_1_1.png").length() > 0)
        val editor = instrumentation.startActivitySync(Intent(context, RetuiWallpaperActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("preview_scene", "clouds"))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            val preview = RetuiWallpaperActivity::class.java.getDeclaredField("preview").apply { isAccessible = true }.get(editor)
            assertTrue("Downloaded pack must render in the real editor", preview is CloudsView)
            RetuiWallpaperActivity::class.java.getDeclaredMethod("useOnPhone").apply { isAccessible = true }.invoke(editor)
        }
        fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (root == null) emptyList()
            else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
        fun click(vararg labels: String): Boolean {
            val node = nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull { item ->
                labels.any { it.equals(item.text?.toString(), true) || it.equals(item.contentDescription?.toString(), true) }
            } ?: return false
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        val until = SystemClock.uptimeMillis() + 30000
        var clicked = false
        while (!clicked && SystemClock.uptimeMillis() < until) {
            clicked = click("Set wallpaper", "Apply", "Set Wallpaper")
            SystemClock.sleep(250)
        }
        assertTrue("Android system wallpaper confirmation must be clicked", clicked)
        val manager = WallpaperManager.getInstance(context)
        val component = ComponentName(context, RetuiWallpaperService::class.java)
        while (SystemClock.uptimeMillis() < until && manager.wallpaperInfo?.component != component) {
            click("Home screen", "Home and lock screens", "Home screen and lock screen")
            SystemClock.sleep(250)
        }
        assertEquals(component, manager.wallpaperInfo?.component)
        assertEquals("clouds", RetuiWallpaperSettings.scene(context))
        instrumentation.uiAutomation.executeShellCommand("input keyevent 3").close()
        SystemClock.sleep(1500)
        val output = File(context.getExternalFilesDir(null), "wallpaper-system-apply.png")
        instrumentation.uiAutomation.takeScreenshot()!!.useBitmap { bitmap -> output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        File(context.getExternalFilesDir(null), "wallpaper-system-apply.txt").writeText("component=${manager.wallpaperInfo?.component}\nscene=${RetuiWallpaperSettings.scene(context)}\narchive=${archive.absolutePath}\nsha256=${entry.sha256}\n")
        instrumentation.runOnMainSync { editor.finish() }
    }
    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try { block(this) } finally { recycle() }
}
