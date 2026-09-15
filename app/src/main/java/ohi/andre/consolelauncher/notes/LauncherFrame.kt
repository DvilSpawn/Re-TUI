package ohi.andre.consolelauncher.notes

import ohi.andre.consolelauncher.R

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Properties
import kotlin.math.roundToInt

internal data class LauncherFrameSpec(
    val assetId: String,
    val sliceLeft: Int,
    val sliceTop: Int,
    val sliceRight: Int,
    val sliceBottom: Int,
    val borderLeft: Float,
    val borderTop: Float,
    val borderRight: Float,
    val borderBottom: Float,
    val modeTop: String,
    val modeRight: String,
    val modeBottom: String,
    val modeLeft: String,
    val modeCenter: String,
    val filtering: String
) {
    fun error(width: Int, height: Int): String? {
        if (!ASSET.matches(assetId)) return "frame_asset_id must be 64 lowercase hex characters"
        if (width !in 1..2048 || height !in 1..2048) return "frame dimensions are outside 1..2048"
        if (listOf(sliceLeft, sliceTop, sliceRight, sliceBottom).any { it <= 0 }) return "frame slices must be positive"
        if (sliceLeft + sliceRight >= width || sliceTop + sliceBottom >= height) return "frame slices overlap"
        if (listOf(borderLeft, borderTop, borderRight, borderBottom).any { !it.isFinite() || it !in 0f..256f }) return "frame borders are invalid"
        if (listOf(modeTop, modeRight, modeBottom, modeLeft).any { it != STRETCH && it != TILE }) return "frame edge mode is invalid"
        if (modeCenter !in setOf(STRETCH, TILE, NONE)) return "frame center mode is invalid"
        if (filtering != NEAREST && filtering != LINEAR) return "frame filtering is invalid"
        return null
    }

    companion object {
        val ASSET = Regex("[0-9a-f]{64}")
        const val STRETCH = "stretch"
        const val TILE = "tile"
        const val NONE = "none"
        const val NEAREST = "nearest"
        const val LINEAR = "linear"
    }
}

internal enum class FramePayloadAction { IGNORE, PRESERVE, CLEAR, IMPORT }

internal fun framePayloadAction(accepted: Boolean, available: Boolean?): FramePayloadAction = when {
    !accepted -> FramePayloadAction.IGNORE
    available == null -> FramePayloadAction.PRESERVE
    !available -> FramePayloadAction.CLEAR
    else -> FramePayloadAction.IMPORT
}

internal object LauncherFrameMath {
    fun fitScale(width: Float, height: Float, left: Float, top: Float, right: Float, bottom: Float): Float = minOf(
        1f,
        (width / (left + right)).coerceAtLeast(0f),
        (height / (top + bottom)).coerceAtLeast(0f)
    )

    fun boundaries(start: Int, end: Int, leading: Float, trailing: Float) = floatArrayOf(
        start.toFloat(),
        (start + leading).roundToInt().toFloat(),
        (end - trailing).roundToInt().toFloat(),
        end.toFloat()
    )
}

