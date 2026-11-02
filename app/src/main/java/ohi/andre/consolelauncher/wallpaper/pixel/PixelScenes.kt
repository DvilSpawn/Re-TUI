package ohi.andre.consolelauncher.wallpaper.pixel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperSettings
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Drops land at random on a still black or white surface (and under your finger when wallpaper touch is on). */
class RipplesView(context: Context) : PixelWallpaperView(context, 240f) {
    companion object {
        const val SCENE = "ripples"
        const val DARK = "monochrome dark"
        const val LIGHT = "monochrome light"
        val STYLES = listOf(DARK, LIGHT)
        private const val SIM_STEP = 1f / 30f
        private val WHITE = 0xFFFFFFFF.toInt()
        private val BLACK = 0xFF000000.toInt()
    }
    var style = RetuiWallpaperSettings.pixelLook(context, SCENE)?.takeIf { it in STYLES } ?: DARK
        set(value) { field = value; invalidate() }
    private var field: RippleField? = null
    private val rng = Random(System.nanoTime())
    private var nextDrop = 0.4f
    private var pending = 0f

    override fun onGrid(width: Int, height: Int) { field = RippleField(width, height) }

    override fun step(seconds: Float) {
        val f = field ?: return
        nextDrop -= seconds
        if (nextDrop <= 0f) {
            f.drop(rng.nextInt(4, gw - 4), rng.nextInt(4, gh - 4), rng.nextInt(2, 5), 4f + rng.nextFloat() * 5f)
            nextDrop = 0.7f + rng.nextFloat() * 2.2f
        }
        pending += seconds
        while (pending >= SIM_STEP) { f.step(); pending -= SIM_STEP }
    }

    override fun render() {
        val f = field ?: return
        val palette = if (style == LIGHT) intArrayOf(WHITE, BLACK) else intArrayOf(BLACK, WHITE)
        for (y in 0 until gh) for (x in 0 until gw) {
            // Only the moving water shows: rings are drawn by how steep the surface is, on a plain ground.
            val slope = f.height(x - 1, y) - f.height(x + 1, y) + f.height(x, y - 1) - f.height(x, y + 1)
            px[y * gw + x] = PixelDither.pick(kotlin.math.abs(slope) * 0.12f, x, y, palette)
        }
    }

    // A settled surface waits for the next drop instead of redrawing the same frame.
    override fun frameDelayMs(): Long = if (field?.quiet != false) (nextDrop * 1000).toLong().coerceIn(33L, 1000L) else 33L

    override fun loadSettings() { style = RetuiWallpaperSettings.pixelLook(context, SCENE)?.takeIf { it in STYLES } ?: DARK }

    override fun handleTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN && event.actionMasked != MotionEvent.ACTION_MOVE) return
        val f = field ?: return
        val x = ((event.x - screenX(0f)) / cell).toInt()
        val y = ((event.y - screenY(0f)) / cell).toInt()
        f.drop(x, y, 2, if (event.actionMasked == MotionEvent.ACTION_DOWN) 8f else 3f)
    }

    override fun signatureColors() = if (style == LIGHT) intArrayOf(WHITE, 0xFF808080.toInt(), BLACK) else intArrayOf(BLACK, 0xFF808080.toInt(), WHITE)
}

/**
 * Any photo, dithered into a portrait-shaped picture inside a small ANSI box-drawing frame. Still unless ripples are
 * on: then a drop lands on the picture now and then, the image wobbles under it and settles again.
 */
