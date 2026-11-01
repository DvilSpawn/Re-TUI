package ohi.andre.consolelauncher.tuils.asciifx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * A small terminal-effects engine for ASCII art, ported from the ideas in TerminalTextEffects (MIT,
 * github.com/ChrisBuilds/terminaltexteffects): every character of the art is an actor with scenes (timed
 * symbol/colour frames) and motion paths (eased waypoints), and each tick the actors are composed onto a
 * terminal-like grid. Rows count from the top; one tick is one frame at [TICKS_PER_SECOND].
 */

const val TICKS_PER_SECOND = 60

/** The art an effect starts from: rows of text and each cell's own colour (0 means [baseColor]). */
class AsciiArt(val lines: List<String>, private val colors: List<IntArray>, val baseColor: Int) {
    val rows = lines.size
    val cols = lines.maxOfOrNull { it.length } ?: 0
    fun symbol(row: Int, col: Int): Char = lines.getOrNull(row)?.getOrNull(col) ?: ' '
    fun color(row: Int, col: Int): Int = colors.getOrNull(row)?.getOrNull(col)?.takeIf { it != 0 } ?: baseColor
}

/** One frame: a grid of symbols and colours the size of the art. */
class AsciiGrid(val rows: Int, val cols: Int) {
    private val symbols = CharArray(rows * cols) { ' ' }
    private val colors = IntArray(rows * cols)

    fun clear() { symbols.fill(' '); colors.fill(0) }
    fun put(row: Int, col: Int, symbol: Char, color: Int) {
        if (row !in 0 until rows || col !in 0 until cols) return
        symbols[row * cols + col] = symbol
        colors[row * cols + col] = color
    }
    fun symbol(row: Int, col: Int): Char = if (row in 0 until rows && col in 0 until cols) symbols[row * cols + col] else ' '
    fun color(row: Int, col: Int): Int = if (row in 0 until rows && col in 0 until cols) colors[row * cols + col] else 0
}

typealias Ease = (Float) -> Float

object Easing {
    val linear: Ease = { it }
    val inOutQuad: Ease = { t -> if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2 }
    val inQuart: Ease = { t -> t.pow(4) }
    val outQuad: Ease = { t -> 1 - (1 - t) * (1 - t) }
    val inOutSine: Ease = { t -> (-(cos(PI * t) - 1) / 2).toFloat() }
    val inOutCirc: Ease = { t ->
        if (t < 0.5f) (1 - sqrt(1 - (2 * t).pow(2))) / 2 else (sqrt(1 - (-2 * t + 2).pow(2)) + 1) / 2
    }
}

object FxColor {
    fun mix(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        var out = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val a = (from ushr shift) and 0xFF
            val b = (to ushr shift) and 0xFF
            out = out or ((a + (b - a) * f).roundToInt() shl shift)
        }
        return out
    }

    /** Colours from stop to stop, [steps] per pair, ending on the last stop (TTE's gradient spectrum). */
    fun gradient(stops: List<Int>, steps: Int): List<Int> {
        if (stops.size < 2) return stops
        val out = ArrayList<Int>()
        for (i in 0 until stops.size - 1) for (s in 0 until steps) out += mix(stops[i], stops[i + 1], s / steps.toFloat())
        out += stops.last()
        return out
    }

    fun lighter(color: Int, amount: Float) = mix(color, color or 0x00FFFFFF, amount)
    fun darker(color: Int, amount: Float) = mix(color, color and 0xFF000000.toInt(), amount)
}

class FxFrame(val symbol: Char, val color: Int, val ticks: Int)

class FxScene(val frames: List<FxFrame>, val loop: Boolean = false) {
    companion object {
        /** Spreads [symbols] over [colors] (or the reverse), one frame per step (TTE's apply_gradient_to_symbols). */
        fun gradient(symbols: List<Char>, colors: List<Int>, ticks: Int, loop: Boolean = false): FxScene {
            val n = max(symbols.size, colors.size)
            return FxScene(List(n) { i -> FxFrame(symbols[i * symbols.size / n], colors[i * colors.size / n], ticks) }, loop)
        }
    }
}

/** Travel through [waypoints] (row, col) at [speed] cells per tick; [ease] shapes progress over the whole path. */
class FxPath(val waypoints: List<Pair<Float, Float>>, val speed: Float, val ease: Ease = Easing.linear)

/** One character of the art: where it belongs, what it shows now, and what it is doing. */
class FxChar(val row: Int, val col: Int, val symbol: Char, val color: Int) {
    var r = row.toFloat(); private set
    var c = col.toFloat(); private set
    var visible = false
    var layer = 0
    var shownSymbol = symbol; private set
    var shownColor = color; private set

