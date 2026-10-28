package ohi.andre.consolelauncher.wallpaper.astro

import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.dither
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.mix
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.rgb
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.shade
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** A painted ship sprite, nose to the right. 0 is an empty pixel. */
internal class ShipSprite(val w: Int, val h: Int) {
    val px = IntArray(w * h)
    val lights = ArrayList<IntArray>()    // x, y, kind
    val nozzles = ArrayList<IntArray>()   // x, y, radius, exhaust
    internal var rng = Random(0)

    fun set(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) px[y * w + x] = c }
    fun get(x: Int, y: Int): Int = if (x in 0 until w && y in 0 until h) px[y * w + x] else 0
    fun filled(x: Int, y: Int) = get(x, y) != 0
    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) { for (y in y0 until y1) for (x in x0 until x1) set(x, y, c) }
    fun hline(x0: Int, x1: Int, y: Int, c: Int) { for (x in x0 until x1) set(x, y, c) }
    fun vline(x: Int, y0: Int, y1: Int, c: Int) { for (y in y0 until y1) set(x, y, c) }

    /** Scanline fill sampled at pixel centres. */
    private fun fill(pts: FloatArray, colour: (Int, Int) -> Int) {
        val n = pts.size / 2
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until n) { minY = min(minY, pts[2 * i + 1]); maxY = max(maxY, pts[2 * i + 1]) }
        val xs = FloatArray(n)
        for (y in minY.toInt()..ceil(maxY).toInt()) {
            val cy = y + 0.5f
            var count = 0
            for (i in 0 until n) {
                val x0 = pts[2 * i]; val y0 = pts[2 * i + 1]
                val j = (i + 1) % n
                val x1 = pts[2 * j]; val y1 = pts[2 * j + 1]
                if ((y0 <= cy && cy < y1) || (y1 <= cy && cy < y0)) xs[count++] = x0 + (cy - y0) * (x1 - x0) / (y1 - y0)
            }
            xs.sort(0, count)
            var k = 0
            while (k + 1 < count) {
                for (x in ceil(xs[k] - 0.5f).toInt() until ceil(xs[k + 1] - 0.5f).toInt()) set(x, y, colour(x, y))
                k += 2
            }
        }
    }
    fun poly(vararg pts: Float, colour: (Int, Int) -> Int) = fill(pts, colour)

    fun tone(r: IntArray, v: Float, x: Int, y: Int): Int =
        r[(v + (dither(x, y) - 0.53125f) * 0.35f).roundToInt().coerceIn(0, 4)]

    /** A hull block: lit top deck, side face darkening toward the keel, chamfered ends. */
    fun box(x0: Int, y0: Int, x1: Int, y1: Int, r: IntArray, top: Int = 3, front: Int = 0, back: Int = 0, base: Float = 1.6f, fall: Float = 1.4f) {
        val h = y1 - y0
        poly((x0 + back).toFloat(), y0.toFloat(), (x1 - front).toFloat(), y0.toFloat(), x1.toFloat(), (y0 + top).toFloat(),
            x1.toFloat(), y1.toFloat(), x0.toFloat(), y1.toFloat(), x0.toFloat(), (y0 + top).toFloat()) { x, y ->
            tone(r, base + fall * (y - y0 - top).toFloat() / max(1, h - top), x, y)
        }
        poly((x0 + back).toFloat(), y0.toFloat(), (x1 - front).toFloat(), y0.toFloat(), x1.toFloat(), (y0 + top).toFloat(),
            x0.toFloat(), (y0 + top).toFloat()) { x, y -> tone(r, 0.8f, x, y) }
        hline(x0 + back, x1 - front, y0, r[0])   // sunlit edge
        hline(x0, x1, y0 + top, r[3])            // crease between deck and side
        hline(x0, x1, y1 - 1, r[4])              // keel shadow
    }

    fun cylinder(x0: Int, y0: Int, x1: Int, y1: Int, r: IntArray) {
        val h = y1 - y0
        for (y in y0 until y1) {
            val t = (y - y0 + 0.5f) / h
            val v = 2.6f - 2.2f * sin(t * PI.toFloat()) + if (t > 0.5f) 0.9f else 0f  // bright band above centre
            for (x in x0 until x1) set(x, y, tone(r, v, x, y))
        }
    }

    fun panelLines(x0: Int, y0: Int, x1: Int, y1: Int, r: IntArray, step: Int, jitter: Int = 3) {
        var x = x0 + rng.nextInt(2, step + 1)
        while (x < x1 - 1) {
            for (y in y0 until y1) if (filled(x, y)) {
                set(x, y, r[4])
                if (filled(x + 1, y)) set(x + 1, y, mix(get(x + 1, y), r[1], 0.5f))
            }
            x += step + rng.nextInt(-jitter, jitter + 1)
        }
    }

    fun rivets(x0: Int, x1: Int, y: Int, r: IntArray, step: Int = 4) { for (x in x0 until x1 step step) if (filled(x, y)) set(x, y, r[0]) }

    fun hazard(x0: Int, y0: Int, x1: Int, y1: Int, phase: Int = 0) {
        for (y in y0 until y1) for (x in x0 until x1) if (filled(x, y)) set(x, y, if ((x + y + phase) % 4 < 2) Freighters.YELLOW[1] else Freighters.BLACK[3])
    }

    fun vent(x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in y0 until y1) for (x in x0 until x1) set(x, y, if (x % 2 == 0) Freighters.BLACK[4] else Freighters.BLACK[2])
        hline(x0, x1, y0, Freighters.BLACK[1])
    }

    fun pipe(x0: Int, x1: Int, y: Int, r: IntArray = Freighters.STEEL) {
        hline(x0, x1, y, r[1]); hline(x0, x1, y + 1, r[3])
        for (x in x0 + 2 until x1 - 1 step 7) { set(x, y, r[4]); set(x, y + 1, r[4]) }
    }

    /** A bevelled plate: shadow along one pair of edges, light along the other. */
    fun inset(x0: Int, y0: Int, x1: Int, y1: Int, r: IntArray) {
        hline(x0, x1, y0, r[3]); vline(x0, y0, y1, r[3])
        hline(x0, x1 + 1, y1, r[1]); vline(x1, y0, y1, r[1])
    }

    /** Scatter small machined details: hatches, vents, plates, bolt heads. */
    fun greebles(x0: Int, y0: Int, x1: Int, y1: Int, r: IntArray, count: Int) {
        repeat(count) {
            val w = rng.nextInt(4, 10); val h = rng.nextInt(3, 6)
            val x = rng.nextInt(x0, max(x0, x1 - w) + 1); val y = rng.nextInt(y0, max(y0, y1 - h) + 1)
            if (!(filled(x, y) && filled(x + w, y) && filled(x, y + h) && filled(x + w, y + h))) return@repeat
            val kind = rng.nextFloat()
            when {
                kind < 0.35f -> inset(x, y, x + w, y + h, r)
                kind < 0.55f -> vent(x, y, x + w, y + h - 1)
                kind < 0.75f -> { rect(x, y, x + w, y + 1, r[1]); rect(x, y + 1, x + w, y + 2, r[3]) }
                kind < 0.96f -> { set(x, y, Freighters.STEEL[1]); set(x + 1, y, Freighters.STEEL[3]) }
                else -> rect(x, y, x + 2, y + 2, if (rng.nextBoolean()) Freighters.YELLOW[1] else rgb("#c83a2a"))
            }
        }
    }

    fun windowStrip(x0: Int, x1: Int, y: Int, lit: Float = 0.8f) {
        for (x in x0 until x1) {
            val on = rng.nextFloat() < lit
            set(x, y, if (on && x % 3 != 0) Freighters.GLASS[1] else if (on) Freighters.GLASS[3] else Freighters.BLACK[3])
            set(x, y + 1, if (on && x % 3 != 0) Freighters.GLASS[2] else Freighters.BLACK[4])
        }
    }

    /** Wear: soot streaks swept back from the bow, stains pooling low, chipped edges and scratches. */
    fun grime(amount: Float, scratches: Int) {
        val seed = rng.nextInt(1 shl 20).toLong()
        fun hash(a: Int, b: Int) = Math.floorMod((a * 73856093L) xor (b * 19349663L) xor seed, 1000L) / 1000f
        fun blot(x: Int, y: Int, sx: Float, sy: Float): Float {
            val fx = x / sx; val fy = y / sy
            val ix = fx.toInt(); val iy = fy.toInt()
            val tx = fx - ix; val ty = fy - iy
            val top = hash(ix, iy) * (1 - tx) + hash(ix + 1, iy) * tx
            val bot = hash(ix, iy + 1) * (1 - tx) + hash(ix + 1, iy + 1) * tx
            return top * (1 - ty) + bot * ty
        }
        val soot = rgb(12, 10, 9); val chip = rgb(255, 250, 240)
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            if (c == 0) continue
            val streak = blot(x, y, 14f, 2.5f)       // long horizontal soot streaks
            val stain = blot(x + 50, y, 5f, 5f)      // patchy stains
            if (streak > 1 - amount * 1.6f || stain < amount * (0.3f + y.toFloat() / h)) px[y * w + x] = mix(c, soot, 0.3f)
            else if (!filled(x, y - 1) && rng.nextFloat() < amount * 0.8f) px[y * w + x] = mix(c, chip, 0.35f)
        }
        val white = rgb(255, 255, 255)
        repeat(scratches) {
            val x = rng.nextInt(w); val y = rng.nextInt(h)
            for (k in 0 until rng.nextInt(2, 5)) if (filled(x + k, y)) px[y * w + x + k] = mix(px[y * w + x + k], white, 0.22f)
        }
    }

    fun outline() {
        val edge = ArrayList<Int>()
        for (y in 0 until h) for (x in 0 until w) {
            if (px[y * w + x] == 0 && (filled(x + 1, y) || filled(x - 1, y) || filled(x, y + 1) || filled(x, y - 1))) edge += y * w + x
        }
        for (i in edge) px[i] = Freighters.OUTLINE
    }

    fun light(x: Int, y: Int, kind: Int) {
        lights += intArrayOf(x, y, kind)
        set(x, y, Freighters.LIGHTS[kind])
    }

    fun nozzle(x: Int, y: Int, radius: Int, exhaust: Int) { nozzles += intArrayOf(x, y, radius, exhaust) }

    /** Shrink by any factor <= 1: each output pixel takes the most common colour of the block it covers. */
    fun scaled(f: Float): ShipSprite {
        if (f >= 0.999f) return this
        val out = ShipSprite(max(1, (w * f).roundToInt()), max(1, (h * f).roundToInt()))
        val counts = HashMap<Int, Int>()
        for (y in 0 until out.h) {
            val y0 = (y / f).toInt(); val y1 = max(y0 + 1, ((y + 1) / f).toInt())
            for (x in 0 until out.w) {
                val x0 = (x / f).toInt(); val x1 = max(x0 + 1, ((x + 1) / f).toInt())
                counts.clear()
                var cells = 0; var solid = 0
                for (sy in y0 until y1) for (sx in x0 until x1) {
                    cells++
                    val c = get(sx, sy)
                    if (c != 0) { solid++; counts[c] = (counts[c] ?: 0) + 1 }
                }
                if (solid > 0 && solid * 2 >= cells) out.px[y * out.w + x] = counts.maxByOrNull { it.value }!!.key
            }
        }
        lights.mapTo(HashSet()) { listOf((it[0] * f).toInt(), (it[1] * f).toInt(), it[2]) }.forEach { out.lights += it.toIntArray() }
        nozzles.forEach { out.nozzles += intArrayOf((it[0] * f).toInt(), (it[1] * f).toInt(), max(1, (it[2] * f).roundToInt()), it[3]) }
        return out
    }
}

