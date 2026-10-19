package ohi.andre.consolelauncher.managers.settings

import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.presentation.ModulePanelPresentation
import ohi.andre.consolelauncher.presentation.ModulePresentationSnapshot
import ohi.andre.consolelauncher.tuils.MusicVisualizerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreviewMusicCanvasTest {
    @Test fun frozenNativeBarsRecolorLikeHomeWithoutChangingContentOrSavedState() = with(SettingsPreviewFixture) {
        withHome {
            val savedState=appearanceState()
            val modulePrefs=context.getSharedPreferences("retui_modules",0).all.toMap()
            instrumentation.runOnMainSync {
                val appearance=HomeAppearance.saved()
                fun host():Pair<FrameLayout,LinearLayout> {
                    val root=FrameLayout(context)
                    val modules=LinearLayout(context).apply { id=R.id.home_modules_container;orientation=LinearLayout.VERTICAL }
                    root.addView(modules,FrameLayout.LayoutParams(-1,-2))
                    return root to modules
                }
                val (source,sourceHost)=host()
                val native=ModulePanelPresentation.inflate(context,sourceHost,ModulePanelPresentation.Kind.MUSIC)
                sourceHost.addView(native);native.visibility=View.VISIBLE
                ModulePanelPresentation.chrome(native,ModulePanelPresentation.Kind.MUSIC,appearance)
                ModulePanelPresentation.musicContent(native,appearance,false)
                val sourceBars=native.findViewById<MusicVisualizerView>(R.id.music_visualizer)
                val frame=MusicVisualizerView.Frame(List(36) { .12f+(it%9)*.09f },
                    Color.argb(140,220,80,30),.63f)
                sourceBars.setFrozenFrame(frame)
                layout(source);ModulePanelPresentation.applyVisualizerSize(native);layout(source)
                val snapshot=checkNotNull(ModulePresentationSnapshot.capture(source))
                val sourcePixels=canvasPixels(sourceBars)
                val (preview,previewHost)=host()
                val draftColor="#FF18C5A7"
                val draft=appearance.overlay({if(it==Theme.module_panel_text_color) draftColor else null},
                    {it==Theme.module_panel_text_color})
                snapshot.bind(previewHost,draft)
                layout(preview);snapshot.afterLayout(preview);layout(preview)
                val previewBars=preview.findViewById<MusicVisualizerView>(R.id.music_visualizer)
                assertEquals("Capture/bind must leave source bars unchanged",frame,sourceBars.captureFrame())
                assertArrayEquals(sourcePixels,canvasPixels(sourceBars))
                assertEquals(frame.bars,previewBars.captureFrame().bars)
                assertEquals(frame.opacity,previewBars.alpha,0f)
                assertEquals(Color.argb(140,24,197,167),previewBars.captureFrame().color)
                assertTrue("Draft module contains no controls, editors or providers",views(preview).none {
                    it.hasOnClickListeners() || it.isLongClickable || it is EditText || it is AppWidgetHostView
                })
                // Use the same actual native canvas and Home's color setter as the parity oracle.
                ModulePanelPresentation.musicContent(native,draft,false)
                sourceBars.setBarColor(draft.color(Theme.module_panel_text_color))
                layout(source);ModulePanelPresentation.applyVisualizerSize(native);layout(source)
                assertEquals(sourceBars.width,previewBars.width)
                assertEquals(sourceBars.height,previewBars.height)
                val expected=canvasPixels(sourceBars)
                assertFalse("The nonempty canvas must actually change color",sourcePixels.contentEquals(expected))
                repeat(4) {
                    assertArrayEquals("Frozen native canvas must match Home's recolored canvas",expected,canvasPixels(previewBars))
                    assertEquals(frame.bars,previewBars.captureFrame().bars)
                }
                assertEquals(modulePrefs,context.getSharedPreferences("retui_modules",0).all)
            }
            savedState.assertUnchanged()
        }
    }

    private fun layout(root:View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY))
        root.layout(0,0,720,700)
    }
    private fun canvasPixels(view:View):IntArray {
        assertTrue("Native visualizer must have a nonempty viewport",view.width>0 && view.height>0)
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(bitmap))
            IntArray(bitmap.width*bitmap.height).also { bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height) }
        } finally { bitmap.recycle() }
    }
}
