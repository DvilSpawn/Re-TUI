package ohi.andre.consolelauncher.wallpaper.pixel

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A palette a pixel wallpaper is dithered to, darkest colour first. */
enum class PixelLook(val id: String, private val fixed: IntArray?) {
    MONO("mono", intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())),
    GAMEBOY("gameboy", intArrayOf(0xFF0F380F.toInt(), 0xFF306230.toInt(), 0xFF8BAC0F.toInt(), 0xFF9BBC0F.toInt())),
    // Black to the launcher's accent colour.
    THEME("theme", null);

    fun colors(ink: Int): IntArray = fixed ?: intArrayOf(
        0xFF000000.toInt(), PixelDither.mix(0xFF000000.toInt(), ink, 0.35f), PixelDither.mix(0xFF000000.toInt(), ink, 0.7f), ink or 0xFF000000.toInt()
    )

    companion object {
        fun of(id: String?, fallback: PixelLook = MONO) = entries.firstOrNull { it.id == id } ?: fallback

        /**
         * The colour a preset is recognised by: the accent (its borders, unless set apart). Falls back to the text
         * colour when the accent is see-through or too dark to show on black.
         */
        fun themeInk(): Int = runCatching {
            val accent = ThemeColorResolver.color(Theme.accent_color)
            if (Color.alpha(accent) < 128 || PixelDither.luminance(accent) < 0.15f) LauncherSettings.getColor(Theme.output_text_color) else accent
        }.getOrDefault(0xFFFFFFFF.toInt())
    }
}

object PixelDither {
    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun threshold(x: Int, y: Int): Float = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f

    /** Brightness [value] (0..1) at pixel (x, y) as one of [palette]'s colours, ordered-dithered between neighbours. */
    fun pick(value: Float, x: Int, y: Int, palette: IntArray): Int {
        val scaled = value.coerceIn(0f, 1f) * (palette.size - 1)
        val base = scaled.toInt().coerceAtMost(palette.size - 1)
        return if (base < palette.size - 1 && scaled - base > threshold(x, y)) palette[base + 1] else palette[base]
    }

    fun luminance(c: Int): Float = ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 255000f

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a ushr shift) and 255) + (((b ushr shift) and 255) - ((a ushr shift) and 255)) * t).roundToInt() shl shift
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }
}

/**
 * The shared pixel renderer (as Astro Chart uses): each frame is painted into a small grid of chart pixels,
 * about [gridShortSide] across the short side, and scaled up by a whole number with nearest sampling so every
 * pixel stays a crisp square. Subclasses step their simulation and fill [px]; this class owns the grid, the clock
 * and drawing. Inside an activity (the picker preview) it runs its own frame loop; the wallpaper service drives it
 * otherwise through [advance].
 */
abstract class PixelWallpaperView(context: Context, private val gridShortSide: Float) : View(context) {
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    protected var gw = 0; private set
    protected var gh = 0; private set
    protected var cell = 1; private set
    protected var px = IntArray(0); private set
    private var bitmap: Bitmap? = null
    private var lastFrameMs = 0L
    private var running = false
    private val frameLoop = object : Runnable {
        override fun run() {
            if (!running) return
            advance()
            invalidate()
            postDelayed(this, frameDelayMs())
        }
    }

    /** Milliseconds until the next frame is worth drawing; a still scene can ask for long gaps. */
    abstract fun frameDelayMs(): Long
    /** False while nothing moves, so the service draws once and waits. */
    open val animated: Boolean get() = true
    protected abstract fun onGrid(width: Int, height: Int)
    /** Advance by [seconds] of real time (capped, so a long pause is not replayed). */
    protected abstract fun step(seconds: Float)
    /** Fill [px] for the current state. */
    protected abstract fun render()
    open fun loadSettings() {}
    open fun handleTouch(event: MotionEvent) {}
    abstract fun signatureColors(): IntArray

    fun advance(nowMs: Long = SystemClock.uptimeMillis()) {
        if (!ensureGrid()) return
        val seconds = if (lastFrameMs == 0L) 0f else (nowMs - lastFrameMs).coerceIn(0L, 250L) / 1000f
        lastFrameMs = nowMs
        step(seconds)
    }

    fun resetClock() { lastFrameMs = 0L }

    private fun ensureGrid(): Boolean {
        if (width <= 0 || height <= 0) return false
        val c = max(1, (min(width, height) / gridShortSide).roundToInt())
        val w = ceil(width / c.toFloat()).toInt()
        val h = ceil(height / c.toFloat()).toInt()
        if (w == gw && h == gh && c == cell && bitmap != null) return true
        cell = c; gw = w; gh = h
        px = IntArray(w * h)
        bitmap?.recycle()
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        onGrid(w, h)
        return true
    }

    /** Where chart pixel (x, y) lands on screen. */
    protected fun screenX(x: Float) = (width - gw * cell) / 2f + x * cell
    protected fun screenY(y: Float) = (height - gh * cell) / 2f + y * cell

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        if (!ensureGrid()) return
        val b = bitmap ?: return
        render()
        b.setPixels(px, 0, gw, 0, 0, gw, gh)
        val w = gw * cell; val h = gh * cell
        val left = (width - w) / 2; val top = (height - h) / 2
        canvas.drawBitmap(b, Rect(0, 0, gw, gh), Rect(left, top, left + w, top + h), paint)
        drawOverlay(canvas)
    }

    /** Drawn at screen resolution over the scaled frame (the photo's text frame). */
    protected open fun drawOverlay(canvas: Canvas) {}

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); updateAnimation() }

    private fun updateAnimation() {
        running = isAttachedToWindow && windowVisibility == VISIBLE && hasWindowFocus()
        removeCallbacks(frameLoop)
        resetClock()
        if (running) post(frameLoop)
    }

    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }

    open fun release() {
        running = false
        removeCallbacks(frameLoop)
        bitmap?.recycle(); bitmap = null
        gw = 0; gh = 0
    }

    @RequiresApi(27)
    fun wallpaperColors(): WallpaperColors {
        val c = signatureColors()
        return WallpaperColors(Color.valueOf(c[0]), Color.valueOf(c[1]), Color.valueOf(c[2]))
    }
}

/**
 * Water ripples on a grid (the classic two-buffer wave): a drop pushes the surface down, the wave spreads and
 * fades. [quiet] once it has settled, so a still surface costs nothing.
 */
class RippleField(val w: Int, val h: Int, private val damping: Float = 0.985f) {
    private var current = FloatArray(w * h)
    private var previous = FloatArray(w * h)
    var quiet = true; private set

    fun height(x: Int, y: Int): Float = if (x in 0 until w && y in 0 until h) current[y * w + x] else 0f

    fun drop(cx: Int, cy: Int, radius: Int = 3, strength: Float = 6f) {
        for (y in cy - radius..cy + radius) for (x in cx - radius..cx + radius) {
            if (x !in 1 until w - 1 || y !in 1 until h - 1) continue
            val d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            if (d2 <= radius * radius) previous[y * w + x] -= strength * (1f - d2 / (radius * radius + 1f))
        }
        quiet = false
    }

    fun step() {
        if (quiet) return
        var energy = 0f
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val next = ((current[i - 1] + current[i + 1] + current[i - w] + current[i + w]) / 2f - previous[i]) * damping
                previous[i] = next
                val a = if (next < 0) -next else next
                if (a > energy) energy = a
            }
        }
        val t = current; current = previous; previous = t
        if (energy < 0.02f) { current.fill(0f); previous.fill(0f); quiet = true }
    }
}