/** Industrial ship designs: worn hulls, hazard trim, plumbing and running lights. Every seed varies paint, length and modules. */
internal object Freighters {
    private fun ramp(vararg hex: String) = hex.map(::rgb).toIntArray()
    val PAINTS = mapOf(
        "gunmetal" to ramp("#a8aeb8", "#737a85", "#4c525c", "#33373f", "#1d2026"),
        "charcoal" to ramp("#8a8e96", "#5c6068", "#3e4148", "#2a2c32", "#17181c"),
        "navy" to ramp("#9aa8c0", "#62708c", "#414d66", "#2a3346", "#161b27"),
        "olive" to ramp("#b4b48e", "#80805e", "#5a5a40", "#3a3a2a", "#1e1e16"),
        "tan" to ramp("#f0dfb4", "#d2bb8c", "#ae9468", "#776348", "#3f3426"),
        "bone" to ramp("#f2efe6", "#cfcac0", "#a29c92", "#6c6760", "#36332f"),
        "rust" to ramp("#d89464", "#a8643c", "#7a4428", "#4e2a1a", "#28150d"),
        "orange" to ramp("#ffc070", "#e88a30", "#b0601c", "#6e3810", "#331a08"),
    )
    val YELLOW = ramp("#ffe070", "#f2c230", "#c8920e", "#7a560a", "#3a2806")
    val BLACK = ramp("#5a5c62", "#3a3c42", "#26272c", "#18191d", "#0c0c0f")
    val STEEL = ramp("#e4e8ee", "#b4bac4", "#848a96", "#545a64", "#2c3036")
    val GLASS = ramp("#ffe8b0", "#ffc070", "#c07a3a", "#5a3420", "#24140e")
    val OUTLINE = rgb("#07070a")
    const val RED = 0; const val AMBER = 1; const val WHITE = 2; const val GREEN = 3
    val LIGHTS = ramp("#ff3a2a", "#ffb040", "#ffffff", "#5aff8a")
    const val ION = 0; const val FUSION = 1; const val PLASMA = 2
    val EXHAUST_CORE = ramp("#e0ffff", "#fff4c0", "#ffe0ff")
    val EXHAUST_TRAIL = ramp("#5ad8ff", "#ffa040", "#ff5ad0")
    val WEIGHTS = intArrayOf(3, 3, 3, 3, 2)

