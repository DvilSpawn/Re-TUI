package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class SurfaceBorderOption(
    private val default: String,
    private val kind: String,
    private val description: String
) : XMLPrefsSave {
    unified_status_border("false", XMLPrefsSave.BOOLEAN, "Use one bottom console for output, status, modules, input, toolbar, and suggestions"),
    ram_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the RAM surface border"),
    ram_border_color("auto", XMLPrefsSave.AUTO_COLOR, "RAM border color; AUTO inherits the active accent"),
    device_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the device surface border"),
    device_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Device border color; AUTO inherits the active accent"),
    time_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the time surface border"),
    time_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Time border color; AUTO inherits the active accent"),
    battery_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the battery surface border"),
    battery_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Battery border color; AUTO inherits the active accent"),
    storage_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the storage surface border"),
    storage_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Storage border color; AUTO inherits the active accent"),
    network_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the network surface border"),
    network_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Network border color; AUTO inherits the active accent"),
    notes_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the notes surface border"),
    notes_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Notes border color; AUTO inherits the active accent"),
    weather_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the weather surface border"),
    weather_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Weather border color; AUTO inherits the active accent"),
    unlock_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the unlock surface border"),
    unlock_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Unlock border color; AUTO inherits the active accent"),
    ascii_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the ASCII surface border"),
    ascii_border_color("auto", XMLPrefsSave.AUTO_COLOR, "ASCII border color; AUTO inherits the active accent"),
    input_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the input surface border"),
    input_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Input border color; AUTO inherits the active accent"),
    output_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the output surface border"),
    output_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Output border color; AUTO inherits the active accent"),
    toolbar_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show the toolbar surface border"),
    toolbar_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Toolbar border color; AUTO inherits the active accent"),
    suggestions_border_enabled("true", XMLPrefsSave.BOOLEAN, "Show suggestion surface borders"),
    suggestions_border_color("auto", XMLPrefsSave.AUTO_COLOR, "Suggestion border color; AUTO inherits the active accent");

    override fun defaultValue(): String = default
    override fun type(): String = kind
    override fun info(): String = description
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
