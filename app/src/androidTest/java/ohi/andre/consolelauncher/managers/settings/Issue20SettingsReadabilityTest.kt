package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Issue20SettingsReadabilityTest {
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun attachedPresetsKeepSettingsAndEditorsReadable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".palettedebug")) { "Use the isolated palette debug app" }
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val theme = File(Tuils.getFolder(), "theme.xml")
        val original = theme.readBytes()
        val framesEnabled = FrameManager.isEnabled(context)
        val output = File(context.getExternalFilesDir(null), "issue20").apply { mkdirs() }
        var activity: TuixtActivity? = null
        fun reload() {
            XMLPrefsManager.dispose()
            XMLPrefsManager.loadCommons(context)
            LauncherSettings.refreshFromLoadedPrefs()
            ThemeColorResolver.clearPreview()
        }
        fun capture(name: String) {
            instrumentation.waitForIdleSync()
            val drawn = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                android.view.Choreographer.getInstance().postFrameCallback {
                    android.view.Choreographer.getInstance().postFrameCallback { drawn.countDown() }
                }
            }
            check(drawn.await(3, java.util.concurrent.TimeUnit.SECONDS)) { "Settings did not draw" }
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        fun checkText(text: TextView) {
            assertTrue("${text.text} has no visible text color", Color.alpha(text.currentTextColor) > 0)
            var background = Color.BLACK
            val ancestors = generateSequence(text as View) { it.parent as? View }.toList().asReversed()
            for (view in ancestors) {
                val drawable = view.background ?: continue
                val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                drawable.draw(Canvas(bitmap))
                background = ColorUtils.compositeColors(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), background)
                bitmap.recycle()
            }
            val foreground = ColorUtils.compositeColors(text.currentTextColor, background)
            assertTrue("${text.text}: ${ColorUtils.calculateContrast(foreground, background)}", ColorUtils.calculateContrast(foreground, background) >= 4.5)
        }
        try {
            FrameManager.setEnabled(context, false)
            for (preset in listOf("modern", "earthy", "kois")) {
                theme.writeBytes(instrumentation.context.assets.open("issue20/$preset.xml").use { it.readBytes() })
                reload()
                val menu = instrumentation.startActivitySync(ThemerActivity.launchIntent(context, ThemerActivity.SECTION_HOME)) as ThemerActivity
                try {
                    capture("$preset-settings-menu")
                    instrumentation.runOnMainSync {
                        views(menu.window.decorView).filterIsInstance<androidx.recyclerview.widget.RecyclerView>().flatMap(::views)
                            .filterIsInstance<TextView>().filter { it.isShown && it.width > 0 && it.text.isNotEmpty() }.forEach(::checkText)
                    }
                } finally {
                    instrumentation.runOnMainSync { menu.finish() }
                    instrumentation.waitForIdleSync()
                }
                for ((file, query) in listOf("theme.xml" to "terminal_border_color", "ui.xml" to "show_enter_button", "behavior.xml" to "double_tap")) {
                    activity = instrumentation.startActivitySync(Intent(context, TuixtActivity::class.java)
                        .putExtra(TuixtActivity.PATH, File(Tuils.getFolder(), file).absolutePath)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TuixtActivity
                    val current = checkNotNull(activity)
                    capture("$preset-${file.removeSuffix(".xml")}-overview")
                    instrumentation.runOnMainSync {
                        views(current.window.decorView).filterIsInstance<EditText>()
                            .first { it.hint == context.getString(R.string.editor_tuixtactivity_search_settings_d25ad) }.setText(query)
                    }
                    instrumentation.waitForIdleSync()
                    instrumentation.runOnMainSync {
                        val titles = views(current.window.decorView).filterIsInstance<TextView>()
                            .filter { it.id == R.id.setting_title && it.isShown }
                        assertTrue("Missing $file rows for $query", titles.isNotEmpty())
                        views(current.window.decorView).filterIsInstance<TextView>().filter {
                            it.isShown && it.width > 0 && it.text.isNotEmpty() &&
                                it.id in listOf(R.id.setting_title, R.id.setting_description, R.id.setting_input, R.id.setting_switch)
                        }.forEach(::checkText)
                    }
                    capture("$preset-${file.removeSuffix(".xml")}-editor")
                    instrumentation.runOnMainSync { current.finish() }
                    instrumentation.waitForIdleSync()
                    activity = null
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            theme.writeBytes(original)
            reload()
            FrameManager.setEnabled(context, framesEnabled)
        }
    }
}
