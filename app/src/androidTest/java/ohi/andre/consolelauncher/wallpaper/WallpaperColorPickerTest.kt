package ohi.andre.consolelauncher.wallpaper

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class WallpaperColorPickerTest {
    @Test fun slidersRemainIndependentAndTypedHexStillApplies() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previousScene = RetuiWallpaperSettings.scene(context)
        RetuiWallpaperSettings.saveScene(context, "solid")
        val activity = instrumentation.startActivitySync(Intent(context, RetuiWallpaperActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var applied: Int? = null
        try {
            instrumentation.runOnMainSync {
                val show = RetuiWallpaperActivity::class.java.getDeclaredMethod(
                    "showColorPicker", Int::class.javaPrimitiveType, String::class.java, Function1::class.java
                ).apply { isAccessible = true }
                val onUse: (Int) -> Unit = { applied = it }
                show.invoke(activity, Color.rgb(64, 128, 192), "Test color", onUse)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val window = WindowInspector.getGlobalWindowViews().single { it.findViewById<View>(R.id.hex_input) != null }
                val hue = window.findViewById<SeekBar>(R.id.seek_hue)
                val sat = window.findViewById<SeekBar>(R.id.seek_sat)
                val value = window.findViewById<SeekBar>(R.id.seek_val)
                val alpha = window.findViewById<SeekBar>(R.id.seek_alpha)
                val input = window.findViewById<EditText>(R.id.hex_input)
                fun move(slider: SeekBar, target: Int) {
                    slider.keyProgressIncrement = 1
                    while (slider.progress != target) {
                        val key = if (slider.progress < target) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                        check(slider.onKeyDown(key, KeyEvent(KeyEvent.ACTION_DOWN, key)))
                    }
                }
                val originalHue = hue.progress
                val originalSat = sat.progress
                for (level in listOf(40, 1, 0, 75)) {
                    move(value, level)
                    assertEquals("Brightness must preserve hue", originalHue, hue.progress)
                    assertEquals("Brightness must preserve saturation", originalSat, sat.progress)
                }
                for (level in listOf(1, 0, 66)) {
                    move(sat, level)
                    assertEquals("Gray must retain the chosen hue", originalHue, hue.progress)
                    assertEquals(75, value.progress)
                }
                move(hue, 360)
                assertEquals(360, hue.progress)
                move(alpha, 0)
                assertEquals(360, hue.progress)
                assertEquals(66, sat.progress)
                assertEquals(75, value.progress)
                val sliderColor = Color.HSVToColor(0, floatArrayOf(360f, .66f, .75f))
                assertEquals(sliderColor, Color.parseColor(input.text.toString()))
                assertEquals(sliderColor, (window.findViewById<View>(R.id.color_preview).background as ColorDrawable).color)

                input.setText("#804080C0")
                assertEquals(128, alpha.progress)
                assertEquals(210, hue.progress)
                assertEquals(66, sat.progress)
                assertEquals(75, value.progress)
                assertEquals("#804080C0", window.findViewById<TextView>(R.id.hex_preview).text.toString())
                fun descendants(view: View): List<View> = listOf(view) +
                    if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                descendants(window).filterIsInstance<TextView>().single {
                    it.text.toString() == context.getString(R.string.editor_retuiwallpaperactivity_use_7dcf4)
                }.performClick()
                assertEquals(0x804080C0.toInt(), applied)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            RetuiWallpaperSettings.saveScene(context, previousScene)
        }
    }
}
