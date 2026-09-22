package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal fun movedBeyondTouchSlop(
    downX: Float,
    downY: Float,
    x: Float,
    y: Float,
    touchSlop: Float
): Boolean {
    val dx = x - downX
    val dy = y - downY
    return dx * dx + dy * dy > touchSlop * touchSlop
}

/** Sparse Omarchy-style pixel field with touch-only ripples. */
class PixelDreamView(context: Context) : View(context) {
    companion object {
        const val SCENE = "pixel dream"
        const val CUSTOM = PixelDreamThemes.CUSTOM_ID
        val PALETTE_NAMES = PixelDreamThemes.ids

        private const val NOISE_SIZE = 128
        private const val CELLS_PER_NOISE = 9f
        private const val FIELD_DENSITY = 0.3f
        private const val BANDS = 32
        private const val SPECTRUM_REACH = 0.92f
        private const val SPECTRUM_DENSITY = 0.7f
        private const val SPECTRUM_HEAT = 0.5f
        private const val RIPPLE_LIFE_MS = 1200f
        private const val RIPPLE_FROM = 1.5f
        private const val RIPPLE_GROWTH = 5.5f
        private const val MAX_RIPPLES = 2
        private val BAYER = intArrayOf(
            0, 32, 8, 40, 2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37,
            63, 31, 55, 23, 61, 29, 53, 21
        )
    }

    private data class Ripple(val x: Float, val y: Float, val born: Long, val charge: Float)
    private data class ActiveRipple(
        val x: Float,
        val y: Float,
        val radius: Float,
        val innerSquared: Float,
        val outerSquared: Float,
        val strength: Float
    )
    private data class Touch(val downX: Float, val downY: Float, val start: Long, val dragged: Boolean = false)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val noise = buildNoise(0x9ECE6A)
    private val jitter = buildJitter(0x0A1F14)
    private val ripples = ArrayDeque<Ripple>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val visualizerLevels = FloatArray(BANDS)
    private val visualizerTargets = FloatArray(BANDS)
    private var touch: Touch? = null
    private var backgroundColor = Color.rgb(14, 14, 20)
    private var dimColor = Color.rgb(57, 72, 46)
    private var midColor = Color.rgb(103, 133, 73)
    private var litColor = Color.rgb(158, 206, 106)
    private var hoverColor = Color.rgb(187, 221, 151)
    private var crestColor = Color.rgb(218, 236, 198)
    private var seed = 0
    var paletteName = PixelDreamThemes.DEFAULT_ID
        private set
    private var startedAt = SystemClock.uptimeMillis()
    private var mediaPlaying = false
    private var interactionEnabled = true
    private var visualizerLastUpdate = 0L
    private var nextVisualizerPulse = 0L
    private var visualizerRandom = 0x51F15EED

    fun loadPosition() {
        paletteName = RetuiWallpaperSettings.pixelDreamPalette(context)
        seed = RetuiWallpaperSettings.pixelDreamSeed(context)
        visualizerRandom = seed xor 0x51F15EED
        when (paletteName) {
            CUSTOM -> setColors(
                RetuiWallpaperSettings.pixelDreamBackground(context),
                RetuiWallpaperSettings.pixelDreamDim(context),
                RetuiWallpaperSettings.pixelDreamMid(context),
                RetuiWallpaperSettings.pixelDreamLit(context),
                RetuiWallpaperSettings.pixelDreamHover(context),
                RetuiWallpaperSettings.pixelDreamCrest(context)
            )
            else -> setPalette(paletteName)
        }
    }

    fun setPalette(name: String) {
        when (name) {
            CUSTOM -> return
            else -> {
                val theme = PixelDreamThemes.find(name) ?: return
                setColors(theme.fieldBg, theme.fieldDim, theme.fieldMid, theme.fieldLit, theme.fieldHover, theme.fieldCrest)
            }
        }
        paletteName = name
        invalidate()
    }

    fun setCustomColor(index: Int, color: Int) {
        when (index) {
            0 -> backgroundColor = color.opaque()
            1 -> dimColor = color
            2 -> midColor = color
            3 -> litColor = color
            4 -> hoverColor = color
            5 -> crestColor = color
            else -> return
        }
        paletteName = CUSTOM
        invalidate()
    }

    fun color(index: Int): Int = when (index) {
        0 -> backgroundColor
        1 -> dimColor
        2 -> midColor
        3 -> litColor
        4 -> hoverColor
        else -> crestColor
    }

    fun currentSeed(): Int = seed

