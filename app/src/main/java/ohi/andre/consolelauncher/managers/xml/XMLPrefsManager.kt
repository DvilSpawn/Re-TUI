package ohi.andre.consolelauncher.managers.xml

import android.content.Context
import android.graphics.Color
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.FirstRunPresetManager
import ohi.andre.consolelauncher.managers.xml.AutoColorManager.getColor
import ohi.andre.consolelauncher.managers.xml.AutoColorManager.init
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Cmd
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Rss
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.min
import org.w3c.dom.NodeList
import java.util.ArrayList
import javax.xml.transform.Transformer
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsEntry

object XMLPrefsManager {
    const val XML_DEFAULT: String = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    const val VALUE_ATTRIBUTE: String = "value"

    private val factory: DocumentBuilderFactory
    private var builder: DocumentBuilder? = null

    init {
        factory = DocumentBuilderFactory.newInstance()
        try {
            builder = factory.newDocumentBuilder()
        } catch (e: ParserConfigurationException) {
        }
    }

    fun dispose() {
        commonsLoaded = false

        for (element in XMLPrefsRoot.entries) {
            element.getValues()?.list?.clear()
        }
    }

    var commonsLoaded: Boolean = false
    fun loadCommons(context: Context?) {
        if (context == null) {
            return
        }
        Tuils.init(context)
        init(context)

        if (commonsLoaded) return
        commonsLoaded = true

        val folder = Tuils.getFolder()
        if (folder == null) {
            Tuils.sendOutput(Color.RED, context, R.string.tuinotfound_xmlprefs)
            return
        }
        FirstRunPresetManager.seedIfNeeded(context, folder)

        for (element in XMLPrefsRoot.entries) {
            if (element === XMLPrefsRoot.APPS) continue

            element.getValues()?.list?.clear()

            val file = File(folder, element.path)
            if (!file.exists()) {
                resetFile(file, element.name)
            }

            val o: Array<Any?>?
            try {
                o = buildDocument(file, element.name)
                if (o == null) {
                    Tuils.sendXMLParseError(context, element.path)
                    return
                }
            } catch (e: SAXParseException) {
                Tuils.sendXMLParseError(context, element.path, e)
                continue
            } catch (e: Exception) {
                Tuils.log(e)
                return
            }

            var d = o[0] as Document
            var root = o[1] as Element?

            //            we are keeping this because maybe there are some new values to write
            val enums: MutableList<XMLPrefsSave> = ArrayList<XMLPrefsSave>(element.enums)

            val deleted: Array<String?>? = element.delete()?.map { it }?.toTypedArray()
            var needToWrite = false

            if (root == null) {
                resetFile(file, element.name)
                try {
                    d = builder!!.parse(file)
                } catch (e: Exception) {
                }
                root = d.getElementsByTagName(element.name).item(0) as Element?
            }

            if (element === XMLPrefsRoot.UI) {
                needToWrite = needToWrite or migrateRenamedUiValue(
                    d,
                    root,
                    "display_margin_mm",
                    Ui.display_margin_top_section.label()
                )
            }
            needToWrite = needToWrite or migrateRootValues(d, root, element)
            needToWrite = needToWrite or ensureDefaultSectionComments(d, root, element)

            val nodes = root!!.getElementsByTagName("*")

            for (count in 0..<nodes.getLength()) {
                val node = nodes.item(count)
                val nn = node.getNodeName()

                var value: String?
                try {
                    value = node.getAttributes().getNamedItem(VALUE_ATTRIBUTE).getNodeValue()
                } catch (e: Exception) {
                    continue
                }

                var check = false
                for (en in enums.indices) {
                    val opt = enums.get(en)

                    if (opt.label() == nn) {
                        val s = enums.removeAt(en)

                        val iv = s.invalidValues()
                        if (iv != null) {
                            for (temp in iv) {
                                if (temp == value) {
                                    value = opt.defaultValue()

                                    val em = node as Element
                                    em.setAttribute(VALUE_ATTRIBUTE, value)

                                    needToWrite = true

                                    break
                                }
                            }
                        }

                        element.getValues()!!.add(nn!!, value!!)

                        check = true
                        break
                    }
                }

                if (!check && deleted != null) {
                    val index = Tuils.find(nn, deleted)
                    if (index != -1) {
                        deleted[index] = null
                        val e = node as Element
                        root.removeChild(e)

                        needToWrite = true
                    }
                }
            }

            if (enums.size == 0) {
                if (needToWrite) writeTo(d, file)
                continue
            }

            appendMissingPrefs(d, root, element, enums)

            writeTo(d, file)
        }
    }

    private val STATUS_KEYS = arrayOf(
        "ram",
        "device",
        "time",
        "battery",
        "storage",
        "network",
        "notes",
        "weather",
        "unlock",
        "ascii"
    )

    private val THEME_RENAMES = arrayOf(
        "input_color" to "input_text_color",
        "output_color" to "output_text_color",
        "bg_color" to "background_color",
        "device_color" to "device_text_color",
        "ascii_color" to "ascii_text_color",
        "time_color" to "time_text_color",
        "storage_color" to "storage_text_color",
        "ram_color" to "ram_text_color",
        "network_info_color" to "network_info_text_color",
        "toolbar_bg" to "toolbar_background_color",
        "toolbar_color" to "toolbar_icon_color",
        "enter_color" to "enter_icon_color",
        "overlay_color" to "wallpaper_overlay_color",
        "alias_content_color" to "alias_content_text_color",
        "app_installed_color" to "app_installed_text_color",
        "app_uninstalled_color" to "app_uninstalled_text_color",
        "mark_color" to "regex_match_background_color",
        "notes_color" to "notes_text_color",
        "notes_locked_color" to "locked_notes_text_color",
        "link_color" to "link_text_color",
        "restart_message_color" to "restart_message_text_color",
        "weather_color" to "weather_text_color",
        "unlock_counter_color" to "unlock_counter_text_color",
        "session_info_color" to "session_info_text_color",
        "input_bg" to "input_background_color",
        "output_bg" to "output_background_color",
        "suggestions_bg" to "suggestions_background_color",
        "input_shadow_color" to "input_text_shadow_color",
        "output_shadow_color" to "output_text_shadow_color",
        "apps_drawer_color" to "apps_drawer_text_color",
        "dashed_border_color" to "terminal_border_color",
        "module_button_bg_color" to "module_button_background_color",
        "module_name_text_color" to "module_text_color",
        "window_terminal_bg" to "terminal_window_background_color",
        "terminal_header_bg" to "terminal_header_background_color"
    )

