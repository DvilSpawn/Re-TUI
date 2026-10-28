package ohi.andre.consolelauncher.wallpaper.astro

import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.dither
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.mix
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.rgb
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.shade
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.withSign
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()
private fun wrapAngle(a: Float): Float = ((a + PI.toFloat()) % TAU + TAU) % TAU - PI.toFloat()
private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)
private fun pack(x: Int, y: Int) = (x + 128) * 512 + (y + 128)

/** A small craft's hull, built facing +x from tail to nose and mirrored about the keel. */
internal class SmallHull(val cells: Map<Int, Char>, val nozzles: IntArray, val length: Int)

internal object SmallHulls {
    enum class Kind(val top: Float, val turn: Float, val accel: Float, val big: Boolean) {
        COURIER(34f, 3.6f, 30f, false), SHUTTLE(22f, 2.6f, 18f, false), FREIGHTER(15f, 1.5f, 9f, true),
        TANKER(13f, 1.3f, 8f, true), LINER(17f, 1.4f, 10f, true), HAULER(10f, 0.8f, 5f, true)
    }

    fun build(rng: Random, kind: Kind): SmallHull {
        val cells = HashMap<Int, Char>()
        fun put(x: Int, y: Int, k: Char) { cells[pack(x, y)] = k; cells[pack(x, -y)] = k }
        fun cell(x: Int, y: Int, k: Char) { cells[pack(x, y)] = k }
        fun nose(x0: Int, h: Int, length: Int, canopyRow: Int = 0): Int {
            for (i in 0 until length) {
                val hh = max(0, round(h * (1 - (i + 1f) / (length + 1))).toInt())
                for (y in 0..hh) put(x0 + i, y, 'h')
            }
            cell(x0 + max(0, length - 2), -canopyRow, 'c')
            return x0 + length
        }
        when (kind) {
            Kind.COURIER -> {  // a needle with swept fins
                val l = rng.nextInt(6, 9)
                cell(0, 0, 'e')
                for (x in 1 until l - 1) put(x, 0, 'h')
                put(1, 1, 'a'); put(2, 1, 'h')
                if (rng.nextFloat() < 0.6f) put(1, 2, 'a')
                cell(l - 1, 0, 'h'); cell(l - 3, 0, 'c')
                return SmallHull(cells, intArrayOf(0), l)
            }
            Kind.SHUTTLE -> {  // stubby body on a delta wing
                val l = rng.nextInt(8, 11)
                cell(0, -1, 'e'); cell(0, 1, 'e')
                for (x in 1 until l - 2) { put(x, 0, 'h'); put(x, 1, 'h') }
                val span = rng.nextInt(2, 4)
                for (i in 0..span) for (y in 2 until 2 + span - i) put(2 + i, y, if (y == 1 + span - i) 'a' else 'h')
                nose(l - 2, 1, 2)
                cell(l - 3, 0, 'c'); cell(3, 0, 'l')
                return SmallHull(cells, intArrayOf(-1, 1), l)
            }
            Kind.FREIGHTER -> {  // cargo pods between an engine block and a cab
                cell(0, -1, 'e'); cell(0, 1, 'e')
                for (x in 1..2) for (y in 0..2) put(x, y, if (y < 2) 'd' else 's')
                var x = 3
                repeat(rng.nextInt(2, 4)) {
                    val k = "kmn"[rng.nextInt(3)]
                    for (i in 0..1) for (y in 0..2) put(x + i, y, if (y < 2) k else k.uppercaseChar())
                    put(x + 2, 0, 's')
                    x += 3
                }
                for (i in 0..1) for (y in 0..2) put(x + i, y, 'h')
                cell(x + 1, -1, 'c')
                return SmallHull(cells, intArrayOf(-1, 1), nose(x + 2, 2, 2))
            }
            Kind.TANKER -> {  // round tanks strung on a spine
                cell(0, 0, 'e'); put(1, 0, 'd'); put(1, 1, 'd')
                var x = 2
                repeat(rng.nextInt(2, 4)) {
                    put(x, 0, 's')
                    intArrayOf(1, 2, 2, 1).forEachIndexed { i, hh -> for (y in 0..hh) put(x + 1 + i, y, 'g') }
                    cell(x + 2, -2, 'G'); cell(x + 1, -1, 'G')
                    x += 5
                }
                put(x, 0, 's')
                for (y in 0..1) put(x + 1, y, 'h')
                val end = nose(x + 2, 1, 2)
                cell(end - 2, 0, 'c')
                return SmallHull(cells, intArrayOf(0), end)
            }
            Kind.LINER -> {  // long sleek hull, lit window rows, swept tail fins
                val body = rng.nextInt(9, 13)
                cell(0, -1, 'e'); cell(0, 1, 'e')
                for (x in 1..body) {
                    for (y in 0..2) put(x, y, if (y == 2) 'a' else 'h')
                    if (x % 2 == 0 && x in 3 until body) put(x, 1, 'l')
                }
                put(1, 3, 'a'); put(2, 3, 'a'); put(1, 4, 'a')
                val end = nose(body + 1, 2, 3)
                cell(body + 1, 0, 'c')
                return SmallHull(cells, intArrayOf(-1, 1), end)
            }
            Kind.HAULER -> {  // a truss spine lined with stacked containers, a bridge tower up front
                for (y in intArrayOf(-2, 0, 2)) cell(0, y, 'e')
                for (x in 1..2) for (y in 0..2) put(x, y, 'd')
                var x = 3
                repeat(rng.nextInt(4, 6)) {
                    put(x, 0, 's')
                    for (i in 0..2) put(x + 1 + i, 0, 's')
                    for (y in 1..3) {
                        val pick = "kmnkmn" + if (y == 3) "." else ""
                        val k = pick[rng.nextInt(pick.length)]
                        if (k == '.') continue
                        for (i in 0..2) {
                            cell(x + 1 + i, y, if (i > 0) k else k.uppercaseChar())
                            val k2 = if (rng.nextFloat() < 0.3f) "kmn"[rng.nextInt(3)] else k
                            cell(x + 1 + i, -y, if (i > 0) k2 else k2.uppercaseChar())
                        }
                    }
                    x += 4
                }
                put(x, 0, 's')
                for (i in 0..2) for (y in 0 until 3 - (if (i == 2) 1 else 0)) put(x + 1 + i, y, 'h')
                cell(x + 2, -1, 'c'); cell(x + 3, -1, 'c')
                return SmallHull(cells, intArrayOf(-2, 0, 2), nose(x + 4, 1, 2))
            }
        }
    }

