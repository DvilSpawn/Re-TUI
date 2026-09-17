/*
 * Re:TUI adaptation of topowall's contour-wallpaper idea.
 * Source: https://github.com/gonzalezerik/topowall
 * Upstream license: MIT. See THIRD_PARTY_NOTICES.md.
 */
package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.floor
import kotlin.math.roundToInt

class TopoNoiseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    companion object {
        const val SCENE = "topo noise"
        const val CUSTOM = "custom"
        val PALETTE_NAMES = listOf("graphite", "ocean", "rose pine", "hypsometric", CUSTOM)

        private val PALETTES = mapOf(
            "graphite" to intArrayOf(Color.rgb(10, 11, 15), Color.rgb(92, 92, 92), Color.rgb(220, 220, 220)),
            "ocean" to intArrayOf(Color.rgb(9, 18, 28), Color.rgb(49, 111, 143), Color.rgb(155, 213, 223)),
            "rose pine" to intArrayOf(Color.rgb(25, 23, 36), Color.rgb(49, 116, 143), Color.rgb(235, 188, 186)),
            "hypsometric" to intArrayOf(Color.rgb(11, 13, 16), Color.rgb(55, 125, 115), Color.rgb(235, 199, 126))
        )
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var layer: Bitmap? = null
    private var layerDirty = true
    private var backgroundColor = PALETTES.getValue("graphite")[0]
    private var lineColor = PALETTES.getValue("graphite")[1]
    private var indexColor = PALETTES.getValue("graphite")[2]

    var offsetX = 0f
        set(value) { field = value; invalidate() }
    var offsetY = 0f
        set(value) { field = value; invalidate() }
    var noiseScale = 1f
        set(value) { field = value.coerceIn(0.5f, 2f); rebuild() }
    var relief = 0.72f
        set(value) { field = value.coerceIn(0.45f, 1f); rebuild() }
    var contourDensity = 5
        set(value) { field = value.coerceIn(1, 10); rebuild() }
    var seed = 0
        private set
    var paletteName = "graphite"
        private set

    fun loadPosition() {
        seed = RetuiWallpaperSettings.topoSeed(context)
        offsetX = RetuiWallpaperSettings.topoOffsetX(context)
        offsetY = RetuiWallpaperSettings.topoOffsetY(context)
        noiseScale = RetuiWallpaperSettings.topoScale(context)
        relief = RetuiWallpaperSettings.topoRelief(context)
        contourDensity = RetuiWallpaperSettings.topoDensity(context)
        val palette = RetuiWallpaperSettings.topoPalette(context)
        if (palette == CUSTOM) {
            paletteName = CUSTOM
            backgroundColor = RetuiWallpaperSettings.topoBackground(context)
            lineColor = RetuiWallpaperSettings.topoLine(context)
            indexColor = RetuiWallpaperSettings.topoIndex(context)
            rebuild()
        } else {
            setPalette(palette)
        }
    }

    fun setPalette(name: String) {
        val colors = PALETTES[name] ?: return
        paletteName = name
        backgroundColor = colors[0]
        lineColor = colors[1]
        indexColor = colors[2]
        rebuild()
    }

    fun setCustomColor(index: Int, color: Int) {
        when (index) {
            0 -> backgroundColor = color
            1 -> lineColor = color
            2 -> indexColor = color
            else -> return
        }
        paletteName = CUSTOM
        rebuild()
    }

    fun color(index: Int): Int = when (index) {
        0 -> backgroundColor
        1 -> lineColor
        else -> indexColor
    }

    fun regenerate() {
        seed = kotlin.random.Random.nextInt()
        rebuild()
    }

    @RequiresApi(27)
    fun wallpaperColors(): WallpaperColors = WallpaperColors(
        Color.valueOf(backgroundColor), Color.valueOf(lineColor), Color.valueOf(indexColor)
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuild()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        layer?.recycle()
        layer = null
        layerDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        ensureLayer()
        canvas.drawColor(backgroundColor)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        layer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.restore()
    }

    private fun rebuild() {
        layerDirty = true
        invalidate()
    }

