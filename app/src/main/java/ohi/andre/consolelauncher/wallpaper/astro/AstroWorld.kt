package ohi.andre.consolelauncher.wallpaper.astro

import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.dither
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.fbm
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.mix
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.rgb
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.shade
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The star is off screen, up and to the left, slightly behind the viewer. */
internal val LIGHT = floatArrayOf(-0.50f, -0.72f, 0.48f)

internal class PlanetPalette(
    val name: String, ocean: Array<String>, land: Array<String>, high: Array<String>, val sea: Float,
    cloud: String, shallow: String, dusk: String, day: String, night: String, city: Array<String>
) {
    val ocean = ocean.map(::rgb).toIntArray(); val land = land.map(::rgb).toIntArray(); val high = high.map(::rgb).toIntArray()
    val cloud = rgb(cloud); val shallow = rgb(shallow); val dusk = rgb(dusk); val day = rgb(day); val night = rgb(night)
    val city = city.map(::rgb).toIntArray()
}

/**
 * Everything a seed decides: the kind of planet, the nebula's colours and shape, the stars and the
 * debris rings. Each reseed steps to the next kind of world, so it always looks different.
 */
internal class AstroWorld(val seed: Int, val w: Int, val h: Int) {
    companion object {
        const val PLANET_RADIUS = 92
        val PALETTES = listOf(
            PlanetPalette("rose sea", arrayOf("#58b8d0", "#2a7898", "#1a3460", "#0a1028"), arrayOf("#e8a0c4", "#b85a8a", "#5a2450", "#160a1e"),
                arrayOf("#b8e8d0", "#68b090", "#265e50", "#0a1818"), 0.52f, "#f4f0ff", "#9affe8", "#ff7aa0", "#9af0ff", "#b06ad8",
                arrayOf("#ffe08a", "#ffb0e0", "#9affff")),
            PlanetPalette("dune", arrayOf("#4a9a8a", "#2a6a64", "#173a3c", "#081618"), arrayOf("#f0c88a", "#c88a4a", "#6a3a22", "#1c0e08"),
                arrayOf("#ffe8c0", "#d8a070", "#7a4a2a", "#20120a"), 0.36f, "#fff0d8", "#8ad8b0", "#ff9a5a", "#ffd08a", "#a0503a",
                arrayOf("#ffd070", "#ff9a50", "#fff0a0")),
            PlanetPalette("glacier", arrayOf("#3a6ad0", "#22449a", "#142a60", "#080e24"), arrayOf("#c8b8f0", "#8a78c8", "#463a78", "#120e22"),
                arrayOf("#f4f8ff", "#c0d0f0", "#6a7aa8", "#1a2032"), 0.5f, "#ffffff", "#7ac8ff", "#c08aff", "#bff0ff", "#5a6ad8",
                arrayOf("#bff8ff", "#ffffff", "#9ab8ff")),
            PlanetPalette("verdigris", arrayOf("#4ad0a0", "#1e8a70", "#0e4a44", "#041816"), arrayOf("#b080e0", "#7a4ab0", "#3a2060", "#10081c"),
                arrayOf("#e0ff8a", "#a0c84a", "#4a6a22", "#121c08"), 0.55f, "#e8fff0", "#a0ffd0", "#d0ff6a", "#a0ffb0", "#7a3ac0",
                arrayOf("#d0ff6a", "#ff8af0", "#ffffff")),
            PlanetPalette("ember", arrayOf("#5a4a52", "#3a2e36", "#22181e", "#0c080a"), arrayOf("#8a7a74", "#5a4c48", "#2e2422", "#0e0a0a"),
                arrayOf("#ffb060", "#ff6a2a", "#a02a14", "#3a0c06"), 0.45f, "#c8b0a8", "#7a5a5a", "#ff5a2a", "#ffa070", "#c0301a",
                arrayOf("#ff8a2a", "#ffd060", "#ff4a1a")),
        )
        private val NEBULAE = arrayOf(floatArrayOf(0.75f, 0.52f), floatArrayOf(0.95f, 0.66f), floatArrayOf(0.40f, 0.60f),
            floatArrayOf(0.08f, 0.82f), floatArrayOf(0.50f, 0.88f), floatArrayOf(0.62f, 0.30f))
        private val ICE = arrayOf("#f0f4ff", "#b8c4e8", "#5a6290", "#141830").map(::rgb).toIntArray()
        private const val TW = 512
        private const val TH = 256

        fun nameOf(seed: Int) = PALETTES[Math.floorMod(seed, PALETTES.size)].name

        /** Colours Android uses to theme the system around the wallpaper, without building the world. */
        fun signature(seed: Int): IntArray {
            val rng = Random(seed * 7919 + 13)
            val hue = NEBULAE[rng.nextInt(NEBULAE.size)]
            val neb = hsvRamp(hue[0] + rng.nextFloat() * 0.06f - 0.03f, 5, 0.27f, 0.75f)
            val p = PALETTES[Math.floorMod(seed, PALETTES.size)]
            return intArrayOf(neb[2], p.land[1], p.ocean[1])
        }

        private fun hsvRamp(hue: Float, n: Int, top: Float, sat: Float) = IntArray(n) { i ->
            AstroPixels.hsv(hue, sat * (0.6f + 0.4f * i / (n - 1)), 0.02f + top * i / (n - 1))
        }
    }

