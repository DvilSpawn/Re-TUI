package ohi.andre.consolelauncher.managers.onboarding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * The "Building your launcher" screen: a boot log of the build stages with each choice written under
 * its stage, a segmented progress bar, and a small sketch of the home screen that assembles as the
 * stages finish. [position] runs from 0 to [stages].size; the activity gates it on the real work.
 */
internal class StartupBuildView(context: Context) : View(context) {
    sealed class Detail {
        data class Text(val value: String) : Detail()
        data class Swatches(val colors: List<Int>, val fallback: String) : Detail()
        data class Border(val style: String, val strokeDp: Float, val gapDp: Int, val label: String) : Detail()
    }
    data class Stage(val label: String, val detail: Detail?)

    /** What the sketch shows once its stage is reached. */
    data class Sketch(val wash: List<Int>, val border: String, val strokeDp: Float, val gapDp: Int,
                      val statusRows: Int, val dock: Boolean, val cyberdeck: Boolean, val crt: Boolean)

    var stages: List<Stage> = emptyList()
        set(value) { field = value; refreshDescription() }
    var sketch: Sketch? = null
    var title = ""
    var subtitle = ""
    var onlineText = ""
    var welcomeText = ""
    var position = 0f
        private set
    /** 0 while building, then 1 at "system online" and 2 at "welcome home". */
    var finale = 0
        private set
    val percent: Int get() = if (stages.isEmpty()) 100 else (position * 100 / stages.size).toInt().coerceIn(0, 100)

    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity
    private val started = SystemClock.uptimeMillis()
    private var settledAt = 0L
    private var settleFrom = 0f
    var text = Color.WHITE
    var dim = Color.GRAY
    var accent = Color.WHITE
    var typeface: Typeface = Typeface.MONOSPACE
        set(value) { field = value; listOf(label, small, big).forEach { it.typeface = value } }

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f * scaled }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f * scaled }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f * scaled; isFakeBoldText = true }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val path = Path()
    private val measure = PathMeasure()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun update(position: Float, finale: Int) {
        val stage = this.position.toInt()
        this.position = position.coerceIn(0f, stages.size.toFloat())
        if (finale > 0 && this.finale == 0) { settledAt = SystemClock.uptimeMillis(); settleFrom = spin() }
        val changed = finale != this.finale || stage != this.position.toInt()
        this.finale = finale
        if (changed) refreshDescription()
        invalidate()
    }

    /** The log as plain text, for accessibility and tests. */
    fun transcript(): String = buildString {
        stages.forEachIndexed { i, stage ->
            if (i > position.toInt()) return@forEachIndexed
            append(stage.label).append(' ').append(status(i)).append('\n')
            when (val d = stage.detail) {
                is Detail.Text -> append("  ").append(d.value).append('\n')
                is Detail.Swatches -> if (d.colors.isEmpty()) append("  ").append(d.fallback).append('\n')
                is Detail.Border -> append("  ").append(d.label).append('\n')
                null -> Unit
            }
        }
        if (finale >= 1) append(onlineText).append('\n')
        if (finale >= 2) append(welcomeText)
    }.trimEnd()

    private fun refreshDescription() { contentDescription = transcript() }

    private fun status(i: Int): String = when {
        i < position.toInt() || position >= stages.size -> "OK"
        i == position.toInt() -> "${((position - i) * 100).toInt().coerceIn(0, 99)}%"
        else -> ""
    }

    private fun dp(v: Float) = v * density

    /** Icon turn in radians: spins while building, eases to face forward once online. */
    private fun spin(): Float {
        val t = (SystemClock.uptimeMillis() - started) / 1000f
        if (finale == 0) return t * 2.2f
        val k = ((SystemClock.uptimeMillis() - settledAt) / 600f).coerceIn(0f, 1f)
        val turns = Math.ceil(settleFrom / (2 * Math.PI)).toFloat() * (2 * Math.PI).toFloat()
        val ease = 1 - (1 - k) * (1 - k) * (1 - k)
        return settleFrom + (turns - settleFrom) * ease
    }

    private fun blink() = ((SystemClock.uptimeMillis() - started) / 450) % 2 == 0L

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cx = w / 2
        var y = dp(6f)
        drawIcon(canvas, cx, y + dp(22f))
        y += dp(54f)
        big.color = accent
        canvas.drawText(title, cx - big.measureText(title) / 2, y - big.ascent(), big)
        y += big.descent() - big.ascent() + dp(2f)
        label.color = dim
        canvas.drawText(subtitle, cx - label.measureText(subtitle) / 2, y - label.ascent(), label)
        y += label.descent() - label.ascent() + dp(18f)

        val rowH = label.descent() - label.ascent()
        val detailH = small.descent() - small.ascent()
        // the log's full height is kept from the start so the bar and sketch below it stay put
        val logEnd = y + stages.sumOf { (rowH + dp(1f) + if (it.detail == null) dp(5f) else detailH + dp(6f)).toDouble() }.toFloat()
        stages.forEachIndexed { i, stage ->
            if (i > position.toInt()) return@forEachIndexed
            val state = status(i)
            label.color = if (i == position.toInt() && position < stages.size) text else dim
            val name = "> " + stage.label
            canvas.drawText(name, 0f, y - label.ascent(), label)
            label.color = if (state == "OK") accent else text
            val sw = label.measureText(state)
            canvas.drawText(state, w - sw, y - label.ascent(), label)
            fill.color = dim
            var x = label.measureText(name) + dp(6f)
            while (x < w - sw - dp(6f)) { canvas.drawRect(x, y + rowH * 0.62f, x + dp(1.5f), y + rowH * 0.62f + dp(1.5f), fill); x += dp(5f) }
            y += rowH + dp(1f)
            val p = if (i < position.toInt()) 1f else (position - i).coerceIn(0f, 1f)
            when (val d = stage.detail) {
                is Detail.Text -> { small.color = dim; canvas.drawText(clip(d.value, w - dp(16f)), dp(16f), y - small.ascent(), small); y += detailH + dp(6f) }
                is Detail.Swatches -> {
                    if (d.colors.isEmpty()) { small.color = dim; canvas.drawText(d.fallback, dp(16f), y - small.ascent(), small) }
                    else d.colors.forEachIndexed { k, c ->
                        val a = ((p * d.colors.size - k) * 255).toInt().coerceIn(0, 255)
                        fill.color = c; fill.alpha = a
                        canvas.drawRect(dp(16f) + k * dp(22f), y + dp(2f), dp(16f) + k * dp(22f) + dp(18f), y + detailH - dp(1f), fill)
                        fill.alpha = 255
                    }
                    y += detailH + dp(6f)
                }
                is Detail.Border -> {
                    rect.set(dp(16f), y + dp(2f), dp(48f), y + detailH)
                    trace(canvas, rect, d.style, d.strokeDp, d.gapDp, p, text)
                    small.color = dim
                    canvas.drawText(d.label, dp(58f), y - small.ascent(), small)
                    y += detailH + dp(6f)
                }
                null -> y += dp(5f)
            }
        }

        // segmented progress bar and percentage
        y = logEnd + dp(6f)
        val pct = "$percent%"
        label.color = text
        val pw = label.measureText("100%") + dp(8f)
        val barW = w - pw
        val seg = dp(6f)
        var x = 0f
        while (x + seg <= barW) {
            val lit = x + seg <= barW * percent / 100f
            fill.color = if (lit) accent else dim
            if (!lit) fill.alpha = 90
            canvas.drawRect(x, y, x + seg - dp(2f), y + dp(8f), fill)
            x += seg
        }
        fill.alpha = 255
        canvas.drawText(pct, w - label.measureText(pct), y + dp(4f) - (label.ascent() + label.descent()) / 2, label)
        y += dp(20f)
        if (finale >= 1) {
            val s = if (finale >= 2) welcomeText + if (blink()) "_" else " " else onlineText
            label.color = accent
            canvas.drawText(s, cx - label.measureText(s) / 2, y - label.ascent(), label)
        }
        y += rowH + dp(14f)

        val room = height - y - dp(4f)
        if (room > dp(90f)) drawSketch(canvas, cx, y, room)
        postInvalidateOnAnimation()
    }

    private fun clip(s: String, max: Float): String {
        if (small.measureText(s) <= max) return s
        var end = s.length
        while (end > 1 && small.measureText(s, 0, end) + small.measureText("…") > max) end--
        return s.substring(0, end) + "…"
    }

    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float) {
        val c = cos(spin())
        val half = dp(20f) * abs(c).coerceAtLeast(0.06f)
        rect.set(cx - half, cy - dp(16f), cx + half, cy + dp(16f))
        line.color = accent; line.strokeWidth = dp(2f); line.pathEffect = null
        fill.color = text; fill.alpha = 40
        canvas.drawRoundRect(rect, dp(4f), dp(4f), fill)
        fill.alpha = 255
        canvas.drawRoundRect(rect, dp(4f), dp(4f), line)
        if (c > 0.35f) {
            canvas.save()
            canvas.scale(c, 1f, cx, cy)
            label.color = accent
            val glyph = ">" + if (finale == 0 || blink()) "_" else " "
            canvas.drawText(glyph, cx - label.measureText(">_") / 2, cy - (label.ascent() + label.descent()) / 2, label)
            canvas.restore()
        }
    }

    /** Draws [fraction] of [r]'s outline, clockwise from the top left, in the chosen border style. */
    private fun trace(canvas: Canvas, r: RectF, style: String, strokeDp: Float, gapDp: Int, fraction: Float, color: Int) {
        if (style == StartupMenuManager.BORDER_NONE) {
            fill.color = color; fill.alpha = (fraction * 50).toInt()
            canvas.drawRect(r, fill); fill.alpha = 255
            return
        }
        path.reset(); path.addRect(r, Path.Direction.CW)
        measure.setPath(path, false)
        val part = Path()
        measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), part, true)
        line.color = color
        line.strokeWidth = dp(strokeDp).coerceAtLeast(1f)
        line.pathEffect = if (style == StartupMenuManager.BORDER_DASHED) DashPathEffect(floatArrayOf(dp(4f), dp(gapDp.toFloat())), 0f) else null
        canvas.drawPath(part, line)
        line.pathEffect = null
    }

    /** A small phone sketch of the home screen that fills in as stages finish. */
    private fun drawSketch(canvas: Canvas, cx: Float, top: Float, room: Float) {
        val s = sketch ?: return
        val h = min(room, dp(230f))
        val w = h * 0.52f
        val left = cx - w / 2
        val phone = RectF(left, top, left + w, top + h)
        fun reached(stage: Int) = (position - stage).coerceIn(0f, 1f)
        line.color = dim; line.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(phone, dp(10f), dp(10f), line)
        val inner = RectF(phone.left + dp(6f), phone.top + dp(10f), phone.right - dp(6f), phone.bottom - dp(10f))

        // stage 1: the wallpaper's colours wash in
        val wash = reached(1)
        if (wash > 0f) {
            val colors = s.wash.ifEmpty { listOf(dim) }
            val band = inner.height() / colors.size
            colors.forEachIndexed { i, c ->
                fill.color = c; fill.alpha = (wash * 70).toInt()
                canvas.drawRect(inner.left, inner.top + i * band, inner.right, inner.top + (i + 1) * band, fill)
            }
            fill.alpha = 255
        }
        val gap = dp(5f)
        val status = RectF(inner.left + gap, inner.top + gap, inner.right - gap, inner.top + inner.height() * 0.22f)
        val output = RectF(status.left, status.bottom + gap, status.right, inner.bottom - inner.height() * 0.24f)
        val dock = RectF(status.left, output.bottom + gap, status.right, output.bottom + gap + inner.height() * 0.08f)
        val input = RectF(status.left, dock.bottom + gap, status.right, inner.bottom - gap)

        // stage 2: pane borders trace themselves in the chosen style
        val borders = reached(2)
        if (borders > 0f) listOf(status, output, input).forEach { trace(canvas, it, s.border, min(s.strokeDp, 2f), min(s.gapDp, 6), borders, text) }

        // stage 3: status rows light up, one per feature
        val rows = reached(3)
        val rowH = dp(4f)
        for (i in 0 until s.statusRows) {
            val k = (rows * s.statusRows - i).coerceIn(0f, 1f)
            if (k <= 0f) break
            val y = status.top + dp(6f) + i * (rowH + dp(4f))
            if (y + rowH > status.bottom - dp(3f)) break
            fill.color = if (i == 0) accent else text; fill.alpha = (k * 200).toInt()
            canvas.drawRect(status.left + dp(6f), y, status.left + dp(6f) + (status.width() - dp(12f)) * (0.35f + 0.1f * (i % 3)) * k, y + rowH, fill)
        }
        fill.alpha = 255

        // stage 4: effects
        val fx = reached(4)
        if (fx > 0f && s.crt) {
            fill.color = Color.BLACK; fill.alpha = (fx * 40).toInt()
            var y = inner.top
            while (y < inner.bottom) { canvas.drawRect(inner.left, y, inner.right, y + dp(1f), fill); y += dp(3f) }
            fill.alpha = 255
        }
        if (fx > 0f && s.cyberdeck) {
            line.color = accent; line.alpha = (fx * 255).toInt(); line.strokeWidth = dp(1.5f)
            val l = dp(8f)
            for ((x, y, dx, dy) in listOf(arrayOf(inner.left, inner.top, 1f, 1f), arrayOf(inner.right, inner.top, -1f, 1f),
                    arrayOf(inner.left, inner.bottom, 1f, -1f), arrayOf(inner.right, inner.bottom, -1f, -1f))) {
                canvas.drawLine(x, y, x + l * dx, y, line); canvas.drawLine(x, y, x, y + l * dy, line)
            }
            line.alpha = 255
        }

        // stage 5: output lines and dock chips
        val out = reached(5)
        val lines = ((output.height() - dp(8f)) / dp(8f)).toInt().coerceAtLeast(0)
        for (i in 0 until lines) {
            val k = (out * lines - i).coerceIn(0f, 1f)
            if (k <= 0f) break
            fill.color = if (i % 4 == 0) accent else dim; fill.alpha = (k * 220).toInt()
            val y = output.top + dp(5f) + i * dp(8f)
            canvas.drawRect(output.left + dp(6f), y, output.left + dp(6f) + (output.width() - dp(12f)) * (0.3f + ((i * 37) % 60) / 100f), y + dp(3f), fill)
        }
        if (s.dock) {
            val chips = 4
            val cw = (dock.width() - gap * (chips - 1)) / chips
            for (i in 0 until chips) {
                val k = (out * chips - i).coerceIn(0f, 1f)
                if (k <= 0f) break
                fill.color = text; fill.alpha = (k * 120).toInt()
                canvas.drawRoundRect(dock.left + i * (cw + gap), dock.top, dock.left + i * (cw + gap) + cw, dock.bottom, dp(3f), dp(3f), fill)
            }
        }
        fill.alpha = 255

        // stage 6: the input line wakes
        if (reached(6) > 0f) {
            small.color = accent
            small.alpha = (reached(6) * 255).toInt()
            val prompt = ">" + if (blink()) "_" else " "
            canvas.drawText(prompt, input.left + dp(6f), input.centerY() - (small.ascent() + small.descent()) / 2, small)
            small.alpha = 255
        }
    }
}
