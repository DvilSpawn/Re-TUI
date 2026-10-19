package ohi.andre.consolelauncher.managers.settings

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.presentation.LauncherHomeSnapshot
import ohi.andre.consolelauncher.presentation.StatusPresentation
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresentationDraftRolesTest {
    private fun layout(view:View,width:Int,height:Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        view.layout(0,0,width,height)
    }

    @Test fun secondaryStatusPaletteDraftUpdatesFrozenBatteryAndLockedNotesSpans() = with(SettingsPreviewFixture) {
        withHome(mapOf(Ui.show_battery to "true",Ui.show_notes to "true",Ui.battery_index to "1",Ui.notes_index to "2",SurfaceBorderOption.unified_status_border to "false",
            Behavior.launcher_mode to "classic",Behavior.orientation to "1")) {
            val before=appearanceState()
            instrumentation.runOnMainSync {
                val source=checkNotNull(LauncherActivity.captureHomePreview())
                val root=source.create(context)
                layout(root,source.width,source.height)
                fun colored(value:String,color:Int)=SpannableString(value).apply {
                    setSpan(ForegroundColorSpan(color),0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val texts=arrayOfNulls<CharSequence>(10).apply {
                    this[3]=colored("BATTERY MEDIUM",source.saved.color(Theme.battery_text_medium))
                    this[6]=colored("LOCKED NOTE",source.saved.color(Theme.locked_notes_text_color))
                }
                val frozen=checkNotNull(LauncherHomeSnapshot.capture(root,StatusPresentation.capture(texts)))
                val edits=mapOf<XMLPrefsSave,String>(Theme.battery_text_medium to "#FF123456",Theme.locked_notes_text_color to "#FF654321")
                val appearance=frozen.saved.overlay({edits[it]},{it in edits})
                val preview=frozen.createDraft(context,appearance)
                layout(preview,frozen.width,frozen.height)
                fun spanColor(value:String):Int {
                    val view=views(preview).filterIsInstance<TextView>().single { it.text.toString()==value }
                    val text=view.text as Spanned
                    return text.getSpans(0,text.length,ForegroundColorSpan::class.java).single().foregroundColor
                }
                assertEquals(0xFF123456.toInt(),spanColor("BATTERY MEDIUM"))
                assertEquals(0xFF654321.toInt(),spanColor("LOCKED NOTE"))
                assertFalse(views(preview).any {it.hasOnClickListeners() || it.isLongClickable})
            }
            before.assertUnchanged()
        }
    }

    private fun headerDraft(savedMode:String,mode:String,check:(TextView)->Unit) = with(SettingsPreviewFixture) {
        withHome(mapOf(Behavior.output_header_mode to savedMode,Behavior.launcher_mode to "classic",Behavior.orientation to "1",
            SurfaceBorderOption.unified_status_border to "false")) {
            val before=appearanceState()
            instrumentation.runOnMainSync {
                val source=checkNotNull(LauncherActivity.captureHomePreview())
                val appearance=source.saved.overlay({if(it==Behavior.output_header_mode) mode else null},{it==Behavior.output_header_mode})
                val preview=source.createDraft(context,appearance)
                layout(preview,source.width,source.height)
                check(preview.findViewById(R.id.terminal_tray_toggle))
                assertFalse(views(preview).any {it.hasOnClickListeners() || it.isLongClickable})
            }
            before.assertUnchanged()
        }
    }

    @Test fun headerModeOnlyDraftUsesArrowChromeDimensions() = headerDraft("normal","arrows") { toggle ->
        assertEquals(Tuils.dpToPx(toggle.context,48),toggle.minWidth)
        assertEquals(Tuils.dpToPx(toggle.context,9),toggle.paddingLeft)
        assertEquals(Tuils.dpToPx(toggle.context,9),toggle.paddingRight)
        assertEquals(View.VISIBLE,toggle.visibility)
        assertNotNull(toggle.foreground)
    }

    @Test fun headerNoneToNormalDraftAppliesPreviouslyHiddenChrome() = headerDraft("none","normal") { toggle ->
        assertEquals(View.VISIBLE,toggle.visibility)
        assertEquals(Tuils.dpToPx(toggle.context,130),toggle.minWidth)
        assertEquals(Tuils.dpToPx(toggle.context,12),toggle.paddingLeft)
        assertNotNull(toggle.background)
        assertEquals(Typeface.BOLD,toggle.typeface.style)
        assertTrue(toggle.text.isNotEmpty())
    }
}
