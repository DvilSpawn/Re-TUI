package ohi.andre.consolelauncher.navigation

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemerBackTest {
    @Test fun backFromAppearanceReturnsToSettingsHub() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = instrumentation.startActivitySync(Intent(context, ThemerActivity::class.java)
            .putExtra(ThemerActivity.EXTRA_SECTION, ThemerActivity.SECTION_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ThemerActivity
        try {
            instrumentation.waitForIdleSync()
            fun findList(view: View): RecyclerView? {
                if (view is RecyclerView) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    findList(view.getChildAt(index))?.let { return it }
                }
                return null
            }
            val list = checkNotNull(findList(settings.window.decorView))
            fun firstLabel() = (checkNotNull(list.findViewHolderForAdapterPosition(0)).itemView as TextView).text.toString()
            instrumentation.runOnMainSync {
                assertTrue(firstLabel().contains("APPEARANCE"))
                list.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertFalse(firstLabel().contains("APPEARANCE")) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertFalse(settings.isFinishing)
                assertTrue(firstLabel().contains("APPEARANCE"))
            }
        } finally {
            instrumentation.runOnMainSync { settings.finish() }
        }
    }
}
