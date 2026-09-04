package ohi.andre.consolelauncher.commands.tuixt

import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuixtAdapterTest {
    @Test
    fun detectsExplicitThemeOrSuggestionColorsForLayeredOverrides() {
        assertTrue(isManualThemeColorChange(Theme.input_text_color, "#123456"))
        assertTrue(isManualThemeColorChange(Suggestions.apps_text_color, "#000000"))
        assertFalse(isManualThemeColorChange(Theme.input_text_color, "auto"))
        assertFalse(isManualThemeColorChange(Ui.input_output_size, "18"))
    }
}
