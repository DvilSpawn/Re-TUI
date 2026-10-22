package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SettingsReloadIntegrationTest {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=i.targetContext
    private fun idle() { i.waitForIdleSync(); android.os.SystemClock.sleep(150);i.waitForIdleSync() }
    private fun fixture(run:()->Unit) {
        check(c.packageName.endsWith(".palettedebug"))
        Tuils.init(c);assertTrue(XMLPrefsManager.loadCommons(c))
        val original=Tuils.getFolder()
        val temporary=File(c.filesDir,"reload-test").apply {deleteRecursively();mkdirs()}
        original.listFiles().orEmpty().filter {it.extension=="xml"}.forEach {it.copyTo(File(temporary,it.name))}
        val folder=Tuils::class.java.getDeclaredField("folder").apply {isAccessible=true}
        folder.set(null,temporary)
        try {XMLPrefsManager.dispose();assertTrue(XMLPrefsManager.loadCommons(c));run()} finally {
            temporary.walkTopDown().forEach {it.setWritable(true,false)}
            folder.set(null,original);XMLPrefsManager.dispose();XMLPrefsManager.loadCommons(c);temporary.deleteRecursively()
        }
    }
    @Test fun malformedLaterFileLeavesEveryCacheAndEarlierFileUnchanged() = fixture {
        val theme=File(Tuils.getFolder(),"theme.xml")
        val before=theme.readBytes()
        val old=LauncherSettings.get(Theme.input_text_color)
        val ui=File(Tuils.getFolder(),"ui.xml")
        ui.writeText("<UI><broken")
        XMLPrefsManager.dispose()
        assertFalse(XMLPrefsManager.loadCommons(c))
        assertArrayEquals(before,theme.readBytes())
        assertEquals(old,LauncherSettings.get(Theme.input_text_color))
        assertEquals("<UI><broken",ui.readText())
        assertTrue(XMLPrefsManager.lastLoadError.orEmpty().contains("ui.xml"))
    }
    @Test fun batchFailurePreservesFilesSnapshotAndEditorValues() = fixture {
        val file=File(Tuils.getFolder(),"ui.xml")
        val before=file.readBytes()
        val old=LauncherSettings.currentSnapshot()
        assertTrue(Tuils.getFolder().setWritable(false,false))
        try {
            assertThrows(Exception::class.java) {
                LauncherSettings.applyChanges(c,mapOf(Theme.input_text_color to "#FF126789", Ui.input_output_size to "27"))
            }
            assertArrayEquals(before,file.readBytes())
            assertEquals(old,LauncherSettings.currentSnapshot())
        } finally {Tuils.getFolder().setWritable(true,false)}
    }
    @Test fun invalidRawEditorSaveCannotOverwriteTheOriginalFile() = fixture {
        val file=File(Tuils.getFolder(),"ui.xml");val before=file.readBytes()
        assertThrows(Exception::class.java) {XMLPrefsManager.saveRawSettings(c,file,"<UI><broken")}
        assertArrayEquals(before,file.readBytes())
    }
    @Test fun existingInvalidValueAndUnknownXmlSurviveAnUnrelatedEditAndReload() = fixture {
        val file=File(Tuils.getFolder(),"ui.xml")
        file.writeText(file.readText().replace(Regex("<input_output_size[^>]*/>"),"<input_output_size value=\"-4\"/>")
            .replace("</UI>","<future_option value=\"keep exact\"/></UI>"))
        XMLPrefsManager.dispose();assertTrue(XMLPrefsManager.loadCommons(c))
        assertEquals("-4",LauncherSettings.get(Ui.input_output_size))
        LauncherSettings.set(c,Theme.input_text_color,"#FF123456")
        XMLPrefsManager.dispose();assertTrue(XMLPrefsManager.loadCommons(c))
        assertEquals("-4",LauncherSettings.get(Ui.input_output_size))
        assertTrue(file.readText().contains("keep exact"))
        assertTrue(XMLPrefsManager.validationWarnings.contains("ui.xml:input_output_size"))
    }
    @Test fun invalidPresetFramesCannotPartiallyApplyItsPalette() = fixture {
        val name="reload-transaction-test"
        ohi.andre.consolelauncher.managers.PresetManager.save(c,name)
        LauncherSettings.set(c,Theme.input_text_color,"#FF345678")
        val before=LauncherSettings.currentSnapshot()
        val theme=File(Tuils.getFolder(),"theme.xml").readBytes()
        val preset=ohi.andre.consolelauncher.managers.PresetManager.getSavedPresetFolder(name)
        val frames=File(preset,"frames").apply {mkdirs()}
        File(frames,"unexpected-file.txt").writeText("invalid frame")
        assertThrows(Exception::class.java) {ohi.andre.consolelauncher.managers.PresetManager.apply(name)}
        assertEquals(before,LauncherSettings.currentSnapshot())
        assertArrayEquals(theme,File(Tuils.getFolder(),"theme.xml").readBytes())
    }

    @Test fun livePaletteAndTypographyKeepManagerInputSelectionAndHomeIdentity() = fixture {
        val startup=c.getSharedPreferences("retui_startup",0)
        startup.edit().putBoolean("initialized",true).putBoolean("complete",true).putBoolean("active",false).commit()
        ohi.andre.consolelauncher.managers.ChangelogManager.markSeen(c)
        LauncherSettings.applyChanges(c,mapOf(Behavior.launcher_mode to "classic",Behavior.auto_show_keyboard to "false"))
        idle()
        ActivityScenario.launch<LauncherActivity>(Intent(c,LauncherActivity::class.java)).use {scenario ->
            idle()
            var manager: Any?=null;var root: View?=null;var input: EditText?=null
            var count=0L
            scenario.onActivity {a ->
                manager=a.uiManager;root=a.findViewById(R.id.mainview);input=a.findViewById(R.id.input_view)
                input!!.setText("preserve my draft");input!!.setSelection(2,7)
                count=SettingsRefreshCoordinator.refreshCount
                LauncherSettings.applyChanges(a,mapOf(Theme.input_text_color to "#FF12AB78",Theme.time_text_color to "#FF12AB78",Ui.input_output_size to "21"))
            }
            idle()
            scenario.onActivity {a ->
                assertSame(manager,a.uiManager);assertSame(root,a.findViewById(R.id.mainview))
                assertSame(input,a.findViewById(R.id.input_view))
                assertEquals("preserve my draft",input!!.text.toString())
                assertEquals(2,input!!.selectionStart);assertEquals(7,input!!.selectionEnd)
                assertEquals(0xFF12AB78.toInt(),input!!.currentTextColor)
                assertEquals(1,SettingsRefreshCoordinator.refreshCount-count)
            }
            scenario.onActivity { a ->
                count = SettingsRefreshCoordinator.refreshCount
                val file = File(Tuils.getFolder(), "theme.xml")
                XMLPrefsManager.saveRawSettings(a, file, file.readText().replace("#FF12AB78", "#FF123ABC"))
            }
            idle()
            scenario.onActivity { a ->
                assertSame(manager, a.uiManager)
                assertSame(input, a.findViewById(R.id.input_view))
                assertEquals("preserve my draft", input!!.text.toString())
                assertEquals(2, input!!.selectionStart); assertEquals(7, input!!.selectionEnd)
                assertEquals(0xFF123ABC.toInt(), input!!.currentTextColor)
                assertEquals(1, SettingsRefreshCoordinator.refreshCount - count)
            }
        }
    }
}
