package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class Apps(
    private val defaultValue: String,
    private val info: String,
    private val type: String = XMLPrefsSave.APP
) : XMLPrefsSave {
    default_app_n1("most_used", "The first default-suggested app"),
    default_app_n2("most_used", "The second default-suggested app"),
    default_app_n3("null", "The third default-suggested app"),
    default_app_n4("null", "The fourth default-suggested app"),
    default_app_n5("null", "The fifth default-suggested app"),
    app_groups_sorting(
        "2",
        "0 = time up->down; 1 = time down->up; 2 = alphabetical up->down; 3 = alphabetical down->up; 4 = most used up->down; 5 = most used down->up",
        XMLPrefsSave.INTEGER
    );

    override fun defaultValue(): String = defaultValue

    override fun info(): String = info

    override fun type(): String = type

    override fun label(): String = name

    override fun parent(): XMLPrefsElement? = AppsManager.instance

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()

    companion object {
        const val MOST_USED: String = "most_used"
        const val NULL: String = "null"
    }
}
