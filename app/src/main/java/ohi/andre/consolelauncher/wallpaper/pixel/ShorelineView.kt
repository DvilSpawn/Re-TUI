package ohi.andre.consolelauncher.wallpaper.pixel

import android.content.Context
import android.text.format.DateFormat
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperSettings
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A night shore seen from above: dark sand with grains that glint and shift, the time (or the date, or your own
 * words) written on it in dot-matrix letters, and waves rolling up from the bottom. The biggest waves wash over the
 * lower quarter of the writing, which fades where the water has been and comes back slowly once it pulls away.
 * ↑ ↓ ← → move the writing and its waves.
 */
class ShorelineView(context: Context) : PixelWallpaperView(context, 180f) {
    companion object {
        const val SCENE = "shoreline"
        const val OFFSET_STEP = 0.05f
        /** Grid pixels per font dot: a 3×3 square dot and a 1-pixel gap. */
        private const val PITCH = 4
        private const val ROWS = 7
        /** Empty dot rows between lines of writing. */
        private const val LINE_GAP = 3
        private const val MAX_LINES = 3
        const val TIME = "time"
        const val DATE = "date"
        const val CUSTOM = "custom"
        val MODES = listOf(TIME, DATE, CUSTOM)
        const val MAX_TEXT = 40
        // A 5×7 dot-matrix font: digits, capitals (lower case is drawn in capitals) and common punctuation.
        private val GLYPHS = mapOf(
            '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
            '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
            '3' to arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
            '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
            '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
            '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
            ':' to arrayOf("00", "11", "11", "00", "11", "11", "00"),
            'A' to arrayOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
            'B' to arrayOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
            'C' to arrayOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
            'D' to arrayOf("11100", "10010", "10001", "10001", "10001", "10010", "11100"),
            'E' to arrayOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
            'F' to arrayOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
            'G' to arrayOf("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
            'H' to arrayOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
            'I' to arrayOf("111", "010", "010", "010", "010", "010", "111"),
            'J' to arrayOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
            'K' to arrayOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
            'L' to arrayOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
            'M' to arrayOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
            'N' to arrayOf("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
            'O' to arrayOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
            'P' to arrayOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
            'Q' to arrayOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
            'R' to arrayOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
            'S' to arrayOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
            'T' to arrayOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
            'U' to arrayOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
            'V' to arrayOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
            'W' to arrayOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
            'X' to arrayOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
            'Y' to arrayOf("10001", "10001", "10001", "01010", "00100", "00100", "00100"),
            'Z' to arrayOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
            ' ' to arrayOf("000", "000", "000", "000", "000", "000", "000"),
            '.' to arrayOf("0", "0", "0", "0", "0", "0", "1"),
            ',' to arrayOf("00", "00", "00", "00", "01", "01", "10"),
            '!' to arrayOf("1", "1", "1", "1", "1", "0", "1"),
            '?' to arrayOf("01110", "10001", "00001", "00010", "00100", "00000", "00100"),
            '\'' to arrayOf("1", "1", "0", "0", "0", "0", "0"),
            '-' to arrayOf("000", "000", "000", "111", "000", "000", "000"),
            '+' to arrayOf("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
            '/' to arrayOf("00001", "00010", "00010", "00100", "01000", "01000", "10000"),
            '(' to arrayOf("001", "010", "100", "100", "100", "010", "001"),
            ')' to arrayOf("100", "010", "001", "001", "001", "010", "100"),
            '#' to arrayOf("01010", "01010", "11111", "01010", "11111", "01010", "01010"),
            '&' to arrayOf("01100", "10010", "10100", "01000", "10101", "10010", "01101"),
            '♥' to arrayOf("00000", "01010", "11111", "11111", "01110", "00100", "00000")
        )

        /** Writing laid out as dots: [cols] × [rows], true where there is ink. */
        class Dots(val cols: Int, val rows: Int, val ink: BooleanArray)

        /**
         * Lays [text] out in the dot font, wrapping at spaces to fit [maxCols] dot columns, up to three centred lines
         * (anything past that is left off). Characters the font has no glyph for are skipped.
         */
        fun layout(text: String, maxCols: Int): Dots {
            fun width(word: String) = word.sumOf { GLYPHS.getValue(it)[0].length } + (word.length - 1).coerceAtLeast(0)
            val clean = text.uppercase(Locale.ROOT).filter { it in GLYPHS }.trim().replace(Regex(" +"), " ")
            val lines = mutableListOf<String>()
            for (word in clean.split(' ').filter { it.isNotEmpty() }) {
                // A word too long for a whole line is broken where it fills one.
                val pieces = mutableListOf<String>()
                var rest = word
                while (width(rest) > maxCols && rest.length > 1) {
                    var cut = rest.length - 1
                    while (cut > 1 && width(rest.take(cut)) > maxCols) cut--
                    pieces.add(rest.take(cut)); rest = rest.drop(cut)
                }
                pieces.add(rest)
                for (piece in pieces) {
                    if (lines.isNotEmpty() && width(lines.last() + " " + piece) <= maxCols) lines[lines.lastIndex] = lines.last() + " " + piece
                    else lines.add(piece)
                }
            }
            val shown = lines.take(MAX_LINES).ifEmpty { listOf("") }
            val cols = shown.maxOf { width(it) }.coerceAtLeast(1)
            val rows = shown.size * ROWS + (shown.size - 1) * LINE_GAP
            val ink = BooleanArray(cols * rows)
            shown.forEachIndexed { line, words ->
                var col = (cols - width(words)) / 2
                val top = line * (ROWS + LINE_GAP)
                for (c in words) {
                    val glyph = GLYPHS.getValue(c)
                    for (row in 0 until ROWS) for (x in glyph[row].indices) if (glyph[row][x] == '1') ink[(top + row) * cols + col + x] = true
                    col += glyph[0].length + 1
                }
            }
            return Dots(cols, rows, ink)
        }
        /** Water takes the ink off fast; dry sand gives it back slowly. */
        private const val ERASE_PER_SECOND = 1.4f
        private const val REVEAL_PER_SECOND = 0.16f
    }

    var look = PixelLook.of(RetuiWallpaperSettings.pixelLook(context, SCENE), PixelLook.MONO)
        set(value) { field = value; palette = value.colors(PixelLook.themeInk()); invalidate() }
    /** Where the time sits, each from -0.5 to 0.5 (0 is centred, a little above the middle). */
    var offsetX = RetuiWallpaperSettings.shoreOffsetX(context)
        set(value) { field = value.coerceIn(-0.5f, 0.5f); invalidate() }
    var offsetY = RetuiWallpaperSettings.shoreOffsetY(context)
        set(value) { field = value.coerceIn(-0.5f, 0.5f); invalidate() }
    /** What is written: [TIME], [DATE] or [CUSTOM] ([customText]). */
    var mode = RetuiWallpaperSettings.shoreMode(context).takeIf { it in MODES } ?: TIME
        set(value) { field = value; minute = -1L; invalidate() }
    var customText = RetuiWallpaperSettings.shoreText(context)
        set(value) { field = value.take(MAX_TEXT); minute = -1L; invalidate() }

    private var palette = look.colors(PixelLook.themeInk())
    private val rng = Random(System.nanoTime())
    private var clock = 0f

    // The writing: its dots (true where a glyph has ink), and how much ink each dot still has.
    private var text = ""
    private var minute = -1L
    private var cols = 0
    private var rows = ROWS
    private var dots = BooleanArray(0)
    private var ink = FloatArray(0)
    private var tx = 0; private var ty = 0

    // Sand: grains that glint, and how wet each pixel still is after the water left it.
    private var grainX = IntArray(0); private var grainY = IntArray(0); private var grainPhase = FloatArray(0)
    private var wet = FloatArray(0)

    // The current wave: it surges from the rest line up to [reach] below the time's top, then pulls back. Both
    // follow the time, so moving it mid-wave carries the water along.
    private var rest = 0f
    private var reach = 0f
    private var waveTime = 0f
    private var surge = 2.2f
    private var recede = 3.4f
    private var pause = 1f
    private var level = 0f
    private var reachY = 0f
    // Its shape: the tongue that runs furthest up the beach lands at [peakX] (as a share of the width) and drifts
    // sideways as the wave runs, sometimes with a smaller second tongue beside it. Each wave picks its own.
    private var peakX = 0.5f
    private var peakDrift = 0f
    private var peakWidth = 0.3f
    private var lobeX = 0.5f
    private var lobeWidth = 0.2f
    private var lobeHeight = 0f
    private var ragged = 0f

        override fun onGrid(width: Int, height: Int) {
        val count = width * height / 90
        grainX = IntArray(count) { rng.nextInt(width) }
        grainY = IntArray(count) { rng.nextInt(height) }
        grainPhase = FloatArray(count) { rng.nextFloat() * 2f * PI.toFloat() }
        wet = FloatArray(width * height)
        minute = -1L
        updateText()
        place()
        level = rest
        nextWave()
    }

    private fun updateText() {
        val now = System.currentTimeMillis()
        if (now / 60_000L == minute && cols > 0) return
        minute = now / 60_000L
        val calendar = Calendar.getInstance()
        val next = when (mode) {
            DATE -> "%d %s".format(calendar.get(Calendar.DAY_OF_MONTH),
                calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH) ?: "")
            CUSTOM -> customText.ifBlank { "HELLO" }
            else -> {
                val hour = if (DateFormat.is24HourFormat(context)) calendar.get(Calendar.HOUR_OF_DAY)
                    else (calendar.get(Calendar.HOUR) + 11) % 12 + 1
                "%d:%02d".format(hour, calendar.get(Calendar.MINUTE))
            }
        }
        if (next == text && cols > 0) return
        text = next
        // Long writing wraps onto more lines; the dots stay the same size.
        val laid = layout(text, ((gw - 8 + 1) / PITCH).coerceAtLeast(5))
        // The ink stays where it was on the sand; only a change of size (12:59 to 1:00) starts it fresh.
        if (laid.cols != cols || laid.rows != rows) { cols = laid.cols; rows = laid.rows; ink = FloatArray(cols * rows) { 1f } }
        dots = laid.ink
    }

    /** Lay the time and the waterline out from the offsets: the waves always come up to the time, wherever it is. */
    private fun place() {
        val w = cols * PITCH - 1
        val h = rows * PITCH - 1
        tx = (gw * (0.5f + offsetX) - w / 2f).roundToInt().coerceIn(2, (gw - w - 2).coerceAtLeast(2))
        ty = (gh * (0.42f + offsetY * 0.8f) - h / 2f).roundToInt().coerceIn((gh * 0.06f).toInt(), (gh * 0.78f).toInt() - h)
        val bottom = ty + h
        rest = bottom + (gh - bottom) * 0.55f
    }

    private fun nextWave() {
        val h = rows * PITCH - 1
        val bottom = ty + h
        // Most waves wash into the digits, up to three quarters of the way down; the rest stop short on the sand.
        reach = if (rng.nextFloat() < 0.65f) h * (0.75f + rng.nextFloat() * 0.25f)
            else h + (rest - bottom) * (0.15f + rng.nextFloat() * 0.5f)
        val hitsTime = reach <= h
        val centre = (tx + cols * PITCH / 2f) / gw
        // A wave meant for the digits usually comes in in front of them; any wave can land anywhere along the shore.
        peakX = if (hitsTime && rng.nextFloat() < 0.7f) centre + (rng.nextFloat() - 0.5f) * 0.3f else 0.05f + rng.nextFloat() * 0.9f
        peakDrift = (rng.nextFloat() - 0.5f) * 0.3f
        peakWidth = 0.18f + rng.nextFloat() * 0.35f
        lobeHeight = if (rng.nextFloat() < 0.45f) 0.35f + rng.nextFloat() * 0.45f else 0f
        lobeX = peakX + (if (rng.nextBoolean()) 1f else -1f) * (0.25f + rng.nextFloat() * 0.3f)
        lobeWidth = 0.1f + rng.nextFloat() * 0.2f
        ragged = rng.nextFloat() * 100f
        surge = 1.8f + rng.nextFloat() * 0.9f
        recede = 3f + rng.nextFloat() * 1.2f
        pause = 0.4f + rng.nextFloat() * 1.6f
        waveTime = 0f
    }

    /**
     * Where the water's edge is at column [x]: [level] at the wave's peak, falling back towards the sea away from it,
     * with a ragged front. The shape grows with the wave, so the sea sits flat between waves.
     */
    private fun edge(x: Int): Float {
        val u = x / gw.toFloat()
        val run = (waveTime / (surge + recede)).coerceIn(0f, 1f)
        // Flat-topped, like a sheet of water running up the sand, rather than a smooth hump.
        fun tongue(at: Float, width: Float) = kotlin.math.abs((u - at) / width).let { exp(-it * it * it) }
        val bump = maxOf(tongue(peakX + peakDrift * run, peakWidth), lobeHeight * tongue(lobeX + peakDrift * run, lobeWidth))
        val grown = if (rest - reachY > 1f) ((rest - level) / (rest - reachY)).coerceIn(0f, 1f) else 0f
        return level + (1f - bump) * (rest - reachY + gh * 0.05f) * 0.55f * grown +
            grown * (3f * sin(x * 0.05f + ragged) + 1.8f * sin(x * 0.13f + ragged * 1.7f)) +
            1.4f * sin(x * 0.09f + clock * 0.8f) + 0.8f * sin(x * 0.23f - clock * 1.7f + 1.3f)
    }

    override fun step(seconds: Float) {
        clock += seconds
        updateText()
        place()
        waveTime += seconds
        val t = waveTime
        reachY = ty + reach
        level = when {
            t < surge -> { val p = t / surge; rest + (reachY - rest) * (1f - (1f - p) * (1f - p)) }
            t < surge + recede -> { val p = (t - surge) / recede; reachY + (rest - reachY) * p * p * (3f - 2f * p) }
            t < surge + recede + pause -> rest + sin((t - surge - recede) * 2f) * 1.5f
            else -> { nextWave(); rest }
        }
        val surging = t < surge
        // The dots the water covers lose ink; dry ones slowly get it back.
        for (row in 0 until rows) {
            val y = ty + row * PITCH + 1
            for (col in 0 until cols) {
                val i = row * cols + col
                ink[i] = if (edge(tx + col * PITCH + 1) < y) (ink[i] - ERASE_PER_SECOND * seconds * (if (surging) 1f else 0.6f)).coerceAtLeast(0f)
                    else (ink[i] + REVEAL_PER_SECOND * seconds).coerceAtMost(1f)
            }
        }
        val dry = 0.5f * seconds
        for (x in 0 until gw) {
            val from = edge(x).toInt().coerceIn(0, gh)
            for (y in 0 until from) { val i = y * gw + x; if (wet[i] > 0f) wet[i] = (wet[i] - dry).coerceAtLeast(0f) }
            for (y in from until gh) wet[y * gw + x] = 1f
        }
        // Now and then a grain shifts by a pixel, as if the sand were settling.
        val moves = (grainX.size * 0.2f * seconds).toInt() + if (rng.nextFloat() < grainX.size * 0.2f * seconds % 1f) 1 else 0
        repeat(moves) {
            val g = rng.nextInt(grainX.size)
            grainX[g] = (grainX[g] + rng.nextInt(-1, 2)).coerceIn(0, gw - 1)
            grainY[g] = (grainY[g] + rng.nextInt(-1, 2)).coerceIn(0, gh - 1)
        }
    }

    override fun render() {
        if (cols == 0) return
        place()
        val dark = palette[0]
        for (x in 0 until gw) {
            val e = edge(x)
            for (y in 0 until gh) {
                val depth = y - e
                val v = when {
                    depth < 0f -> wet[y * gw + x] * 0.05f
                    depth < 1.5f -> if (waveTime < surge) 1f else 0.75f
                    depth < 5f -> 0.5f * (0.5f + 0.5f * sin(x * 0.7f + y * 1.3f + clock * 3f)) * (0.5f + 0.5f * sin(x * 0.31f - clock * 2f))
                    // Swell lines rolling in towards the beach over a dark sea. They take the edge's shape, squeezed
                    // in where the water runs up and straightening out towards the bottom of the screen.
                    else -> {
                        val along = depth / (gh - e) * (gh - rest)
                        val swell = sin(along * 0.45f + clock * 2.2f + 0.8f * sin(x * 0.07f + clock * 0.3f))
                        0.03f + if (swell > 0.6f) (swell - 0.6f) * 1.1f else 0f
                    }
                }
                px[y * gw + x] = if (v <= 0f) dark else PixelDither.pick(v, x, y, palette)
            }
        }
        // Grains glint on dry sand only.
        for (g in grainX.indices) {
            val x = grainX[g]; val y = grainY[g]
            if (y >= edge(x)) continue
            val glint = 0.5f + 0.5f * sin(grainPhase[g] + clock * (0.6f + (g % 7) * 0.15f))
            if (glint > 0.55f) px[y * gw + x] = PixelDither.pick(glint, x, y, palette)
        }
        // The time, a dot at a time, where the sand is dry.
        for (row in 0 until rows) for (col in 0 until cols) {
            val i = row * cols + col
            if (!dots[i] || ink[i] <= 0f) continue
            val x0 = tx + col * PITCH; val y0 = ty + row * PITCH
            for (dy in 0..2) for (dx in 0..2) {
                val x = x0 + dx; val y = y0 + dy
                if (x !in 0 until gw || y !in 0 until gh || y >= edge(x)) continue
                px[y * gw + x] = PixelDither.pick(ink[i], x, y, palette)
            }
        }
    }

    override fun frameDelayMs() = 50L

    override fun loadSettings() {
        look = PixelLook.of(RetuiWallpaperSettings.pixelLook(context, SCENE), PixelLook.MONO)
        offsetX = RetuiWallpaperSettings.shoreOffsetX(context)
        offsetY = RetuiWallpaperSettings.shoreOffsetY(context)
        mode = RetuiWallpaperSettings.shoreMode(context).takeIf { it in MODES } ?: TIME
        customText = RetuiWallpaperSettings.shoreText(context)
    }

    override fun signatureColors() = palette.let { intArrayOf(it.last(), it[it.size / 2], it.first()) }
}