class PhotoDitherView(context: Context) : PixelWallpaperView(context, 270f) {
    companion object {
        const val SCENE = "dither photo"
        const val OFFSET_STEP = 0.05f
        fun saved(context: Context): File {
            // The apply page previews a staged photo; the running wallpaper keeps the applied one.
            if (context is RetuiWallpaperSettings.PreviewContext && RetuiWallpaperSettings.hasPending(context))
                pending(context).takeIf { it.isFile }?.let { return it }
            return File(context.filesDir, "dither-photo.jpg")
        }
        fun pending(context: Context) = File(context.filesDir, "dither-photo.pending.jpg")

        /** The user applied: a staged photo replaces the applied one. */
        fun commitPending(context: Context) {
            val staged = pending(context)
            if (staged.isFile && !staged.renameTo(File(context.filesDir, "dither-photo.jpg"))) {
                staged.copyTo(File(context.filesDir, "dither-photo.jpg"), overwrite = true); staged.delete()
            }
        }
        private const val SIM_STEP = 1f / 30f
    }
    var look = PixelLook.of(RetuiWallpaperSettings.pixelLook(context, SCENE), PixelLook.MONO)
        set(value) { field = value; palette = value.colors(PixelLook.themeInk()); invalidate() }
    var ripples = RetuiWallpaperSettings.photoRipples(context)
        set(value) { field = value; if (!value) water = null; invalidate() }
    /** The photo shown; the picker previews a new one before Use on phone saves it. */
    var photo: File? = saved(context).takeIf { it.isFile }
        set(value) { field = value; source?.recycle(); source = null; tones = null; invalidate() }
    /** Where the frame sits, from -0.5 (top) to 0.5 (bottom); the picker's ↑ and ↓ move it. */
    var offset = RetuiWallpaperSettings.photoOffset(context)
        set(value) { field = value.coerceIn(-0.5f, 0.5f); place(); invalidate() }
    private var palette = look.colors(PixelLook.themeInk())
    private var source: Bitmap? = null
    private var tones: FloatArray? = null
    private var rx = 0; private var ry = 0; private var rw = 0; private var rh = 0
    private var water: RippleField? = null
    private val rng = Random(System.nanoTime())
    private var nextDrop = 1.5f
    private var pending = 0f
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onGrid(width: Int, height: Int) {
        // Portrait (3:4), about two thirds of the width, a little above centre.
        rw = (width * 0.66f).roundToInt()
        rh = min((rw * 4f / 3f).roundToInt(), (height * 0.62f).roundToInt())
        rw = min(rw, (rh * 3f / 4f).roundToInt())
        rx = (width - rw) / 2
        place()
        tones = null
        water = null
    }

    private fun place() { ry = ((gh - rh) * (0.42f + offset)).roundToInt().coerceIn(1, (gh - rh - 1).coerceAtLeast(1)) }

    /** The photo's brightness at picture size, centre-cropped to the frame and stretched to full contrast. */
    private fun tones(): FloatArray {
        tones?.let { return it }
        val out = FloatArray(rw * rh)
        val bitmap = source ?: photo?.let { decode(it) }?.also { source = it }
        if (bitmap == null) {
            // No photo yet: a soft lit sphere, so the frame shows what the look does.
            for (y in 0 until rh) for (x in 0 until rw) {
                val dx = (x - rw * 0.5f) / (rw * 0.42f); val dy = (y - rh * 0.5f) / (rw * 0.42f)
                val d = dx * dx + dy * dy
                out[y * rw + x] = if (d > 1f) 0.04f else (0.25f + 0.75f * (1f - ((dx + 0.35f) * (dx + 0.35f) + (dy + 0.35f) * (dy + 0.35f)) / 2f)).coerceIn(0f, 1f)
            }
            return out.also { tones = it }
        }
        val scale = max(rw / bitmap.width.toFloat(), rh / bitmap.height.toFloat())
        val cw = (rw / scale).roundToInt().coerceIn(1, bitmap.width)
        val ch = (rh / scale).roundToInt().coerceIn(1, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, (bitmap.width - cw) / 2, (bitmap.height - ch) / 2, cw, ch)
        val small = Bitmap.createScaledBitmap(crop, rw, rh, true)
        val pixels = IntArray(rw * rh)
        small.getPixels(pixels, 0, rw, 0, 0, rw, rh)
        if (small !== crop) small.recycle()
        if (crop !== bitmap) crop.recycle()
        for (i in pixels.indices) out[i] = PixelDither.luminance(pixels[i])
        // Auto levels: the 2nd and 98th percentiles become black and white, so dark or flat photos still read.
        val sorted = out.copyOf().also { it.sort() }
        val low = sorted[(sorted.size * 0.02f).toInt()]
        val high = sorted[(sorted.size * 0.98f).toInt().coerceAtMost(sorted.lastIndex)].coerceAtLeast(low + 0.05f)
        for (i in out.indices) out[i] = ((out[i] - low) / (high - low)).coerceIn(0f, 1f)
        return out.also { tones = it }
    }