    fun advance() {
        val now = SystemClock.uptimeMillis()
        if (mediaPlaying) {
            if (visualizerLastUpdate == 0L) {
                visualizerLastUpdate = now
                nextVisualizerPulse = now
            }
            while (now >= nextVisualizerPulse) {
                for (band in 0 until BANDS) {
                    visualizerTargets[band] = 0.12f + nextRandom() * 0.88f
                }
                nextVisualizerPulse = now + 180L + (nextRandom() * 260L).toLong()
            }
            for (band in 0 until BANDS) {
                visualizerLevels[band] += (visualizerTargets[band] - visualizerLevels[band]) * 0.16f
            }
        } else {
            for (band in 0 until BANDS) visualizerLevels[band] *= 0.82f
            visualizerLastUpdate = 0L
        }
        invalidate()
    }

    fun setMediaPlaying(playing: Boolean) {
        if (mediaPlaying == playing) return
        mediaPlaying = playing
        if (!playing) {
            visualizerTargets.fill(0f)
            nextVisualizerPulse = 0L
        }
        invalidate()
    }

    fun setInteractionEnabled(enabled: Boolean) {
        interactionEnabled = enabled
        if (!enabled) {
            touch = null
            ripples.clear()
        }
    }

    fun regenerate() {
        seed = kotlin.random.Random.nextInt()
        invalidate()
    }

    fun handleTouch(event: MotionEvent): Boolean {
        if (!interactionEnabled) return false
        val now = SystemClock.uptimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touch = Touch(event.x, event.y, now)
            MotionEvent.ACTION_MOVE -> touch?.let {
                if (!it.dragged && movedBeyondTouchSlop(it.downX, it.downY, event.x, event.y, touchSlop)) {
                    touch = it.copy(dragged = true)
                }
            }
            MotionEvent.ACTION_UP -> {
                touch?.let {
                    if (!it.dragged && !movedBeyondTouchSlop(it.downX, it.downY, event.x, event.y, touchSlop)) {
                        val held = ((now - it.start) / 1100f).coerceIn(0f, 1f)
                        ripples.addLast(Ripple(event.x, event.y, now, held))
                        while (ripples.size > MAX_RIPPLES) ripples.removeFirst()
                        invalidate()
                    }
                }
                touch = null
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> touch = null
        }
        return true
    }

    @RequiresApi(27)
    fun wallpaperColors(): WallpaperColors = WallpaperColors(
        Color.valueOf(backgroundColor), Color.valueOf(litColor), Color.valueOf(crestColor)
    )

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val time = (now - startedAt).coerceAtLeast(0L) / 1000f
        val cell = max(4f, min(0.88f * max(width - 48f, 1f), 896f) / 80f)
        val cols = (width / cell).toInt() + 1
        val rows = (height / cell).toInt() + 1
        val activeRipples = activeRipples(now, cell)
        canvas.drawColor(backgroundColor)