    private val SUGGESTION_RENAMES = arrayOf(
        "default_bg_color" to "default_background_color",
        "apps_bg_color" to "apps_background_color",
        "alias_bg_color" to "alias_background_color",
        "cmd_bg_color" to "cmd_background_color",
        "song_bg_color" to "song_background_color",
        "contact_bg_color" to "contact_background_color",
        "file_bg_color" to "file_background_color"
    )

    private val NOTIFICATION_RENAMES = arrayOf(
        "default_notification_color" to "notification_text_color"
    )

    private val RSS_RENAMES = arrayOf(
        "rss_default_color" to "rss_item_text_color",
        "rss_download_message_color" to "rss_download_message_text_color"
    )

    private val BEHAVIOR_RENAMES = arrayOf(
        "tui_notification_time_color" to "tui_notification_time_text_color",
        "tui_notification_input_color" to "tui_notification_input_text_color"
    )

    private fun migrateRootValues(d: Document, root: Element?, element: XMLPrefsRoot): Boolean {
        var changed = false
        if (root == null) {
            return false
        }

        val renames = when (element) {
            XMLPrefsRoot.THEME -> THEME_RENAMES
            XMLPrefsRoot.SUGGESTIONS -> SUGGESTION_RENAMES
            XMLPrefsRoot.NOTIFICATIONS -> NOTIFICATION_RENAMES
            XMLPrefsRoot.RSS -> RSS_RENAMES
            XMLPrefsRoot.BEHAVIOR -> BEHAVIOR_RENAMES
            else -> emptyArray<Pair<String, String>>()
        }
        for (rename in renames) {
            changed = renameDirectChild(d, root, rename.first, rename.second) or changed
        }

        if (element === XMLPrefsRoot.THEME) {
            changed = expandListValue(
                d,
                root,
                "status_lines_bg",
                STATUS_KEYS.map { it + "_status_background_color" }.toTypedArray(),
                "#00000000"
            ) or changed
            changed = expandListValue(
                d,
                root,
                "status_lines_shadow_color",
                STATUS_KEYS.map { it + "_status_text_shadow_color" }.toTypedArray(),
                "#00000000"
            ) or changed
        } else if (element === XMLPrefsRoot.UI) {
            changed = expandListValue(
                d,
                root,
                "status_lines_alignment",
                STATUS_KEYS.map { it + "_status_alignment" }.toTypedArray(),
                "-1"
            ) or changed
        }

        return changed
    }

    private fun renameDirectChild(d: Document, root: Element, oldName: String, newName: String): Boolean {
        val oldElement = findDirectChild(root, oldName) ?: return false
        if (findDirectChild(root, newName) != null) {
            root.removeChild(oldElement)
            return true
        }

        try {
            d.renameNode(oldElement, null, newName)
        } catch (ignored: Exception) {
            val replacement = d.createElement(newName)
            replacement.setAttribute(VALUE_ATTRIBUTE, oldElement.getAttribute(VALUE_ATTRIBUTE))
            root.insertBefore(replacement, oldElement)
            root.removeChild(oldElement)
        }
        return true
    }

    private fun expandListValue(
        d: Document,
        root: Element,
        oldName: String,
        newNames: Array<String>,
        defaultValue: String
    ): Boolean {
        val oldElement = findDirectChild(root, oldName) ?: return false
        val values = getListOfStringValues(oldElement.getAttribute(VALUE_ATTRIBUTE), newNames.size, defaultValue)
        for (index in newNames.indices) {
            if (findDirectChild(root, newNames[index]) != null) {
                continue
            }
            val replacement = d.createElement(newNames[index])
            replacement.setAttribute(VALUE_ATTRIBUTE, values[index] ?: defaultValue)
            root.insertBefore(replacement, oldElement)
        }
        root.removeChild(oldElement)
        return true
    }

    private fun appendMissingPrefs(
        d: Document,
        root: Element,
        element: XMLPrefsRoot,
        missing: MutableList<XMLPrefsSave>
    ) {
        val grouped = groupBySection(missing)
        for (entry in grouped.entries) {
            val section = entry.key
            appendSectionCommentIfNeeded(d, root, section)

            for (save in entry.value) {
                val value = save.defaultValue()
                val em = d.createElement(save.label())
                em.setAttribute(VALUE_ATTRIBUTE, value)
                root.appendChild(em)
                root.appendChild(d.createTextNode("\n"))

                element.getValues()!!.add(save.label()!!, value!!)
            }
        }
    }

