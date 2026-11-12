package ohi.andre.consolelauncher.sidebar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** One piece of the edge stack. A [EdgeSegment.Tab] with no kind is the home tab back to the sidebar. */
internal sealed interface EdgeSegment {
    data object Sidebar : EdgeSegment
    data class Tab(val kind: EdgePills.Kind?) : EdgeSegment
    data class Body(val kind: EdgePills.Kind) : EdgeSegment
}

/** What the stack has focused: the saved choice while it is still running, else the sidebar, else the first item. */
internal fun edgeStackFocus(sidebar: Boolean, running: List<EdgePills.Kind>, saved: EdgePills.Kind?): EdgePills.Kind? =
    saved?.takeIf { it in running } ?: if (sidebar) null else running.firstOrNull()

/** The stack from top to bottom. A null [focus] means the sidebar is the main tab. */
internal fun edgeStackSegments(sidebar: Boolean, running: List<EdgePills.Kind>, focus: EdgePills.Kind?): List<EdgeSegment> =
    if (focus == null) (if (sidebar) listOf(EdgeSegment.Sidebar) else emptyList()) + running.map { EdgeSegment.Tab(it) }
    else (if (sidebar) listOf(EdgeSegment.Tab(null)) else emptyList()) + EdgeSegment.Body(focus) +
        running.filter { it != focus }.map { EdgeSegment.Tab(it) }

/**
 * The sidebar and everything running, as one object grown out of the screen bezel: the focused item fills the main
 * tab and the others wait as small icon tabs, with a home tab back to the sidebar while something else is in focus.
 */
class EdgeStackView(context: Context, rightEdge: Boolean) : View(context) {
    sealed interface Hit {
        data object Sidebar : Hit
        data class Tab(val kind: EdgePills.Kind?) : Hit
        data class Button(val kind: EdgePills.Kind) : Hit
        data class Body(val kind: EdgePills.Kind) : Hit
    }

    var rightEdge = rightEdge
        set(value) { if (field != value) { field = value; invalidate() } }
    var icons: Map<EdgePills.Kind?, Drawable> = emptyMap()
    var typeface: Typeface? = null
        set(value) { field = value; textPaint.typeface = value ?: Typeface.MONOSPACE; invalidate() }
    var bezelColor = Color.BLACK
    var textColor = Color.WHITE
    var accentColor = Color.WHITE

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private val slimWidth = dp(7f) * EdgeSidebar.widthScale()
    private val slimHeight = dp(84f) * EdgeSidebar.lengthScale()
    private val slimBar = dp(2.5f) * EdgeSidebar.widthScale()
    private val wideWidth = dp(30f) * EdgeSidebar.widthScale()
    private val tabHeight = dp(38f)
    private val bodyHeight = dp(128f) * EdgeSidebar.lengthScale()
    private val shoulder = dp(8f)
    private val pad = dp(6f)
    private val button = wideWidth * 0.76f

    private var segments: List<EdgeSegment> = emptyList()
    private var pills: Map<EdgePills.Kind, EdgePills.Pill> = emptyMap()

    private fun widthOf(segment: EdgeSegment) = if (segment === EdgeSegment.Sidebar) slimWidth else wideWidth
    private fun heightOf(segment: EdgeSegment) = when (segment) {
        EdgeSegment.Sidebar -> slimHeight
        is EdgeSegment.Tab -> tabHeight
        is EdgeSegment.Body -> bodyHeight
    }

    val stackWidth get() = segments.maxOfOrNull(::widthOf) ?: 0f
    val stackHeight get() = segments.sumOf { heightOf(it).toDouble() }.toFloat()

    /** The window leaves room past the stack so it is easy to grab. */
    val windowWidth get() = (stackWidth + dp(if (stackWidth > slimWidth) 10f else 15f)).toInt()
    val windowHeight get() = (stackHeight + shoulder * 2).toInt()

    private fun top() = (height - stackHeight) / 2

    /** The sidebar part's lower edge within this view when it is the main tab and has tabs under it, else null. */
    fun sidebarBottom(): Float? = if (segments.firstOrNull() === EdgeSegment.Sidebar && segments.size > 1) windowHeight / 2f - stackHeight / 2 + slimHeight else null

    internal fun show(next: List<EdgeSegment>, running: List<EdgePills.Pill>) {
        segments = next
        pills = running.associateBy { it.kind }
        invalidate()
    }

