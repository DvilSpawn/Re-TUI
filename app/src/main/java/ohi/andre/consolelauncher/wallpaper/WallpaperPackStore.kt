package ohi.andre.consolelauncher.wallpaper

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CancellationException
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.nio.ByteBuffer

/** Data-only packs. No classes, libraries, scripts or arbitrary paths can enter this store. */
class WallpaperPackStore(private val root: File) {
    companion object {
        private val mutationLock = Any()
        internal fun checkCancelled(cancelled: () -> Boolean) {
            if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Wallpaper installation cancelled")
        }
        const val MAX_BYTES = 4 * 1024 * 1024
        val SCENES = linkedMapOf("topo" to "topo noise", "pixel" to "pixel dream", "clouds" to "clouds", "nagomi" to "nagomi",
            "astro" to "astro chart", "matrix" to "matrix rain")
        fun assets(id: String): Set<String> = when (id) {
            "clouds" -> (1..8).flatMap { scene ->
                val count = when (scene) { 5 -> 5; 6, 8 -> 6; else -> 4 }
                (1..count).map { "clouds_${scene}_$it.png" }
            }.toSet()
            "nagomi" -> setOf("nagomi_pond_bed.png")
            else -> emptySet()
        }
    }
    fun installed(id: String): Boolean = id in SCENES && runCatching {
        val folder = folder(id)
        val manifest = JSONObject(File(folder, "wallpaper.json").readText())
        manifest.getInt("api") == 1 && manifest.getString("id") == id && manifest.getString("engine") == SCENES[id] && assets(id).all { File(folder, it).isFile }
    }.getOrDefault(false)
    fun asset(id: String, name: String): File {
        require(name in assets(id))
        return File(folder(id), name)
    }

    private fun folder(id: String): File = File(root, id).let { target ->
        if (target.isDirectory) target else File(root, "$id.previous")
    }

    fun install(id: String, bytes: ByteArray, sha256: String, archiveDirectory: File? = null,
                cancelled: () -> Boolean = { false }): File = synchronized(mutationLock) {
        checkCancelled(cancelled)
        require(id in SCENES && bytes.size <= MAX_BYTES)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == sha256) { "Wallpaper download does not match its checksum" }
        check(root.isDirectory || root.mkdirs()) { "Cannot create wallpaper storage" }
        val recovery = File(root, "$id.previous")
        if (!File(root, id).exists() && recovery.exists()) check(recovery.renameTo(File(root, id)))
        val stage = File(root, "$id.partial").apply { deleteRecursively(); check(mkdirs()) }
        val seen = HashSet<String>()
        val allowed = assets(id) + setOf("wallpaper.json", "LICENSE.txt")
        var total = 0
        try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    checkCancelled(cancelled)
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in allowed && seen.add(entry.name)) { "Unexpected or duplicate wallpaper file" }
                    FileOutputStream(File(stage, entry.name)).use { output ->
                        val buffer = ByteArray(8192)
                        var length = 0
                        while (true) {
                            checkCancelled(cancelled)
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count; length += count
                            require(total <= MAX_BYTES && (entry.name.endsWith(".png") || length <= 64 * 1024)) { "Wallpaper pack is too large" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
            }
            require(seen == allowed) { "Wallpaper pack is incomplete" }
            for (name in assets(id)) {
                val header = File(stage, name).inputStream().use { input -> ByteArray(33).also { require(input.read(it) == 33) } }
                require(header.take(8).toByteArray().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) { "Invalid PNG image" }
                require(ByteBuffer.wrap(header, 8, 4).int == 13 && String(header, 12, 4, Charsets.US_ASCII) == "IHDR")
                val width = ByteBuffer.wrap(header, 16, 4).int
                val height = ByteBuffer.wrap(header, 20, 4).int
                require(width in 1..1024 && height in 1..2048 && width.toLong() * height <= 1024L * 2048) { "Wallpaper image dimensions are too large" }
            }
            val manifest = JSONObject(File(stage, "wallpaper.json").readText())
            require(manifest.getInt("api") == 1 && manifest.getString("id") == id && manifest.getString("engine") == SCENES[id]) { "Incompatible wallpaper pack" }
            checkCancelled(cancelled)
            val archive = archiveDirectory?.let { saveArchive(it, id, bytes, sha256, cancelled) }
            checkCancelled(cancelled)
            val old = File(root, "$id.previous")
            val target = File(root, id)
            check(!old.exists() || old.deleteRecursively())
            if (target.exists()) check(target.renameTo(old))
            if (!stage.renameTo(target)) {
                if (old.exists()) check(old.renameTo(target)) { "Could not restore previous wallpaper" }
                error("Could not install wallpaper")
            }
            old.deleteRecursively()
            archive ?: target
        } finally { stage.deleteRecursively() }
    }
    private fun saveArchive(directory: File, id: String, bytes: ByteArray, hash: String, cancelled: () -> Boolean): File {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create wallpaper download directory" }
        val destination = File(directory, "$id-${hash.take(12)}.retui-wallpaper")
        val pending = File(directory, ".download-${UUID.randomUUID()}")
        try {
            FileOutputStream(pending).use { output ->
                var offset = 0
                while (offset < bytes.size) {
                    checkCancelled(cancelled)
                    val count = minOf(8192, bytes.size - offset)
                    output.write(bytes, offset, count)
                    offset += count
                }
                output.fd.sync()
            }
            checkCancelled(cancelled)
            check(pending.renameTo(destination)) { "Cannot save wallpaper archive" }
            return destination
        } finally { pending.delete() }
    }
}
