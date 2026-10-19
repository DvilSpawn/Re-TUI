package ohi.andre.consolelauncher.managers.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsPreviewModuleParityTest {
    @Test fun visibleMusicModuleIsFrozenWithoutActionsOrSourceChanges() = with(SettingsPreviewFixture) {
        val prefs=context.getSharedPreferences("retui_modules",0)
        val original=prefs.all.mapValues { (_,value)->if(value is Set<*>) value.toSet() else value }
        try {
            ModuleManager.addToDock(context,arrayListOf<String?>(ModuleManager.MUSIC))
            withHome(mapOf(Behavior.launcher_mode to "classic",Behavior.search_only_mode to "false",
                Behavior.show_module_dock to "true",Ui.system_wallpaper to "false",
                SurfaceBorderOption.unified_status_border to "false")) {
                val home=checkNotNull(LauncherActivity.instance)
                instrumentation.runOnMainSync {
                    val label=ModuleManager.displayTitle(home,ModuleManager.MUSIC).orEmpty()
                    val button=views(home.findViewById<View>(R.id.mainview)).filterIsInstance<TextView>().first {
                        it.getTag(R.id.home_dock_binding) is Boolean && it.text.toString().removePrefix("✓ ").trim().equals(label,true)
                    }
                    assertTrue("Music dock must respond to a real Home tap",button.performClick())
                }
                settle();Thread.sleep(400);instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync { steadyPresentation(home.findViewById(R.id.mainview)) }
                instrumentation.waitForIdleSync()
                val saved=appearanceState()
                val modulePrefs=prefs.all.mapValues { (_,value)->if(value is Set<*>) value.toSet() else value }
                instrumentation.runOnMainSync {
                    val actual=home.findViewById<ViewGroup>(R.id.mainview)
                    val modules=actual.findViewById<ViewGroup>(R.id.home_modules_container)
                    assertTrue("Source module container must have real content",modules.childCount>0)
                    val music=checkNotNull(modules.findViewById<View>(R.id.music_module))
                    assertTrue("Music must actually be visible on Home",music.isShown && music.getGlobalVisibleRect(Rect()))
                    val play=music.findViewById<TextView>(R.id.music_play_pause)
                    assertTrue(play.isShown && play.text.isNotEmpty() && play.hasOnClickListeners())
                    assertTrue(music.findViewById<TextView>(R.id.music_song_title).text.isNotEmpty())
                    assertEquals(ModuleManager.MUSIC,ModuleManager.getActiveModule(context))
                    val childCount=modules.childCount
                    val title=music.findViewById<TextView>(R.id.music_song_title).text.toString()
                    val left=render(actual)
                    try {
                        val snapshot=checkNotNull(LauncherActivity.captureHomePreview())
                        val expected=pixels(left)
                        val folder=File(context.getExternalFilesDir(null),"issue23").apply { mkdirs() }
                        File(folder,"module-home.png").outputStream().use { left.compress(Bitmap.CompressFormat.PNG,100,it) }
                        repeat(3) { pass ->
                            val preview=snapshot.createDraft(home,snapshot.saved)
                            steadyPresentation(preview)
                            preview.measure(View.MeasureSpec.makeMeasureSpec(snapshot.width,View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(snapshot.height,View.MeasureSpec.EXACTLY))
                            preview.layout(0,0,snapshot.width,snapshot.height)
                            assertEquals(actual.width,preview.width);assertEquals(actual.height,preview.height)
                            val copiedMusic=checkNotNull(preview.findViewById<View>(R.id.music_module))
                            assertEquals(title,copiedMusic.findViewById<TextView>(R.id.music_song_title).text.toString())
                            assertFalse("Frozen module must not retain controls",views(preview).any {
                                it.hasOnClickListeners() || it.isLongClickable || it is EditText
                            })
                            val right=render(preview)
                            try {
                                if(pass==0) File(folder,"module-preview.png").outputStream().use { right.compress(Bitmap.CompressFormat.PNG,100,it) }
                                val actualPixels=pixels(right)
                                val differences=expected.indices.count { expected[it]!=actualPixels[it] }
                                assertEquals("Cached music scene differs in $differences pixels on clone $pass",0,differences)
                            } finally { right.recycle() }
                            assertEquals(childCount,modules.childCount)
                            assertSame("Preview must not replace or execute the source module",music,modules.findViewById<View>(R.id.music_module))
                            assertEquals(ModuleManager.MUSIC,ModuleManager.getActiveModule(context))
                            assertEquals(modulePrefs,prefs.all)
                        }
                    } finally { left.recycle() }
                }
                saved.assertUnchanged()
                assertEquals(modulePrefs,prefs.all)
            }
        } finally {
            instrumentation.runOnMainSync {
                val editor=prefs.edit().clear()
                original.forEach { (key,value)->when(value) {
                    is String -> editor.putString(key,value)
                    is Int -> editor.putInt(key,value)
                    is Long -> editor.putLong(key,value)
                    is Float -> editor.putFloat(key,value)
                    is Boolean -> editor.putBoolean(key,value)
                    is Set<*> -> editor.putStringSet(key,value.filterIsInstance<String>().toSet())
                } }
                assertTrue(editor.commit())
            }
        }
    }

    private fun render(view: View)=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    private fun pixels(bitmap: Bitmap)=IntArray(bitmap.width*bitmap.height).also {
        bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    }
}