    fun hitAt(y: Float): Hit? {
        var at = top()
        for (segment in segments) {
            val h = heightOf(segment)
            if (y < at + h || segment === segments.last()) return when (segment) {
                EdgeSegment.Sidebar -> Hit.Sidebar
                is EdgeSegment.Tab -> Hit.Tab(segment.kind)
                is EdgeSegment.Body -> if (y >= at + h - pad * 2 - button) Hit.Button(segment.kind) else Hit.Body(segment.kind)
            }
            at += h
        }
        return null
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
    private val path = Path()
    private val oval = RectF()

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (android.os.Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, width, height))
    }

    /** The outline from the screen edge, along every segment and its steps, back to the edge. */
    private fun outline(closed: Boolean) {
        val top = top()
        val first = widthOf(segments.first())
        val corner = min(first / 2, dp(10f))
        val f = min(shoulder, max(first - corner, dp(3f)))
        path.reset()
        path.moveTo(0f, top - f)
        oval.set(0f, top - 2 * f, 2 * f, top); path.arcTo(oval, 180f, -90f)
        path.lineTo(first - corner, top)
        oval.set(first - 2 * corner, top, first, top + 2 * corner); path.arcTo(oval, 270f, 90f)
        var y = top
        val step = dp(4f)
        segments.forEachIndexed { index, segment ->
            val w = widthOf(segment)
            y += heightOf(segment)
            val next = segments.getOrNull(index + 1) ?: return@forEachIndexed
            val b = widthOf(next)
            if (b > w) {
                // A concave step out to the wider tab, then its rounded corner.
                path.lineTo(w, y - step)
                oval.set(w, y - 2 * step, w + 2 * step, y); path.arcTo(oval, 180f, -90f)
                path.lineTo(b - step, y)
                oval.set(b - 2 * step, y, b, y + 2 * step); path.arcTo(oval, 270f, 90f)
            } else path.lineTo(w, y)
        }
        val last = widthOf(segments.last())
        val end = min(last / 2, dp(10f))
        val g = min(shoulder, max(last - end, dp(3f)))
        path.lineTo(last, y - end)
        oval.set(last - 2 * end, y - 2 * end, last, y); path.arcTo(oval, 0f, 90f)
        path.lineTo(g, y)
        oval.set(0f, y, 2 * g, y + 2 * g); path.arcTo(oval, 270f, -90f)
        if (closed) path.close()
    }

    override fun onDraw(canvas: Canvas) {
        if (segments.isEmpty()) return
        canvas.save()
        if (rightEdge) canvas.scale(-1f, 1f, width / 2f, 0f)
        paint.style = Paint.Style.FILL; paint.color = bezelColor
        outline(closed = true); canvas.drawPath(path, paint)
        outline(closed = false)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = density; paint.color = textColor; paint.alpha = 64
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        var y = top()
        segments.forEachIndexed { index, segment ->
            val h = heightOf(segment)
            if (segment === EdgeSegment.Sidebar) {
                paint.color = accentColor; paint.alpha = 150
                val ph = (h - dp(16f)).coerceAtLeast(dp(12f))
                oval.set(slimWidth / 2 - slimBar / 2, y + h / 2 - ph / 2, slimWidth / 2 + slimBar / 2, y + h / 2 + ph / 2)
                canvas.drawRoundRect(oval, slimBar, slimBar, paint)
            }
            if (index > 0 && widthOf(segments[index - 1]) == widthOf(segment)) {
                paint.color = textColor; paint.alpha = 56
                canvas.drawRect(dp(4f), y, widthOf(segment) - dp(4f), y + density, paint)
            }
            if (segment is EdgeSegment.Body) drawButton(canvas, y + h - pad - button / 2, pills[segment.kind]?.running == true)
            y += h
        }
        canvas.restore()
        y = top()
        for (segment in segments) {
            val h = heightOf(segment)
            val cx = if (rightEdge) width - wideWidth / 2 else wideWidth / 2
            when (segment) {
                is EdgeSegment.Tab -> drawIcon(canvas, icons[segment.kind], cx, y + h / 2, dp(18f))
                is EdgeSegment.Body -> {
                    val pill = pills[segment.kind]
                    val regionTop = y + pad
                    val regionBottom = y + h - pad * 2 - button
                    val mid = (regionTop + regionBottom) / 2
                    if (pill?.text.isNullOrEmpty()) drawIcon(canvas, icons[segment.kind], cx, mid, wideWidth * 0.62f)
                    else drawTime(canvas, pill!!.text, cx, mid, regionBottom - regionTop)
                }
                EdgeSegment.Sidebar -> Unit
            }
            y += h
        }
    }

    private fun drawIcon(canvas: Canvas, icon: Drawable?, cx: Float, cy: Float, size: Float) {
        val half = size / 2
        icon?.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        icon?.draw(canvas)
    }

    private fun drawTime(canvas: Canvas, text: String, cx: Float, cy: Float, room: Float) {
        textPaint.color = textColor
        textPaint.textSize = wideWidth * 0.62f
        val measured = textPaint.measureText(text)
        if (measured > room) textPaint.textSize *= room / measured
        canvas.save()
        canvas.rotate(if (rightEdge) -90f else 90f, cx, cy)
        val metrics = textPaint.fontMetrics
        canvas.drawText(text, cx, cy - (metrics.ascent + metrics.descent) / 2, textPaint)
        canvas.restore()
    }

    /** A soft rounded square holding pause bars or a play triangle. */
    private fun drawButton(canvas: Canvas, cy: Float, running: Boolean) {
        val cx = wideWidth / 2
        val half = button / 2
        paint.color = textColor; paint.alpha = 46
        oval.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(oval, half * 0.7f, half * 0.7f, paint)
        paint.alpha = 230
        val g = half * 0.42f
        if (running) {
            val bar = g * 0.42f
            canvas.drawRect(cx - g * 0.75f, cy - g, cx - g * 0.75f + bar, cy + g, paint)
            canvas.drawRect(cx + g * 0.75f - bar, cy - g, cx + g * 0.75f, cy + g, paint)
        } else {
            path.reset()
            path.moveTo(cx - g * 0.9f, cy - g * 0.8f); path.lineTo(cx + g * 0.9f, cy - g * 0.8f); path.lineTo(cx, cy + g * 0.9f)
            path.close(); canvas.drawPath(path, paint)
        }
    }
}