    private fun decode(file: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    override val animated: Boolean get() = ripples

    override fun step(seconds: Float) {
        if (!ripples || rw <= 0) return
        val w = water ?: RippleField(rw, rh, 0.975f).also { water = it }
        nextDrop -= seconds
        if (nextDrop <= 0f) {
            w.drop(rng.nextInt(6, rw - 6), rng.nextInt(6, rh - 6), rng.nextInt(4, 7), 10f + rng.nextFloat() * 6f)
            nextDrop = 3f + rng.nextFloat() * 5f
        }
        pending += seconds
        while (pending >= SIM_STEP) { w.step(); pending -= SIM_STEP }
    }

    override fun render() {
        px.fill(palette[0])
        if (rw <= 0) return
        val t = tones()
        val w = water
        for (y in 0 until rh) for (x in 0 until rw) {
            var sx = x; var sy = y
            var light = 0f
            if (w != null && !w.quiet) {
                // Refraction: the picture shifts with the slope of the water, and the slope catches a little light.
                val dx = w.height(x - 1, y) - w.height(x + 1, y)
                val dy = w.height(x, y - 1) - w.height(x, y + 1)
                sx = (x + dx * 4f).roundToInt().coerceIn(0, rw - 1)
                sy = (y + dy * 4f).roundToInt().coerceIn(0, rh - 1)
                light = (dx + dy) * 0.2f
            }
            val gx = rx + x; val gy = ry + y
            px[gy * gw + gx] = PixelDither.pick(t[sy * rw + sx] + light, gx, gy, palette)
        }
    }

    /** The ANSI frame: double box-drawing lines one character outside the picture, in the launcher font. */
    override fun drawOverlay(canvas: Canvas) {
        if (rw <= 0) return
        framePaint.typeface = Tuils.getTypeface(context)
        framePaint.color = palette.last()
        framePaint.textSize = max(width / 34f, cell * 4f)
        val cw = framePaint.measureText("═")
        val metrics = framePaint.fontMetrics
        val lh = metrics.descent - metrics.ascent
        val left = screenX(rx.toFloat()); val right = screenX((rx + rw).toFloat())
        val top = screenY(ry.toFloat()); val bottom = screenY((ry + rh).toFloat())
        val cols = ceil((right - left) / cw).toInt()
        val rows = ceil((bottom - top) / lh).toInt()
        // Centred on the picture: the frame's inner box is whole characters, never narrower than the photo.
        val x0 = (left + right) / 2f - cols * cw / 2f - cw
        val y0 = (top + bottom) / 2f - rows * lh / 2f - lh
        fun put(text: String, col: Int, row: Int) = canvas.drawText(text, x0 + col * cw, y0 + row * lh - metrics.ascent, framePaint)
        put("╔" + "═".repeat(cols) + "╗", 0, 0)
        for (r in 1..rows) { put("║", 0, r); put("║", cols + 1, r) }
        put("╚" + "═".repeat(cols) + "╝", 0, rows + 1)
    }

    // Still, it never redraws; with ripples on, it wakes for the next drop and runs only while water moves.
    override fun frameDelayMs(): Long = when {
        !ripples -> 1000L
        water?.quiet != false -> (nextDrop * 1000).toLong().coerceIn(33L, 1000L)
        else -> 33L
    }

    override fun loadSettings() {
        look = PixelLook.of(RetuiWallpaperSettings.pixelLook(context, SCENE), PixelLook.MONO)
        ripples = RetuiWallpaperSettings.photoRipples(context)
        offset = RetuiWallpaperSettings.photoOffset(context)
        val saved = saved(context).takeIf { it.isFile }
        if (saved?.lastModified() != photo?.lastModified() || saved?.path != photo?.path) photo = saved
    }

    override fun release() { super.release(); source?.recycle(); source = null; tones = null }

    override fun signatureColors() = palette.let { intArrayOf(it.last(), it[it.size / 2], it.first()) }
}

/** The classic Doom fire: heat rises from the bottom row, cooling and drifting at random. */
class DoomFireView(context: Context) : PixelWallpaperView(context, 110f) {
    companion object {
        const val SCENE = "doom fire"
        const val CLASSIC = "classic"
        val LOOKS = listOf(CLASSIC) + PixelLook.entries.map { it.id }
        // The original 37-step fire palette, black to white through red and yellow.
        private val FIRE = intArrayOf(
            0x070707, 0x1F0707, 0x2F0F07, 0x470F07, 0x571707, 0x671F07, 0x771F07, 0x8F2707, 0x9F2F07, 0xAF3F07,
            0xBF4707, 0xC74707, 0xDF4F07, 0xDF5707, 0xDF5707, 0xD75F07, 0xD75F07, 0xD7670F, 0xCF6F0F, 0xCF770F,
            0xCF7F0F, 0xCF8717, 0xC78717, 0xC78F17, 0xC7971F, 0xBF9F1F, 0xBF9F1F, 0xBFA727, 0xBFA727, 0xBFAF2F,
            0xB7AF2F, 0xB7B72F, 0xB7B737, 0xCFCF6F, 0xDFDF9F, 0xEFEFC7, 0xFFFFFF
        ).map { it or 0xFF000000.toInt() }.toIntArray()
        private const val SIM_STEP = 1f / 27f
    }
    var styleName = RetuiWallpaperSettings.pixelLook(context, SCENE) ?: CLASSIC
        set(value) { field = value; palette = if (value == CLASSIC) null else PixelLook.of(value).colors(PixelLook.themeInk()); invalidate() }
    private var palette: IntArray? = if (styleName == CLASSIC) null else PixelLook.of(styleName).colors(PixelLook.themeInk())
    private var heat = ByteArray(0)
    private val rng = Random(System.nanoTime())
    private var pending = 0f

