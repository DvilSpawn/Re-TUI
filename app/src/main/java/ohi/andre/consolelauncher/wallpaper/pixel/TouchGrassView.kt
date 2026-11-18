package ohi.andre.consolelauncher.wallpaper.pixel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import ohi.andre.consolelauncher.managers.settings.MonoMode
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperSettings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Touch grass: a lawn seen from a few inches above. Each blade is a short stroke from its root to its tip; gusts
 * roll through in patches, cloud shadows drift over, and a finger parts the grass, leaving a path that springs back.
 * Tilting the phone slides the tips a little further than the ground, so the lawn sits just below the glass. Left
 * alone for a while, it says so. Drawn at twelve frames a second, like the grass demo by Dylearn that inspired it.
 */
class TouchGrassView(context: Context) : PixelWallpaperView(context, 130f), SensorEventListener {
    companion object {
        const val SCENE = "touch grass"
        const val MEADOW = "meadow"
        const val DUSK = "dusk"
        const val THEME = "theme"
        const val MONO = "mono"
        // Mono is left out until it reads as grass rather than noise.
        val LOOKS = listOf(MEADOW, DUSK, THEME)
        private const val FPS = 12
        /** How far a fully leaning blade reaches, and how far the tallest tips slide with a full tilt, in pixels. */
        private const val LEN = 6f
        private const val PARALLAX = 3.5f
        /** A finger's reach in pixels, and how long a parted path takes to stand up again. */
        private const val REACH = 13
        /**
         * A parted path stays flat for [HOLD] seconds, then stands back up from its tail: each step along it starts
         * [HEAL_STEP] seconds after the one before, and takes [RECOVER] seconds to stand.
         */
        private const val HOLD = 0.35f
        private const val HEAL_STEP = 0.02f
        private const val RECOVER = 0.9f
        private const val WIND = 0.45f
        private const val IDLE_SECONDS = 7f
        private const val MAX_TRAIL = 400
        private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
    }

    var look = RetuiWallpaperSettings.pixelLook(context, SCENE)?.takeIf { it in LOOKS } ?: MEADOW
        set(value) { field = value; colors = null; invalidate() }

    // The lawn, short blades first so the tall ones draw over them.
    private var count = 0
    private var bx = FloatArray(0); private var by = FloatArray(0); private var bh = FloatArray(0)
    private var lx = FloatArray(0); private var ly = FloatArray(0); private var seed = FloatArray(0)
    private var flower = ByteArray(0); private var clover = BooleanArray(0)
    private var ground = ByteArray(0)

    // Per-frame fields over the grid: gust strength, cloud shadow, and how touch pushes and presses the grass.
    private var gust = FloatArray(0); private var shade = BooleanArray(0)
    private var pushX = FloatArray(0); private var pushY = FloatArray(0); private var pressed = FloatArray(0)

    private var clock = 0f
    private var lastTouch = 0f
    private val pointers = HashMap<Int, FloatArray>()
    private val trail = ArrayList<FloatArray>()   // x, y, when it starts to stand up
    private var lastHeal = 0f

    /** Adds a step of path at (x, y), standing up no sooner than the step before it. */
    private fun addTrail(x: Float, y: Float) {
        lastHeal = max(clock + HOLD, lastHeal + HEAL_STEP)
        trail += floatArrayOf(x, y, lastHeal)
    }

    private val reduced = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    // Tilt: the tips follow how the phone is held, measured against a slowly following rest position.
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var listening = false
    private var lastStep = 0L
    private val tilt = FloatArray(2); private val rest = FloatArray(2); private var restSet = false
    private var camX = 0f; private var camY = 0f

