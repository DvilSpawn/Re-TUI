package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A low-CPU, terminal-glyph black hole with a rotating accretion disk. */
class BlackHoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private data class Particle(
        val radius: Float,
        var angle: Float,
        val speed: Float,
        val glyph: String,
        val band: Int
    )

    private val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = FONT_SIZE
    }
    private val cellWidth = paint.measureText("█")
    private val particles = mutableListOf<Particle>()
    private var staticLayer: Bitmap? = null
    private val animationBounds = Rect()
    private var random = Random(404)
    private var running = false
    private var colors = PALETTES.getValue("amber").map(Color::parseColor)
    var paletteName = "amber"
        private set
    var offsetX = 0f
        set(value) { field = value; invalidate() }
    var offsetY = 0f
        set(value) { field = value; invalidate() }
    var sceneScale = 1f
        set(value) { field = value.coerceIn(0.5f, 2f); invalidate() }
    var diskTilt = 0.72f
        set(value) {
            val tilt = value.coerceIn(0.45f, 1f)
            if (field == tilt) return
            field = tilt
            rebuildStaticLayer()
            invalidate()
        }
    var diskWidth = 1f
        set(value) {
            val width = value.coerceIn(0.75f, 2.5f)
            if (field == width) return
            field = width
            rebuildStaticLayer()
            invalidate()
        }
    var particleDensity = 5
        set(value) {
            val density = value.coerceIn(1, 10)
            if (field == density) return
            field = density
            rebuild()
        }

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            advance()
            invalidate()
            postDelayed(this, FRAME_DELAY_MS)
        }
    }

    fun loadPosition() {
        offsetX = RetuiWallpaperSettings.offsetX(context)
        offsetY = RetuiWallpaperSettings.offsetY(context)
        sceneScale = RetuiWallpaperSettings.scale(context)
        diskTilt = RetuiWallpaperSettings.height(context)
        diskWidth = RetuiWallpaperSettings.treeWidth(context)
        particleDensity = RetuiWallpaperSettings.petalDensity(context)
        setPalette(RetuiWallpaperSettings.blackHolePalette(context))
    }

    fun setPalette(name: String) {
        val next = PALETTES[name] ?: return
        paletteName = name
        colors = next.map(Color::parseColor)
        rebuildStaticLayer()
        invalidate()
    }

    @RequiresApi(27)
    fun wallpaperColors(): WallpaperColors = WallpaperColors(
        Color.valueOf(colors[0]), Color.valueOf(colors[3]), Color.valueOf(colors[5])
    )

    fun regenerate() {
        random = Random(Random.nextInt())
        rebuild()
    }

    fun advance() {
        particles.forEach { it.angle = (it.angle + it.speed) % TAU }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuild()
        rebuildStaticLayer()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = windowVisibility == VISIBLE
        removeCallbacks(frame)
        if (running) post(frame)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        running = visibility == VISIBLE
        removeCallbacks(frame)
        if (running) post(frame)
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = colors[0]
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.save()
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(sceneScale, sceneScale)
        staticLayer?.let { canvas.drawBitmap(it, -width / 2f, -height / 2f, paint) }
        drawParticles(canvas)
        canvas.restore()
    }

    fun animationBounds(): Rect {
        val radius = eventRadius()
        val orbit = radius * 4.1f * diskWidth.coerceAtMost(1.8f) * sceneScale
        val tilt = 0.16f + (1f - diskTilt) * 0.55f
        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY
        animationBounds.set(
            (cx - orbit - CELL_H).toInt().coerceAtLeast(0),
            (cy - orbit * tilt - CELL_H * 2).toInt().coerceAtLeast(0),
            (cx + orbit + CELL_H).toInt().coerceAtMost(width),
            (cy + orbit * tilt + CELL_H * 2).toInt().coerceAtMost(height)
        )
        return animationBounds
    }

    private fun rebuildStaticLayer() {
        if (width == 0 || height == 0) return
        staticLayer?.recycle()
        staticLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.translate(width / 2f, height / 2f)
            drawStars(canvas)
            drawLensing(canvas)
            drawDiskBands(canvas, front = false)
            drawEventHorizon(canvas)
            drawDiskBands(canvas, front = true)
        }
    }

    private fun drawStars(canvas: Canvas) {
        paint.textSize = FONT_SIZE
        repeat(52) { i ->
            val x = ((i * 73 + 29) % 997) / 997f * width - width / 2f
            val y = ((i * 151 + 47) % 991) / 991f * height - height / 2f
            val distance = x * x + y * y
            if (distance < eventRadius() * eventRadius() * 4f) return@repeat
            paint.color = if (i % 7 == 0) colors[4] else colors[2]
            glyph(canvas, if (i % 9 == 0) "+" else "·", x, y)
        }
    }

    private fun drawLensing(canvas: Canvas) {
        val radius = eventRadius()
        ellipseGlyphs(canvas, radius * 1.35f, radius * 1.42f, 198f, 342f, "*", colors[5])
        ellipseGlyphs(canvas, radius * 1.55f, radius * 1.62f, 205f, 335f, ".", colors[3])
    }

    private fun drawParticles(canvas: Canvas) {
        val radius = eventRadius()
        val tilt = 0.16f + (1f - diskTilt) * 0.55f
        paint.textSize = FONT_SIZE
        for (particle in particles) {
            val depth = sin(particle.angle)
            val orbit = radius * particle.radius * diskWidth.coerceAtMost(1.8f)
            val x = cos(particle.angle) * orbit
            val y = depth * orbit * tilt
            if (x * x + y * y < radius * radius) continue
            val brightness = if (x < 0) 2 else 0
            paint.color = colors[(particle.band + brightness).coerceIn(2, 6)]
            glyph(canvas, particle.glyph, x, y - depth * radius * 0.08f)
        }
    }

    private fun drawDiskBands(canvas: Canvas, front: Boolean) {
        val radius = eventRadius()
        val tilt = 0.16f + (1f - diskTilt) * 0.55f
        repeat(5) { band ->
            val orbit = radius * (1.3f + band * 0.42f) * diskWidth.coerceAtMost(1.8f)
            ellipseGlyphs(
                canvas, orbit, orbit * tilt,
                if (front) 0f else 180f, if (front) 180f else 360f,
                if (band < 2) "=" else "-", colors[if (band < 2) 5 else 3]
            )
        }
    }

    private fun drawEventHorizon(canvas: Canvas) {
        val radius = eventRadius()
        paint.color = Color.BLACK
        var y = -radius
        while (y <= radius) {
            var x = -radius
            while (x <= radius) {
                if (x * x + y * y <= radius * radius) glyph(canvas, "█", x, y)
                x += cellWidth
            }
            y += CELL_H
        }
        ellipseGlyphs(canvas, radius, radius, 205f, 335f, "#", colors[3])
    }

    private fun ellipseGlyphs(
        canvas: Canvas,
        rx: Float,
        ry: Float,
        startDegrees: Float,
        endDegrees: Float,
        character: String,
        color: Int
    ) {
        paint.color = color
        val steps = ((endDegrees - startDegrees) / 2.5f).toInt().coerceAtLeast(1)
        repeat(steps + 1) { step ->
            val angle = Math.toRadians((startDegrees + (endDegrees - startDegrees) * step / steps).toDouble())
            glyph(canvas, character, cos(angle).toFloat() * rx, sin(angle).toFloat() * ry)
        }
    }

    private fun glyph(canvas: Canvas, character: String, x: Float, y: Float) {
        val gx = (x / cellWidth).toInt() * cellWidth
        val gy = (y / CELL_H).toInt() * CELL_H
        canvas.drawText(character, gx, gy - paint.ascent() / 2f, paint)
    }

    private fun eventRadius() = (width.coerceAtMost(height) * 0.115f).coerceAtLeast(52f)

    private fun rebuild() {
        particles.clear()
        repeat(120 + particleDensity * 42) { index ->
            val innerBias = random.nextFloat()
            particles += Particle(
                radius = 1.18f + innerBias * innerBias * 2.9f,
                angle = random.nextFloat() * TAU,
                speed = (0.004f + random.nextFloat() * 0.011f) / (1f + innerBias),
                glyph = GLYPHS[index % GLYPHS.size],
                band = 2 + random.nextInt(3)
            )
        }
        invalidate()
    }

    companion object {
        private const val FONT_SIZE = 15f
        private const val CELL_H = FONT_SIZE * 1.08f
        const val FRAME_DELAY_MS = 1000L / 12L
        private const val TAU = (PI * 2).toFloat()
        private val GLYPHS = listOf("·", ":", "-", "=", "*", "░", "▒")
        val PALETTE_NAMES = listOf("amber", "ion", "blood")
        private val PALETTES = mapOf(
            "amber" to listOf("#030207", "#090711", "#4a2030", "#a64b32", "#ed8b3a", "#ffd27a", "#fff2c7"),
            "ion" to listOf("#02040a", "#060c17", "#18315e", "#315ea8", "#65a8e8", "#b9e4ff", "#eefaff"),
            "blood" to listOf("#050203", "#100507", "#4c1019", "#9e2430", "#e34c45", "#ff9a70", "#ffe0bd")
        )
    }
}
