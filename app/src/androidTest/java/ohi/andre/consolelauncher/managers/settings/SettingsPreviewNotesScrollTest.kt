package ohi.andre.consolelauncher.managers.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.presentation.LauncherHomeSnapshot
import ohi.andre.consolelauncher.presentation.StatusPresentation
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreviewNotesScrollTest {
    @Test fun cappedFormattedNotesKeepContentAndScrollAcrossRepeatedAppearanceDrafts() = with(SettingsPreviewFixture) {
        withHome(mapOf(Behavior.launcher_mode to "classic",Behavior.orientation to "1",Ui.show_notes to "true",
            Ui.notes_index to "1",Ui.notes_max_lines to "3",SurfaceBorderOption.unified_status_border to "false")) {
            val savedState=appearanceState()
            instrumentation.runOnMainSync {
                val home=checkNotNull(LauncherActivity.captureHomePreview())
                val source=home.create(context)
                val value=(1..24).joinToString("\n") { "NOTE %02d: frozen content".format(it) }
                val formatted=SpannableString(value).apply {
                    setSpan(ForegroundColorSpan(home.saved.color(Theme.notes_text_color)),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(AbsoluteSizeSpan(Tuils.convertSpToPixels(home.saved.number(Ui.notes_size,12f),context)),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(StyleSpan(Typeface.BOLD),0,7,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val content=StatusPresentation.capture(arrayOfNulls<CharSequence>(10).apply {this[6]=formatted})
                StatusPresentation.renderRows(source,content,home.saved,home.saved)
                layout(source,home.width,home.height)
                val notes=checkNotNull(StatusPresentation.notesView(source,home.saved))
                capped(notes,value)
                val initial=notesPixels(notes)
                notes.scrollTo(0,notes.layout.getLineTop(2))
                assertTrue("Long notes must scroll within their capped viewport",notes.scrollY>0)
                assertFalse("Scrolling must display a different portion of the frozen notes",initial.contentEquals(notesPixels(notes)))
                val offset=notes.scrollY
                val snapshot=checkNotNull(LauncherHomeSnapshot.capture(source,content))
                val preview=snapshot.createDraft(context,snapshot.saved)
                var appearance=snapshot.saved
                repeat(4) { pass ->
                    val current=checkNotNull(StatusPresentation.notesView(preview,appearance,snapshot.saved))
                    capped(current,value)
                    assertEquals("Rebuilding status rows must preserve the logical notes scroll",offset,current.scrollY)
                    assertTrue(current.scrollY>0)
                    assertEquals(1,(current.text as Spanned).getSpans(0,current.text.length,StyleSpan::class.java).size)
                    assertFalse(views(preview).any { it.hasOnClickListeners() || it.isLongClickable })
                    val edits=mapOf<XMLPrefsSave,String>(Theme.notes_text_color to if(pass%2==0) "#FF18C5A7" else "#FFD34E83",
                        Ui.notes_size to if(pass%2==0) "18" else "14",Ui.system_font to (pass%2==0).toString())
                    appearance=snapshot.saved.overlay({edits[it]},{it in edits})
                    snapshot.bindDraft(preview,appearance)
                    layout(preview,snapshot.width,snapshot.height)
                    val rebound=checkNotNull(StatusPresentation.notesView(preview,appearance,snapshot.saved))
                    capped(rebound,value)
                    assertEquals(offset,rebound.scrollY)
                    val text=rebound.text as Spanned
                    assertEquals(appearance.color(Theme.notes_text_color),text.getSpans(0,text.length,ForegroundColorSpan::class.java).single().foregroundColor)
                }
                assertEquals(value,notes.text.toString())
                assertEquals(offset,notes.scrollY)
            }
            savedState.assertUnchanged()
        }
    }

    private fun capped(view:TextView,value:String) {
        assertEquals("The complete formatted content must survive the viewport cap",value,view.text.toString())
        val textLayout=checkNotNull(view.layout)
        assertTrue("The cached content must exceed the visible notes viewport",textLayout.lineCount>3)
        val threeLines=textLayout.getLineBottom(2)+view.compoundPaddingTop+view.compoundPaddingBottom
        assertTrue("Notes must render within three lines, height=${view.height}, cap=$threeLines",view.height<=threeLines+1)
        assertTrue("Notes viewport must remain nonempty",view.height>0)
        assertTrue("Full notes content must remain available below the viewport",textLayout.height>view.height)
    }
    private fun layout(view:View,width:Int,height:Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        view.layout(0,0,width,height)
    }
    private fun notesPixels(view:View):IntArray {
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(bitmap))
            IntArray(bitmap.width*bitmap.height).also {bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)}
        } finally {bitmap.recycle()}
    }
}