    private fun ensureDefaultSectionComments(d: Document, root: Element?, element: XMLPrefsRoot): Boolean {
        if (root == null || hasAnyComment(root)) {
            return false
        }

        val existing: MutableMap<String, Node> = HashMap()
        val children = root.childNodes
        for (index in 0..<children.length) {
            val node = children.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            existing[node.nodeName] = node.cloneNode(true)
        }

        if (existing.isEmpty()) {
            return false
        }

        while (root.hasChildNodes()) {
            root.removeChild(root.firstChild)
        }

        root.appendChild(d.createTextNode("\n"))
        var addedAny = false
        val grouped = groupBySection(element.enums.toMutableList())
        for (entry in grouped.entries) {
            val section = entry.key
            val nodes: MutableList<Node> = ArrayList<Node>()
            for (save in entry.value) {
                val label = save.label() ?: continue
                val node = existing[label] ?: continue
                nodes.add(node)
            }
            if (nodes.isEmpty()) {
                continue
            }

            if (addedAny) {
                root.appendChild(d.createTextNode("\n"))
            }
            root.appendChild(d.createComment(" #$section "))
            root.appendChild(d.createTextNode("\n"))

            for (node in nodes) {
                root.appendChild(node)
                root.appendChild(d.createTextNode("\n"))
            }
            addedAny = true
        }

        return addedAny
    }

    private fun groupBySection(saves: MutableList<XMLPrefsSave>): LinkedHashMap<String, MutableList<XMLPrefsSave>> {
        val grouped: LinkedHashMap<String, MutableList<XMLPrefsSave>> =
            LinkedHashMap<String, MutableList<XMLPrefsSave>>()
        for (save in saves) {
            val section = sectionFor(save)
            var bucket = grouped[section]
            if (bucket == null) {
                bucket = ArrayList<XMLPrefsSave>()
                grouped[section] = bucket
            }
            bucket.add(save)
        }
        return grouped
    }

    private fun hasAnyComment(root: Element): Boolean {
        val children = root.childNodes
        for (index in 0..<children.length) {
            val node = children.item(index)
            if (node.nodeType == Node.COMMENT_NODE) {
                return true
            }
        }
        return false
    }

    private fun hasSectionComment(root: Element): Boolean {
        val children = root.childNodes
        for (index in 0..<children.length) {
            val node = children.item(index)
            if (node.nodeType != Node.COMMENT_NODE) {
                continue
            }


            if (node.nodeValue.trim().startsWith("#")) {
                return true
            }
        }
        return false
    }

    private fun appendSectionCommentIfNeeded(d: Document, root: Element, section: String) {
        if (lastSectionComment(root) == section.trim().lowercase(Locale.US)) {
            return
        }
        if (root.hasChildNodes()) {
            root.appendChild(d.createTextNode("\n"))
        }
        root.appendChild(d.createComment(" #$section "))
        root.appendChild(d.createTextNode("\n"))
    }

    private fun lastSectionComment(root: Element): String? {
        val children = root.childNodes
        for (index in children.length - 1 downTo 0) {
            val node = children.item(index)
            if (node.nodeType == Node.COMMENT_NODE) {
                val label = node.nodeValue.trim()
                if (label.startsWith("#")) {
                    return label.removePrefix("#").trim().lowercase(Locale.US)
                }
            }
        }
        return null
    }

    fun sectionFor(save: XMLPrefsSave): String {
        val label = save.label() ?: return "General"
        return when (save) {
            is Theme -> themeSection(label)
            is Ui -> uiSection(label)
            is Behavior -> behaviorSection(label)
            is Suggestions -> suggestionsSection(label)
            is Toolbar -> if (label.startsWith("shortcut_button")) "Custom Shortcuts" else "Visibility"
            is Notifications -> notificationSection(label)
            is Apps -> if (label.startsWith("default_app_n")) "Default App Suggestions" else "App Drawer"
            is Rss -> rssSection(label)
            is Cmd -> "Commands"
            else -> "General"
        }
    }

    private fun themeSection(label: String): String = when {
        label == "background_color" || label == "wallpaper_overlay_color" -> "Launcher"
        label.contains("_status_") || label.startsWith("battery_text_") ||
            label in arrayOf(
                "device_text_color",
                "ascii_text_color",
                "time_text_color",
                "storage_text_color",
                "ram_text_color",
                "network_info_text_color",
                "weather_text_color",
                "unlock_counter_text_color"
            ) -> "Status Lines"
        label.contains("background_color") || label.contains("border_color") || label.contains("shadow_color") -> "Terminal Surfaces"
        label.contains("module") || label.startsWith("terminal_") -> "Modules And Panels"
        label.contains("icon_color") || label == "cursor_color" -> "Terminal Controls"
        else -> "Content Text"
    }

    private fun uiSection(label: String): String = when {
        label.startsWith("show_") && (label.contains("ram") || label.contains("battery") || label.contains("time") ||
            label.contains("storage") || label.contains("network") || label.contains("notes") || label.contains("weather") ||
            label.contains("unlock") || label.contains("ascii") || label.contains("device")) -> "Status Lines"
        label.endsWith("_size") || label.endsWith("_index") || label.endsWith("_alignment") -> "Status Lines"
        label.contains("font") || label == "input_output_size" -> "Text And Font"
        label.contains("margin") || label.contains("duo") || label.contains("fullscreen") || label.contains("wallpaper") -> "Layout"
        label.contains("notes") -> "Notes"
        label.contains("weather") || label.contains("unlock") -> "Weather And Unlock"
        label.contains("dashed") || label.contains("corner_radius") || label.contains("header") || label.contains("shadow") -> "Chrome"
        else -> "General"
    }

    private fun behaviorSection(label: String): String = when {
        label.contains("notification") -> "Notifications"
        label.contains("weather") || label.contains("location") -> "Weather"
        label.contains("unlock") -> "Unlock Counter"
        label.contains("music") || label.contains("module") || label.contains("pomodoro") || label.contains("events") -> "Modules"
        label.contains("format") || label.contains("separator") || label == "not_available_text" -> "Formats"
        label.contains("alias") || label.contains("cmd") || label.contains("command") || label.contains("shell") -> "Commands"
        label.contains("path") || label.contains("storage") || label.contains("backend") -> "Files And Paths"
        label.contains("click") || label.contains("tap") || label.contains("keyboard") || label.contains("swipe") || label.contains("button") -> "Input"
        else -> "Terminal Behavior"
    }

