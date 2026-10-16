package ohi.andre.consolelauncher.calculator

import android.content.Context
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/** All downloads enter a private staging directory; only a validated pack can become active. */
object CalculatorPackStore {
    const val EXTENSION = ".retui.calc.engine"
    const val MIME = "application/vnd.retui.calculator-engine+zip"
    const val MAX_ARCHIVE_BYTES = 32L * 1024 * 1024
    private val lock = Any()
    private val stores = HashMap<String, CalculatorPackStorage>()
    private val generation = AtomicLong()
    private fun storage(context: Context): CalculatorPackStorage {
        val root = File(context.filesDir, "calculator-packs")
        return synchronized(stores) { stores.getOrPut(root.absolutePath) { CalculatorPackStorage(root) } }
    }

    fun activeDirectory(context: Context): File? = storage(context).activeDirectory()
    fun installedVersion(context: Context): String? = storage(context).installedVersion()
    fun downloadDirectory(context: Context): File {
        Tuils.init(context)
        val launcher = Tuils.getFolder()
        check(launcher.isAbsolute && launcher.isDirectory) { "Launcher storage is unavailable" }
        return File(launcher, "calculator-engines")
    }
    fun rollback(context: Context, failedDirectory: File): Boolean = storage(context).rollback(failedDirectory)
    fun install(context: Context, input: InputStream, cancelled: () -> Boolean = { false },
                progress: (Long, Long) -> Unit = { _, _ -> }): String = synchronized(lock) {
        storage(context).install(input, cancelled, progress)
    }
    fun remove(context: Context) {
        generation.incrementAndGet()
        synchronized(lock) { storage(context).remove() }
    }

    fun downloadAndInstall(context: Context, catalogUrl: String, cancelled: () -> Boolean = { false },
                           progress: (Long, Long) -> Unit = { _, _ -> }): String {
        val started = generation.get()
        val shouldCancel = { cancelled() || generation.get() != started }
        return synchronized(lock) {
        val catalogUri = safeCatalogUri(catalogUrl)
        val catalogBytes = fetch(catalogUri, 64 * 1024L, shouldCancel) { _, _ -> }
        val catalog = JSONObject(String(catalogBytes, Charsets.UTF_8))
        require(catalog.getInt("schema") == 1) { "Unsupported calculator catalogue" }
        val version = catalog.getString("version")
        val packUri = safePackUri(catalogUri, catalog.getString("url"))
        val expected = catalog.getString("sha256")
        require(expected.matches(Regex("[a-f0-9]{64}"))) { "Invalid calculator checksum" }
        val bytes = fetch(packUri, MAX_ARCHIVE_BYTES, shouldCancel, progress)
        CalculatorPackArchiveStorage(downloadDirectory(context)).save(version, bytes, expected, shouldCancel)
        // Install the verified bytes, not a shared-storage file that could change after download.
        storage(context).install(bytes.inputStream(), shouldCancel, progress, version)
        }
    }