    private fun <T> Random.pick(vararg items: T) = items[nextInt(items.size)]
    private fun sprite(w: Int, h: Int, seed: Int) = ShipSprite(w, h).also { it.rng = Random(seed) }

    fun build(design: Int, seed: Int): ShipSprite = when (design) {
        0 -> mule(seed); 1 -> prospector(seed); 2 -> train(seed); 3 -> tanker(seed); else -> workboat(seed)
    }

    /** Heavy hauler: long armoured hull, deck plumbing, nacelles with yellow-rimmed intakes, a cab up front. */
    private fun mule(seed: Int): ShipSprite {
        val rng = Random(seed)
        val paint = PAINTS.getValue(rng.pick("gunmetal", "charcoal", "navy", "olive"))
        val l = rng.nextInt(92, 105)
        val s = sprite(l + 4, 44, seed)
        val top = 12; val keel = 34
        val nx0 = (l * 0.56f).toInt()  // far nacelle peeking above the deck
        s.box(nx0, 4, nx0 + 26, 13, BLACK, top = 2, front = 2)
        s.rect(nx0 + 24, 5, nx0 + 27, 13, YELLOW[2]); s.vline(nx0 + 26, 5, 13, YELLOW[1])
        s.poly(10f, top + 1f, 15f, 1f, 21f, 1f, 22f, top + 1f) { x, y -> s.tone(paint, 1.5f + (x - 10) / 12f, x, y) }  // tail fin
        s.rect(15, 1, 21, 4, YELLOW[1]); s.hline(15, 21, 1, YELLOW[0])
        s.poly(1f, top + 6f, 12f, top + 3f, 14f, top + 7f, 2f, top + 8f) { x, y -> s.tone(paint, 2.2f, x, y) }  // stabiliser
        s.rect(1, top + 6, 4, top + 8, YELLOW[1])
        s.box(6, top, l - 14, keel, paint, top = 4, back = 6)  // main hull, nose sloping down into the cab
        s.poly(l - 16f, top + 4f, l - 6f, top + 9f, l.toFloat(), top + 16f, l.toFloat(), keel - 2f, l - 4f, keel.toFloat(), l - 16f, keel.toFloat()) { x, y ->
            s.tone(paint, 1.4f + (y - top) / 14f, x, y)
        }
        s.poly(l - 16f, top.toFloat(), l - 14f, top.toFloat(), l - 4f, top + 7f, l - 6f, top + 9f, l - 16f, top + 4f) { x, y -> s.tone(paint, 0.7f, x, y) }
        for (x in l - 13 until l - 3) for (y in top + 8 + (x - (l - 13)) / 3 until top + 12) s.set(x, y, if ((x + y) % 5 != 0) GLASS[2] else GLASS[1])
        s.hline(l - 13, l - 4, top + 12, BLACK[4])
        s.vent(l - 9, keel - 7, l - 3, keel - 4)
        s.light(l - 2, keel - 9, WHITE); s.light(l - 2, keel - 3, WHITE)
        s.rect(18, keel, l - 18, keel + 3, BLACK[3]); s.hline(18, l - 18, keel + 2, BLACK[4])  // underslung machinery
        for (x in 22 until l - 22 step 9) s.rect(x, keel, x + 4, keel + 4, BLACK[2])
        s.pipe(14, l - 22, top + 1)  // deck plumbing, rails, a hatch and marker lights
        s.hline(26, (l * 0.5f).toInt(), top - 2, BLACK[1])
        for (x in 26 until (l * 0.5f).toInt() step 8) s.vline(x, top - 2, top, BLACK[2])
        val hx = (l * 0.32f).toInt()
        s.box(hx, top - 3, hx + 12, top + 1, paint, top = 1)
        s.rect(hx + 3, top - 2, hx + 9, top, BLACK[3])
        for (x in (l * 0.4f).toInt() until l - 22 step 6) s.light(x, top + 3, RED)
        s.panelLines(8, top + 5, l - 16, keel - 1, paint, rng.nextInt(9, 14))
        val cx = (l * 0.66f).toInt(); val cy = top + 12; val r = 6  // turbine ring on the flank
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            val d = hypot((x - cx).toFloat(), (y - cy).toFloat())
            if (d > r) continue
            s.set(x, y, when {
                d > r - 1.5f -> if (y < cy) BLACK[1] else BLACK[3]
                d > r - 3 -> if (Math.floorMod(floor(atan2((y - cy).toFloat(), (x - cx).toFloat()) * 6).toInt(), 2) == 1) STEEL[2] else STEEL[3]
                else -> if (d > 1.5f) BLACK[4] else STEEL[1]
            })
        }
        s.vent(14, top + 8, 22, top + 14)
        s.inset(24, top + 6, (l * 0.3f).toInt(), top + 15, paint)
        s.inset((l * 0.5f).toInt(), top + 6, (l * 0.58f).toInt(), top + 18, paint)
        s.greebles(8, top + 5, l - 18, keel - 2, paint, 11)
        s.greebles(14, top - 1, l - 24, top + 3, paint, 4)
        s.hazard((l * 0.74f).toInt(), top + 5, (l * 0.74f).toInt() + 6, top + 8, 1)
        s.rect((l * 0.52f).toInt(), top + 7, (l * 0.52f).toInt() + 7, top + 9, STEEL[1])  // a pale ID plate
        s.rivets(8, l - 16, top + 6, paint, 5)
        val nx = (l * 0.18f).toInt(); val fx = nx + (l * 0.36f).toInt()  // near nacelle hung on the flank
        s.cylinder(nx - 6, keel - 6, nx + 2, keel + 4, STEEL)
        s.box(nx, keel - 9, fx, keel + 6, paint, top = 2, front = 1)
        s.panelLines(nx + 2, keel - 6, fx - 2, keel + 5, paint, 7)
        s.greebles(nx + 2, keel - 6, fx - 3, keel + 4, paint, 4)
        s.hline(nx, fx, keel - 10, paint[4])
        s.rect(fx - 1, keel - 9, fx + 4, keel + 6, YELLOW[2])
        s.vline(fx + 3, keel - 9, keel + 6, YELLOW[0]); s.vline(fx - 1, keel - 9, keel + 6, YELLOW[3])
        s.rect(fx, keel - 7, fx + 3, keel + 4, BLACK[4])
        s.light(nx + 6, keel - 4, AMBER)
        s.nozzle(nx - 7, keel - 1, 3, ION); s.nozzle(nx0 - 1, 8, 2, ION)
        s.grime(0.16f, 14)
        s.outline()
        return s
    }

    /** Mining barge: armoured sand hull, cab on top, twin forward rails with black machinery between. */
    private fun prospector(seed: Int): ShipSprite {
        val rng = Random(seed)
        val paint = PAINTS.getValue(rng.pick("tan", "bone", "orange", "tan"))
        val l = rng.nextInt(90, 101)
        val s = sprite(l + 4, 46, seed)
        val body = (l * 0.58f).toInt()
        s.box(body - 12, 10, l - 4, 17, paint, top = 2, front = 3)  // upper rail, behind the body
        for (x in body - 4 until l - 10 step 4) { s.rect(x, 17, x + 2, 21, BLACK[2]); s.set(x, 20, BLACK[0]) }
        s.hline(body - 6, l - 8, 17, BLACK[4])
        s.box(4, 8, body, 30, paint, top = 5, back = 5, front = 3)
        s.box(body - 8, 14, body + 6, 30, paint, top = 3, front = 3)
        val cx = (body * 0.42f).toInt()  // cab block with a tinted canopy
        s.box(cx, 2, cx + 18, 10, paint, top = 2, back = 2, front = 4)
        s.rect(cx + 3, 5, cx + 15, 8, BLACK[4])
        for (x in cx + 4 until cx + 14) s.set(x, 6, if (x % 3 != 0) GLASS[3] else GLASS[2])
        s.rect(8, 30, body + 2, 36, BLACK[3])  // machinery belly and engine block
        for (x in 10 until body step 5) { s.rect(x, 30, x + 3, 37, BLACK[2]); s.set(x + 1, 31, BLACK[0]) }
        s.cylinder(0, 14, 7, 26, BLACK)
        s.rect(0, 16, 2, 24, BLACK[4])
        s.box(body - 16, 32, l, 40, paint, top = 2, front = 3)  // lower rail with a cable run
        s.rect(l - 6, 32, l, 40, BLACK[3]); s.vline(l - 1, 33, 39, BLACK[1])
        s.panelLines(body - 14, 34, l - 7, 39, paint, 8)
        for (x in body - 10 until l - 10 step 3) s.set(x, 31, if (x % 2 != 0) BLACK[2] else STEEL[3])
        s.hline(body - 14, l - 8, 35, BLACK[4])
        s.panelLines(6, 13, body - 2, 29, paint, rng.nextInt(8, 13))
        s.vent(10, 15, 18, 21)
        s.inset(20, 14, (body * 0.55f).toInt(), 27, paint)
        s.greebles(6, 13, body - 2, 29, paint, 8)
        s.greebles(body - 14, 33, l - 8, 39, paint, 3)
        val mx = (body * 0.62f).toInt()
        for (k in 0..2) s.rect(mx + k * 3, 22, mx + k * 3 + 2, 23, BLACK[4])  // stencilled marks
        s.rect(mx, 25, mx + 8, 26, BLACK[3])
        s.hazard(body - 6, 26, body + 4, 29)
        s.rivets(6, body, 14, paint, 4)
        s.light(cx + 17, 4, RED); s.light(l - 3, 12, AMBER); s.light(l - 3, 34, AMBER); s.light(6, 10, GREEN)
        s.nozzle(-1, 20, 4, FUSION)
        s.grime(0.2f, 16)
        s.outline()
        return s
    }

    /** A tug pushing a string of beat-up containers on a spine. */
    private fun train(seed: Int): ShipSprite {
        val rng = Random(seed)
        val n = rng.nextInt(4, 7)
        val cw = 15
        val l = 18 + n * (cw + 1) + 16
        val s = sprite(l + 4, 30, seed)
        s.rect(14, 18, l - 14, 20, STEEL[3]); s.hline(14, l - 14, 18, STEEL[2])
        var x = 16
        for (i in 0 until n) {
            val paint = PAINTS.getValue(rng.pick("rust", "navy", "olive", "bone", "orange", "rust"))
            val y0 = 18 - rng.pick(12, 12, 14) + 4
            s.box(x, y0, x + cw, 22, paint, top = 2)
            for (k in x + 2 until x + cw - 1 step 2) s.vline(k, y0 + 3, 21, paint[3])  // corrugation
            s.inset(x + 1, y0 + 3, x + cw - 2, 20, paint)
            if (rng.nextFloat() < 0.4f) s.rect(x + 3, y0 + 5, x + cw - 3, y0 + 7, mix(paint[1], rgb(255, 255, 255), 0.25f))  // faded logo
            if (rng.nextFloat() < 0.3f) s.hazard(x + cw - 4, y0 + 3, x + cw - 1, 21, i)
            x += cw + 1
        }
        val tug = if (rng.nextBoolean()) PAINTS.getValue("orange") else YELLOW
        s.box(x, 8, x + 16, 24, tug, top = 3, front = 4)
        s.rect(x + 7, 11, x + 14, 14, BLACK[4])
        for (k in x + 8 until x + 13) s.set(k, 12, if (k % 2 != 0) GLASS[1] else GLASS[2])
        s.hazard(x + 1, 20, x + 13, 23)
        s.light(x + 15, 18, WHITE); s.light(x + 3, 9, RED)
        s.box(2, 10, 16, 24, BLACK, top = 2, back = 2)  // pusher engine
        s.cylinder(0, 12, 4, 22, STEEL)
        s.light(8, 11, AMBER)
        s.nozzle(-1, 17, 3, FUSION)
        s.grime(0.22f, 10)
        s.outline()
        return s
    }

    /** Bulk tanker: banded cylindrical tanks under a catwalk, bridge tower aft. */
    private fun tanker(seed: Int): ShipSprite {
        val rng = Random(seed)
        val paint = PAINTS.getValue(rng.pick("rust", "bone", "gunmetal", "orange"))
        val tanks = rng.nextInt(3, 5)
        val tw = 18
        val l = 28 + tanks * (tw + 2) + 10
        val s = sprite(l + 4, 40, seed)
        s.rect(18, 30, l - 8, 33, BLACK[3]); s.hline(18, l - 8, 32, BLACK[4])  // keel spine
        var x = 24
        repeat(tanks) {
            s.cylinder(x, 12, x + tw, 31, paint)
            for (b in intArrayOf(x + 3, x + tw - 4)) { s.vline(b, 12, 31, paint[4]); s.vline(b + 1, 12, 31, paint[1]) }
            if (rng.nextFloat() < 0.5f) s.rect(x + 6, 18, x + 12, 20, BLACK[3])
            x += tw + 2
        }
        s.hline(22, l - 8, 10, STEEL[2])  // catwalk with posts
        for (k in 22 until l - 8 step 4) s.vline(k, 10, 13, STEEL[3])
        s.poly(l - 10f, 14f, l - 2f, 18f, l.toFloat(), 26f, l - 4f, 32f, l - 10f, 32f) { xx, yy -> s.tone(paint, 1.5f + (yy - 14) / 12f, xx, yy) }
        s.light(l - 2, 22, WHITE)
        val bone = PAINTS.getValue("bone")
        s.box(6, 2, 22, 32, bone, top = 2, front = 2)  // bridge tower
        s.windowStrip(8, 20, 6)
        s.windowStrip(8, 20, 12, 0.5f)
        s.vline(14, 0, 2, STEEL[2]); s.light(14, 0, RED)
        s.hazard(6, 27, 22, 30)
        s.greebles(7, 16, 21, 26, bone, 6)
        s.cylinder(0, 18, 7, 30, STEEL)
        s.nozzle(-1, 24, 4, PLASMA)
        s.grime(0.2f, 14)
        s.outline()
        return s
    }

    /** A small utility tug with a manipulator arm. */
    private fun workboat(seed: Int): ShipSprite {
        val rng = Random(seed)
        val paint = PAINTS.getValue(rng.pick("orange", "tan", "olive"))
        val s = sprite(34, 18, seed)
        s.box(4, 4, 24, 14, paint, top = 2, front = 3, back = 2)
        s.rect(14, 6, 21, 9, BLACK[4]); s.hline(15, 21, 7, GLASS[1])
        s.hline(24, 31, 11, STEEL[2])
        s.set(31, 10, STEEL[1]); s.set(31, 12, STEEL[1]); s.set(32, 10, STEEL[2]); s.set(32, 12, STEEL[2])
        s.cylinder(0, 6, 5, 12, BLACK)
        s.hazard(6, 11, 14, 13)
        s.light(10, 4, AMBER)
        s.nozzle(-1, 9, 2, ION)
        s.grime(0.15f, 4)
        s.outline()
        return s
    }
}

