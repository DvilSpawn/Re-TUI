package ohi.andre.consolelauncher.wallpaper.pixel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The dot home: a black screen with one blinking dot, drawn on the shared pixel grid. Hold the dot to bring up
 * a ring per app around the dot (up to [MAX_APPS]); slide to a ring and lift to open it, or lift on the dot to
 * cancel. Tap the dot to show the module rings (an analog clock, battery and temperature); double-tap it to search.
 */
class DotHomeView(context: Context) : PixelWallpaperView(context, GRID) {
    /** One ring: what it shows and what opening it does. */
    class Ring(val label: String, val open: () -> Unit)

    companion object {
        const val MAX_APPS = 5
        private const val GRID = 120f
        private const val BLINK_MS = 600L
        private const val REVEAL_MS = 220L
        private const val STAGGER_MS = 45L
    }

    /** Fetched when the rings open, so they follow the latest apps and settings. */
    var rings: () -> List<Ring> = { emptyList() }
    var onSearch: () -> Unit = {}
    /** The current temperature with its unit, e.g. "21°C", or null when weather is not set up. */
    var temperature: () -> String? = { null }

    /** Whether a tap has put the module rings up around the dot. */
    private var modules = false
    private var battery: Pair<Int, Boolean>? = null
    private var batteryReadAt = 0L
    private val toggle = Runnable { toggleModules() }

