package ohi.andre.consolelauncher.tuils.asciifx

import kotlin.math.hypot
import kotlin.math.roundToInt

/** The effects ASCII art can play when Home appears, by setting name. Each follows its TerminalTextEffects namesake. */
object AsciiEffects {
    const val NONE = "none"
    val NAMES = listOf(NONE, "print", "decrypt", "slide", "wipe", "rain", "waves", "beams", "burn", "middleout", "colorshift", "glitch")
    val SPEEDS = listOf("slow", "normal", "fast")
    fun speed(name: String?): Float = when (name?.lowercase()) { "slow" -> 0.5f; "fast" -> 2f; else -> 1f }

    // Very large art would cost more per frame than an intro is worth.
    const val MAX_CHARACTERS = 4000

    fun create(name: String?, art: AsciiArt): AsciiEffect? {
        if (art.rows == 0 || art.cols == 0) return null
        val effect = when (name?.lowercase()) {
            "print" -> PrintEffect(art)
            "decrypt" -> DecryptEffect(art)
            "slide" -> SlideEffect(art)
            "wipe" -> WipeEffect(art)
            "rain" -> RainEffect(art)
            "waves" -> WavesEffect(art)
            "beams" -> BeamsEffect(art)
            "burn" -> BurnEffect(art)
            "middleout" -> MiddleOutEffect(art)
            "colorshift" -> ColorShiftEffect(art)
            "glitch" -> GlitchEffect(art)
            else -> null
        } ?: return null
        return effect.takeIf { it.chars.size in 1..MAX_CHARACTERS }
    }
}

private val BLOCK_FADE = listOf('█', '▓', '▒', '░')

/** Types the art row by row behind a block cursor that cools into each character's colour. */
private class PrintEffect(art: AsciiArt) : AsciiEffect(art) {
    private val byCell = chars.associateBy { it.row to it.col }
    private val rowEnds = (0 until rows).map { row -> chars.filter { it.row == row }.maxOfOrNull { it.col } ?: -1 }
    private var row = 0
    private var col = 0

    override fun advance(): Boolean {
        repeat(SPEED) {
            while (row < rows && col > rowEnds[row]) { row++; col = 0 }
            if (row >= rows) return false
            byCell[row to col]?.let { char ->
                char.play(FxScene.gradient(BLOCK_FADE + char.symbol, FxColor.gradient(listOf(accent(char), char.color), 4), 3))
                start(char)
            }
            col++
        }
        return true
    }

    companion object { const val SPEED = 2 }
}

/** Cipher glyphs type in, scramble fast then slowly, and resolve one by one into the art. */
private class DecryptEffect(art: AsciiArt) : AsciiEffect(art) {
    private val cipher: List<Char> = ((33..126) + (0x2588..0x259F) + (0x2500..0x257F)).map { it.toChar() }
    private val pending = ArrayDeque(chars)
    private var decrypting = false

    private fun cipherColor(char: FxChar) = listOf(FxColor.darker(char.color, 0.45f), FxColor.darker(char.color, 0.2f), accent(char)).random(random)

    override fun advance(): Boolean {
        if (!decrypting) {
            if (pending.isNotEmpty()) {
                if (random.nextInt(100) < 75) repeat(TYPING_SPEED) {
                    val char = pending.removeFirstOrNull() ?: return@repeat
                    val color = cipherColor(char)
                    char.play(FxScene(BLOCK_FADE.map { FxFrame(it, color, 2) } + FxFrame(cipher.random(random), color, 1)))
                    start(char)
                }
                return true
            }
            decrypting = true
            for (char in chars) {
                val color = cipherColor(char)
                val fast = FxScene(List(40) { FxFrame(cipher.random(random), color, 2) })
                val slow = FxScene(List(random.nextInt(1, 9)) {
                    FxFrame(cipher.random(random), color, if (random.nextInt(100) < 30) random.nextInt(20, 40) else random.nextInt(3, 6))
                })
                val found = FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(FxColor.lighter(char.color, 0.9f), char.color), 10), 5)
                char.play(fast) { char.play(slow) { char.play(found) } }
                start(char)
            }
            return true
        }
        return false
    }

    companion object { const val TYPING_SPEED = 4 }
}

