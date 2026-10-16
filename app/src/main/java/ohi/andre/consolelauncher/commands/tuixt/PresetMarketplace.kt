package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.graphics.Color
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Curated data-only presets use the same inspector as external file imports. */
object PresetMarketplace {
    const val CATALOG = "https://re-tui.pages.dev/terminal-colors/catalog.json"
    data class Entry(val id: String, val name: String, val url: String, val bytes: Int, val hash: String,
                     val background: Int, val foreground: Int, val swatches: List<Int>)
    internal var client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    internal fun resolve(path: String): String {
        require(!path.contains('%') && !path.contains('\\') && path.split('/').none { it == "." || it == ".." })
        val base = URI(CATALOG).resolve("./")
        val url = base.resolve(path)
        require(url.scheme == "https" && url.host == base.host && url.port == -1 && url.userInfo == null &&
            url.query == null && url.fragment == null && url.path.startsWith(base.path)) { "Untrusted preset address" }
        return url.toString()
    }
    internal fun parse(text: String): List<Entry> {
        val root = JSONObject(text)
        require(root.getInt("schema") == 1)
        val themes = root.getJSONArray("themes")
        require(themes.length() <= 100)
        val seen = HashSet<String>()
        return (0 until themes.length()).map { index ->
            val item = themes.getJSONObject(index)
            val id = item.getString("id"); require(id.matches(Regex("[a-z0-9-]{1,80}")) && seen.add(id))
            val name = item.getString("name"); require(name.length in 1..100 && name.none { it.isISOControl() })
            val size = item.getInt("bytes"); require(size in 1..4 * 1024 * 1024)
            val hash = item.getString("sha256"); require(hash.matches(Regex("[a-f0-9]{64}")))
            val colors = item.getJSONArray("swatches"); require(colors.length() in 1..16)
            Entry(id, name, resolve(item.getString("download")), size, hash,
                Color.parseColor(item.getString("background")), Color.parseColor(item.getString("foreground")),
                (0 until colors.length()).map { Color.parseColor(colors.getString(it)) })
        }
    }
    fun load(): List<Entry> = parse(download(CATALOG, 256 * 1024, { false }) {}.toString(Charsets.UTF_8))
    private fun download(url: String, limit: Int, cancelled: () -> Boolean, progress: (Int) -> Unit): ByteArray {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Download failed (${response.code})" }
            val body = checkNotNull(response.body)
            require(body.contentLength() <= limit) { "Preset download is too large" }
            body.byteStream().use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    if (cancelled()) throw CancellationException()
                    val count = input.read(buffer); if (count < 0) break
                    require(out.size() + count <= limit) { "Preset download is too large" }
                    out.write(buffer, 0, count); progress(out.size())
                }
                if (cancelled()) throw CancellationException()
                return out.toByteArray()
            }
        }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun fingerprint(folder: File): String = digest(folder.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(folder).path }
        .fold(ByteArrayOutputStream()) { out, file -> out.apply {
            write(file.relativeTo(folder).path.toByteArray()); write(0); write(file.readBytes()); write(0)
        } }.toByteArray())
    @Synchronized fun install(context: Context, entry: Entry, cancelled: () -> Boolean, progress: (Int) -> Unit): String {
        val bytes = download(resolve(entry.url), entry.bytes, cancelled, progress)
        require(bytes.size == entry.bytes && digest(bytes) == entry.hash) { "Preset verification failed" }
        Tuils.init(context)
        val prefs = context.getSharedPreferences("preset_marketplace", Context.MODE_PRIVATE)
        val previous = prefs.getString(entry.hash, null)
        if (previous != null && previous in PresetManager.listPresets()) {
            val folder = File(PresetManager.presetsDir, previous)
            if (fingerprint(folder) == prefs.getString(entry.hash + ".contents", null)) return previous
        }
        return BackupManager.inspectPreset(bytes.inputStream(), File(context.cacheDir, "preset-marketplace")).use { staged ->
            if (cancelled()) throw CancellationException()
            val name = staged.save()
            prefs.edit().putString(entry.hash, name).putString(entry.hash + ".contents", fingerprint(File(PresetManager.presetsDir, name))).apply()
            name
        }
    }
}
