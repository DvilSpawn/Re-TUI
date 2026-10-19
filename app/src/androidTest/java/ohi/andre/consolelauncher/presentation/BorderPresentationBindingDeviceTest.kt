package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.TerminalBorderDrawable
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BorderPresentationBindingDeviceTest {
    @Test fun replacingCutoutsAndRemovingBorderClearsPreviousListeners() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=ApplicationProvider.getApplicationContext<Context>()
            val panel=TrackedView(context).apply {
                background=TerminalBorderDrawable(Color.BLACK,Color.WHITE,1,0f,false,0f,0f)
            }
            val oldCutout=TrackedView(context)
            val nextCutout=TrackedView(context)
            TerminalBorderRuntime.bindPresentation(panel,oldCutout)
            assertEquals(1,panel.listeners.size);assertEquals(1,oldCutout.listeners.size)
            TerminalBorderRuntime.bindPresentation(panel,nextCutout)
            assertTrue(oldCutout.listeners.isEmpty())
            assertEquals(1,panel.listeners.size);assertEquals(1,nextCutout.listeners.size)
            panel.setBackgroundColor(Color.TRANSPARENT)
            TerminalBorderRuntime.bindPresentation(panel,nextCutout)
            assertTrue(panel.listeners.isEmpty());assertTrue(nextCutout.listeners.isEmpty())
            assertNull(panel.getTag(R.id.home_border_layout_binding))
        }
    }

    private class TrackedView(context: Context): View(context) {
        val listeners=mutableSetOf<OnLayoutChangeListener>()
        override fun addOnLayoutChangeListener(listener: OnLayoutChangeListener) {
            listeners.add(listener);super.addOnLayoutChangeListener(listener)
        }
        override fun removeOnLayoutChangeListener(listener: OnLayoutChangeListener) {
            listeners.remove(listener);super.removeOnLayoutChangeListener(listener)
        }
    }
}