    override fun onGrid(width: Int, height: Int) {
        heat = ByteArray(width * height)
        for (x in 0 until width) heat[(height - 1) * width + x] = 36
    }

    override fun step(seconds: Float) {
        pending += seconds
        var steps = 0
        while (pending >= SIM_STEP && steps < 4) { spread(); pending -= SIM_STEP; steps++ }
        if (steps == 4) pending = 0f
    }

    private fun spread() {
        val w = gw
        for (x in 0 until w) for (y in 1 until gh) {
            val src = y * w + x
            val value = heat[src].toInt()
            if (value == 0) { heat[src - w] = 0; continue }
            val r = rng.nextInt(4)
            // Drift sideways with the random draw and cool by 0 or 1; this is what makes the flames flicker.
            val dst = (src - r + 1 - w).coerceIn(0, heat.size - 1)
            heat[dst] = (value - (r and 1)).coerceAtLeast(0).toByte()
        }
    }

    override fun render() {
        val p = palette
        for (y in 0 until gh) for (x in 0 until gw) {
            val v = heat[y * gw + x].toInt()
            px[y * gw + x] = if (p == null) FIRE[v] else PixelDither.pick(v / 36f, x, y, p)
        }
    }

    override fun frameDelayMs() = 37L

    override fun loadSettings() { styleName = RetuiWallpaperSettings.pixelLook(context, SCENE) ?: CLASSIC }

    override fun signatureColors() = palette?.let { intArrayOf(it.last(), it[it.size / 2], it.first()) }
        ?: intArrayOf(FIRE[30], FIRE[12], FIRE[0])
}