    /** Rotate with 4x4 supersampled majority voting, so tiny sprites stay solid at any heading. */
    fun rotate(hull: SmallHull, angle: Float): RotatedSprite {
        val cx = (hull.length - 1) / 2f
        val ca = cos(angle); val sa = sin(angle)
        var reach = 0f
        for (key in hull.cells.keys) reach = max(reach, hypot(key / 512 - 128 - cx, (key % 512 - 128).toFloat()))
        val r = ceil(reach).toInt() + 1
        val xs = ArrayList<Int>(); val ys = ArrayList<Int>(); val ks = ArrayList<Char>()
        val votes = HashMap<Char, Int>()
        for (oy in -r..r) for (ox in -r..r) {
            votes.clear()
            for (sy in 0..3) for (sx in 0..3) {
                val fx = ox - 0.375f + sx * 0.25f; val fy = oy - 0.375f + sy * 0.25f
                val u = floor(fx * ca + fy * sa + cx + 0.5f).toInt()
                val v = floor(-fx * sa + fy * ca + 0.5f).toInt()
                val k = hull.cells[pack(u, v)] ?: continue
                votes[k] = (votes[k] ?: 0) + 1
            }
            val best = votes.maxByOrNull { it.value } ?: continue
            if (best.value >= 6) { xs += ox; ys += oy; ks += best.key }
        }
        return RotatedSprite(xs.toIntArray(), ys.toIntArray(), ks.toCharArray())
    }
}

internal class RotatedSprite(val x: IntArray, val y: IntArray, val key: CharArray) {
    /** The dark outline around the silhouette, worked out once rather than every frame. */
    val outlineX: IntArray
    val outlineY: IntArray

