package ohi.andre.consolelauncher.managers.settings

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreviewStructureDeviceTest {
    private fun draft(vararg saved:Pair<XMLPrefsSave,String>,edits:Map<XMLPrefsSave,String>,check:(ViewGroup,ViewGroup)->Unit) = with(SettingsPreviewFixture) {
        withHome(saved.toMap()) {
            val files=appearanceState()
            instrumentation.runOnMainSync {
                val snapshot=checkNotNull(LauncherActivity.captureHomePreview())
                val values=snapshot.saved.overlay({edits[it]},{it in edits})
                val current=snapshot.create(context)
                val preview=snapshot.createDraft(context,values)
                preview.measure(View.MeasureSpec.makeMeasureSpec(snapshot.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(snapshot.height,View.MeasureSpec.EXACTLY))
                preview.layout(0,0,snapshot.width,snapshot.height)
                assertFalse("Preview creates command input",views(preview).any {it is EditText})
                assertFalse("Preview retains launcher actions",views(preview).any {it.hasOnClickListeners() || it.isLongClickable})
                assertEquals(snapshot.width,preview.width)
                assertEquals(snapshot.height,preview.height)
                check(current,preview)
            }
            files.assertUnchanged()
        }
    }

    @Test fun classicToSearchUsesRealSearchLayoutAndInset() = draft(
        Behavior.launcher_mode to "classic",SurfaceBorderOption.unified_status_border to "false",
        edits=mapOf(Behavior.launcher_mode to "search")
    ) { current,preview ->
        assertEquals(View.GONE,preview.findViewById<View>(R.id.main_container).visibility)
        assertEquals(View.GONE,preview.findViewById<View>(R.id.terminal_container).visibility)
        assertEquals(View.VISIBLE,preview.findViewById<View>(R.id.search_mode_results_scroll).visibility)
        assertEquals(View.VISIBLE,preview.findViewById<View>(R.id.search_mode_clock_tab).visibility)
        val original=current.findViewById<View>(R.id.terminal_tray_container).layoutParams as ViewGroup.MarginLayoutParams
        val tray=preview.findViewById<View>(R.id.terminal_tray_container)
        val margins=tray.layoutParams as ViewGroup.MarginLayoutParams
        val inset=ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.searchEdgeInset(preview.context,true)
        assertSame(preview,tray.parent)
        assertEquals(original.leftMargin+inset,margins.leftMargin)
        assertEquals(original.rightMargin+inset,margins.rightMargin)
        assertTrue(tray.width>0 && tray.height>0)
    }

    @Test fun searchToClassicRestoresCurrentContentAndRemovesSearchInset() = draft(
        Behavior.launcher_mode to "search",SurfaceBorderOption.unified_status_border to "false",
        Toolbar.show_toolbar to "true",Suggestions.show_suggestions to "true",
        edits=mapOf(Behavior.launcher_mode to "classic")
    ) { current,preview ->
        assertEquals(View.VISIBLE,preview.findViewById<View>(R.id.main_container).visibility)
        assertEquals(View.VISIBLE,preview.findViewById<View>(R.id.terminal_container).visibility)
        assertEquals(View.VISIBLE,preview.findViewById<View>(R.id.tools_view).visibility)
        assertEquals(View.GONE,preview.findViewById<View>(R.id.search_mode_results_scroll).visibility)
        val original=current.findViewById<View>(R.id.terminal_tray_container).layoutParams as ViewGroup.MarginLayoutParams
        val margins=preview.findViewById<View>(R.id.terminal_tray_container).layoutParams as ViewGroup.MarginLayoutParams
        val inset=ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.searchEdgeInset(preview.context,true)
        assertEquals(original.leftMargin-inset,margins.leftMargin)
        assertEquals(original.rightMargin-inset,margins.rightMargin)
        assertEquals(current.findViewById<TextView>(R.id.terminal_view).text.toString(),preview.findViewById<TextView>(R.id.terminal_view).text.toString())
    }

    @Test fun normalToUnifiedMovesExistingHeaderAndModuleIntoActualHosts() = draft(
        Behavior.launcher_mode to "classic",SurfaceBorderOption.unified_status_border to "false",
        edits=mapOf(SurfaceBorderOption.unified_status_border to "true")
    ) { _,preview ->
        assertSame(preview.findViewById<View>(R.id.unified_status_host),preview.findViewById<View>(R.id.header_container).parent)
        assertSame(preview.findViewById<View>(R.id.unified_dock_host),preview.findViewById<View>(R.id.module_dock_scroll).parent)
        assertSame(preview.findViewById<View>(R.id.unified_module_host),preview.findViewById<View>(R.id.home_modules_container).parent)
        assertEquals(View.VISIBLE,preview.findViewById<View>(R.id.unified_bottom_console).visibility)
        assertEquals(View.VISIBLE,preview.findViewById<View>(R.id.unified_status_container).visibility)
    }

    @Test fun unifiedToNormalRestoresActualClassicParenting() = draft(
        Behavior.launcher_mode to "classic",SurfaceBorderOption.unified_status_border to "true",
        edits=mapOf(SurfaceBorderOption.unified_status_border to "false")
    ) { _,preview ->
        assertSame(preview.findViewById<View>(R.id.main_container),preview.findViewById<View>(R.id.header_container).parent)
        assertEquals(View.GONE,preview.findViewById<View>(R.id.unified_bottom_console).visibility)
        assertEquals(View.GONE,preview.findViewById<View>(R.id.unified_status_container).visibility)
        assertNotSame(preview.findViewById<View>(R.id.unified_module_host),preview.findViewById<View>(R.id.home_modules_container).parent)
    }
}