    private fun suggestionsSection(label: String): String = when {
        label.contains("_text_color") || label.contains("_background_color") -> {
            if (label.startsWith("default_")) "Default Styling" else "Category Styling"
        }
        label.contains("order") -> "Ordering"
        label.contains("algorithm") || label.contains("deadline") || label.contains("quickcompare") ||
            label.contains("per_category") || label.contains("min_command_priority") -> "Performance"
        else -> "Display"
    }

    private fun notificationSection(label: String): String = when {
        label.contains("format") || label.contains("color") -> "Display"
        label.contains("popup") || label.contains("click") -> "Actions"
        else -> "Enablement"
    }

    private fun rssSection(label: String): String = when {
        label.contains("download") -> "Download Messages"
        label.contains("hidden") || label.contains("include") -> "Filtering"
        label.contains("click") -> "Actions"
        else -> "Item Display"
    }

    private fun migrateRenamedUiValue(
        d: Document,
        root: Element?,
        oldName: String?,
        newName: String?
    ): Boolean {
        val oldElement = findDirectChild(root, oldName)
        if (oldElement == null) {
            return false
        }

        var newElement = findDirectChild(root, newName)
        if (newElement == null) {
            var value = Ui.display_margin_top_section.defaultValue()
            try {
                val oldValue = oldElement.getAttribute(VALUE_ATTRIBUTE)
                if (oldValue != null && oldValue.length > 0) {
                    value = oldValue
                }
            } catch (ignored: Exception) {
            }

            newElement = d.createElement(newName)
            newElement.setAttribute(VALUE_ATTRIBUTE, value)
            root!!.appendChild(newElement)
        }
        root!!.removeChild(oldElement)
        return true
    }

    private fun findDirectChild(root: Element?, name: String?): Element? {
        if (root == null) {
            return null
        }

        val nodes = root.getElementsByTagName(name)
        for (count in 0..<nodes.getLength()) {
            val node = nodes.item(count)
            if (node is Element && node.getParentNode() === root) {
                return node
            }
        }
        return null
    }

    @Throws(Exception::class)
    fun transform(s: String, c: Class<*>?): Any? {
        if (s == null) throw UnsupportedOperationException()

        if (c == Int::class.javaPrimitiveType) return s.toInt()
        if (c == Color::class.java) {
            try {
                return Color.parseColor(s)
            } catch (e: Exception) {
                return Color.WHITE
            }
        }
        if (c == Boolean::class.javaPrimitiveType) return s.toBoolean()
        if (c == String::class.java) return s
        if (c == Float::class.javaPrimitiveType) return s.toFloat()
        if (c == Double::class.javaPrimitiveType) return s.toDouble()
        if (c == File::class.java) {
            if (s.length == 0) return null

            val file = File(s)
            if (!file.exists()) throw UnsupportedOperationException()

            return file
        }

        return Tuils.getDefaultValue(c)
    }

    fun getFloat(prefsSave: XMLPrefsSave?): Float {
        return XMLPrefsManager.get(Float::class.javaPrimitiveType, prefsSave) ?: 0f
    }

    fun getDouble(prefsSave: XMLPrefsSave?): Double {
        return XMLPrefsManager.get(Double::class.javaPrimitiveType, prefsSave) ?: 0.0
    }

    fun getInt(prefsSave: XMLPrefsSave?): Int {
        return XMLPrefsManager.get(Int::class.javaPrimitiveType, prefsSave) ?: 0
    }

    fun getBoolean(prefsSave: XMLPrefsSave?): Boolean {
        return XMLPrefsManager.get(Boolean::class.javaPrimitiveType, prefsSave) ?: false
    }

    fun getColor(prefsSave: XMLPrefsSave): Int {
        if (prefsSave.parent() == null) return Int.Companion.MAX_VALUE

        var color: Int
        try {
            color = transform(
                prefsSave.parent()!!.getValues()!!.get(prefsSave)!!.value,
                Color::class.java
            ) as Int
        } catch (e: Exception) {
            val def = prefsSave.defaultValue()
            if (def == null || def.length == 0) {
                return Int.Companion.MAX_VALUE
            }

            try {
                color = transform(def, Color::class.java) as Int
            } catch (e1: Exception) {
                return Int.Companion.MAX_VALUE
            }
        }

        return getColor(prefsSave, color)
    }

    fun getString(prefsSave: XMLPrefsSave?): String {
        return get(prefsSave)
    }

    fun <T> get(c: Class<T>?, root: XMLPrefsRoot, s: String?): T? {
        try {
            val entry = root.getValues()!!.get(s)
            if (entry == null) return null
            return transform(entry.value, c) as T?
        } catch (e: Exception) {
            return null
        }
    }

    fun <T> get(c: Class<T>?, prefsSave: XMLPrefsSave?): T? {
        try {
            val entry = prefsSave!!.parent()!!.getValues()!!.get(prefsSave)
            if (entry == null) throw NullPointerException("Entry not found for " + prefsSave.label())
            return transform(entry.value, c) as T?
        } catch (e: Exception) {
            try {
                return XMLPrefsManager.transform(prefsSave!!.defaultValue()!!, c) as T?
            } catch (e1: Exception) {
                Tuils.log("XMLPrefsManager.get error for " + (if (prefsSave != null) prefsSave.label() else "null") + ": " + e.message)
                return Tuils.getDefaultValue(c)
            }
        }
    }

