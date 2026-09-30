package ohi.andre.consolelauncher.wallpaper

import android.app.Notification
import android.app.Activity
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.ParcelFileDescriptor
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.wallpaper.nagomi.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NagomiWallpaperTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    // Animated wallpaper/TextureView frames can keep Instrumentation's idle waiter busy.
    private fun openPreview(): Activity {
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context, RetuiWallpaperActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        var activity: Activity? = null
        val deadline = SystemClock.uptimeMillis() + 5000
        while (activity == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull { it is RetuiWallpaperActivity }
            }
            if (activity == null) SystemClock.sleep(50)
        }
        return requireNotNull(activity) { "Wallpaper preview did not resume" }
    }

    @Test(timeout = 45000) fun systemNotificationReachesThePondThroughTheListener() {
        val oldScene = RetuiWallpaperSettings.scene(context)
        RetuiWallpaperSettings.saveScene(context, NagomiView.SCENE)
        val activity = openPreview()
        try {
            android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(
                context, ohi.andre.consolelauncher.managers.notifications.NotificationService::class.java))
            val deadline = SystemClock.uptimeMillis() + 15000
            val liveListener = "ComponentInfo{${context.packageName}/ohi.andre.consolelauncher.managers.notifications.NotificationService} (user 0):"
            var connected = false
            while (!connected && SystemClock.uptimeMillis() < deadline) {
                connected = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                    "dumpsys notification"
                )).bufferedReader().use { it.readText().contains(liveListener) }
                if (!connected) SystemClock.sleep(200)
            }
            assertTrue("Enable Re:TUI notification access before running this integration test", connected)
            SystemClock.sleep(300)
            val tag = "nagomi-instrumentation-${SystemClock.uptimeMillis()}"
            lateinit var view: NagomiView
            var start = 0L
            var count = 0
            instrumentation.runOnMainSync {
                view = descendants(activity.window.decorView).filterIsInstance<NagomiView>().single()
                start = NagomiNotifications.after(0).lastOrNull()?.id ?: 0
                count = view.renderer.school.fish.size
            }
            fun post() {
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                    "cmd notification post -t Nagomi $tag Fish"
                )).use { it.readBytes() }
            }
            post()
            SystemClock.sleep(1000)
            instrumentation.runOnMainSync {
                assertEquals("A real OS post must reach the existing listener", 1, NagomiNotifications.after(start).size)
                assertEquals(count + 1, view.renderer.school.fish.size)
            }
            post()
            SystemClock.sleep(700)
            instrumentation.runOnMainSync {
                assertEquals("Updating the OS notification must not add another fish", 1, NagomiNotifications.after(start).size)
                assertEquals(count + 1, view.renderer.school.fish.size)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }

            RetuiWallpaperSettings.saveScene(context, oldScene)
        }
    }

    @Test(timeout = 30000) fun previewRendersMovesPausesAndKeepsCreditLinks() {
        val oldScene = RetuiWallpaperSettings.scene(context)
        RetuiWallpaperSettings.saveScene(context, NagomiView.SCENE)
        val activity = openPreview()
        try {
            SystemClock.sleep(2000)
            lateinit var view: NagomiView
            var frames = 0L
            instrumentation.runOnMainSync {
                val views = descendants(activity.window.decorView)
                view = views.filterIsInstance<NagomiView>().single()
                assertTrue("GLES must present frames", view.renderer.frames > 3)
                frames = view.renderer.frames
                assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == context.getString(R.string.nagomi_github) && it.isShown })
                assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == context.getString(R.string.nagomi_demo) && it.isShown })
            }
            val first = instrumentation.uiAutomation.takeScreenshot()
            SystemClock.sleep(1000)
            val second = instrumentation.uiAutomation.takeScreenshot()
            assertFalse("Pond must animate", first.sameAs(second))
            File(context.getExternalFilesDir(null), "nagomi-preview.png").outputStream().use { second.compress(Bitmap.CompressFormat.PNG, 100, it) }
            first.recycle(); second.recycle()
            instrumentation.runOnMainSync {
                assertTrue(view.renderer.frames > frames)
                view.setResumed(false)
                frames = view.renderer.frames
            }
            SystemClock.sleep(300)
            instrumentation.runOnMainSync {
                assertEquals(frames, view.renderer.frames)
                // Read the pond itself (without the controls). Interior pixels of each
                // enlarged source texel must stay identical, not become a blurry ramp.
                val pond = requireNotNull(view.bitmap)
                try {
                    val sourceWidth = view.renderer.school.width
                    var checked = 0
                    var identical = 0
                    for (y in 0 until pond.height step 11) {
                        for (x in 1 until pond.width - 2) {
                            if (((x - .5) * sourceWidth / pond.width).toInt() !=
                                ((x + 2.5) * sourceWidth / pond.width).toInt()) continue
                            checked++
                            if (pond.getPixel(x, y) == pond.getPixel(x + 1, y)) identical++
                        }
                    }
                    assertTrue("Check visible enlarged pixels", checked > 1000)
                    assertTrue("Pixel edges must remain crisp: $identical/$checked", identical > checked * .98)
                } finally { pond.recycle() }
                view.setResumed(true)
            }
            SystemClock.sleep(300)
            instrumentation.runOnMainSync { assertTrue(view.renderer.frames > frames) }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }

            RetuiWallpaperSettings.saveScene(context, oldScene)
        }
    }

    @Test(timeout = 30000) fun imageImportDoesNotApplyUntilConfirmedAndSurvivesOriginalDeletion() {
        val saved = NagomiBackground.saved(context)
        val old = saved.takeIf { it.isFile }?.readBytes()
        val source = File(context.cacheDir, "nagomi-test-source.png")
        val bitmap = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.MAGENTA)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var imported: File? = null
        try {
            imported = NagomiBackground.import(context, Uri.fromFile(source))
            assertArrayEquals(old, saved.takeIf { it.isFile }?.readBytes())
            source.delete()
            NagomiBackground.apply(context, imported)
            assertTrue(saved.isFile)
            val loaded = NagomiBackground.load(context, saved, 270, 540)
            val pixel = loaded.getPixel(130, 270)
            assertTrue(Color.red(pixel) > 250 && Color.blue(pixel) > 250 && Color.green(pixel) < 5)
            loaded.recycle()
            val invalid = File(context.cacheDir, "nagomi-invalid.image").apply { writeText("not an image") }
            try {
                assertTrue(runCatching { NagomiBackground.import(context, Uri.fromFile(invalid)) }.isFailure)
                assertTrue(saved.isFile)
            } finally { invalid.delete() }
            NagomiBackground.apply(context, null)
            assertFalse(saved.exists())
            NagomiBackground.load(context, null, 270, 540).recycle()
        } finally {
            source.delete(); imported?.delete()
            if (old != null) saved.writeBytes(old) else saved.delete()
        }
    }

    @Test fun backgroundScalingDoesNotBlendNeighboringTexels() {
        val source = File.createTempFile("nagomi-checker-", ".png", context.cacheDir)
        val checker = Bitmap.createBitmap(17, 29, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until checker.height) for (x in 0 until checker.width)
                checker.setPixel(x, y, if ((x + y) % 2 == 0) Color.BLACK else Color.WHITE)
            source.outputStream().use { checker.compress(Bitmap.CompressFormat.PNG, 100, it) }
            for ((width, height) in listOf(270 to 600, 9 to 13)) {
                val scaled = NagomiBackground.load(context, source, width, height)
                try {
                    for (y in 0 until height) for (x in 0 until width) {
                        val color = scaled.getPixel(x, y)
                        assertTrue("Scaling must not introduce blurred gray texels", color == Color.BLACK || color == Color.WHITE)
                    }
                } finally { scaled.recycle() }
            }
        } finally { checker.recycle(); source.delete() }
    }

    @Test fun plantsFrameThePondAndDriftWithoutCoveringItsCenter() {
        val plants = NagomiPlants()
        for ((width, height) in listOf(270 to 600, 600 to 270)) {
            val first = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val later = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                plants.drawSurface(android.graphics.Canvas(first), 0.0)
                plants.drawSurface(android.graphics.Canvas(later), 12.0)
                var green = 0; var petals = 0
                for (y in 0 until height) for (x in 0 until width) {
                    val color = first.getPixel(x, y)
                    if (Color.alpha(color) == 0) continue
                    if (Color.green(color) > Color.red(color)) green++
                    if (Color.red(color) > 220 && Color.blue(color) > 140) petals++
                }
                assertTrue("Lotus and duckweed must be visible", green > 1000)
                assertTrue("Original pink flowers must be visible", petals > 100)
                assertEquals("Custom pond bed remains visible in the center", 0, Color.alpha(first.getPixel(width / 2, height / 2)))
                assertFalse("Plants drift on the simulation clock", first.sameAs(later))
            } finally { first.recycle(); later.recycle() }
        }
    }

    @Test(timeout = 30000) fun postingSignalAddsOneColoredFishAndIgnoresUpdatesAndSummaries() {
        instrumentation.runOnMainSync {
            val oldScene = RetuiWallpaperSettings.scene(context)
            RetuiWallpaperSettings.saveScene(context, NagomiView.SCENE)
            try {
                val tag = "nagomi-test-${SystemClock.uptimeMillis()}"
                fun notification(id: Int, flags: Int = 0) = StatusBarNotification(
                    context.packageName, context.packageName, id, tag, Process.myUid(), 0, 0,
                    Notification.Builder(context).setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setColor(Color.CYAN).build().apply { this.flags = flags },
                    UserHandle.getUserHandleForUid(Process.myUid()), System.currentTimeMillis())
                val start = NagomiNotifications.after(0).lastOrNull()?.id ?: 0L
                val posted = notification(1)
                NagomiNotifications.posted(context, posted)
                NagomiNotifications.posted(context, posted)
                NagomiNotifications.posted(context, notification(2, Notification.FLAG_GROUP_SUMMARY))
                NagomiNotifications.posted(context, notification(3, Notification.FLAG_ONGOING_EVENT))
                val events = NagomiNotifications.after(start)
                assertEquals(1, events.size)
                val pond = NagomiSchool()
                val fish = pond.spawn(events.single().accent, events.single().marking)
                assertEquals(NagomiSchool.BASE_FISH + 1, pond.fish.size)
                assertNotNull(fish.accent)
                NagomiNotifications.keys.removed(posted.key)
                NagomiNotifications.posted(context, posted)
                assertEquals(2, NagomiNotifications.after(start).size)
            } finally { RetuiWallpaperSettings.saveScene(context, oldScene) }
        }
    }
}
