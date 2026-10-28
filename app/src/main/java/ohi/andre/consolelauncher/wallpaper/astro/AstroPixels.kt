package ohi.andre.consolelauncher.wallpaper.astro

import kotlin.math.floor

/** Colour, noise and dither helpers shared by the Astro Chart renderer. Colours are opaque ARGB ints. */
internal object AstroPixels {
    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun dither(x: Int, y: Int): Float = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f

    fun rgb(hex: String): Int = (hex.substring(1).toLong(16).toInt()) or OPAQUE
    fun rgb(r: Int, g: Int, b: Int): Int = OPAQUE or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** HSV (hue in 0..1) to an opaque colour, without android.graphics so the renderer runs in plain JVM tests. */
    fun hsv(h: Float, s: Float, v: Float): Int {
        val hh = (((h % 1f) + 1f) % 1f) * 6f
        val i = hh.toInt().coerceIn(0, 5); val f = hh - i
        val p = v * (1 - s); val q = v * (1 - s * f); val t = v * (1 - s * (1 - f))
        val (r, g, b) = when (i) { 0 -> Triple(v, t, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, t); 3 -> Triple(p, q, v); 4 -> Triple(t, p, v); else -> Triple(v, p, q) }
        return rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val ar = a shr 16 and 255; val ag = a shr 8 and 255; val ab = a and 255
        return rgb((ar + ((b shr 16 and 255) - ar) * t).toInt(), (ag + ((b shr 8 and 255) - ag) * t).toInt(), (ab + ((b and 255) - ab) * t).toInt())
    }

    fun shade(c: Int, f: Float): Int = rgb(((c shr 16 and 255) * f).toInt(), ((c shr 8 and 255) * f).toInt(), ((c and 255) * f).toInt())

    private fun lattice(ix: Int, iy: Int, seed: Int): Float {
        var h = ix * 374761393 + iy * 668265263 + seed * 2147483647
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0xffffffffL).toFloat() / 0xffffffffL.toFloat()
    }

    fun noise(x: Float, y: Float, seed: Int, wrap: Int = 0): Float {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        var fx = x - ix; var fy = y - iy
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
        val x0 = if (wrap > 0) Math.floorMod(ix, wrap) else ix
        val x1 = if (wrap > 0) Math.floorMod(ix + 1, wrap) else ix + 1
        val a = lattice(x0, iy, seed); val b = lattice(x1, iy, seed)
        val c = lattice(x0, iy + 1, seed); val d = lattice(x1, iy + 1, seed)
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy
    }

    fun fbm(x: Float, y: Float, seed: Int, wrap: Int = 0, octaves: Int = 4): Float {
        var total = 0f; var amp = 1f; var norm = 0f; var f = 1
        for (o in 0 until octaves) {
            total += noise(x * f, y * f, seed + o, if (wrap > 0) wrap * f else 0) * amp
            norm += amp; amp *= 0.5f; f *= 2
        }
        return total / norm
    }

    const val OPAQUE = -0x1000000
}

/** A low-resolution frame. Everything is painted here at chart-pixel size, then scaled up with nearest sampling. */
internal class AstroFrame(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun set(x: Float, y: Float, c: Int, alpha: Float = 1f) = set(floor(x + 0.5f).toInt(), floor(y + 0.5f).toInt(), c, alpha)

    fun set(x: Int, y: Int, c: Int, alpha: Float = 1f) {
        if (x < 0 || y < 0 || x >= w || y >= h || alpha <= 0f) return
        val i = y * w + x
        px[i] = if (alpha >= 1f) c else AstroPixels.mix(px[i], c, alpha)
    }
}
