package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Issue23PreviewIntegrationTest {
    private val i get()=InstrumentationRegistry.getInstrumentation()
    private val c get()=i.targetContext
    private fun prepare() {
        ohi.andre.consolelauncher.managers.modules.ModuleManager.setActiveModule(c,ohi.andre.consolelauncher.managers.modules.ModuleManager.MUSIC)
        Tuils.init(c);XMLPrefsManager.loadCommons(c);LauncherSettings.refreshFromLoadedPrefs()
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(c)
        LauncherSettings.set(ohi.andre.consolelauncher.managers.xml.options.Behavior.auto_show_keyboard,"false")
        LauncherSettings.set(ohi.andre.consolelauncher.managers.xml.options.Notifications.terminal_notifications,"false")
        LauncherSettings.set(ohi.andre.consolelauncher.managers.xml.options.Notifications.show_notifications,"false")
        c.getSharedPreferences("retui_startup",0).edit().putBoolean("initialized",true).putBoolean("complete",true).putBoolean("active",false).commit()
        c.getSharedPreferences("changelogPrefs",0).edit().putInt("last_seen_version_code",BuildConfig.VERSION_CODE).commit()
    }
    private fun bitmap(v: View): Bitmap = Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
    private fun layout(v: View,w: Int,h: Int) { v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h) }
    @Test fun actualHomeAndUneditedPreviewHaveTheSamePixels() {
        prepare()
        ActivityScenario.launch<LauncherActivity>(Intent(c,LauncherActivity::class.java)).use { scenario ->
            i.waitForIdleSync();Thread.sleep(500);i.waitForIdleSync()
            scenario.onActivity { home ->
                val actual=home.findViewById<ViewGroup>(R.id.mainview)
                SettingsPreviewFixture.steadyPresentation(actual)
                val a=bitmap(actual)
                val snapshot=checkNotNull(LauncherActivity.captureHomePreview())
                val preview=snapshot.create(home)
                SettingsPreviewFixture.steadyPresentation(preview)
                layout(preview,snapshot.width,snapshot.height)
                fun dump(v: View, level: Int=0): String {
                    val lp=v.layoutParams
                    val m=lp as? ViewGroup.MarginLayoutParams
                    val name=runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault(v.javaClass.simpleName)
                    val extra=if(v is TextView) " text=${v.text} color=${v.currentTextColor} paint=${v.paint.color} alpha=${v.paint.alpha} flags=${v.paintFlags} bold=${v.paint.isFakeBoldText} skew=${v.paint.textSkewX} size=${v.textSize} layout=${v.layout?.width},${v.layout?.height},${v.layout?.lineCount} minmax=${v.minLines},${v.maxLines},${v.minHeight},${v.maxHeight} font=${v.typeface} align=${v.textAlignment} transform=${v.transformationMethod} scroll=${v.scrollX},${v.scrollY}" else ""
                    val row="${" ".repeat(level)}$name ${v.javaClass.simpleName} ${v.left},${v.top},${v.width},${v.height} lp=${lp?.width},${lp?.height} m=${m?.leftMargin},${m?.topMargin},${m?.rightMargin},${m?.bottomMargin} rel=${m?.marginStart},${m?.marginEnd} $extra\n"
                    return row+(if(v is ViewGroup) (0 until v.childCount).joinToString("") {dump(v.getChildAt(it),level+1)} else "")
                }
                val b=bitmap(preview)
                val out=File(c.getExternalFilesDir(null),"issue23").apply { mkdirs() }
                File(out,"bounds.txt").writeText(dump(actual)+"\nPREVIEW\n"+dump(preview))
                File(out,"home.png").outputStream().use { a.compress(Bitmap.CompressFormat.PNG,100,it) }
                File(out,"preview.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
                val left=IntArray(a.width*a.height);val right=IntArray(b.width*b.height)
                a.getPixels(left,0,a.width,0,0,a.width,a.height);b.getPixels(right,0,b.width,0,0,b.width,b.height)
                val differences=left.indices.count { left[it]!=right[it] }
                a.recycle();b.recycle()
                assertEquals("Unedited real preview differs in $differences pixels",0,differences)
            }
        }
    }
    @Test fun draftDoesNotChangeSavedValuesAndUsesCurrentContent() {
        prepare()
        ActivityScenario.launch<LauncherActivity>(Intent(c,LauncherActivity::class.java)).use { home ->
            i.waitForIdleSync();Thread.sleep(300)
            home.onActivity {
                val state=checkNotNull(LauncherActivity.captureHomePreview())
                val before=LauncherSettings.get(Theme.output_background_color)
                val draft=state.saved.overlay({ if(it==Theme.output_background_color) "#FF123456" else null },{it==Theme.output_background_color})
                val preview=state.createDraft(it,draft);layout(preview,state.width,state.height)
                assertEquals(before,LauncherSettings.get(Theme.output_background_color))
                assertFalse(ThemeColorResolver.hasPreview())
                assertEquals(0xFF123456.toInt(),draft.color(Theme.output_background_color))
                assertNotNull(preview.findViewById<View>(R.id.terminal_view))
            }
        }
    }
}