    init {
        val occupied = x.indices.mapTo(HashSet()) { pack(x[it], y[it]) }
        val edge = LinkedHashSet<Int>()
        for (i in x.indices) for ((nx, ny) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val k = pack(x[i] + nx, y[i] + ny)
            if (k !in occupied) edge += k
        }
        outlineX = edge.map { it / 512 - 128 }.toIntArray()
        outlineY = edge.map { it % 512 - 128 }.toIntArray()
    }
}

/** A few hulls of every kind, each pre-rotated to all headings while the wallpaper prepares. Ships pick from these. */
internal class SmallHullSet(seed: Int, variants: Int = 6, progress: (Float) -> Unit = {}) {
    class Variant(val hull: SmallHull, val rotations: Array<RotatedSprite>)

    private val sets: Map<SmallHulls.Kind, List<Variant>>

    init {
        val kinds = SmallHulls.Kind.values()
        var done = 0
        sets = kinds.associateWith { kind ->
            List(variants) { v ->
                val hull = SmallHulls.build(Random(seed * 131 + kind.ordinal * 31 + v), kind)
                Variant(hull, Array(SmallShip.DIRS) { i -> SmallHulls.rotate(hull, i * TAU / SmallShip.DIRS) })
                    .also { progress(++done / (kinds.size * variants).toFloat()) }
            }
        }
    }

    fun pick(kind: SmallHulls.Kind, rng: Random): Variant = sets.getValue(kind).let { it[rng.nextInt(it.size)] }
}

/** One of the small craft that dock at the station, land on the planet or pass through. */
internal class SmallShip(private val traffic: SmallTraffic, val kind: SmallHulls.Kind, seed: Int) {
    enum class State { FLY, APPROACH, HOLD, DOCKED, UNDOCK, EXIT, LAND }
    private class Waypoint(val x: Float, val y: Float, val arrive: Boolean, val slow: Float, val tol: Float)

    companion object {
        const val DIRS = 32
        private val HULLS = arrayOf("#e8e6f0" to "#f0a040", "#a8b0c8" to "#7af0f0", "#c8805a" to "#e8d8a0", "#b8a0e8" to "#ff8ad8",
            "#9ad8c0" to "#ffd070", "#6a6e88" to "#ff6a6a", "#d8c098" to "#5ab0ff", "#e8a0b8" to "#9affff")
        private val ENGINES = arrayOf("#d8ffff" to "#5ad8ff", "#ffe0ff" to "#ff5ad0", "#fff4c0" to "#ffa040",
            "#e8fff0" to "#6affa0", "#f0e0ff" to "#a070ff", "#ffffe0" to "#ffe060")
        private val CRATES = arrayOf("#e8a040", "#5ab0ff", "#ff6a8a", "#9ae070", "#d0c0ff", "#f0e0a0", "#40c8b0")
        val HOT = arrayOf("#fff4e0", "#ffc070", "#ff6aa0").map(::rgb).toIntArray()
        private val OUTLINE = rgb("#05040c")
        private val TRAIL_HOT = rgb("#ffd0a0")
    }

    private val rng = Random(seed)
    private val variant = traffic.hulls.pick(kind, rng)
    private val hull = variant.hull
    val length = hull.length
    private val colours = HashMap<Char, Int>()
    val core: Int
    val trailColour: Int
    val top = kind.top * rng.range(0.85f, 1.15f)
    private val turn = kind.turn
    private val accel = kind.accel
    var x = 0f; var y = 0f; var heading = 0f; var speed = 0f
    var alpha = 1f; var fade = 0f; var heat = 0f; var thrust = 0f
    var state = State.FLY
    var port: Port? = null
    var gone = false
    private var timer = 0f
    private var pivotX = 0f; private var pivotY = 0f; private var pivoting = false
    private val waypoints = ArrayDeque<Waypoint>()
    private var waypointTime = 0f
    private var currentWaypoint: Waypoint? = null
    val trails = Array(hull.nozzles.size) { ArrayDeque<Float>() }  // x, y pairs

