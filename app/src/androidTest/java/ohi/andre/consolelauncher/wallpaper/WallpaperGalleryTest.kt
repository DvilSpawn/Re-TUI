package ohi.andre.consolelauncher.wallpaper

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Opt in on an isolated package: -e wallpaperGalleryTests true. No live downloads or system apply. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class WallpaperGalleryTest {
    @Test(timeout = 45000)
    fun confirmationCancellationInstallAndExplicitPreviewKeepCurrentWallpaper() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wallpaperGalleryTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith("debug")) { "Use an isolated debug package" }
        val previousScene = RetuiWallpaperSettings.scene(context)
        val pack = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                for ((name, text) in listOf(
                    "wallpaper.json" to """{"id":"topo","api":1,"engine":"topo noise"}""",
                    "LICENSE.txt" to UUID.randomUUID().toString()
                )) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            }
        }.toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(pack).joinToString("") { "%02x".format(it.toInt() and 255) }
        val catalog = """{"schema":2,"wallpapers":[{"id":"from-a-later-build","api":1,"name":"Future","description":"Skipped","download":"downloads/future.retui-wallpaper","preview":"previews/future.gif","sha256":"$hash"},{"id":"topo","api":1,"name":"Gallery fixture","description":"Contours test","download":"downloads/topo.retui-wallpaper","preview":"previews/topo.gif","sha256":"$hash"}]}"""
        val storeRoot = File(context.filesDir, "wallpaper-packs")
        val existing = File(storeRoot, "topo")
        val backup = File(context.cacheDir, "gallery-backup-${UUID.randomUUID()}")
        if (existing.exists()) check(existing.renameTo(backup))
        Tuils.init(context)
        val archive = File(Tuils.getFolder(), "wallpaper-downloads/topo-${hash.take(12)}.retui-wallpaper")
        val archiveExisted = archive.exists()
        val requested = AtomicInteger()
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val originalClient = WallpaperLibrary.client
        WallpaperLibrary.client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().url.toString() == WallpaperLibrary.CATALOG_URL) {
                catalog.toResponseBody("application/json".toMediaType())
            } else {
                val attempt = requested.incrementAndGet()
                object : ResponseBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = pack.size.toLong()
                    private val bytes = object : ForwardingSource(Buffer().write(pack)) {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            if (attempt == 1) {
                                readStarted.countDown()
                                check(releaseRead.await(10, TimeUnit.SECONDS))
                            }
                            return super.read(sink, byteCount)
                        }
                    }.buffer()
                    override fun source(): BufferedSource = bytes
                }
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        var activity: Activity? = null
        var previewIntent: Intent? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.component?.className != RetuiWallpaperActivity::class.java.name) return null
                previewIntent = Intent(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        fun views() = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
        fun waitFor(assertion: () -> Boolean) {
            val until = SystemClock.uptimeMillis() + 10000
            while (true) {
                var success = false
                instrumentation.runOnMainSync { success = assertion() }
                if (success) return
                check(SystemClock.uptimeMillis() < until) { "Timed out waiting for gallery state" }
                SystemClock.sleep(40)
            }
        }
        fun clickTag(tag: String) {
            waitFor { views().any { it.tag == tag && it.isShown } }
            instrumentation.runOnMainSync { views().single { it.tag == tag && it.isShown }.performClick() }
        }
        fun confirm() {
            waitFor { WindowInspector.getGlobalWindowViews().size > 1 && views().filterIsInstance<TextView>().any {
                it.text.toString().equals(context.getString(R.string.wallpaper_library_confirm), true)
            } }
            instrumentation.runOnMainSync {
                val dialog = WindowInspector.getGlobalWindowViews().last()
                descendants(dialog).filterIsInstance<TextView>().single {
                    it.text.toString().equals(context.getString(R.string.wallpaper_library_download), true)
                }.performClick()
            }
        }
        try {
            activity = instrumentation.startActivitySync(Intent(context, WallpaperGalleryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            clickTag("wallpaper-download-topo")
            assertEquals("Confirmation must precede package transfer", 0, requested.get())
            confirm()
            assertTrue(readStarted.await(10, TimeUnit.SECONDS))
            clickTag("wallpaper-cancel-topo")
            releaseRead.countDown()
            waitFor { views().filterIsInstance<TextView>().any { it.text == context.getString(R.string.wallpaper_library_cancelled) } }
            assertFalse(WallpaperLibrary.store(context).installed("topo"))
            assertFalse(archive.exists())
            assertEquals(previousScene, RetuiWallpaperSettings.scene(context))
            clickTag("wallpaper-download-topo")
            confirm()
            waitFor { views().any { it.tag == "wallpaper-use-topo" && it.isShown } }
            assertEquals(2, requested.get())
            assertTrue(WallpaperLibrary.store(context).installed("topo"))
            assertTrue(archive.isFile)
            assertEquals("Installation must not select or apply", previousScene, RetuiWallpaperSettings.scene(context))
            instrumentation.addMonitor(monitor)
            clickTag("wallpaper-use-topo")
            waitFor { previewIntent != null }
            assertEquals("topo noise", previewIntent!!.getStringExtra("preview_scene"))
            assertEquals(previousScene, RetuiWallpaperSettings.scene(context))
        } finally {
            releaseRead.countDown()
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { activity?.finish() }
            instrumentation.waitForIdleSync()
            WallpaperLibrary.client = originalClient
            File(storeRoot, "topo").deleteRecursively()
            File(storeRoot, "topo.partial").deleteRecursively()
            if (backup.exists()) check(backup.renameTo(existing))
            if (!archiveExisted) archive.delete()
        }
    }
}
