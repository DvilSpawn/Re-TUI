package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class SurfaceBorderOption(
    private val default: String,
    private val kind: String,
    private val description: Int
) : XMLPrefsSave {
    unified_status_border("false", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_unified_status_border_description),
    ram_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_ram_border_enabled_description),
    ram_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_ram_border_color_description),
    device_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_device_border_enabled_description),
    device_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_device_border_color_description),
    time_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_time_border_enabled_description),
    time_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_time_border_color_description),
    battery_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_battery_border_enabled_description),
    battery_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_battery_border_color_description),
    storage_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_storage_border_enabled_description),
    storage_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_storage_border_color_description),
    network_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_network_border_enabled_description),
    network_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_network_border_color_description),
    notes_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_notes_border_enabled_description),
    notes_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_notes_border_color_description),
    weather_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_weather_border_enabled_description),
    weather_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_weather_border_color_description),
    unlock_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_unlock_border_enabled_description),
    unlock_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_unlock_border_color_description),
    ascii_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_ascii_border_enabled_description),
    ascii_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_ascii_border_color_description),
    input_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_input_border_enabled_description),
    input_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_input_border_color_description),
    output_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_output_border_enabled_description),
    output_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_output_border_color_description),
    toolbar_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_toolbar_border_enabled_description),
    toolbar_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_toolbar_border_color_description),
    suggestions_border_enabled("true", XMLPrefsSave.BOOLEAN, R.string.setting_surfaceborders_suggestions_border_enabled_description),
    suggestions_border_color("auto", XMLPrefsSave.AUTO_COLOR, R.string.setting_surfaceborders_suggestions_border_color_description);

    override fun defaultValue(): String = default
    override fun type(): String = kind
    override fun infoRes(): Int = description
    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.UI
    override fun label(): String = name
    override fun invalidValues(): Array<String?>? = null
    override fun getLowercaseString(): String = name
    override fun getString(): String = name
}

enum class SurfaceBorder(
    val enabled: SurfaceBorderOption,
    val color: SurfaceBorderOption
) {
    RAM(SurfaceBorderOption.ram_border_enabled, SurfaceBorderOption.ram_border_color),
    DEVICE(SurfaceBorderOption.device_border_enabled, SurfaceBorderOption.device_border_color),
    TIME(SurfaceBorderOption.time_border_enabled, SurfaceBorderOption.time_border_color),
    BATTERY(SurfaceBorderOption.battery_border_enabled, SurfaceBorderOption.battery_border_color),
    STORAGE(SurfaceBorderOption.storage_border_enabled, SurfaceBorderOption.storage_border_color),
    NETWORK(SurfaceBorderOption.network_border_enabled, SurfaceBorderOption.network_border_color),
    NOTES(SurfaceBorderOption.notes_border_enabled, SurfaceBorderOption.notes_border_color),
    WEATHER(SurfaceBorderOption.weather_border_enabled, SurfaceBorderOption.weather_border_color),
    UNLOCK(SurfaceBorderOption.unlock_border_enabled, SurfaceBorderOption.unlock_border_color),
    ASCII(SurfaceBorderOption.ascii_border_enabled, SurfaceBorderOption.ascii_border_color),
    INPUT(SurfaceBorderOption.input_border_enabled, SurfaceBorderOption.input_border_color),
    OUTPUT(SurfaceBorderOption.output_border_enabled, SurfaceBorderOption.output_border_color),
    TOOLBAR(SurfaceBorderOption.toolbar_border_enabled, SurfaceBorderOption.toolbar_border_color),
    SUGGESTIONS(SurfaceBorderOption.suggestions_border_enabled, SurfaceBorderOption.suggestions_border_color);

    companion object {
        fun isEnabled(master: Boolean, enabled: Boolean): Boolean = master && enabled

        fun resolveColor(raw: String?, inherited: Int): Int =
            if (raw.equals("auto", true)) inherited
            else parseHex(raw) ?: inherited

        private fun parseHex(raw: String?): Int? {
            val digits = raw?.takeIf { it.matches("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$".toRegex()) }
                ?.removePrefix("#") ?: return null
            val value = digits.toLong(16)
            return (if (digits.length == 6) value or 0xFF000000 else value).toInt()
        }
    }
}