    class Ring(val rx: Float, val ry: Float, val tilt: Float, val period: Float,
               val angle: FloatArray, val jitter: FloatArray, val speed: FloatArray, val colour: IntArray, val chunk: BooleanArray) {
        var sats: List<Sat> = emptyList()
    }
    class Sat(val angle: Float, val rows: Array<String>, val light: Int, val phase: Float)

    val palette = PALETTES[Math.floorMod(seed, PALETTES.size)]
    val px = w / 2f
    val py = h / 2f
    private val cityDensity = (70 * max(0.35f, 1 - (0.52f - palette.sea) * 3.5f)).toInt()
    private val rng = Random(seed * 7919 + 13)
    private val nebHue = NEBULAE[rng.nextInt(NEBULAE.size)]
    val neb = hsvRamp(nebHue[0] + rng.nextFloat() * 0.06f - 0.03f, 5, 0.27f, 0.75f)
    private val teal = hsvRamp(nebHue[1] + rng.nextFloat() * 0.06f - 0.03f, 4, 0.22f, 0.7f)
    private val shiftX = rng.nextFloat() * 400f
    private val shiftY = rng.nextFloat() * 400f
    val rings: List<Ring>
    val starX: IntArray; val starY: IntArray; val starC: IntArray; val starPhase: FloatArray

