package ohi.andre.consolelauncher.commands.tuixt

import ohi.andre.consolelauncher.managers.settings.SettingsValidation
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

internal object SettingsInputValidation {
    fun error(item: XMLPrefsSave, value: String?) = SettingsValidation.error(item, value)
}
