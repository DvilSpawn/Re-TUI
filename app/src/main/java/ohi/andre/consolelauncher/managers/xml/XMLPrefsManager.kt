package ohi.andre.consolelauncher.managers.xml

import android.content.Context
import android.graphics.Color
import android.util.AtomicFile
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.FirstRunPresetManager
import ohi.andre.consolelauncher.managers.onboarding.StartupMenuManager
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
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.settings.StatusRowResolver
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.io.IOException
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

    /** Invalidate the disk view without throwing away the last working values. */
    fun dispose() { commonsLoaded = false }

    @Volatile var commonsLoaded: Boolean = false
    @Volatile var lastLoadError: String? = null
        private set
    @Volatile var validationWarnings: List<String> = emptyList()
        private set
    internal var settingsContext: Context? = null
        private set

    fun loadCommons(context: Context?): Boolean = loadCandidate(context, emptyMap())

    fun saveRawSettings(context: Context, file: File, text: String) = synchronized(
        ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
        val root = XMLPrefsRoot.entries.firstOrNull { it.path == file.name }
            ?: throw IOException("Unknown settings file: ${file.name}")
        require(file.canonicalFile == File(Tuils.getFolder(), root.path).canonicalFile) { "Invalid settings path" }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(text.byteInputStream())
        require(document.documentElement.tagName == root.name) { "Unexpected root in ${file.name}" }
        val nodes = document.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val setting = root.enums.firstOrNull { it.label() == node.tagName } ?: continue
            val value = node.getAttribute(VALUE_ATTRIBUTE)
            if (value != ohi.andre.consolelauncher.managers.settings.LauncherSettings.get(setting))
                require(ohi.andre.consolelauncher.managers.settings.SettingsValidation.error(setting, value) == null) {
                    "Invalid setting: ${setting.label()}"
                }
        }
        val previous = ohi.andre.consolelauncher.managers.settings.LauncherSettings.currentSnapshot()
        val wasLoaded = commonsLoaded
        commonsLoaded = false
        if (!loadCandidate(context, mapOf(file.canonicalFile to text))) {
            commonsLoaded = wasLoaded
            throw IOException(lastLoadError ?: "Unable to save settings")
        }
        val current = ohi.andre.consolelauncher.managers.settings.LauncherSettings.currentSnapshot()
        val changed = current.values.keys.filterTo(linkedSetOf()) { previous.values[it] != current.values[it] }
        ohi.andre.consolelauncher.managers.settings.SettingsRefreshCoordinator.request(context,
            ohi.andre.consolelauncher.managers.settings.LauncherSettings.Change(previous, current, changed))
    }

    private fun loadCandidate(context: Context?, overrides: Map<File, String>): Boolean = synchronized(
        ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
        if (context == null) return@synchronized false
        var reading = "settings"
        var staging: File? = null
        try {
            settingsContext = context.applicationContext
            Tuils.init(context)
            ohi.andre.consolelauncher.managers.BackupManager.recoverInterruptedRestore(context)
            ohi.andre.consolelauncher.managers.settings.SettingsFiles.recover(settingsContext)
            init(context)
            if (commonsLoaded) return@synchronized true

            val folder = Tuils.getFolder()
            val existing = XMLPrefsRoot.entries.any { File(folder, it.path).isFile }
            StartupMenuManager.initialize(context, existing)
            FirstRunPresetManager.seedIfNeeded(context, folder)
            val candidates = linkedMapOf<XMLPrefsRoot, ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList>()
            val replacements = linkedMapOf<File, File?>()
            val migrationSources = arrayListOf<File>()
            val warnings = arrayListOf<String>()
            val prepared = File.createTempFile(".settings-load-", ".tmp", folder)
            staging = prepared
            check(prepared.delete() && prepared.mkdir()) { "Unable to prepare settings" }
            for (element in XMLPrefsRoot.entries) {
                if (element === XMLPrefsRoot.APPS) continue
                reading = element.path
                val file = File(folder, element.path)
                val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                val exists = file.exists() || File(file.path + ".bak").exists()
                val override = overrides[file.canonicalFile]
                val d = if (override != null) override.byteInputStream().use { parser.parse(it) }
                    else if (exists) AtomicFile(file).openRead().use { parser.parse(it) }
                    else parser.newDocument().also { it.appendChild(it.createElement(element.name)) }
                val root = d.documentElement
                require(root != null && root.tagName == element.name) { "Unexpected root in ${element.path}" }
                val migrated = migrateRootValues(d, root, element)
                if (migrated && exists) migrationSources.add(file)
                var changed = !exists || migrated || override != null
                changed = ensureDefaultSectionComments(d, root, element) || changed
                val values = ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList()
                val missing = element.enums.toMutableList()
                val deleted = element.delete().orEmpty().toSet()
                // Iterate a copy: removal from the live NodeList would skip the next entry.
                val nodes = (0 until root.childNodes.length).map { root.childNodes.item(it) }
                for (node in nodes) {
                    if (node !is Element || !node.hasAttribute(VALUE_ATTRIBUTE)) continue
                    val setting = missing.firstOrNull { it.label() == node.tagName }
                    if (setting != null) {
                        missing.remove(setting)
                        val raw = node.getAttribute(VALUE_ATTRIBUTE)
                        values.add(node.tagName, raw)
                        if (ohi.andre.consolelauncher.managers.settings.SettingsValidation.error(setting, raw) != null)
                            warnings.add("${element.path}:${setting.label()}")
                    } else if (node.tagName in deleted) {
                        root.removeChild(node); changed = true
                    }
                }
                if (missing.isNotEmpty()) {
                    appendMissingPrefs(d, root, element, missing, values)
                    changed = true
                }
                if (element === XMLPrefsRoot.UI) {
                    val raw = StatusRowResolver.settings.associateWith { values[it]?.value }
                    if (raw.all { (setting, value) -> ohi.andre.consolelauncher.managers.settings.SettingsValidation.error(setting, value) == null }) {
                        val normalized = StatusRowResolver.normalize(raw)
                        if (normalized.changed) {
                            for ((setting, value) in normalized.values) {
                                findDirectChild(root, setting.label()!!)!!.setAttribute(VALUE_ATTRIBUTE, value)
                                values.add(setting.label(), value)
                            }
                            changed = true
                        }
                    }
                }
                candidates[element] = values
                if (changed) {
                    val staged = File(prepared, element.path)
                    writeTo(d, staged)
                    replacements[file] = staged
                }
            }
            // Do not create migration copies until every input file has been parsed successfully.
            migrationSources.forEach(::preserveMigrationSource)
            ohi.andre.consolelauncher.managers.settings.SettingsFiles.replace(settingsContext, replacements) {
                candidates.forEach { (element, values) -> element.publishValues(values) }
                commonsLoaded = true
                lastLoadError = null
                validationWarnings = warnings.toList()
                ohi.andre.consolelauncher.managers.settings.LauncherSettings.refreshFromLoadedPrefs()
            }
            true
        } catch (error: Exception) {
            lastLoadError = "$reading: ${error.message ?: error.javaClass.simpleName}"
            Tuils.log(error)
            Tuils.sendXMLParseError(context, reading)
            false
        } finally { staging?.deleteRecursively() }
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

    private fun renamesFor(element: XMLPrefsRoot): Array<Pair<String, String>> = when (element) {
        XMLPrefsRoot.THEME -> THEME_RENAMES
        XMLPrefsRoot.SUGGESTIONS -> SUGGESTION_RENAMES
        XMLPrefsRoot.NOTIFICATIONS -> NOTIFICATION_RENAMES
        XMLPrefsRoot.RSS -> RSS_RENAMES
        XMLPrefsRoot.BEHAVIOR -> BEHAVIOR_RENAMES
        else -> emptyArray()
    }

    internal fun canonicalSettingLabel(element: XMLPrefsRoot, label: String): String =
        renamesFor(element).firstOrNull { it.first == label }?.second ?: label

    internal fun migrateRootValues(d: Document, root: Element?, element: XMLPrefsRoot): Boolean {
        var changed = false
        if (root == null) {
            return false
        }

        val singletonNames = element.enums.mapNotNull { it.label() }.toSet() +
            renamesFor(element).map { it.first } + setOf("time_format", "status_lines_bg", "status_lines_shadow_color", "status_lines_alignment")
        val seen = HashMap<String, String>()
        val duplicates = ArrayList<Node>()
        for (index in 0 until root.childNodes.length) {
            val node = root.childNodes.item(index) as? Element ?: continue
            if (!node.hasAttribute(VALUE_ATTRIBUTE) || node.tagName !in singletonNames) continue
            // Notification filters and per-app formats are repeatable records, not singleton options.
            if (element === XMLPrefsRoot.NOTIFICATIONS && (node.tagName == "filter" || node.tagName == "format")) continue
            val value = node.getAttribute(VALUE_ATTRIBUTE)
            val previous = seen.put(node.tagName, value)
            require(previous == null || previous == value) { "Conflicting setting: ${node.tagName}" }
            if (previous != null) duplicates.add(node)
        }
        duplicates.forEach { root.removeChild(it); changed = true }
        if (element === XMLPrefsRoot.BEHAVIOR) {
            findDirectChild(root, "time_format")?.let { legacy ->
                for (name in arrayOf("status_time_format", "output_time_format")) {
                    if (findDirectChild(root, name) == null) {
                        root.insertBefore(d.createElement(name).apply {
                            setAttribute(VALUE_ATTRIBUTE, legacy.getAttribute(VALUE_ATTRIBUTE))
                        }, legacy)
                    }
                }
                root.removeChild(legacy)
                changed = true
            }
        }
        if (element === XMLPrefsRoot.UI) {
            changed = renameDirectChild(d, root, "display_margin_mm", "display_margin_top_section") or changed
        }
        for (rename in renamesFor(element)) {
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

    /** Keep the original bytes before the first destructive settings migration. */
    internal fun preserveMigrationSource(file: File) = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
        val backup = File(file.parentFile, file.name + ".pre-migration")
        if (backup.isFile) return
        val temporary = File.createTempFile(file.name + ".migration-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                file.inputStream().use { it.copyTo(output) }
                output.fd.sync()
            }
            check(temporary.renameTo(backup)) { "Cannot preserve original settings: ${file.name}" }
        } finally {
            temporary.delete()
        }
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
        missing: MutableList<XMLPrefsSave>,
        values: XMLPrefsList
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

                values.add(save.label()!!, value!!)
            }
        }
    }

    private fun ensureDefaultSectionComments(d: Document, root: Element?, element: XMLPrefsRoot): Boolean {
        if (root == null || hasAnyComment(root)) {
            return false
        }

        val existing = linkedMapOf<String, MutableList<Node>>()
        val children = root.childNodes
        for (index in 0..<children.length) {
            val node = children.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            existing.getOrPut(node.nodeName) { arrayListOf() }.add(node.cloneNode(true))
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
                val known = existing.remove(label) ?: continue
                nodes.addAll(known)
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

        existing.values.flatten().forEach { root.appendChild(it) }
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
            is SurfaceBorderOption -> "Surface Borders"
            is Ui -> uiSection(label)
            is Behavior -> behaviorSection(label)
            is Suggestions -> suggestionsSection(label)
            is Toolbar -> when {
                label.startsWith("gesture_") -> "Gesture Button"
                label.startsWith("shortcut_button") -> "Custom Shortcuts"
                else -> "Visibility"
            }
            is Notifications -> notificationSection(label)
            is Apps -> if (label.startsWith("default_app_n")) "Default App Suggestions" else "App Drawer"
            is Rss -> rssSection(label)
            is Cmd -> "Commands"
            else -> "General"
        }
    }

    fun isAsciiArtSetting(save: XMLPrefsSave?): Boolean {
        return save === Ui.show_ascii ||
            save === Ui.show_ascii_landscape ||
            save === Ui.ascii_index ||
            save === Ui.ascii_size ||
            save === Ui.ascii_max_lines ||
            save === Ui.ascii_pane_height_rows ||
            save === Ui.ascii_status_alignment ||
            save === Behavior.ascii_animation ||
            save === Behavior.ascii_animation_frame_delay_ms ||
            save === Behavior.ascii_animation_max_file_kb ||
            save === Theme.ascii_text_color ||
            save === Theme.ascii_status_background_color ||
            save === Theme.ascii_status_text_shadow_color
    }

    fun isAdvancedSuggestionSetting(save: XMLPrefsSave?): Boolean {
        return save === Suggestions.suggestions_deadline ||
            save === Suggestions.suggestions_algorithm ||
            save === Suggestions.suggestions_quickcompare_n
    }

    private fun themeSection(label: String): String = when {
        Theme.entries.firstOrNull { it.label() == label }?.advanced == true -> when {
            label.startsWith("ansi_") -> "Advanced — ANSI Palette"
            label.startsWith("app_drawer_") -> "Advanced — App Drawer"
            label.startsWith("module_") -> "Advanced — Modules"
            label.startsWith("widget_") -> "Advanced — Widgets"
            label.startsWith("workspace_") -> "Advanced — Workspaces"
            label.startsWith("settings_") -> "Advanced — Settings"
            label.startsWith("dialog_") || label.startsWith("overlay_") -> "Advanced — Dialogs And Overlays"
            label.startsWith("button_") || label.startsWith("primary_button_") ||
                label.startsWith("icon_button_") || label.startsWith("field_") ||
                label.startsWith("list_") || label.startsWith("toggle_") ||
                label.startsWith("slider_") || label.startsWith("chip_") ||
                label.startsWith("tab_") -> "Advanced — Controls"
            else -> "Advanced — Global States"
        }
        label == "background_color" || label == "wallpaper_overlay_color" ||
            label == "settings_wallpaper_overlay_color" -> "Launcher"
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
        label.contains("ascii") -> "Status Lines"
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
        label == "qalculate" -> "Commands"
        label.contains("sound") -> "Sounds"
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

    fun getColor(prefsSave: XMLPrefsSave): Int = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
        if (prefsSave is Theme) return ThemeColorResolver.color(prefsSave)
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

        return color
    }

    fun getString(prefsSave: XMLPrefsSave?): String {
        return get(prefsSave)
    }

    fun <T> get(c: Class<T>?, root: XMLPrefsRoot, s: String?): T? = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
        try {
            val entry = root.getValues()!!.get(s)
            if (entry == null) return null
            return transform(entry.value, c) as T?
        } catch (e: Exception) {
            return null
        }
    }

    fun <T> get(c: Class<T>?, prefsSave: XMLPrefsSave?): T? = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
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
        val parts = removeListDecoration(values).split(',')
        return IntArray(length) { index -> parts.getOrNull(index)?.toIntOrNull() ?: defaultValue }
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
    fun buildDocument(file: File?, rootName: String?): Array<Any?>? = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
        if (file == null) {
            return null
        }
        if (!file.exists()) {
            resetFile(file, rootName)
        }

        var d: Document
        try {
            d = AtomicFile(file).openRead().use { builder!!.parse(it) }
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

    @Throws(Exception::class)
    fun writeTo(d: Document?, f: File?) = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
        val transformer = TransformerFactory.newInstance().newTransformer()
        val writer = StringWriter()
        transformer.transform(DOMSource(checkNotNull(d)), StreamResult(writer))
        val bytes = fixNewlines(writer.toString()).toByteArray(Charsets.UTF_8)
        val atomic = AtomicFile(checkNotNull(f))
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }

    @Throws(IOException::class)
    fun writeSetting(file: File, save: XMLPrefsSave, value: String) {
        val error = set(file, save.label(), arrayOf(VALUE_ATTRIBUTE), arrayOf(value))
        if (!error.isNullOrEmpty()) throw IOException(error)
    }

    //    this will only add, it won't check if there's already one
    fun add(
        file: File?,
        elementName: String?,
        attributeNames: Array<out String?>,
        attributeValues: Array<out String?>
    ): String? = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
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
    ): String? = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
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
    ): String? = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
        try {
            val o: Array<Any?>?
            try {
                o = buildDocument(file, null)
                if (o == null) return IOException("Unable to read ${file?.name}").toString()
            } catch (e: Exception) {
                Tuils.log(e)
                return e.toString()
            }

            val d = o[0] as Document?
            val root = o[1] as Element?

            if (d == null || root == null) {
                return IOException("Unable to read ${file?.name}").toString()
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
    ): String? = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
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

    fun resetFile(f: File?, name: String?): Boolean = synchronized(ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
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
        UI(arrayOf<XMLPrefsSave>(*Ui.entries.toTypedArray(), *SurfaceBorderOption.entries.toTypedArray())) {
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
                    "show_hints",
                    "enable_app_launch",
                    "network_info_update_ms",
                    "htmlextractor_notfound_message",
                    "tui_notification_time_color",
                    "tui_notification_input_color",
                    "external_storage_path",
                    "file_backend"
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
        internal fun publishValues(values: XMLPrefsList) { prefsValues = values }
        @Volatile private var prefsValues: XMLPrefsList?
        var enums: MutableList<XMLPrefsSave>

        init {
            this.prefsValues = XMLPrefsList()

            this.enums = ArrayList<XMLPrefsSave>(Arrays.asList(*en))
            this.path = this.name.lowercase(Locale.getDefault()) + ".xml"
        }

        override fun write(save: XMLPrefsSave, value: String) = synchronized(
            ohi.andre.consolelauncher.managers.settings.SettingsFiles.lock) {
            if (prefsValues == null) {
                prefsValues = XMLPrefsList()
            }
            val f = File(Tuils.getFolder(), path)
            XMLPrefsManager.writeSetting(f, save, value)
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
    )
}