    init {
        val count = intArrayOf(1, 1, 2, 2, 3)[rng.nextInt(5)]
        val debrisColours = arrayOf("#8a80a8", "#b0a4cc", "#d0c8e8", "#a89070", "#c8b090", "#7a90a8", "#e8d0c0", "#6a7080").map(::rgb)
        rings = (0 until count).map { i ->
            val rx = 112f + rng.nextFloat() * 38f + i * 14
            val width = 2f + rng.nextFloat() * 4f
            val n = 260 + rng.nextInt(440)
            val colours = debrisColours.shuffled(rng).take(3)
            val r = Random(rng.nextInt())
            Ring(rx, rx * (0.16f + rng.nextFloat() * 0.14f), rng.nextFloat() * 0.9f - 0.45f,
                (900f + rng.nextFloat() * 1500f) * (if (rng.nextBoolean()) 1 else -1),
                FloatArray(n) { r.nextFloat() * 2 * PI.toFloat() }, FloatArray(n) { (r.nextGaussian() * width / 2).toFloat() },
                FloatArray(n) { 0.95f + r.nextFloat() * 0.1f }, IntArray(n) { colours[r.nextInt(3)] }, BooleanArray(n) { r.nextFloat() < 0.15f })
        }
        val shapes = arrayOf(arrayOf("p.s.p"), arrayOf("pp.s.pp"), arrayOf(".s.", "psp", ".s."), arrayOf("p.o.p"))
        val lights = arrayOf("#7affb0", "#ff5a8a", "#ffffff").map(::rgb)
        rings.maxByOrNull { it.rx }!!.sats = (0 until 14).map {
            Sat(it / 14f * 2 * PI.toFloat() + rng.nextFloat() * 0.3f - 0.15f, shapes[rng.nextInt(shapes.size)], lights[rng.nextInt(3)], rng.nextFloat() * 6f)
        }
        val bright = arrayOf("#e8e0ff", "#ffd8f0", "#c8fff4", "#fff4d8").map(::rgb)
        val n = (w * h / 540).coerceAtLeast(60)
        starX = IntArray(n + 14) { rng.nextInt(w) }
        starY = IntArray(n + 14) { rng.nextInt(h) }
        starC = IntArray(n + 14) { if (it < n) mix(neb[4], rgb(230, 230, 255), 0.2f + rng.nextFloat() * 0.4f) else bright[rng.nextInt(4)] }
        starPhase = FloatArray(n + 14) { rng.nextFloat() * 6.3f }
    }

    private fun Random.nextGaussian(): Double {
        var u: Double; var v: Double; var s: Double
        do { u = nextDouble() * 2 - 1; v = nextDouble() * 2 - 1; s = u * u + v * v } while (s >= 1 || s == 0.0)
        return u * sqrt(-2 * kotlin.math.ln(s) / s)
    }

    /** The dithered nebula, painted once per world and size. */
    fun background(): IntArray {
        val out = IntArray(w * h)
        val salt = seed and 0xffff
        for (y in 0 until h) for (x in 0 until w) {
            val v = fbm((x + shiftX) / 70f, (y + shiftY) / 70f, 3 + salt)
            val u = fbm((x + shiftY) / 55f + 40, (y + shiftX) / 55f + 9, 21 + salt)
            val th = dither(x, y) - 0.5f
            val diag = (x.toFloat() / w + y.toFloat() / h) / 2
            val vv = max(0f, v * 1.5f - 0.55f + (1 - diag) * 0.35f)
            val uu = max(0f, u * 1.6f - 0.75f + diag * 0.35f)
            val lv = (vv * 6 + th).toInt().coerceIn(0, 4)
            val lu = (uu * 6 + th).toInt().coerceIn(0, 3)
            out[y * w + x] = if (lv >= lu) neb[lv] else teal[lu]
        }
        return out
    }

    // ---- planet ----
    // The surface turns one texel at a time (about every 1.8 s) and the clouds about once a second, so the
    // lit disc is shaded into [layer] only when one of them steps. Each frame copies the layer, adds the
    // flickering city lights, and blends the static atmosphere glow over whatever is behind the planet.
    private val land = FloatArray(TW * TH)
    private val clouds = FloatArray(TW * TH)
    private val discIndex: IntArray; private val discLon: IntArray; private val discLat: IntArray
    private val discLight: FloatArray; private val discLevel: FloatArray; private val discRim: BooleanArray
    private val glowIndex: IntArray; private val glowColour: IntArray; private val glowAlpha: FloatArray
    private val layer: IntArray
    private var cityCells = IntArray(64); private var cityKey = IntArray(64); private var cityCount = 0
    private var cityLit = IntArray(64)
    private var shadedRot = Int.MIN_VALUE
    private var shadedClouds = Int.MIN_VALUE

