package ohi.andre.consolelauncher.commands.tuixt

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.settings.*
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CurrentLiveSettingsPreviewTest {
    private fun draft(activity:TuixtActivity,vararg values:Pair<XMLPrefsSave,String>)=with(SettingsPreviewFixture) {
        instrumentation.runOnMainSync { adapter(activity).restoreDraftState(Bundle().apply {
            values.forEach { (key,value)->putString("${key.parent()?.path()}:${key.label()}",value) }
        }) }
        settle()
    }
    private fun capture(name:String)=with(SettingsPreviewFixture) {
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null),"current-preview-$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    private val onlyBattery=mapOf<XMLPrefsSave,String>(Ui.show_battery to "true",Ui.battery_index to "1",Ui.show_ram to "false",Ui.show_device_name to "false",
        Ui.show_time to "false",Ui.show_notes to "false",Ui.show_storage_info to "false",Ui.show_network_info to "false",Ui.show_weather to "false",
        Ui.show_unlock_counter to "false",Ui.show_ascii to "false",SurfaceBorderOption.unified_status_border to "false")

    @Test fun savedBatteryBarMatchesActualLauncherAndDraftIsPrivate()=with(SettingsPreviewFixture) {
        withHome(onlyBattery+mapOf(Behavior.battery_progress_bar to "true",Behavior.battery_progress_bar_length to "10",Behavior.battery_progress_bar_symbol to "#",Ui.font_size_offset to "2")) {
            val activity=open(XMLPrefsManager.XMLPrefsRoot.UI)
            val before=appearanceState()
            try {
                val sticky=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
                val level=sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,0)*100/sticky.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,100)
                instrumentation.runOnMainSync {
                    val actual=LauncherActivity.captureHomePreview()!!.statuses!!.texts[3].toString()
                    val shown=preview(activity).findViewById<TextView>(R.id.tv0)
                    assertEquals("[${"#".repeat(10)}] $level%",actual)
                    assertEquals(actual,shown.text.toString())
                    val home=LauncherActivity.instance!!.findViewById<TextView>(R.id.tv0)
                    assertEquals(home.textSize,shown.textSize,.01f)
                    assertEquals(home.gravity,shown.gravity)
                }
                capture("saved-battery")
                draft(activity,Behavior.battery_progress_bar_symbol to "=",Behavior.battery_progress_bar_length to "12")
                instrumentation.runOnMainSync {
                    assertEquals("[${"=".repeat(12)}] $level%",preview(activity).findViewById<TextView>(R.id.tv0).text.toString())
                    assertEquals("#",LauncherSettings.get(Behavior.battery_progress_bar_symbol))
                    assertFalse(ThemeColorResolver.hasPreview())
                }
                before.assertUnchanged()
                capture("draft-battery")
                draft(activity,SurfaceBorderOption.unified_status_border to "true")
                instrumentation.runOnMainSync {
                    assertTrue(preview(activity).findViewById<TextView>(R.id.unified_status_battery).text.toString().startsWith("["))
                }
                before.assertUnchanged()
                capture("unified-battery")
            } finally {instrumentation.runOnMainSync {activity.finish()}}
        }
    }

    @Test fun terminalAndRichLuaComeFromCurrentLauncherWithoutActions()=with(SettingsPreviewFixture) {
        withHome {
            val ui=LauncherActivity.instance!!.uiManager!!
            val original=ui.snapshotTerminalState()
            try {
                instrumentation.runOnMainSync {
                    ui.setOutput("CURRENT-MASTER-LIVE-OUTPUT",TerminalManager.CATEGORY_OUTPUT)
                    ui.setInput("live draft input")
                    LauncherActivity.instance!!.findViewById<android.view.ViewGroup>(R.id.home_modules_container).removeAllViews()
                    val result=LuaWidgetEngine.RenderResult().apply {
                        title="Current live Lua"
                        layoutJson="""{"type":"column","children":[{"type":"text","text":"Actual module content"},{"type":"progress","label":"Focus","value":0.5},{"type":"button","label":"Run","command":"clear"}]}"""
                    }
                    UIManager::class.java.getDeclaredMethod("showLuaWidgetModule",String::class.java,String::class.java,LuaWidgetEngine.RenderResult::class.java)
                        .apply {isAccessible=true}.invoke(ui,"preview_fixture","preview_fixture",result)
                }
                settle()
                val activity=open(XMLPrefsManager.XMLPrefsRoot.THEME)
                val before=appearanceState()
                try {
                    instrumentation.runOnMainSync {preview(activity).pager.setCurrentItem(1,false)};settle()
                    instrumentation.runOnMainSync {
                        val card=preview(activity)
                        assertTrue(card.findViewById<TextView>(R.id.terminal_view).text.contains("CURRENT-MASTER-LIVE-OUTPUT"))
                        assertEquals("live draft input",card.findViewById<TextView>(R.id.input_view).text.toString())
                        assertFalse(views(card).any {it is android.widget.EditText || it.hasOnClickListeners()})
                        card.pager.setCurrentItem(2,false)
                    };settle()
                    draft(activity,Theme.module_panel_text_color to "#FFABCDEF")
                    instrumentation.runOnMainSync {
                        val texts=views(preview(activity)).filterIsInstance<TextView>()
                        assertEquals(0xFFABCDEF.toInt(),texts.first {it.text.toString()=="Actual module content"}.currentTextColor)
                        val run=texts.first {it.text.toString()=="Run"}
                        assertFalse(run.hasOnClickListeners());assertFalse(run.performClick())
                        assertTrue(ui.snapshotTerminalState()!!.output.contains("CURRENT-MASTER-LIVE-OUTPUT"))
                    }
                    before.assertUnchanged()
                    capture("lua")
                } finally {instrumentation.runOnMainSync {activity.finish()}}
            } finally {instrumentation.runOnMainSync {ui.restoreTerminalState(original)}}
        }
    }

    @Test fun ansiDraftAndSaveBoundaryWorkOnCurrentBase()=with(SettingsPreviewFixture) {
        withHome(mapOf(Theme.ansi_red to "#FFFF0000")) {
            val activity=open(XMLPrefsManager.XMLPrefsRoot.THEME)
            val before=appearanceState()
            try {
                draft(activity,Theme.ansi_red to "#FF0077CC")
                instrumentation.runOnMainSync {preview(activity).pager.setCurrentItem(5,false)};settle()
                instrumentation.runOnMainSync {
                    val sample=views(preview(activity)).filterIsInstance<TextView>().first {it.text.contains("SUCCESS")}.text as Spanned
                    val index=sample.toString().indexOf("ERROR")
                    assertTrue(sample.getSpans(index,index+5,ForegroundColorSpan::class.java).any {it.foregroundColor==0xFF0077CC.toInt()})
                    assertEquals("#FFFF0000",LauncherSettings.get(Theme.ansi_red))
                }
                before.assertUnchanged();capture("ansi")
                click(activity,R.string.editor_tuixtactivity_save_50815)
                assertEquals("#FF0077CC",LauncherSettings.get(Theme.ansi_red))
                assertFalse(adapter(activity).hasPendingChanges())
            } finally {instrumentation.runOnMainSync {activity.finish()}}
        }
    }
    @Test fun swipingAndRecreationRetainPageAndTransientDraft()=with(SettingsPreviewFixture) {
        withHome {
            var activity=open(XMLPrefsManager.XMLPrefsRoot.UI)
            val before=appearanceState()
            try {
                draft(activity,Ui.dashed_border_stroke_width to "2.5")
                val rect=android.graphics.Rect()
                instrumentation.runOnMainSync {preview(activity).pager.getGlobalVisibleRect(rect)}
                val down=android.os.SystemClock.uptimeMillis()
                fun event(action:Int,x:Float) {
                    android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,rect.exactCenterY(),0).also {
                        instrumentation.uiAutomation.injectInputEvent(it,true);it.recycle()
                    }
                }
                val from=rect.left+rect.width()*.85f
                val to=rect.left+rect.width()*.15f
                event(android.view.MotionEvent.ACTION_DOWN,from)
                for(step in 1..12) {Thread.sleep(20);event(android.view.MotionEvent.ACTION_MOVE,from+(to-from)*step/12)}
                event(android.view.MotionEvent.ACTION_UP,to)
                Thread.sleep(500);settle()
                instrumentation.runOnMainSync {assertEquals(1,preview(activity).pager.currentItem)}
                val monitor=android.app.Instrumentation.ActivityMonitor(TuixtActivity::class.java.name,null,false)
                instrumentation.addMonitor(monitor)
                try {
                    instrumentation.runOnMainSync {activity.recreate()}
                    activity=instrumentation.waitForMonitorWithTimeout(monitor,5000) as TuixtActivity
                    settle()
                } finally {instrumentation.removeMonitor(monitor)}
                instrumentation.runOnMainSync {
                    assertEquals(1,preview(activity).pager.currentItem)
                    assertEquals("2.5",adapter(activity).draftValue(Ui.dashed_border_stroke_width))
                }
                before.assertUnchanged()
            } finally {instrumentation.runOnMainSync {activity.finish()}}
        }
    }

    @Test fun everyEnabledStatusUsesActualLauncherContentAndDraftStyling()=with(SettingsPreviewFixture) {
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        // Recover known literal formats left by the earlier interrupted ordering test in this disposable package.
        if(context.packageName.endsWith(".fetchdebug")) {
            if(LauncherSettings.get(Behavior.ram_format)=="RAM") LauncherSettings.set(Behavior.ram_format,Behavior.ram_format.defaultValue())
            if(LauncherSettings.get(Behavior.status_time_format)=="'TIME'") LauncherSettings.set(Behavior.status_time_format,Behavior.status_time_format.defaultValue())
        }
        val descriptors=ohi.andre.consolelauncher.presentation.StatusPresentation.descriptors
        val fixture=descriptors.flatMapIndexed { index,d -> listOf(d.visible to "true",d.index to (index+1).toString()) }.toMap()+
            mapOf<XMLPrefsSave,String>(SurfaceBorderOption.unified_status_border to "false",Behavior.battery_progress_bar to "true",Ui.enable_dashed_border to "true")
        withHome(fixture) {
            Thread.sleep(800);settle()
            val activity=open(XMLPrefsManager.XMLPrefsRoot.THEME)
            val before=appearanceState()
            try {
                instrumentation.runOnMainSync {
                    val card=preview(activity)
                    val snapshot=SettingsAppearancePreview::class.java.getDeclaredField("live").apply {isAccessible=true}.get(card) as ohi.andre.consolelauncher.presentation.LauncherHomeSnapshot
                    val texts=checkNotNull(snapshot.statuses).texts
                    assertTrue("Fixture must exercise multiple real status sources",texts.count {!it.isNullOrEmpty()}>=6)
                    for(index in descriptors.indices) {
                        val row=card.findViewById<TextView>(intArrayOf(R.id.tv0,R.id.tv1,R.id.tv2,R.id.tv3,R.id.tv4,R.id.tv5,R.id.tv6,R.id.tv7,R.id.tv8,R.id.tv9)[index])
                        if(!texts[index].isNullOrEmpty()) {
                            val displayed=if(row is ohi.andre.consolelauncher.tuils.AsciiArtTextView) {
                                @Suppress("UNCHECKED_CAST")
                                val lines=ohi.andre.consolelauncher.tuils.AsciiArtTextView::class.java.getDeclaredField("lines").apply {isAccessible=true}.get(row) as List<String>
                                lines.joinToString("\n")
                            } else row.text.toString()
                            assertEquals("Status $index differs from Home",texts[index].toString(),displayed)
                        }
                        assertFalse(row.hasOnClickListeners());assertFalse(row.isFocusable)
                    }
                }
                SettingsPreviewFixture.capture("all-status-live")
                draft(activity,Theme.ram_text_color to "#FF32AB67",Ui.ram_status_alignment to "1",Ui.dashed_border_stroke_width to "2.5")
                instrumentation.runOnMainSync {
                    val ram=preview(activity).findViewById<TextView>(R.id.tv0)
                    assertEquals(0xFF32AB67.toInt(),ram.currentTextColor)
                    assertEquals(android.view.Gravity.END,ram.gravity and android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
                    val text=ram.text as Spanned
                    assertTrue(text.getSpans(0,text.length,ForegroundColorSpan::class.java).any {it.foregroundColor==0xFF32AB67.toInt()})
                }
                before.assertUnchanged()
                SettingsPreviewFixture.capture("all-status-draft")
                instrumentation.runOnMainSync {
                    val card=preview(activity)
                    views(card).filterIsInstance<android.widget.ScrollView>().first().scrollTo(0,card.findViewById<View>(R.id.tv4).top)
                }
                settle()
                SettingsPreviewFixture.capture("all-status-lower")
            } finally {instrumentation.runOnMainSync {activity.finish()}}
        }
    }

}
