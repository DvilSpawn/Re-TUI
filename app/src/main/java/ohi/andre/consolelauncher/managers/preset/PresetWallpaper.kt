package ohi.andre.consolelauncher.managers.preset

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperService
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperSettings
import org.json.JSONObject

/**
 * A preset's own wallpaper: an image, or one of the launcher's live wallpapers with its settings.
 *
 * Android 13+ does not let the Play Store build read the wallpaper back, so saving keeps what the launcher can know:
 * a live wallpaper of its own, or an image it set itself (checked by wallpaper id). Otherwise the caller asks the user
 * for the image.
 */
object PresetWallpaper {
    enum class Saved { IMAGE, LIVE, UNKNOWN }

    private const val PREFS = "retui_preset_wallpaper"
    private const val KEY_FILE = "file"
    private const val KEY_ID = "id"
    private const val KEY_COMPONENT = "component"
    private const val KEY_SETTINGS = "settings"
    /** Live wallpapers that draw a picked photo keep it here; the preset copies them alongside its settings. */
    private val LIVE_PHOTOS = listOf("dither-photo.jpg", "nagomi-background.jpg")
    private const val MAX_SIDE = 2560

    fun image(name: String): File = PresetManager.wallpaperFile(name)
    private fun live(name: String) = File(image(name).path + ".live")
    private fun livePhoto(name: String, photo: String) = File(image(name).path + ".live." + photo)

    /** Keeps the current wallpaper with preset [name], replacing what it had. */
    fun capture(context: Context, name: String): Saved {
        val manager = WallpaperManager.getInstance(context)
        val info = manager.wallpaperInfo
        if (info != null && info.packageName == context.packageName) {
            clear(name)
            live(name).parentFile?.mkdirs()
            live(name).writeText(JSONObject()
                .put(KEY_COMPONENT, info.component.className)
                .put(KEY_SETTINGS, RetuiWallpaperSettings.snapshot(context)).toString())
            LIVE_PHOTOS.forEach { photo ->
                File(context.filesDir, photo).takeIf { it.isFile }?.copyTo(livePhoto(name, photo), overwrite = true)
            }
            return Saved.LIVE
        }
        val known = knownImage(context)
        if (known != null) {
            if (known.canonicalPath != image(name).canonicalPath) { clear(name); copy(known, image(name)) } else clearLive(name)
            return Saved.IMAGE
        }
        clear(name)
        return Saved.UNKNOWN
    }

    /** Stores a picked image as preset [name]'s wallpaper. */
    fun setImage(context: Context, name: String, uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read the image")
        val fitted = fit(bytes) ?: error("Not an image")
        clear(name)
        write(image(name), fitted)
    }

    /** Sets preset [name]'s wallpaper, if it has one. A live one may need the user to confirm it in Android's page. */
    fun apply(context: Context, name: String) {
        live(name).takeIf { it.isFile }?.let { file ->
            val saved = JSONObject(file.readText())
            val component = saved.optString(KEY_COMPONENT).ifEmpty { RetuiWallpaperService::class.java.name }
            RetuiWallpaperSettings.restore(context, saved.optJSONObject(KEY_SETTINGS) ?: JSONObject())
            LIVE_PHOTOS.forEach { photo ->
                livePhoto(name, photo).takeIf { it.isFile }?.copyTo(File(context.filesDir, photo), overwrite = true)
            }
            val current = WallpaperManager.getInstance(context).wallpaperInfo?.component
            if (current?.packageName == context.packageName && current.className == component) {
                context.sendBroadcast(Intent(RetuiWallpaperService.ACTION_REFRESH).setPackage(context.packageName))
            } else {
                val target = ComponentName(context.packageName, component)
                val change = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(change) }
                    .onFailure { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return
        }
        val file = image(name).takeIf { it.isFile } ?: return
        file.inputStream().use { WallpaperManager.getInstance(context).setStream(it) }
        record(context, file)
    }

    fun remove(name: String) = clear(name)

    fun duplicate(source: String, target: String) {
        image(source).takeIf { it.isFile }?.let { copy(it, image(target)) }
        live(source).takeIf { it.isFile }?.let { copy(it, live(target)) }
        LIVE_PHOTOS.forEach { photo -> livePhoto(source, photo).takeIf { it.isFile }?.let { copy(it, livePhoto(target, photo)) } }
    }

    /** An image wallpaper's bytes, scaled down when large; null when they are not an image. */
    fun fit(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (maxOf(bounds.outWidth, bounds.outHeight) <= MAX_SIDE) return bytes
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out); bitmap.recycle(); out.toByteArray() }
    }

    /** The image the launcher itself last set, while it is still the wallpaper. */
    private fun knownImage(context: Context): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val file = prefs.getString(KEY_FILE, null)?.let(::File)?.takeIf { it.isFile } ?: return null
        val id = runCatching { WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return null
        return file.takeIf { prefs.getInt(KEY_ID, -1) == id }
    }

    private fun record(context: Context, file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val id = runCatching { WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_FILE, file.path).putInt(KEY_ID, id).apply()
    }

    private fun clearLive(name: String) {
        live(name).delete()
        LIVE_PHOTOS.forEach { livePhoto(name, it).delete() }
    }

    private fun clear(name: String) {
        image(name).delete()
        clearLive(name)
    }

    private fun write(file: File, bytes: ByteArray) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Unable to store the preset wallpaper" }
        file.writeBytes(bytes)
    }

    private fun copy(from: File, to: File) {
        check(to.parentFile!!.isDirectory || to.parentFile!!.mkdirs()) { "Unable to store the preset wallpaper" }
        from.copyTo(to, overwrite = true)
    }
}
