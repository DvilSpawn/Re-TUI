package ohi.andre.consolelauncher.presentation

import android.content.res.Configuration
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.SettingsPreviewFixture
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LauncherFontScaleDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun largeFontContext() = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 2f })
    private fun expectedPixels(view: TextView, sp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, view.resources.displayMetrics)

    @Test @SdkSuppress(minSdkVersion = 34)
    fun nonlinearConversionRoundTripsAndApplyIsIdempotentAcrossAllOffsets() {
        instrumentation.runOnMainSync {
            val large = largeFontContext()
            val probe = TextView(large).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f) }
            assertTrue("Fixture must exercise Android's nonlinear font converter", abs(probe.textSize - 48f * probe.resources.displayMetrics.scaledDensity) > 1f)
            for (base in listOf(12f, 18f, 48f)) {
                val view = TextView(large).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, base) }
                for (offset in LauncherFontScale.MIN_OFFSET..LauncherFontScale.MAX_OFFSET) {
                    LauncherFontScale.apply(view, offset)
                    val scaled = (base + offset).coerceAtLeast(1f)
                    assertEquals("Wrong nonlinear pixel size for $base sp and $offset", expectedPixels(view, scaled), view.textSize, .02f)
                    assertEquals(base, view.getTag(R.id.retui_font_base_size_sp) as Float, .02f)
                    assertEquals(scaled, LauncherFontScale.pixelsToSp(view), .02f)
                    val once = view.textSize
                    repeat(4) { LauncherFontScale.apply(view, offset) }
                    assertEquals("Repeated apply drifted at offset $offset", once, view.textSize, .001f)
                }
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
                LauncherFontScale.apply(view, 3)
                assertEquals("A deliberate external text size must replace the base", 40f, view.getTag(R.id.retui_font_base_size_sp) as Float, .02f)
                assertEquals(expectedPixels(view, 43f), view.textSize, .02f)
            }
        }
    }

    @Test @SdkSuppress(minSdkVersion = 34)
    fun explicitBaseSizeTagsRemainStableAcrossNonlinearRescaling() {
        instrumentation.runOnMainSync {
            val view = TextView(largeFontContext())
            for (base in listOf(4f, 12f, 36f)) for (offset in LauncherFontScale.MIN_OFFSET..LauncherFontScale.MAX_OFFSET) {
                val scaled = (base + offset).coerceAtLeast(1f)
                LauncherFontScale.setBaseSize(view, base, offset)
                assertEquals(base, view.getTag(R.id.retui_font_base_size_sp) as Float, 0f)
                assertEquals(scaled, view.getTag(R.id.retui_font_scaled_size_sp) as Float, 0f)
                assertEquals(expectedPixels(view, scaled), view.textSize, .02f)
                repeat(3) { LauncherFontScale.apply(view, offset) }
                assertEquals("Tagged base drifted after apply", base, view.getTag(R.id.retui_font_base_size_sp) as Float, .02f)
                assertEquals(expectedPixels(view, scaled), view.textSize, .02f)
            }
        }
    }

    @Test @SdkSuppress(minSdkVersion = 34)
    fun draftFontKeepsModuleMonoAndThemeModesWithoutWritingSettings() {
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val fixture = mapOf<XMLPrefsSave, String>(Ui.system_font to "false", Ui.font_size_offset to "0", Ui.module_body_text_size to "12")
        val originals = fixture.keys.associateWith { LauncherSettings.get(it) }
        try {
            fixture.forEach { (key, value) -> LauncherSettings.set(key, value) }
            val persisted = SettingsPreviewFixture.appearanceState()
            val hasGlobalPreview = ThemeColorResolver.hasPreview()
            val globalColor = ThemeColorResolver.color(Theme.output_text_color)
            instrumentation.runOnMainSync {
                val saved = HomeAppearance.saved()
                val changes = mapOf<XMLPrefsSave, String>(Ui.system_font to "true", Ui.font_size_offset to "4", Ui.module_body_text_size to "24")
                val draft = saved.overlay({ changes[it] }, changes::containsKey)
                val root = LinearLayout(largeFontContext())
                fun body(mode: String?) = TextView(root.context).apply {
                    id = R.id.module_text_body
                    setTag(R.id.module_text_font_mode, mode)
                    typeface = Typeface.MONOSPACE
                    text = "module output 123"
                    ModuleContentPresentation.body(this, saved, themeFont = mode == "theme")
                    root.addView(this)
                }
                val mono = body("mono"); val theme = body("theme"); val inheritedMono = body(null)
                for (view in listOf(mono, theme, inheritedMono)) {
                    assertTrue("Shared module binder must own the draft font", ModuleContentPresentation.styleDraft(view, draft, saved))
                }
                for (view in listOf(mono, theme, inheritedMono)) {
                    assertEquals("Draft module body size must include the master offset", expectedPixels(view, 28f), view.textSize, .02f)
                    assertEquals(24f, view.getTag(R.id.retui_font_base_size_sp) as Float, 0f)
                    assertEquals(28f, view.getTag(R.id.retui_font_scaled_size_sp) as Float, 0f)
                }
                assertEquals(Typeface.MONOSPACE, mono.typeface)
                assertEquals(Typeface.MONOSPACE, inheritedMono.typeface)
                assertEquals(Typeface.DEFAULT, theme.typeface)
                assertEquals("mono", mono.getTag(R.id.module_text_font_mode))
                assertEquals("theme", theme.getTag(R.id.module_text_font_mode))
                assertEquals("false", LauncherSettings.get(Ui.system_font))
                assertEquals("0", LauncherSettings.get(Ui.font_size_offset))
                assertEquals("12", LauncherSettings.get(Ui.module_body_text_size))
            }
            persisted.assertUnchanged()
            assertEquals(hasGlobalPreview, ThemeColorResolver.hasPreview())
            assertEquals(globalColor, ThemeColorResolver.color(Theme.output_text_color))
        } finally { originals.forEach { (key, value) -> LauncherSettings.set(key, value) } }
    }
}
