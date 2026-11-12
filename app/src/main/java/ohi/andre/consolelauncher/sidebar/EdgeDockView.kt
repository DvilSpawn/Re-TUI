package ohi.andre.consolelauncher.sidebar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.min

/**
 * The sidebar drawn as part of the screen bezel: at rest a slim tab with a pill, open a wider tab
 * holding the sidebar actions. Both grow out of the edge on concave shoulders, so the dock reads as
 * the frame of the phone bulging in rather than a panel floating on top.
 */
class EdgeDockView(context: Context, rightEdge: Boolean) : View(context) {
    /** Which screen edge the tab grows out of; it can change while the tab is dragged across. */
    var rightEdge = rightEdge
        set(value) { if (field != value) { field = value; invalidate() } }
    class Item(val action: EdgeSidebar.Action, val icon: Drawable, val label: String)

    var items: List<Item> = emptyList()
        set(value) { field = value; requestLayout(); invalidate() }
    var bezelColor = Color.BLACK
    var iconColor = Color.WHITE
    var accentColor = Color.WHITE

    /** Grows upward from the bottom of its window rather than out from the middle. */
    var growsUp = false
    /** How far above the window's bottom the dock's own bottom sits. */
    val bottomInset get() = shoulder(1f)

    /** 0 is the resting tab, 1 the open dock. */
    var progress = 0f
        private set
    var highlighted: EdgeSidebar.Action? = null
        set(value) { if (field != value) { field = value; invalidate() } }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    /** The resting tab's size, where a drag lifts it from. */
    val restWidth = dp(7f) * EdgeSidebar.widthScale()
    val restHeight = dp(84f) * EdgeSidebar.lengthScale()
    private val openWidth = dp(58f)
    private val slot = dp(54f)
    private val openPadding = dp(10f)
    private val iconSize = dp(24f)
    private val restPillWidth = dp(2.5f) * EdgeSidebar.widthScale()

    val windowWidth get() = (openWidth + dp(4f)).toInt()
    val windowHeight get() = (openHeight() + shoulder(1f) * 2).toInt()
    /** The resting window is wider than the tab so the thin tab stays easy to grab. */
    val restWindowWidth get() = (restWidth + dp(15f)).toInt()
    val restWindowHeight get() = (restHeight + shoulder(0f) * 2).toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val oval = RectF()
    private var animator: ValueAnimator? = null

    private fun openHeight() = items.size * slot + openPadding * 2
    private fun bodyWidth(p: Float) = restWidth + (openWidth - restWidth) * p
    private fun bodyHeight(p: Float) = restHeight + (openHeight() - restHeight) * p
    private fun corner(p: Float) = min(bodyWidth(p) / 2, dp(18f))
    // A shoulder wider than the straight part of the tab would fold the outline over itself.
    private fun shoulder(p: Float) = min(dp(8f) + dp(10f) * p, bodyWidth(p) - corner(p))

    private fun bodyTop(p: Float) = if (growsUp) height - bottomInset - bodyHeight(p) else (height - bodyHeight(p)) / 2

