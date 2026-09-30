package ohi.andre.consolelauncher.wallpaper.nagomi

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import ohi.andre.consolelauncher.R
import java.io.File
import java.io.IOException
import kotlin.math.max

internal object NagomiBackground {
    fun saved(context: Context): File = File(context.filesDir, "nagomi-background.jpg")

    /** Import privately, with bounded input and decoded dimensions. Nothing is applied here. */
    fun import(context: Context, uri: Uri): File {
        val source = File.createTempFile("nagomi-source-", ".image", context.cacheDir)
        val result = File.createTempFile("nagomi-preview-", ".jpg", context.cacheDir)
        var bitmap: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 32L * 1024 * 1024) throw IOException("Image exceeds 32 MB")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Cannot open image")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Unsupported image")
            var sample = 1
            while (max(options.outWidth, options.outHeight) / sample > 2048) sample *= 2
            bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("Cannot decode image")
            val orientation = try { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (_: IOException) { 1 }
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, false)
                if (rotated !== bitmap) bitmap!!.recycle()
                bitmap = rotated
            }
            result.outputStream().use { if (!bitmap!!.compress(Bitmap.CompressFormat.JPEG, 92, it)) throw IOException("Cannot save image") }
            return result
        } catch (e: Exception) {
            result.delete()
            throw e
        } finally {
            bitmap?.recycle()
            source.delete()
        }
    }

    fun apply(context: Context, selected: File?) {
        val target = saved(context)
        if (selected == target) return
        val atomic = AtomicFile(target)
        if (selected == null) {
            atomic.delete()
            if (target.exists()) throw IOException("Cannot reset background")
            return
        }
        val output = atomic.startWrite()
        try {
            selected.inputStream().use { it.copyTo(output) }
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
    }

    fun load(context: Context, file: File?, width: Int, height: Int): Bitmap {
        val source = file?.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.nagomi_pond_bed, BitmapFactory.Options().apply { inScaled = false })
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val w = source.width * scale
        val h = source.height * scale
        val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
        Canvas(result).drawBitmap(source, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), paint)
        source.recycle()
        return result
    }
}