/** Rows slide in from the left, one character after another, cooling from a light accent. */
private class SlideEffect(art: AsciiArt) : AsciiEffect(art) {
    private val groups = (0 until rows).map { row -> ArrayDeque(chars.filter { it.row == row }.sortedByDescending { it.col }) }
        .filter { it.isNotEmpty() }
    private var wait = 0

    init { chars.forEach { it.place(it.row.toFloat(), -1f) } }

    override fun advance(): Boolean {
        if (wait-- > 0) return groups.any { it.isNotEmpty() }
        wait = GAP
        for (group in groups) {
            val char = group.removeFirstOrNull() ?: continue
            char.play(FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(accent(char), char.color), 12), 4))
            char.move(FxPath(listOf(char.row.toFloat() to char.col.toFloat()), 0.8f, Easing.inOutQuad))
            start(char)
        }
        return groups.any { it.isNotEmpty() }
    }

    companion object { const val GAP = 2 }
}

/** A diagonal wipe from the top left, eased so it gathers pace and then settles. */
private class WipeEffect(art: AsciiArt) : AsciiEffect(art) {
    private val groups = chars.groupBy { it.row + it.col }.toSortedMap().values.toList()
    private var stepIndex = 0
    private var shown = 0

    override fun advance(): Boolean {
        if (shown >= groups.size) return false
        stepIndex++
        val target = (Easing.inOutCirc((stepIndex / groups.size.toFloat()).coerceAtMost(1f)) * groups.size).roundToInt().coerceAtMost(groups.size)
        while (shown < target) {
            for (char in groups[shown]) {
                char.play(FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(accent(char), char.color), 12), 3))
                start(char)
            }
            shown++
        }
        return shown < groups.size
    }
}

/** Drops fall from the top, bottom row first, and fade into the art where they land. */
private class RainEffect(art: AsciiArt) : AsciiEffect(art) {
    private val rowsLeft = chars.groupBy { it.row }.toSortedMap(compareByDescending { it }).values.map { it.toMutableList() }.toMutableList()
    private val pending = mutableListOf<FxChar>()
    private val drops = listOf('o', '.', ',', '*', '|')

    override fun advance(): Boolean {
        if (pending.isEmpty() && rowsLeft.isNotEmpty()) pending += rowsLeft.removeAt(0)
        repeat(random.nextInt(1, 3)) {
            if (pending.isEmpty()) return@repeat
            val char = pending.removeAt(random.nextInt(pending.size))
            val drop = FxColor.mix(FxColor.darker(char.color, 0.5f), accent(char), random.nextFloat())
            char.place(0f, char.col.toFloat())
            char.play(FxScene(listOf(FxFrame(drops.random(random), drop, 1))))
            char.move(FxPath(listOf(char.row.toFloat() to char.col.toFloat()), random.nextFloat() * 0.24f + 0.33f, Easing.inQuart)) {
                char.play(FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(drop, char.color), 7), 3))
            }
            start(char)
        }
        return pending.isNotEmpty() || rowsLeft.isNotEmpty()
    }
}

/** Waves of rising and falling blocks ripple out from the centre, then each cell settles into the art. */
private class WavesEffect(art: AsciiArt) : AsciiEffect(art) {
    private val rings = chars.groupBy {
        // Cells are about twice as tall as wide, so rows count double for a round ripple.
        hypot((it.col - cols / 2f).toDouble(), ((it.row - rows / 2f) * 2).toDouble()).roundToInt()
    }.toSortedMap().values.toList()
    private val symbols = "▁▂▃▄▅▆▇█▇▆▅▄▃▂▁".toList()
    private var ring = 0

    override fun advance(): Boolean {
        rings.getOrNull(ring++)?.forEach { char ->
            val wave = FxColor.gradient(listOf(accent(char), char.color, FxColor.darker(char.color, 0.4f), char.color, accent(char)), 6)
            val frames = List(WAVES) { FxScene.gradient(symbols, wave, 2).frames }.flatten() +
                FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(wave.last(), char.color), 12), 2).frames
            char.play(FxScene(frames))
            start(char)
        }
        return ring < rings.size
    }

    companion object { const val WAVES = 3 }
}

