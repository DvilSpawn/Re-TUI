package ohi.andre.consolelauncher.managers

import ohi.andre.consolelauncher.tuils.LocalizedArgumentException
import ohi.andre.consolelauncher.tuils.LocalizedStateException
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.BuildConfig
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Collections
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import ohi.andre.consolelauncher.managers.onboarding.GuideManager
import ohi.andre.consolelauncher.managers.podcast.PodcastManager
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceLauncherManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetReminderManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperService

object BackupManager {
    private const val BACKUP_SUFFIX = ".retui-backup"
    private const val MANIFEST_FILE = "manifest.txt"
    private const val SHARED_PREFS_DIR = "shared_prefs/"
    private const val PRIVATE_FILES_DIR = "app_files/"
    internal val personalPrivateFileNames = listOf(
        "remember-notes.xml", "remember-notes.xml.bak", "notes-library", "images",
        "launcher-language-packs", "launcher_frames", "fonts"
    )
    private val MAX_BACKUP_BYTES = 32L * 1024L * 1024L
    private val ENCRYPTED_MAGIC = kotlin.byteArrayOf(
        'R'.code.toByte(),
        'E'.code.toByte(),
        'T'.code.toByte(),
        'U'.code.toByte(),
        'I'.code.toByte(),
        'E'.code.toByte(),
        'N'.code.toByte(),
        'C'.code.toByte(),
        '1'.code.toByte()
    )
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val KDF_ITERATIONS = 120000
    private const val GCM_TAG_BITS = 128
    private const val TYPE_BACKUP = "retui-backup"
    private const val TYPE_SHAREABLE = "retui-shareable-config"
    private val SHAREABLE_FILES = kotlin.arrayOf<kotlin.String>(
        XMLPrefsManager.XMLPrefsRoot.THEME.path,
        XMLPrefsManager.XMLPrefsRoot.UI.path,
        XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path
    )
    private val REQUIRED_PERSONAL_ENTRIES = setOf(
        MANIFEST_FILE,
        XMLPrefsManager.XMLPrefsRoot.THEME.path,
        XMLPrefsManager.XMLPrefsRoot.UI.path,
        XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path,
        SHARED_PREFS_DIR + "ui.properties"
    )
    private val PERSONAL_PREFS = kotlin.arrayOf<kotlin.String?>(
        "ui",
        "apps",
        PinnedShortcutManager.PREFS,
        "retui_modules",
        "retui_reminders",
        LuaWidgetReminderManager.PREFS,
        "retui_termux_apps",
        "retui_module_prompt",
        "retui_callback_auth",
        "retui_frames",
        "pomodoro_state",
        RetuiCreditManager.PREFS,
        SpaceManager.PREFS,
        GuideManager.PREFS,
        ohi.andre.consolelauncher.managers.onboarding.StartupMenuManager.PREFS,
        PodcastManager.PREFS,
        "tasker_integration",
        "retui_wallpaper",
        "retui_profile",
        TermuxWorkspaceLauncherManager.PREFS,
        "changelogPrefs",
        "launcher-language",
        "remember-theme"
    )

    internal fun personalSharedPreferenceNames(): List<kotlin.String> = PERSONAL_PREFS.filterNotNull()