/**
 * Every industrial design in a few paint jobs, pre-shrunk to each depth and pre-blended into the nebula's
 * haze while the wallpaper prepares, so a ship never has to be painted, scaled or hazed mid-animation.
 */
internal class FlybyFleet(seed: Int, haze: Int, progress: (Float) -> Unit = {}) {
    companion object {
        const val DEPTHS = 8
        const val VARIANTS = 3
        fun depthOf(bucket: Int) = (bucket + 0.5f) / DEPTHS
    }

    private val sprites: Array<Array<Array<ShipSprite>>>

    init {
        val designs = Freighters.WEIGHTS.size
        var done = 0
        sprites = Array(designs) { design ->
            Array(VARIANTS) { v ->
                val full = Freighters.build(design, seed * 977 + design * 53 + v)
                Array(DEPTHS) { bucket ->
                    val z = depthOf(bucket)
                    val shrunk = full.scaled(0.28f + 0.72f * z)
                    val hazed = ShipSprite(shrunk.w, shrunk.h)
                    val fog = (1 - z) * 0.62f
                    for (i in shrunk.px.indices) if (shrunk.px[i] != 0) hazed.px[i] = mix(shrunk.px[i], haze, fog)
                    hazed.lights += shrunk.lights; hazed.nozzles += shrunk.nozzles
                    hazed
                }.also { progress(++done / (designs * VARIANTS).toFloat()) }
            }
        }
    }

