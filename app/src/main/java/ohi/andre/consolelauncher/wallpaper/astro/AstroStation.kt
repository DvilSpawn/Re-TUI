package ohi.andre.consolelauncher.wallpaper.astro

import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.dither
import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.rgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A docking collar. [angle] points the way a ship backs out. */
internal class Port(val x: Float, val y: Float, val angle: Float, val big: Boolean) {
    var owner: SmallShip? = null
}

/** The big station at the centre of the screen: hub, spinning habitat ring, spine, solar wings and six collars. */
internal class AstroStation(val sx: Float, val sy: Float) {
    companion object {
        /** The keep-out ellipse ships route around. */
        const val KEEP_RX = 58f
        const val KEEP_RY = 30f
        private val STEEL = arrayOf("#eef0ff", "#b0b4d0", "#6a6e90", "#33364e").map(::rgb).toIntArray()
        private val PANEL = arrayOf("#7a8aff", "#3a44a8", "#1e2260").map(::rgb).toIntArray()
        private val CYAN = rgb("#9affff")
        private val AMBER = rgb("#ffcf6a")
        private val GREEN = rgb("#7affb0")
        private val PINK = rgb("#ff5a8a")
        private val WINDOW = rgb("#ffe08a")
        private val WHITE = rgb("#ffffff")
        private const val RING_STEPS = 180

        fun lit(nx: Float, ny: Float): Float {
            val nz = sqrt(max(0f, 1 - nx * nx - ny * ny))
            return nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        }

        fun tone(ramp: IntArray, l: Float, x: Int, y: Int): Int {
            val v = l + (dither(x, y) - 0.5f) * 0.3f
            return if (v > 0.75f) ramp[0] else if (v > 0.4f) ramp[1] else if (v > 0.05f) ramp[2] else ramp[3]
        }
    }

    val ports = listOf(
        Port(sx - 47, sy, PI.toFloat(), true), Port(sx + 47, sy, 0f, true),
        Port(sx, sy - 31, -PI.toFloat() / 2, false), Port(sx, sy + 31, PI.toFloat() / 2, false),
        Port(sx - 30, sy - 17, -PI.toFloat() / 2, false), Port(sx + 30, sy + 17, PI.toFloat() / 2, false),
    )
    private val cx = sx.toInt()
    private val cy = sy.toInt()

    fun draw(f: AstroFrame, t: Float) {
        // solar wings on masts above and below each end of the spine
        for (wx in intArrayOf(-30, 30)) for (side in intArrayOf(-1, 1)) {
            for (j in 4..14) for (i in -4..4) {
                val grid = i == -4 || i == 0 || i == 4 || j == 4 || j == 9 || j == 14
                f.set(cx + wx + i, cy + side * j, if (grid) PANEL[2] else if (Math.floorMod(i + j + (t * 2).toInt(), 9) == 0) PANEL[0] else PANEL[1])
            }
            for (j in 1..3) f.set(cx + wx, cy + side * j, STEEL[2])
        }
        // spine truss, with running lights chasing out toward both docking ends
        val chase = (t * 16).toInt()
        for (i in -44..44) for (j in -2..2) {
            var c = when {
                abs(j) == 2 -> if (j < 0) STEEL[1] else STEEL[3]
                Math.floorMod(i + j, 4) == 0 -> STEEL[2]
                j != 0 -> STEEL[3]
                else -> STEEL[2]
            }
            if (abs(j) == 2 && abs(i) > 10 && Math.floorMod(abs(i) - chase, 14) == 0) c = CYAN
            f.set(cx + i, cy + j, c)
        }
        // docking collars, their status lights, and guide lights while a ship lines up
        for (p in ports) {
            val x0 = p.x.toInt(); val y0 = p.y.toInt()
            for (dy in -2..2) for (dx in -2..2) if (abs(dx) + abs(dy) <= 3) f.set(x0 + dx, y0 + dy, tone(STEEL, lit(dx / 3f, dy / 3f), x0 + dx, y0 + dy))
            val owner = p.owner
            if (owner != null && owner.state == SmallShip.State.APPROACH) {
                val nx = -sin(p.angle); val ny = cos(p.angle)
                for (side in intArrayOf(-1, 1)) for (d in intArrayOf(3, 5, 7)) {
                    if (Math.floorMod((t * 10 - d).toInt(), 3) == 0)
                        f.set(p.x + nx * side * 4 + cos(p.angle) * d, p.y + ny * side * 4 + sin(p.angle) * d, AMBER)
                }
            }
            f.set(x0, y0, when {
                owner != null -> if ((t * 2).toInt() % 2 == 1) GREEN else AMBER
                Math.floorMod((t * 1.3f + p.x).toInt(), 3) == 0 -> PINK
                else -> STEEL[3]
            })
        }
        // masts out to the top and bottom collars
        for (p in ports.subList(2, 4)) {
            val steps = hypot(p.x - sx, p.y - sy).toInt()
            for (k in 10 until steps - 2) {
                val x = sx + (p.x - sx) * k / steps; val y = sy + (p.y - sy) * k / steps
                f.set(x, y, STEEL[2]); f.set(x + 1, y, STEEL[3])
            }
        }
        val spin = t * 0.5f
        habitatRing(f, spin, back = true)
        for (s in 0 until 4) {  // spokes
            val a = spin + s * PI.toFloat() / 2
            for (r in 8 until 24) f.set(sx + cos(a) * r, sy + sin(a) * r * 9 / 24, STEEL[2])
        }
        for (dy in -9..9) for (dx in -9..9) {  // hub
            if (dx * dx + dy * dy > 85) continue
            var c = tone(STEEL, lit(dx / 9.5f, dy / 9.5f), cx + dx, cy + dy)
            if ((dy == -3 || dy == 3) && abs(dx) < 7 && Math.floorMod(dx + (t * 3).toInt(), 4) == 0) c = WINDOW
            f.set(cx + dx, cy + dy, c)
        }
        val core = if ((t * 4).toInt() % 6 != 0) CYAN else WHITE
        for (dy in -1..0) for (dx in -1..1) f.set(cx + dx, cy + dy, core)
        habitatRing(f, spin, back = false)
        if ((t * 1.5f).toInt() % 2 == 0) f.set(cx, cy - 34, PINK)  // beacon on the top mast
    }

    /** The tilted habitat ring, drawn in two halves so the hub sits between them. */
    private fun habitatRing(f: AstroFrame, spin: Float, back: Boolean) {
        val eighth = PI.toFloat() / 4
        for (k in 0 until RING_STEPS) {
            val a = k / RING_STEPS.toFloat() * 2 * PI.toFloat()
            val y = sin(a) * 9
            if ((y < 0) != back) continue
            val x = cos(a) * 24
            for (wv in 0..1) {
                val px = (sx + x + 0.5f).toInt(); val py = (sy + y + wv + 0.5f).toInt()
                var c = tone(STEEL, lit(cos(a) * 0.7f, sin(a) * 0.7f - 0.2f), px, py)
                val seg = ((a + spin) % eighth + eighth) % eighth
                if (seg < 0.06f && wv == 0) c = STEEL[3]
                else if (wv == 1 && Math.floorMod(((a + spin) * 18).toInt(), 3) == 0) c = WINDOW
                f.set(px, py, c)
            }
        }
    }
}
