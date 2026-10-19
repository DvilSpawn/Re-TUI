package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@androidx.test.filters.SdkSuppress(minSdkVersion=30)
class SettingsPreviewKeyboardParityTest {
    @Test fun actualHomeImeViewportMatchesFrozenPreviewWithoutWritingSettings() = with(SettingsPreviewFixture) {
        assumeTrue("WindowInsets IME visibility requires API30",Build.VERSION.SDK_INT>=30)
        withHome(mapOf(Behavior.auto_show_keyboard to "false",Ui.system_wallpaper to "false")) {
            val home=checkNotNull(LauncherActivity.instance)
            val saved=appearanceState()
            var originalSoftInput=false
            instrumentation.runOnMainSync {
                val input=home.findViewById<EditText>(R.id.input_view)
                originalSoftInput=input.showSoftInputOnFocus
                input.showSoftInputOnFocus=true
                input.requestFocus()
                WindowCompat.getInsetsController(home.window,home.window.decorView).show(WindowInsetsCompat.Type.ime())
                (home.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)
            }
            try {
                waitForIme(home,true)
                instrumentation.runOnMainSync { steadyPresentation(home.findViewById(R.id.mainview)) }
                instrumentation.waitForIdleSync()
                capture("keyboard-home-with-ime")
                instrumentation.runOnMainSync {
                    val actual=home.findViewById<ViewGroup>(R.id.mainview)
                    assertTrue("Home keyboard must really be visible",home.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime()))
                    assertTrue(home.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom>0)
                    // Drawing first freezes the same scrollbar opacity that the snapshot subsequently reads.
                    val left=render(actual)
                    val snapshot=checkNotNull(LauncherActivity.captureHomePreview())
                    assertEquals(actual.width,snapshot.width);assertEquals(actual.height,snapshot.height)
                    val preview=snapshot.createDraft(home,snapshot.saved)
                    steadyPresentation(preview)
                    preview.measure(View.MeasureSpec.makeMeasureSpec(snapshot.width,View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(snapshot.height,View.MeasureSpec.EXACTLY))
                    preview.layout(0,0,snapshot.width,snapshot.height)
                    val right=render(preview)
                    try {
                        val folder=File(context.getExternalFilesDir(null),"issue23").apply { mkdirs() }
                        File(folder,"keyboard-home.png").outputStream().use { left.compress(Bitmap.CompressFormat.PNG,100,it) }
                        File(folder,"keyboard-preview.png").outputStream().use { right.compress(Bitmap.CompressFormat.PNG,100,it) }
                        File(folder,"keyboard-viewport.txt").writeText("viewport=${snapshot.width}x${snapshot.height}\nimeBottom=${home.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom}\n")
                        val actualPixels=IntArray(left.width*left.height)
                        val previewPixels=IntArray(right.width*right.height)
                        left.getPixels(actualPixels,0,left.width,0,0,left.width,left.height)
                        right.getPixels(previewPixels,0,right.width,0,0,right.width,right.height)
                        val differences=actualPixels.indices.count { actualPixels[it]!=previewPixels[it] }
                        assertEquals("Home with IME differs from frozen preview in $differences pixels",0,differences)
                    } finally { left.recycle();right.recycle() }
                }
            } finally {
                instrumentation.runOnMainSync {
                    (home.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(home.window.decorView.windowToken,0)
                    WindowCompat.getInsetsController(home.window,home.window.decorView).hide(WindowInsetsCompat.Type.ime())
                    home.findViewById<EditText>(R.id.input_view).showSoftInputOnFocus=originalSoftInput
                }
                waitForIme(home,false)
                saved.assertUnchanged()
            }
        }
    }

    private fun waitForIme(home: LauncherActivity,visible: Boolean) {
        repeat(50) {
            var current=false
            SettingsPreviewFixture.instrumentation.runOnMainSync {
                current=home.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true
            }
            if(current==visible) return
            Thread.sleep(100)
        }
        fail("Home IME visibility never became $visible")
    }

    private fun render(view: View)=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
}