    fun sprite(design: Int, variant: Int, bucket: Int) = sprites[design][variant][bucket]
}

/**
 * A big ship crossing the screen, emitted at a random depth: z = 0 is far away, 1 is right in front of the camera.
 * Depth sets its size, speed (parallax), haze, trail length and whether it passes behind the planet.
 */
internal class Flyby(rng: Random, others: List<Flyby>, w: Int, h: Int, stationY: Float, fleet: FlybyFleet) {
    private val bucket = (Math.pow(rng.nextFloat().toDouble(), 0.8) * FlybyFleet.DEPTHS).toInt().coerceIn(0, FlybyFleet.DEPTHS - 1)
    val z = FlybyFleet.depthOf(bucket)
    val far = z < 0.45f
    private val design = run {
        var roll = rng.nextInt(Freighters.WEIGHTS.sum())
        Freighters.WEIGHTS.indexOfFirst { roll -= it; roll < 0 }
    }
    private val spr = fleet.sprite(design, rng.nextInt(FlybyFleet.VARIANTS), bucket)
    private val dir = if (rng.nextBoolean()) 1 else -1
    private val speed = (3 + 13 * z) * (0.85f + rng.nextFloat() * 0.3f) * (if (design == 2) 0.7f else 1f)
    private var x = if (dir > 0) -spr.w - 10f else w + 10f
    var y = 0f; private set
    val height get() = spr.h
    private val drift: Float
    private val phase = rng.nextFloat() * 10
    private val trailLength = (8 + 20 * z).toInt()
    private val trails = Array(spr.nozzles.size) { ArrayDeque<Float>() }
    private val screenW = w
    var gone = false; private set

