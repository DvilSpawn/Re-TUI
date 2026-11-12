package ohi.andre.consolelauncher.mono

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The mono home's banner: a small animated scene for the weather and the time of day, drawn as brightness and
 * ordered-dithered to ink and paper on a coarse grid, behind a strip of dithered hills. It steps at a low,
 * deliberate frame rate and stops while the home is not on screen.
 */
class MonoBanner(context: Context, private val ink: Int, private val paper: Int) : View(context) {
    enum class Scene { CLEAR_DAY, CLEAR_NIGHT, CLOUDS, RAIN, SNOW, STORM, FOG }

    /** The weather's symbol code (met.no style: clearsky_day, rain, fog…), or null when there is no weather. */
    var symbol: String? = null
        set(value) { field = value; invalidate() }

    private var preview: Scene? = null
    private var previewUntil = 0L
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    private val started = SystemClock.uptimeMillis()
    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            if (running) postDelayed(this, FRAME_MS)
        }
    }
    private var running = false

    init {
        isClickable = true
        contentDescription = context.getString(ohi.andre.consolelauncher.R.string.mono_banner_description)
        // Tapping the sky steps through the scenes, then the live weather returns.
        setOnClickListener {
            val now = SystemClock.uptimeMillis()
            val current = if (now < previewUntil) preview ?: liveScene() else liveScene()
            preview = Scene.entries[(current.ordinal + 1) % Scene.entries.size]
            previewUntil = now + PREVIEW_MS
            invalidate()
        }
    }

    /** The scene for the weather now, or for the hour when there is no weather. */
    fun liveScene(): Scene {
        val s = symbol.orEmpty()
        val night = s.endsWith("_night") || (s.isEmpty() && isNight())
        return when {
            "thunder" in s -> Scene.STORM
            "snow" in s -> Scene.SNOW
            "rain" in s || "sleet" in s -> Scene.RAIN
            s == "fog" -> Scene.FOG
            s == "cloudy" || s.startsWith("partlycloudy") -> Scene.CLOUDS
            night -> Scene.CLEAR_NIGHT
            else -> Scene.CLEAR_DAY
        }
    }

    private fun isNight(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 19
    }

    /** Sky brightness for the hour: dark at night, a lift through dawn and dusk, brightest at midday. */
    private fun daylight(): Float {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
        return when {
            h < 5f || h >= 21f -> 0f
            h < 8f -> (h - 5f) / 3f
            h >= 18f -> (21f - h) / 3f
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        val cell = max(2, (resources.displayMetrics.density * 1.25f).roundToInt())
        val w = width / cell
        val h = height / cell
        if (w <= 0 || h <= 0) return
        if (bitmap?.width != w || bitmap?.height != h) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            pixels = IntArray(w * h)
        }
        val now = SystemClock.uptimeMillis()
        if (now >= previewUntil) preview = null
        val scene = preview ?: liveScene()
        // Time steps in whole frames so motion moves cell by cell, like an old display.
        val t = ((now - started) / FRAME_MS) * FRAME_MS / 1000f
        render(scene, w, h, t)
        val b = bitmap ?: return
        b.setPixels(pixels, 0, w, 0, 0, w, h)
        canvas.drawBitmap(b, Rect(0, 0, w, h), Rect(0, 0, w * cell, h * cell), paint)
    }

    private fun render(scene: Scene, w: Int, h: Int, t: Float) {
        val light = daylight()
        val flash = scene == Scene.STORM && (t % 7f) in 0f..0.25f
        for (y in 0 until h) for (x in 0 until w) {
            val ny = y / h.toFloat()
            var v = sky(scene, light, ny)
            v = max(v, body(scene, x, y, w, h, t))
            v = weather(scene, v, x, y, w, h, t)
            // Foreground hills, always on top.
            val hill = h * (0.66f + 0.10f * sin(x / (w / 9f)) + 0.06f * sin(x / (w / 27f) + 1.3f))
            if (y > hill) v = 0.82f - (y - hill) * 0.55f / h
            if (flash) v = 1f - v
            val threshold = (bayer[(y % 4) * 4 + (x % 4)] + 0.5f) / 16f
            pixels[y * w + x] = if (v > threshold) ink else paper
        }
    }

    /** The sky's own tone: clear days are brighter toward the horizon, nights nearly black. */
    private fun sky(scene: Scene, light: Float, ny: Float): Float = when (scene) {
        Scene.CLEAR_NIGHT -> 0.02f
        Scene.CLEAR_DAY -> (0.08f + 0.22f * ny) * (0.4f + 0.6f * light)
        Scene.CLOUDS, Scene.FOG -> (0.10f + 0.12f * ny) * (0.3f + 0.7f * light)
        else -> (0.05f + 0.10f * ny) * (0.3f + 0.7f * light)
    }

    /** The sun or the moon, and the stars. */
    private fun body(scene: Scene, x: Int, y: Int, w: Int, h: Int, t: Float): Float {
        val cx = w * 0.78f
        val cy = h * 0.34f
        val r = h * 0.17f
        val d = hypot(x - cx, y - cy)
        return when (scene) {
            Scene.CLEAR_DAY -> when {
                d < r -> 1f
                d < r * 2.1f -> {
                    // Rays: twelve spokes turning slowly, fading out from the disc.
                    val a = atan2(y - cy, x - cx)
                    if (cos(12f * (a - t * 0.25f)) > 0.82f) 0.9f * (1f - (d - r) / (r * 1.1f)) else 0f
                }
                else -> 0f
            }
            Scene.CLEAR_NIGHT -> {
                val moon = if (d < r && hypot(x - (cx + r * 0.45f), y - (cy - r * 0.2f)) > r * 0.85f) 0.95f else 0f
                // Stars: sparse fixed points, each twinkling on its own phase.
                val seed = ((x * 73856093) xor (y * 19349663)) and 0x7fffffff
                val star = if (seed % 61 == 0 && y < h * 0.6f) (0.55f + 0.45f * sin(t * 2.3f + seed % 17)) else 0f
                max(moon, star)
            }
            else -> 0f
        }
    }

    /** Clouds, rain, snow and fog over the sky. */
    private fun weather(scene: Scene, v: Float, x: Int, y: Int, w: Int, h: Int, t: Float): Float = when (scene) {
        // Crisp-edged banks: density pushed toward solid, so the dither draws shapes rather than haze.
        Scene.CLOUDS -> max(v, smooth(0.25f, 0.6f, clouds(x, y, w, h, t, 1f)) * 0.95f)
        Scene.RAIN, Scene.STORM -> {
            // A heavy cloud deck along the top, then streaks: every other slanted column carries a drop falling
            // at its own speed.
            val deck = if (y < h * 0.22f) 0.7f + 0.3f * clouds(x, y, w, h, t, 1f) else 0f
            val col = x + floor(y * 0.4f).toInt()
            val speed = 30f + (col * 37 % 17)
            val head = ((col * 53 % h) + t * speed) % (h * 1.3f)
            val drop = col % 2 == 0 && y > h * 0.2f && y <= head && y >= head - 5f
            val bolt = scene == Scene.STORM && (t % 7f) in 0f..0.5f && lightning(x, y, w, h)
            if (drop || bolt) 1f else max(v, deck)
        }
        Scene.SNOW -> {
            val deck = if (y < h * 0.2f) 0.55f + 0.3f * clouds(x, y, w, h, t, 1f) else 0f
            var flake = 0f
            for (i in 0 until 70) {
                val fy = ((i * 97 % h) + t * (4f + i % 5)) % h
                val fx = ((i * 211) % w) + sin(t * 1.1f + i) * 3f
                if (abs(x - fx) < 1.2f && abs(y - fy) < 1.2f) flake = 1f
            }
            max(max(v, deck), flake)
        }
        Scene.FOG -> max(v, 0.32f + 0.18f * sin(x * 0.07f + t * 0.8f + y * 0.45f) * sin(y * 0.9f))
        else -> v
    }

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val k = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    /** A jagged bolt from the cloud deck down toward the hills. */
    private fun lightning(x: Int, y: Int, w: Int, h: Int): Boolean {
        if (y < h * 0.18f || y > h * 0.7f) return false
        val step = (y / (h * 0.1f)).toInt()
        val bx = w * 0.3f + (if (step % 2 == 0) 3f else -3f) * (step % 3) + step * 1.5f
        return abs(x - bx) < 1.1f
    }

    /** Soft cloud banks drifting right, as summed blobs whose density the dither turns into texture. */
    private fun clouds(x: Int, y: Int, w: Int, h: Int, t: Float, cover: Float): Float {
        var c = 0f
        for (i in 0 until 5) {
            val cx = ((i * 0.23f * w + t * (3f + i)) % (w * 1.3f)) - w * 0.15f
            val cy = h * (0.18f + 0.09f * (i % 3))
            val dx = (x - cx) / (w * 0.11f)
            val dy = (y - cy) / (h * 0.13f)
            c += exp(-(dx * dx + dy * dy))
        }
        return min(1f, c * cover)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateRunning() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); updateRunning() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateRunning() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); updateRunning() }

    private fun updateRunning() {
        val should = isAttachedToWindow && windowVisibility == VISIBLE && isShown
        if (should == running) return
        running = should
        removeCallbacks(frame)
        if (running) post(frame)
    }

    private companion object {
        const val FRAME_MS = 125L
        const val PREVIEW_MS = 10_000L
    }
}
