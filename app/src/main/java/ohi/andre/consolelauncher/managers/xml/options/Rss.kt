package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class Rss(
    private val defaultValue: String,
    private val info: Int,
    private val type: String
) : XMLPrefsSave {
    rss_item_text_color("#f44336", R.string.setting_rss_rss_item_text_color_description, XMLPrefsSave.COLOR),
    rss_default_format(
        "%[50][green]title ### %[100][teal]description (%pubDate)",
        R.string.setting_rss_rss_default_format_description,
        XMLPrefsSave.TEXT
    ),
    include_rss_default(
        "true",
        R.string.setting_rss_include_rss_default_description,
        XMLPrefsSave.BOOLEAN
    ),
    rss_hidden_tags("img", R.string.setting_rss_rss_hidden_tags_description, XMLPrefsSave.TEXT),
    rss_time_format("%t0", R.string.setting_rss_rss_time_format_description, XMLPrefsSave.TEXT),
    show_rss_download("true", R.string.setting_rss_show_rss_download_description, XMLPrefsSave.BOOLEAN),
    rss_download_format("RSS: %id --- Downloaded %sb bytes", R.string.setting_rss_rss_download_format_description, XMLPrefsSave.TEXT),
    rss_download_message_text_color("aqua", R.string.setting_rss_rss_download_message_text_color_description, XMLPrefsSave.COLOR),
    click_rss("true", R.string.setting_rss_click_rss_description, XMLPrefsSave.BOOLEAN);

    override fun defaultValue(): String = defaultValue

    override fun type(): String = type

    override fun infoRes(): Int = info

    override fun parent(): XMLPrefsElement? = RssManager.instance

    override fun label(): String = name

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