/** Light beams sweep along rows and down columns, leaving the art dim; a diagonal wipe then brings it to full colour. */
private class BeamsEffect(art: AsciiArt) : AsciiEffect(art) {
    private class Beam(val chars: ArrayDeque<FxChar>, val speed: Float, val row: Boolean) { var counter = 0f }
    private val pending = ArrayDeque(
        ((0 until rows).map { r -> chars.filter { it.row == r }.sortedBy { it.col } to true } +
            (0 until cols).map { c -> chars.filter { it.col == c }.sortedBy { it.row } to false })
            .filter { it.first.isNotEmpty() }.shuffled(random)
    )
    private val beams = mutableListOf<Beam>()
    private var wait = 0
    private var wiping = false
    private val diagonals = chars.groupBy { it.row + it.col }.toSortedMap().values.toList()
    private var wiped = 0
    private var wipeStep = 0

    private fun dim(char: FxChar) = FxColor.darker(char.color, 0.6f)

    override fun advance(): Boolean {
        if (!wiping) {
            if (wait-- <= 0 && pending.isNotEmpty()) {
                val (line, row) = pending.removeFirst()
                val ordered = if (random.nextBoolean()) line else line.reversed()
                beams += Beam(ArrayDeque(ordered), if (row) random.nextInt(10, 26) / 10f else random.nextInt(6, 13) / 10f, row)
                wait = BEAM_GAP
            }
            for (beam in beams) {
                beam.counter += beam.speed
                while (beam.counter >= 1f && beam.chars.isNotEmpty()) {
                    beam.counter -= 1f
                    val char = beam.chars.removeFirst()
                    val symbols = if (beam.row) listOf('▂', '▁', '_') else listOf('▌', '▍', '▎', '▏')
                    val colors = FxColor.gradient(listOf(FxColor.lighter(char.color, 0.95f), accent(char), dim(char)), 4)
                    char.play(FxScene(FxScene.gradient(symbols, colors, 2).frames + FxFrame(char.symbol, dim(char), 1)))
                    start(char)
                }
            }
            beams.removeAll { it.chars.isEmpty() }
            if (pending.isEmpty() && beams.isEmpty()) wiping = true
            return true
        }
        val target = ((Easing.inOutCirc((++wipeStep / diagonals.size.toFloat()).coerceAtMost(1f))) * diagonals.size).roundToInt()
        while (wiped < target) {
            for (char in diagonals[wiped]) {
                char.play(FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(dim(char), FxColor.lighter(char.color, 0.8f), char.color), 6), 3))
                start(char)
            }
            wiped++
        }
        return wiped < diagonals.size
    }

    companion object { const val BEAM_GAP = 4 }
}

/** The art sits dim, then catches fire from one point; flames spread outward and burn each character into colour. */
private class BurnEffect(art: AsciiArt) : AsciiEffect(art) {
    private val order: ArrayDeque<FxChar>
    private val fire = listOf('\'', '.', '▖', '▙', '█', '▜', '▀', '▝', '.')

    init {
        val origin = chars[random.nextInt(chars.size)]
        // Spread by distance from where it caught, with a little noise so the edge flickers rather than rings.
        // Each key is drawn once: a sort that re-rolls the random part per comparison breaks its contract and throws.
        order = ArrayDeque(chars.map { it to hypot((it.col - origin.col).toDouble(), ((it.row - origin.row) * 2).toDouble()) + random.nextDouble() * 3 }
            .sortedBy { it.second }.map { it.first })
        chars.forEach { it.visible = true; it.look(it.symbol, FxColor.darker(it.color, 0.75f)) }
    }

    override fun advance(): Boolean {
        repeat(maxOf(1, chars.size / 60)) {
            val char = order.removeFirstOrNull() ?: return false
            val flame = FxColor.gradient(listOf(FxColor.lighter(char.color, 0.95f), accent(char), char.color, FxColor.darker(char.color, 0.5f)), 3)
            char.play(FxScene(FxScene.gradient(fire, flame, 3).frames +
                FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(FxColor.darker(char.color, 0.5f), char.color), 8), 3).frames))
            start(char)
        }
        return order.isNotEmpty()
    }
}

