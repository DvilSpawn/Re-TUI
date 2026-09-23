package ohi.andre.consolelauncher.navigation

import android.content.Intent
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModuleSuggestionScrollDeviceTest {
    @Test fun tappingAnActiveModuleChipKeepsTheScrollPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        instrumentation.waitForIdleSync()
        val ui = checkNotNull(LauncherActivity.instance?.uiManager)
        assumeTrue(XMLPrefsManager.getBoolean(Suggestions.show_suggestions))
        val originalModule = ModuleManager.getActiveModule(context)
        val show = UIManager::class.java.getDeclaredMethod("showHomeModule", String::class.java)
            .apply { isAccessible = true }
        val close = UIManager::class.java.getDeclaredMethod("closeHomeModule")
            .apply { isAccessible = true }
        val refresh = UIManager::class.java.getDeclaredMethod("refreshModuleSuggestionsStrip")
            .apply { isAccessible = true }
        val stripField = UIManager::class.java.getDeclaredField("moduleSuggestionsScroll")
            .apply { isAccessible = true }
        var strip: HorizontalScrollView? = null
        var originalWidth: Int? = null
        try {
            instrumentation.runOnMainSync {
                show.invoke(ui, ModuleManager.TIMER)
                refresh.invoke(ui)
            }
            instrumentation.waitForIdleSync()
            strip = checkNotNull(stripField.get(ui) as? HorizontalScrollView)
            val currentStrip = strip!!
            instrumentation.runOnMainSync {
                originalWidth = currentStrip.layoutParams.width
                currentStrip.layoutParams = currentStrip.layoutParams.apply { width = 300 }
            }
            instrumentation.waitForIdleSync()
            var before = 0
            instrumentation.runOnMainSync {
                currentStrip.scrollTo(120, 0)
                before = currentStrip.scrollX
                assertTrue("Suggestion strip did not overflow: width=${currentStrip.width}, child=${currentStrip.getChildAt(0).width}, visibility=${currentStrip.visibility}, module=${ModuleManager.getActiveModule(context)}", before > 0)
                val chips = currentStrip.getChildAt(0) as ViewGroup
                val statusIndex = ModuleManager.getActiveSuggestions(context)
                    .filterNotNull()
                    .filter { it.mode == ModuleManager.ModuleSuggestion.MODE_COMMAND && !it.label.isNullOrEmpty() && !it.action.isNullOrEmpty() }
                    .indexOfFirst { it.action == "timer -status" }
                assertTrue("No read-only timer status chip", statusIndex >= 0)
                chips.getChildAt(statusIndex).performClick()
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { assertEquals(before, currentStrip.scrollX) }
        } finally {
            instrumentation.runOnMainSync {
                strip?.let { view ->
                    originalWidth?.let { width ->
                        view.layoutParams = view.layoutParams.apply { this.width = width }
                    }
                    view.scrollTo(0, 0)
                }
                if (originalModule.isEmpty()) close.invoke(ui) else show.invoke(ui, originalModule)
            }
        }
    }
}
