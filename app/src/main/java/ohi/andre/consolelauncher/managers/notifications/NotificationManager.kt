package ohi.andre.consolelauncher.managers.notifications

import android.content.Context
import android.graphics.Color
import android.os.Build
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.RegexManager
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.appNotificationsEnabledByDefault
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.defaultColorRaw
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.IdValue
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.util.Arrays
import java.util.regex.Pattern
import kotlin.math.max
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.regex.Matcher
import ohi.andre.consolelauncher.managers.settings.NotificationSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.VALUE_ATTRIBUTE
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.resetFile
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.set
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.writeTo

/**
 * Created by francescoandreuzzi on 29/04/2017.
 */
class NotificationManager private constructor(context: Context?) : XMLPrefsElement {
    var default_app_state: Boolean = appNotificationsEnabledByDefault()
    var default_color: String? = defaultColorRaw()

    override fun delete(): Array<String?>? {
        return null
    }

    override fun getValues(): XMLPrefsList? {
        return values
    }

    override fun write(save: XMLPrefsSave, value: String) {
        XMLPrefsManager.set(
            File(Tuils.getFolder(), PATH),
            save.label(),
            arrayOf<String>(XMLPrefsManager.VALUE_ATTRIBUTE),
            arrayOf<String?>(value)
        )
        values!!.add(save.label()!!, value)
    }

    override fun path(): String {
        return PATH
    }

    private var values: XMLPrefsList?
    private var apps: MutableList<NotificatedApp>?
    private var filters: MutableList<FilterRule>?
    private var formats: MutableList<IdValue>?

