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
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.util.ArrayList
import java.util.HashSet
import java.util.HashMap
import java.util.Map
import java.util.Set
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils

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
        XMLPrefsManager.XMLPrefsRoot.UI.path
    )
    private val SHAREABLE_UI = XMLPrefsManager.XMLPrefsRoot.UI.enums.filterNot {
        it == Ui.username || it == Ui.deviceName || it == Ui.font_file || it == Ui.auto_color_pick
    }.toTypedArray()

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
        for (fileName in PRESET_XML_FILES) {
            val sourceFile = File(source, fileName)
            if (sourceFile.isFile) Tuils.copy(sourceFile, File(presetFolder, fileName))
        }
        return cleanName
    }

    @Throws(Exception::class)
    fun save(name: kotlin.String) {
        val cleanName = cleanName(name)
        val presetFolder: File = File(presetsDir, cleanName)
        check(!(!presetFolder.exists() && !presetFolder.mkdirs())) { "Unable to create preset folder" }

        writeXml(
            File(presetFolder, XMLPrefsManager.XMLPrefsRoot.THEME.path),
            XMLPrefsManager.XMLPrefsRoot.THEME, Theme.entries.toTypedArray()
        )
        writeXml(
            File(presetFolder, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path),
            XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS, Suggestions.entries.toTypedArray()
        )
        writeXml(
            File(presetFolder, XMLPrefsManager.XMLPrefsRoot.UI.path),
            XMLPrefsManager.XMLPrefsRoot.UI, SHAREABLE_UI
        )

        apply(cleanName)
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
        Tuils.insertOld(currentTheme)
        Tuils.insertOld(currentSuggestions)
        Tuils.copy(presetTheme, currentTheme)
        Tuils.copy(presetSuggestions, currentSuggestions)
        val presetUi = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.UI.path)
        if (presetUi.isFile) applyUi(presetUi)
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
            addTextEntry(zip, MANIFEST_FILE, manifest(cleanName))
            addFileEntry(zip, XMLPrefsManager.XMLPrefsRoot.THEME.path, presetTheme)
            addFileEntry(zip, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path, presetSuggestions)
            val presetUi = File(presetFolder, XMLPrefsManager.XMLPrefsRoot.UI.path)
            if (presetUi.isFile) addFileEntry(zip, XMLPrefsManager.XMLPrefsRoot.UI.path, presetUi)
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
        require(!(context == null || uri == null)) { "Preset package is required" }

        val displayName = displayName(context, uri)
        val cleanName = cleanPresetPackageName(displayName)
        val packageFile = packageFile(cleanName)
        copyUriToFile(context, uri, packageFile)
        importPackageFile(cleanName, packageFile)
        return cleanName
    }

    @Throws(Exception::class)
    fun importFolder(context: Context, treeUri: Uri): kotlin.String {
        require(!(context == null || treeUri == null)) { "Preset folder is required" }

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
            validatePresetFolder(tempFolder)

            val presetFolder: File = File(presetsDir, cleanName)
            check(!(!presetFolder.exists() && !presetFolder.mkdirs())) { "Unable to create preset folder" }

            for (fileName in PRESET_XML_FILES) {
                if (!File(tempFolder, fileName).isFile) continue
                val dest = File(presetFolder, fileName)
                if (dest.exists()) {
                    Tuils.insertOld(dest)
                }
                Tuils.copy(File(tempFolder, fileName), dest)
            }
            return cleanName
        } finally {
            Tuils.delete(tempFolder)
        }
    }

    @Throws(Exception::class)
    fun exportPackage(context: Context, packageFile: File, uri: Uri) {
        require(!(context == null || packageFile == null || uri == null || !packageFile.isFile())) { "Preset package not found" }

        val `in`: InputStream = BufferedInputStream(FileInputStream(packageFile))
        val out: OutputStream =
            BufferedOutputStream(context.getContentResolver().openOutputStream(uri, "w"))
        if (out == null) {
            `in`.close()
            throw IllegalArgumentException("Unable to open export destination")
        }
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

            for (fileName in PRESET_XML_FILES) {
                if (!File(tempFolder, fileName).isFile) continue
                val dest = File(presetFolder, fileName)
                if (dest.exists()) {
                    Tuils.insertOld(dest)
                }
                Tuils.copy(File(tempFolder, fileName), dest)
            }
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

    internal fun shareableUiXml(): String = xml(XMLPrefsRoot.UI, SHAREABLE_UI)

    internal fun applyUi(file: File) {
        validateXmlRoot(file, XMLPrefsRoot.UI.name)
        val root = DocumentBuilderFactory.newInstance().apply { setExpandEntityReferences(false) }
            .newDocumentBuilder().parse(file).documentElement
        val allowed = SHAREABLE_UI.associateBy { it.label() }
        val children = root.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            val setting = allowed[node.nodeName] ?: continue
            val value = node.attributes?.getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE)?.nodeValue ?: continue
            LauncherSettings.set(setting, value)
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
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        zip.write(text.toByteArray(charset("UTF-8")))
        zip.closeEntry()
    }

    @Throws(Exception::class)
    private fun addFileEntry(zip: ZipOutputStream, name: kotlin.String?, file: File?) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        val `in` = FileInputStream(file)
        val buffer = ByteArray(4096)
        try {
            var read: Int
            while ((`in`.read(buffer).also { read = it }) != -1) {
                zip.write(buffer, 0, read)
            }
        } finally {
            `in`.close()
        }
        zip.closeEntry()
    }

    private fun manifest(name: kotlin.String?): kotlin.String {
        return ("{\n"
                + "  \"type\": \"retui-preset\",\n"
                + "  \"schema\": 1,\n"
                + "  \"name\": \"" + jsonEscape(name) + "\",\n"
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
                require(!(name.contains("/") || name.contains("\\") || name.contains(".."))) { "Unsafe preset package" }
                require(seen.add(name)) { "Preset package contains duplicate entries" }

                val allowedXml = PRESET_XML_FILES.contains(name)
                require(!(!allowedXml && MANIFEST_FILE != name)) { "Unsupported preset package file: " + name }

                val out = File(tempFolder, name)
                val stream = FileOutputStream(out, false)
                var total = 0
                try {
                    var read: Int
                    while ((zip.read(buffer).also { read = it }) != -1) {
                        total += read
                        require(total <= MAX_ENTRY_BYTES) { "Preset package file too large: " + name }
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
    }

    @Throws(Exception::class)
    private fun validateXmlRoot(file: File, expectedRoot: kotlin.String) {
        require(!(!file.isFile() || file.length() > MAX_ENTRY_BYTES)) { "Preset package is incomplete" }
        val factory = DocumentBuilderFactory.newInstance()
        factory.setExpandEntityReferences(false)
        val doc = factory.newDocumentBuilder().parse(file)
        require(
            !(doc == null || doc.getDocumentElement() == null || (expectedRoot != doc.getDocumentElement()
                .getNodeName()))
        ) { "Invalid preset XML: " + file.getName() }
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
        val stream = FileOutputStream(file, false)
        stream.write(xml(root, values).toByteArray())
        stream.flush()
        stream.close()
    }

    private fun xml(root: XMLPrefsRoot, values: Array<out XMLPrefsSave>): String {
        val xml: StringBuilder = StringBuilder(XMLPrefsManager.XML_DEFAULT)
        xml.append("<").append(root.name).append(">\n")
        for (value in values) {
            xml.append("\t<")
                .append(value.label())
                .append(" value=\"")
                .append(xmlEscape(LauncherSettings.getEffective(value)))
                .append("\" />\n")
        }
        xml.append("</").append(root.name).append(">\n")
        return xml.toString()
    }

    private fun xmlEscape(value: String?): String = value.orEmpty()
        .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