/** Everything starts as one point in the middle, spreads into a line across the centre, then opens out into the art. */
private class MiddleOutEffect(art: AsciiArt) : AsciiEffect(art) {
    private val middleRow = (rows - 1) / 2f
    private var arrived = 0
    private var phase = 0

    init { chars.forEach { it.place(middleRow, (cols - 1) / 2f) } }

    override fun advance(): Boolean {
        when (phase) {
            0 -> {
                for (char in chars) {
                    char.look(char.symbol, accent(char))
                    char.move(FxPath(listOf(middleRow to char.col.toFloat()), 0.35f, Easing.inOutSine)) { arrived++ }
                    start(char)
                }
                phase = 1
            }
            1 -> if (arrived == chars.size) {
                for (char in chars) {
                    char.move(FxPath(listOf(char.row.toFloat() to char.col.toFloat()), 0.1f, Easing.inOutSine))
                    char.play(FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(accent(char), char.color), 10), 4))
                    start(char)
                }
                phase = 2
            }
        }
        return phase < 2
    }
}

/** A band of colour travels diagonally across the art a few times, then the characters settle into their own colour. */
private class ColorShiftEffect(art: AsciiArt) : AsciiEffect(art) {
    private var started = false

    override fun advance(): Boolean {
        if (started) return false
        started = true
        for (char in chars) {
            val spectrum = FxColor.gradient(listOf(char.color, accent(char), FxColor.darker(char.color, 0.55f), char.color), 8)
            // Offset by position so the band runs across the art instead of everything pulsing together.
            val shift = ((char.row + char.col) / (rows + cols).coerceAtLeast(1).toFloat() * spectrum.size).roundToInt()
            val cycle = spectrum.drop(shift) + spectrum.take(shift)
            val frames = List(CYCLES) { cycle }.flatten().map { FxFrame(char.symbol, it, 2) } +
                FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(cycle.last(), char.color), 8), 2).frames
            char.play(FxScene(frames))
            start(char)
        }
        return false
    }

    companion object { const val CYCLES = 3 }
}

/** The art shakes and flickers, bursts apart across the box, then snaps back together (TTE's unstable). */
private class GlitchEffect(art: AsciiArt) : AsciiEffect(art) {
    private var tick = 0
    private val cipher = "!@#$%&*<>/\\|=+?~^".toList()
    private val scattered: List<Pair<Float, Float>>

    init {
        val cells = (0 until rows).flatMap { r -> (0 until cols).map { c -> r.toFloat() to c.toFloat() } }.shuffled(random)
        scattered = chars.indices.map { cells[it % cells.size] }
        chars.forEach { start(it) }
    }

    override fun advance(): Boolean {
        tick++
        when {
            tick <= RUMBLE -> {
                // Rumble: small jumps and glyph flicker, growing towards the burst.
                val intensity = tick / RUMBLE.toFloat()
                for (char in chars) {
                    if (tick % 3 == 0) {
                        val jump = random.nextFloat() < intensity * 0.6f
                        char.place(char.row + if (jump) random.nextInt(-1, 2).toFloat() else 0f, char.col + if (jump) random.nextInt(-1, 2).toFloat() else 0f)
                    }
                    if (random.nextFloat() < intensity * 0.25f) char.look(cipher.random(random), accent(char))
                    else char.look(char.symbol, FxColor.mix(char.color, accent(char), intensity))
                }
            }
            tick == RUMBLE + 1 -> chars.forEachIndexed { i, char ->
                char.move(FxPath(listOf(scattered[i]), 1.2f, Easing.outQuad))
                start(char)
            }
            tick == RUMBLE + HOLD -> chars.forEach { char ->
                char.move(FxPath(listOf(char.row.toFloat() to char.col.toFloat()), 0.5f, Easing.inOutQuad))
                char.play(FxScene.gradient(listOf(char.symbol), FxColor.gradient(listOf(accent(char), char.color), 8), 4))
                start(char)
            }
        }
        return tick < RUMBLE + HOLD
    }

    companion object {
        const val RUMBLE = 60
        const val HOLD = 45
    }
}
