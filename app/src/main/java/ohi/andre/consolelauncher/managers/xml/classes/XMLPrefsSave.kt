package ohi.andre.consolelauncher.managers.xml.classes

import it.andreuzzi.comparestring2.StringableObject

interface XMLPrefsSave : StringableObject {
    fun defaultValue(): String?
    fun type(): String?
    fun info(): String?
    fun parent(): XMLPrefsElement?
    fun label(): String?
    fun invalidValues(): Array<out String?>?

    companion object {
        const val APP = "app"
        const val INTEGER = "int"
        const val BOOLEAN = "boolean"
        const val TEXT = "text"
        const val COLOR = "color"
        const val AUTO_COLOR = "auto_color"
    }
}
