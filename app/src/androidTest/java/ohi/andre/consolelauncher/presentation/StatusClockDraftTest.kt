package ohi.andre.consolelauncher.presentation

import android.graphics.Color
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test

class StatusClockDraftTest {
    @org.junit.Test fun unchangedClockUsesCapturedContentRatherThanSamplingAnotherSecond() {
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val appearance=HomeAppearance.saved()
            val texts=arrayOfNulls<CharSequence>(10).apply {this[2]="FROZEN_CLOCK_SECOND"}
            val view=android.widget.TextView(instrumentation.targetContext)
            StatusPresentation.bindContent(view,listOf(2),StatusPresentation.capture(texts),appearance,appearance)
            org.junit.Assert.assertEquals("FROZEN_CLOCK_SECOND",view.text.toString())
        }
    }

    @Test fun draftClockUsesFormatterWithoutChangingSavedSettingsOrLiveClock() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val before = listOf(Behavior.status_time_format,Behavior.output_time_format,Behavior.time_format_separator)
            .associateWith { LauncherSettings.get(it) }
        val live = TimeManager.instance
        instrumentation.runOnMainSync {
            val saved = HomeAppearance.saved()
            val edits = mutableMapOf<XMLPrefsSave,String>(
                Behavior.status_time_format to "[size=22]'DRAFT' {clock_words}[/size]%nd MMM yyyy",
                Behavior.time_format_separator to "@",
                Ui.time_size to "14",
                Theme.time_text_color to "#FF12AB34")
            val row = TextView(context)
            val source = StatusPresentation.Content(List(10) { if(it==2) "STALE CLOCK" else null },null,null)
            fun render() = StatusPresentation.bindContent(row,listOf(2),source,saved.overlay({ edits[it] }, { it in edits }),saved)
            render()
            assertTrue(row.text.toString(),row.text.startsWith("DRAFT "))
            assertTrue(row.text.contains('\n'))
            assertFalse(row.text.contains("STALE CLOCK"))
            val text = row.text as Spanned
            val sizes = text.getSpans(0,text.length,AbsoluteSizeSpan::class.java).map { it.size }
            assertTrue(sizes.contains(Tuils.convertSpToPixels(22f,context)))
            assertTrue(sizes.contains(Tuils.convertSpToPixels(14f,context)))
            assertTrue(text.getSpans(0,text.length,ForegroundColorSpan::class.java).any { it.foregroundColor==Color.parseColor("#FF12AB34") })
            edits[Behavior.status_time_format] = "#123456'COLORED'"
            render()
            val colored = row.text as Spanned
            assertTrue(colored.getSpans(0,colored.length,ForegroundColorSpan::class.java).any { it.foregroundColor==Color.parseColor("#123456") })
            edits[Behavior.status_time_format] = "'FIRST'@'SECOND'"
            render()
            assertEquals("FIRST",row.text.toString())
            edits[Behavior.status_time_format] = "[unfinished"
            render() // Incomplete date patterns must stay local to the preview.
            edits[Behavior.time_format_separator] = "["
            render() // An incomplete separator must not crash the editor.
            assertSame("Draft rendering must not replace the live formatter",live,TimeManager.instance)
        }
        before.forEach { (key,value) -> assertEquals(value,LauncherSettings.get(key)) }
    }
}
