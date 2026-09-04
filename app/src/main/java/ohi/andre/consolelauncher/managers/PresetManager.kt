package ohi.andre.consolelauncher.managers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.w3c.dom.Document
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.math.max
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.util.ArrayList
import java.util.HashSet
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Map
import java.util.Set
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.FrameManager

object PresetManager {
    private const val PRESETS_FOLDER = "presets"
    private const val PRESET_PACKAGE_SUFFIX = ".retui-preset"
    private const val MANIFEST_FILE = "manifest.json"
    private val MAX_ENTRY_BYTES = 256 * 1024
    private val BUILT_IN_PRESETS =
        arrayOf<String?>("blue", "red", "green", "pink", "bw", "cyberpunk")
    private val REQUIRED_PRESET_XML_FILES = arrayOf<String>(
        XMLPrefsManager.XMLPrefsRoot.THEME.path,
        XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path
    )
    private val PRESET_XML_FILES = arrayOf<String>(
        *REQUIRED_PRESET_XML_FILES,
        XMLPrefsManager.XMLPrefsRoot.UI.path,
        XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path
    )
    private val SHAREABLE_THEME = Theme.entries.toTypedArray()
    private val SHAREABLE_SUGGESTIONS = Suggestions.entries.toTypedArray()
    private val SHAREABLE_UI = XMLPrefsManager.XMLPrefsRoot.UI.enums.filterNot {
        it == Ui.username || it == Ui.deviceName || it == Ui.font_file || it == Ui.auto_color_pick
    }.toTypedArray()
    private val SHAREABLE_BEHAVIOR = arrayOf(
        Behavior.double_tap_lock,
        Behavior.random_play,
        Behavior.launcher_sounds,
        Behavior.sound_boot,
        Behavior.sound_click,
        Behavior.sound_success,
        Behavior.sound_failure,
        Behavior.sound_notification,
        Behavior.sound_reminder,
        Behavior.sound_timer,
        Behavior.songs_from_mediastore,
        Behavior.tui_notification,
        Behavior.auto_show_keyboard,
        Behavior.search_only_mode,
        Behavior.auto_scroll,
        Behavior.show_alias_content,
        Behavior.show_launch_history,
        Behavior.show_module_dock,
        Behavior.show_tmux_workspace_button,
        Behavior.show_android_widget_drawer_button,
        Behavior.enable_cyberdeck_mode,
        Behavior.enable_crt_filter,
        Behavior.ascii_animation,
        Behavior.ascii_animation_frame_delay_ms,
        Behavior.ascii_animation_max_file_kb,
        Behavior.clear_after_cmds,
        Behavior.clear_input_after_command,
        Behavior.clear_after_seconds,
        Behavior.max_lines,
        Behavior.time_format_separator,
        Behavior.battery_medium,
        Behavior.battery_low,
        Behavior.battery_progress_bar,
        Behavior.battery_progress_bar_symbol,
        Behavior.battery_progress_bar_length,
        Behavior.toggle_output_state,
        Behavior.output_tray_mode,
        Behavior.output_header_mode,
        Behavior.auto_hide_output,
        Behavior.output_auto_hide_seconds,
        Behavior.enable_music,
        Behavior.max_optional_depth,
        Behavior.tui_notification_click_showhome,
        Behavior.tui_notification_lastcmds_size,
        Behavior.tui_notification_lastcmds_updown,
        Behavior.tui_notification_priority,
        Behavior.long_click_vibration_duration,
        Behavior.long_click_duration,
        Behavior.click_commands,
        Behavior.long_click_commands,
        Behavior.append_quote_before_file,
        Behavior.notes_sorting,
        Behavior.notes_allow_link,
        Behavior.orientation,
        Behavior.duo_mode,
        Behavior.tui_notification_time_text_color,
        Behavior.tui_notification_input_text_color,
        Behavior.weather_temperature_measure,
        Behavior.unlock_time_divider,
        Behavior.unlock_time_order,
        Behavior.unlock_counter_cycle_start,
        Behavior.clear_on_lock,
        Behavior.back_button_enabled,
        Behavior.swipe_down_notifications,
        Behavior.weather_update_time,
        Behavior.location_update_mintime,
        Behavior.location_update_mindistance,
        Behavior.show_weather_updates,
        Behavior.swipe_up_apps_drawer,
        Behavior.show_music_widget,
        Behavior.auto_show_music_widget,
        Behavior.pomodoro_focus_minutes,
        Behavior.pomodoro_relax_minutes,
        Behavior.shell_requires_prefix,
        Behavior.events_lookahead_days
    )
    private val COLOR_VALUE = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
    private val DECIMAL_VALUE = Regex("^-?[0-9]{1,6}(\\.[0-9]{1,4})?$")
    private val FOUR_NUMBERS = Regex("^-?[0-9]{1,5}(\\.[0-9]{1,3})?(,-?[0-9]{1,5}(\\.[0-9]{1,3})?){3}$")
    private val THREE_NUMBERS = Regex("^-?[0-9]{1,5}(\\.[0-9]{1,3})?(,-?[0-9]{1,5}(\\.[0-9]{1,3})?){2}$")
    private val SUGGESTION_ORDER = Regex("^([0-9]{1,2}\\([0-9]{1,3}\\)){1,16}$")
    private val SAFE_PUNCTUATION = Regex("^[!#$%&()*+,./:;<=>?@\\[\\]^_{|}~-]{0,8}$")

