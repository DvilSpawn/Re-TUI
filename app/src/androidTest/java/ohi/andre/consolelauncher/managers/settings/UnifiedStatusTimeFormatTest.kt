package ohi.andre.consolelauncher.managers.settings

import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Issue #27: status_time_format had no effect on the unified status card, on Home or in the preview. */
@RunWith(AndroidJUnit4::class)
class UnifiedStatusTimeFormatTest {
    private val unified = mapOf<XMLPrefsSave, String>(SurfaceBorderOption.unified_status_border to "true", Ui.show_time to "true")

    @Test fun customFormatReplacesSystemClockOnHomeAndInPreview() = with(SettingsPreviewFixture) {
        withHome(unified + mapOf(Behavior.status_time_format to "'CLOCK' HH:mm")) {
            Thread.sleep(1500); settle()
            instrumentation.runOnMainSync {
                val home = LauncherActivity.instance!!
                val time = home.findViewById<TextView>(R.id.unified_status_time).text.toString()
                assertTrue("Home unified time ignored status_time_format: $time", time.startsWith("CLOCK "))
                assertEquals(View.GONE, home.findViewById<View>(R.id.unified_status_date).visibility)
            }
            val activity = open(XMLPrefsManager.XMLPrefsRoot.BEHAVIOR)
            try {
                search(activity, "status_time_format"); edit(activity, "'DRAFT' HH:mm")
                instrumentation.runOnMainSync {
                    val scene = checkNotNull(preview(activity).scene)
                    val time = scene.findViewById<TextView>(R.id.unified_status_time).text.toString()
                    assertTrue("Preview unified time ignored the draft: $time", time.startsWith("DRAFT "))
                    assertEquals("'CLOCK' HH:mm", LauncherSettings.get(Behavior.status_time_format))
                }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    @Test fun defaultFormatKeepsCompactSystemDateAndTime() = with(SettingsPreviewFixture) {
        withHome(unified + mapOf(Behavior.status_time_format to Behavior.status_time_format.defaultValue()!!)) {
            Thread.sleep(1500); settle()
            instrumentation.runOnMainSync {
                val home = LauncherActivity.instance!!
                assertEquals(View.VISIBLE, home.findViewById<View>(R.id.unified_status_date).visibility)
                assertFalse(home.findViewById<TextView>(R.id.unified_status_time).text.isNullOrEmpty())
            }
        }
    }
}