    fun get(prefsSave: XMLPrefsSave?): String {
        return XMLPrefsManager.get(String::class.java, prefsSave)!!
    }

    fun get(root: XMLPrefsRoot, s: String?): String? {
        return XMLPrefsManager.get(String::class.java, root, s)
    }

    private val LIST_DECORATION: Pattern = Pattern.compile("[\\[\\]\\s]")

    fun getListOfIntValues(values: String, length: Int, defaultValue: Int): IntArray {
        var values = values
        val parsed = IntArray(length)
        values = removeListDecoration(values)
        val split: Array<String?> =
            values.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var count = 0
        while (count < split.size) {
            try {
                parsed[count] = split[count]!!.toInt()
            } catch (e: Exception) {
                parsed[count] = defaultValue
            }
            count++
        }
        while (count < split.size) parsed[count] = defaultValue

        return parsed
    }

    fun getListOfStringValues(values: String, length: Int, defaultValue: String?): Array<String?> {
        val parsed = arrayOfNulls<String>(length)
        val split: Array<String?> =
            values.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        var len = min(split.size, parsed.size)
        System.arraycopy(split, 0, parsed, 0, len)

        while (len < parsed.size) parsed[len++] = defaultValue

        return parsed
    }

    private fun removeListDecoration(value: String): String {
        return LIST_DECORATION.matcher(value).replaceAll(Tuils.EMPTYSTRING)
    }

    fun wasChanged(save: XMLPrefsSave, allowLengthZero: Boolean): Boolean {
        val value = get(save)
        return (allowLengthZero || value.length > 0) && value != save.defaultValue()
    }

    val p1: Pattern = Pattern.compile(">")

    //    static final Pattern p2 = Pattern.compile("</");
    val p3: Pattern = Pattern.compile("\n\n")
    val p1s: String = ">" + Tuils.NEWLINE

    //    static final String p2s = "\n</";
    val p3s: String = Tuils.NEWLINE

    fun fixNewlines(s: String): String {
        var s = s
        s = p1.matcher(s).replaceAll(p1s)
        //        s = p2.matcher(s).replaceAll(p2s);
        s = p3.matcher(s).replaceAll(p3s)
        return s
    }

    //    rootName is needed in order to rebuild the file if it's corrupted
    //    [0] = document
    //    [1] = root
    @Throws(Exception::class)
    fun buildDocument(file: File?, rootName: String?): Array<Any?>? {
        if (file == null) {
            return null
        }
        if (!file.exists()) {
            resetFile(file, rootName)
        }

        var d: Document
        try {
            d = builder!!.parse(file)
        } catch (e: Exception) {
            Tuils.log(e)

            val nOfBytes = Tuils.nOfBytes(file)
            if (nOfBytes == 0 && rootName != null) {
                resetFile(file, rootName)
                d = builder!!.parse(file)
            } else return null
        }

        val r = d.getDocumentElement()
        return arrayOf<Any?>(d, r)
    }

