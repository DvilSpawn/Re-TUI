package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.text.SpannableStringBuilder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class TimeFormatReloadIntegrationTest {
    @Test fun refreshUsesSavedStatusAndOutputFormatsWithoutRestarting() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("timeReloadReview") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch<LauncherActivity>(Intent(context, LauncherActivity::class.java)).use { scenario ->
            instrumentation.waitForIdleSync()
            val status = LauncherSettings.get(Behavior.status_time_format)
            val output = LauncherSettings.get(Behavior.output_time_format)
            val separator = LauncherSettings.get(Behavior.time_format_separator)
            try {
                val previous = TimeManager.instance
                scenario.onActivity { activity ->
                    LauncherSettings.set(activity, Behavior.time_format_separator, "@")
                    LauncherSettings.set(activity, Behavior.status_time_format, "[size=20]'STATUS_RELOADED' {clock_words}[/size]\\nd MMM yyyy")
                    LauncherSettings.set(activity, Behavior.output_time_format, "'OUTPUT_RELOADED'%nHH:mm")
                    activity.refreshUiInPlace()
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertNotSame("UI refresh must reload cached time formats", previous, TimeManager.instance)
                    val clock = TimeManager.instance!!.getCharSequence(context, 20, "%t").toString()
                    assertTrue(clock, clock.startsWith("STATUS_RELOADED "))
                    assertTrue("Multiline status formatting must survive reload", clock.contains('\n'))
                    val rendered = LauncherActivity.captureHomePreview()?.statuses?.texts?.getOrNull(2).toString()
                    assertEquals("Actual Home clock must use the saved status format", clock, rendered)
                    val timestamp = TimeManager.instance!!.replace(SpannableStringBuilder("%t")).toString()
                    assertTrue(timestamp, timestamp.startsWith("OUTPUT_RELOADED\n"))
                    assertFalse(timestamp.contains("STATUS_RELOADED"))
                }
            } finally {
                scenario.onActivity { activity ->
                    LauncherSettings.set(activity, Behavior.status_time_format, status)
                    LauncherSettings.set(activity, Behavior.output_time_format, output)
                    LauncherSettings.set(activity, Behavior.time_format_separator, separator)
                    activity.refreshUiInPlace()
                }
                instrumentation.waitForIdleSync()
            }
        }
    }
}
