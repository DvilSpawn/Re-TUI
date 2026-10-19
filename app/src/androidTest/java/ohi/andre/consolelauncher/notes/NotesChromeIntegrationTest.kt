package ohi.andre.consolelauncher.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class NotesChromeIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun everyStyledSurfaceHonorsDisabledSolidAndDashedBorders() {
        instrumentation.runOnMainSync {
            val themes = listOf(
                RetuiTheme(dashed = false) to false,
                RetuiTheme(dashed = true, gap = 0) to true,
                RetuiTheme(dashed = true, gap = 6) to true,
                RetuiTheme(cyberdeck = true) to true
            )
            for ((base, expectedBorder) in themes) {
                val theme = base.copy(panel = Color.BLACK, panelBorder = Color.WHITE,
                    header = Color.BLACK, inputBg = Color.BLACK, outputBg = Color.BLACK,
                    outputBorder = Color.WHITE, button = Color.BLACK, buttonBorder = Color.WHITE,
                    radius = 0, headerRadius = 0, outputRadius = 0)
                val surfaces = listOf("retui_shell", "retui_panel", "retui_card", "retui_input",
                    "retui_toolbar", "retui_accent", "retui_h1", "retui_h2").map { tag ->
                    TextView(context).apply { this.tag = tag }
                } + listOf(EditText(context), Button(context))
                for (surface in surfaces) {
                    applyRetuiStyle(surface, theme)
                    val image = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
                    try {
                        surface.background.setBounds(0, 0, image.width, image.height)
                        surface.background.draw(Canvas(image))
                        val pixels = IntArray(120 * 80)
                        image.getPixels(pixels, 0, 120, 0, 0, 120, 80)
                        assertEquals("Border on ${surface.tag ?: surface.javaClass.simpleName}, $base",
                            expectedBorder, pixels.any { it != Color.BLACK })
                    } finally { image.recycle() }
                }
            }
        }
    }

    @Test fun newNoteDialogHasFinalWidthAndNoWindowAnimation() {
        val activity = instrumentation.startActivitySync(Intent(context, RememberActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RememberActivity
        var dialog: android.app.AlertDialog? = null
        try {
            instrumentation.runOnMainSync {
                dialog = activity.showNewNotePrompt(RetuiTheme(dashed = false), emptyList(), emptyList(), null,
                    { fail("Unexpected open") }, { _, _ -> fail("Unexpected create") })
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val attributes = dialog!!.window!!.attributes
                assertEquals(0, attributes.windowAnimations)
                assertEquals((activity.resources.displayMetrics.widthPixels * .9f).toInt(), attributes.width)
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        } finally { instrumentation.runOnMainSync { dialog?.dismiss(); activity.finishAndRemoveTask() } }
    }
}
