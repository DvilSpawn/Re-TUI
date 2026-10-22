package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.settings.StatusRowResolver
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherFontScale

object SettingsValidation {
    private val hexColor = Regex("^#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val fontSizes = setOf("ram_size", "battery_size", "device_size", "time_size",
        "storage_size", "network_size", "notes_size", "input_output_size", "ascii_size",
        "weather_size", "unlock_size", "module_header_text_size", "module_body_text_size",
        "output_header_text_size", "suggestions_size")

    fun error(item: XMLPrefsSave, value: String?): Int? {
        val raw = value.orEmpty()
        if (item === Behavior.unlock_counter_cycle_start) {
            val time = Regex("^(\\d{1,2})\\.(\\d{1,2})$").matchEntire(raw)
            return if (time == null || time.groupValues[1].toInt() !in 0..23 ||
                time.groupValues[2].toInt() !in 0..59) R.string.settings_editor_invalid_time else null
        }
        // Status indices are declared int, but fractional values intentionally share a row.
        if (StatusRowResolver.isStatusIndex(item)) {
            val number = raw.toBigDecimalOrNull()
            return if (number == null || number < java.math.BigDecimal.ONE ||
                number > java.math.BigDecimal(Int.MAX_VALUE - StatusRowResolver.settings.size))
                R.string.settings_editor_invalid_status_index else null
        }
        if (item === Behavior.launcher_mode && raw.isNotEmpty() && LauncherMode.normalize(raw) == null)
            return R.string.settings_editor_invalid_value
        when (item.type()) {
            XMLPrefsSave.BOOLEAN -> if (!raw.equals("true",true) && !raw.equals("false",true) && raw != item.defaultValue())
                return R.string.settings_editor_invalid_value
            XMLPrefsSave.INTEGER -> {
                val number = raw.toIntOrNull() ?: return R.string.settings_editor_invalid_integer
                if (item === Ui.font_size_offset && number !in LauncherFontScale.MIN_OFFSET..LauncherFontScale.MAX_OFFSET)
                    return R.string.settings_editor_invalid_font_offset
                if (item.label() in fontSizes && number < 1)
                    return R.string.settings_editor_invalid_font_size
            }
            XMLPrefsSave.COLOR, XMLPrefsSave.AUTO_COLOR -> {
                val valid = if (item is Theme) ThemeColorResolver.isValid(item, raw)
                else hexColor.matches(raw) || (item.type() == XMLPrefsSave.AUTO_COLOR && raw.equals("auto", true))
                if (!valid) return if (item.type() == XMLPrefsSave.AUTO_COLOR)
                    R.string.settings_editor_invalid_auto_color else R.string.settings_editor_invalid_color
            }
        }
        if (item.invalidValues()?.contains(raw) == true) return R.string.settings_editor_invalid_value
        return null
    }
}
