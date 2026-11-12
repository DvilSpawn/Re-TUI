package ohi.andre.consolelauncher.sidebar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Drags an edge tab like a drop of tar or ferrofluid. Pulling it off the edge swells it into a drop joined to
 * the edge by a neck that thins and snaps; the drop trails the finger on a spring, stretching as it moves; on
 * release it flows to the nearer edge, which reaches out and draws it back in. It draws in a full-screen window
 * that takes no touches, so the gesture stays with the tab that started it.
 *
 * [source] is the tab's rectangle on screen; [done] gets the edge and screen y the drop settled on.
 */
class EdgeBlob(
    context: Context,
    private val windows: WindowManager,
    source: RectF,
    private var sourceRight: Boolean,
    private val fill: Int,
    private val rim: Int,
    private val bar: Int,
    private val done: (right: Boolean, centerY: Float) -> Unit
) {
    private val density = context.resources.displayMetrics.density
    /** Wide enough to show around the fingertip that is dragging it; follows the pill width setting. */
    private val radius = (40f * density * EdgeSidebar.widthScale()).coerceIn(30f * density, 72f * density)
    /** How much of the tab pools at the edge it leaves or lands on. */
    private val poolRadius = (source.height() * 0.32f).coerceAtLeast(radius * 0.9f)
    private val finger = PointF(source.centerX(), source.centerY())
    private val blob = PointF(source.centerX(), source.centerY())
    private var vx = 0f
    private var vy = 0f
    private val originY = source.centerY()
    private var originPool = poolRadius
    private var detached = false
    private var settling = false
    private var targetRight = sourceRight
    private var targetY = 0f
    private var targetPool = 0f
    private var lastFrame = 0L
    private var offsetX = 0f
    private var offsetY = 0f
    private var finished = false

    private val view = object : View(context) {
        override fun onDraw(canvas: Canvas) = render(canvas)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rim; alpha = 64; style = Paint.Style.STROKE; strokeWidth = density }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bar; alpha = 150 }
    private val path = Path()
    private val oval = RectF()
    private val tick = Runnable { frame(System.nanoTime()) }

    init {
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { windows.addView(view, params) }
        view.postOnAnimation(tick)
    }


    /** The finger moved, in screen coordinates. */
    fun move(rawX: Float, rawY: Float) { if (!settling) finger.set(rawX, rawY) }

    /** The finger lifted: flow to the edge nearer the finger, at the finger's height. */
    fun release() {
        if (settling) return
        settling = true
        // Where the finger let go decides the edge, not where the trailing drop has got to.
        targetRight = finger.x > screenWidth() / 2f
        targetY = finger.y.coerceIn(screenHeight() * 0.06f, screenHeight() * 0.94f)
        finger.set(edgeX(targetRight) + if (targetRight) -radius * 0.55f else radius * 0.55f, targetY)
    }

    private fun screenWidth() = view.width.takeIf { it > 0 }?.toFloat() ?: view.resources.displayMetrics.widthPixels.toFloat()
    private fun screenHeight() = view.height.takeIf { it > 0 }?.toFloat() ?: view.resources.displayMetrics.heightPixels.toFloat()
    private fun edgeX(right: Boolean) = if (right) screenWidth() else 0f

    private fun frame(now: Long) {
        if (finished) return
        if (view.width > 0 && offsetX == 0f && offsetY == 0f) {
            val at = IntArray(2).also(view::getLocationOnScreen)
            offsetX = at[0].toFloat(); offsetY = at[1].toFloat()
        }
        val dt = if (lastFrame == 0L) 1f / 60 else ((now - lastFrame) / 1e9f).coerceIn(1f / 240, 1f / 30)
        lastFrame = now
        // An underdamped spring: the drop lags, overshoots a little and wobbles to rest.
        val stiffness = if (settling) 260f else 340f
        val damping = if (settling) 20f else 22f
        vx += ((finger.x - blob.x) * stiffness - vx * damping) * dt
        vy += ((finger.y - blob.y) * stiffness - vy * damping) * dt
        blob.x += vx * dt; blob.y += vy * dt

        val reach = poolRadius + radius * 2.4f
        val fromOrigin = hypot(blob.x - edgeX(sourceRight), blob.y - originY)
        if (!detached && fromOrigin > reach) detached = true
        originPool = if (detached) originPool * 0.78f else poolRadius * (1f - (fromOrigin / reach) * 0.55f).coerceIn(0.35f, 1f)
        if (settling) {
            val toTarget = hypot(blob.x - edgeX(targetRight), blob.y - targetY)
            targetPool = poolRadius * (1f - toTarget / reach).coerceIn(0f, 1f)
            if (abs(blob.x - finger.x) < 1.5f && abs(blob.y - finger.y) < 1.5f && abs(vx) + abs(vy) < 40f) {
                finished = true
                done(targetRight, targetY)
                // The tab redraws at its new place first, so removing the drop shows no gap.
                view.postOnAnimation { view.postOnAnimation { runCatching { windows.removeView(view) } } }
                return
            }
        }
        view.invalidate()
        view.postOnAnimation(tick)
    }

    private fun render(canvas: Canvas) {
        canvas.save()
        canvas.translate(-offsetX, -offsetY)
        val bx = blob.x; val by = blob.y
        // Drop: stretched along its motion, squashed across it.
        val speed = hypot(vx, vy)
        val stretch = 1f + min(speed / 2600f, 0.45f)
        val dropRadius = if (settling) radius * (0.62f + 0.38f * (1f - targetPool / poolRadius)) else radius
        canvas.save()
        canvas.rotate(Math.toDegrees(atan2(vy, vx).toDouble()).toFloat(), bx, by)
        oval.set(bx - dropRadius * stretch, by - dropRadius / sqrt(stretch), bx + dropRadius * stretch, by + dropRadius / sqrt(stretch))
        canvas.drawOval(oval, fillPaint)
        canvas.drawOval(oval, rimPaint)
        canvas.restore()
        pool(canvas, sourceRight, originY, originPool, bx, by, dropRadius)
        if (settling) pool(canvas, targetRight, targetY, targetPool, bx, by, dropRadius)
        // The tab's bar rides along in the drop: a line while joined to an edge, a circle once it floats free.
        val joined = (maxOf(originPool, targetPool) / poolRadius).coerceIn(0f, 1f)
        val dot = dropRadius * 0.16f
        val halfH = dot + (dropRadius * 0.42f - dot) * joined
        val halfW = dot + (1.25f * density - dot) * joined
        oval.set(bx - halfW, by - halfH, bx + halfW, by + halfH)
        canvas.drawRoundRect(oval, halfW, halfW, barPaint)
        canvas.restore()
    }

    /** A pool of tar at the screen edge and the neck joining it to the drop while they are close enough. */
    private fun pool(canvas: Canvas, right: Boolean, y: Float, r: Float, bx: Float, by: Float, dropRadius: Float) {
        if (r < 0.5f) return
        val cx = edgeX(right) + if (right) r * 0.35f else -r * 0.35f
        canvas.drawCircle(cx, y, r, fillPaint)
        if (metaball(r, dropRadius, cx, y, bx, by)) canvas.drawPath(path, fillPaint)
    }

    /**
     * The connector between two circles that makes them read as one fluid body: two curves from tangent points
     * on each circle whose handles pull together as the circles part, until they are too far apart.
     */
    private fun metaball(r1: Float, r2: Float, x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val d = hypot(x2 - x1, y2 - y1)
        val max = r1 + r2 * 2.4f
        if (d > max || d <= abs(r1 - r2) || d == 0f) return false
        val v = 0.5f
        val handle = 2.4f
        val u1: Float; val u2: Float
        if (d < r1 + r2) {
            u1 = acos(((r1 * r1 + d * d - r2 * r2) / (2 * r1 * d)).coerceIn(-1f, 1f))
            u2 = acos(((r2 * r2 + d * d - r1 * r1) / (2 * r2 * d)).coerceIn(-1f, 1f))
        } else { u1 = 0f; u2 = 0f }
        val between = atan2(y2 - y1, x2 - x1)
        val spread = acos(((r1 - r2) / d).coerceIn(-1f, 1f))
        val a1 = between + u1 + (spread - u1) * v
        val a2 = between - u1 - (spread - u1) * v
        val a3 = between + PI.toFloat() - u2 - (PI.toFloat() - u2 - spread) * v
        val a4 = between - PI.toFloat() + u2 + (PI.toFloat() - u2 - spread) * v
        fun px(cx: Float, a: Float, r: Float) = cx + cos(a) * r
        fun py(cy: Float, a: Float, r: Float) = cy + sin(a) * r
        val p1x = px(x1, a1, r1); val p1y = py(y1, a1, r1)
        val p2x = px(x1, a2, r1); val p2y = py(y1, a2, r1)
        val p3x = px(x2, a3, r2); val p3y = py(y2, a3, r2)
        val p4x = px(x2, a4, r2); val p4y = py(y2, a4, r2)
        val total = r1 + r2
        val d2 = min(v * handle, hypot(p3x - p1x, p3y - p1y) / total) * min(1f, d * 2 / total)
        val half = (PI / 2).toFloat()
        path.reset()
        path.moveTo(p1x, p1y)
        path.cubicTo(px(p1x, a1 - half, r1 * d2), py(p1y, a1 - half, r1 * d2), px(p3x, a3 + half, r2 * d2), py(p3y, a3 + half, r2 * d2), p3x, p3y)
        path.lineTo(p4x, p4y)
        path.cubicTo(px(p4x, a4 - half, r2 * d2), py(p4y, a4 - half, r2 * d2), px(p2x, a2 + half, r1 * d2), py(p2y, a2 + half, r1 * d2), p2x, p2y)
        path.close()
        return true
    }
}
