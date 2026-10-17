package ohi.andre.consolelauncher.managers.widgets

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.FrameTarget
import com.dvil.retui.contract.RetuiVisualContract
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class FrameExportCacheTest {
    @Test fun unchangedFrameReusesBytesAndExportWhileSavedChangesInvalidateIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WidgetPaneIntegrationTest().fixture { _, drawer ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync { drawer.hide() }
            fun save(color: Int) {
                val image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                val png = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                image.recycle()
                FrameManager.beginEdit(context).apply {
                    applyToAll = true
                    importFrame(null, "Cache regression.png", ByteArrayInputStream(png))
                    save()
                }
            }
            val method = RetuiThemeBridge::class.java.getDeclaredMethod("frameBundle", android.content.Context::class.java,
                FrameTarget::class.java, String::class.java, String::class.java).apply { isAccessible = true }
            fun export() = method.invoke(RetuiThemeBridge, context, FrameTarget.KEYBOARD, "com.dvil.retui.keyboard", "retui-cache-regression") as android.os.Bundle
            val directory = File(context.cacheDir, "retui-cache-regression")
            directory.deleteRecursively()
            try {
                save(Color.BLUE)
                val first = FrameManager.sharedSource(context, FrameTarget.KEYBOARD)!!
                assertSame("Unchanged immutable assets reuse parsed PNG bytes", first, FrameManager.sharedSource(context, FrameTarget.KEYBOARD))
                assertTrue(export().getBoolean(RetuiVisualContract.FRAME_AVAILABLE))
                val image = File(directory, "active.png")
                assertArrayEquals(first.png, image.readBytes())
                assertTrue(image.setLastModified(1000))
                assertTrue(export().getBoolean(RetuiVisualContract.FRAME_AVAILABLE))
                assertEquals("An unchanged keyboard frame must not rewrite a PNG on resume", 1000L, image.lastModified())
                save(Color.RED)
                val changed = FrameManager.sharedSource(context, FrameTarget.KEYBOARD)!!
                assertNotEquals(first.imageId, changed.imageId)
                assertTrue(export().getBoolean(RetuiVisualContract.FRAME_AVAILABLE))
                assertArrayEquals(changed.png, image.readBytes())
                image.delete()
                assertTrue("Evicted disk exports must be rebuilt", export().getBoolean(RetuiVisualContract.FRAME_AVAILABLE))
                assertTrue(image.isFile)
            } finally { directory.deleteRecursively() }
        }
    }
}
