package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class Toolbar(
    private val defaultValue: String,
    private val info: Int,
    private val type: String
) : XMLPrefsSave {
    show_toolbar("true", R.string.setting_toolbar_show_toolbar_description, XMLPrefsSave.BOOLEAN),
    hide_toolbar_no_input("false", R.string.setting_toolbar_hide_toolbar_no_input_description, XMLPrefsSave.BOOLEAN),
    shortcut_button_1_enabled("false", R.string.setting_toolbar_shortcut_button_1_enabled_description, XMLPrefsSave.BOOLEAN),
    shortcut_button_1_command("", R.string.setting_toolbar_shortcut_button_1_command_description, XMLPrefsSave.TEXT),
    shortcut_button_1_icon("star", R.string.setting_toolbar_shortcut_button_1_icon_description, XMLPrefsSave.TEXT),
    shortcut_button_2_enabled("false", R.string.setting_toolbar_shortcut_button_2_enabled_description, XMLPrefsSave.BOOLEAN),
    shortcut_button_2_command("", R.string.setting_toolbar_shortcut_button_2_command_description, XMLPrefsSave.TEXT),
    shortcut_button_2_icon("star", R.string.setting_toolbar_shortcut_button_2_icon_description, XMLPrefsSave.TEXT);

    override fun defaultValue(): String = defaultValue

    override fun infoRes(): Int = info

    override fun type(): String = type

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.TOOLBAR

    override fun label(): String = name

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