    init {
        // pick a lane: close ships keep clear of the station, and no two ships at a similar depth share a lane
        for (attempt in 0 until 20) {
            y = 20 + rng.nextFloat() * max(1, h - 40 - spr.h)
            val mid = y + spr.h / 2f
            if (z > 0.6f && kotlin.math.abs(mid - stationY) < 50 + spr.h / 2f) continue
            if (others.any { kotlin.math.abs(it.z - z) < 0.3f && kotlin.math.abs(it.y + it.height / 2f - mid) < (it.height + spr.h) / 2f + 8 }) continue
            break
        }
        drift = (rng.nextFloat() * 0.12f - 0.06f) * speed  // a slight climb or descent, so lanes cross
    }

    private fun worldX(sx: Int) = x + if (dir > 0) sx else spr.w - 1 - sx

    fun step(dt: Float) {
        x += dir * speed * dt
        y += drift * dt
        spr.nozzles.forEachIndexed { n, nz ->
            trails[n].addLast(worldX(nz[0] - 1)); trails[n].addLast(y + nz[1])
            while (trails[n].size > trailLength * 2) { trails[n].removeFirst(); trails[n].removeFirst() }
        }
        if (x < -spr.w - 60 || x > screenW + 60) gone = true
    }

    fun draw(f: AstroFrame, t: Float, frame: Int) {
        spr.nozzles.forEachIndexed { n, nz ->
            val r = nz[2]
            val core = Freighters.EXHAUST_CORE[nz[3]]; val trail = Freighters.EXHAUST_TRAIL[nz[3]]
            val tr = trails[n]
            val count = tr.size / 2
            for (k in 0 until count) {
                val fk = (k + 1f) / count
                for (dy in -r + 1 until r) if (kotlin.math.abs(dy) <= r * fk)
                    f.set(tr[2 * k], tr[2 * k + 1] + dy, mix(shade(trail, 0.5f), trail, fk), (0.08f + 0.4f * fk * fk) * (0.45f + 0.55f * z))
            }
            val fx = worldX(nz[0]); val fy = y + nz[1]  // the nozzle flame, flickering
            for (k in 0 until r + 2 + frame % 2) for (dy in -r + 1 until r) {
                if (kotlin.math.abs(dy) < r - k * 0.5f) f.set(fx - dir * k, fy + dy, if (k < 2) core else trail, 0.9f - k * 0.12f)
            }
        }
        val yi = floor(y + 0.5f).toInt()
        val xi = floor(x + 0.5f).toInt()
        for (sy in 0 until spr.h) for (sx in 0 until spr.w) {
            val c = spr.px[sy * spr.w + sx]
            if (c != 0) f.set(xi + if (dir > 0) sx else spr.w - 1 - sx, yi + sy, c)
        }
        for (light in spr.lights) {
            val kind = light[2]
            if ((kind == Freighters.RED || kind == Freighters.GREEN) && sin(t * 4 + phase + light[0] * 0.3f) <= 0.2f) continue
            val lx = xi + if (dir > 0) light[0] else spr.w - 1 - light[0]
            val ly = yi + light[1]
            val c = Freighters.LIGHTS[kind]
            f.set(lx, ly, c)
            if (z > 0.6f) { f.set(lx + 1, ly, c, 0.35f); f.set(lx - 1, ly, c, 0.35f); f.set(lx, ly + 1, c, 0.35f); f.set(lx, ly - 1, c, 0.35f) }
        }
    }
}