    init {
        val shift = 0.52f - palette.sea  // re-centre the height map so each world's sea level behaves the same
        val salt = (seed and 0xff) * 13
        for (v in 0 until TH) for (u in 0 until TW) {
            land[v * TW + u] = (fbm(u / 32f, v / 32f, 77 + salt, TW / 32, 5) + shift).coerceIn(0f, 1f)
            clouds[v * TW + u] = fbm(u / 32f, v / 13f, 91 + salt, TW / 32)
        }
        val r = PLANET_RADIUS
        val di = ArrayList<Int>(); val dlon = ArrayList<Int>(); val dlat = ArrayList<Int>()
        val dlight = ArrayList<Float>(); val dlevel = ArrayList<Float>(); val drim = ArrayList<Boolean>()
        val gi = ArrayList<Int>(); val gc = ArrayList<Int>(); val ga = ArrayList<Float>()
        val cx = px.toInt(); val cy = py.toInt()
        for (y in cy - r - 6..cy + r + 6) for (x in cx - r - 6..cx + r + 6) {
            if (x < 0 || y < 0 || x >= w || y >= h) continue
            val dx = (x - cx).toFloat() / r; val dy = (y - cy).toFloat() / r
            val d2 = dx * dx + dy * dy
            if (d2 <= 1f) {
                val dz = sqrt(1 - d2)
                val light = dx * LIGHT[0] + dy * LIGHT[1] + dz * LIGHT[2]
                di += y * w + x
                dlon += kotlin.math.floor(atan2(dx, dz) / (2 * PI.toFloat()) * TW).toInt()
                dlat += ((asin(dy.coerceIn(-1f, 1f)) / PI.toFloat() + 0.5f) * (TH - 1)).toInt()
                dlight += light
                dlevel += light + (dither(x, y) - 0.5f) * 0.28f
                drim += dz < 0.18f
            } else if (d2 <= ((r + 6f) / r).pow(2)) {  // atmosphere glow beyond the limb, fixed for the world
                val light = dx * LIGHT[0] + dy * LIGHT[1]
                val fall = 1 - (sqrt(d2) * r - r) / 6
                if (fall * (0.35f + max(0f, light + 0.3f)) > dither(x, y)) {
                    gi += y * w + x
                    gc += mix(palette.night, palette.day, (light + 0.6f).coerceIn(0f, 1f))
                    ga += 0.5f * fall
                }
            }
        }
        discIndex = di.toIntArray(); discLon = dlon.toIntArray(); discLat = dlat.toIntArray()
        discLight = dlight.toFloatArray(); discLevel = dlevel.toFloatArray(); discRim = drim.toBooleanArray()
        glowIndex = gi.toIntArray(); glowColour = gc.toIntArray(); glowAlpha = ga.toFloatArray()
        layer = IntArray(discIndex.size)
    }

    private fun shadePlanet(rot: Int, crot: Int) {
        val p = palette
        cityCount = 0
        for (i in discIndex.indices) {
            val lat = discLat[i]
            val u = Math.floorMod(discLon[i] + rot, TW)
            val hgt = land[lat * TW + u]
            val lv = discLevel[i]
            val polar = abs(lat - TH / 2f) / (TH / 2f) + (hgt - 0.5f) * 0.25f
            val tones = if (polar > 0.84f) ICE else if (hgt < 0.52f) p.ocean else if (hgt < 0.66f) p.land else p.high
            var c = when {
                lv > 0.55f -> tones[0]
                lv > 0.2f -> tones[1]
                lv > -0.08f -> tones[2]
                else -> {
                    if (hgt >= 0.52f) {  // a city on the night side: remember it so it can flicker every frame
                        val k = (u * 73856093 xor lat * 19349663) and 1023
                        if (k < cityDensity) addCity(i, k, p.city[k % 3])
                    }
                    tones[3]
                }
            }
            if (tones === p.ocean && hgt > 0.49f && lv > -0.08f) c = mix(c, p.shallow, 0.35f)
            if (lv > -0.12f && lv < 0.08f) c = mix(c, p.dusk, 0.38f)
            val cl = clouds[lat * TW + Math.floorMod(discLon[i] + crot, TW)]
            if (cl > 0.62f && lv > -0.08f) c = mix(c, if (lv > 0.2f) p.cloud else shade(p.cloud, 0.55f), if (cl > 0.7f) 0.75f else 0.4f)
            if (discRim[i]) c = mix(c, if (discLight[i] > -0.1f) p.day else p.night, 0.45f)
            layer[i] = c
        }
    }