    fun animateTo(target: Float, done: (() -> Unit)? = null) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = if (target > progress) 220 else 160
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { done?.invoke() }
            })
            start()
        }
    }

    /** The action under a point in this view's coordinates, when the dock is open. */
    fun actionAt(x: Float, y: Float): EdgeSidebar.Action? {
        if (progress < 0.9f) return null
        val localX = if (rightEdge) width - x else x
        if (localX < 0 || localX > bodyWidth(progress) + dp(16f)) return null
        val top = bodyTop(progress) + openPadding
        val index = ((y - top) / slot).toInt()
        if (y < top || index !in items.indices) return null
        return items[index].action
    }

    /**
     * Dragging the tab off the screen edge must not count as the system back gesture. Android allows excluding
     * up to 200dp of each edge, so only the tab's own window is excluded.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (android.os.Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, width, height))
    }

    override fun onDraw(canvas: Canvas) {
        val p = progress
        val w = bodyWidth(p)
        val h = bodyHeight(p)
        val f = shoulder(p)
        val top = bodyTop(p)
        val bottom = top + h
        val r = corner(p)
        canvas.save()
        if (rightEdge) canvas.scale(-1f, 1f, width / 2f, 0f)

        // Bezel tab: concave shoulder, rounded body, concave shoulder, closed along the edge.
        bezelPath(path, oval, w, top, bottom, f, r)
        paint.style = Paint.Style.FILL
        paint.color = bezelColor
        canvas.drawPath(path, paint)
        drawBezelRim(canvas, path, oval, paint, iconColor, density, w, top, bottom, f, r)

        // Resting pill, fading out as the dock opens.
        val pillAlpha = ((1f - p * 2.5f).coerceIn(0f, 1f) * 150).toInt()
        if (pillAlpha > 0) {
            paint.color = accentColor; paint.alpha = pillAlpha
            // The bar spans the tab, so on dark apps, where the black tab disappears, it still shows the real size.
            val pw = restPillWidth; val ph = (h - dp(16f)).coerceAtLeast(dp(12f)); val cx = w / 2
            val mid = top + h / 2
            oval.set(cx - pw / 2, mid - ph / 2, cx + pw / 2, mid + ph / 2)
            canvas.drawRoundRect(oval, pw, pw, paint)
        }
        canvas.restore()

        // Actions, fading in once the tab is wide enough to hold them.
        val iconAlpha = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
        if (iconAlpha > 0f) {
            val slotTop = top + openPadding
            items.forEachIndexed { index, item ->
                val cy = slotTop + slot * index + slot / 2
                val cx = w / 2
                val x = if (rightEdge) width - cx else cx
                if (item.action == highlighted) {
                    paint.color = accentColor; paint.alpha = (60 * iconAlpha).toInt()
                    val half = dp(20f)
                    oval.set(x - half, cy - half, x + half, cy + half)
                    canvas.drawRoundRect(oval, dp(10f), dp(10f), paint)
                }
                val half = iconSize / 2
                item.icon.setBounds((x - half).toInt(), (cy - half).toInt(), (x + half).toInt(), (cy + half).toInt())
                item.icon.alpha = (255 * iconAlpha).toInt()
                item.icon.draw(canvas)
            }
        }
    }
}

/**
 * A tab growing out of the left screen edge at x = 0: a concave shoulder from the edge into the body, a body
 * [width] wide from [top] to [bottom] with [corner]-rounded outer corners, and a concave shoulder back to the
 * edge. [shoulder] must not exceed width - corner.
 */
internal fun bezelPath(path: Path, oval: RectF, width: Float, top: Float, bottom: Float, shoulder: Float, corner: Float, closed: Boolean = true) {
    val f = shoulder; val r = corner; val w = width
    path.reset()
    path.moveTo(0f, top - f)
    oval.set(0f, top - 2 * f, 2 * f, top); path.arcTo(oval, 180f, -90f)
    path.lineTo(w - r, top)
    oval.set(w - 2 * r, top, w, top + 2 * r); path.arcTo(oval, 270f, 90f)
    path.lineTo(w, bottom - r)
    oval.set(w - 2 * r, bottom - 2 * r, w, bottom); path.arcTo(oval, 0f, 90f)
    path.lineTo(f, bottom)
    oval.set(0f, bottom, 2 * f, bottom + 2 * f); path.arcTo(oval, 270f, -90f)
    if (closed) path.close()
}

/**
 * The hairline rim along a bezel tab: the shoulders and body, but not the screen edge, so it flows into the
 * bezel like the tab itself. It traces the tab's shape over dark apps, where the black tab blends in.
 */
internal fun drawBezelRim(canvas: Canvas, path: Path, oval: RectF, paint: Paint, color: Int, density: Float,
                          width: Float, top: Float, bottom: Float, shoulder: Float, corner: Float) {
    bezelPath(path, oval, width, top, bottom, shoulder, corner, closed = false)
    val style = paint.style; val stroke = paint.strokeWidth
    paint.style = Paint.Style.STROKE; paint.strokeWidth = density
    paint.color = color; paint.alpha = 64
    canvas.drawPath(path, paint)
    paint.style = style; paint.strokeWidth = stroke
}
