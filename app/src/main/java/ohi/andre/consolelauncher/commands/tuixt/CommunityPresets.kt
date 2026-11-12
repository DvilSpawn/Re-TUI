package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ohi.andre.consolelauncher.managers.backup.BackupManager
import ohi.andre.consolelauncher.managers.preset.PresetManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Presets shared by Re:TUI users through the website. The site lists only approved ones and reviews every upload,
 * so the app never publishes anything itself. Downloads carry no checksum: the preset inspector is the safety check.
 */
object CommunityPresets {
    const val SITE = "https://re-tui.pages.dev"
    private const val LIST = "$SITE/api/presets"
    private const val SUBMIT = "$SITE/api/presets/submit"
    const val MAX_PRESET = 5 * 1024 * 1024
    const val MAX_IMAGE = 8 * 1024 * 1024

    data class Item(
        val id: String, val name: String, val description: String, val tags: List<String>, val submitter: String?,
        val appVersion: String?, val screenshotUrl: String, val presetUrl: String, val presetBytes: Int,
        val wallpaperUrl: String?, val wallpaperBytes: Int?, val fontUrl: String?, val downloads: Int
    )

    /** What a downloaded package changes, read before anything is saved. */
    data class Contents(val sections: Set<String>)

    class Submission(
        val name: String, val description: String, val tags: String, val submitter: String, val fontUrl: String,
        val preset: ByteArray, val screenshot: ByteArray, val wallpaper: ByteArray?
    )

    private val uploads by lazy {
        PresetMarketplace.client.newBuilder().readTimeout(30, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS).build()
    }

    /** Only the site's own asset paths are fetched, whatever the list says. */
    internal fun resolve(path: String): String {
        // The site versions screenshot links with an encoded query, so % is allowed there only.
        require(!path.contains('\\') && !path.substringBefore('?').contains('%')) { "Untrusted preset address" }
        val base = URI("$SITE/")
        val url = base.resolve(path)
        require(url.scheme == "https" && url.host == base.host && url.port == -1 && url.userInfo == null &&
            url.fragment == null && url.path.startsWith("/api/presets/assets/") &&
            url.path.split('/').none { it == "." || it == ".." }) { "Untrusted preset address" }
        return url.toString()
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key, "").ifEmpty { null }
    private fun String.clean(max: Int) = filterNot { it.isISOControl() }.trim().take(max)

    internal fun parse(text: String): List<Item> {
        val list = JSONObject(text).getJSONArray("presets")
        require(list.length() <= 1000)
        // One malformed entry should not hide the others.
        return (0 until list.length()).mapNotNull { index -> runCatching { item(list.getJSONObject(index)) }.getOrNull() }
    }

    private fun item(o: JSONObject): Item {
        val id = o.getString("id"); require(id.matches(Regex("[A-Za-z0-9-]{1,64}")))
        val name = o.getString("name").clean(64); require(name.isNotEmpty())
        val size = o.optInt("presetSize", 0); require(size in 1..MAX_PRESET)
        val tags = o.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map { tags.optString(it).clean(32) } }
            .orEmpty().filter { it.isNotEmpty() }.take(8)
        val font = o.text("fontUrl")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        return Item(
            id, name, o.text("description")?.clean(400).orEmpty(), tags, o.text("submitterName")?.clean(80),
            o.text("appVersion")?.clean(64), resolve(o.getString("screenshotUrl")), resolve(o.getString("downloadUrl")), size,
            o.text("wallpaperUrl")?.let(::resolve), o.optInt("wallpaperSize", 0).takeIf { it > 0 }, font,
            o.optInt("downloadCount", 0).coerceAtLeast(0)
        )
    }

    fun load(popular: Boolean): List<Item> =
        parse(PresetMarketplace.download(if (popular) "$LIST?sort=popular" else LIST, 1024 * 1024, { false }) {}.toString(Charsets.UTF_8))

    /** The submitted screenshot, decoded small enough for a card or the detail view. */
    fun screenshot(item: Item, maxHeight: Int): Bitmap? =
        decode(PresetMarketplace.download(item.screenshotUrl, MAX_IMAGE, { false }) {}, maxHeight)

    fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Reads what the preset changes; "peek" keeps a look from counting as a download on the site. */
    fun contents(context: Context, item: Item): Contents {
        val bytes = PresetMarketplace.download("${item.presetUrl}?peek=1", MAX_PRESET, { false }) {}
        Tuils.init(context)
        return BackupManager.inspectPreset(bytes.inputStream(), File(context.cacheDir, "preset-community")).use { staged ->
            Contents(staged.sections.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet())
        }
    }

    @Synchronized
    fun install(context: Context, item: Item, withWallpaper: Boolean, cancelled: () -> Boolean, progress: (Int) -> Unit): String {
        val bytes = PresetMarketplace.download(item.presetUrl, MAX_PRESET, cancelled, progress)
        val wallpaper = if (withWallpaper && item.wallpaperUrl != null)
            PresetMarketplace.download(item.wallpaperUrl, MAX_IMAGE, cancelled) {}.also {
                require(decode(it, 64) != null) { "The wallpaper is not a readable image" }
            } else null
        Tuils.init(context)
        return BackupManager.inspectPreset(bytes.inputStream(), File(context.cacheDir, "preset-community")).use { staged ->
            if (cancelled()) throw CancellationException()
            val name = staged.save()
            if (wallpaper != null) PresetManager.saveWallpaper(name, wallpaper)
            name
        }
    }

    /** Sends a setup for review; returns the site's message or throws with its reason. */
    fun submit(submission: Submission): String {
        val image = "image/jpeg".toMediaType()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("name", submission.name)
            .addFormDataPart("description", submission.description)
            .addFormDataPart("tags", submission.tags)
            .addFormDataPart("submitterName", submission.submitter)
            .addFormDataPart("fontUrl", submission.fontUrl)
            .addFormDataPart("consent", "true")
            .addFormDataPart("preset", "preset.retui-backup.zip", submission.preset.toRequestBody("application/zip".toMediaType()))
            .addFormDataPart("screenshot", "screenshot.jpg", submission.screenshot.toRequestBody(image))
            .apply { submission.wallpaper?.let { addFormDataPart("wallpaper", "wallpaper.jpg", it.toRequestBody(image)) } }
            .build()
        uploads.newCall(Request.Builder().url(SUBMIT).post(body).build()).execute().use { response ->
            val text = response.body?.string().orEmpty().take(64 * 1024)
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful || json?.optBoolean("ok") != true) {
                error(json?.optString("error")?.takeIf { it.isNotBlank() } ?: "Upload failed (${response.code})")
            }
            return json!!.optString("message", "")
        }
    }
}