    init {
        instance = this

        apps = ArrayList<NotificatedApp>()
        filters = ArrayList<FilterRule>()
        formats = ArrayList<IdValue>()
        values = XMLPrefsList()

        run loadPrefs@ {
        try {
            val r = Tuils.getFolder()
            if (r == null) {
                context?.let { Tuils.sendOutput(Color.RED, it, R.string.tuinotfound_notifications) }
                return@loadPrefs
            }

            val file = File(r, PATH)
            if (!file.exists()) {
                XMLPrefsManager.resetFile(file, NAME)
            }

            val o: Array<Any?>?
            try {
                o = XMLPrefsManager.buildDocument(file, NAME)
                if (o == null) {
                    context?.let { Tuils.sendXMLParseError(it, PATH) }
                    return@loadPrefs
                }
            } catch (e: SAXParseException) {
                context?.let { Tuils.sendXMLParseError(it, PATH, e) }
                return@loadPrefs
            } catch (e: Exception) {
                Tuils.log(e)
                return@loadPrefs
            }

            val d = o[0] as Document
            val root = o[1] as Element

            val enums: MutableList<Notifications> =
                ArrayList<Notifications>(Arrays.asList<Notifications>(*Notifications.values()))
            val nodes = root.getElementsByTagName("*")

            val deleted: Array<String?>? = instance!!.delete()
            var needToWrite = false

            for (count in 0..<nodes.getLength()) {
                val node = nodes.item(count)

                val nn = node.getNodeName()
                if (Tuils.find(nn, enums as MutableList<*>) != -1) {
                    values!!.add(
                        nn,
                        node.getAttributes().getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE)
                            .getNodeValue()
                    )

                    for (en in enums.indices) {
                        if (enums.get(en).label() == nn) {
                            enums.removeAt(en)
                            break
                        }
                    }
                } else if (nn == FILTER_ATTRIBUTE) {
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        val e = node as Element

                        val regex =
                            XMLPrefsManager.getStringAttribute(e, XMLPrefsManager.VALUE_ATTRIBUTE)
                        if (regex == null) continue
                        var filterId: Int
                        try {
                            filterId =
                                if (e.hasAttribute(ID_ATTRIBUTE)) e.getAttribute(ID_ATTRIBUTE)
                                    .toInt() else -1
                        } catch (exc: NumberFormatException) {
                            filterId = -1
                        }
                        var pattern: Pattern?
                        try {
                            val id = regex.toInt()
                            pattern = RegexManager.instance!!.get(id)!!.regex
                        } catch (exc: Exception) {
                            try {
                                pattern = Pattern.compile(regex)
                            } catch (exc2: Exception) {
                                pattern = Pattern.compile(regex, Pattern.LITERAL)
                            }
                        }

                        filters!!.add(FilterRule(filterId, regex, pattern!!))
                    }
                } else if (nn == FORMAT_ATTRIBUTE) {
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        val e = node as Element

                        val format =
                            XMLPrefsManager.getStringAttribute(e, XMLPrefsManager.VALUE_ATTRIBUTE)
                        if (format == null) continue

                        val id: Int
                        try {
                            id = if (e.hasAttribute(ID_ATTRIBUTE)) e.getAttribute(ID_ATTRIBUTE)
                                .toInt() else -1
                        } catch (f: NumberFormatException) {
                            continue
                        }

                        formats!!.add(IdValue(format, id))
                    }
                } else {
                    val index = if (deleted == null) -1 else Tuils.find(nn, deleted)
                    if (index != -1) {
                        deleted!![index] = null
                        val e = node as Element
                        root.removeChild(e)

                        needToWrite = true
                    }

                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        val e = node as Element

                        val app: NotificatedApp?

                        val enabled = XMLPrefsManager.getBooleanAttribute(e, ENABLED_ATTRIBUTE)
                        val color = XMLPrefsManager.getStringAttribute(e, COLOR_ATTRIBUTE)
                        val format = XMLPrefsManager.getStringAttribute(e, FORMAT_ATTRIBUTE)

                        app = NotificatedApp(nn, color!!, format!!, enabled)
                        apps!!.add(app)
                    }
                }
            }

            if (enums.size > 0) {
                for (s in enums) {
                    val value: String? = s.defaultValue()

                    val em = d.createElement(s.label())
                    em.setAttribute(XMLPrefsManager.VALUE_ATTRIBUTE, value)
                    root.appendChild(em)

                    values!!.add(s.label(), value!!)
                }

                XMLPrefsManager.writeTo(d, file)
            } else if (needToWrite) {
                XMLPrefsManager.writeTo(d, file)
            }
        } catch (e: Exception) {
            Tuils.log(e)
            Tuils.toFile(e)
        }
        }

        for (app in apps!!) {
            try {
                val formatID = app.format.toInt()

                for (idValue in formats!!) {
                    if (idValue.id == formatID) {
                        app.format = idValue.value ?: app.format
                        break
                    }
                }
            } catch (e: Exception) {
            }
        }

        default_app_state = appNotificationsEnabledByDefault()
        default_color = defaultColorRaw()
    }

    fun dispose() {
        if (values != null) {
            values!!.list.clear()
            values = null
        }

        if (apps != null) {
            apps!!.clear()
            apps = null
        }

        if (filters != null) {
            filters!!.clear()
            filters = null
        }

        if (formats != null) {
            formats!!.clear()
            formats = null
        }

        instance = null
    }

    fun match(text: String): Boolean {
//        if(pkg.equals(BuildConfig.APPLICATION_ID)) return true;

        for (f in filters!!) {
            val m = f.pattern.matcher(text)
            if (m.matches() || m.find() || text == f.rawPattern) {
                return true
            }
        }

        return false
    }

    fun apps(): Int {
        return apps!!.size
    }

    fun describeRules(): String {
        val builder = StringBuilder()
        builder.append("Default app state: ")
            .append(if (default_app_state) "included" else "excluded")
            .append(Tuils.NEWLINE)

        builder.append("Included apps:")
        var foundIncluded = false
        for (app in apps!!) {
            if (app.enabled) {
                builder.append(Tuils.NEWLINE).append("- ").append(app.pkg)
                foundIncluded = true
            }
        }
        if (!foundIncluded) builder.append(" []")

        builder.append(Tuils.NEWLINE).append("Excluded apps:")
        var foundExcluded = false
        for (app in apps!!) {
            if (!app.enabled) {
                builder.append(Tuils.NEWLINE).append("- ").append(app.pkg)
                foundExcluded = true
            }
        }
        if (!foundExcluded) builder.append(" []")

        builder.append(Tuils.NEWLINE).append("Filters:")
        if (filters!!.isEmpty()) {
            builder.append(" []")
        } else {
            for (filter in filters!!) {
                builder.append(Tuils.NEWLINE)
                    .append("- ")
                if (filter.id >= 0) {
                    builder.append("[").append(filter.id).append("] ")
                }
                builder.append(displayPattern(filter.rawPattern))
            }
        }

        return builder.toString()
    }

    fun getAppState(pkg: String?): NotificatedApp? {
        val index = Tuils.find(pkg, apps)
        if (index == -1) return null
        return apps!!.get(index)
    }

    class NotificatedApp(
        var pkg: String,
        var color: String,
        var format: String,
        var enabled: Boolean
    ) {
        override fun equals(obj: Any?): Boolean {
            return this.toString() == obj.toString()
        }

        override fun toString(): String {
            return pkg
        }
    }

    private class FilterRule(val id: Int, val rawPattern: String?, val pattern: Pattern)
    companion object {
        private const val COLOR_ATTRIBUTE = "color"
        var ENABLED_ATTRIBUTE: String = "enabled"
        var ID_ATTRIBUTE: String = "id"
        var FORMAT_ATTRIBUTE: String = "format"
        var FILTER_ATTRIBUTE: String = "filter"

        const val PATH: String = "notifications.xml"
        private const val NAME = "NOTIFICATIONS"

        var instance: NotificationManager? = null
        fun create(context: Context?): NotificationManager {
            if (instance == null) return NotificationManager(context)
            else return instance!!
        }

        fun setState(pkg: String?, state: Boolean): String? {
            return XMLPrefsManager.set(
                File(Tuils.getFolder(), PATH), pkg, arrayOf<String?>(
                    ENABLED_ATTRIBUTE
                ), arrayOf<String>(state.toString())
            )
        }

        fun setColor(pkg: String?, color: String?): String? {
            return XMLPrefsManager.set(
                File(Tuils.getFolder(), PATH), pkg, arrayOf<String?>(
                    ENABLED_ATTRIBUTE, COLOR_ATTRIBUTE
                ), arrayOf<String?>(true.toString(), color)
            )
        }

        fun setFormat(pkg: String?, format: String?): String? {
            return XMLPrefsManager.set(
                File(Tuils.getFolder(), PATH), pkg, arrayOf<String?>(
                    FORMAT_ATTRIBUTE
                ), arrayOf<String?>(format)
            )
        }

        fun addFilter(pattern: String?, id: Int): String? {
            val file = File(Tuils.getFolder(), PATH)
            val filterId = if (id >= 0) id else nextFilterId(file)
            return XMLPrefsManager.add(
                file,
                FILTER_ATTRIBUTE,
                arrayOf<String?>(ID_ATTRIBUTE, XMLPrefsManager.VALUE_ATTRIBUTE),
                arrayOf<String?>(filterId.toString(), pattern)
            )
        }

        fun addLiteralFilter(value: String?): String? {
            if (value == null || value.length == 0) return Tuils.EMPTYSTRING
            return addFilter(Pattern.quote(value), -1)
        }

        fun addFormat(format: String?, id: Int): String? {
            return XMLPrefsManager.add(
                File(Tuils.getFolder(), PATH), FORMAT_ATTRIBUTE, arrayOf<String?>(
                    ID_ATTRIBUTE, XMLPrefsManager.VALUE_ATTRIBUTE
                ), arrayOf<String?>(id.toString(), format)
            )
        }

        fun rmFilter(id: Int): String? {
            return XMLPrefsManager.removeNode(
                File(Tuils.getFolder(), PATH), FILTER_ATTRIBUTE, arrayOf<String?>(
                    ID_ATTRIBUTE
                ), arrayOf<String>(id.toString())
            )
        }

        fun rmFormat(id: Int): String? {
            return XMLPrefsManager.removeNode(
                File(Tuils.getFolder(), PATH), FORMAT_ATTRIBUTE, arrayOf<String?>(
                    ID_ATTRIBUTE
                ), arrayOf<String>(id.toString())
            )
        }

        private fun nextFilterId(file: File): Int {
            var max = -1
            try {
                val o = XMLPrefsManager.buildDocument(file, null)
                if (o == null) return 0
                val root = o[1] as Element
                val nodes = root.getElementsByTagName(FILTER_ATTRIBUTE)
                for (i in 0..<nodes.getLength()) {
                    val node = nodes.item(i)
                    if (node !is Element) continue
                    val element = node
                    try {
                        max = max(max, element.getAttribute(ID_ATTRIBUTE).toInt())
                    } catch (ignored: Exception) {
                    }
                }
            } catch (e: Exception) {
                Tuils.log(e)
            }
            return max + 1
        }

        private fun displayPattern(rawPattern: String?): String {
            if (rawPattern == null) return Tuils.EMPTYSTRING
            if (rawPattern.startsWith("\\Q") && rawPattern.endsWith("\\E")) {
                return rawPattern.substring(2, rawPattern.length - 2)
                    .replace("\\E\\\\E\\Q", "\\E")
            }
            return rawPattern
        }
    }
}