    fun writeTo(d: Document?, f: File?) {
        try {
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()

            val source = DOMSource(d)
            val writer = StringWriter()
            val result = StreamResult(writer)
            transformer.transform(source, result)

            val s = fixNewlines(writer.toString())

            val stream = FileOutputStream(f)
            stream.write(s.toByteArray())

            stream.flush()
            stream.close()
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    //    this will only add, it won't check if there's already one
    fun add(
        file: File?,
        elementName: String?,
        attributeNames: Array<out String?>,
        attributeValues: Array<out String?>
    ): String? {
        try {
            val o: Array<Any?>?
            try {
                o = buildDocument(file, null)
                if (o == null) return Tuils.EMPTYSTRING
            } catch (e: Exception) {
                Tuils.log(e)
                return e.toString()
            }

            val d = o[0] as Document
            val root = o[1] as Element

            val element = d.createElement(elementName)
            for (c in attributeNames.indices) {
                val attributeName = attributeNames[c] ?: continue
                val attributeValue = attributeValues[c] ?: continue
                element.setAttribute(attributeName, attributeValue)
            }
            root.appendChild(element)

            writeTo(d, file)
        } catch (e: Exception) {
            Tuils.log(e)
            return e.toString()
        }
        return null
    }

    fun set(
        file: File?,
        elementName: String?,
        attributeNames: Array<out String?>,
        attributeValues: Array<out String?>
    ): String? {
        return set(file, elementName, null, null, attributeNames, attributeValues, true)
    }

    fun set(
        file: File?,
        elementName: String?,
        thatHasThose: Array<out String?>?,
        forValues: Array<out String?>?,
        attributeNames: Array<out String?>,
        attributeValues: Array<out String?>,
        addIfNotFound: Boolean
    ): String? {
        val values = Array<Array<out String?>?>(1) { arrayOfNulls<String>(attributeValues.size) }
        values[0] = attributeValues

        return setMany(
            file,
            arrayOf<String?>(elementName),
            thatHasThose,
            forValues,
            attributeNames,
            values,
            addIfNotFound
        )
    }

    fun setMany(
        file: File?,
        elementNames: Array<String?>?,
        attributeNames: Array<out String?>,
        attributeValues: Array<out Array<out String?>?>
    ): String? {
        return setMany(file, elementNames, null, null, attributeNames, attributeValues, true)
    }

    fun setMany(
        file: File?,
        elementNames: Array<String?>?,
        thatHasThose: Array<out String?>?,
        forValues: Array<out String?>?,
        attributeNames: Array<out String?>,
        attributeValues: Array<out Array<out String?>?>,
        addIfNotFound: Boolean
    ): String? {
        try {
            val o: Array<Any?>?
            try {
                o = buildDocument(file, null)
                if (o == null) return Tuils.EMPTYSTRING
            } catch (e: Exception) {
                Tuils.log(e)
                return e.toString()
            }

            val d = o[0] as Document?
            val root = o[1] as Element?

            if (d == null || root == null) {
                return Tuils.EMPTYSTRING
            }

            var nFound = 0

            Main@ for (c in elementNames!!.indices) {
                val nodes = root.getElementsByTagName(elementNames[c])

                Nodes@ for (j in 0..<nodes.getLength()) {
                    val n = nodes.item(j)
                    if (n.getNodeType() == Node.ELEMENT_NODE) {
                        val e = n as Element

                        if (!checkAttributes(e, thatHasThose, forValues, false)) {
                            continue@Nodes
                        }

                        nFound++

                        for (a in attributeNames.indices) {
                            val attributeName = attributeNames[a] ?: continue
                            val attributeValue = attributeValues[c]!![a] ?: continue
                            e.setAttribute(attributeName, attributeValue)
                        }

                        elementNames[c] = null

                        continue@Main
                    }
                }
            }

            if (nFound < elementNames.size) {
                for (count in elementNames.indices) {
                    if (elementNames[count] == null || elementNames[count]!!.length == 0) continue

                    if (!addIfNotFound) continue

                    val element = d.createElement(elementNames[count])
                    for (c in attributeNames.indices) {
                        val attributeName = attributeNames[c] ?: continue
                        val attributeValue = attributeValues[count]!![c] ?: continue
                        element.setAttribute(attributeName, attributeValue)
                    }
                    root.appendChild(element)
                }
            }

            writeTo(d, file)

            if (nFound == 0) return Tuils.EMPTYSTRING
            return null
        } catch (e: Exception) {
            Tuils.log(e)
            Tuils.toFile(e)
            return e.toString()
        }
    }

    //    return "" if node not found, null if all good
    @JvmOverloads
    fun removeNode(
        file: File?,
        nodeName: String?,
        thatHasThose: Array<out String?>? = null,
        forValues: Array<out String?>? = null
    ): String? {
        try {
            val o: Array<Any?>?
            try {
                o = buildDocument(file, null)
                if (o == null) return Tuils.EMPTYSTRING
            } catch (e: Exception) {
                return e.toString()
            }

            val d = o[0] as Document?
            val root = o[1] as Element

            val n = findNode(root, nodeName, thatHasThose, forValues)
            if (n == null) return Tuils.EMPTYSTRING

            root.removeChild(n)
            writeTo(d, file)

            return null
        } catch (e: Exception) {
            return e.toString()
        }
    }

    @JvmOverloads
    fun removeNode(
        file: File?,
        thatHasThose: Array<String?>?,
        forValues: Array<String?>?,
        alsoNotFound: Boolean = false,
        all: Boolean = false
    ): String? {
        try {
            val o: Array<Any?>?
            try {
                o = buildDocument(file, null)
                if (o == null) return Tuils.EMPTYSTRING
            } catch (e: Exception) {
                return e.toString()
            }

            val d = o[0] as Document?
            val root = o[1] as Element

            val list = root.getElementsByTagName("*")

            var check = false

            for (c in 0..<list.getLength()) {
                val n = list.item(c)

                if (n !is Element) continue
                val e = n

                if (checkAttributes(e, thatHasThose, forValues, alsoNotFound)) {
                    check = true
                    root.removeChild(n)
                    if (!all) break
                }
            }

            writeTo(d, file)

            return if (check) null else Tuils.EMPTYSTRING
        } catch (e: Exception) {
            return e.toString()
        }
    }

    @JvmOverloads
    fun findNode(
        file: File,
        nodeName: String?,
        thatHasThose: Array<String?>? = null,
        forValues: Array<String?>? = null
    ): Node? {
        try {
            val o: Array<Any?>?
            try {
                o = buildDocument(file, null)
                if (o == null) return null
            } catch (e: Exception) {
                return null
            }

            val root = o[1] as Element

            return findNode(root, nodeName, thatHasThose, forValues)
        } catch (e: Exception) {
            return null
        }
    }

    //    useful only if you're looking for a single node
    @JvmOverloads
    fun findNode(
        root: Element?,
        nodeName: String?,
        thatHasThose: Array<out String?>? = null,
        forValues: Array<out String?>? = null
    ): Node? {
        if (root == null) {
            return null
        }
        val nodes = root.getElementsByTagName(nodeName)
        for (j in 0..<nodes.getLength()) if (XMLPrefsManager.checkAttributes(
                (nodes.item(j) as org.w3c.dom.Element?)!!,
                thatHasThose,
                forValues,
                false
            )
        ) return nodes.item(j)
        return null
    }

    fun findNodes(
        root: Element,
        nodeName: String?,
        thatHasThose: Array<String?>?,
        forValue: Array<String?>?
    ): MutableList<Node?> {
        val nodes = root.getElementsByTagName(if (nodeName != null) nodeName else "*")

        val nodeList: MutableList<Node?> = ArrayList<Node?>()

        for (c in 0..<nodes.getLength()) {
            val n = nodeList.get(c)

            if (n !is Element) continue
            val e = n

            if (checkAttributes(e, thatHasThose, forValue, false)) {
                nodeList.add(n)
            }
        }

        return nodeList
    }

    fun findNodes(
        root: Element,
        thatHasThose: Array<String?>?,
        forValue: Array<String?>?
    ): MutableList<Node?> {
        return findNodes(root, null, thatHasThose, forValue)
    }

    fun attrValue(file: File, nodeName: String?, attrName: String?): String? {
        return attrValue(file, nodeName, null, null, attrName)
    }

    fun attrValue(
        file: File,
        nodeName: String?,
        thatHasThose: Array<String?>?,
        forValues: Array<String?>?,
        attrName: String?
    ): String? {
        val vs = attrValues(file, nodeName, thatHasThose, forValues, arrayOf<String?>(attrName))
        if (vs != null && vs.size > 0) return vs[0]
        return null
    }

    fun attrValues(file: File, nodeName: String?, attrNames: Array<String?>): Array<String?>? {
        return attrValues(file, nodeName, null, null, attrNames)
    }

    fun attrValues(
        file: File,
        nodeName: String?,
        thatHasThose: Array<String?>?,
        forValues: Array<String?>?,
        attrNames: Array<String?>
    ): Array<String?>? {
        try {
            val o: Array<Any?>?
            try {
                o = buildDocument(file, null)
                if (o == null) return null
            } catch (e: Exception) {
                return null
            }

            val root = o[1] as Element
            val nodes = root.getElementsByTagName(nodeName)

            for (count in 0..<nodes.getLength()) {
                val node = nodes.item(count)
                val e = node as Element

                if (!checkAttributes(e, thatHasThose, forValues, false)) continue

                val values = arrayOfNulls<String>(attrNames.size)
                for (c in attrNames.indices) values[count] = e.getAttribute(attrNames[c])

                return values
            }
        } catch (e: Exception) {
        }

        return null
    }

    private fun checkAttributes(
        e: Element,
        thatHasThose: Array<out String?>?,
        forValues: Array<out String?>?,
        alsoIfAttributeNotFound: Boolean
    ): Boolean {
        if (thatHasThose != null && forValues != null && thatHasThose.size == forValues.size) {
            for (a in thatHasThose.indices) {
                val attributeName = thatHasThose[a] ?: return alsoIfAttributeNotFound
                if (!e.hasAttribute(attributeName)) return alsoIfAttributeNotFound
                if (forValues[a] != e.getAttribute(attributeName)) return false
            }
        }
        return true
    }

    fun resetFile(f: File?, name: String?): Boolean {
        if (f == null) {
            return false
        }
        try {
            if (f.exists()) f.delete()

            val stream = FileOutputStream(f)
            stream.write(XML_DEFAULT.toByteArray())
            stream.write(("<" + name + ">\n").toByteArray())
            stream.write(("</" + name + ">\n").toByteArray())
            stream.flush()
            stream.close()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun getStringAttribute(e: Element, attribute: String?): String? {
        return if (e.hasAttribute(attribute)) e.getAttribute(attribute) else null
    }

    fun getLongAttribute(e: Element, attribute: String?): Long {
        val value = getStringAttribute(e, attribute)
        try {
            return value!!.toLong()
        } catch (ex: Exception) {
            return -1
        }
    }

    fun getBooleanAttribute(e: Element, attribute: String?): Boolean {
        val s = getStringAttribute(e, attribute)
        return s != null && s.toBoolean()
    }

    fun getIntAttribute(e: Element, attribute: String?): Int {
        try {
            return getStringAttribute(e, attribute)!!.toInt()
        } catch (ex: Exception) {
            return -1
        }
    }

    fun getFloatAttribute(e: Element, attribute: String?): Float {
        try {
            return getStringAttribute(e, attribute)!!.toFloat()
        } catch (ex: Exception) {
            return -1f
        }
    }

    enum class XMLPrefsRoot(en: Array<out XMLPrefsSave>) : XMLPrefsElement {
        THEME(Theme.entries.toTypedArray()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>(
                    "input_color",
                    "output_color",
                    "bg_color",
                    "device_color",
                    "ascii_color",
                    "time_color",
                    "storage_color",
                    "ram_color",
                    "network_info_color",
                    "toolbar_bg",
                    "toolbar_color",
                    "enter_color",
                    "overlay_color",
                    "alias_content_color",
                    "app_installed_color",
                    "app_uninstalled_color",
                    "mark_color",
                    "notes_color",
                    "notes_locked_color",
                    "link_color",
                    "restart_message_color",
                    "weather_color",
                    "unlock_counter_color",
                    "session_info_color",
                    "input_bg",
                    "output_bg",
                    "suggestions_bg",
                    "input_shadow_color",
                    "output_shadow_color",
                    "apps_drawer_color",
                    "dashed_border_color",
                    "module_button_bg_color",
                    "module_name_text_color",
                    "window_terminal_bg",
                    "terminal_header_bg",
                    "statusbar_color",
                    "navigationbar_color",
                    "hint_color",
                    "status_lines_bgrectcolor",
                    "input_bgrectcolor",
                    "output_bgrectcolor",
                    "toolbar_bgrectcolor",
                    "suggestions_bgrectcolor",
                    "status_lines_bg",
                    "status_lines_shadow_color",
                    "module_button_border_color"
                )
            }
        },
        CMD(Cmd.values()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>()
            }
        },
        TOOLBAR(Toolbar.values()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>()
            }
        },
        UI(Ui.entries.toTypedArray()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>(
                    "status_lines_alignment",
                    "ignore_bar_color",
                    "bgrect_params"
                )
            }
        },
        BEHAVIOR(Behavior.entries.toTypedArray()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>(
                    "time_format",
                    "show_hints",
                    "enable_app_launch",
                    "network_info_update_ms",
                    "htmlextractor_notfound_message",
                    "tui_notification_time_color",
                    "tui_notification_input_color"
                )
            }
        },
        SUGGESTIONS(Suggestions.entries.toTypedArray()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>(
                    "app_suggestions_minrate",
                    "contact_suggestions_minrate",
                    "song_suggestions_minrate",
                    "file_suggestions_minrate",
                    "default_bg_color",
                    "apps_bg_color",
                    "alias_bg_color",
                    "cmd_bg_color",
                    "song_bg_color",
                    "contact_bg_color",
                    "file_bg_color"
                )
            }
        },
        NOTIFICATIONS(Notifications.values()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>("default_notification_color")
            }
        },
        APPS(Apps.values()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>()
            }
        },
        RSS(Rss.values()) {
            override fun delete(): Array<String?>? {
                return arrayOf<String?>(
                    "rss_default_color",
                    "rss_download_message_color"
                )
            }
        }; //        notifications
        //        apps
        //        alias

        var path: String
        private var prefsValues: XMLPrefsList?
        var enums: MutableList<XMLPrefsSave>

        init {
            this.prefsValues = XMLPrefsList()

            this.enums = ArrayList<XMLPrefsSave>(Arrays.asList(*en))
            this.path = this.name.lowercase(Locale.getDefault()) + ".xml"
        }

        override fun write(save: XMLPrefsSave, value: String) {
            if (prefsValues == null) {
                prefsValues = XMLPrefsList()
            }
            val f = File(Tuils.getFolder(), path)
            XMLPrefsManager.set(
                f,
                save.label(),
                arrayOf<String>(VALUE_ATTRIBUTE),
                arrayOf<String?>(value)
            )
            prefsValues!!.add(save.label()!!, value)
        }

        override fun getValues(): XMLPrefsList? {
            if (this === XMLPrefsRoot.APPS && AppsManager.instance != null) {
                return AppsManager.instance!!.getValues()
            }
            if (prefsValues == null) {
                prefsValues = XMLPrefsList()
            }
            return prefsValues
        }

        override fun path(): String {
            return path
        }
    }

    class IdValue(
        var value: String?,
        var id: Int
    )  //    private static HashMap<XMLPrefsSave, String> getOld(BufferedReader reader) {
    //        HashMap<XMLPrefsSave, String> map = new HashMap<>();
    //
    //        String line;
    //        try {
    //            while((line = reader.readLine()) != null) {
    //                String[] split = line.split("=");
    //                if(split.length != 2) continue;
    //
    //                String name = split[0].trim();
    //                String value = split[1];
    //
    //                XMLPrefsSave s = getCorresponding(name);
    //                if(s == null) continue;
    //
    //                map.put(s, value);
    //            }
    //        } catch (IOException e) {
    //            return null;
    //        }
    //
    //        return map;
    //    }
    //    static final SimpleMutableEntry[] OLD = {
    //            new SimpleMutableEntry("deviceColor", Theme.device_text_color),
    //            new SimpleMutableEntry("inputColor", Theme.input_text_color),
    //            new SimpleMutableEntry("outputColor", Theme.output_text_color),
    //            new SimpleMutableEntry("backgroundColor", Theme.background_color),
    //            new SimpleMutableEntry("useSystemFont", Ui.system_font),
    //            new SimpleMutableEntry("fontSize", Ui.font_size),
    //            new SimpleMutableEntry("ramColor", Theme.ram_text_color),
    //            new SimpleMutableEntry("username", Ui.username),
    //            new SimpleMutableEntry("showSubmit", Ui.show_enter_button),
    //            new SimpleMutableEntry("deviceName", Ui.deviceName),
    //            new SimpleMutableEntry("showRam", Ui.show_ram),
    //            new SimpleMutableEntry("showDevice", Ui.show_device_name),
    //            new SimpleMutableEntry("showToolbar", Toolbar.show_toolbar),
    //
    //            new SimpleMutableEntry("suggestionTextColor", Suggestions.default_text_color),
    //            new SimpleMutableEntry("transparentSuggestions", Suggestions.transparent),
    //            new SimpleMutableEntry("aliasSuggestionBg", Suggestions.alias_background_color),
    //            new SimpleMutableEntry("appSuggestionBg", Suggestions.apps_background_color),
    //            new SimpleMutableEntry("commandSuggestionsBg", Suggestions.cmd_background_color),
    //            new SimpleMutableEntry("songSuggestionBg", Suggestions.song_background_color),
    //            new SimpleMutableEntry("contactSuggestionBg", Suggestions.contact_background_color),
    //            new SimpleMutableEntry("fileSuggestionBg", Suggestions.file_background_color),
    //            new SimpleMutableEntry("defaultSuggestionBg", Suggestions.default_background_color),
    //
    //            new SimpleMutableEntry("useSystemWallpaper", Ui.system_wallpaper),
    //            new SimpleMutableEntry("fullscreen", Ui.fullscreen),
    //            new SimpleMutableEntry("keepAliveWithNotification", Behavior.tui_notification),
    //            new SimpleMutableEntry("openKeyboardOnStart", Behavior.auto_show_keyboard),
    //
    //            new SimpleMutableEntry("fromMediastore", Behavior.songs_from_mediastore),
    //            new SimpleMutableEntry("playRandom", Behavior.random_play),
    //            new SimpleMutableEntry("songsFolder", Behavior.songs_folder),
    //
    //            new SimpleMutableEntry("closeOnDbTap", Behavior.double_tap_closes),
    //            new SimpleMutableEntry("showSuggestions", Suggestions.show_suggestions),
    //            new SimpleMutableEntry("showDonationMessage", Behavior.donation_message),
    //            new SimpleMutableEntry("showAliasValue", Behavior.show_alias_content),
    //            new SimpleMutableEntry("showAppsHistory", Behavior.show_launch_history),
    //
    //            new SimpleMutableEntry("defaultSearch", Cmd.default_search)
    //    };
    //
    //    private static XMLPrefsSave getCorresponding(String old) {
    //        for(SimpleMutableEntry<String, XMLPrefsSave> s : OLD) {
    //            if(old.equals(s.getKey())) return s.getValue();
    //        }
    //        return null;
    //    }
}
