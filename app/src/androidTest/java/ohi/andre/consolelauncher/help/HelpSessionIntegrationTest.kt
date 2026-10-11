package ohi.andre.consolelauncher.help

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Test

/** Reading state and window geometry are restored, including on the physical phone. */
class HelpSessionIntegrationTest {
    @get:org.junit.Rule val readerPreferences = HelpReaderPreferencesRule()
    @get:org.junit.Rule val windowPreferences = WindowPreferencesRule(fresh = false)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun settled(activity: OfflineHelpActivity) {
        val end = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < end) {
            var ready = false
            instrumentation.runOnMainSync {
                ready = activity.findViewById<ScrollView>(R.id.offline_help_scroll).height > 0 &&
                    activity.javaClass.getDeclaredField("pendingScroll").apply { isAccessible = true }.get(activity) == null
            }
            if (ready) return
            SystemClock.sleep(25)
        }
        fail("Help reading position did not settle")
    }
    private fun open(topic: String? = null): OfflineHelpActivity {
        val intent = Intent(context, OfflineHelpActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (topic != null) intent.putExtra("topic", topic)
        var activity = instrumentation.startActivitySync(intent) as OfflineHelpActivity
        if (activity.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            val monitor = instrumentation.addMonitor(OfflineHelpActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as OfflineHelpActivity
            } finally { instrumentation.removeMonitor(monitor) }
        }
        settled(activity); return activity
    }
    @Test fun backClosesImmediatelyAndBareHelpRestoresTopicScrollDraftAndSelection() {
        var activity: OfflineHelpActivity? = null
        try {
            var screen = open("calculator-help"); activity = screen
            var readingOffset = 0
            instrumentation.runOnMainSync {
                val scroll = screen.findViewById<ScrollView>(R.id.offline_help_scroll)
                scroll.scrollTo(0, 220); readingOffset = scroll.scrollY
                assertTrue("Use a real scrolled article", readingOffset > 0)
                val input = screen.findViewById<EditText>(R.id.offline_help_input)
                input.setText("123"); input.setSelection(1, 2)
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertTrue("One Back closes, not Topics", screen.isFinishing) }
            screen = open(); activity = screen
            instrumentation.runOnMainSync {
                assertTrue(screen.findViewById<TextView>(R.id.offline_help_output).text.contains("calc>"))
                assertEquals(readingOffset, screen.findViewById<ScrollView>(R.id.offline_help_scroll).scrollY)
                val input = screen.findViewById<EditText>(R.id.offline_help_input)
                assertEquals("123", input.text.toString()); assertEquals(1, input.selectionStart); assertEquals(2, input.selectionEnd)
                input.setText("0"); screen.findViewById<View>(R.id.offline_help_enter).performClick()
                assertTrue(screen.isFinishing)
            }
            instrumentation.waitForIdleSync()
            screen = open("calculator-help"); activity = screen
            instrumentation.runOnMainSync {
                assertEquals(readingOffset, screen.findViewById<ScrollView>(R.id.offline_help_scroll).scrollY)
                assertEquals("", screen.findViewById<EditText>(R.id.offline_help_input).text.toString())
                screen.finish()
            }
            instrumentation.waitForIdleSync()
            screen = open("terminal-basics"); activity = screen
            instrumentation.runOnMainSync {
                assertTrue(screen.findViewById<TextView>(R.id.offline_help_output).text.contains(screen.getString(OfflineHelpCatalog.find("terminal-basics")!!.title)))
                assertEquals(0, screen.findViewById<ScrollView>(R.id.offline_help_scroll).scrollY)
            }
        } finally {
            activity?.let { screen -> instrumentation.runOnMainSync { screen.finish() }; instrumentation.waitForIdleSync() }
        }
    }
    @Test fun obsoleteSavedTopicFallsBackSafelyToItsCategory() {
        context.getSharedPreferences("offline_help_reader", android.content.Context.MODE_PRIVATE).edit()
            .putString("category", "tools").putString("article", "removed-topic").putInt("page", 999).commit()
        val screen = open()
        try {
            instrumentation.runOnMainSync {
                assertFalse(screen.findViewById<TextView>(R.id.offline_help_output).text.contains("removed-topic"))
                val input = screen.findViewById<EditText>(R.id.offline_help_input)
                input.setText("10"); screen.findViewById<View>(R.id.offline_help_enter).performClick()
                assertTrue(screen.findViewById<TextView>(R.id.offline_help_output).text.contains(screen.getString(R.string.offline_help_getting_started_title)))
            }
        } finally { instrumentation.runOnMainSync { screen.finish() }; instrumentation.waitForIdleSync() }
    }
}