    internal fun safeCatalogUri(value: String): URI = URI(value).also {
        require(it.scheme == "https" && !it.host.isNullOrEmpty() && it.port == -1 &&
            it.userInfo == null && it.query == null && it.fragment == null && it.rawPath == it.path &&
            !it.path.contains('\\') && it.normalize() == it && it.path.endsWith("/catalog.json")) {
            "Calculator catalogue must use a trusted HTTPS address"
        }
    }
    internal fun safePackUri(catalog: URI, value: String): URI = catalog.resolve(value).also {
        val folder = catalog.path.substringBeforeLast('/') + "/"
        require(it.scheme == "https" && it.host == catalog.host && it.port == -1 &&
            it.userInfo == null && it.query == null && it.fragment == null && it.rawPath == it.path &&
            !it.path.contains('\\') && it.normalize() == it && it.path.startsWith(folder) &&
            it.path.endsWith(EXTENSION)) { "Calculator pack must come from the catalogue directory" }
    }
    private fun fetch(uri: URI, limit: Long, cancelled: () -> Boolean,
                      progress: (Long, Long) -> Unit): ByteArray {
        checkCancelled(cancelled)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 1_000
        connection.readTimeout = 1_000
        connection.setRequestProperty("Accept-Encoding", "identity")
        val deadline = System.nanoTime() + 60_000_000_000L
        try {
            require(connection.responseCode == 200) { "Calculator download failed (${connection.responseCode})" }
            val size = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1
            require(size <= limit) { "Calculator download is too large" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    checkCancelled(cancelled)
                    require(System.nanoTime() < deadline) { "Calculator download timed out" }
                    val count = try { input.read(buffer) } catch (_: SocketTimeoutException) { continue }
                    if (count < 0) break
                    require(output.size().toLong() + count <= limit) { "Calculator download is too large" }
                    output.write(buffer, 0, count)
                    progress(output.size().toLong(), size)
                }
                require(size < 0 || output.size().toLong() == size) { "Incomplete calculator download" }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
    internal fun checkCancelled(cancelled: () -> Boolean) {
        if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Calculator installation cancelled")
    }
    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Retains the original download; active executable code stays in the separate private store. */
internal class CalculatorPackArchiveStorage(private val directory: File) {
    fun save(version: String, bytes: ByteArray, expectedSha256: String,
             cancelled: () -> Boolean = { false }): File {
        require(version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}"))) { "Invalid calculator version" }
        require(bytes.size.toLong() <= CalculatorPackStore.MAX_ARCHIVE_BYTES &&
            expectedSha256.matches(Regex("[a-f0-9]{64}")) && CalculatorPackStore.sha256(bytes) == expectedSha256) {
            "Calculator download does not match its checksum"
        }
        CalculatorPackStore.checkCancelled(cancelled)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create calculator download directory" }
        val destination = File(directory, "qalculate-$version${CalculatorPackStore.EXTENSION}")
        val pending = File(directory, ".download-${UUID.randomUUID()}")
        try {
            FileOutputStream(pending).use { output ->
                var offset = 0
                while (offset < bytes.size) {
                    CalculatorPackStore.checkCancelled(cancelled)
                    val count = minOf(8192, bytes.size - offset)
                    output.write(bytes, offset, count)
                    offset += count
                }
                output.fd.sync()
            }
            CalculatorPackStore.checkCancelled(cancelled)
            check(pending.renameTo(destination)) { "Cannot save calculator engine archive" }
            return destination
        } finally { pending.delete() }
    }
}

/** Pure filesystem implementation kept separate so transaction and ZIP boundaries are unit tested. */
internal class CalculatorPackStorage(private val root: File) {
    private val payloads = setOf("engine.js", "engine.wasm", "LICENSE.txt", "NOTICE.txt")
    private val allowed = payloads + "manifest.json"
    private val pointerName = Regex("pack-[a-f0-9-]{36}")
    private val mutationLock = Any()
    private val verified = ConcurrentHashMap<String, String>()

    @Synchronized fun activeDirectory(): File? {
        val active = readPointer("active")
        if (active != null) return active
        return readPointer("previous")
    }
    @Synchronized fun installedVersion(): String? = activeDirectory()?.let { manifest(it).getString("version") }
    private fun readPointer(name: String): File? = runCatching {
        val value = File(root, name).readText()
        require(pointerName.matches(value))
        File(root, value).also { validate(it) }
    }.getOrNull()

    fun install(input: InputStream, cancelled: () -> Boolean = { false },
                progress: (Long, Long) -> Unit = { _, _ -> }, expectedVersion: String? = null): String =
        synchronized(mutationLock) { installLocked(input, cancelled, progress, expectedVersion) }

    private fun installLocked(input: InputStream, cancelled: () -> Boolean,
                progress: (Long, Long) -> Unit, expectedVersion: String?): String {
        check(root.isDirectory || root.mkdirs()) { "Cannot create calculator storage" }
        root.listFiles()?.filter { it.name.startsWith(".stage-") || it.name.startsWith(".archive-") ||
            it.name == "active.tmp" || it.name == "previous.tmp" }?.forEach { it.deleteRecursively() }
        val token = UUID.randomUUID().toString()
        val stage = File(root, ".stage-$token")
        val archive = File(root, ".archive-$token")
        check(stage.mkdir())
        var committed = false
        val target = File(root, "pack-$token")
        try {
            input.use { source ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        CalculatorPackStore.checkCancelled(cancelled)
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= CalculatorPackStore.MAX_ARCHIVE_BYTES) { "Calculator pack is too large" }
                        output.write(buffer, 0, count)
                        progress(total, -1)
                    }
                    output.fd.sync()
                }
            }
            val seen = HashSet<String>()
            var expanded = 0L
            ZipInputStream(archive.inputStream()).use { zip ->
                while (true) {
                    CalculatorPackStore.checkCancelled(cancelled)
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in allowed && seen.add(entry.name)) {
                        "Unexpected or duplicate calculator file"
                    }
                    FileOutputStream(File(stage, entry.name)).use { output ->
                        val buffer = ByteArray(8192)
                        var size = 0L
                        while (true) {
                            CalculatorPackStore.checkCancelled(cancelled)
                            val count = zip.read(buffer)
                            if (count < 0) break
                            size += count; expanded += count
                            val limit = when (entry.name) {
                                "engine.wasm" -> 32L * 1024 * 1024
                                "engine.js" -> 4L * 1024 * 1024
                                else -> 256L * 1024
                            }
                            require(size <= limit && expanded <= 40L * 1024 * 1024) { "Calculator pack expands beyond its size limit" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
            }
            require(seen == allowed) { "Calculator pack is incomplete" }
            validate(stage)
            val version = manifest(stage).getString("version")
            require(expectedVersion == null || expectedVersion == version) { "Calculator version differs from its catalogue" }
            CalculatorPackStore.checkCancelled(cancelled)
            syncDirectory(stage)
            check(stage.renameTo(target)) { "Cannot stage calculator pack" }
            syncDirectory(root)
            val old = synchronized(this) {
                val previous = activeDirectory()
                if (previous != null) writePointer("previous", previous.name)
                CalculatorPackStore.checkCancelled(cancelled)
                verified[stage.absolutePath]?.let { verified[target.absolutePath] = it }
                writePointer("active", target.name)
                committed = true
                previous
            }
            // Keep the previous immutable directory until a later successful update or explicit removal.
            val keep = setOf(target.name, old?.name)
            root.listFiles()?.filter { pointerName.matches(it.name) && it.name !in keep }?.forEach {
                it.deleteRecursively()
                verified.remove(it.absolutePath)
            }
            return version
        } finally {
            archive.delete()
            stage.deleteRecursively()
            verified.remove(stage.absolutePath)
            if (!committed) {
                target.deleteRecursively()
                verified.remove(target.absolutePath)
            }
        }
    }

    fun rollback(failedDirectory: File): Boolean {
        val restored = synchronized(this) {
            val pointer = File(root, "active")
            val name = runCatching { pointer.readText() }.getOrNull()
            if (name == null || !pointerName.matches(name) ||
                File(root, name).absolutePath != failedDirectory.absolutePath) return false
            val previous = readPointer("previous")?.takeIf { it.absolutePath != failedDirectory.absolutePath }
            if (previous != null) {
                writePointer("active", previous.name)
            } else {
                check(pointer.delete()) { "Cannot disable failed calculator pack" }
                val oldPointer = File(root, "previous")
                check(!oldPointer.exists() || oldPointer.delete()) { "Cannot disable failed calculator recovery record" }
                syncDirectory(root)
            }
            previous != null
        }
        failedDirectory.deleteRecursively()
        verified.remove(failedDirectory.absolutePath)
        return restored
    }

    fun remove() = synchronized(mutationLock) {
        synchronized(this) {
            // Readers see no runtime before its files are removed.
            for (name in listOf("active", "previous")) {
                val pointer = File(root, name)
                check(!pointer.exists() || pointer.delete()) { "Cannot remove calculator activation record" }
            }
            syncDirectory(root)
        }
        root.listFiles()?.forEach { it.deleteRecursively() }
        verified.clear()
    }
    private fun manifest(folder: File): JSONObject {
        val text = File(folder, "manifest.json").readText()
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') quoted = false
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 4) { "Calculator manifest is nested too deeply" } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid calculator manifest" } }
            }
        }
        return JSONObject(text)
    }
    private fun validate(folder: File) {
        require(folder.isDirectory && folder.list()?.toSet() == allowed) { "Incomplete calculator pack" }
        val fingerprint = allowed.sorted().joinToString(";") { name ->
            val file = File(folder, name)
            "$name:${file.length()}:${file.lastModified()}"
        }
        if (verified[folder.absolutePath] == fingerprint) return
        val spec = manifest(folder)
        require(spec.getInt("schema") == 1 && spec.getString("kind") == "qalculate" &&
            spec.getInt("runtimeApi") == 1 && spec.getString("version").matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}"))) {
            "Incompatible calculator pack"
        }
        val hashes = spec.getJSONObject("files")
        require(hashes.keys().asSequence().toSet() == payloads) { "Invalid calculator file manifest" }
        for (name in payloads) {
            val expected = hashes.getString(name)
            require(expected.matches(Regex("[a-f0-9]{64}")) &&
                CalculatorPackStore.sha256(File(folder, name).readBytes()) == expected) { "Calculator file checksum failed: $name" }
        }
        val magic = File(folder, "engine.wasm").inputStream().use { input -> ByteArray(8).also { require(input.read(it) == 8) } }
        require(magic.contentEquals(byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0))) { "Invalid WebAssembly calculator" }
        verified[folder.absolutePath] = fingerprint
    }
    private fun writePointer(name: String, value: String) {
        val temporary = File(root, "$name.tmp")
        FileOutputStream(temporary).use { it.write(value.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        check(temporary.renameTo(File(root, name))) { "Cannot activate calculator pack" }
        syncDirectory(root)
    }
    private fun syncDirectory(directory: File) {
        // Android/Linux needs the directory entry synced as well as each file before reporting success.
        if (android.os.Build.VERSION.SDK_INT == 0) return // Plain JVM unit tests have no Android filesystem API.
        val descriptor = android.system.Os.open(directory.absolutePath, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(descriptor) } finally { android.system.Os.close(descriptor) }
    }
}
