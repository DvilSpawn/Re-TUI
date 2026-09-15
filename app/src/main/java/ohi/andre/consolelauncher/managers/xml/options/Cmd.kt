package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class Cmd(
    private val defaultValue: String,
    private val info: Int,
    private val type: String = XMLPrefsSave.TEXT
) : XMLPrefsSave {
    default_search(
        "-gg",
        R.string.setting_cmd_default_search_description
    );

    override fun defaultValue(): String = defaultValue

    override fun type(): String = type

    override fun infoRes(): Int = info

    override fun parent(): XMLPrefsElement = XMLPrefsManager.XMLPrefsRoot.CMD

    override fun label(): String = name

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