        for (row in 0 until rows) {
            val top = row * cell
            for (col in 0 until cols) {
                val xLeft = col * cell
                val cx = xLeft + cell / 2f
                val cy = top + cell / 2f
                val base = 0.6f * sample(noise, col / CELLS_PER_NOISE + time * 0.14f, row / CELLS_PER_NOISE - time * 0.055f) +
                    0.4f * sample(noise, col / CELLS_PER_NOISE * 0.55f - time * 0.08f, row / CELLS_PER_NOISE * 0.55f + time * 0.06f)
                val twinkle = 0.5f + 0.5f * sin(time * 1.1f + jitter[(row * 37 + col * 11) and 4095] * 6.283f)
                val nx = cx / width * 2f - 1f
                val ny = cy / height * 2f - 1f
                val radius = kotlin.math.sqrt(nx * nx + ny * ny * 0.82f)
                val edge = ((radius - 0.42f) / 0.85f).coerceIn(0f, 1f)
                val shade = FIELD_DENSITY * (0.16f + edge * edge * 0.84f)
                var luminance = shade * (0.3f + 0.52f * base * base + 0.18f * twinkle) * 0.62f
                var heat = 0f

                val band = visualizerLevels[col * BANDS / cols]
                val fromBottom = rows - 1 - row
                val bandHeight = band * rows * SPECTRUM_REACH
                if (mediaPlaying && band > 0.08f && fromBottom < bandHeight) {
                    val spectrum = band * (1f - fromBottom / bandHeight).coerceIn(0f, 1f)
                    luminance += spectrum * SPECTRUM_DENSITY * min(1f, shade * 3f)
                    heat = spectrum * SPECTRUM_HEAT
                }

                val ripple = if (activeRipples.isEmpty()) 0f else rippleAt(cx, cy, cell, activeRipples)
                luminance += ripple
                heat = max(heat, ripple)

                val threshold = 0.78f * ((BAYER[(row and 7) * 8 + (col and 7)] + 0.5f) / 64f) +
                    0.22f * jitter[(row and 63) * 64 + (col and 63)]
                if (luminance <= threshold) continue
                paint.color = when {
                    heat > 0.45f -> crestColor
                    heat > 0.18f -> hoverColor
                    luminance > 0.34f -> blend(midColor, litColor, 0.45f)
                    luminance > 0.2f -> midColor
                    else -> dimColor
                }
                val x = xLeft.toInt()
                val y = top.toInt()
                canvas.drawRect(x.toFloat(), y.toFloat(), (xLeft + cell).toInt().toFloat(), (top + cell).toInt().toFloat(), paint)
            }
        }
        if (isAttachedToWindow) postInvalidateDelayed(50L)
    }

    private fun activeRipples(now: Long, cell: Float): List<ActiveRipple> {
        while (ripples.isNotEmpty() && (now - ripples.first().born) >= RIPPLE_LIFE_MS) ripples.removeFirst()
        if (ripples.isEmpty()) return emptyList()

        val ringWidth = cell * 2.5f
        return ripples.mapNotNull { ripple ->
            val age = ((now - ripple.born) / RIPPLE_LIFE_MS).coerceIn(0f, 1f)
            if (age >= 1f) return@mapNotNull null
            val radius = cell * (RIPPLE_FROM + RIPPLE_GROWTH * ripple.charge) * (1f + age * 5f)
            val inner = max(0f, radius - ringWidth)
            val outer = radius + ringWidth
            ActiveRipple(ripple.x, ripple.y, radius, inner * inner, outer * outer, (1f - age) * 0.9f)
        }
    }

    private fun rippleAt(cx: Float, cy: Float, cell: Float, activeRipples: List<ActiveRipple>): Float {
        var strongest = 0f
        for (ripple in activeRipples) {
            val dx = cx - ripple.x
            val dy = cy - ripple.y
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared < ripple.innerSquared || distanceSquared > ripple.outerSquared) continue
            val ring = (1f - abs(sqrt(distanceSquared) - ripple.radius) / (cell * 2.5f)).coerceIn(0f, 1f)
            strongest = max(strongest, ring * ripple.strength)
        }
        return strongest
    }

    private fun setColors(background: Int, dim: Int, mid: Int, lit: Int, hover: Int, crest: Int) {
        backgroundColor = background.opaque()
        dimColor = dim
        midColor = mid
        litColor = lit
        hoverColor = hover
        crestColor = crest
    }

    private fun buildNoise(seed: Int): FloatArray {
        var state = seed
        var field = FloatArray(NOISE_SIZE * NOISE_SIZE) { state = state * 1664525 + 1013904223; (state ushr 1) / Int.MAX_VALUE.toFloat() }
        repeat(2) {
            val next = FloatArray(field.size)
            for (y in 0 until NOISE_SIZE) for (x in 0 until NOISE_SIZE) {
                var total = 0f
                for (dy in -1..1) for (dx in -1..1) {
                    val sx = (x + dx + NOISE_SIZE) % NOISE_SIZE
                    val sy = (y + dy + NOISE_SIZE) % NOISE_SIZE
                    total += field[sy * NOISE_SIZE + sx]
                }
                next[y * NOISE_SIZE + x] = total / 9f
            }
            field = next
        }
        val min = field.minOrNull() ?: 0f
        val span = (field.maxOrNull() ?: 1f) - min
        return FloatArray(field.size) { (field[it] - min) / max(span, 1e-6f) }
    }

    private fun buildJitter(seed: Int): FloatArray {
        var state = seed
        return FloatArray(64 * 64) { state = state * 1664525 + 1013904223; (state ushr 1) / Int.MAX_VALUE.toFloat() }
    }

    private fun sample(field: FloatArray, x: Float, y: Float): Float {
        val xi = kotlin.math.floor(x).toInt()
        val yi = kotlin.math.floor(y).toInt()
        val fx = x - xi
        val fy = y - yi
        val x0 = ((xi % NOISE_SIZE) + NOISE_SIZE) % NOISE_SIZE
        val y0 = ((yi % NOISE_SIZE) + NOISE_SIZE) % NOISE_SIZE
        val x1 = (x0 + 1) % NOISE_SIZE
        val y1 = (y0 + 1) % NOISE_SIZE
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)
        val a = field[y0 * NOISE_SIZE + x0]
        val b = field[y0 * NOISE_SIZE + x1]
        val c = field[y1 * NOISE_SIZE + x0]
        val d = field[y1 * NOISE_SIZE + x1]
        return (a * (1f - sx) + b * sx) * (1f - sy) + (c * (1f - sx) + d * sx) * sy
    }

    private fun Int.opaque(): Int = if (Color.alpha(this) == 0) Color.rgb(Color.red(this), Color.green(this), Color.blue(this)) else this

    private fun nextRandom(): Float {
        visualizerRandom = visualizerRandom * 1664525 + 1013904223
        return (visualizerRandom ushr 1) / Int.MAX_VALUE.toFloat()
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt()
        )
    }
}