    init {
        val (h, a) = HULLS[rng.nextInt(HULLS.size)]
        val (c, t) = ENGINES[rng.nextInt(ENGINES.size)]
        val hullC = rgb(h); val accent = rgb(a)
        colours['h'] = hullC; colours['a'] = accent; colours['A'] = shade(accent, 0.7f)
        colours['d'] = shade(hullC, 0.45f); colours['s'] = shade(hullC, 0.7f); colours['g'] = mix(hullC, accent, 0.35f)
        colours['G'] = mix(hullC, rgb("#ffffff"), 0.6f); colours['c'] = rgb("#9affff"); colours['l'] = rgb("#ffe08a")
        CRATES.toList().shuffled(rng).take(3).forEachIndexed { i, hex ->
            colours["kmn"[i]] = rgb(hex); colours["KMN"[i]] = shade(rgb(hex), 0.62f)
        }
        core = rgb(c); trailColour = rgb(t)
    }

    private fun sprite(): RotatedSprite = variant.rotations[Math.floorMod(round(heading / TAU * DIRS).toInt(), DIRS)]

    // ---- missions ----
    private fun go(x: Float, y: Float, arrive: Boolean = false, slow: Float = Float.NaN, tol: Float = 4f) =
        waypoints.addLast(Waypoint(x, y, arrive, slow, tol))

    /** If the leg to (fx, fy) would cross the station, go round it on waypoints outside its keep-out ellipse. */
    private fun detour(fx: Float, fy: Float) {
        val st = traffic.station
        val start = waypoints.lastOrNull()
        val sx = start?.x ?: x; val sy = start?.y ?: y
        val ax = (sx - st.sx) / AstroStation.KEEP_RX; val ay = (sy - st.sy) / AstroStation.KEEP_RY
        val bx = (fx - st.sx) / AstroStation.KEEP_RX; val by = (fy - st.sy) / AstroStation.KEEP_RY
        val dx = bx - ax; val dy = by - ay
        val len = (dx * dx + dy * dy).takeIf { it > 0f } ?: 1f
        val u = (-(ax * dx + ay * dy) / len).coerceIn(0f, 1f)
        if (hypot(ax + dx * u, ay + dy * u) >= 1f) return
        val a0 = atan2(ay, ax)
        val span = wrapAngle(atan2(by, bx) - a0)
        val step = PI.toFloat() / 4
        var k = 1
        while (k * step < abs(span)) {
            val a = a0 + (k * step).withSign(span)
            go(st.sx + cos(a) * AstroStation.KEEP_RX * 1.3f, st.sy + sin(a) * AstroStation.KEEP_RY * 1.3f, tol = 10f)
            k++
        }
    }

    fun route(x: Float, y: Float) { detour(x, y); go(x, y) }

    private fun requestPort(): Port? {
        val free = traffic.station.ports.filter { it.owner == null && (it.big || !kind.big) && (!it.big || kind.big || rng.nextFloat() < 0.3f) }
        if (free.isEmpty()) return null
        return free[rng.nextInt(free.size)].also { it.owner = this }
    }

    fun planDocking() {
        val p = requestPort()
        port = p
        if (p == null) {  // park in the holding area and try again
            val st = traffic.station
            if (state != State.HOLD) go(st.sx - 70 + rng.range(-20f, 20f), st.sy + (if (rng.nextBoolean()) 1 else -1) * rng.range(55f, 120f), arrive = true, tol = 6f)
            state = State.HOLD; timer = rng.range(2f, 5f)
            return
        }
        val reach = length / 2f + 2; val far = 26f + length
        val fx = p.x + cos(p.angle) * far; val fy = p.y + sin(p.angle) * far
        detour(fx, fy)
        go(fx, fy, arrive = true, slow = 0.5f * top, tol = 5f)
        go(p.x + cos(p.angle) * reach, p.y + sin(p.angle) * reach, arrive = true, slow = 4f, tol = 1.2f)
        state = State.APPROACH
    }

    fun landSomewhere(spread: Float) {
        val lat = rng.range(-spread, spread)
        route(traffic.px - cos(lat) * (traffic.pr - 6), traffic.py + sin(lat) * (traffic.pr - 6))
        state = State.LAND
    }

    private fun leave() {
        if (rng.nextFloat() < 0.3f && kind in setOf(SmallHulls.Kind.SHUTTLE, SmallHulls.Kind.COURIER, SmallHulls.Kind.LINER, SmallHulls.Kind.FREIGHTER)) {
            landSomewhere(0.6f)
        } else {
            val (ex, ey) = traffic.edgePoint(rng)
            route(ex, ey)
            state = State.EXIT
        }
    }

