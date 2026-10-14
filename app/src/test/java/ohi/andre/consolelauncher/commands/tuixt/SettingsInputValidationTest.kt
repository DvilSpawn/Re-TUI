package ohi.andre.consolelauncher.commands.tuixt

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsInputValidationTest {
    @Test fun rejectsMalformedAndOverflowingNumbers() {
        for (value in listOf("", "not-a-number", "1.2", "2147483648", "-2147483649")) {
            assertEquals(value, R.string.settings_editor_invalid_integer,
                SettingsInputValidation.error(Ui.input_output_size, value))
        }
        assertNull(SettingsInputValidation.error(Behavior.clear_after_cmds, "-1"))
        assertNull(SettingsInputValidation.error(Behavior.clear_after_cmds, "2147483647"))
    }

    @Test fun enforcesMasterOffsetBoundsAndPositiveFontSize() {
        assertNull(SettingsInputValidation.error(Ui.font_size_offset, "-8"))
        assertNull(SettingsInputValidation.error(Ui.font_size_offset, "8"))
        assertEquals(R.string.settings_editor_invalid_font_offset,
            SettingsInputValidation.error(Ui.font_size_offset, "9"))
        assertEquals(R.string.settings_editor_invalid_font_size,
            SettingsInputValidation.error(Ui.input_output_size, "0"))
        assertNull(SettingsInputValidation.error(Ui.input_output_size, "1"))
    }

    @Test fun rejectsBadColorsAndLimitsAutoToInheritedRoles() {
        assertEquals(R.string.settings_editor_invalid_color,
            SettingsInputValidation.error(Theme.output_text_color, "#123"))
        assertEquals(R.string.settings_editor_invalid_color,
            SettingsInputValidation.error(Theme.output_text_color, "auto"))
        assertNull(SettingsInputValidation.error(Theme.output_text_color, "#aBcD12"))
        assertNull(SettingsInputValidation.error(Theme.output_text_color, "#00123456"))
        assertNull(SettingsInputValidation.error(Theme.settings_panel_text_color, "AUTO"))
    }

    @Test fun preservesSentinelsAndFreeTextValues() {
        assertNull(SettingsInputValidation.error(Ui.ascii_max_lines, "-1"))
        assertNull(SettingsInputValidation.error(Ui.input_prefix, "#123"))
        assertNull(SettingsInputValidation.error(Ui.notes_index, "1.1"))
        assertEquals(R.string.settings_editor_invalid_status_index,
            SettingsInputValidation.error(Ui.notes_index, "0"))
        assertNull(SettingsInputValidation.error(Ui.shadow_params, "1,2,3,#FF000000"))
        assertNull(SettingsInputValidation.error(Ui.dashed_border_stroke_width, "1.5"))
    }

    @Test fun allDeclaredIntegerDefaultsAreAccepted() {
        XMLPrefsManager.XMLPrefsRoot.entries.flatMap { it.enums.toList() }
            .filter { it.type() == XMLPrefsSave.INTEGER }.forEach { item ->
                assertNull(item.label(), SettingsInputValidation.error(item, item.defaultValue()))
            }
    }

    @Test fun clockSettingRetainsItsHourMinuteFormat() {
        assertNull(SettingsInputValidation.error(Behavior.unlock_counter_cycle_start, "6.00"))
        assertNull(SettingsInputValidation.error(Behavior.unlock_counter_cycle_start, "23.59"))
        assertEquals(R.string.settings_editor_invalid_time,
            SettingsInputValidation.error(Behavior.unlock_counter_cycle_start, "24.00"))
        assertEquals(R.string.settings_editor_invalid_time,
            SettingsInputValidation.error(Behavior.unlock_counter_cycle_start, "6.60"))
    }
}
