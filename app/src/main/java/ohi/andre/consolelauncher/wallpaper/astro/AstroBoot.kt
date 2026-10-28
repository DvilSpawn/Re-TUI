package ohi.andre.consolelauncher.wallpaper.astro

import ohi.andre.consolelauncher.wallpaper.astro.AstroPixels.rgb
import kotlin.math.cos
import kotlin.math.sin

/** The boot screen shown while a world is prepared: a short terminal-style log with a progress bar. */
internal object AstroBoot {
    val STAGES = arrayOf("SHAPING PLANET", "PLOTTING NEBULA", "SHADING SURFACE", "RIGGING FLEET", "TRIMMING SMALL CRAFT", "SPINNING UP TRAFFIC")
    private val BG = rgb("#05060c")
    private val TITLE = rgb("#9af0ff")
    private val TEXT = rgb("#6ad8e0")
    private val DIM = rgb("#2a5a62")
    private val OK = rgb("#7affb0")
    private val BAR = rgb("#9af0ff")
    private val PLANET = rgb("#2a7898")

    private val FONT = mapOf(
        'A' to "010101111101101", 'B' to "110101110101110", 'C' to "011100100100011", 'D' to "110101101101110",
        'E' to "111100110100111", 'F' to "111100110100100", 'G' to "011100101101011", 'H' to "101101111101101",
        'I' to "111010010010111", 'J' to "001001001101010", 'K' to "101101110101101", 'L' to "100100100100111",
        'M' to "101111111101101", 'N' to "110101101101101", 'O' to "010101101101010", 'P' to "110101110100100",
        'Q' to "010101101110011", 'R' to "110101110101101", 'S' to "011100010001110", 'T' to "111010010010010",
        'U' to "101101101101111", 'V' to "101101101101010", 'W' to "101101111111101", 'X' to "101101010101101",
        'Y' to "101101010010010", 'Z' to "111001010100111", '0' to "111101101101111", '1' to "010110010010111",
        '2' to "110001010100111", '3' to "110001010001110", '4' to "101101111001001", '5' to "111100110001110",
        '6' to "011100111101111", '7' to "111001010010010", '8' to "111101111101111", '9' to "111101111001110",
        '-' to "000000111000000", ':' to "000010000010000", '.' to "000000000000010", '/' to "001001010100100",
        '>' to "100010001010100", '%' to "101001010100101", ' ' to "000000000000000",
    )

    fun text(f: AstroFrame, x: Int, y: Int, s: String, c: Int, scale: Int = 1) {
        var cx = x
        for (ch in s) {
            val g = FONT[ch] ?: FONT.getValue(' ')
            for (i in 0 until 15) if (g[i] == '1') {
                for (dy in 0 until scale) for (dx in 0 until scale) f.set(cx + (i % 3) * scale + dx, y + (i / 3) * scale + dy, c)
            }
            cx += 4 * scale
        }
    }

    fun width(s: String, scale: Int = 1) = s.length * 4 * scale - scale

    /** [stage] is the index being worked on, [progress] its fraction; stages before it are done. */
    fun draw(f: AstroFrame, t: Float, stage: Int, progress: Float, worldName: String) {
        java.util.Arrays.fill(f.px, BG)
        val cx = f.w / 2; val top = f.h / 2 - 70
        // a small planet with a moon circling it, so the screen is visibly alive
        for (dy in -9..9) for (dx in -9..9) if (dx * dx + dy * dy <= 81) f.set(cx + dx, top + dy, if (dx + dy < -4) TEXT else PLANET)
        val a = t * 2.4f
        f.set(cx + cos(a) * 16, top + sin(a) * 6, TITLE)
        val title = "ASTRO CHART"
        text(f, cx - width(title, 2) / 2, top + 20, title, TITLE, 2)
        val sub = "WORLD: ${worldName.uppercase()}"
        text(f, cx - width(sub) / 2, top + 36, sub, DIM)
        val left = cx - 62
        var y = top + 52
        STAGES.forEachIndexed { i, name ->
            val state = when {
                i < stage -> "OK"
                i == stage -> "${(progress * 100).toInt().coerceIn(0, 99)}%"
                else -> ""
            }
            val colour = if (i < stage) DIM else if (i == stage) TEXT else DIM
            text(f, left, y, "> $name", colour)
            val dots = (left + width("> $name") + 3 until left + 112 - width(state)).step(2)
            if (i <= stage) for (x in dots) f.set(x, y + 4, DIM)
            if (state.isNotEmpty()) text(f, left + 124 - width(state), y, state, if (i < stage) OK else TEXT)
            y += 8
        }
        // overall progress bar, segmented
        val done = ((stage + progress) / STAGES.size).coerceIn(0f, 1f)
        y += 6
        for (x in 0 until 124) {
            val lit = x < done * 124
            for (yy in 0 until 4) if (x % 4 != 3) f.set(left + x, y + yy, if (lit) BAR else DIM)
        }
        if (stage >= STAGES.size) {
            val online = "SYSTEM ONLINE"
            text(f, cx - width(online) / 2, y + 10, online, OK)
        } else if (((t * 2).toInt() and 1) == 0) {
            f.set(left + 125, y + 1, TEXT); f.set(left + 125, y + 2, TEXT)  // blinking cursor
        }
    }
}
