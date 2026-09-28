package ohi.andre.consolelauncher.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.content.Intent
import android.content.IntentFilter
import android.app.Instrumentation
import android.app.WallpaperManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Spinner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import ohi.andre.consolelauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudsViewTest {
    @Test fun slowFramesKeepTheSameSpeedAndPauseDoesNotJump() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val fast = CloudsView(instrumentation.targetContext).apply { setScene(8) }
            val slow = CloudsView(instrumentation.targetContext).apply { setScene(8) }
            fun snapshot(view: CloudsView): Bitmap {
                view.layout(0, 0, 360, 800)
                return Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
            try {
                fast.advance(1000)
                slow.advance(1000)
                repeat(300) { fast.advance(1100L + it * 100) }
                repeat(30) { slow.advance(2000L + it * 1000) }
                val expected = snapshot(fast)
                val actual = snapshot(slow)
                assertTrue("Frame rate must not change travel distance", expected.sameAs(actual))
                slow.resetClock()
                slow.advance(1000000)
                val resumed = snapshot(slow)
                assertTrue("Time spent hidden must not move the clouds", actual.sameAs(resumed))
                expected.recycle()
                actual.recycle()
                resumed.recycle()
            } finally {
                fast.release()
                slow.release()
            }
        }
    }

    @Test fun pickerSelectsCloudsAndSavesOnlyOnApply() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previous = RetuiWallpaperSettings.scene(context)
        val previousCloud = RetuiWallpaperSettings.cloudScene(context)
        RetuiWallpaperSettings.saveScene(context, "solid")
        val activity = instrumentation.startActivitySync(Intent(context, RetuiWallpaperActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val applyMonitor = instrumentation.addMonitor(IntentFilter(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER),
            Instrumentation.ActivityResult(0, null), true)
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
            else emptyList()
        try {
            instrumentation.runOnMainSync {
                val picker = descendants(activity.window.decorView).filterIsInstance<Spinner>().first()
                picker.setSelection((0 until picker.count).first {
                    picker.getItemAtPosition(it) == context.getString(R.string.wallpaper_clouds)
                })
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val views = descendants(activity.window.decorView)
                assertEquals(1, views.filterIsInstance<CloudsView>().size)
                assertEquals("solid", RetuiWallpaperSettings.scene(context))
                assertEquals(2, views.filterIsInstance<Spinner>().count { it.visibility == View.VISIBLE })
            }
            var cloudPreview: CloudsView? = null
            for (number in 1..8) {
                instrumentation.runOnMainSync {
                    val views = descendants(activity.window.decorView)
                    val current = views.filterIsInstance<CloudsView>().single()
                    if (cloudPreview == null) cloudPreview = current
                    assertTrue("Scene changes retain the preview view", cloudPreview === current)
                    views.filterIsInstance<Spinner>().last().setSelection(number - 1)
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(number, cloudPreview!!.sceneNumber)
                    assertEquals(previousCloud, RetuiWallpaperSettings.cloudScene(context))
                }
            }
            instrumentation.runOnMainSync {
                val views = descendants(activity.window.decorView)
                views.filterIsInstance<Button>().first {
                    it.text == context.getString(R.string.editor_retuiwallpaperactivity_use_on_phone_d908b)
                }.performClick()
                assertEquals(CloudsView.SCENE, RetuiWallpaperSettings.scene(context))
                assertEquals(8, RetuiWallpaperSettings.cloudScene(context))
            }
            instrumentation.waitForIdleSync()
            assertEquals("Apply must open Android's wallpaper screen even when Re:TUI is selected",
                1, applyMonitor.hits)
        } finally {
            instrumentation.removeMonitor(applyMonitor)
            instrumentation.runOnMainSync { activity.finish() }
            RetuiWallpaperSettings.saveScene(context, previous)
            RetuiWallpaperSettings.saveCloudScene(context, previousCloud)
            context.sendBroadcast(Intent(RetuiWallpaperService.ACTION_REFRESH).setPackage(context.packageName))
        }
    }

    @Test fun cloudsDriftWithStaticSkyCrispPixelsAndFullCoverage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val view = CloudsView(context).apply { setScene(8) }
            fun render(width: Int, height: Int): Bitmap {
                view.layout(0, 0, width, height)
                return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    view.draw(Canvas(it))
                    val pixels = IntArray(width * height)
                    it.getPixels(pixels, 0, width, 0, 0, width, height)
                    assertTrue("Every pixel stays opaque", pixels.all { pixel -> Color.alpha(pixel) == 255 })
                }
            }
            try {
                val before = render(1080, 2400)
                // At this size each source pixel fills an 8x8 block, starting at x=4.
                // This also covers the source stars, which intentionally have partial alpha.
                for (y in 0 until 2392 step 32) for (x in 4 until 1072 step 32) {
                    assertEquals("Nearest-neighbor scaling keeps flat pixel blocks",
                        before.getPixel(x, y), before.getPixel(x + 7, y + 7))
                }
                view.advance(1000L)
                repeat(300) { view.advance(1100L + it * 100L) }
                val after = render(1080, 2400)
                assertFalse("Clouds must move", before.sameAs(after))
                val skyBefore = IntArray(1080 * 200)
                val skyAfter = IntArray(skyBefore.size)
                before.getPixels(skyBefore, 0, 1080, 0, 0, 1080, 200)
                after.getPixels(skyAfter, 0, 1080, 0, 0, 1080, 200)
                assertTrue("Upper sky and stars stay fixed", skyBefore.contentEquals(skyAfter))
                File(context.getExternalFilesDir(null), "clouds-8-before.png").outputStream().use {
                    before.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                File(context.getExternalFilesDir(null), "clouds-8-after.png").outputStream().use {
                    after.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                before.recycle()
                after.recycle()
                // Cross reflected tile boundaries, resize and reload after releasing resources.
                repeat(4000) { view.advance(31100L + it * 100L) }
                render(800, 360).recycle()
                val portrait = render(360, 800)
                view.release()
                val reloaded = render(360, 800)
                assertTrue("Reattaching preserves the scene", portrait.sameAs(reloaded))
                portrait.recycle()
                reloaded.recycle()
            } finally {
                view.release()
            }
        }
    }

    @Test fun everySceneRendersAndAnimatesWithoutGaps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val view = CloudsView(context)
            view.layout(0, 0, 360, 800)
            val hashes = mutableSetOf<Int>()
            fun render(): Bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
                val pixels = IntArray(360 * 800)
                it.getPixels(pixels, 0, 360, 0, 0, 360, 800)
                assertTrue("Scene ${view.sceneNumber} fills the screen", pixels.all { pixel -> Color.alpha(pixel) == 255 })
                hashes += pixels.contentHashCode()
            }
            try {
                for (number in 1..8) {
                    view.setScene(number)
                    val before = render()
                    view.advance(1000)
                    view.advance(31000)
                    val after = render()
                    assertFalse("Scene $number must animate", before.sameAs(after))
                    File(context.getExternalFilesDir(null), "clouds-$number.png").outputStream().use {
                        before.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    before.recycle()
                    after.recycle()
                }
                assertEquals("Every sky has distinct artwork and movement", 16, hashes.size)
            } finally {
                view.release()
            }
        }
    }
}