    private var scene: FxScene? = null
    private var frameIndex = 0
    private var frameLeft = 0
    private var sceneEnd: (() -> Unit)? = null

    private var points: List<Pair<Float, Float>> = emptyList()
    private var lengths = FloatArray(0)
    private var pathLength = 0f
    private var travelled = 0f
    private var path: FxPath? = null
    private var pathEnd: (() -> Unit)? = null

    val busy get() = scene != null || path != null

    fun place(row: Float, col: Float) { r = row; c = col }

    fun look(symbol: Char, color: Int) { shownSymbol = symbol; shownColor = color }

    fun play(scene: FxScene, then: (() -> Unit)? = null) {
        if (scene.frames.isEmpty()) { then?.invoke(); return }
        this.scene = scene
        frameIndex = 0
        frameLeft = scene.frames[0].ticks
        sceneEnd = then
        scene.frames[0].let { look(it.symbol, it.color) }
    }

    fun move(path: FxPath, then: (() -> Unit)? = null) {
        points = listOf(r to c) + path.waypoints
        lengths = FloatArray(points.size - 1) { i ->
            hypot(points[i + 1].first - points[i].first, points[i + 1].second - points[i].second)
        }
        pathLength = lengths.sum()
        travelled = 0f
        this.path = path
        pathEnd = then
        if (pathLength <= 0f) finishPath()
    }

    /** Back home, showing its own symbol and colour. */
    fun settle() {
        scene = null; path = null; sceneEnd = null; pathEnd = null
        r = row.toFloat(); c = col.toFloat()
        look(symbol, color)
        visible = true
    }

    fun tick() {
        path?.let { p ->
            travelled += p.speed
            val t = min(1f, travelled / pathLength)
            var distance = p.ease(t) * pathLength
            var i = 0
            while (i < lengths.size - 1 && distance > lengths[i]) { distance -= lengths[i]; i++ }
            val f = if (lengths[i] > 0f) (distance / lengths[i]).coerceIn(0f, 1f) else 1f
            r = points[i].first + (points[i + 1].first - points[i].first) * f
            c = points[i].second + (points[i + 1].second - points[i].second) * f
            if (t >= 1f) finishPath()
        }
        scene?.let { s ->
            if (--frameLeft > 0) return
            frameIndex++
            if (frameIndex >= s.frames.size) {
                if (s.loop) frameIndex = 0
                else {
                    scene = null
                    sceneEnd.also { sceneEnd = null }?.invoke()
                    return
                }
            }
            frameLeft = s.frames[frameIndex].ticks
            s.frames[frameIndex].let { look(it.symbol, it.color) }
        }
    }

    private fun finishPath() {
        points.lastOrNull()?.let { (row, col) -> r = row; c = col }
        path = null
        pathEnd.also { pathEnd = null }?.invoke()
    }
}

/** An effect over one piece of art. [step] advances a tick; once it returns false the art is complete. */
abstract class AsciiEffect(val art: AsciiArt) {
    val rows = art.rows
    val cols = art.cols
    val chars: List<FxChar> = buildList {
        for (row in 0 until art.rows) for (col in 0 until art.cols) {
            val symbol = art.symbol(row, col)
            if (!symbol.isWhitespace()) add(FxChar(row, col, symbol, art.color(row, col)))
        }
    }
    private val active = LinkedHashSet<FxChar>()
    protected val random = kotlin.random.Random(System.nanoTime())
    private var ordered: List<FxChar> = chars

    /** Starts characters this tick; returns true while more are still waiting to start. */
    protected abstract fun advance(): Boolean

    protected fun start(char: FxChar) { char.visible = true; active += char }

    fun step(): Boolean {
        val more = advance()
        // Characters started from a callback during the tick join next tick.
        for (char in active.toList()) char.tick()
        active.removeAll { !it.busy }
        return more || active.isNotEmpty()
    }

    fun finish() { active.clear(); chars.forEach { it.settle() } }

    fun compose(grid: AsciiGrid) {
        grid.clear()
        if (ordered.zipWithNext().any { (a, b) -> a.layer > b.layer }) ordered = chars.sortedBy { it.layer }
        for (char in ordered) if (char.visible) grid.put(char.r.roundToInt(), char.c.roundToInt(), char.shownSymbol, char.shownColor)
    }

    // A theme-led palette: light accents and dark shades of each character's own colour.
    protected fun accent(char: FxChar) = FxColor.lighter(char.color, 0.65f)
}