    // ---- physics ----
    fun step(dt: Float) {
        when (state) {
            State.DOCKED -> {
                timer -= dt; thrust = 0f
                if (timer <= 0) { state = State.UNDOCK; timer = 2.5f + length / 8f }
                return
            }
            State.UNDOCK -> {
                val p = port!!
                x += cos(p.angle) * 4 * dt; y += sin(p.angle) * 4 * dt
                timer -= dt
                if (timer <= 0) {
                    p.owner = null; port = null; speed = 0f
                    pivotX = cos(p.angle); pivotY = sin(p.angle); pivoting = true
                    leave()
                }
                return
            }
            else -> Unit
        }
        if (pivoting) {  // clear of the collar: swing round on thrusters while drifting out, then light the mains
            val wp = waypoints.firstOrNull()
            if (wp == null) { pivoting = false; return }
            val err = wrapAngle(atan2(wp.y - y, wp.x - x) - heading)
            heading += err.coerceIn(-turn * 1.4f * dt, turn * 1.4f * dt)
            x += pivotX * 3 * dt; y += pivotY * 3 * dt
            if (abs(err) < 0.35f) { pivoting = false; speed = 3f }
            return
        }
        if (fade != 0f) {
            alpha = (alpha + fade * dt).coerceIn(0f, 1f)
            // re-entry heat builds fast as a ship sinks in; a launch cools as it climbs out
            heat = if (fade < 0) min(1f, (1 - alpha) * 2.5f) else (1 - alpha) * 0.9f
            if (alpha <= 0f) { gone = true; return }
            if (alpha >= 1f && fade > 0) fade = 0f
        }
        if (waypoints.isEmpty()) {
            when (state) {
                State.LAND -> {
                    if (fade == 0f) fade = -0.55f
                    x += cos(heading) * speed * dt; y += sin(heading) * speed * dt
                    return
                }
                State.HOLD -> {  // park and drift until a collar frees up
                    speed = max(0f, speed - accel * dt)
                    timer -= dt
                    if (timer <= 0) planDocking()
                    if (state == State.HOLD) { x += cos(heading) * speed * dt; y += sin(heading) * speed * dt; return }
                }
                State.EXIT -> { gone = true; return }
                else -> return
            }
        }
        val wp = waypoints.firstOrNull() ?: return
        if (wp !== currentWaypoint) { currentWaypoint = wp; waypointTime = 0f }
        waypointTime += dt
        if (waypointTime > 30 && !(state == State.APPROACH && waypoints.size <= 2)) {
            waypoints.removeFirst()  // boxed in for too long: skip ahead rather than circle forever
            return
        }
        val dx = wp.x - x; val dy = wp.y - y
        val dist = hypot(dx, dy)
        var vx = cos(atan2(dy, dx)); var vy = sin(atan2(dy, dx))
        val final = state == State.APPROACH && waypoints.size == 1
        val st = traffic.station
        if (!final) {
            // look a little ahead; if that point is inside the keep-out ellipse, steer out along its normal
            val lx = x + cos(heading) * speed * 0.5f - st.sx; val ly = y + sin(heading) * speed * 0.5f - st.sy
            val e = hypot(lx / AstroStation.KEEP_RX, ly / AstroStation.KEEP_RY)
            if (e < 1.1f) {
                val gx = lx / (AstroStation.KEEP_RX * AstroStation.KEEP_RX); val gy = ly / (AstroStation.KEEP_RY * AstroStation.KEEP_RY)
                val g = hypot(gx, gy).takeIf { it > 0f } ?: 1f
                val push = (1.1f - e) * 4
                vx += gx / g * push; vy += gy / g * push
            }
        }
        for (o in traffic.ships) {
            if (o === this || o.state == State.DOCKED || o.state == State.UNDOCK) continue
            val ox = x - o.x; val oy = y - o.y
            val od = hypot(ox, oy)
            if (od > 0f && od < 10f) { vx += ox / od * (10 - od) / 10 * 0.8f; vy += oy / od * (10 - od) / 10 * 0.8f }
        }
        var err = wrapAngle(atan2(vy, vx) - heading)
        if (final) err = wrapAngle(port!!.angle + PI.toFloat() - heading)  // line up with the collar while creeping in
        val turnNow = turn * if (final) 2 else 1
        heading += err.coerceIn(-turnNow * dt, turnNow * dt)
        var target = top * max(0.25f, cos(min(abs(err), PI.toFloat() / 2)))
        if (!wp.slow.isNaN()) target = min(target, max(wp.slow, 0f))
        if (wp.arrive) target = min(target, sqrt(2 * accel * 0.6f * max(0f, dist - wp.tol * 0.5f)) + 0.6f)
        val dv = (target - speed).coerceIn(-accel * dt, accel * dt)
        thrust = dv / (accel * dt)
        speed += dv
        if (final) {  // creep straight down the collar's axis
            val d = if (dist > 0f) dist else 1f
            x += dx / d * speed * dt; y += dy / d * speed * dt
        } else {
            x += cos(heading) * speed * dt; y += sin(heading) * speed * dt
        }
        if (state == State.LAND && hypot(x - traffic.px, y - traffic.py) < traffic.pr + 4 && fade == 0f) fade = -0.55f
        if (dist < wp.tol) {
            waypoints.removeFirst()
            if (final) {
                val p = port!!
                x = p.x + cos(p.angle) * (length / 2f + 2); y = p.y + sin(p.angle) * (length / 2f + 2)
                heading = p.angle + PI.toFloat(); speed = 0f
                state = State.DOCKED; timer = rng.range(5f, 14f)
            }
        }
        recordTrails()
    }