    private fun addCity(cell: Int, key: Int, lit: Int) {
        if (cityCount == cityCells.size) {
            cityCells = cityCells.copyOf(cityCount * 2); cityKey = cityKey.copyOf(cityCount * 2)
            cityLit = cityLit.copyOf(cityCount * 2)
        }
        cityCells[cityCount] = cell; cityKey[cityCount] = key; cityLit[cityCount] = lit
        cityCount++
    }

    /** Shade the disc now (used while the world is being prepared, so the first frame is ready). */
    fun preparePlanet(t: Float) = drawPlanet(null, t)

    fun drawPlanet(f: AstroFrame?, t: Float) {
        val rot = kotlin.math.floor(t / 900f * TW).toInt()     // the surface turns once every 15 minutes
        val crot = kotlin.math.floor(t / 520f * TW).toInt()    // clouds run a little faster
        if (rot != shadedRot || crot != shadedClouds) { shadePlanet(rot, crot); shadedRot = rot; shadedClouds = crot }
        if (f == null) return
        val out = f.px
        for (i in glowIndex.indices) {
            val idx = glowIndex[i]
            out[idx] = mix(out[idx], glowColour[i], glowAlpha[i])
        }
        for (i in discIndex.indices) out[discIndex[i]] = layer[i]
        for (n in 0 until cityCount) {
            val k = cityKey[n]
            if (sin(t * 1.5f + k) > -0.7f) out[discIndex[cityCells[n]]] = cityLit[n]
        }
    }

    // ---- rings ----
    private fun ringPoint(r: Ring, a: Float, j: Float, out: FloatArray) {
        val x = cos(a) * (r.rx + j); val y = sin(a) * (r.ry + j * 0.3f)
        val ct = cos(r.tilt); val st = sin(r.tilt)
        out[0] = px + x * ct - y * st; out[1] = py + x * st + y * ct; out[2] = sin(a)
    }

    private val tmp = FloatArray(3)
    private val satColours = mapOf('p' to rgb("#4a5ad0"), 's' to rgb("#c8d0e8"), 'o' to rgb("#ffffff"))

    fun drawRings(f: AstroFrame, t: Float, front: Boolean) {
        for (r in rings) {
            val base = t / r.period * 2 * PI.toFloat()
            for (i in r.angle.indices) {
                ringPoint(r, r.angle[i] + base * r.speed[i], r.jitter[i], tmp)
                if ((tmp[2] > 0) != front) continue
                val c = if (front) r.colour[i] else shade(r.colour[i], 0.6f)
                f.set(tmp[0], tmp[1], c)
                if (r.chunk[i]) f.set(tmp[0] + 1, tmp[1], shade(c, 0.7f))
            }
            for (s in r.sats) {
                ringPoint(r, s.angle + base, 0f, tmp)
                if ((tmp[2] > 0) != front) continue
                s.rows.forEachIndexed { row, line ->
                    line.forEachIndexed { col, k ->
                        if (k != '.') f.set(tmp[0] + col - line.length / 2, tmp[1] + row - s.rows.size / 2, satColours.getValue(k))
                    }
                }
                if (sin(t * 3 + s.phase) > 0.4f) f.set(tmp[0], tmp[1] - s.rows.size / 2 - 1, s.light)
            }
        }
    }

    fun drawStars(f: AstroFrame, t: Float) {
        for (i in starX.indices) {
            val ph = starPhase[i]
            f.set(starX[i], starY[i], starC[i], 0.55f + 0.45f * sin(t * (0.6f + ph % 1.3f) + ph * 7))
        }
    }
}
