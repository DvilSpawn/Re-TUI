package ohi.andre.consolelauncher.wallpaper

import android.content.Context
import android.content.Intent
import okhttp3.OkHttpClient
import okhttp3.Request
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

object WallpaperLibrary {
    const val CATALOG_URL = "https://re-tui.pages.dev/wallpaper-packs/catalog.json"
    data class Entry(val id: String, val title: String, val description: String, val url: String, val preview: String, val sha256: String)
    internal var client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    fun store(context: Context) = WallpaperPackStore(File(context.filesDir, "wallpaper-packs"))
    fun available(context: Context, scene: String): Boolean =
        WallpaperPackStore.SCENES.entries.firstOrNull { it.value == scene }?.let { store(context).installed(it.key) } ?: true
    fun effectiveScene(context: Context, saved: String) = saved.takeIf { available(context, it) } ?: "csakura"

    internal fun parse(text: String): List<Entry> {
        val root = JSONObject(text)
        require(root.getInt("schema") == 1) { "Unsupported wallpaper catalogue" }
        val list = root.getJSONArray("wallpapers")
        require(list.length() <= WallpaperPackStore.SCENES.size)
        val seen = HashSet<String>()
        return (0 until list.length()).map { index ->
            val item = list.getJSONObject(index)
            val id = item.getString("id")
            require(id in WallpaperPackStore.SCENES && seen.add(id) && item.getInt("api") == 1)
            val title = item.getString("name"); val description = item.getString("description")
            require(title.length in 1..80 && description.length in 1..400)
            val hash = item.getString("sha256"); require(hash.matches(Regex("[a-f0-9]{64}")))
            Entry(id, title, description, resolve(item.getString("download")), resolve(item.getString("preview")), hash)
        }
    }
    private fun resolve(path: String): String {
        require(!path.contains('%') && !path.contains('\\') && path.split('/').none { it == "." || it == ".." })
        val base = URI(CATALOG_URL).resolve("./")
        val url = base.resolve(path)
        require(url.scheme == "https" && url.host == base.host && url.port == -1 && url.userInfo == null &&
            url.query == null && url.fragment == null && url.path.startsWith(base.path)) { "Untrusted wallpaper address" }
        return url.toString()
    }
    fun loadCatalog(context: Context): List<Entry> = parse(download(CATALOG_URL, 64 * 1024, { false }) { _, _ -> }.toString(Charsets.UTF_8))

    /** Blocking worker operation. The saved archive is data only; rendering uses the private verified copy. */
    fun install(context: Context, entry: Entry, cancelled: () -> Boolean = { false },
                onProgress: (Long, Long) -> Unit = { _, _ -> }): File {
        require(entry.id in WallpaperPackStore.SCENES)
        val url = resolve(entry.url)
        val bytes = download(url, WallpaperPackStore.MAX_BYTES, cancelled, onProgress)
        Tuils.init(context)
        val launcher = Tuils.getFolder()
        check(launcher.isAbsolute && launcher.isDirectory) { "Launcher storage is unavailable" }
        val archive = store(context).install(entry.id, bytes, entry.sha256, File(launcher, "wallpaper-downloads"), cancelled)
        context.applicationContext.sendBroadcast(Intent(RetuiWallpaperService.ACTION_REFRESH).setPackage(context.packageName))
        return archive
    }
    private fun download(url: String, limit: Int, cancelled: () -> Boolean, progress: (Long, Long) -> Unit): ByteArray {
        WallpaperPackStore.checkCancelled(cancelled)
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            WallpaperPackStore.checkCancelled(cancelled)
            check(response.isSuccessful) { "Download failed (${response.code})" }
            val body = checkNotNull(response.body)
            val total = body.contentLength()
            require(total <= limit) { "Wallpaper download is too large" }
            body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                progress(0, total)
                while (true) {
                    WallpaperPackStore.checkCancelled(cancelled)
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= limit) { "Wallpaper download is too large" }
                    output.write(buffer, 0, count)
                    progress(output.size().toLong(), total)
                }
                WallpaperPackStore.checkCancelled(cancelled)
                return output.toByteArray()
            }
        }
    }
}
