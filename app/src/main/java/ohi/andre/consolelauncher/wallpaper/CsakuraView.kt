/*
 * Android Canvas port of csakura by realstrawhat.
 * Source: https://github.com/realstrawhat/csakura
 * Source revision: 9664fcbffac096acdb44cbc8c81527fb57d13639
 * Copyright (c) 2026 realstrawhat. Licensed under the MIT License.
 *
 * This file follows the author's web/index.html Canvas implementation and
 * preserves its generation formulas, glyphs, update loop, and regrow behavior.
 * Android View lifecycle and positioning are Re:T-UI-specific adaptations.
 */
package ohi.andre.consolelauncher.wallpaper

import android.content.Context
import android.app.WallpaperColors
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class CsakuraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private data class Cell(val g: String, val color: Int, val bold: Boolean = false)
    private data class Coord(val x: Double, val y: Double)
    private data class Blob(val x: Double, val y: Double, val rx: Double, val ry: Double)
    private data class Petal(
        var x: Double, var y: Double, val vy: Double, val amp: Double,
        val freq: Double, var phase: Double, var g: String, var color: Int,
        var rest: Double = -1.0
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = FONT_SIZE
    }
    private val boldTypeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private var grid = arrayOfNulls<Cell>(0)
    private val sources = mutableListOf<Coord>()
    private val blobs = mutableListOf<Blob>()
    private val tips = mutableListOf<Coord>()
    private val petals = mutableListOf<Petal?>()
    private var gw = 0
    private var screenGw = 0
    private var gh = 0
    private var petalGh = 0
    private var cellW = FONT_SIZE * 0.62f
    private var cellH = FONT_SIZE * 1.06f
    private var wind = 0.0
    private var windTarget = 0.0
    private var brXmin = 0.0
    private var brXmax = 0.0
    private var brYmin = 0.0
    private var running = false
    private var random = Random(0)
    private var ramp = PALETTES.getValue("sakura").map(Color::parseColor)
    private var faded = ramp[5]
    var offsetX = 0f
        set(value) { field = value; invalidate() }
    var offsetY = 0f
        set(value) { field = value; invalidate() }
    var treeScale = 1f
        set(value) { field = value.coerceIn(0.5f, 2f); invalidate() }
    var treeHeight = 0.72f
        set(value) {
            val height = value.coerceIn(0.45f, 1f)
            if (field == height) return
            field = height
            resizeGrid()
        }
    var treeWidth = 1f
        set(value) {
            val width = value.coerceIn(0.75f, 2.5f)
            if (field == width) return
            field = width
            resizeGrid()
        }
    var petalDensity = 5
        set(value) {
            val density = value.coerceIn(1, 10)
            if (field == density) return
            field = density
            if (gw > 0) resetPetals(true)
        }
    var treeSeed = 0
        private set
    var paletteName = "sakura"
        private set

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            updatePetals(1.0 / FPS)
            invalidate()
            postDelayed(this, 1000L / FPS)
        }
    }

    fun loadPosition() {
        val savedSeed = RetuiWallpaperSettings.treeSeed(context)
        val seedChanged = treeSeed != savedSeed
        treeSeed = savedSeed
        offsetX = RetuiWallpaperSettings.offsetX(context)
        offsetY = RetuiWallpaperSettings.offsetY(context)
        treeScale = RetuiWallpaperSettings.scale(context)
        treeHeight = RetuiWallpaperSettings.height(context)
        treeWidth = RetuiWallpaperSettings.treeWidth(context)
        petalDensity = RetuiWallpaperSettings.petalDensity(context)
        setPalette(RetuiWallpaperSettings.palette(context))
        if (seedChanged && gw > 0 && gh > 0) {
            genTree()
            resetPetals(false)
            invalidate()
        }
    }

    fun setPalette(name: String) {
        if (name == paletteName) return
        val colors = PALETTES[name] ?: return
        paletteName = name
        ramp = colors.map(Color::parseColor)
        faded = ramp[5]
        if (gw > 0 && gh > 0) {
            genTree()
            resetPetals(false)
            invalidate()
        }
    }

    fun wallpaperColors(): WallpaperColors = WallpaperColors(
        Color.valueOf(BG_BOTTOM),
        Color.valueOf(ramp[4]),
        Color.valueOf(ramp[1])
    )

    /** csakura's r command: regenerate the tree, then reset petals without scatter. */
    fun regrow() {
        if (gw == 0 || gh == 0) return
        treeSeed = Random.nextInt()
        genTree()
        resetPetals(false)
        invalidate()
    }

    fun advance() {
        updatePetals(1.0 / FPS)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paint.typeface = Typeface.MONOSPACE
        cellW = paint.measureText(G_FULL)
        cellH = FONT_SIZE * 1.06f
        resizeGrid()
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
        super.onDraw(canvas)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        canvas.save()
        val topMargin = (petalGh - gh) * cellH
        canvas.translate(width / 2f + offsetX, height.toFloat() + offsetY)
        canvas.scale(treeScale, treeScale)
        canvas.translate(-width / 2f, -height.toFloat() + topMargin)
        canvas.translate((width - gw * cellW) / 2f, 0f)
        for (y in 0 until gh) for (x in 0 until gw) {
            val c = grid.getOrNull(y * gw + x) ?: continue
            paint.typeface = if (c.bold) boldTypeface else Typeface.MONOSPACE
            paint.color = c.color
            canvas.drawText(c.g, x * cellW, y * cellH - paint.ascent(), paint)
        }
        canvas.restore()
        paint.typeface = Typeface.MONOSPACE
        for (p in petals) {
            p ?: continue
            val px = p.x.toInt()
            val py = p.y.toInt()
            if (px !in 0 until screenGw || py !in 0 until petalGh) continue
            paint.color = p.color
            canvas.drawText(p.g, px * cellW, py * cellH - paint.ascent(), paint)
        }
    }

    private fun resizeGrid() {
        if (width == 0 || height == 0) return
        screenGw = max((width / cellW).toInt(), 20)
        gw = max((screenGw * treeWidth).toInt(), 20)
        petalGh = max((height / cellH).toInt(), 12)
        gh = max((height * treeHeight / cellH).toInt(), 12)
        genTree()
        resetPetals(true)
        invalidate()
    }

    private fun put(x: Double, y: Double, g: String, color: Int, bold: Boolean = false) {
        val ix = x.toInt()
        val iy = y.toInt()
        if (ix !in 0 until gw || iy !in 0 until gh) return
        grid[iy * gw + ix] = Cell(g, color, bold)
    }

    private fun field(x: Double, y: Double): Double {
        var sum = 0.0
        for (b in blobs) {
            val dx = (x - b.x) / b.rx
            val dy = (y - b.y) / b.ry
            sum += exp(-(dx * dx + dy * dy) * 2.2)
        }
        return sum
    }

    private fun genCanopy() {
        var bx0 = 1e9; var bx1 = -1e9; var by0 = 1e9; var by1 = -1e9
        for (b in blobs) {
            bx0 = min(bx0, b.x - b.rx); bx1 = max(bx1, b.x + b.rx)
            by0 = min(by0, b.y - b.ry); by1 = max(by1, b.y + b.ry)
        }
        val cy = (by0 + by1) / 2
        val ry = max((by1 - by0) / 2, 2.0)
        for (y in (by0 - 2).toInt()..(by1 + 3).toInt()) {
            if (y !in 0 until gh) continue
            for (x in (bx0 - 3).toInt()..(bx1 + 3).toInt()) {
                if (x !in 0 until gw) continue
                val f = field(x.toDouble(), y.toDouble())
                if (f < 0.30 || (f < 0.42 && chance(0.35))) continue
                val h = clamp((y - (cy - ry)) / (2 * ry), 0.0, 1.0)
                if (h > 0.62 && f < 0.85 && chance((h - 0.62) * 1.3)) continue
                var shade = h * 6 + rand(-0.9, 0.9)
                val fu = field(x.toDouble(), y - 1.6)
                if (fu > f * 1.12) shade += 1.7 else if (fu < f * 0.88) shade -= 1.5
                var bold = false
                var g = when {
                    f > 0.92 -> if (chance(0.80)) G_FULL else G_DARK
                    f > 0.55 -> if (chance(0.60)) G_DARK else G_MED
                else -> { bold = chance(0.3); val r = random.nextDouble(); if (r < 0.45) G_MED else if (r < 0.85) pick(BLOOMS) else G_DOT }
                }
                if (f > 0.55 && chance(0.07)) { g = pick(BLOOMS); bold = true; shade -= 2 }
                put(x.toDouble(), y.toDouble(), g, ramp[clamp(shade, 0.0, 7.0).toInt()], bold)
                if ((f < 0.60 || fu > f * 1.12) && chance(0.5)) sources += Coord(x.toDouble(), y.toDouble())
            }
        }
    }

    private fun genTrunk(bx: Double, tx: Double, ty: Double) {
        val baseY = gh - 2.0
        val h = max(baseY - ty, 2.0)
        val maxw = clamp(gw * 0.028, 2.0, 5.0)
        val bend = rand(-1.0, 1.0) * clamp(gw * 0.02, 1.0, 4.0)
        val steps = (h * 2 + 2).toInt()
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val y = baseY - t * h
            val x = bx + (tx - bx) * t + sin(t * PI) * bend
            val w = maxw * (1 - t).pow(1.10) * (1 + 1.1 * exp(-t * 10)) + 0.6
            for (dx in -w.toInt()..w.toInt()) {
                val color = if (dx < -w * 0.35) TRUNK_D else if (dx > w * 0.45) TRUNK_L else TRUNK_M
                put(x + dx, y, G_FULL, color)
            }
        }
    }

    private fun genBranch(startX: Double, startY: Double, startAngle: Double, len: Double, depth: Int) {
        var x = startX; var y = startY; var angle = startAngle; var t = 0.0
        while (t < len) {
            x += cos(angle) * 1.7; y -= sin(angle) * 0.85; t += 1
            angle = clamp(angle + rand(-0.10, 0.10), 0.15, PI - 0.15)
            if (x < brXmin) { x = brXmin; angle = PI - angle }
            if (x > brXmax) { x = brXmax; angle = PI - angle }
            if (y < brYmin) { y = brYmin; angle = if (angle > PI / 2) PI - 0.20 else 0.20 }
            put(x, y, G_FULL, if (depth == 0) TRUNK_M else TRUNK_L)
            if (depth == 0) put(x + 1, y, G_FULL, TRUNK_D)
            else if (chance(0.35)) put(x + if (chance(0.5)) -1 else 1, y, G_FULL, TRUNK_M)
        }
        if (depth >= 2 || len < 3) { tips += Coord(x, y); return }
        val kids = 2 + if (chance(0.5)) 1 else 0
        for (i in 0 until kids) {
            val spread = rand(0.40, 0.80)
            val next = if (i == 0) angle + spread else if (i == 1) angle - spread else angle + rand(-0.25, 0.25)
            genBranch(x, y, next, len * rand(0.55, 0.75), depth + 1)
        }
    }

    private fun genGround(cx: Double, rx: Double) {
        val y = gh - 1.0
        for (x in 0 until gw) {
            val dx = (x - cx) / (rx * 1.25)
            val p = exp(-dx * dx * 2.2)
            val r = random.nextDouble()
            when {
                r < p * 0.50 -> {
                    val rr = random.nextDouble()
                    put(x.toDouble(), y, if (rr < 0.25) pick(BLOOMS) else if (rr < 0.60) G_DOT else ",", ramp[3 + random.nextInt(4)])
                }
                r < p * 0.50 + 0.10 -> put(x.toDouble(), y, "\"", GRASS)
                r < p * 0.50 + 0.16 -> put(x.toDouble(), y, ",", GRASS)
                else -> put(x.toDouble(), y, "_", GRASS)
            }
            if (gh > 3 && chance(p * 0.12)) put(x.toDouble(), y - 1, G_DOT, faded)
        }
    }

    private fun genTree() {
        random = Random(treeSeed)
        grid = arrayOfNulls(gw * gh); sources.clear(); blobs.clear(); tips.clear()
        val rx = max(gw * 0.26, 6.0)
        val ry = clamp(gh * 0.26, 3.0, rx * 0.55)
        val cx = gw * 0.5 + rand(-1.5, 1.5); val cy = ry + 2.5
        val bx = cx + rand(-2.0, 2.0); val tx = cx + rand(-2.0, 2.0)
        val ty = min(cy + ry * 1.15, gh - 4.0)
        brXmin = cx - rx * 0.80; brXmax = cx + rx * 0.80; brYmin = max(1.0, cy - ry * 0.55)
        genGround(cx, rx); genTrunk(bx, tx, ty)
        val limbs = 3 + random.nextInt(2); val reach = (ty - brYmin) * 0.60 + 2
        for (i in 0 until limbs) {
            val angle = PI / 2 + (i - (limbs - 1) / 2.0) * rand(0.55, 0.75) + rand(-0.15, 0.15)
            genBranch(tx + rand(-1.0, 1.0), ty + rand(0.0, 1.5), angle, reach * rand(0.75, 1.0), 0)
        }
        blobs += Blob(cx, cy - ry * 0.10, rx * 0.50, ry * 0.50)
        for (tip in tips) {
            if (blobs.size >= 24) break
            blobs += Blob(tip.x + rand(-1.5, 1.5), tip.y - rand(0.0, 1.5), rx * rand(0.18, 0.30), ry * rand(0.24, 0.38))
        }
        repeat(5) { if (blobs.size < 28) blobs += Blob(cx + rand(-0.70, 0.70) * rx, cy + rand(0.25, 0.60) * ry, rx * rand(0.18, 0.28), ry * rand(0.24, 0.34)) }
        genCanopy()
    }

    private fun spawnPetal(scatter: Boolean): Petal {
        var x: Double; var y: Double
        if (sources.isNotEmpty() && chance(0.85)) {
            val source = pick(sources)
            val sourcePx = (width - gw * cellW) / 2.0 + source.x * cellW
            val sourcePy = (petalGh - gh + source.y) * cellH
            val screenPx = width / 2.0 + offsetX + treeScale * (sourcePx - width / 2.0)
            val screenPy = height + offsetY + treeScale * (sourcePy - height)
            x = screenPx / cellW + rand(-1.0, 1.0)
            y = screenPy / cellH + rand(0.0, 1.0)
        } else { x = random.nextDouble() * screenGw; y = -rand(0.0, 3.0) }
        if (scatter) y = rand(0.0, petalGh - 2.0)
        return Petal(x, y, rand(0.10, 0.28), rand(0.10, 0.45), rand(0.05, 0.18), random.nextDouble() * PI * 2, pick(PETAL_GLYPHS), ramp[1 + random.nextInt(5)])
    }

    private fun resetPetals(scatter: Boolean) {
        petals.clear()
        repeat(((screenGw * petalDensity / 4).coerceIn(16, 768))) { petals += if (chance(0.6)) spawnPetal(scatter) else null }
    }

    private fun updatePetals(dt: Double) {
        if (chance(0.008)) windTarget = rand(-0.12, 0.45)
        wind += (windTarget - wind) * 0.02
        for (i in petals.indices) {
            val p = petals[i]
            if (p == null) { if (chance(0.03)) petals[i] = spawnPetal(false); continue }
            if (p.rest >= 0) { p.rest -= dt; if (p.rest < 0) petals[i] = null; continue }
            p.phase += p.freq; p.x += wind + p.amp * sin(p.phase); p.y += p.vy
            if (p.x < -2) p.x = screenGw + 1.0 else if (p.x > screenGw + 2) p.x = -1.0
            if (p.y >= petalGh - 1) { p.y = petalGh - 1.0; p.rest = rand(2.0, 7.0); p.color = faded; p.g = G_DOT }
        }
    }

    private fun rand(a: Double, b: Double) = a + random.nextDouble() * (b - a)
    private fun chance(probability: Double) = random.nextDouble() < probability
    private fun clamp(value: Double, low: Double, high: Double) = value.coerceIn(low, high)
    private fun <T> pick(items: List<T>) = items[random.nextInt(items.size)]

    companion object {
        private const val FONT_SIZE = 15f
        private const val FPS = 24
        private const val G_FULL = "█"
        private const val G_DARK = "▓"
        private const val G_MED = "▒"
        private const val G_DOT = "·"
        private val BLOOMS = listOf("❀", "✿", "❁", "✽")
        private val PETAL_GLYPHS = listOf("❀", "✿", "*", "·", "∘")
        val PALETTE_NAMES = listOf("sakura", "rose", "blush", "magenta", "peach", "coral", "sunset", "gold", "lavender", "violet", "sky", "mint", "matcha", "white", "ink")
        private val PALETTES = mapOf(
            "sakura" to listOf("#ffe3f1", "#ffd0e7", "#ffb3d9", "#ff8fc6", "#f76fae", "#e05593", "#c13d78", "#97295c"),
            "rose" to listOf("#ffd6e0", "#ffb8c9", "#ff8fab", "#f76a8a", "#e04a6a", "#c23452", "#9e2a42", "#7a1f34"),
            "blush" to listOf("#fff0f3", "#ffdce4", "#ffc0ce", "#ffa0b4", "#f08098", "#d86078", "#b84860", "#943848"),
            "magenta" to listOf("#ffe0f8", "#ffc0f0", "#ff90e0", "#f060c8", "#d040b0", "#b02890", "#8c1e70", "#681850"),
            "peach" to listOf("#ffe8d6", "#ffd4b8", "#ffb894", "#f59a6e", "#e07a4a", "#c45e32", "#a04824", "#7a3418"),
            "coral" to listOf("#ffe0d8", "#ffc4b4", "#ffa08c", "#f57a64", "#e05540", "#c23c2a", "#9e2c1e", "#7a2016"),
            "sunset" to listOf("#ffe8d0", "#ffd0a8", "#ffb078", "#ff9060", "#f07050", "#d05040", "#a83830", "#802828"),
            "gold" to listOf("#fff6d0", "#ffecc0", "#ffd890", "#f0c060", "#d8a040", "#b88028", "#906018", "#684810"),
            "lavender" to listOf("#f0e6ff", "#e0d0ff", "#c9b0f5", "#b08ee6", "#9670d4", "#7a54b8", "#5e3e96", "#462e72"),
            "violet" to listOf("#f0e0ff", "#e0c0ff", "#c890f5", "#a860e0", "#8840c8", "#6c28a8", "#501888", "#3c1068"),
            "sky" to listOf("#e0f0ff", "#c8e4ff", "#a0d0ff", "#70b4f5", "#4898e0", "#3078c0", "#205898", "#144070"),
            "mint" to listOf("#e0fff0", "#c0f5e0", "#90e8c8", "#60d4a8", "#40b888", "#289868", "#1c7850", "#145838"),
            "matcha" to listOf("#e8f5d0", "#d0e8a8", "#b0d878", "#90c050", "#70a038", "#548028", "#3c6018", "#284810"),
            "white" to listOf("#ffffff", "#fff0f5", "#ffe4ee", "#ffc8dc", "#f7a8c4", "#e080a0", "#c05c7c", "#98405c"),
            "ink" to listOf("#e8e4f0", "#d0cce0", "#b0a8c0", "#8880a0", "#686080", "#484860", "#303048", "#202030")
        )
        private val TRUNK_D = Color.parseColor("#3a2115")
        private val TRUNK_M = Color.parseColor("#5c3520")
        private val TRUNK_L = Color.parseColor("#82542f")
        private val GRASS = Color.parseColor("#87a985")
        private val BG_TOP = Color.parseColor("#1a1322")
        private val BG_BOTTOM = Color.parseColor("#120e18")
    }
}
