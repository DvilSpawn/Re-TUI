package ohi.andre.consolelauncher.commands.tuixt

import android.content.Intent
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.managers.settings.SettingsPreviewFixture
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.presentation.*
import ohi.andre.consolelauncher.tuils.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** Opt-in read-only investigation of the device's real settings; never saves a draft. */
class WirelessLauncherReviewTest {
    @Test fun outputFrameFlipKeepsItsMirrorInConstantState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            fun render(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
                val bitmap = android.graphics.Bitmap.createBitmap(300,100,android.graphics.Bitmap.Config.ARGB_8888)
                drawable.setBounds(0,0,300,100); drawable.draw(android.graphics.Canvas(bitmap))
                return bitmap
            }
            val normal = render(TerminalBorderDrawable(android.graphics.Color.BLACK,android.graphics.Color.GREEN,3,0f,false,0f,0f,true))
            val frame = TerminalBorderDrawable(android.graphics.Color.BLACK,android.graphics.Color.GREEN,3,0f,false,0f,0f,true,flipVertically=true)
            val flipped = render(frame)
            val copied = render(frame.constantState!!.newDrawable())
            try {
                var mirroredError = 0L
                var originalError = 0L
                for (y in 0 until 100) for (x in 0 until 300) {
                    val expected = normal.getPixel(x,99-y)
                    val actual = flipped.getPixel(x,y)
                    for (shift in listOf(0,8,16,24)) {
                        mirroredError += kotlin.math.abs(((expected ushr shift) and 255)-((actual ushr shift) and 255))
                        originalError += kotlin.math.abs(((normal.getPixel(x,y) ushr shift) and 255)-((actual ushr shift) and 255))
                    }
                    assertEquals(actual,copied.getPixel(x,y))
                }
                // Rasterizing a reflected vector can vary at antialiased edges; compare against both orientations.
                assertTrue("Frame did not flip: mirror error=$mirroredError original error=$originalError", originalError > 0 && mirroredError < originalError / 5)
            } finally { normal.recycle(); flipped.recycle(); copied.recycle() }
        }
    }
    @Test fun realConfigurationAndTimeDraftProbe() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wirelessReview") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch<LauncherActivity>(Intent(context,LauncherActivity::class.java)).use { scenario ->
            Thread.sleep(700); instrumentation.waitForIdleSync()
            val savedState = SettingsPreviewFixture.appearanceState()
            lateinit var preview: SettingsAppearancePreview
            lateinit var savedText: String
            lateinit var format: String
            scenario.onActivity { activity ->
                format = LauncherSettings.get(Behavior.status_time_format).orEmpty()
                savedText = LauncherActivity.captureHomePreview()?.statuses?.texts?.getOrNull(2)?.toString().orEmpty()
                assertTrue("Home clock must be available for the preview probe",savedText.isNotEmpty())
                val source = File(Tuils.getFolder(),"notifications.xml")
                val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source)
                val root = document.documentElement
                XMLPrefsManager.migrateRootValues(document,root,XMLPrefsManager.XMLPrefsRoot.NOTIFICATIONS)
                val filters = root.getElementsByTagName("filter").length
                assertTrue("Device filters should survive validation",filters >= 2)
                val appearance = HomeAppearance.saved()
                assertTrue(appearance.bool(Behavior.enable_cyberdeck_mode))
                assertFalse(appearance.bool(Ui.enable_dashed_border))
                for (ordinal in listOf(0,1,2,3,4,5,6,7)) {
                    val row = TextView(activity)
                    StatusPresentation.bindStyle(row,ordinal,appearance)
                    val border = row.background as? TerminalBorderDrawable
                    if (border != null) assertTrue("Status $ordinal Cyberdeck frame is missing",border.strokeWidthPx > 0)
                }
                preview = SettingsAppearancePreview(activity,
                    { key -> if(key == Behavior.status_time_format) "'ISSUE27_DRAFT' HH:mm" else null },
                    { key -> key == Behavior.status_time_format })
                activity.addContentView(preview,ViewGroup.LayoutParams(-1,700))
                preview.refresh()
            }
            Thread.sleep(300); instrumentation.waitForIdleSync()
            scenario.onActivity {
                val draftRendered = SettingsPreviewFixture.views(preview).filterIsInstance<TextView>()
                    .any { it.text.toString().contains("ISSUE27_DRAFT") }
                assertEquals(format,LauncherSettings.get(Behavior.status_time_format))
                assertFalse(LauncherActivity.captureHomePreview()?.statuses?.texts?.getOrNull(2)?.contains("ISSUE27_DRAFT") == true)
                File(context.getExternalFilesDir(null),"wireless-review.json").writeText(JSONObject()
                    .put("notificationFiltersValid",true).put("classicCyberdeckFramesValid",true)
                    .put("savedCustomTimeAvailable",savedText.isNotEmpty()).put("draftTimeFormatRendered",draftRendered).toString())
                (preview.parent as? ViewGroup)?.removeView(preview)
            }
            savedState.assertUnchanged()
        }
    }
}