    private var ink = PixelLook.themeInk()
    private var palette = PixelLook.THEME.colors(ink)
    private var shown = emptyList<Ring>()
    private var holding = false
    private var heldAt = 0L
    private var selected = -1
    private var downX = 0f
    private var downY = 0f
    private var lastTapAt = 0L
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val hold = Runnable { startHold() }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
        typeface = runCatching { ohi.andre.consolelauncher.tuils.Tuils.getTypeface(context) }.getOrNull() ?: Typeface.MONOSPACE
    }
    private val boxPaint = Paint()
    private val box = RectF()

    init {
        isClickable = true
        isFocusable = true
        // Focusable with no background of its own, so Android would otherwise wash it grey as "focused".
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
        contentDescription = context.getString(ohi.andre.consolelauncher.R.string.dot_home_description)
    }

    override fun loadSettings() {
        ink = PixelLook.themeInk()
        palette = PixelLook.THEME.colors(ink)
        invalidate()
    }

    override fun signatureColors(): IntArray = intArrayOf(0xFF000000.toInt(), palette[1], ink)

    override fun frameDelayMs(): Long =
        if (holding) 16L else BLINK_MS - SystemClock.uptimeMillis() % BLINK_MS + 1

    override fun onGrid(width: Int, height: Int) {}

    override fun step(seconds: Float) {}

    // Ring geometry, in screen pixels around the view's centre.
    private val centerX get() = width / 2f
    private val centerY get() = height / 2f
    private val outer get() = minOf(width, height) * 0.44f
    private val inner get() = outer * 0.3f
    private val gap get() = (outer - inner) / (MAX_APPS - 1)
    private fun radius(index: Int) = inner + index * gap
    private val dotHit get() = maxOf(inner - gap / 2f, resources.displayMetrics.density * 40f)

    override fun render() {
        px.fill(palette[0])
        val now = SystemClock.uptimeMillis()
        val cx = gw / 2f
        val cy = gh / 2f
        // The module rings give way while the app rings are up, and come back after.
        val charging = batteryState()?.second == true
        if (modules && !holding) drawModules(cx, cy)
        // The centre: a bolt while charging, otherwise the dot, steady while the app rings are up.
        if (charging) bolt(cx, cy)
        else if (holding || (now / BLINK_MS) % 2 == 0L) disc(cx, cy, 1.6f, if (holding && selected < 0) ink else palette[3])
        if (!holding) return
        shown.indices.forEach { i ->
            val grown = ((now - heldAt - i * STAGGER_MS) / REVEAL_MS.toFloat()).coerceIn(0f, 1f)
            if (grown <= 0f) return@forEach
            val r = radius(i) / cell * grown
            val active = i == selected
            ring(cx, cy, r, if (active) ink else palette[1], dashed = !active)
        }
    }

    // Module ring radii, in grid cells. Hours sit on the outermost app ring's radius.
    private val hourOrbit = 50f
    private val minuteOrbit = 37f
    private val batteryOrbit = 24f

    /**
     * The module rings: hours as twelve dim dots and minutes as sixty, with only the current hour and minute
     * written out, lit, in their place; a battery ring around the dot; the temperature above the 12.
     */
    private fun drawModules(cx: Float, cy: Float) {
        val calendar = java.util.Calendar.getInstance()
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val hour = calendar.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
        for (h in 1..12) {
            val (x, y) = polar(cx, cy, hourOrbit, h * 30f)
            if (h == hour) number(hour.toString(), x, y) else set(x.roundToInt(), y.roundToInt(), palette[1])
        }
        for (m in 0 until 60) {
            // The written minute takes the place of its own dot and the one on each side.
            val distance = minOf(abs(m - minute), 60 - abs(m - minute))
            if (distance <= 1) continue
            val (x, y) = polar(cx, cy, minuteOrbit, m * 6f)
            set(x.roundToInt(), y.roundToInt(), palette[1])
        }
        polar(cx, cy, minuteOrbit, minute * 6f).let { (x, y) -> number("%02d".format(minute), x, y) }
        // Battery: pips filled clockwise from twelve; the next one pulses while charging.
        val (level, charging) = batteryState() ?: (-1 to false)
        val pips = 48
        val lit = if (level < 0) 0 else (level * pips + 50) / 100
        val pulse = charging && (SystemClock.uptimeMillis() / BLINK_MS) % 2 == 0L
        for (i in 0 until pips) {
            val (x, y) = polar(cx, cy, batteryOrbit, i * 360f / pips)
            set(x.roundToInt(), y.roundToInt(), when {
                i < lit -> palette[3]
                i == lit && pulse -> palette[2]
                else -> palette[1]
            })
        }
        // Temperature above the 12, left out when weather is not set up.
        temperature()?.replace(" ", "")?.uppercase(java.util.Locale.US)?.let { value ->
            DotDigits.drawCentred(value, cx.roundToInt(), (cy - hourOrbit - 8f).roundToInt()) { px, py -> set(px, py, palette[2]) }
        }
    }

    private fun number(text: String, x: Float, y: Float) =
        DotDigits.drawCentred(text, x.roundToInt(), y.roundToInt()) { px, py -> set(px, py, palette[3]) }

    /** A point [r] cells from the centre, [degrees] clockwise from twelve. */
    private fun polar(cx: Float, cy: Float, r: Float, degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        return (cx + r * kotlin.math.sin(radians)).toFloat() to (cy - r * kotlin.math.cos(radians)).toFloat()
    }

    /** The dot's stand-in while charging: a 3×5 bolt, about the dot's size. */
    private fun bolt(cx: Float, cy: Float) {
        val left = cx.toInt() - 1
        val top = cy.toInt() - 2
        DotDigits.BOLT.forEachIndexed { row, bits ->
            for (col in 0 until 3) if (bits shr (2 - col) and 1 != 0) set(left + col, top + row, palette[3])
        }
    }

    private fun batteryState(): Pair<Int, Boolean>? {
        val now = SystemClock.uptimeMillis()
        if (battery == null || now - batteryReadAt > 30_000L) {
            batteryReadAt = now
            battery = runCatching {
                val status = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    ?: return@runCatching null
                val level = status.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = status.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                val plugged = status.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (level < 0 || scale <= 0) null else (level * 100 / scale) to plugged
            }.getOrNull()
        }
        return battery
    }

    private fun disc(cx: Float, cy: Float, r: Float, color: Int) {
        val r2 = r * r
        for (y in (cy - r).toInt()..(cy + r).toInt() + 1) for (x in (cx - r).toInt()..(cx + r).toInt() + 1) {
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            if (dx * dx + dy * dy <= r2) set(x, y, color)
        }
    }

    private fun ring(cx: Float, cy: Float, r: Float, color: Int, dashed: Boolean) {
        val top = (cy - r - 1).toInt()
        val bottom = (cy + r + 1).toInt()
        val left = (cx - r - 1).toInt()
        val right = (cx + r + 1).toInt()
        for (y in top..bottom) for (x in left..right) {
            if (abs(hypot(x + 0.5f - cx, y + 0.5f - cy) - r) >= 0.55f) continue
            if (dashed && (x + y) % 2 != 0) continue
            set(x, y, color)
        }
    }

    private fun set(x: Int, y: Int, color: Int) {
        if (x in 0 until gw && y in 0 until gh) px[y * gw + x] = color
    }

    /** App names sit where each ring crosses the top, cut into the ring like a pane title. */
    override fun drawOverlay(canvas: Canvas) {
        if (!holding) return
        val now = SystemClock.uptimeMillis()
        val pad = labelPaint.textSize * 0.35f
        shown.forEachIndexed { i, ring ->
            if (now - heldAt - i * STAGGER_MS < REVEAL_MS) return@forEachIndexed
            val active = i == selected
            val y = centerY - radius(i)
            val half = labelPaint.measureText(ring.label) / 2f + pad
            val metrics = labelPaint.fontMetrics
            box.set(centerX - half, y + metrics.ascent / 2f - pad, centerX + half, y - metrics.ascent / 2f + pad)
            boxPaint.color = if (active) ink else palette[0]
            canvas.drawRect(box, boxPaint)
            labelPaint.color = if (active) palette[0] else palette[2]
            canvas.drawText(ring.label, centerX, y - (metrics.ascent + metrics.descent) / 2f, labelPaint)
        }
    }

    private fun startHold() {
        removeCallbacks(toggle)
        lastTapAt = 0L
        shown = rings().take(MAX_APPS)
        if (shown.isEmpty()) return
        holding = true
        heldAt = SystemClock.uptimeMillis()
        selected = -1
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
        kick()
    }

    /** The frame loop may be waiting out a blink; wake it for smooth reveal frames. */
    private fun kick() {
        removeCallbacks(tick)
        post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            if (holding && SystemClock.uptimeMillis() - heldAt < REVEAL_MS + STAGGER_MS * MAX_APPS) postDelayed(this, 16L)
        }
    }

    private fun pick(x: Float, y: Float): Int {
        val d = hypot(x - centerX, y - centerY)
        // On the dot, or past the outermost ring, nothing is chosen and lifting cancels.
        if (d < inner - gap / 2f || d > radius(shown.size - 1) + gap / 2f) return -1
        return ((d - inner) / gap).roundToInt().coerceIn(0, shown.size - 1)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                // Only a hold on the dot (or the readout in its place) brings up the rings.
                if (inCentre(event.x, event.y)) postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (holding) {
                    val next = pick(event.x, event.y)
                    if (next != selected) {
                        selected = next
                        if (next >= 0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        invalidate()
                    }
                } else if (hypot(event.x - downX, event.y - downY) > slop) {
                    removeCallbacks(hold)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                if (holding) {
                    val chosen = shown.getOrNull(selected)
                    endHold()
                    chosen?.open?.invoke()
                } else if (hypot(event.x - downX, event.y - downY) <= slop) {
                    tap(event.x, event.y, event.eventTime)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                endHold()
            }
        }
        return true
    }

    /** One tap steps the readout once the double-tap window has passed; a second tap in time searches instead. */
    private fun tap(x: Float, y: Float, at: Long) {
        if (!inCentre(x, y)) { lastTapAt = 0L; return }
        if (at - lastTapAt <= ViewConfiguration.getDoubleTapTimeout()) {
            lastTapAt = 0L
            removeCallbacks(toggle)
            onSearch()
        } else {
            lastTapAt = at
            postDelayed(toggle, ViewConfiguration.getDoubleTapTimeout().toLong())
        }
    }

    /** The dot, with a margin for a thumb. */
    private fun inCentre(x: Float, y: Float): Boolean = hypot(x - centerX, y - centerY) <= dotHit

    private fun toggleModules() {
        modules = !modules
        batteryReadAt = 0L
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (modules) announceForAccessibility(describeModules())
        invalidate()
    }

    /** What the module rings show, for TalkBack. */
    private fun describeModules(): String {
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())
        val (level, charging) = batteryState() ?: (-1 to false)
        return listOfNotNull(time, if (level >= 0) "$level%" + if (charging) " ⚡" else "" else null, temperature()).joinToString(", ")
    }

    private fun endHold() {
        if (!holding) return
        holding = false
        selected = -1
        shown = emptyList()
        invalidate()
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) { removeCallbacks(hold); endHold() }
    }

    // TalkBack: activating the view searches, and each app is an action on it.
    override fun performClick(): Boolean {
        super.performClick()
        onSearch()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_BASE + MAX_APPS,
            context.getString(ohi.andre.consolelauncher.R.string.dot_home_toggle_modules)))
        rings().take(MAX_APPS).forEachIndexed { i, ring ->
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_BASE + i, ring.label))
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val index = action - ACTION_BASE
        if (index == MAX_APPS) { toggleModules(); return true }
        if (index in 0 until MAX_APPS) {
            rings().getOrNull(index)?.let { it.open(); return true }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private val ACTION_BASE = 0x00D07000
}
