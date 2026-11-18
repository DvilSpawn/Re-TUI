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
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The mono home's scene: crisp pixel art of a town under the weather and the time of day. A skyline with lit
 * windows, hills dithered in front, and above it the sun or the moon and stars, cloud sprites, rain, snow or fog.
 * Everything moves a whole cell at a time at a few frames a second, and stops while the home is not on screen.
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
    private var on = BooleanArray(0)
    private var pixels = IntArray(0)
    private var w = 0
    private var h = 0
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
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

    override fun onDraw(canvas: Canvas) {
        val cell = max(2, (resources.displayMetrics.density * 2f).roundToInt())
        w = width / cell
        h = height / cell
        if (w <= 0 || h <= 0) return
        if (bitmap?.width != w || bitmap?.height != h) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            on = BooleanArray(w * h)
            pixels = IntArray(w * h)
        }
        val now = SystemClock.uptimeMillis()
        if (now >= previewUntil) preview = null
        val scene = preview ?: liveScene()
        val step = (now / FRAME_MS).toInt()
        on.fill(false)
        render(scene, step)
        for (i in on.indices) pixels[i] = if (on[i]) ink else paper
        val b = bitmap ?: return
        b.setPixels(pixels, 0, w, 0, 0, w, h)
        // Centred, so leftover pixels split evenly round the edge.
        val left = (width - w * cell) / 2
        val top = (height - h * cell) / 2
        canvas.drawBitmap(b, Rect(0, 0, w, h), Rect(left, top, left + w * cell, top + h * cell), paint)
    }

    private fun render(scene: Scene, step: Int) {
        val horizon = (h * 0.62f).toInt()
        val night = scene == Scene.CLEAR_NIGHT || (scene != Scene.CLEAR_DAY && isNight())
        // A thin dithered haze above the horizon gives the sky depth.
        for (y in horizon - 6 until horizon) for (x in 0 until w) dither(x, y, 0.12f)
        when (scene) {
            Scene.CLEAR_DAY -> sun(step)
            Scene.CLEAR_NIGHT -> { stars(step, horizon); moon() }
            Scene.CLOUDS -> { if (night) moon() else sun(step); clouds(step, 3, 0.12f) }
            Scene.RAIN -> { clouds(step, 4, 0.02f); drops(step, horizon, snow = false) }
            Scene.SNOW -> { clouds(step, 3, 0.02f); drops(step, horizon, snow = true) }
            Scene.STORM -> { clouds(step, 4, 0.0f); drops(step, horizon, snow = false); bolt(step, horizon) }
            Scene.FOG -> fog(step, horizon)
        }
        skyline(step, horizon, night || scene == Scene.STORM || scene == Scene.RAIN)
        hills(horizon)
    }

    private fun set(x: Int, y: Int, value: Boolean = true) {
        if (x in 0 until w && y in 0 until h) on[y * w + x] = value
    }

    private fun dither(x: Int, y: Int, level: Float) {
        if (level > (bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f) set(x, y)
    }

    private fun disc(cx: Int, cy: Int, r: Int, value: Boolean = true) {
        for (y in -r..r) for (x in -r..r) if (x * x + y * y <= r * r + r / 2) set(cx + x, cy + y, value)
    }

    private fun hash(a: Int, b: Int = 0): Int {
        var v = a * 374761393 + b * 668265263
        v = (v xor (v ushr 13)) * 1274126177
        return (v xor (v ushr 16)) and 0x7fffffff
    }

    /** A sun whose rays step out and back slowly. */
    private fun sun(step: Int) {
        val cx = (w * 0.74f).toInt()
        val cy = (h * 0.24f).toInt()
        val r = max(3, h / 10)
        disc(cx, cy, r)
        val reach = r + 2 + (step / 4) % 2
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)) {
            val long = dx == 0 || dy == 0
            for (k in r + 2..reach + if (long) 1 else 0) set(cx + dx * (if (long) k else k * 3 / 4), cy + dy * (if (long) k else k * 3 / 4))
        }
    }

    /** A crescent: a disc with a paper bite. */
    private fun moon() {
        val cx = (w * 0.74f).toInt()
        val cy = (h * 0.24f).toInt()
        val r = max(3, h / 10)
        disc(cx, cy, r)
        disc(cx + r / 2 + 1, cy - r / 3, r, value = false)
    }

    /** Fixed stars; a few blink now and then. */
    private fun stars(step: Int, horizon: Int) {
        for (i in 0 until w * horizon / 70) {
            val x = hash(i, 1) % w
            val y = hash(i, 2) % max(1, horizon - 8)
            val blinks = i % 4 == 0
            if (!blinks || (step + hash(i, 3)) % 9 != 0) set(x, y)
        }
    }

    /** Pixel cloud sprites, flat-bottomed with stepped tops, drifting right a cell at a time. */
    private fun clouds(step: Int, count: Int, fill: Float) {
        for (i in 0 until count) {
            val cw = w / 3 + hash(i, 5) % (w / 5 + 1)
            val ch = max(4, h / 9)
            val travel = w + cw
            val x0 = ((hash(i, 6) % travel) + step / (3 + i % 2)) % travel - cw
            val y0 = 2 + (i * h / 9) + hash(i, 7) % 3
            for (x in 0 until cw) {
                // Bumps: two or three rounded humps along the top.
                val bump = (ch * (0.55f + 0.45f * abs(kotlin.math.sin(x * Math.PI / (cw / 2.5))).toFloat())).toInt()
                val top = y0 + ch - bump
                for (y in top until y0 + ch) {
                    val edge = y == top || y == y0 + ch - 1 || x == 0 || x == cw - 1
                    // Clear the sky behind the cloud, then outline it and fill it lightly.
                    set(x0 + x, y, false)
                    if (edge) set(x0 + x, y) else dither(x0 + x, y, fill)
                }
            }
        }
    }

    /** Rain falls as short slanted streaks; snow as single flakes drifting side to side. */
    private fun drops(step: Int, horizon: Int, snow: Boolean) {
        val count = w * horizon / (if (snow) 40 else 60)
        for (i in 0 until count) {
            val speed = if (snow) 1 else 3
            val x = hash(i, 8) % w
            val y = (hash(i, 9) % horizon + step * speed) % horizon
            if (y < h / 6) continue
            if (snow) set(x + ((step / 3 + i) % 3) - 1, y)
            else {
                // A streak four cells long, leaning one cell left for every two down.
                val sx = ((x - y / 2) % w + w) % w
                for (k in 0 until 4) set(sx + k / 2, y - k)
            }
        }
    }

    /** Every few seconds a bolt cracks for two frames. */
    private fun bolt(step: Int, horizon: Int) {
        if (step % 24 > 1) return
        var x = (w * (0.25f + (step / 24 % 3) * 0.2f)).toInt()
        for (y in h / 6 until horizon) {
            set(x, y); set(x + 1, y)
            if (y % 3 == 0) x += if ((y / 3) % 2 == 0) 1 else -2
        }
    }

    /** Fog: dashed bands sliding across, one cell at a time. */
    private fun fog(step: Int, horizon: Int) {
        var y = 3
        var band = 0
        while (y < horizon) {
            val shift = step / (2 + band % 2) * (if (band % 2 == 0) 1 else -1)
            for (x in 0 until w) if (((x + shift) % 12 + 12) % 12 < 8) set(x, y)
            y += 4
            band++
        }
    }

    /** Town on the horizon: block towers with windows that light and go out one at a time. */
    private fun skyline(step: Int, horizon: Int, lit: Boolean) {
        var x = 0
        var i = 0
        while (x < w) {
            val bw = 4 + hash(i, 10) % 6
            val bh = 4 + hash(i, 11) % max(4, h / 4)
            val top = horizon - bh
            for (bx in x until x + bw) for (by in top until h) set(bx, by)
            // Windows: paper pixels on a 2-cell grid; at night most are lit and one in a few toggles slowly.
            var wy = top + 2
            while (wy < horizon - 1) {
                var wx = x + 1
                while (wx < x + bw - 1) {
                    val id = hash(wx, wy)
                    val shown = if (lit) (id % 5 != 0) xor ((step / 6 + id) % 37 == 0) else (id % 9 == 0)
                    if (shown) set(wx, wy, false)
                    wx += 2
                }
                wy += 2
            }
            x += bw + hash(i, 12) % 2
            i++
        }
    }

    /** Hills in front of the town, dithered half tone with a solid crest. */
    private fun hills(horizon: Int) {
        for (x in 0 until w) {
            val crest = horizon + 2 + (2.5 * kotlin.math.sin(x / (w / 4.0)) + 1.5 * kotlin.math.sin(x / (w / 11.0) + 1.3)).toInt()
            for (y in crest until h) {
                set(x, y, false)
                if (y == crest) set(x, y) else dither(x, y, 0.5f)
            }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateRunning() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); updateRunning() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateRunning() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); updateRunning() }

    private fun updateRunning() {
        // Under reduced motion the scene is drawn once and stays still.
        val still = runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        val should = isAttachedToWindow && windowVisibility == VISIBLE && isShown && !still
        if (should == running) return
        running = should
        removeCallbacks(frame)
        if (running) post(frame)
    }

    private companion object {
        /** Four frames a second: calm, and every move is a whole cell. */
        const val FRAME_MS = 250L
        const val PREVIEW_MS = 10_000L
    }
}