    private fun recordTrails() {
        val limit = 10 + length
        if (speed > 1.5f) {
            val back = -(length - 1) / 2f - 1
            val c = cos(heading); val s = sin(heading)
            hull.nozzles.forEachIndexed { n, ny ->
                trails[n].addLast(x + c * back - s * ny); trails[n].addLast(y + s * back + c * ny)
                while (trails[n].size > limit * 2) { trails[n].removeFirst(); trails[n].removeFirst() }
            }
        } else {
            for (tr in trails) if (tr.isNotEmpty()) { tr.removeFirst(); tr.removeFirst() }
        }
    }

    // ---- drawing ----
    fun drawTrails(f: AstroFrame) {
        for (tr in trails) {
            val n = tr.size / 2
            var lastX = Int.MIN_VALUE; var lastY = Int.MIN_VALUE
            for (k in 1 until n) {
                val x0 = tr[2 * k - 2]; val y0 = tr[2 * k - 1]; val x1 = tr[2 * k]; val y1 = tr[2 * k + 1]
                val steps = max(1, ceil(max(abs(x1 - x0), abs(y1 - y0))).toInt())
                for (j in 0 until steps) {
                    val u = j.toFloat() / steps
                    val px = floor(x0 + (x1 - x0) * u + 0.5f).toInt(); val py = floor(y0 + (y1 - y0) * u + 0.5f).toInt()
                    if (px == lastX && py == lastY) continue
                    lastX = px; lastY = py
                    val t = (k - 1 + u) / max(1, n - 1)
                    var c = mix(shade(trailColour, 0.45f), trailColour, t)
                    if (heat > 0f) c = mix(c, TRAIL_HOT, heat)
                    if (t > 0.4f || dither(px, py) < t * 2.2f) f.set(px, py, c, (0.1f + 0.65f * t * t) * alpha)
                }
            }
        }
    }

    fun draw(f: AstroFrame, frame: Int) {
        val spr = sprite()
        if (heat > 0.02f) {  // a plasma sheath, brightest at the nose and swept back along the hull
            val r = length / 2f + 3
            val hx = cos(heading); val hy = sin(heading)
            val ri = r.toInt()
            for (oy in -ri - 1..ri + 1) for (ox in -ri - 1..ri + 1) {
                val d = hypot(ox.toFloat(), oy.toFloat())
                if (d > r) continue
                val front = (ox * hx + oy * hy) / r
                val g = heat * (1 - d / r) * (0.6f + 0.5f * front)
                if (g > dither(ox + frame, oy)) f.set(x + ox, y + oy, HOT[if (g > 0.8f) 0 else if (g > 0.45f) 1 else 2], min(1f, g) * 0.7f * max(alpha, 0.3f))
            }
        }
        for (i in spr.outlineX.indices) f.set(x + spr.outlineX[i], y + spr.outlineY[i], OUTLINE, 0.55f * alpha)  // keeps silhouettes readable
        for (i in spr.x.indices) {
            val k = spr.key[i]
            var c = if (k == 'e') when {
                state == State.DOCKED -> shade(trailColour, 0.35f)
                thrust > 0.3f -> if ((frame + x.toInt()) % 3 != 0) core else trailColour
                else -> trailColour
            } else {
                val base = colours[k] ?: colours.getValue('h')
                if (spr.y[i] > 0 && (k == 'h' || k == 'g' || k == 's')) shade(base, 0.78f) else base
            }
            if (heat > 0.02f) c = mix(c, HOT[1], heat * 0.6f)
            f.set(x + spr.x[i], y + spr.y[i], c, alpha)
        }
    }
}