    private class Colors(val mono: Boolean, val ground: IntArray, val shadowGround: IntArray, val greens: IntArray,
        val shadowGreens: IntArray, val pressed: IntArray, val flowers: IntArray, val ink: Int, val paper: Int, val text: Int, val box: Int)
    private var colors: Colors? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = runCatching { Typeface.createFromAsset(context.assets, MonoMode.FONT_ASSET) }.getOrDefault(Typeface.MONOSPACE)
        textSize = 30f * context.resources.displayMetrics.density
    }
    private val boxPaint = Paint()

    // ---- noise ----

    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0xFFFFFFFFL) / 4294967295f
    }

    private fun noise(x: Float, y: Float): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val xf = x - xi; val yf = y - yi
        val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf)
        val a = hash(xi, yi); val b = hash(xi + 1, yi); val c = hash(xi, yi + 1); val d = hash(xi + 1, yi + 1)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    private fun bayer(x: Int, y: Int) = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f

    // ---- palettes ----

    private fun palette(): Colors {
        colors?.let { return it }
        fun hex(s: String) = (s.removePrefix("#").toLong(16) or 0xFF000000L).toInt()
        fun scale(c: Int, f: Float) = PixelDither.mix(0xFF000000.toInt(), c, f)
        fun make(ground: List<Int>, greens: List<Int>, flowers: List<Int>, text: Int): Colors {
            val g = greens.toIntArray()
            return Colors(false, ground.toIntArray(), ground.map { scale(it, 0.7f) }.toIntArray(), g,
                g.map { scale(it, 0.68f) }.toIntArray(), g.map { scale(it, 0.82f) }.toIntArray(), flowers.toIntArray(),
                0, 0, text, 0xD9000000.toInt())
        }
        val made = when (look) {
            DUSK -> make(listOf(hex("#0d1117"), hex("#11161d"), hex("#151b23")),
                listOf(hex("#0f2620"), hex("#14362b"), hex("#1d4a39"), hex("#2a644b"), hex("#468a66")),
                listOf(hex("#cfd8ff"), hex("#ffd27a"), hex("#b8a2ff")), hex("#cfd8ff"))
            THEME -> {
                // Black earth and grass in the launcher's own accent, from deep to bright.
                val ink = PixelLook.themeInk()
                val black = 0xFF000000.toInt()
                make(listOf(0.05f, 0.08f, 0.11f).map { PixelDither.mix(black, ink, it) },
                    listOf(0.2f, 0.32f, 0.46f, 0.64f, 0.88f).map { PixelDither.mix(black, ink, it) },
                    listOf(PixelDither.mix(ink, 0xFFFFFFFF.toInt(), 0.75f), 0xFFFFFFFF.toInt(), PixelDither.mix(ink, 0xFFFFFFFF.toInt(), 0.4f)),
                    ink or 0xFF000000.toInt())
            }
            MONO -> {
                val ink = hex("#1e1e1c"); val paper = hex("#e3e1da")
                Colors(true, IntArray(0), IntArray(0), IntArray(0), IntArray(0), IntArray(0), IntArray(0), ink, paper, ink, 0xD9E3E1DA.toInt())
            }
            else -> make(listOf(hex("#2a2317"), hex("#33291b"), hex("#3b3020")),
                listOf(hex("#1d3a1a"), hex("#2b5521"), hex("#3c732a"), hex("#579533"), hex("#86bd4e")),
                listOf(hex("#f3ecd2"), hex("#f1c84b"), hex("#d97aa7")), hex("#f3ecd2"))
        }
        colors = made
        return made
    }

    // ---- the lawn ----

    override fun onGrid(width: Int, height: Int) {
        val rand = Random(1207)
        val margin = LEN.toInt()
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>(); val hs = ArrayList<Float>()
        val lxs = ArrayList<Float>(); val lys = ArrayList<Float>(); val seeds = ArrayList<Float>()
        val flowers = ArrayList<Byte>(); val clovers = ArrayList<Boolean>()
        for (y in -margin until height + margin) for (x in -margin until width + margin) {
            if (rand.nextFloat() > 0.42f) continue
            val tall = rand.nextFloat() < 0.025f
            val angle = rand.nextFloat() * 6.2831855f
            val lean = 0.18f + rand.nextFloat() * 0.42f
            xs += x + rand.nextFloat(); ys += y + rand.nextFloat()
            hs += if (tall) 1.25f else 0.35f + 0.65f * rand.nextFloat().pow(0.7f)
            lxs += cos(angle) * lean; lys += sin(angle) * lean
            seeds += rand.nextFloat() * 10f
            flowers += if (rand.nextFloat() < 0.007f) (1 + rand.nextInt(3)).toByte() else 0
            clovers += rand.nextFloat() < 0.004f
        }
        val order = hs.indices.sortedBy { hs[it] }
        count = order.size
        bx = FloatArray(count) { xs[order[it]] }; by = FloatArray(count) { ys[order[it]] }; bh = FloatArray(count) { hs[order[it]] }
        lx = FloatArray(count) { lxs[order[it]] }; ly = FloatArray(count) { lys[order[it]] }; seed = FloatArray(count) { seeds[order[it]] }
        flower = ByteArray(count) { flowers[order[it]] }; clover = BooleanArray(count) { clovers[order[it]] }
        ground = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val n = noise(x * 0.18f, y * 0.18f) * 0.7f + noise(x * 0.9f, y * 0.9f) * 0.3f
            ground[y * width + x] = if (n < 0.42f) 0 else if (n < 0.62f) 1 else 2
        }
        val cells = width * height
        gust = FloatArray(cells); shade = BooleanArray(cells)
        pushX = FloatArray(cells); pushY = FloatArray(cells); pressed = FloatArray(cells)
    }

    override fun step(seconds: Float) {
        clock += seconds
        lastStep = SystemClock.uptimeMillis()
        if (!listening && gravity != null && !reduced) {
            listening = sensors?.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI) == true
        }
        // With no tilt reading, a slow drift stands in for a hand that is never quite still.
        val targetX: Float; val targetY: Float
        if (reduced) { targetX = 0f; targetY = 0f }
        else if (listening && restSet) {
            // Tilting the phone one way leans the tips the other way.
            targetX = (-(tilt[0] - rest[0]) / 2.5f).coerceIn(-1f, 1f)
            targetY = ((tilt[1] - rest[1]) / 2.5f).coerceIn(-1f, 1f)
        } else { targetX = sin(clock / 3.1f) * 0.45f; targetY = cos(clock / 4.3f) * 0.35f }
        val ease = min(1f, seconds * 6f)
        camX += (targetX - camX) * ease
        camY += (targetY - camY) * ease
        trail.removeAll { clock - it[2] >= RECOVER }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Nobody is drawing any more (the wallpaper is hidden): stop listening until the next frame.
        if (SystemClock.uptimeMillis() - lastStep > 2000) { stopListening(); return }
        val a = 0.25f
        tilt[0] += (event.values[0] - tilt[0]) * a
        tilt[1] += (event.values[1] - tilt[1]) * a
        if (!restSet) { rest[0] = event.values[0]; rest[1] = event.values[1]; tilt[0] = rest[0]; tilt[1] = rest[1]; restSet = true }
        rest[0] += (tilt[0] - rest[0]) * 0.02f
        rest[1] += (tilt[1] - rest[1]) * 0.02f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun stopListening() {
        if (listening) sensors?.unregisterListener(this)
        listening = false
        restSet = false
    }

    override fun release() {
        stopListening()
        super.release()
    }

    // ---- touch ----

    override fun handleTouch(event: MotionEvent) {
        val now = clock
        fun gridX(i: Int) = (event.getX(i) - screenX(0f)) / cell
        fun gridY(i: Int) = (event.getY(i) - screenY(0f)) / cell
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                pointers[event.getPointerId(i)] = floatArrayOf(gridX(i), gridY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                val prev = pointers[id] ?: continue
                val x = gridX(i); val y = gridY(i)
                // A point every couple of pixels behind the finger, so a quick swipe still leaves a full path.
                val steps = (hypot(x - prev[0], y - prev[1]) / 2f).toInt()
                for (s in 1..steps) addTrail(prev[0] + (x - prev[0]) * s / steps, prev[1] + (y - prev[1]) * s / steps)
                while (trail.size > MAX_TRAIL) trail.removeAt(0)
                prev[0] = x; prev[1] = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = event.getPointerId(event.actionIndex)
                pointers.remove(id)?.let { addTrail(it[0], it[1]) }
                if (event.actionMasked != MotionEvent.ACTION_POINTER_UP) pointers.clear()
            }
        }
        lastTouch = now
    }

    /** Adds a finger at (x, y), [life] strong, to the push and press fields. */
    private fun stamp(x: Float, y: Float, life: Float) {
        if (life <= 0f) return
        val cx = x.roundToInt(); val cy = y.roundToInt()
        for (gy in max(0, cy - REACH)..min(gh - 1, cy + REACH)) for (gx in max(0, cx - REACH)..min(gw - 1, cx + REACH)) {
            val dx = gx - x; val dy = gy - y
            val d = hypot(dx, dy)
            if (d >= REACH) continue
            val s = (1f - d / REACH).pow(1.5f) * life
            val i = gy * gw + gx
            // Each blade follows its strongest push only, so every step of a path stands up on its own time.
            if (s > pressed[i]) {
                pressed[i] = s
                if (d > 0.001f) { pushX[i] = dx / d * s * 1.7f; pushY[i] = dy / d * s * 1.7f }
            }
        }
    }

    // ---- drawing ----

    private fun cell(x: Float, y: Float): Int = (y.toInt().coerceIn(0, gh - 1)) * gw + x.toInt().coerceIn(0, gw - 1)

    private fun put(x: Float, y: Float, color: Int) {
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        if (xi < 0 || yi < 0 || xi >= gw || yi >= gh) return
        px[yi * gw + xi] = color
    }

    override fun render() {
        if (count == 0 || gust.size != gw * gh) return
        val p = palette()
        val t = (clock * FPS).roundToInt() / FPS.toFloat()

        // Gusts: two noise fields drifting at slightly different angles, multiplied, so they roll through in patches.
        for (y in 0 until gh) for (x in 0 until gw) {
            val i = y * gw + x
            gust[i] = if (reduced) 0.12f else {
                val n1 = noise(x * 0.05f - t * 0.9f, y * 0.05f - t * 0.25f)
                val n2 = noise(x * 0.11f - t * 1.3f + 40f, y * 0.11f + t * 0.35f)
                (n1 * n2 * 2.4f - 0.25f).coerceIn(0f, 1f)
            }
            val c = noise(x * 0.025f + t * 0.012f, y * 0.025f + t * 0.005f) * 0.75f + noise(x * 0.07f - t * 0.01f, y * 0.07f) * 0.25f
            shade[i] = c > 0.58f + (bayer(x, y) - 0.5f) * 0.08f
        }
        pushX.fill(0f); pushY.fill(0f); pressed.fill(0f)
        pointers.values.forEach { stamp(it[0], it[1], 1f) }
        for (point in trail) {
            val age = ((clock - point[2]) / RECOVER).coerceIn(0f, 1f)
            stamp(point[0], point[1], 1f - age * age * (3 - 2 * age))
        }

        // Earth, dappled, under the clouds' shadows.
        for (y in 0 until gh) for (x in 0 until gw) {
            val i = y * gw + x
            val g = ground[i].toInt()
            px[i] = if (p.mono) (if (bayer(x, y) < 0.06f + g * 0.05f + (if (shade[i]) 0.18f else 0f)) p.ink else p.paper)
                else (if (shade[i]) p.shadowGround else p.ground)[g]
        }

        for (b in 0 until count) {
            val root = cell(bx[b], by[b])
            val s = (0.1f + gust[root] * 1.1f) * WIND + (if (reduced) 0f else sin((t + seed[b]) * 2.4f) * 0.06f * WIND)
            var ox = lx[b] + s * 0.92f + pushX[root]
            var oy = ly[b] + s * 0.38f + pushY[root]
            val m = hypot(ox, oy)
            if (m > 1.9f) { ox *= 1.9f / m; oy *= 1.9f / m }
            // The tips are nearer the phone than the ground, so they slide further when it tilts.
            val h = bh[b]
            val reach = h * LEN
            val ex = bx[b] + ox * reach - camX * PARALLAX * h
            val ey = by[b] + oy * reach - camY * PARALLAX * h
            val sx = bx[b] - camX * PARALLAX * 0.15f
            val sy = by[b] - camY * PARALLAX * 0.15f
            val steps = max(1, ceil(max(abs(ex - sx), abs(ey - sy))).toInt())
            val pressedHere = pressed[root] > 0.35f
            val shadowed = shade[root]
            for (k in 0..steps) {
                val f = k.toFloat() / steps
                val x = sx + (ex - sx) * f; val y = sy + (ey - sy) * f
                // Lighter toward the tip and on taller blades; pressed grass shows its darker underside.
                val level = min(4, ((0.25f + f * 0.75f) * h * 4.6f).toInt())
                if (p.mono) {
                    val v = 0.95f - level * 0.13f + (if (pressedHere) 0.1f else 0f) + (if (shadowed) 0.08f else 0f)
                    put(x, y, if (bayer(floor(x).toInt(), floor(y).toInt()) < v) p.ink else p.paper)
                } else {
                    put(x, y, (if (pressedHere) p.pressed else if (shadowed) p.shadowGreens else p.greens)[level])
                }
            }
            if (flower[b] > 0) {
                val c = if (p.mono) p.paper else p.flowers[flower[b] - 1]
                put(ex, ey, c); put(ex + 1, ey, c); put(ex, ey + 1, c)
                put(ex + 1, ey + 1, if (p.mono) c else PixelDither.mix(0xFF000000.toInt(), c, 0.8f))
                if (p.mono) { put(ex - 1, ey, p.ink); put(ex + 2, ey, p.ink) }
            }
            if (clover[b]) {
                val c = if (p.mono) p.ink else p.greens[3]
                put(ex - 1, ey, c); put(ex + 1, ey, c); put(ex, ey - 1, c); put(ex, ey + 1, c)
            }
        }
    }

    /** After a while untouched, the lawn asks. */
    override fun drawOverlay(canvas: Canvas) {
        val shown = ((clock - lastTouch - IDLE_SECONDS) / 1.5f).coerceIn(0f, 1f)
        if (shown <= 0f) return
        val p = palette()
        val size = textPaint.textSize
        val cursor = if (((clock * 1000) / 530).toInt() % 2 == 1) "_" else " "
        val width = textPaint.measureText("> touch grass_")
        val x = (this.width - width) / 2f
        val y = height * 0.82f
        boxPaint.color = p.box
        boxPaint.alpha = (shown * (p.box ushr 24)).toInt()
        canvas.drawRect(x - size * 0.4f, y - size * 0.85f, x + width + size * 0.4f, y + size * 0.35f, boxPaint)
        textPaint.color = p.text
        textPaint.alpha = (shown * 255).toInt()
        canvas.drawText("> touch grass$cursor", x, y, textPaint)
    }

    override fun frameDelayMs(): Long = if (reduced && pointers.isEmpty() && trail.isEmpty()) 1000L else 1000L / FPS

    override fun loadSettings() { look = RetuiWallpaperSettings.pixelLook(context, SCENE)?.takeIf { it in LOOKS } ?: MEADOW }

    override fun signatureColors(): IntArray {
        val p = palette()
        return if (p.mono) intArrayOf(p.paper, 0xFF808080.toInt(), p.ink) else intArrayOf(p.ground[0], p.greens[2], p.greens[4])
    }
}
