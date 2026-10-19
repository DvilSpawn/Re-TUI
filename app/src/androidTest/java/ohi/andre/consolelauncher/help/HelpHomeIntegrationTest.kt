package ohi.andre.consolelauncher.help

import android.content.Intent
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HelpHomeIntegrationTest {
    @get:Rule val readerPreferences = HelpReaderPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun homeFromArticleCategoryAndCommandReferenceReturnsToFirstDirectoryPage() {
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, OfflineHelpActivity::class.java)
            .putExtra("topic", "termux-workspace").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as OfflineHelpActivity
        fun output() = activity.findViewById<TextView>(R.id.offline_help_output)
        fun choose(number: Int) {
            instrumentation.runOnMainSync {
                activity.findViewById<EditText>(R.id.offline_help_input).setText(number.toString())
                activity.findViewById<View>(R.id.offline_help_enter).performClick()
            }
            instrumentation.waitForIdleSync()
        }
        fun assertHome() = instrumentation.runOnMainSync {
            assertTrue(output().text.startsWith("1 " + context.getString(R.string.offline_help_getting_started_title)))
            assertFalse(output().text.contains("8 " + context.getString(R.string.offline_help_previous_page)))
            assertEquals("", activity.findViewById<EditText>(R.id.offline_help_input).text.toString())
            assertFalse(activity.isFinishing)
        }
        try {
            instrumentation.waitForIdleSync()
            assertTrue(output().text.contains("10 " + context.getString(R.string.offline_help_root)))
            choose(10); assertHome()
            choose(7) // Automation category; tapping the link must work too.
            instrumentation.runOnMainSync {
                val text = output().text as Spanned
                val start = text.toString().indexOf("10 " + context.getString(R.string.offline_help_root))
                assertTrue(start >= 0)
                text.getSpans(start, start + 2, ClickableSpan::class.java).single().onClick(output())
            }
            instrumentation.waitForIdleSync(); assertHome()
            choose(9); choose(2) // Reference starts from the second root page.
            var appChoice: Int? = null
            repeat(20) {
                if (appChoice == null) {
                    appChoice = output().text.lineSequence().firstOrNull { it.matches(Regex("[1-7] apps")) }
                        ?.substringBefore(' ')?.toInt()
                    if (appChoice == null) choose(9)
                }
            }
            assertNotNull("apps command reference listed", appChoice)
            choose(appChoice!!)
            assertTrue(output().text.contains("10 " + context.getString(R.string.offline_help_root)))
            choose(10); assertHome()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
