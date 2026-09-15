package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class Apps(
    private val defaultValue: String,
    private val info: Int,
    private val type: String = XMLPrefsSave.APP
) : XMLPrefsSave {
    default_app_n1("most_used", R.string.setting_apps_default_app_n1_description),
    default_app_n2("most_used", R.string.setting_apps_default_app_n2_description),
    default_app_n3("null", R.string.setting_apps_default_app_n3_description),
    default_app_n4("null", R.string.setting_apps_default_app_n4_description),
    default_app_n5("null", R.string.setting_apps_default_app_n5_description),
    app_groups_sorting(
        "2",
        R.string.setting_apps_app_groups_sorting_description,
        XMLPrefsSave.INTEGER
    );

    override fun defaultValue(): String = defaultValue

    override fun infoRes(): Int = info

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
