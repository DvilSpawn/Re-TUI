package ohi.andre.consolelauncher.managers.settings

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.assertEquals
import org.junit.Test

class TextShadowsIntegrationTest {
    @Test fun appliesSharedParametersToSuggestionsAndModuleChildrenAndClearsTransparent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context)
        val settings = listOf(Ui.shadow_params, Theme.suggestions_text_shadow_color, Theme.module_text_shadow_color)
        val saved = settings.associateWith { LauncherSettings.get(it) }
        try {
            LauncherSettings.set(context, Ui.shadow_params, "-2,3,4")
            LauncherSettings.set(context, Theme.suggestions_text_shadow_color, "#FF123456")
            LauncherSettings.set(context, Theme.module_text_shadow_color, "#FF654321")
            instrumentation.runOnMainSync {
                val suggestion = TextView(context)
                TextShadows.apply(suggestion, Theme.suggestions_text_shadow_color)
                assertEquals(Color.parseColor("#FF123456"), suggestion.shadowColor)
                assertEquals(-2f, suggestion.shadowDx, 0f)
                assertEquals(3f, suggestion.shadowDy, 0f)
                assertEquals(4f, suggestion.shadowRadius, 0f)
                val module = LinearLayout(context)
                val child = TextView(context)
                module.addView(child)
                TextShadows.applyModule(module)
                assertEquals(Color.parseColor("#FF654321"), child.shadowColor)
                LauncherSettings.set(context, Theme.module_text_shadow_color, "#00000000")
                TextShadows.applyModule(module)
                assertEquals(0f, child.shadowRadius, 0f)
                assertEquals(Color.TRANSPARENT, child.shadowColor)
            }
        } finally {
            saved.forEach { (setting, value) -> LauncherSettings.set(context, setting, value) }
        }
    }
}