internal class LauncherFrameStore(
    private val context: Context,
    private val openFrame: (Uri) -> InputStream? = context.contentResolver::openInputStream
) {
    private val directory = File(context.filesDir, "launcher_frames")
    private val metadata = AtomicFile(File(directory, "active.properties"))

    fun process(intent: Intent?, accepted: Boolean) {
        if (!accepted) return
        val extras = intent?.extras
        val hasAvailable = extras?.containsKey(PreviewContract.Visual.FRAME_AVAILABLE) == true
        val available = if (hasAvailable) raw(extras!!, PreviewContract.Visual.FRAME_AVAILABLE) as? Boolean else null
        if (hasAvailable && available == null) {
            clear("frame_available must be a Boolean")
            return
        }
        when (framePayloadAction(accepted, available)) {
            FramePayloadAction.IGNORE, FramePayloadAction.PRESERVE -> return
            FramePayloadAction.CLEAR -> return clear()
            FramePayloadAction.IMPORT -> Unit
        }
        runCatching {
            val payload = parse(extras!!)
            directory.mkdirs()
            val target = File(directory, "${payload.spec.assetId}.png")
            if (!target.isFile || validate(target, payload.spec) != null) import(payload.uri, target, payload.spec)
            validate(target, payload.spec)?.let(::error)
            writeMetadata(payload.spec)
        }.onFailure { clear(it.message ?: "invalid Launcher frame", it) }
    }

    fun loadActive(accepted: Boolean): LauncherFrameAsset? {
        if (!accepted || !metadata.baseFile.isFile) return null
        return runCatching {
            val properties = Properties().apply { metadata.openRead().use(::load) }
            if (properties.getProperty("available") != "true") return null
            val spec = properties.toSpec()
            val file = File(directory, "${spec.assetId}.png")
            validate(file, spec)?.let(::error)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("cached frame cannot be decoded")
            LauncherFrameAsset(bitmap, spec, context.resources.displayMetrics.density)
        }.onFailure { clear("Ignoring invalid cached Launcher frame", it) }.getOrNull()
    }

    private fun parse(extras: android.os.Bundle): Payload {
        fun string(key: String) = (raw(extras, key) as? String)?.takeIf(String::isNotBlank) ?: error("invalid $key")
        fun int(key: String) = raw(extras, key) as? Int ?: error("invalid $key")
        fun float(key: String) = (raw(extras, key) as? Float)?.takeIf(Float::isFinite) ?: error("invalid $key")
        val v = PreviewContract.Visual
        val spec = LauncherFrameSpec(
            string(v.FRAME_ASSET_ID), int(v.FRAME_SLICE_LEFT), int(v.FRAME_SLICE_TOP),
            int(v.FRAME_SLICE_RIGHT), int(v.FRAME_SLICE_BOTTOM), float(v.FRAME_BORDER_LEFT),
            float(v.FRAME_BORDER_TOP), float(v.FRAME_BORDER_RIGHT), float(v.FRAME_BORDER_BOTTOM),
            string(v.FRAME_MODE_TOP), string(v.FRAME_MODE_RIGHT), string(v.FRAME_MODE_BOTTOM),
            string(v.FRAME_MODE_LEFT), string(v.FRAME_MODE_CENTER), string(v.FRAME_FILTERING)
        )
        if (!LauncherFrameSpec.ASSET.matches(spec.assetId)) error("invalid ${v.FRAME_ASSET_ID}")
        val uri = Uri.parse(string(v.FRAME_IMAGE_URI))
        if (uri.scheme != "content" || uri.authority != PreviewContract.LAUNCHER_PACKAGE + ".FILE_PROVIDER") error("invalid ${v.FRAME_IMAGE_URI}")
        return Payload(spec, uri)
    }

    private fun import(uri: Uri, target: File, spec: LauncherFrameSpec) {
        val temporary = File.createTempFile("incoming-", ".png", directory)
        try {
            val source = openFrame(uri) ?: error("frame_image_uri is unreadable")
            source.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) error("Launcher frame exceeds 4 MiB")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            validate(temporary, spec)?.let(::error)
            val atomic = AtomicFile(target)
            val output = atomic.startWrite()
            try {
                FileInputStream(temporary).use { it.copyTo(output) }
                output.fd.sync()
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        } finally {
            temporary.delete()
        }
    }

    private fun validate(file: File, spec: LauncherFrameSpec): String? {
        if (!file.isFile || file.length() !in PNG.size.toLong()..MAX_BYTES) return "frame PNG is missing or too large"
        FileInputStream(file).use { input ->
            val signature = ByteArray(PNG.size)
            if (input.read(signature) != signature.size || !signature.contentEquals(PNG)) return "invalid PNG signature"
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        spec.error(bounds.outWidth, bounds.outHeight)?.let { return it }
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return "frame PNG cannot be decoded"
        decoded.recycle()
        return null
    }

    private fun writeMetadata(spec: LauncherFrameSpec) {
        directory.mkdirs()
        val output = metadata.startWrite()
        try {
            spec.properties().store(output, null)
            output.fd.sync()
            metadata.finishWrite(output)
        } catch (error: Exception) {
            metadata.failWrite(output)
            throw error
        }
    }

    private fun clear(reason: String? = null, error: Throwable? = null) {
        directory.mkdirs()
        runCatching {
            val output = metadata.startWrite()
            try {
                Properties().apply { setProperty("available", "false") }.store(output, null)
                output.fd.sync()
                metadata.finishWrite(output)
            } catch (failure: Exception) {
                metadata.failWrite(output)
                throw failure
            }
        }
        if (reason != null) if (error == null) Log.w(TAG, reason) else Log.w(TAG, reason, error)
    }

    private fun raw(extras: android.os.Bundle, key: String): Any? =
        if (!extras.containsKey(key)) error("missing $key") else @Suppress("DEPRECATION") extras.get(key)

    private fun LauncherFrameSpec.properties() = Properties().apply {
        setProperty("available", "true"); setProperty("asset", assetId)
        setProperty("sliceLeft", sliceLeft.toString()); setProperty("sliceTop", sliceTop.toString())
        setProperty("sliceRight", sliceRight.toString()); setProperty("sliceBottom", sliceBottom.toString())
        setProperty("borderLeft", borderLeft.toString()); setProperty("borderTop", borderTop.toString())
        setProperty("borderRight", borderRight.toString()); setProperty("borderBottom", borderBottom.toString())
        setProperty("modeTop", modeTop); setProperty("modeRight", modeRight)
        setProperty("modeBottom", modeBottom); setProperty("modeLeft", modeLeft)
        setProperty("modeCenter", modeCenter); setProperty("filtering", filtering)
    }

    private fun Properties.toSpec() = LauncherFrameSpec(
        getProperty("asset") ?: "", getProperty("sliceLeft")?.toIntOrNull() ?: 0,
        getProperty("sliceTop")?.toIntOrNull() ?: 0, getProperty("sliceRight")?.toIntOrNull() ?: 0,
        getProperty("sliceBottom")?.toIntOrNull() ?: 0, getProperty("borderLeft")?.toFloatOrNull() ?: Float.NaN,
        getProperty("borderTop")?.toFloatOrNull() ?: Float.NaN, getProperty("borderRight")?.toFloatOrNull() ?: Float.NaN,
        getProperty("borderBottom")?.toFloatOrNull() ?: Float.NaN, getProperty("modeTop") ?: "",
        getProperty("modeRight") ?: "", getProperty("modeBottom") ?: "", getProperty("modeLeft") ?: "",
        getProperty("modeCenter") ?: "", getProperty("filtering") ?: ""
    )

    private data class Payload(val spec: LauncherFrameSpec, val uri: Uri)

    companion object {
        private const val TAG = "LauncherFrame"
        private const val MAX_BYTES = 4L * 1024L * 1024L
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}

class LauncherFrameAsset internal constructor(bitmap: Bitmap, private val spec: LauncherFrameSpec, private val density: Float) {
    private val parts = FrameParts(bitmap, spec)

    fun drawable(background: Drawable, interactive: Boolean): Drawable {
        val layered = LayerDrawable(arrayOf(background, LauncherFrameDrawable(parts, spec, density)))
        return if (interactive) StatefulFrameDrawable(layered) else layered
    }
}

private class StatefulFrameDrawable(private val frame: Drawable) : Drawable() {
    private val feedback = Paint()
    private var feedbackColor = Color.TRANSPARENT

    override fun draw(canvas: Canvas) {
        frame.draw(canvas)
        if (feedbackColor != Color.TRANSPARENT) {
            feedback.color = feedbackColor
            canvas.drawRect(bounds, feedback)
        }
    }

    override fun onBoundsChange(bounds: Rect) { frame.bounds = bounds }
    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        val next = when {
            !state.contains(android.R.attr.state_enabled) -> 0x55000000
            state.contains(android.R.attr.state_pressed) || state.contains(android.R.attr.state_focused) -> 0x24ffffff
            else -> Color.TRANSPARENT
        }
        if (next == feedbackColor) return false
        feedbackColor = next
        invalidateSelf()
        return true
    }
    override fun setAlpha(alpha: Int) { frame.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { frame.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

private class FrameParts(bitmap: Bitmap, spec: LauncherFrameSpec) {
    val topLeft = Bitmap.createBitmap(bitmap, 0, 0, spec.sliceLeft, spec.sliceTop)
    val top = Bitmap.createBitmap(bitmap, spec.sliceLeft, 0, bitmap.width - spec.sliceLeft - spec.sliceRight, spec.sliceTop)
    val topRight = Bitmap.createBitmap(bitmap, bitmap.width - spec.sliceRight, 0, spec.sliceRight, spec.sliceTop)
    val left = Bitmap.createBitmap(bitmap, 0, spec.sliceTop, spec.sliceLeft, bitmap.height - spec.sliceTop - spec.sliceBottom)
    val center = Bitmap.createBitmap(bitmap, spec.sliceLeft, spec.sliceTop, bitmap.width - spec.sliceLeft - spec.sliceRight, bitmap.height - spec.sliceTop - spec.sliceBottom)
    val right = Bitmap.createBitmap(bitmap, bitmap.width - spec.sliceRight, spec.sliceTop, spec.sliceRight, bitmap.height - spec.sliceTop - spec.sliceBottom)
    val bottomLeft = Bitmap.createBitmap(bitmap, 0, bitmap.height - spec.sliceBottom, spec.sliceLeft, spec.sliceBottom)
    val bottom = Bitmap.createBitmap(bitmap, spec.sliceLeft, bitmap.height - spec.sliceBottom, bitmap.width - spec.sliceLeft - spec.sliceRight, spec.sliceBottom)
    val bottomRight = Bitmap.createBitmap(bitmap, bitmap.width - spec.sliceRight, bitmap.height - spec.sliceBottom, spec.sliceRight, spec.sliceBottom)
}

private class LauncherFrameDrawable(
    private val parts: FrameParts,
    private val spec: LauncherFrameSpec,
    private val density: Float
) : Drawable() {
    private val paint = Paint().apply { isFilterBitmap = spec.filtering == LauncherFrameSpec.LINEAR }
    private val shaderPaint = Paint().apply { isFilterBitmap = spec.filtering == LauncherFrameSpec.LINEAR }
    private val matrix = Matrix()

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val scale = LauncherFrameMath.fitScale(
            bounds.width().toFloat(), bounds.height().toFloat(), spec.borderLeft * density,
            spec.borderTop * density, spec.borderRight * density, spec.borderBottom * density
        )
        val x = LauncherFrameMath.boundaries(bounds.left, bounds.right, spec.borderLeft * density * scale, spec.borderRight * density * scale)
        val y = LauncherFrameMath.boundaries(bounds.top, bounds.bottom, spec.borderTop * density * scale, spec.borderBottom * density * scale)
        stretch(canvas, parts.topLeft, RectF(x[0], y[0], x[1], y[1]))
        edge(canvas, parts.top, RectF(x[1], y[0], x[2], y[1]), spec.modeTop, Axis.HORIZONTAL, scale)
        stretch(canvas, parts.topRight, RectF(x[2], y[0], x[3], y[1]))
        edge(canvas, parts.left, RectF(x[0], y[1], x[1], y[2]), spec.modeLeft, Axis.VERTICAL, scale)
        if (spec.modeCenter != LauncherFrameSpec.NONE) edge(canvas, parts.center, RectF(x[1], y[1], x[2], y[2]), spec.modeCenter, Axis.BOTH, scale)
        edge(canvas, parts.right, RectF(x[2], y[1], x[3], y[2]), spec.modeRight, Axis.VERTICAL, scale)
        stretch(canvas, parts.bottomLeft, RectF(x[0], y[2], x[1], y[3]))
        edge(canvas, parts.bottom, RectF(x[1], y[2], x[2], y[3]), spec.modeBottom, Axis.HORIZONTAL, scale)
        stretch(canvas, parts.bottomRight, RectF(x[2], y[2], x[3], y[3]))
    }

    private fun stretch(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        if (destination.width() > 0f && destination.height() > 0f) canvas.drawBitmap(bitmap, null, destination, paint)
    }

    private fun edge(canvas: Canvas, bitmap: Bitmap, destination: RectF, mode: String, axis: Axis, frameScale: Float) {
        if (destination.width() <= 0f || destination.height() <= 0f) return
        if (mode == LauncherFrameSpec.STRETCH) return stretch(canvas, bitmap, destination)
        val scale = when (axis) {
            Axis.HORIZONTAL -> destination.height() / bitmap.height
            Axis.VERTICAL -> destination.width() / bitmap.width
            Axis.BOTH -> tileScale() * frameScale
        }.coerceAtLeast(0.01f)
        val scaleX = if (axis == Axis.VERTICAL) destination.width() / bitmap.width else scale
        val scaleY = if (axis == Axis.HORIZONTAL) destination.height() / bitmap.height else scale
        val shader = BitmapShader(bitmap,
            if (axis == Axis.VERTICAL) Shader.TileMode.CLAMP else Shader.TileMode.REPEAT,
            if (axis == Axis.HORIZONTAL) Shader.TileMode.CLAMP else Shader.TileMode.REPEAT)
        matrix.reset(); matrix.setScale(scaleX, scaleY); matrix.postTranslate(destination.left, destination.top)
        shader.setLocalMatrix(matrix); shaderPaint.shader = shader
        canvas.drawRect(destination, shaderPaint); shaderPaint.shader = null
    }

    private fun tileScale() = listOf(
        spec.borderLeft * density / spec.sliceLeft, spec.borderTop * density / spec.sliceTop,
        spec.borderRight * density / spec.sliceRight, spec.borderBottom * density / spec.sliceBottom
    ).firstOrNull { it > 0f } ?: 1f

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; shaderPaint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; shaderPaint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    private enum class Axis { HORIZONTAL, VERTICAL, BOTH }
}