/** The small craft around the station: arrivals from every edge, launches from the surface, docking and departures. */
internal class SmallTraffic(seed: Int, val station: AstroStation, val px: Float, val py: Float, val pr: Float, val w: Int, val h: Int,
                            val hulls: SmallHullSet = SmallHullSet(seed)) {
    private val rng = Random(seed)
    val ships = ArrayList<SmallShip>()
    private var nextSpawn = 0f
    private var serial = 0
    private var time = 0f
    private val mix: List<SmallHulls.Kind> = "FFHTSSSCCL".map { k -> SmallHulls.Kind.values().first { it.name[0] == k } }

    fun edgePoint(r: Random): Pair<Float, Float> = when (r.nextInt(4)) {
        0 -> -24f to r.range(30f, h - 30f)
        1 -> w + 24f to r.range(30f, h - 30f)
        2 -> r.range(-10f, w + 10f) to -24f
        else -> r.range(-10f, w + 10f) to h + 24f
    }

    private fun spawn() {
        serial++
        val kind = mix[rng.nextInt(mix.size)]
        val s = SmallShip(this, kind, 1000 + serial)
        val small = kind == SmallHulls.Kind.SHUTTLE || kind == SmallHulls.Kind.COURIER || kind == SmallHulls.Kind.LINER
        if (rng.nextFloat() < 0.22f && small) {  // launched from the surface
            val lat = rng.range(-0.7f, 0.7f)
            s.x = px - cos(lat) * (pr - 2); s.y = py + sin(lat) * (pr - 2)
            s.heading = PI.toFloat() + lat * 0.5f
            s.alpha = 0f; s.fade = 1f; s.speed = s.top * 0.4f
        } else {
            val (ex, ey) = edgePoint(rng)
            s.x = ex; s.y = ey
            s.heading = atan2(station.sy - ey, station.sx - ex) + rng.range(-0.4f, 0.4f)
            s.speed = s.top
        }
        val room = station.ports.any { it.owner == null && (it.big || !kind.big) }
        if (!room && (small || kind == SmallHulls.Kind.FREIGHTER) && rng.nextFloat() < 0.5f) {
            s.landSomewhere(0.6f)  // station is full: head down to the surface instead
        } else if ((kind == SmallHulls.Kind.COURIER && rng.nextFloat() < 0.5f) || !room) {
            var wx: Float; var wy: Float
            do {  // a waypoint in open space, clear of the station
                wx = w * rng.range(0.1f, 0.9f); wy = h * rng.range(0.15f, 0.85f)
            } while (hypot(wx - station.sx, (wy - station.sy) * 1.6f) <= 70f)
            s.route(wx, wy)
            val (ex, ey) = edgePoint(rng)
            s.route(ex, ey)
            s.state = SmallShip.State.EXIT
        } else {
            s.planDocking()
        }
        ships += s
    }

    fun step(dt: Float) {
        time += dt
        if (time >= nextSpawn && ships.count { it.state != SmallShip.State.DOCKED && it.state != SmallShip.State.UNDOCK } < 5) {
            spawn()
            nextSpawn = time + rng.range(2f, 5f)
        }
        for (s in ships) {
            s.step(dt)
            if (s.state == SmallShip.State.EXIT && (s.x < -40 || s.x > w + 40 || s.y < -40 || s.y > h + 40)) s.gone = true
        }
        for (s in ships) if (s.gone) s.port?.let { if (it.owner === s) it.owner = null }
        ships.removeAll { it.gone }
    }
}
