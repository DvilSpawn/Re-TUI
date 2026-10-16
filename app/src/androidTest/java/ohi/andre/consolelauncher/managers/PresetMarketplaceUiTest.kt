package ohi.andre.consolelauncher.managers

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.commands.tuixt.PresetMarketplaceActivity
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PresetMarketplaceUiTest {
    @Test fun liveCatalogDownloadsAndImportsWithoutApplying() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Tuils.init(context)
        val before = PresetManager.listPresets().toSet()
        val active = Tuils.getFolder().listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }
            .associateWith { it.readBytes() }
        fun texts(view: View): List<TextView> = buildList {
            if (view is TextView) add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
        }
        val activity = instrumentation.startActivitySync(Intent(context, PresetMarketplaceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun waitFor(label: String, predicate: (TextView) -> Boolean): TextView {
            val deadline = android.os.SystemClock.uptimeMillis() + 30000
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                var found: TextView? = null
                instrumentation.runOnMainSync {
                    found = texts(activity.window.decorView).firstOrNull(predicate)
                }
                found?.let { return it }
                Thread.sleep(50)
            }
            fail("Missing $label")
            throw AssertionError(label)
        }
        try {
            val download = waitFor("live catalog", { it is Button && it.text.contains("Download", true) })
            instrumentation.runOnMainSync { download.performClick() }
            instrumentation.runOnMainSync {
                val confirm = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .filter { it !== activity.window.decorView }.flatMap(::texts)
                    .first { it.isClickable && it.text.contains("Download", true) }
                confirm.performClick()
            }
            waitFor("import completion", { it.text.contains("Imported", true) || it.text.contains("Saved preset", true) })
            assertEquals(1, (PresetManager.listPresets().toSet() - before).size)
            active.forEach { (file, bytes) -> assertArrayEquals("Marketplace applied ${file.name}", bytes, file.readBytes()) }
            waitFor("copy apply command", { it is Button && it.text.contains("Copy", true) })
            File(context.getExternalFilesDir(null), "preset-market-native.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            (PresetManager.listPresets().toSet() - before).forEach { File(PresetManager.presetsDir, it).deleteRecursively() }
        }
    }
}
