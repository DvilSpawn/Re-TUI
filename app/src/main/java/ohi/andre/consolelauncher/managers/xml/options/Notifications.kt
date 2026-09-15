package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class Notifications(
    private val defaultValue: String,
    private val info: Int,
    private val type: String = XMLPrefsSave.BOOLEAN
) : XMLPrefsSave {
    show_notifications("false", R.string.setting_notifications_show_notifications_description),
    terminal_notifications("true", R.string.setting_notifications_terminal_notifications_description),
    app_notification_enabled_default(
        "true",
        R.string.setting_notifications_app_notification_enabled_default_description
    ),
    notification_text_color("#00FF00", R.string.setting_notifications_notification_text_color_description, XMLPrefsSave.COLOR),
    notification_format("[%t] %pkg: %[100][teal]title --- %text", R.string.setting_notifications_notification_format_description, XMLPrefsSave.TEXT),
    click_notification(
        "true",
        R.string.setting_notifications_click_notification_description
    ),
    long_click_notification(
        "true",
        R.string.setting_notifications_long_click_notification_description
    ),
    notification_popup_exclude_app(
        "true",
        R.string.setting_notifications_notification_popup_exclude_app_description
    ),
    notification_popup_exclude_notification(
        "true",
        R.string.setting_notifications_notification_popup_exclude_notification_description
    ),
    notification_popup_reply(
        "true",
        R.string.setting_notifications_notification_popup_reply_description
    );

    override fun defaultValue(): String = defaultValue

    override fun infoRes(): Int = info

    override fun parent(): XMLPrefsElement? = NotificationManager.instance

    override fun label(): String = name

    override fun type(): String = type

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}