    val presetsDir: File
        get() = File(Tuils.getFolder(), PRESETS_FOLDER)

    fun listPresets(): MutableList<String> {
        val files: Array<File>? = presetsDir.listFiles()
        val presets: MutableList<String> = ArrayList<String>()
        val seen: MutableSet<String?> = HashSet<String?>()
        if (files != null) {
            for (file in files) {
                var name: String? = null
                if (file.isDirectory()) {
                    name = file.getName()
                } else if (file.isFile() && file.getName().lowercase(Locale.getDefault()).endsWith(
                        PRESET_PACKAGE_SUFFIX
                    )
                ) {
                    name = file.getName()
                        .substring(0, file.getName().length - PRESET_PACKAGE_SUFFIX.length)
                }
                if (name != null && seen.add(name.lowercase(Locale.getDefault()))) {
                    presets.add(name)
                }
            }
        }
        Collections.sort<String?>(presets, String.CASE_INSENSITIVE_ORDER)
        return presets
    }

    fun listSavedPresetFolders(): MutableList<kotlin.String?> {
        val files: Array<File>? = presetsDir.listFiles()
        val presets: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory()) {
                    presets.add(file.getName())
                }
            }
        }
        Collections.sort<kotlin.String?>(presets, String.CASE_INSENSITIVE_ORDER)
        return presets
    }

    fun listBuiltInPresets(): MutableList<kotlin.String> {
        val presets: MutableList<kotlin.String> = ArrayList<kotlin.String>()
        Collections.addAll<kotlin.String?>(presets, *BUILT_IN_PRESETS)
        return presets
    }

    fun listAllPresetNames(): MutableList<kotlin.String> {
        val presets = listPresets()
        for (builtIn in BUILT_IN_PRESETS) {
            if (!containsIgnoreCase(presets, builtIn)) {
                presets.add(builtIn!!)
            }
        }
        Collections.sort<kotlin.String?>(presets, String.CASE_INSENSITIVE_ORDER)
        return presets
    }

    fun isBuiltInPreset(name: kotlin.String?): Boolean {
        return containsIgnoreCase(listBuiltInPresets(), name)
    }

    fun getSavedPresetFolder(name: kotlin.String): File {
        return File(presetsDir, cleanName(name))
    }

    fun remove(name: kotlin.String) {
        val cleanName = cleanPresetPackageName(name)
        val folder = File(presetsDir, cleanName)
        val file = packageFile(cleanName)
        require(folder.isDirectory || file.isFile) { "Saved preset not found" }
        if (folder.exists()) Tuils.delete(folder)
        if (file.exists() && !file.delete()) throw IllegalStateException("Unable to remove preset")
        check(!folder.exists()) { "Unable to remove preset" }
    }

    internal fun importExtractedFolder(name: kotlin.String, source: File): kotlin.String {
        validatePresetFolder(source)
        val base = cleanPresetPackageName(name)
        var cleanName = base
        var suffix = 2
        while (File(presetsDir, cleanName).exists() || packageFile(cleanName).exists()) {
            cleanName = "$base ($suffix)"
            suffix++
        }
        val presetFolder = File(presetsDir, cleanName)
        check(presetFolder.mkdirs()) { "Unable to create preset folder" }
        copySanitizedXmlFiles(source, presetFolder)
        FrameManager.copyPortableState(source, presetFolder)
        return cleanName
    }

    @Throws(Exception::class)
    fun save(context: Context, name: kotlin.String) {
        val cleanName = cleanName(name)
        val presetFolder: File = File(presetsDir, cleanName)
        check(!(!presetFolder.exists() && !presetFolder.mkdirs())) { "Unable to create preset folder" }

        writeXml(
            File(presetFolder, XMLPrefsManager.XMLPrefsRoot.THEME.path),
            XMLPrefsManager.XMLPrefsRoot.THEME, SHAREABLE_THEME
        )
        writeXml(
            File(presetFolder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path),
            XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS, SHAREABLE_SUGGESTIONS
        )
        writeXml(
            File(presetFolder, XMLPrefsManager.XMLPrefsRoot.UI.path),
            XMLPrefsManager.XMLPrefsRoot.UI, SHAREABLE_UI
        )
        writeXml(
            File(presetFolder, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path),
            XMLPrefsManager.XMLPrefsRoot.BEHAVIOR, SHAREABLE_BEHAVIOR
        )
        FrameManager.copyCurrentTo(context, presetFolder)
    }

    @Throws(Exception::class)
    fun apply(name: kotlin.String) {
        val cleanName = cleanPresetPackageName(name)
        var presetFolder: File = File(presetsDir, cleanName)
        if (!presetFolder.isDirectory()) {
            val packageFile = packageFile(cleanName)
            if (packageFile.isFile()) {
                importPackage(cleanName)
                presetFolder = File(presetsDir, cleanName)
            }
        }

        if (!presetFolder.isDirectory()) {
            if (applyBuiltIn(cleanName)) {
                return
            }
            throw IllegalArgumentException("Preset not found")
        }

        val presetTheme = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.THEME.path)
        val presetSuggestions = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path)
        require(!(!presetTheme.isFile() || !presetSuggestions.isFile())) { "Preset is incomplete" }

        val currentTheme: File = File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.THEME.path)
        val currentSuggestions: File =
            File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path)
        val themeXml = sanitizeShareableXml(presetTheme, XMLPrefsRoot.THEME)
        val suggestionsXml = sanitizeShareableXml(presetSuggestions, XMLPrefsRoot.SUGGESTIONS)
        Tuils.insertOld(currentTheme)
        Tuils.insertOld(currentSuggestions)
        writeText(currentTheme, themeXml)
        writeText(currentSuggestions, suggestionsXml)
        val presetUi = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.UI.path)
        if (presetUi.isFile) applyAllowed(presetUi, XMLPrefsRoot.UI, SHAREABLE_UI)
        val presetBehavior = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path)
        if (presetBehavior.isFile) applyAllowed(presetBehavior, XMLPrefsRoot.BEHAVIOR, SHAREABLE_BEHAVIOR)
        FrameManager.applyPortableState(presetFolder)
        LauncherSettings.setAutoColorPick(false)
    }

    @Throws(Exception::class)
    fun exportPackage(name: kotlin.String): File {
        val cleanName = cleanPresetPackageName(name)
        val presetFolder: File = File(presetsDir, cleanName)
        require(presetFolder.isDirectory()) { "Preset not found" }

        val presetTheme = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.THEME.path)
        val presetSuggestions = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path)
        require(!(!presetTheme.isFile() || !presetSuggestions.isFile())) { "Preset is incomplete" }

        val out = packageFile(cleanName)
        val zip = ZipOutputStream(BufferedOutputStream(FileOutputStream(out, false)))
        try {
            addTextEntry(zip, MANIFEST_FILE, manifest())
            addTextEntry(zip, XMLPrefsManager.XMLPrefsRoot.THEME.path, sanitizeShareableXml(presetTheme, XMLPrefsRoot.THEME))
            addTextEntry(zip, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path, sanitizeShareableXml(presetSuggestions, XMLPrefsRoot.SUGGESTIONS))
            val presetUi = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.UI.path)
            if (presetUi.isFile) addTextEntry(zip, XMLPrefsManager.XMLPrefsRoot.UI.path, sanitizeShareableXml(presetUi, XMLPrefsRoot.UI))
            val presetBehavior = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path)
            if (presetBehavior.isFile) addTextEntry(zip, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path, sanitizeShareableXml(presetBehavior, XMLPrefsRoot.BEHAVIOR))
        } finally {
            zip.close()
        }
        return out
    }

    @Throws(Exception::class)
    fun importPackage(name: kotlin.String) {
        val cleanName = cleanPresetPackageName(name)
        val packageFile = packageFile(cleanName)
        require(packageFile.isFile()) { "Preset package not found" }
        importPackageFile(cleanName, packageFile)
    }

    @Throws(Exception::class)
    fun importPackage(context: Context, uri: Uri): kotlin.String {
        val displayName = displayName(context, uri)
        val cleanName = cleanPresetPackageName(displayName)
        val packageFile = packageFile(cleanName)
        copyUriToFile(context, uri, packageFile)
        importPackageFile(cleanName, packageFile)
        return cleanName
    }

    @Throws(Exception::class)
    fun importFolder(context: Context, treeUri: Uri): kotlin.String {
        val cleanName = cleanName(treeName(treeUri))
        val tempFolder: File = File(presetsDir, "." + cleanName + ".importing")
        if (tempFolder.exists()) {
            Tuils.delete(tempFolder)
        }
        check(tempFolder.mkdirs()) { "Unable to create import folder" }

        try {
            val children = folderChildren(context, treeUri)
            for (fileName in REQUIRED_PRESET_XML_FILES) {
                val child = children.get(fileName.lowercase(Locale.getDefault()))
                requireNotNull(child) { "Preset folder is incomplete" }
                copyUriToFile(context, child, File(tempFolder, fileName))
            }
            children[XMLPrefsManager.XMLPrefsRoot.UI.path.lowercase(Locale.getDefault())]?.let {
                copyUriToFile(context, it, File(tempFolder, XMLPrefsManager.XMLPrefsRoot.UI.path))
            }
            children[XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path.lowercase(Locale.getDefault())]?.let {
                copyUriToFile(context, it, File(tempFolder, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path))
            }
            validatePresetFolder(tempFolder)

            val presetFolder: File = File(presetsDir, cleanName)
            check(!(!presetFolder.exists() && !presetFolder.mkdirs())) { "Unable to create preset folder" }

            copySanitizedXmlFiles(tempFolder, presetFolder)
            return cleanName
        } finally {
            Tuils.delete(tempFolder)
        }
    }

    @Throws(Exception::class)
    fun exportPackage(context: Context, packageFile: File, uri: Uri) {
        require(packageFile.isFile()) { "Preset package not found" }

        val `in`: InputStream = BufferedInputStream(FileInputStream(packageFile))
        val destination = context.getContentResolver().openOutputStream(uri, "w")
        if (destination == null) {
            `in`.close()
            throw IllegalArgumentException("Unable to open export destination")
        }
        val out: OutputStream = BufferedOutputStream(destination)
        try {
            copyStream(`in`, out)
        } finally {
            `in`.close()
            out.close()
        }
    }

    fun packageFileName(packageFile: File?): kotlin.String {
        return if (packageFile == null) "preset" + PRESET_PACKAGE_SUFFIX else packageFile.getName()
    }

    @Throws(Exception::class)
    private fun importPackageFile(cleanName: kotlin.String, packageFile: File?) {
        val tempFolder: File = File(presetsDir, "." + cleanName + ".importing")
        if (tempFolder.exists()) {
            Tuils.delete(tempFolder)
        }
        check(tempFolder.mkdirs()) { "Unable to create import folder" }

        try {
            extractPackage(packageFile, tempFolder)
            validatePresetFolder(tempFolder)

            val presetFolder: File = File(presetsDir, cleanName)
            check(!(!presetFolder.exists() && !presetFolder.mkdirs())) { "Unable to create preset folder" }

            copySanitizedXmlFiles(tempFolder, presetFolder)
            FrameManager.copyPortableState(tempFolder, presetFolder)
        } finally {
            Tuils.delete(tempFolder)
        }
    }

    private fun displayName(context: Context, uri: Uri): kotlin.String {
        var name: kotlin.String? = null
        var cursor: Cursor? = null
        try {
            cursor = context.getContentResolver().query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = cursor.getString(index)
                }
            }
        } catch (ignored: Exception) {
        } finally {
            if (cursor != null) cursor.close()
        }

        if (name == null || name.trim { it <= ' ' }.length == 0) {
            val path = uri.getLastPathSegment()
            name = if (path == null) "imported-preset" else File(path).getName()
        }
        return name
    }

    private fun treeName(treeUri: Uri?): kotlin.String {
        val id: kotlin.String? = DocumentsContract.getTreeDocumentId(treeUri)
        if (id == null || id.trim { it <= ' ' }.length == 0) {
            return "imported-preset"
        }
        val slash = id.lastIndexOf('/')
        val colon = id.lastIndexOf(':')
        val cut = max(slash, colon)
        return if (cut >= 0 && cut < id.length - 1) id.substring(cut + 1) else id
    }

    @Throws(Exception::class)
    private fun folderChildren(context: Context, treeUri: Uri?): MutableMap<kotlin.String?, Uri?> {
        val children: MutableMap<kotlin.String?, Uri?> = HashMap<kotlin.String?, Uri?>()
        val childrenUri: Uri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        var cursor: Cursor? = null
        try {
            cursor = context.getContentResolver().query(
                childrenUri,
                arrayOf<kotlin.String>(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )
            requireNotNull(cursor) { "Unable to read preset folder" }
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(0)
                val name = cursor.getString(1)
                if (documentId != null && name != null) {
                    children.put(
                        name.lowercase(Locale.getDefault()),
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    )
                }
            }
        } finally {
            if (cursor != null) cursor.close()
        }
        return children
    }

    @Throws(Exception::class)
    private fun copyUriToFile(context: Context, uri: Uri, file: File) {
        val parent = file.getParentFile()
        check(!(parent != null && !parent.exists() && !parent.mkdirs())) { "Unable to create preset folder" }

        val `in`: InputStream =
            BufferedInputStream(context.getContentResolver().openInputStream(uri))
        requireNotNull(`in`) { "Unable to open preset package" }
        val out: OutputStream = BufferedOutputStream(FileOutputStream(file, false))
        try {
            copyStream(`in`, out)
        } finally {
            `in`.close()
            out.close()
        }
    }

    @Throws(Exception::class)
    private fun copyStream(`in`: InputStream, out: OutputStream) {
        val buffer = ByteArray(4096)
        var read: Int
        var total = 0
        while ((`in`.read(buffer).also { read = it }) != -1) {
            total += read
            require(total <= MAX_ENTRY_BYTES * PRESET_XML_FILES.size + 64 * 1024) { "Preset package file too large" }
            out.write(buffer, 0, read)
        }
        out.flush()
    }

    internal fun shareableXml(root: XMLPrefsRoot): String = xml(root, shareableValues(root)) {
        LauncherSettings.getEffective(it)
    }

    internal fun shareableUiXml(): String = shareableXml(XMLPrefsRoot.UI)

    internal fun shareableBehaviorXml(): String = shareableXml(XMLPrefsRoot.BEHAVIOR)

    internal fun sanitizeShareableXml(file: File, root: XMLPrefsRoot): String {
        val values = shareableValues(root)
        val allowed = values.associateBy { it.label() }
        val parsed = LinkedHashMap<XMLPrefsSave, String>()
        val document = parseXml(file)
        require(document.documentElement?.nodeName == root.name) { "Invalid preset XML: ${file.name}" }
        val children = document.documentElement.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val setting = allowed[XMLPrefsManager.canonicalSettingLabel(root, node.nodeName)] ?: continue
            val value = node.attributes?.getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE)?.nodeValue ?: continue
            if (!isShareableValue(setting, value)) continue
            require(parsed.put(setting, value) == null) { "Duplicate preset setting: ${setting.label()}" }
        }
        return xml(root, values) { parsed[it] }
    }

    private fun applyAllowed(file: File, rootType: XMLPrefsRoot, values: Array<out XMLPrefsSave>) {
        validateXmlRoot(file, rootType.name)
        val root = parseXml(file).documentElement
        val allowed = values.associateBy { it.label() }
        val children = root.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            val setting = allowed[node.nodeName] ?: continue
            val value = node.attributes?.getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE)?.nodeValue ?: continue
            if (!isShareableValue(setting, value)) continue
            LauncherSettings.set(setting, value)
        }
    }

    private fun shareableValues(root: XMLPrefsRoot): Array<out XMLPrefsSave> = when (root) {
        XMLPrefsRoot.THEME -> SHAREABLE_THEME
        XMLPrefsRoot.SUGGESTIONS -> SHAREABLE_SUGGESTIONS
        XMLPrefsRoot.UI -> SHAREABLE_UI
        XMLPrefsRoot.BEHAVIOR -> SHAREABLE_BEHAVIOR
        else -> throw IllegalArgumentException("Unsupported shareable configuration")
    }

    private fun isShareableValue(setting: XMLPrefsSave, value: String): Boolean {
        if (value.length > 128) return false
        return when (setting.type()) {
            XMLPrefsSave.BOOLEAN -> value == "true" || value == "false"
            XMLPrefsSave.INTEGER -> value.toIntOrNull()?.let { it in -100_000..100_000 } == true
            XMLPrefsSave.COLOR -> value.isEmpty() || COLOR_VALUE.matches(value)
            XMLPrefsSave.AUTO_COLOR -> value.equals("auto", true) || COLOR_VALUE.matches(value)
            XMLPrefsSave.TEXT -> when (setting) {
                Ui.input_prefix, Ui.input_root_prefix,
                Behavior.time_format_separator, Behavior.battery_progress_bar_symbol,
                Behavior.unlock_time_divider -> value == "%n" || SAFE_PUNCTUATION.matches(value)

                Ui.display_margin_top_section, Ui.display_margin_bottom_section,
                Ui.display_margin_landscape_mm, Ui.status_lines_margins,
                Ui.output_field_margins, Ui.input_field_margins, Ui.input_area_margins,
                Ui.toolbar_margins, Ui.suggestions_area_margin,
                Suggestions.suggestions_spaces -> FOUR_NUMBERS.matches(value)

                Ui.shadow_params -> THREE_NUMBERS.matches(value)
                Ui.dashed_border_stroke_width, Suggestions.suggestions_deadline ->
                    DECIMAL_VALUE.matches(value)

                Suggestions.noinput_suggestions_order, Suggestions.suggestions_order ->
                    SUGGESTION_ORDER.matches(value)

                Suggestions.hide_suggestions_when_empty -> value in setOf("always", "true", "false")
                Behavior.output_tray_mode -> value in setOf("native", "auto", "toggled")
                Behavior.output_header_mode -> value in setOf("normal", "arrows", "none")
                Behavior.weather_temperature_measure -> value in setOf("metric", "imperial", "standard")
                else -> false
            }
            else -> false
        }
    }

    fun applyBuiltIn(name: kotlin.String?): Boolean {
        val cleanName =
            if (name == null) null else name.trim { it <= ' ' }.lowercase(Locale.getDefault())
        if (!isBuiltInPreset(cleanName)) {
            return false
        }

        val colors: MutableMap<Theme, kotlin.String> = HashMap()
        val suggestionColors: MutableMap<Suggestions, kotlin.String> = HashMap()

        val isTransparent: Boolean = LauncherSettings.getBoolean(Ui.system_wallpaper)
        val backgroundTarget = if (isTransparent) Theme.wallpaper_overlay_color else Theme.background_color
        val transPrefix = if (isTransparent) "#00" else "#FF"

        when (cleanName) {
            "blue" -> {
                colors.put(backgroundTarget, transPrefix + "001221")
                colors.put(Theme.input_text_color, "#00BFFF")
                colors.put(Theme.output_text_color, "#E0FFFF")
                colors.put(Theme.device_text_color, "#1E90FF")
                colors.put(Theme.enter_icon_color, "#00BFFF")
                colors.put(Theme.toolbar_icon_color, "#00BFFF")
                colors.put(Theme.time_text_color, "#87CEFA")

                suggestionColors.put(Suggestions.apps_background_color, "#0000FF")
                suggestionColors.put(Suggestions.alias_background_color, "#4169E1")
                suggestionColors.put(Suggestions.cmd_background_color, "#00BFFF")
                suggestionColors.put(Suggestions.file_background_color, "#87CEFA")
                suggestionColors.put(Suggestions.song_background_color, "#1E90FF")
            }

            "red" -> {
                colors.put(backgroundTarget, transPrefix + "210000")
                colors.put(Theme.input_text_color, "#FF4500")
                colors.put(Theme.output_text_color, "#FFEBEE")
                colors.put(Theme.device_text_color, "#B71C1C")
                colors.put(Theme.enter_icon_color, "#FF0000")
                colors.put(Theme.toolbar_icon_color, "#FF5252")
                colors.put(Theme.time_text_color, "#FF8A80")

                suggestionColors.put(Suggestions.apps_background_color, "#FF0000")
                suggestionColors.put(Suggestions.alias_background_color, "#DC143C")
                suggestionColors.put(Suggestions.cmd_background_color, "#FF4500")
                suggestionColors.put(Suggestions.file_background_color, "#FA8072")
                suggestionColors.put(Suggestions.song_background_color, "#B22222")
            }

            "green" -> {
                colors.put(backgroundTarget, transPrefix + "001B00")
                colors.put(Theme.input_text_color, "#00FF41")
                colors.put(Theme.output_text_color, "#D5F5E3")
                colors.put(Theme.device_text_color, "#2ECC71")
                colors.put(Theme.enter_icon_color, "#00FF41")
                colors.put(Theme.toolbar_icon_color, "#27AE60")
                colors.put(Theme.time_text_color, "#A9DFBF")

                suggestionColors.put(Suggestions.apps_background_color, "#00FF00")
                suggestionColors.put(Suggestions.alias_background_color, "#32CD32")
                suggestionColors.put(Suggestions.cmd_background_color, "#00FF41")
                suggestionColors.put(Suggestions.file_background_color, "#90EE90")
                suggestionColors.put(Suggestions.song_background_color, "#228B22")
            }

            "pink" -> {
                colors.put(backgroundTarget, transPrefix + "1A0010")
                colors.put(Theme.input_text_color, "#FF69B4")
                colors.put(Theme.output_text_color, "#FCE4EC")
                colors.put(Theme.device_text_color, "#AD1457")
                colors.put(Theme.enter_icon_color, "#FF1493")
                colors.put(Theme.toolbar_icon_color, "#F06292")
                colors.put(Theme.time_text_color, "#F8BBD0")

                suggestionColors.put(Suggestions.apps_background_color, "#FF69B4")
                suggestionColors.put(Suggestions.alias_background_color, "#FF1493")
                suggestionColors.put(Suggestions.cmd_background_color, "#FFB6C1")
                suggestionColors.put(Suggestions.file_background_color, "#FFC0CB")
                suggestionColors.put(Suggestions.song_background_color, "#C71585")
            }

            "bw" -> {
                colors.put(backgroundTarget, transPrefix + "000000")
                colors.put(Theme.input_text_color, "#FFFFFF")
                colors.put(Theme.output_text_color, "#CCCCCC")
                colors.put(Theme.device_text_color, "#AAAAAA")
                colors.put(Theme.enter_icon_color, "#FFFFFF")
                colors.put(Theme.toolbar_icon_color, "#FFFFFF")
                colors.put(Theme.time_text_color, "#FFFFFF")

                suggestionColors.put(Suggestions.apps_background_color, "#FFFFFF")
                suggestionColors.put(Suggestions.alias_background_color, "#EEEEEE")
                suggestionColors.put(Suggestions.cmd_background_color, "#DDDDDD")
                suggestionColors.put(Suggestions.file_background_color, "#CCCCCC")
                suggestionColors.put(Suggestions.song_background_color, "#BBBBBB")

                suggestionColors.put(Suggestions.apps_text_color, "#000000")
                suggestionColors.put(Suggestions.alias_text_color, "#000000")
                suggestionColors.put(Suggestions.cmd_text_color, "#000000")
                suggestionColors.put(Suggestions.file_text_color, "#000000")
                suggestionColors.put(Suggestions.song_text_color, "#000000")
            }

            "cyberpunk" -> {
                colors.put(backgroundTarget, transPrefix + "0D0615")
                colors.put(Theme.input_text_color, "#FCEE09")
                colors.put(Theme.output_text_color, "#00F0FF")
                colors.put(Theme.device_text_color, "#FF003C")
                colors.put(Theme.enter_icon_color, "#FCEE09")
                colors.put(Theme.toolbar_icon_color, "#39FF14")
                colors.put(Theme.time_text_color, "#00F0FF")
                colors.put(Theme.terminal_border_color, "#E6F2F2F2")
                colors.put(Theme.terminal_header_border_color, "#E6F2F2F2")
                colors.put(Theme.terminal_window_background_color, "#CC070711")
                colors.put(Theme.terminal_header_background_color, "#E6070711")
                colors.put(Theme.module_button_background_color, "#66070711")
                colors.put(Theme.module_text_color, "#F2F2F2")

                suggestionColors.put(Suggestions.apps_background_color, "#FF003C")
                suggestionColors.put(Suggestions.alias_background_color, "#FCEE09")
                suggestionColors.put(Suggestions.cmd_background_color, "#00F0FF")
                suggestionColors.put(Suggestions.file_background_color, "#39FF14")
                suggestionColors.put(Suggestions.song_background_color, "#BC00FF")

                suggestionColors.put(Suggestions.alias_text_color, "#000000")
            }

            else -> return false
        }

        colors.put(Theme.toolbar_background_color, "#00000000")
        for (entry in colors.entries) {
            LauncherSettings.setTheme(entry.key, entry.value)
        }
        for (entry in suggestionColors.entries) {
            LauncherSettings.setSuggestion(entry.key, entry.value)
        }
        LauncherSettings.setAutoColorPick(false)
        return true
    }

    private fun cleanName(name: kotlin.String): kotlin.String {
        requireNotNull(name) { "Preset name is required" }
        val cleanName = name.trim { it <= ' ' }
        require(
            !(cleanName.length == 0 || cleanName.contains("/") || cleanName.contains("\\") || cleanName.contains(
                ".."
            ))
        ) { "Invalid preset name" }
        return cleanName
    }

    private fun cleanPresetPackageName(name: kotlin.String): kotlin.String {
        var cleanName = cleanName(name)
        if (cleanName.lowercase(Locale.getDefault()).endsWith(PRESET_PACKAGE_SUFFIX)) {
            cleanName = cleanName.substring(0, cleanName.length - PRESET_PACKAGE_SUFFIX.length)
        }
        require(cleanName.length != 0) { "Invalid preset name" }
        return cleanName
    }

    private fun packageFile(cleanName: kotlin.String?): File {
        return File(presetsDir, cleanName + PRESET_PACKAGE_SUFFIX)
    }

    @Throws(Exception::class)
    private fun addTextEntry(zip: ZipOutputStream, name: kotlin.String?, text: kotlin.String) {
        val entry = ZipEntry(name).apply { time = 0L }
        zip.putNextEntry(entry)
        zip.write(text.toByteArray(charset("UTF-8")))
        zip.closeEntry()
    }

    private fun manifest(): kotlin.String {
        return ("{\n"
                + "  \"type\": \"retui-preset\",\n"
                + "  \"schema\": 2,\n"
                + "  \"privacy\": \"pi-safe\",\n"
                + "  \"appVersion\": \"" + jsonEscape(BuildConfig.VERSION_NAME) + "\"\n"
                + "}\n")
    }

    private fun jsonEscape(value: kotlin.String?): kotlin.String {
        if (value == null) return ""
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    @Throws(Exception::class)
    private fun extractPackage(packageFile: File?, tempFolder: File?) {
        val required: MutableSet<kotlin.String> = HashSet<kotlin.String>()
        Collections.addAll<kotlin.String?>(required, *REQUIRED_PRESET_XML_FILES)
        val seen: MutableSet<kotlin.String> = HashSet<kotlin.String>()
        var hasManifest = false

        val zip = ZipInputStream(BufferedInputStream(FileInputStream(packageFile)))
        val buffer = ByteArray(4096)
        try {
            var entry: ZipEntry?
            while ((zip.getNextEntry().also { entry = it }) != null) {
                if (entry!!.isDirectory()) {
                    continue
                }

                val name = entry.getName()
                val allowedFrame = FrameManager.isPortableEntry(name)
                require(allowedFrame || !(name.contains("/") || name.contains("\\") || name.contains(".."))) { "Unsafe preset package" }
                require(seen.add(name)) { "Preset package contains duplicate entries" }

                val allowedXml = PRESET_XML_FILES.contains(name)
                require(!(!allowedXml && !allowedFrame && MANIFEST_FILE != name)) { "Unsupported preset package file: " + name }

                val out = File(tempFolder, name)
                val parent = out.parentFile
                check(!(parent != null && !parent.exists() && !parent.mkdirs())) { "Unable to create preset folder" }
                val stream = FileOutputStream(out, false)
                var total = 0
                try {
                    var read: Int
                    while ((zip.read(buffer).also { read = it }) != -1) {
                        total += read
                        val limit = if (allowedFrame && name.endsWith(".retui-frame")) FrameManager.MAX_BUNDLE_BYTES else MAX_ENTRY_BYTES
                        require(total <= limit) { "Preset package file too large: " + name }
                        stream.write(buffer, 0, read)
                    }
                } finally {
                    stream.close()
                }

                if (MANIFEST_FILE == name) {
                    hasManifest = true
                } else if (required.contains(name)) {
                    required.remove(name)
                }
            }
        } finally {
            zip.close()
        }

        require(!(!hasManifest || !required.isEmpty())) { "Preset package is incomplete" }
    }

    @Throws(Exception::class)
    private fun validatePresetFolder(folder: File?) {
        validateXmlRoot(
            File(folder, XMLPrefsManager.XMLPrefsRoot.THEME.path),
            XMLPrefsManager.XMLPrefsRoot.THEME.name
        )
        validateXmlRoot(
            File(folder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path),
            XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.name
        )
        val ui = File(folder, XMLPrefsManager.XMLPrefsRoot.UI.path)
        if (ui.isFile) validateXmlRoot(ui, XMLPrefsManager.XMLPrefsRoot.UI.name)
        val behavior = File(folder, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path)
        if (behavior.isFile) validateXmlRoot(behavior, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.name)
        FrameManager.validatePortableState(folder!!)
    }

    @Throws(Exception::class)
    private fun validateXmlRoot(file: File, expectedRoot: kotlin.String) {
        require(!(!file.isFile() || file.length() > MAX_ENTRY_BYTES)) { "Preset package is incomplete" }
        val doc = parseXml(file)
        require(doc.documentElement?.nodeName == expectedRoot) { "Invalid preset XML: " + file.getName() }
    }

    private fun parseXml(file: File): Document {
        require(file.isFile && file.length() <= MAX_ENTRY_BYTES) { "Preset package is incomplete" }
        val xml = file.readText(Charsets.UTF_8)
        require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "Preset XML cannot contain a DOCTYPE" }
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        return builder.parse(InputSource(StringReader(xml)))
    }

    private fun copySanitizedXmlFiles(source: File, destination: File) {
        for (root in arrayOf(XMLPrefsRoot.THEME, XMLPrefsRoot.SUGGESTIONS, XMLPrefsRoot.UI, XMLPrefsRoot.BEHAVIOR)) {
            val sourceFile = File(source, root.path)
            if (!sourceFile.isFile) continue
            val destinationFile = File(destination, root.path)
            if (destinationFile.exists()) Tuils.insertOld(destinationFile)
            writeText(destinationFile, sanitizeShareableXml(sourceFile, root))
        }
    }

    private fun containsIgnoreCase(
        list: MutableList<kotlin.String>,
        value: kotlin.String?
    ): Boolean {
        if (value == null) {
            return false
        }
        for (entry in list) {
            if (entry.equals(value, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    @Throws(Exception::class)
    private fun writeXml(file: File?, root: XMLPrefsRoot, values: Array<out XMLPrefsSave>) {
        writeText(requireNotNull(file), xml(root, values) { LauncherSettings.getEffective(it) })
    }

    private fun writeText(file: File, text: String) {
        FileOutputStream(file, false).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }

    private fun xml(
        root: XMLPrefsRoot,
        values: Array<out XMLPrefsSave>,
        valueFor: (XMLPrefsSave) -> String?
    ): String {
        val xml: StringBuilder = StringBuilder(XMLPrefsManager.XML_DEFAULT)
        xml.append("<").append(root.name).append(">\n")
        for (setting in values) {
            val value = valueFor(setting) ?: continue
            if (!isShareableValue(setting, value)) continue
            xml.append("\t<")
                .append(setting.label())
                .append(" value=\"")
                .append(xmlEscape(value))
                .append("\" />\n")
        }
        xml.append("</").append(root.name).append(">\n")
        return xml.toString()
    }

    private fun xmlEscape(value: String?): String = value.orEmpty()
        .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
