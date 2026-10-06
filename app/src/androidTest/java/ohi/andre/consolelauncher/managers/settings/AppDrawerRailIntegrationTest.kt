package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.ui.AppDrawerPaneManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDrawerRailIntegrationTest {
    @Test fun renderedRailsUseIndependentNormalAndSelectedColors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Tuils.init(context)
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val framePrefs = context.getSharedPreferences("retui_frames", Context.MODE_PRIVATE)
        val hadFrameSetting = framePrefs.contains("frames_enabled")
        val framesEnabled = framePrefs.getBoolean("frames_enabled", true)
        try {
            framePrefs.edit().putBoolean("frames_enabled", false).commit()
            mapOf(
                Theme.app_drawer_group_background_color to "#FF123456",
                Theme.app_drawer_group_text_color to "#FF234567",
                Theme.app_drawer_group_selected_background_color to "#FF345678",
                Theme.app_drawer_group_selected_text_color to "#FF456789",
                Theme.app_drawer_alphabet_background_color to "#FF56789A",
                Theme.app_drawer_alphabet_text_color to "#FF6789AB",
                Theme.app_drawer_alphabet_selected_background_color to "#FF789ABC",
                Theme.app_drawer_alphabet_selected_text_color to "#FF89ABCD"
            ).forEach { (role, value) -> check(ThemeColorResolver.preview(role, value)) }
            instrumentation.runOnMainSync {
                val root = LayoutInflater.from(context).inflate(R.layout.apps_drawer, null) as ViewGroup
                val drawer = AppDrawerPaneManager(context, root, { null }, {}, {}, {})
                val addCategory = AppDrawerPaneManager::class.java.getDeclaredMethod("addGroupTab",
                    String::class.java, String::class.java, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                val categories = root.findViewById<ViewGroup>(R.id.apps_group_tabs)
                for (selected in listOf(false, true)) {
                    addCategory.invoke(drawer, "ALL", null, 0, 0, 0, selected)
                    val category = categories.getChildAt(categories.childCount - 1) as TextView
                    assertEquals(if (selected) 0xFF456789.toInt() else 0xFF234567.toInt(), category.currentTextColor)
                    assertEquals(if (selected) 0xFF345678.toInt() else 0xFF123456.toInt(), fill(category))
                }
                val styleAlphabet = AppDrawerPaneManager::class.java.getDeclaredMethod("styleAlphaTab",
                    TextView::class.java, String::class.java, Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
                AppDrawerPaneManager::class.java.getDeclaredField("selectedAlpha").apply {
                    isAccessible = true; set(drawer, "A")
                }
                for (letter in listOf("B", "A")) {
                    val alphabet = TextView(context)
                    styleAlphabet.invoke(drawer, alphabet, letter, 0, 0, 0)
                    assertEquals(if (letter == "A") 0xFF89ABCD.toInt() else 0xFF6789AB.toInt(), alphabet.currentTextColor)
                    assertEquals(if (letter == "A") 0xFF789ABC.toInt() else 0xFF56789A.toInt(), fill(alphabet))
                }
            }
        } finally {
            ThemeColorResolver.clearPreview()
            framePrefs.edit().apply {
                if (hadFrameSetting) putBoolean("frames_enabled", framesEnabled) else remove("frames_enabled")
            }.commit()
        }
    }

    private fun fill(view: TextView): Int {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        view.background.setBounds(0, 0, 80, 80)
        view.background.draw(Canvas(bitmap))
        return bitmap.getPixel(40, 40).also { bitmap.recycle() }
    }
}