    fun defaultBackupName(): kotlin.String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return "retui-backup-" + stamp + ohi.andre.consolelauncher.managers.BackupManager.BACKUP_SUFFIX
    }

    @JvmOverloads
    fun defaultShareableConfigurationName(sourceName: kotlin.String? = null): kotlin.String {
        val name = sourceName?.replace(Regex("[^A-Za-z0-9_-]+"), "_")?.trim('_')?.take(80)
        if (!name.isNullOrEmpty()) return "$name.retui"
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "retui-config-$stamp.retui"
    }

    const val PRESET_MIME = "application/vnd.retui+zip"

    @JvmOverloads
    @Throws(java.lang.Exception::class)
    fun exportBackup(
        context: android.content.Context,
        uri: android.net.Uri,
        password: kotlin.String? = null
    ) {
        FileOperationCancellation.check()
        val backup = ohi.andre.consolelauncher.managers.BackupManager.createPersonalBackup(context)
        FileOperationCancellation.check()
        val payload = if (!password.isNullOrEmpty()) encrypt(backup, password) else backup
        FileOperationCancellation.check()
        writeVerified(context, uri, payload)
    }

    @JvmOverloads
    @Throws(java.lang.Exception::class)
    fun exportShareableConfiguration(
        context: android.content.Context,
        uri: android.net.Uri,
        presetName: kotlin.String? = null,
        behaviorLabels: Set<String> = PresetManager.defaultShareableBehaviorLabels()
    ): Uri {
        val preset = if (presetName == null) null else presetName.trim { it <= ' ' }
        var sourceRoot: java.io.File = Tuils.getFolder()
        var sourceType = "current"
        var presetSource = false
        if (preset != null && preset.length > 0) {
            sourceRoot = PresetManager.getSavedPresetFolder(preset)
            kotlin.require(sourceRoot.isDirectory()) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_preset_not_found_ce086) }
            sourceType = "preset"
            presetSource = true
        }

        val uiFile = java.io.File(sourceRoot, XMLPrefsManager.XMLPrefsRoot.UI.path)
        val behaviorFile = java.io.File(sourceRoot, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path)
        val includeUi = !presetSource || uiFile.isFile
        val includeBehavior = !presetSource || behaviorFile.isFile
        val sections = "theme" + (if (includeUi) ",ui" else "") + ",suggestions" +
            (if (includeBehavior) ",behavior" else "")

        val out = java.io.ByteArrayOutputStream()
        val zip = java.util.zip.ZipOutputStream(out)
        try {
            ohi.andre.consolelauncher.managers.BackupManager.addTextEntry(
                zip, ohi.andre.consolelauncher.managers.BackupManager.MANIFEST_FILE,
                shareableManifest(
                    sourceType,
                    sections,
                    if (presetSource) sourceRoot.name else null,
                    behaviorLabels
                )
            )
            val roots = mutableListOf(XMLPrefsManager.XMLPrefsRoot.THEME)
            if (includeUi) roots.add(XMLPrefsManager.XMLPrefsRoot.UI)
            roots.add(XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS)
            if (includeBehavior) roots.add(XMLPrefsManager.XMLPrefsRoot.BEHAVIOR)
            for (root in roots) {
                val file = java.io.File(sourceRoot, root.path)
                if (presetSource) kotlin.require(file.isFile()) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_preset_is_incomplete_85622) }
                ohi.andre.consolelauncher.managers.BackupManager.addTextEntry(
                    zip,
                    root.path,
                    if (root == XMLPrefsManager.XMLPrefsRoot.BEHAVIOR) {
                        if (presetSource) PresetManager.sanitizeSelectedBehaviorXml(file, behaviorLabels)
                        else PresetManager.shareableBehaviorXml(behaviorLabels)
                    } else if (presetSource) PresetManager.sanitizeShareableXml(file, root)
                    else PresetManager.shareableXml(root)
                )
            }
        } finally {
            zip.close()
        }
        val payload = out.toByteArray()
        FileOperationCancellation.check()
        writeVerified(context, uri, payload)
        val shares = File(context.cacheDir, "preset-share")
        check(shares.isDirectory || shares.mkdirs()) { "Unable to create preset share cache" }
        val now = System.currentTimeMillis()
        shares.listFiles()?.filter { now - it.lastModified() > 24L * 60 * 60 * 1000 }?.forEach { it.deleteRecursively() }
        val folder = File(shares, java.util.UUID.randomUUID().toString())
        check(folder.mkdir()) { "Unable to cache preset for sharing" }
        try {
            val cached = File(folder, defaultShareableConfigurationName(presetName))
            cached.writeBytes(payload)
            cached.inputStream().use { verifyExport(payload, it) }
            return androidx.core.content.FileProvider.getUriForFile(context,
                ohi.andre.consolelauncher.tuils.GenericFileProvider.PROVIDER_NAME, cached)
        } catch (error: Exception) {
            folder.deleteRecursively()
            throw error
        }
    }

    internal fun shareableManifest(
        sourceType: String,
        sections: String,
        presetName: String?,
        behaviorLabels: Set<String>? = null
    ): String = buildString {
        append("type=").append(TYPE_SHAREABLE).append('\n')
        append("schema=").append(if (behaviorLabels == null) "2" else "3").append('\n')
        append("profile=shareable\n")
        append("privacy=").append(if (behaviorLabels == null) "pi-safe" else "user-selected").append('\n')
        append("appVersion=").append(BuildConfig.VERSION_NAME).append('\n')
        append("source=").append(sourceType).append('\n')
        if (!presetName.isNullOrBlank()) append("presetName=").append(presetName).append('\n')
        append("sections=").append(sections).append('\n')
        if (behaviorLabels != null) {
            append("behaviorFields=").append(behaviorLabels.sorted().joinToString(",")).append('\n')
        }
    }

    private fun writeVerified(context: Context, uri: Uri, payload: ByteArray) {
        require(payload.isNotEmpty() && payload.size <= MAX_BACKUP_BYTES) { context.getString(R.string.manager_backupmanager_backup_package_is_empty_or_too_large_ea31d) }
        val raw = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
            context.getString(R.string.manager_backupmanager_unable_to_open_backup_destination_b73a4)
        }
        BufferedOutputStream(raw).use { output ->
            FileOperationCancellation.copy(ByteArrayInputStream(payload), output, MAX_BACKUP_BYTES)
            output.flush()
        }
        val written = requireNotNull(context.contentResolver.openInputStream(uri)) {
            context.getString(R.string.manager_backupmanager_unable_to_verify_backup_destination_dcbcd)
        }
        BufferedInputStream(written).use { input ->
            FileOperationCancellation.track(input) { verifyExport(payload, it) }
        }
    }

    internal fun verifyExport(expected: ByteArray, actual: InputStream) {
        require(expected.isNotEmpty()) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_backup_verification_failed_6772e) }
        val buffer = ByteArray(8192)
        var offset = 0
        while (true) {
            FileOperationCancellation.check()
            val read = actual.read(buffer)
            if (read == -1) break
            require(offset + read <= expected.size) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_backup_verification_failed_6772e) }
            for (index in 0 until read) {
                require(buffer[index] == expected[offset + index]) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_backup_verification_failed_6772e) }
            }
            offset += read
        }
        require(offset == expected.size) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_backup_verification_failed_6772e) }
    }

    @JvmOverloads
    @Throws(java.lang.Exception::class)
    fun importBackup(
        context: android.content.Context,
        uri: android.net.Uri,
        password: kotlin.String? = null
    ): kotlin.String? {
        FileOperationCancellation.check()
        recoverInterruptedRestore(context)
        val tempDir: java.io.File = java.io.File(Tuils.getFolder(), ".restore-importing")
        if (tempDir.exists()) {
            Tuils.delete(tempDir)
        }
        kotlin.check(tempDir.mkdirs()) { throw LocalizedStateException(R.string.validation_backup_backupmanager_unable_to_create_restore_folder_89757) }

        try {
            var hasManifest = false
            var manifest: kotlin.String? = null
            var totalBytes: kotlin.Long = 0
            val entries = java.util.HashSet<kotlin.String>()
            val packageStream = ohi.andre.consolelauncher.managers.BackupManager.backupInputStream(
                context,
                uri,
                password
            )
            val zip = java.util.zip.ZipInputStream(java.io.BufferedInputStream(packageStream))
            val buffer = kotlin.ByteArray(8192)
            try {
                var entry: java.util.zip.ZipEntry?
                while ((zip.getNextEntry().also { entry = it }) != null) {
                    FileOperationCancellation.check()
                    val name = entry!!.getName()
                    if (entry.isDirectory()) continue
                    kotlin.require(ohi.andre.consolelauncher.managers.BackupManager.isSafeEntry(name)) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_unsafe_backup_package_a3cb1) }
                    kotlin.require(entries.add(name)) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_backup_package_contains_duplicate_entries_aec49) }

                    if (ohi.andre.consolelauncher.managers.BackupManager.MANIFEST_FILE == name) {
                        hasManifest = true
                    }

                    val out = java.io.File(tempDir, name)
                    val parent = out.getParentFile()
                    kotlin.check(!(parent != null && !parent.exists() && !parent.mkdirs())) { throw LocalizedStateException(R.string.validation_backup_backupmanager_unable_to_restore_backup_folder_184df) }

                    val stream = java.io.FileOutputStream(out, false)
                    try {
                        var read: Int
                        val manifestOut =
                            if (ohi.andre.consolelauncher.managers.BackupManager.MANIFEST_FILE == name) java.io.ByteArrayOutputStream() else null
                        while ((zip.read(buffer).also { read = it }) != -1) {
                            FileOperationCancellation.check()
                            totalBytes += read.toLong()
                            kotlin.require(totalBytes <= ohi.andre.consolelauncher.managers.BackupManager.MAX_BACKUP_BYTES) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_backup_package_is_too_large_df84c) }
                            stream.write(buffer, 0, read)
                            if (manifestOut != null) {
                                manifestOut.write(buffer, 0, read)
                            }
                        }
                        if (manifestOut != null) {
                            manifest = manifestOut.toString("UTF-8")
                        }
                    } finally {
                        stream.close()
                    }
                }
            } finally {
                zip.close()
            }

            kotlin.require(hasManifest) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_backup_package_is_incomplete_91553) }

            val personal = validatePackage(manifest, entries)
            if (personal) {
                synchronized(ohi.andre.consolelauncher.notes.NoteStore.lock) {
                    restorePersonalBackup(context, tempDir, manifestValue(manifest, "schema") == "2")
                }
                context.applicationContext.sendBroadcast(
                    Intent(RetuiWallpaperService.ACTION_REFRESH).setPackage(context.packageName)
                )
                FrameManager.onFilesRestored(context)
                SpaceManager.ensureInitialized(context)
                return null
            }
            FileOperationCancellation.beginCommit()
            val importedName = PresetManager.importExtractedFolder(
                manifestValue(manifest, "presetName")?.takeIf { it.isNotBlank() } ?: "Imported preset",
                tempDir,
                if (manifestValue(manifest, "schema") == "3") behaviorFields(manifest) else null
            )
            return importedName
        } finally {
            tempDir.deleteRecursively()
        }
    }

    class StagedPreset internal constructor(
        private val staging: File,
        val folder: File,
        val manifest: String
    ) : AutoCloseable {
        val name: String get() = manifestValue(manifest, "presetName").ifBlank { "Imported preset" }
        val sections: String get() = manifestValue(manifest, "sections")
        val producerVersion: String get() = manifestValue(manifest, "appVersion")
        val schema: String get() = manifestValue(manifest, "schema")
        val behaviorLabels: Set<String>? get() = if (schema == "3") behaviorFields(manifest) else null

        fun save(): String {
            // Re-extract and validate the private snapshot, never the original provider URI.
            File(staging, "package.retui").inputStream().use { input ->
                inspectPreset(input, staging.parentFile!!).use { checked ->
                    FileOperationCancellation.beginCommit()
                    return PresetManager.importExtractedFolder(checked.name, checked.folder, checked.behaviorLabels)
                }
            }
        }

        override fun close() { staging.deleteRecursively() }
    }

    fun inspectPreset(input: InputStream, privateRoot: File): StagedPreset {
        check(privateRoot.isDirectory || privateRoot.mkdirs()) { "Unable to create private preset staging" }
        val staging = File.createTempFile("preset-", ".staging", privateRoot)
        check(staging.delete() && staging.mkdir()) { "Unable to create private preset staging" }
        try {
            val archive = File(staging, "package.retui")
            input.use { source -> archive.outputStream().use { target -> copyBounded(source, target, MAX_BACKUP_BYTES) } }
            val folder = File(staging, "contents")
            check(folder.mkdir()) { "Unable to stage preset contents" }
            val entries = HashSet<String>()
            var total = 0L
            java.util.zip.ZipFile(archive).use { zip ->
                val all = zip.entries()
                var count = 0
                while (all.hasMoreElements()) {
                    FileOperationCancellation.check()
                    val entry = all.nextElement()
                    require(++count <= 256) { "Preset contains too many archive entries" }
                    val name = entry.name
                    require(isSafeEntry(name) && !name.contains('\u0000') && name.length <= 240) { "Unsafe preset path" }
                    require(entries.add(name)) { "Preset contains duplicate archive entries" }
                    require(!entry.isDirectory) { "Unexpected preset directory entry" }
                    require(name == MANIFEST_FILE || name in SHAREABLE_FILES ||
                        name == XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path || FrameManager.isPortableEntry(name)) {
                        "Unexpected preset file: $name"
                    }
                    val target = File(folder, name)
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "Unable to stage preset" }
                    val limit = when {
                        name == MANIFEST_FILE -> 16L * 1024
                        name.endsWith(".xml") -> 256L * 1024
                        else -> MAX_BACKUP_BYTES
                    }
                    zip.getInputStream(entry).use { source ->
                        target.outputStream().use { output -> total += copyBounded(source, output, minOf(limit, MAX_BACKUP_BYTES - total)) }
                    }
                    val crc = java.util.zip.CRC32()
                    target.inputStream().use { source ->
                        val buffer = ByteArray(8192)
                        while (true) { FileOperationCancellation.check(); val n = source.read(buffer); if (n < 0) break; crc.update(buffer, 0, n) }
                    }
                    require(crc.value == entry.crc && target.length() == entry.size) { "Corrupt preset archive entry" }
                }
            }
            val manifest = File(folder, MANIFEST_FILE).takeIf { it.isFile }?.readText(Charsets.UTF_8)
                ?: throw IllegalArgumentException("Preset manifest is missing")
            val keys = manifest.lineSequence().filter { it.isNotBlank() }.map { it.substringBefore('=') }.toList()
            require(keys.size == keys.toSet().size) { "Duplicate preset manifest fields" }
            require(manifestValue(manifest, "type") == TYPE_SHAREABLE) { "Personal backups cannot be imported as presets. Use Restore Backup instead." }
            require(!validatePackage(manifest, entries)) { "Only shareable presets are supported" }
            PresetManager.validatePresetFolder(folder)
            for (root in arrayOf(XMLPrefsManager.XMLPrefsRoot.THEME, XMLPrefsManager.XMLPrefsRoot.UI,
                XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS, XMLPrefsManager.XMLPrefsRoot.BEHAVIOR)) {
                val file = File(folder, root.path)
                if (!file.isFile) continue
                if (root == XMLPrefsManager.XMLPrefsRoot.BEHAVIOR && manifestValue(manifest, "schema") == "3")
                    PresetManager.sanitizeSelectedBehaviorXml(file, behaviorFields(manifest))
                else PresetManager.sanitizeShareableXml(file, root)
            }
            return StagedPreset(staging, folder, manifest)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    internal fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long =
        FileOperationCancellation.track(input) { source -> FileOperationCancellation.track(output) { target ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                FileOperationCancellation.check()
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "Preset package is too large" }
                target.write(buffer, 0, count)
            }
            total
        } }

    internal fun validatePackage(manifest: kotlin.String?, entries: kotlin.collections.Set<kotlin.String>): kotlin.Boolean {
        val type = manifestValue(manifest, "type")
        val schema = manifestValue(manifest, "schema")
        val profile = manifestValue(manifest, "profile")

        return when (type) {
            TYPE_BACKUP -> {
                kotlin.require(schema == "1" || schema == "2") { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_unsupported_backup_schema_94afd) }
                kotlin.require(profile == "personal") { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_invalid_personal_backup_profile_e275d) }
                kotlin.require(entries.containsAll(REQUIRED_PERSONAL_ENTRIES)) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_personal_backup_is_incomplete_838a0) }
                require(entries.filter { it.startsWith(PRIVATE_FILES_DIR) }.all { entry ->
                    schema == "2" && personalPrivateFileNames.any { name ->
                        entry == PRIVATE_FILES_DIR + name || entry.startsWith(PRIVATE_FILES_DIR + name + "/")
                    }
                }) { "Unsupported private backup file" }
                true
            }
            TYPE_SHAREABLE -> {
                kotlin.require(schema == "1" || schema == "2" || schema == "3") { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_unsupported_backup_schema_94afd) }
                kotlin.require(profile == "shareable") { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_invalid_shareable_configuration_profile_78695) }
                val sections = manifestValue(manifest, "sections")
                if (schema == "2" || schema == "3") {
                    kotlin.require(
                        manifestValue(manifest, "privacy") == if (schema == "2") "pi-safe" else "user-selected"
                    ) {
                        throw LocalizedArgumentException(R.string.validation_backup_backupmanager_shareable_configuration_privacy_marker_is_555ab)
                    }
                    if (schema == "3") behaviorFields(manifest)
                    kotlin.require(
                        sections == "theme,suggestions" ||
                            sections == "theme,suggestions,behavior" ||
                            sections == "theme,ui,suggestions" ||
                            sections == "theme,ui,suggestions,behavior"
                    ) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_unsupported_shareable_configuration_sectio_c88e0) }
                    val expected = setOf(MANIFEST_FILE, XMLPrefsManager.XMLPrefsRoot.THEME.path,
                        XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path) +
                        (if (sections.contains(",ui,")) setOf(XMLPrefsManager.XMLPrefsRoot.UI.path) else emptySet()) +
                        (if (sections.endsWith(",behavior")) setOf(XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path) else emptySet())
                    kotlin.require(entries == expected) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_shareable_configuration_contains_unsupport_2455b) }
                    return false
                }
                kotlin.require(
                    sections == "theme,suggestions" ||
                        sections == "theme,ui,suggestions" ||
                        sections == "theme,ui,suggestions,frames" ||
                        sections == "theme,ui,suggestions,behavior" ||
                        sections == "theme,ui,suggestions,behavior,frames"
                ) {
                    throw LocalizedArgumentException(R.string.validation_backup_backupmanager_unsupported_shareable_configuration_sectio_c88e0)
                }
                val expected = if (sections == "theme,suggestions")
                    setOf(MANIFEST_FILE, XMLPrefsManager.XMLPrefsRoot.THEME.path, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path)
                else setOf(MANIFEST_FILE, *SHAREABLE_FILES) +
                    if (sections.contains(",behavior")) setOf(XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.path) else emptySet()
                kotlin.require(entries.containsAll(expected)) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_shareable_configuration_is_incomplete_13f8c) }
                val extras = entries - expected
                if (sections.endsWith(",frames")) {
                    kotlin.require(extras.contains(FrameManager.FRAME_FOLDER + "/" + FrameManager.STATE_FILE)) {
                        throw LocalizedArgumentException(R.string.validation_backup_backupmanager_shareable_frame_settings_are_incomplete_3a160)
                    }
                    kotlin.require(extras.all(FrameManager::isPortableEntry)) {
                        throw LocalizedArgumentException(R.string.validation_backup_backupmanager_shareable_configuration_contains_unsupport_2455b)
                    }
                } else {
                    kotlin.require(extras.isEmpty()) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_shareable_configuration_contains_unsupport_2455b) }
                }
                false
            }
            else -> throw LocalizedArgumentException(R.string.backup_error_unsupported)
        }
    }

    internal fun behaviorFields(manifest: String?): Set<String> {
        val value = manifestValue(manifest, "behaviorFields")
        val fields = if (value.isBlank()) emptySet() else value.split(',').toSet()
        val known = ohi.andre.consolelauncher.managers.xml.options.Behavior.entries
            .mapNotNull { it.label() }
            .toSet()
        require(fields.all { it in known }) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_shareable_configuration_contains_unknown_b_d886f) }
        return fields
    }

    @Throws(java.lang.Exception::class)
    fun isEncryptedBackup(context: Context?, uri: Uri?): Boolean {
        if (context == null || uri == null) return false
        FileOperationCancellation.check()
        val source = context.contentResolver.openInputStream(uri) ?: return false
        return BufferedInputStream(source).use { input ->
            FileOperationCancellation.track(input) { tracked ->
                val header = ByteArray(ENCRYPTED_MAGIC.size)
                var offset = 0
                while (offset < header.size) {
                    FileOperationCancellation.check()
                    val count = tracked.read(header, offset, header.size - offset)
                    if (count < 0) break
                    offset += count
                }
                offset == header.size && matchesMagic(header)
            }
        }
    }

    @Throws(java.lang.Exception::class)
    private fun createPersonalBackup(context: android.content.Context): kotlin.ByteArray {
        SpaceManager.saveActive(context)

        val out = java.io.ByteArrayOutputStream()
        val zip = java.util.zip.ZipOutputStream(out)
        try {
            ohi.andre.consolelauncher.managers.BackupManager.addTextEntry(
                zip, ohi.andre.consolelauncher.managers.BackupManager.MANIFEST_FILE,
                ("type=" + ohi.andre.consolelauncher.managers.BackupManager.TYPE_BACKUP + "\n"
                        + "schema=2\n"
                        + "profile=personal\n"
                        + "appVersion=" + BuildConfig.VERSION_NAME + "\n")
            )
            val root: java.io.File = Tuils.getFolder()
            ohi.andre.consolelauncher.managers.BackupManager.addDirectory(
                zip,
                root,
                root,
                FrameManager.bundledAssetFileNames(root)
            )
            synchronized(ohi.andre.consolelauncher.notes.NoteStore.lock) {
                for (name in personalPrivateFileNames) {
                    val file = File(context.filesDir, name)
                    if (file.exists()) addPrivateFiles(zip, context.filesDir, file)
                }
            }
            ohi.andre.consolelauncher.managers.BackupManager.addSharedPreferences(zip, context)
        } finally {
            zip.close()
        }
        return out.toByteArray()
    }

    @Throws(java.lang.Exception::class)
    private fun addDirectory(
        zip: java.util.zip.ZipOutputStream,
        root: java.io.File,
        dir: java.io.File,
        excludedFiles: kotlin.collections.Set<kotlin.String>
    ) {
        val files = dir.listFiles()
        if (files == null) return

        for (file in files) {
            FileOperationCancellation.check()
            val name = ohi.andre.consolelauncher.managers.BackupManager.relativeName(root, file)
            if (!ohi.andre.consolelauncher.managers.BackupManager.isBackupCandidate(
                    file,
                    name
                )
            ) continue
            if (file.name in excludedFiles) continue

            if (file.isDirectory()) {
                ohi.andre.consolelauncher.managers.BackupManager.addDirectory(zip, root, file, excludedFiles)
            } else if (file.isFile()) {
                ohi.andre.consolelauncher.managers.BackupManager.addFileEntry(zip, name, file)
            }
        }
    }

    internal fun isBackupCandidate(file: java.io.File, name: kotlin.String?): kotlin.Boolean {
        if (name == null || name.length == 0) return false
        if (name.startsWith(ohi.andre.consolelauncher.managers.BackupManager.SHARED_PREFS_DIR)) return false
        if (name.startsWith("spaces/.")) return false
        if (name.startsWith(".restore-") || name.startsWith(PRIVATE_FILES_DIR) ||
            name.endsWith(ohi.andre.consolelauncher.managers.BackupManager.BACKUP_SUFFIX)) return false
        if (name.startsWith("crash.txt")) return false
        return file.isDirectory() || file.isFile()
    }

    @Throws(java.lang.Exception::class)
    private fun addSharedPreferences(
        zip: java.util.zip.ZipOutputStream,
        context: android.content.Context
    ) {
        for (name in ohi.andre.consolelauncher.managers.BackupManager.PERSONAL_PREFS) {
            FileOperationCancellation.check()
            val prefs: SharedPreferences = context.getApplicationContext()
                .getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
            val values: kotlin.collections.MutableMap<kotlin.String?, *> = prefs.getAll()
            ohi.andre.consolelauncher.managers.BackupManager.addTextEntry(
                zip,
                ohi.andre.consolelauncher.managers.BackupManager.SHARED_PREFS_DIR + name + ".properties",
                ohi.andre.consolelauncher.managers.BackupManager.serializePrefs(values)
            )
        }
    }

    private fun serializePrefs(values: kotlin.collections.MutableMap<kotlin.String?, *>): kotlin.String {
        val out = java.lang.StringBuilder()
        val keys = values.keys.filterNotNull().sorted()
        for (key in keys) {
            FileOperationCancellation.check()
            val value: Any? = values.get(key)
            if (value == null) continue
            if (value is kotlin.collections.MutableSet<*>) {
                val setValues: kotlin.collections.MutableList<kotlin.String> =
                    java.util.ArrayList()
                for (item in value) {
                    if (item != null) setValues.add(item.toString())
                }
                setValues.sortWith(String.CASE_INSENSITIVE_ORDER)
                out.append("set|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(key))
                    .append("|").append(
                        ohi.andre.consolelauncher.managers.BackupManager.encode(
                            ohi.andre.consolelauncher.managers.BackupManager.joinSet(setValues)
                        )
                    ).append('\n')
            } else if (value is kotlin.Boolean) {
                out.append("boolean|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(key))
                    .append("|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(value.toString()))
                    .append('\n')
            } else if (value is Int) {
                out.append("int|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(key))
                    .append("|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(value.toString()))
                    .append('\n')
            } else if (value is kotlin.Long) {
                out.append("long|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(key))
                    .append("|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(value.toString()))
                    .append('\n')
            } else if (value is kotlin.Float) {
                out.append("float|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(key))
                    .append("|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(value.toString()))
                    .append('\n')
            } else {
                out.append("string|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(key))
                    .append("|")
                    .append(ohi.andre.consolelauncher.managers.BackupManager.encode(value.toString()))
                    .append('\n')
            }
        }
        return out.toString()
    }

    @Throws(java.lang.Exception::class)
    private fun addTextEntry(
        zip: java.util.zip.ZipOutputStream,
        name: kotlin.String?,
        text: kotlin.String
    ) {
        val entry = java.util.zip.ZipEntry(name).apply { time = 0L }
        zip.putNextEntry(entry)
        zip.write(text.toByteArray(kotlin.text.charset("UTF-8")))
        zip.closeEntry()
    }

    @Throws(java.lang.Exception::class)
    private fun addFileEntry(
        zip: java.util.zip.ZipOutputStream,
        name: kotlin.String?,
        file: java.io.File?
    ) {
        val entry = java.util.zip.ZipEntry(name)
        zip.putNextEntry(entry)
        java.io.FileInputStream(file).use { input ->
            FileOperationCancellation.copy(input, zip, MAX_BACKUP_BYTES)
        }
        zip.closeEntry()
    }

    internal fun addPrivateFiles(zip: ZipOutputStream, root: File, file: File) {
        FileOperationCancellation.check()
        if (file.isDirectory) {
            val children = requireNotNull(file.listFiles()) { "Unable to read private backup folder" }
            children.forEach { addPrivateFiles(zip, root, it) }
        } else if (file.isFile) {
            addFileEntry(zip, PRIVATE_FILES_DIR + relativeName(root, file), file)
        }
    }

    private fun restorePersonalBackup(context: Context, staging: File, includesPrivateFiles: Boolean) {
        val root = Tuils.getFolder()
        val replacements = linkedMapOf<File, File?>()
        val incoming = requireNotNull(staging.listFiles()).filter {
            it.name != MANIFEST_FILE && it.name != SHARED_PREFS_DIR.trimEnd('/') &&
                it.name != PRIVATE_FILES_DIR.trimEnd('/')
        }
        incoming.forEach { require(!it.name.startsWith(".restore-") && !it.name.endsWith(BACKUP_SUFFIX)) }
        requireNotNull(root.listFiles()).filter {
            !it.name.startsWith(".restore-") && !it.name.endsWith(BACKUP_SUFFIX)
        }.forEach { replacements[it] = null }
        incoming.forEach { replacements[File(root, it.name)] = it }
        if (includesPrivateFiles) {
            personalPrivateFileNames.forEach { name ->
                replacements[File(context.filesDir, name)] = File(staging, PRIVATE_FILES_DIR + name).takeIf { it.exists() }
            }
        }
        val prefsDir = File(staging, SHARED_PREFS_DIR)
        val preferenceSnapshots = linkedMapOf<String, String>()
        val preferences = personalSharedPreferenceNames().mapNotNull { name ->
            File(prefsDir, "$name.properties").takeIf { it.isFile }?.let { file ->
                val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                val editor = prefs.edit().clear()
                // Decode every value before any live file is moved or preference is committed.
                applyPrefs(editor, readText(file))
                preferenceSnapshots[name] = serializePrefs(prefs.all)
                editor
            }
        }
        val journal = restoreJournalFile(context)
        check(journal.parentFile!!.isDirectory || journal.parentFile!!.mkdirs()) { "Unable to create restore journal folder" }
        syncRestoreDirectory(context.noBackupFilesDir)
        BackupRestoreTransaction.replace(replacements, journal, preferenceSnapshots,
            { restoreRecoveryTargetAllowed(context, it) },
            { restorePreferenceSnapshots(context, it) }, ::syncRestoreDirectory,
            commit = {
                preferences.forEach { editor ->
                    check(editor.commit()) { "Unable to save restored preferences" }
                }
            })
    }

    internal fun restoreJournalFile(context: Context) = File(context.noBackupFilesDir, "restore-transaction/journal")

    /** Call after Tuils.init and before loading settings, language packs, Notes, or launcher state. */
    fun recoverInterruptedRestore(context: Context) {
        val journal = restoreJournalFile(context)
        if (!journal.exists()) return
        synchronized(ohi.andre.consolelauncher.notes.NoteStore.lock) {
            BackupRestoreTransaction.recover(journal,
                { restoreRecoveryTargetAllowed(context, it) },
                { restorePreferenceSnapshots(context, it) }, ::syncRestoreDirectory)
        }
    }

    internal fun restoreRecoveryTargetAllowed(context: Context, file: File): Boolean {
        val target = file.absoluteFile
        val launcherRoot = Tuils.getFolder().canonicalFile
        val privateRoot = context.filesDir.canonicalFile
        return (target.parentFile == launcherRoot && !target.name.startsWith(".restore-") &&
            !target.name.endsWith(BACKUP_SUFFIX)) ||
            (target.parentFile == privateRoot && target.name in personalPrivateFileNames)
    }

    internal fun restorePreferenceSnapshots(context: Context, snapshots: Map<String, String>) {
        require(snapshots.keys.all { it in personalSharedPreferenceNames() }) { "Unsafe restore preference snapshot" }
        // Parse all snapshots before committing any, so a corrupt journal cannot partially reset preferences.
        val editors = snapshots.map { (name, text) ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().also {
                applyPrefs(it, text)
            }
        }
        editors.forEach { check(it.commit()) { "Unable to recover original preferences" } }
    }

    internal fun syncRestoreDirectory(directory: File) {
        check(directory.isDirectory) { "Restore sync path is not a directory" }
        val descriptor = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(descriptor) } finally { android.system.Os.close(descriptor) }
    }

    private fun applyPrefs(editor: SharedPreferences.Editor, text: String) {
        for (line in text.lineSequence()) {
            if (line.isEmpty()) continue
            val parts = line.split('|', limit = 3)
            require(parts.size == 3) { "Invalid backup preference" }
            val key = String(Base64.decode(parts[1], Base64.NO_WRAP), Charsets.UTF_8)
            val value = String(Base64.decode(parts[2], Base64.NO_WRAP), Charsets.UTF_8)
            require(key.isNotEmpty()) { "Invalid backup preference key" }
            when (parts[0]) {
                "boolean" -> {
                    require(value == "true" || value == "false") { "Invalid backup boolean" }
                    editor.putBoolean(key, value.toBoolean())
                }
                "int" -> editor.putInt(key, value.toInt())
                "long" -> editor.putLong(key, value.toLong())
                "float" -> editor.putFloat(key, value.toFloat().also { require(it.isFinite()) })
                "set" -> editor.putStringSet(key, splitSet(value))
                "string" -> editor.putString(key, value)
                else -> throw IllegalArgumentException("Invalid backup preference type")
            }
        }
    }

    @Throws(java.lang.Exception::class)
    private fun backupInputStream(
        context: android.content.Context,
        uri: android.net.Uri,
        password: kotlin.String?
    ): java.io.InputStream {
        val bytes = ohi.andre.consolelauncher.managers.BackupManager.readUri(context, uri)
        if (!ohi.andre.consolelauncher.managers.BackupManager.startsWithMagic(bytes)) {
            return java.io.ByteArrayInputStream(bytes)
        }
        kotlin.require(!(password == null || password.length == 0)) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_backup_password_is_required_9706d) }
        return java.io.ByteArrayInputStream(
            ohi.andre.consolelauncher.managers.BackupManager.decrypt(
                bytes,
                password
            )
        )
    }

    @Throws(java.lang.Exception::class)
    private fun encrypt(plain: kotlin.ByteArray?, password: kotlin.String): kotlin.ByteArray {
        val salt =
            ohi.andre.consolelauncher.managers.BackupManager.randomBytes(ohi.andre.consolelauncher.managers.BackupManager.SALT_BYTES)
        val iv =
            ohi.andre.consolelauncher.managers.BackupManager.randomBytes(ohi.andre.consolelauncher.managers.BackupManager.IV_BYTES)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            ohi.andre.consolelauncher.managers.BackupManager.key(password, salt),
            javax.crypto.spec.GCMParameterSpec(
                ohi.andre.consolelauncher.managers.BackupManager.GCM_TAG_BITS,
                iv
            )
        )
        val encrypted = cipher.doFinal(plain)

        val out = java.io.ByteArrayOutputStream()
        out.write(ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC)
        out.write(salt)
        out.write(iv)
        out.write(encrypted)
        return out.toByteArray()
    }

    @Throws(java.lang.Exception::class)
    private fun decrypt(encrypted: kotlin.ByteArray, password: kotlin.String): kotlin.ByteArray? {
        val headerBytes =
            ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC.size + ohi.andre.consolelauncher.managers.BackupManager.SALT_BYTES + ohi.andre.consolelauncher.managers.BackupManager.IV_BYTES
        kotlin.require(
            !(encrypted.size <= headerBytes || !ohi.andre.consolelauncher.managers.BackupManager.startsWithMagic(
                encrypted
            ))
        ) { throw LocalizedArgumentException(R.string.validation_backup_backupmanager_encrypted_backup_is_incomplete_777d0) }

        val salt = kotlin.ByteArray(ohi.andre.consolelauncher.managers.BackupManager.SALT_BYTES)
        val iv = kotlin.ByteArray(ohi.andre.consolelauncher.managers.BackupManager.IV_BYTES)
        java.lang.System.arraycopy(
            encrypted,
            ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC.size,
            salt,
            0,
            ohi.andre.consolelauncher.managers.BackupManager.SALT_BYTES
        )
        java.lang.System.arraycopy(
            encrypted,
            ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC.size + ohi.andre.consolelauncher.managers.BackupManager.SALT_BYTES,
            iv,
            0,
            ohi.andre.consolelauncher.managers.BackupManager.IV_BYTES
        )

        val payloadStart = headerBytes
        val payload = kotlin.ByteArray(encrypted.size - payloadStart)
        java.lang.System.arraycopy(encrypted, payloadStart, payload, 0, payload.size)

        try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                ohi.andre.consolelauncher.managers.BackupManager.key(password, salt),
                javax.crypto.spec.GCMParameterSpec(
                    ohi.andre.consolelauncher.managers.BackupManager.GCM_TAG_BITS,
                    iv
                )
            )
            return cipher.doFinal(payload)
        } catch (e: java.lang.Exception) {
            throw LocalizedArgumentException(R.string.backup_error_password)
        }
    }

    @Throws(java.lang.Exception::class)
    private fun key(
        password: kotlin.String,
        salt: kotlin.ByteArray?
    ): javax.crypto.spec.SecretKeySpec {
        val spec = javax.crypto.spec.PBEKeySpec(
            password.toCharArray(),
            salt,
            ohi.andre.consolelauncher.managers.BackupManager.KDF_ITERATIONS,
            ohi.andre.consolelauncher.managers.BackupManager.KEY_BITS
        )
        var factory: javax.crypto.SecretKeyFactory
        try {
            factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (e: java.lang.Exception) {
            factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        return javax.crypto.spec.SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES")
    }

    private fun randomBytes(count: Int): kotlin.ByteArray {
        val bytes = kotlin.ByteArray(count)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes
    }

    @Throws(java.lang.Exception::class)
    private fun readUri(context: Context, uri: Uri): ByteArray {
        val raw = requireNotNull(context.contentResolver.openInputStream(uri)) {
            throw LocalizedArgumentException(R.string.validation_backup_backupmanager_unable_to_open_backup_package_bdca3)
        }
        val out = ByteArrayOutputStream()
        BufferedInputStream(raw).use { input -> FileOperationCancellation.copy(input, out, MAX_BACKUP_BYTES) }
        return out.toByteArray()
    }

    private fun startsWithMagic(bytes: kotlin.ByteArray?): kotlin.Boolean {
        return bytes != null && bytes.size >= ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC.size && ohi.andre.consolelauncher.managers.BackupManager.matchesMagic(
            bytes
        )
    }

    private fun matchesMagic(bytes: kotlin.ByteArray?): kotlin.Boolean {
        if (bytes == null || bytes.size < ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC.size) return false
        for (i in ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC.indices) {
            if (bytes[i] != ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC[i]) return false
        }
        return true
    }

    private fun manifestValue(manifest: kotlin.String?, key: kotlin.String?): kotlin.String {
        if (manifest == null || key == null) return ""
        val prefix = key + "="
        val lines = manifest.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (line in lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length).trim { it <= ' ' }
            }
        }
        return ""
    }

    @Throws(java.lang.Exception::class)
    private fun readText(file: File?): String {
        val out = ByteArrayOutputStream()
        FileInputStream(file).use { input -> FileOperationCancellation.copy(input, out, MAX_BACKUP_BYTES) }
        return out.toString("UTF-8")
    }

    private fun joinSet(values: kotlin.collections.List<kotlin.String>): kotlin.String {
        val out = java.lang.StringBuilder()
        for (value in values) {
            if (out.length > 0) out.append('\u001F')
            out.append(value)
        }
        return out.toString()
    }

    private fun splitSet(value: kotlin.String?): kotlin.collections.MutableSet<kotlin.String?> {
        val set: kotlin.collections.MutableSet<kotlin.String?> = java.util.HashSet<kotlin.String?>()
        if (value == null || value.length == 0) return set
        val parts: kotlin.Array<kotlin.String?> =
            value.split('\u001F'.toString().toRegex()).toTypedArray()
        java.util.Collections.addAll<kotlin.String?>(set, *parts)
        return set
    }

    private fun encode(value: kotlin.String?): kotlin.String? {
        var value = value
        if (value == null) value = ""
        return android.util.Base64.encodeToString(value.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private fun relativeName(root: java.io.File, file: java.io.File): kotlin.String {
        val rootPath = root.getAbsolutePath()
        val filePath = file.getAbsolutePath()
        if (!filePath.startsWith(rootPath)) return file.getName()
        var name = filePath.substring(rootPath.length)
        if (name.startsWith(java.io.File.separator)) name = name.substring(1)
        return name.replace(java.io.File.separatorChar, '/')
    }

    private fun isSafeEntry(name: kotlin.String?): kotlin.Boolean {
        return name != null && name.length > 0 && !name.startsWith("/") && !name.contains("\\") && !name.contains(
            ".."
        )
    }
}
