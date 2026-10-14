package ohi.andre.consolelauncher.managers.modules

import android.content.Context
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import java.net.URI
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The website owns the catalogue; installed scripts and their settings belong to the user. */
object ModuleLibrary {
    const val CATALOG_URL = "https://re-tui.pages.dev/modules/index.json"
    private const val MAX_BYTES = 256 * 1024
    private val worker = Executors.newSingleThreadExecutor()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    internal var client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).build()
    @Volatile var entries: List<Entry> = emptyList()
        private set

    data class Entry(val key: String, val id: String, val title: String, val description: String,
        val url: String, val sha256: String, val version: String, val api: Int,
        val preview: String = "", val details: String = "", val author: String = "", val license: String = "", val requirements: String = "", val screenshot: String = "")
    data class Catalogue(val entries: List<Entry>, val cached: Boolean)

    internal fun parse(text: String): List<Entry> {
        val root = JSONObject(text)
        require(root.getInt("schema") == 1) { "Unsupported catalogue format" }
        val modules = root.getJSONArray("modules")
        require(modules.length() <= 100) { "Catalogue is too large" }
        val base = URI(CATALOG_URL).resolve("./")
        val aliases = HashSet<String>()
        return (0 until modules.length()).map { index ->
            val item = modules.getJSONObject(index)
            val key = item.getString("key")
            val id = item.getString("id")
            require(key.matches(Regex("[a-z0-9_]{1,64}")) && id.matches(Regex("[a-z0-9_]{1,64}")) && aliases.add(key) && (id == key || aliases.add(id)) && !ModuleManager.builtIns.contains(id)) { "Invalid or duplicate module ID" }
            val api = item.getInt("api")
            require(api > 0) { "Invalid module API version" }
            val path = item.getString("path")
            require(!path.contains('%') && !path.contains('\\') && path.split('/').none { it == "." || it == ".." }) { "Invalid module download path" }
            val url = base.resolve(path).normalize()
            require(url.scheme == "https" && url.host == base.host && url.port == -1 &&
                url.userInfo == null && url.query == null && url.fragment == null &&
                url.path.startsWith(base.path)) { "Invalid module download location" }
            val hash = item.getString("sha256")
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "Invalid module checksum" }
            val title = item.getString("name")
            val description = item.getString("description")
            require(title.isNotBlank() && title.length <= 80 && description.isNotBlank() && description.length <= 400) { "Invalid module description" }
            val preview = item.optString("preview")
            val details = item.optString("details", description)
            val author = item.optString("author")
            val license = item.optString("license")
            require(preview.length <= 2000 && details.length <= 2000 && author.length <= 200 && license.length <= 80) { "Module details are too long" }
            val requirements = item.optString("requirements")
            require(requirements.length <= 1000) { "Module requirements are too long" }
            val imagePath = item.optString("screenshot")
            val image = if (imagePath.isEmpty()) "" else {
                require(imagePath.matches(Regex("[a-z0-9_/.-]+")) && imagePath.split('/').none { it == "." || it == ".." }) { "Invalid screenshot path" }
                val uri = base.resolve(imagePath)
                require(uri.host == base.host && uri.scheme == "https" && uri.path.startsWith(base.path) && uri.port == -1 && uri.query == null && uri.fragment == null) { "Invalid screenshot location" }
                uri.toString()
            }
            Entry(key, id, title, description, url.toString(), hash, item.getString("version"), api, preview, details, author, license, requirements, image)
        }
    }

    private fun download(url: String): ByteArray {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Download failed (${response.code})" }
            val body = checkNotNull(response.body)
            require(body.contentLength() <= MAX_BYTES) { "Download is too large" }
            body.byteStream().use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= MAX_BYTES) { "Download is too large" }
                    out.write(buffer, 0, count)
                }
                return out.toByteArray()
            }
        }
    }

    fun refresh(context: Context, callback: (Result<Catalogue>) -> Unit) {
        worker.execute {
            val prefs = context.getSharedPreferences("retui_module_library", Context.MODE_PRIVATE)
            val result = runCatching {
                val text = download(CATALOG_URL).toString(Charsets.UTF_8)
                val parsed = parse(text)
                prefs.edit().putString("catalogue", text).apply()
                Catalogue(parsed, false)
            }.recoverCatching { failure ->
                val cached = prefs.getString("catalogue", null) ?: throw failure
                Catalogue(parse(cached), true)
            }
            result.onSuccess { entries = it.entries }
            main.post { callback(result) }
        }
    }

    fun screenshot(entry: Entry, callback: (Result<android.graphics.Bitmap>) -> Unit) {
        worker.execute {
            val result = runCatching {
                val bytes = download(entry.screenshot)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 4_000_000) { "Screenshot is too large" }
                checkNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            }
            main.post { callback(result) }
        }
    }

    fun script(context: Context, entry: Entry): String {
        val bytes = download(entry.url)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == entry.sha256) { "Downloaded module does not match the catalogue" }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun hash(source: String): String = MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun status(context: Context, entry: Entry): String {
        if (!LuaWidgetManager.exists(entry.id)) return "Not installed · details"
        val source = LuaWidgetManager.readScript(entry.id) ?: ""
        val prefs = context.getSharedPreferences("retui_module_library_installs", Context.MODE_PRIVATE)
        val baseline = prefs.getString(entry.id + ".hash", null)
        val current = hash(source)
        if (baseline == null) return if (current == entry.sha256) "Installed · details" else "Installed · local copy"
        return listOfNotNull("Installed", "Edited locally".takeIf { current != baseline },
            "Update available".takeIf { entry.sha256 != baseline }).joinToString(" · ")
    }

    fun hasUpdate(context: Context, entry: Entry): Boolean {
        val baseline = context.getSharedPreferences("retui_module_library_installs", Context.MODE_PRIVATE)
            .getString(entry.id + ".hash", null)
        return LuaWidgetManager.exists(entry.id) && (baseline ?: hash(LuaWidgetManager.readScript(entry.id) ?: "")) != entry.sha256
    }

    fun remove(context: Context, id: String) {
        LuaWidgetManager.delete(id)
        check(!LuaWidgetManager.widgetDir(id).exists()) { "Could not remove module files" }
        ModuleManager.removeScriptModule(context, id)
        context.getSharedPreferences("retui_module_library_installs", Context.MODE_PRIVATE).edit()
            .remove(id + ".hash").remove(id + ".version").apply()
    }

    fun install(context: Context, entry: Entry, replace: Boolean = false): String {
        val before = LuaWidgetManager.readScript(entry.id)
        if (!LuaWidgetManager.exists(entry.id) || replace) {
            require(entry.api == 1) { "This module requires a newer launcher" }
            val script = script(context, entry)
            require(LuaWidgetManager.apiVersionFromScript(script) == "1") { "This module requires a newer launcher" }
            require(LuaWidgetManager.getScriptTypeFromScript(script) == "module") { "This download is not a dock module" }
            check(LuaWidgetManager.readScript(entry.id) == before) { "Module changed while downloading. Please review it again." }
            LuaWidgetManager.save(entry.id, entry.title, script)
            context.getSharedPreferences("retui_module_library_installs", Context.MODE_PRIVATE).edit()
                .putString(entry.id + ".hash", entry.sha256).putString(entry.id + ".version", entry.version).apply()
        }
        ModuleManager.setScriptModule(context, entry.id, LuaWidgetManager.SOURCE_PREFIX + entry.id)
        ModuleManager.addToDock(context, arrayListOf<String?>(entry.id))
        return entry.id
    }

    fun installAsync(context: Context, entry: Entry, replace: Boolean = false, callback: (Result<String>) -> Unit) {
        worker.execute {
            val result = runCatching { install(context, entry, replace) }
            main.post { callback(result) }
        }
    }
}