    private fun ensureLayer() {
        if (!layerDirty || width == 0 || height == 0) return
        layer?.recycle()
        layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            render(Canvas(bitmap))
        }
        layerDirty = false
    }

    private fun render(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        val step = (8f / noiseScale).coerceIn(4f, 14f)
        val cols = (width / step).roundToInt().coerceIn(48, 220) + 1
        val rows = (height / step).roundToInt().coerceIn(48, 320) + 1
        val values = FloatArray(cols * rows)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val nx = x.toFloat() / (cols - 1) * 3.4f
                val ny = y.toFloat() / (rows - 1) * 3.4f
                val centered = (fractalNoise(nx, ny) - 0.5f) * 2f
                values[y * cols + x] = (0.5f + centered * relief * 0.5f).coerceIn(0f, 1f)
            }
        }

        val cellW = width.toFloat() / (cols - 1)
        val cellH = height.toFloat() / (rows - 1)
        val levels = 8 + contourDensity * 2
        for (i in 1 until levels) {
            drawLevel(
                canvas, values, cols, rows, i.toFloat() / levels,
                if (i % 4 == 0) indexColor else lineColor,
                if (i % 4 == 0) 1.35f else 0.85f, cellW, cellH
            )
        }
    }

    private fun drawLevel(
        canvas: Canvas,
        values: FloatArray,
        cols: Int,
        rows: Int,
        level: Float,
        color: Int,
        widthPx: Float,
        cellW: Float,
        cellH: Float
    ) {
        val path = Path()
        paint.color = color
        paint.alpha = if (widthPx > 1f) 175 else 105
        paint.strokeWidth = widthPx
        for (y in 0 until rows - 1) {
            for (x in 0 until cols - 1) {
                val tl = values[y * cols + x]
                val tr = values[y * cols + x + 1]
                val br = values[(y + 1) * cols + x + 1]
                val bl = values[(y + 1) * cols + x]
                val xs = FloatArray(4)
                val ys = FloatArray(4)
                var count = 0
                fun edge(a: Float, b: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
                    if ((a < level) == (b < level)) return
                    val t = ((level - a) / (b - a)).coerceIn(0f, 1f)
                    xs[count] = x1 + (x2 - x1) * t
                    ys[count] = y1 + (y2 - y1) * t
                    count++
                }
                val left = x * cellW
                val top = y * cellH
                edge(tl, tr, left, top, left + cellW, top)
                edge(tr, br, left + cellW, top, left + cellW, top + cellH)
                edge(br, bl, left + cellW, top + cellH, left, top + cellH)
                edge(bl, tl, left, top + cellH, left, top)
                when (count) {
                    2 -> segment(path, xs, ys, 0, 1)
                    4 -> {
                        segment(path, xs, ys, 0, 1)
                        segment(path, xs, ys, 2, 3)
                    }
                }
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun segment(path: Path, xs: FloatArray, ys: FloatArray, a: Int, b: Int) {
        path.moveTo(xs[a], ys[a])
        path.lineTo(xs[b], ys[b])
    }

    private fun fractalNoise(x: Float, y: Float): Float {
        var total = 0f
        var amplitude = 1f
        var frequency = 1f
        var normalizer = 0f
        repeat(5) {
            total += valueNoise(x * frequency, y * frequency) * amplitude
            normalizer += amplitude
            amplitude *= 0.5f
            frequency *= 2f
        }
        return total / normalizer
    }

    private fun valueNoise(x: Float, y: Float): Float {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val tx = smooth(x - ix)
        val ty = smooth(y - iy)
        val top = lerp(hash(ix, iy), hash(ix + 1, iy), tx)
        val bottom = lerp(hash(ix, iy + 1), hash(ix + 1, iy + 1), tx)
        return lerp(top, bottom, ty)
    }

    private fun hash(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263 + seed * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        return ((n xor (n ushr 16)) and 0x7fffffff) / 2147483647f
    }

    private fun smooth(value: Float): Float = value * value * (3f - 2f * value)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
