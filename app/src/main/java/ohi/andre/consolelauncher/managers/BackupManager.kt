package ohi.andre.consolelauncher.managers

import ohi.andre.consolelauncher.BuildConfig
import android.content.Context
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
import java.util.Map
import java.util.Set
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
import ohi.andre.consolelauncher.managers.termux.TermuxAppManager
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetReminderManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils

object BackupManager {
    private const val BACKUP_SUFFIX = ".retui-backup"
    private const val MANIFEST_FILE = "manifest.txt"
    private const val SHARED_PREFS_DIR = "shared_prefs/"
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
        XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path
    )
    private val PERSONAL_PREFS = kotlin.arrayOf<kotlin.String?>(
        "ui",
        "apps",
        PinnedShortcutManager.PREFS,
        "retui_modules",
        "retui_reminders",
        LuaWidgetReminderManager.PREFS,
        TermuxAppManager.PREFS,
        "retui_module_prompt",
        "retui_callback_auth",
        "pomodoro_state",
        RetuiCreditManager.PREFS,
        SpaceManager.PREFS,
        GuideManager.PREFS,
        PodcastManager.PREFS,
        "changelogPrefs"
    )

    fun defaultBackupName(): kotlin.String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return "retui-backup-" + stamp + ohi.andre.consolelauncher.managers.BackupManager.BACKUP_SUFFIX
    }

    @JvmOverloads
    fun defaultShareableConfigurationName(sourceName: kotlin.String? = null): kotlin.String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val source = ohi.andre.consolelauncher.managers.BackupManager.fileSafeName(sourceName)
        if (source.length > 0) {
            return "retui-config-" + source + "-" + stamp + ohi.andre.consolelauncher.managers.BackupManager.BACKUP_SUFFIX
        }
        return "retui-config-" + stamp + ohi.andre.consolelauncher.managers.BackupManager.BACKUP_SUFFIX
    }

    @JvmOverloads
    @Throws(java.lang.Exception::class)
    fun exportBackup(
        context: android.content.Context,
        uri: android.net.Uri,
        password: kotlin.String? = null
    ) {
        kotlin.require(!(context == null || uri == null)) { "Backup destination is required" }

        val out: java.io.OutputStream =
            java.io.BufferedOutputStream(context.getContentResolver().openOutputStream(uri, "w"))
        kotlin.requireNotNull(out) { "Unable to open backup destination" }

        val backup = ohi.andre.consolelauncher.managers.BackupManager.createPersonalBackup(context)
        try {
            if (password != null && password.length > 0) {
                out.write(
                    ohi.andre.consolelauncher.managers.BackupManager.encrypt(
                        backup,
                        password
                    )
                )
            } else {
                out.write(backup)
            }
        } finally {
            out.close()
        }
    }

    @JvmOverloads
    @Throws(java.lang.Exception::class)
    fun exportShareableConfiguration(
        context: android.content.Context,
        uri: android.net.Uri,
        presetName: kotlin.String? = null
    ) {
        kotlin.require(!(context == null || uri == null)) { "Configuration destination is required" }

        val preset = if (presetName == null) null else presetName.trim { it <= ' ' }
        var sourceRoot: java.io.File = Tuils.getFolder()
        var sourceType = "current"
        var sourceLabel: kotlin.String? = null
        if (preset != null && preset.length > 0) {
            sourceRoot = PresetManager.getSavedPresetFolder(preset)
            kotlin.require(sourceRoot.isDirectory()) { "Preset not found" }
            sourceType = "preset"
            sourceLabel = sourceRoot.getName()
        }

        val out: java.io.OutputStream =
            java.io.BufferedOutputStream(context.getContentResolver().openOutputStream(uri, "w"))
        kotlin.requireNotNull(out) { "Unable to open configuration destination" }

        val zip = java.util.zip.ZipOutputStream(out)
        try {
            ohi.andre.consolelauncher.managers.BackupManager.addTextEntry(
                zip, ohi.andre.consolelauncher.managers.BackupManager.MANIFEST_FILE,
                ("type=" + ohi.andre.consolelauncher.managers.BackupManager.TYPE_SHAREABLE + "\n"
                        + "schema=1\n"
                        + "profile=shareable\n"
                        + "appVersion=" + BuildConfig.VERSION_NAME + "\n"
                        + "source=" + sourceType + "\n"
                        + (if (sourceLabel == null) "" else "presetName=" + ohi.andre.consolelauncher.managers.BackupManager.manifestSafeValue(
                    sourceLabel
                ) + "\n")
                        + "sections=theme,suggestions\n")
            )
            for (name in ohi.andre.consolelauncher.managers.BackupManager.SHAREABLE_FILES) {
                val file = java.io.File(sourceRoot, name)
                kotlin.require(file.isFile()) { if (sourceLabel == null) "Configuration is incomplete" else "Preset is incomplete" }
                ohi.andre.consolelauncher.managers.BackupManager.addFileEntry(zip, name, file)
            }
        } finally {
            zip.close()
        }
    }

    @JvmOverloads
    @Throws(java.lang.Exception::class)
    fun importBackup(
        context: android.content.Context,
        uri: android.net.Uri,
        password: kotlin.String? = null
    ) {
        kotlin.require(!(context == null || uri == null)) { "Backup package is required" }

        val tempDir: java.io.File = java.io.File(Tuils.getFolder(), ".restore-importing")
        if (tempDir.exists()) {
            Tuils.delete(tempDir)
        }
        kotlin.check(tempDir.mkdirs()) { "Unable to create restore folder" }

        var hasManifest = false
        var manifest: kotlin.String? = null
        var totalBytes: kotlin.Long = 0
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
                val name = entry!!.getName()
                if (entry.isDirectory()) continue
                kotlin.require(ohi.andre.consolelauncher.managers.BackupManager.isSafeEntry(name)) { "Unsafe backup package" }

                if (ohi.andre.consolelauncher.managers.BackupManager.MANIFEST_FILE == name) {
                    hasManifest = true
                }

                val out = java.io.File(tempDir, name)
                val parent = out.getParentFile()
                kotlin.check(!(parent != null && !parent.exists() && !parent.mkdirs())) { "Unable to restore backup folder" }

                val stream = java.io.FileOutputStream(out, false)
                try {
                    var read: Int
                    val manifestOut =
                        if (ohi.andre.consolelauncher.managers.BackupManager.MANIFEST_FILE == name) java.io.ByteArrayOutputStream() else null
                    while ((zip.read(buffer).also { read = it }) != -1) {
                        totalBytes += read.toLong()
                        kotlin.require(totalBytes <= ohi.andre.consolelauncher.managers.BackupManager.MAX_BACKUP_BYTES) { "Backup package is too large" }
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

        kotlin.require(hasManifest) { "Backup package is incomplete" }

        val type = ohi.andre.consolelauncher.managers.BackupManager.manifestValue(manifest, "type")
        kotlin.require(!(ohi.andre.consolelauncher.managers.BackupManager.TYPE_BACKUP != type && ohi.andre.consolelauncher.managers.BackupManager.TYPE_SHAREABLE != type)) { "Unsupported backup package" }
        val personal = ohi.andre.consolelauncher.managers.BackupManager.TYPE_BACKUP == type
        if (personal) {
            ohi.andre.consolelauncher.managers.BackupManager.clearForPersonalRestore(Tuils.getFolder())
        }
        ohi.andre.consolelauncher.managers.BackupManager.restoreDirectory(
            tempDir,
            Tuils.getFolder(),
            personal
        )
        if (personal) {
            ohi.andre.consolelauncher.managers.BackupManager.restoreSharedPreferences(
                context,
                tempDir
            )
            SpaceManager.ensureInitialized(context)
        }
        Tuils.delete(tempDir)
    }

    @Throws(java.lang.Exception::class)
    fun isEncryptedBackup(
        context: android.content.Context?,
        uri: android.net.Uri?
    ): kotlin.Boolean {
        if (context == null || uri == null) return false
        val `in`: java.io.InputStream =
            java.io.BufferedInputStream(context.getContentResolver().openInputStream(uri))
        if (`in` == null) return false
        try {
            val header =
                kotlin.ByteArray(ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC.size)
            val read = `in`.read(header)
            return read == ohi.andre.consolelauncher.managers.BackupManager.ENCRYPTED_MAGIC.size && ohi.andre.consolelauncher.managers.BackupManager.matchesMagic(
                header
            )
        } finally {
            `in`.close()
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
                        + "schema=1\n"
                        + "profile=personal\n"
                        + "appVersion=" + BuildConfig.VERSION_NAME + "\n")
            )
            val root: java.io.File = Tuils.getFolder()
            ohi.andre.consolelauncher.managers.BackupManager.addDirectory(zip, root, root)
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
        dir: java.io.File
    ) {
        val files = dir.listFiles()
        if (files == null) return

        for (file in files) {
            val name = ohi.andre.consolelauncher.managers.BackupManager.relativeName(root, file)
            if (!ohi.andre.consolelauncher.managers.BackupManager.isBackupCandidate(
                    file,
                    name
                )
            ) continue

            if (file.isDirectory()) {
                ohi.andre.consolelauncher.managers.BackupManager.addDirectory(zip, root, file)
            } else if (file.isFile()) {
                ohi.andre.consolelauncher.managers.BackupManager.addFileEntry(zip, name, file)
            }
        }
    }

    private fun isBackupCandidate(file: java.io.File, name: kotlin.String?): kotlin.Boolean {
        if (name == null || name.length == 0) return false
        if (name.startsWith(ohi.andre.consolelauncher.managers.BackupManager.SHARED_PREFS_DIR)) return false
        if (name.startsWith("spaces/.")) return false
        if (name.startsWith(".restore-importing") || name.endsWith(ohi.andre.consolelauncher.managers.BackupManager.BACKUP_SUFFIX)) return false
        if (name.startsWith("crash.txt")) return false
        return file.isDirectory() || file.isFile()
    }

    private fun fileSafeName(value: kotlin.String?): kotlin.String {
        if (value == null) return ""
        val trimmed = value.trim { it <= ' ' }.lowercase()
        if (trimmed.length == 0) return ""
        return trimmed.replace("[^a-z0-9._-]+".toRegex(), "-").replace("^-+|-+$".toRegex(), "")
    }

    private fun manifestSafeValue(value: kotlin.String?): kotlin.String {
        if (value == null) return ""
        return value.replace('\r', ' ').replace('\n', ' ').trim { it <= ' ' }
    }

    @Throws(java.lang.Exception::class)
    private fun addSharedPreferences(
        zip: java.util.zip.ZipOutputStream,
        context: android.content.Context
    ) {
        for (name in ohi.andre.consolelauncher.managers.BackupManager.PERSONAL_PREFS) {
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
        val entry = java.util.zip.ZipEntry(name)
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
        val `in` = java.io.FileInputStream(file)
        val buffer = kotlin.ByteArray(8192)
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

    @Throws(java.lang.Exception::class)
    private fun restoreDirectory(
        source: java.io.File,
        target: java.io.File?,
        replaceExisting: kotlin.Boolean
    ) {
        val files = source.listFiles()
        if (files == null) return

        for (file in files) {
            if (ohi.andre.consolelauncher.managers.BackupManager.MANIFEST_FILE == file.getName()) continue
            val relative =
                ohi.andre.consolelauncher.managers.BackupManager.relativeName(source, file)
            if (relative.startsWith(ohi.andre.consolelauncher.managers.BackupManager.SHARED_PREFS_DIR)) continue
            val dest = java.io.File(
                target,
                ohi.andre.consolelauncher.managers.BackupManager.relativeName(source, file)
            )
            if (file.isDirectory()) {
                kotlin.check(!(!dest.exists() && !dest.mkdirs())) { "Unable to restore folder: " + dest.getName() }
                ohi.andre.consolelauncher.managers.BackupManager.restoreDirectory(
                    file,
                    dest,
                    replaceExisting
                )
            } else if (file.isFile()) {
                val parent = dest.getParentFile()
                kotlin.check(!(parent != null && !parent.exists() && !parent.mkdirs())) { "Unable to restore folder: " + parent!!.getName() }
                if (!replaceExisting && dest.exists()) {
                    Tuils.insertOld(dest)
                }
                ohi.andre.consolelauncher.managers.BackupManager.copyFile(file, dest)
            }
        }
    }

    @Throws(java.lang.Exception::class)
    private fun restoreSharedPreferences(context: android.content.Context, tempDir: java.io.File?) {
        val prefsDir =
            java.io.File(tempDir, ohi.andre.consolelauncher.managers.BackupManager.SHARED_PREFS_DIR)
        if (!prefsDir.isDirectory()) return
        for (name in ohi.andre.consolelauncher.managers.BackupManager.PERSONAL_PREFS) {
            val file = java.io.File(prefsDir, name + ".properties")
            if (!file.isFile()) continue
            val editor: SharedPreferences.Editor = context.getApplicationContext()
                .getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
                .edit()
                .clear()
            ohi.andre.consolelauncher.managers.BackupManager.applyPrefs(
                editor,
                ohi.andre.consolelauncher.managers.BackupManager.readText(file)
            )
            editor.apply()
        }
    }

    private fun applyPrefs(editor: SharedPreferences.Editor, text: kotlin.String) {
        val lines = text.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (line in lines) {
            if (line.length == 0) continue
            val parts: kotlin.Array<kotlin.String?> =
                line.split("\\|".toRegex(), limit = 3).toTypedArray()
            if (parts.size != 3) continue
            val type = parts[0]
            val key = ohi.andre.consolelauncher.managers.BackupManager.decode(parts[1])
            val value = ohi.andre.consolelauncher.managers.BackupManager.decode(parts[2])
            try {
                if ("boolean" == type) {
                    editor.putBoolean(key, value.toBoolean())
                } else if ("int" == type) {
                    editor.putInt(key, value.toInt())
                } else if ("long" == type) {
                    editor.putLong(key, value.toLong())
                } else if ("float" == type) {
                    editor.putFloat(key, value.toFloat())
                } else if ("set" == type) {
                    editor.putStringSet(
                        key,
                        ohi.andre.consolelauncher.managers.BackupManager.splitSet(value)
                    )
                } else {
                    editor.putString(key, value)
                }
            } catch (ignored: java.lang.Exception) {
            }
        }
    }

    private fun clearForPersonalRestore(root: java.io.File) {
        val files = root.listFiles()
        if (files == null) return
        for (file in files) {
            val name = file.getName()
            if (name.startsWith(".restore-importing") || name.endsWith(ohi.andre.consolelauncher.managers.BackupManager.BACKUP_SUFFIX)) continue
            Tuils.delete(file)
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
        kotlin.require(!(password == null || password.length == 0)) { "Backup password is required" }
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
        ) { "Encrypted backup is incomplete" }

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
            throw java.lang.IllegalArgumentException("Backup password is incorrect or the file was changed")
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
    private fun readUri(context: android.content.Context, uri: android.net.Uri): kotlin.ByteArray {
        val `in`: java.io.InputStream =
            java.io.BufferedInputStream(context.getContentResolver().openInputStream(uri))
        kotlin.requireNotNull(`in`) { "Unable to open backup package" }
        val out = java.io.ByteArrayOutputStream()
        val buffer = kotlin.ByteArray(8192)
        var total: kotlin.Long = 0
        try {
            var read: Int
            while ((`in`.read(buffer).also { read = it }) != -1) {
                total += read.toLong()
                kotlin.require(total <= ohi.andre.consolelauncher.managers.BackupManager.MAX_BACKUP_BYTES) { "Backup package is too large" }
                out.write(buffer, 0, read)
            }
        } finally {
            `in`.close()
        }
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
    private fun readText(file: java.io.File?): kotlin.String {
        val out = java.io.ByteArrayOutputStream()
        val `in`: java.io.InputStream = java.io.BufferedInputStream(java.io.FileInputStream(file))
        val buffer = kotlin.ByteArray(4096)
        try {
            var read: Int
            while ((`in`.read(buffer).also { read = it }) != -1) {
                out.write(buffer, 0, read)
            }
        } finally {
            `in`.close()
        }
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

    private fun decode(value: kotlin.String?): kotlin.String {
        try {
            return kotlin.text.String(
                android.util.Base64.decode(
                    value,
                    android.util.Base64.NO_WRAP
                )
            )
        } catch (e: java.lang.Exception) {
            return ""
        }
    }

    @Throws(java.lang.Exception::class)
    private fun copyFile(source: java.io.File?, dest: java.io.File?) {
        val `in`: java.io.InputStream = java.io.BufferedInputStream(java.io.FileInputStream(source))
        val out: java.io.OutputStream =
            java.io.BufferedOutputStream(java.io.FileOutputStream(dest, false))
        val buffer = kotlin.ByteArray(8192)
        try {
            var read: Int
            while ((`in`.read(buffer).also { read = it }) != -1) {
                out.write(buffer, 0, read)
            }
        } finally {
            `in`.close()
            out.close()
        }
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
