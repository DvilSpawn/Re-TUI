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
 * cancel. Tap the dot (or the black) to show the module rings (an analog clock, battery, temperature, date and alarm);
 * double-tap the dot to search; hold the black to open the Dot settings. While music plays the dot is its play button,
 * ringed by the track's progress, with ripples pulsing out. Pips orbit it for apps with notifications. Words are drawn
 * in Mono's pixel font, rasterised onto the grid.
 */
class DotHomeView(context: Context) : PixelWallpaperView(context, GRID) {
    /** One ring: what it shows and what opening it does. */
    class Ring(val label: String, val open: () -> Unit)
    /** What is playing: the dot becomes its play button. */
    class Playing(val title: String, val artist: String?, val playing: Boolean, val progress: Float?)

    companion object {
        const val MAX_APPS = 5
        private const val MAX_PIPS = 6
        /** Cells across the short side; fine enough that rings read as circles. Sizes below are for 120 and scale with [S]. */
        private const val GRID = 240f
        private const val S = GRID / 120f
        /** A glyph or pip cell is this many grid cells square, so they keep their size on the finer grid. */
        private const val Z = 2
        private const val RIPPLE_STEP_MS = 60L
        private const val RIPPLE_FROM = 11
        private const val RIPPLE_SPAN = 54
        /** A new ripple leaves the play button every beat. */
        private const val BEAT_MS = 1500L
        /** How long a ripple lives, in beats: this many are out at once, each at its own age. */
        private const val RIPPLE_BEATS = 3
        private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        private const val BREATHE_MS = 2400L
        private const val BREATHE_STEP_MS = 120L
        /** Word sizes, Mono's small and a big one for the app being chosen (sp). */
        private const val SMALL_WORD = 18f
        private const val BIG_WORD = 34f
        private const val BLINK_MS = 600L
        private const val REVEAL_MS = 220L
        private const val STAGGER_MS = 45L
    }

    /** Fetched when the rings open, so they follow the latest apps and settings. */
    var rings: () -> List<Ring> = { emptyList() }
    var onSearch: () -> Unit = {}
    /** The current temperature with its unit, e.g. "21°C", or null when weather is not set up. */
    var temperature: () -> String? = { null }
    /** Apps with notifications waiting, one orbiting pip each. */
    var notified: () -> Int = { 0 }
    var music: () -> Playing? = { null }
    var onPlayPause: () -> Unit = {}
    var onSettings: () -> Unit = {}
    /** Whether the search hint still shows (dot_hint: auto hides it once the user has searched a few times). */
    var hintWanted: () -> Boolean = { true }

    private var blinkStyle = "blink"
    private var ripples = true
    private var ringSet = setOf("clock", "battery", "temperature", "date", "alarm")
    private var pipsOn = true
    private val settingsHold = Runnable {
        settingsHeld = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onSettings()
    }
    private var settingsHeld = false
    private val playPause = Runnable { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onPlayPause(); invalidate() }

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
        ink = runCatching { ohi.andre.consolelauncher.managers.settings.ThemeColorResolver.color(ohi.andre.consolelauncher.managers.xml.options.Theme.dot_ink_color) }
            .getOrDefault(PixelLook.themeInk()).let { if (android.graphics.Color.alpha(it) < 128) PixelLook.themeInk() else it }
        palette = PixelLook.THEME.colors(ink)
        val settings = ohi.andre.consolelauncher.managers.settings.LauncherSettings
        blinkStyle = settings.get(ohi.andre.consolelauncher.managers.xml.options.Behavior.dot_blink)?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty().ifEmpty { "blink" }
        ripples = settings.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Behavior.dot_ripples)
        pipsOn = settings.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Behavior.dot_notifications)
        ringSet = settings.get(ohi.andre.consolelauncher.managers.xml.options.Behavior.dot_rings).orEmpty().split(',').map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
        invalidate()
    }

    /** The dot's ink, for controls drawn beside it. */
    val inkColor: Int get() = ink

    override fun signatureColors(): IntArray = intArrayOf(0xFF000000.toInt(), palette[1], ink)

    override fun frameDelayMs(): Long {
        if (holding) return 16L
        val now = SystemClock.uptimeMillis()
        val tune = music()
        if (tune?.playing == true && ripples) return RIPPLE_STEP_MS
        if (blinkStyle == "breathe") return BREATHE_STEP_MS
        return BLINK_MS - now % BLINK_MS + 1
    }

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
        words.clear()
        val now = SystemClock.uptimeMillis()
        val cx = gw / 2f
        val cy = gh / 2f
        val tune = if (holding) null else music()
        // Ripples go first, under everything else.
        // Each ripple is born on a beat, slows as it spreads, and fades: ink, mid, dim, then thinning into the black.
        if (tune?.playing == true && ripples) for (k in 0 until RIPPLE_BEATS) {
            val age = ((now % (BEAT_MS * RIPPLE_BEATS)) + k * BEAT_MS) % (BEAT_MS * RIPPLE_BEATS) / (BEAT_MS * RIPPLE_BEATS).toFloat()
            val spread = 1f - (1f - age) * (1f - age)
            val life = 1f - age
            val shade = when { life > 0.66f -> palette[3]; life > 0.33f -> palette[2]; else -> palette[1] }
            ring(cx, cy, (RIPPLE_FROM + RIPPLE_SPAN * spread) * S, shade, dashed = true, density = if (life > 0.33f) 1f else life / 0.33f)
        }
        // The module rings give way while the app rings are up, and come back after.
        val charging = batteryState()?.second == true
        if (modules && !holding) drawModules(cx, cy)
        if (!holding && pipsOn) drawPips(cx, cy, now, (if (tune != null) 12f else 9f) * S)
        when {
            tune != null -> drawPlaying(cx, cy, tune)
            // The centre: a bolt while charging, otherwise the dot, steady while the app rings are up.
            charging -> bolt(cx, cy)
            holding -> disc(cx, cy, 1.6f * S, if (selected < 0) ink else palette[3])
            blinkStyle == "steady" -> disc(cx, cy, 1.6f * S, palette[3])
            blinkStyle == "breathe" -> {
                val phase = (now % BREATHE_MS).toFloat() / BREATHE_MS
                val level = 0.5f - 0.5f * kotlin.math.cos(phase * 2f * Math.PI.toFloat())
                disc(cx, cy, 1.6f * S, palette[1 + (level * 2.99f).toInt()])
            }
            (now / BLINK_MS) % 2 == 0L -> disc(cx, cy, 1.6f * S, palette[3])
        }
        if (!holding) {
            if (tune == null && showHint && hintWanted()) word(context.getString(ohi.andre.consolelauncher.R.string.dot_home_search_hint), cx, gh - hintLift() - 6f * S, SMALL_WORD, palette[1])
            return
        }
        shown.getOrNull(selected)?.let { word(it.label, cx, cy - radius(MAX_APPS - 1) / cell - 18f * S, BIG_WORD, ink) }
        shown.indices.forEach { i ->
            val grown = ((now - heldAt - i * STAGGER_MS) / REVEAL_MS.toFloat()).coerceIn(0f, 1f)
            if (grown <= 0f) return@forEach
            val r = radius(i) / cell * grown
            val active = i == selected
            ring(cx, cy, r, if (active) ink else palette[1], dashed = !active)
        }
    }

    /** One pip per app with notifications, stepping round the dot once a blink. */
    private fun drawPips(cx: Float, cy: Float, now: Long, orbit: Float) {
        val count = notified().coerceIn(0, MAX_PIPS)
        val turn = (now / BLINK_MS % 60) * 6f
        for (i in 0 until count) {
            val (x, y) = polar(cx, cy, orbit, turn + i * 360f / count)
            block(x, y, if (i == 0) palette[3] else palette[2])
        }
    }

    /** The dot as the play button: a pixel ▶ (playing) or bars (paused), the track's progress around it, its name below. */
    private fun drawPlaying(cx: Float, cy: Float, tune: Playing) {
        val pips = 32
        val lit = tune.progress?.let { (it * pips).roundToInt() } ?: 0
        for (i in 0 until pips) {
            val (x, y) = polar(cx, cy, 8f * S, i * 360f / pips)
            block(x, y, if (i < lit) palette[3] else palette[1])
        }
        // Drawn in glyph cells (Z grid cells square), so it keeps its size on the finer grid.
        val left = cx.toInt() - Z
        val top = cy.toInt() - 3 * Z
        fun glyph(col: Int, row: Int, color: Int) = fill(left + col * Z, top + row * Z, color)
        if (tune.playing) {
            val h = 14f
            val w = h * 4f / 7f
            val x0 = cx - w * 0.4f
            for (y in (cy - h / 2f).toInt()..(cy + h / 2f).toInt()) {
                val reach = (1f - abs(y + 0.5f - cy) / (h / 2f)) * w
                for (x in x0.toInt() until (x0 + reach).roundToInt()) set(x, y, palette[3])
            }
        } else {
            for (y in 0 until 7) { glyph(-1, y, palette[2]); glyph(2, y, palette[2]) }
        }
        // Below the date and alarm when the rings are up.
        val below = if (modules) cy + 84f * S else cy + 66f * S
        word(tune.title, cx, below, SMALL_WORD, if (tune.playing) palette[3] else palette[2])
        tune.artist?.takeIf { it.isNotBlank() }?.let { word(it, cx, below + 10f * S, SMALL_WORD, palette[2]) }
    }

    // Words are drawn over the grid in Mono's font (VT323) at Mono's sizes, as the ring labels are: on the grid itself
    // the font only stays clean 25 cells tall. render() queues them; drawOverlay() writes them.
    private val wordFace: Typeface = runCatching { Typeface.createFromAsset(context.assets, ohi.andre.consolelauncher.managers.settings.MonoMode.FONT_ASSET) }
        .getOrDefault(Typeface.MONOSPACE)
    private class Word(val text: String, val x: Float, val y: Float, val sp: Float, val color: Int)
    private val words = ArrayList<Word>()
    private val wordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = wordFace; textAlign = Paint.Align.CENTER }

    /** [text] centred on (cx, cy) in grid cells, [sp] tall, ellipsised to the screen's width. */
    private fun word(text: String, cx: Float, cy: Float, sp: Float, color: Int) {
        if (text.isNotBlank()) words += Word(text, cx, cy, sp, color)
    }

    private fun drawWords(canvas: Canvas) {
        for (w in words) {
            wordPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, w.sp, resources.displayMetrics)
            wordPaint.color = w.color
            val text = android.text.TextUtils.ellipsize(w.text, android.text.TextPaint(wordPaint), width * 0.9f, android.text.TextUtils.TruncateAt.END).toString()
            val metrics = wordPaint.fontMetrics
            canvas.drawText(text, screenX(w.x), screenY(w.y) - (metrics.ascent + metrics.descent) / 2f, wordPaint)
        }
    }

    private fun hintLift(): Float {
        val bar = rootWindowInsets?.let { androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(it, this)
            .getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom } ?: 0
        return bar / cell.toFloat() + 4f
    }

    // Module ring radii, in grid cells. Hours sit on the outermost app ring's radius.
    private val hourOrbit = 50f * S
    private val minuteOrbit = 37f * S
    private val batteryOrbit = 24f * S

    /**
     * The module rings: hours as twelve dim dots and minutes as sixty, with only the current hour and minute
     * written out, lit, in their place; a battery ring around the dot; the temperature above the 12.
     */
    private fun drawModules(cx: Float, cy: Float) {
        val calendar = java.util.Calendar.getInstance()
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val hour = calendar.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
        if ("date" in ringSet) {
            word(android.text.format.DateFormat.format("EEE d MMM", calendar).toString().lowercase(), cx, cy + hourOrbit + 10f * S, SMALL_WORD, palette[2])
        }
        if ("alarm" in ringSet) nextAlarm()?.let { word(it, cx, cy + hourOrbit + 20f * S, SMALL_WORD, palette[1]) }
        if ("clock" in ringSet) for (h in 1..12) {
            val (x, y) = polar(cx, cy, hourOrbit, h * 30f)
            if (h == hour) number(hour.toString(), x, y) else block(x, y, palette[1])
        }
        if ("clock" in ringSet) for (m in 0 until 60) {
            // The written minute takes the place of its own dot and the one on each side.
            val distance = minOf(abs(m - minute), 60 - abs(m - minute))
            if (distance <= 1) continue
            val (x, y) = polar(cx, cy, minuteOrbit, m * 6f)
            block(x, y, palette[1])
        }
        if ("clock" in ringSet) polar(cx, cy, minuteOrbit, minute * 6f).let { (x, y) -> number("%02d".format(minute), x, y) }
        // Battery: pips filled clockwise from twelve; the next one pulses while charging.
        val (level, charging) = batteryState() ?: (-1 to false)
        if ("battery" in ringSet) drawBattery(cx, cy, level, charging)
        // Temperature above the 12, left out when weather is not set up.
        if ("temperature" in ringSet) temperature()?.replace(" ", "")?.uppercase(java.util.Locale.US)?.let { value ->
            digits(value, cx, cy - hourOrbit - 8f * S, palette[2])
        }
    }

    /** The next alarm, as "alarm mon 7:00", or null when none is set. */
    private fun nextAlarm(): String? = runCatching {
        val next = (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).nextAlarmClock ?: return null
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(next.triggerTime))
        "alarm " + android.text.format.DateFormat.format("EEE", next.triggerTime).toString().lowercase() + " " + time.lowercase()
    }.getOrNull()

    private fun drawBattery(cx: Float, cy: Float, level: Int, charging: Boolean) {
        val pips = 48
        val lit = if (level < 0) 0 else (level * pips + 50) / 100
        val pulse = charging && (SystemClock.uptimeMillis() / BLINK_MS) % 2 == 0L
        for (i in 0 until pips) {
            val (x, y) = polar(cx, cy, batteryOrbit, i * 360f / pips)
            block(x, y, when {
                i < lit -> palette[3]
                i == lit && pulse -> palette[2]
                else -> palette[1]
            })
        }
    }

    private fun number(text: String, x: Float, y: Float) = digits(text, x, y, palette[3])

    /** The 3×5 digits, each font cell a Z-square block, centred on (x, y). */
    private fun digits(text: String, x: Float, y: Float, color: Int) {
        val left = x.roundToInt() - DotDigits.width(text) * Z / 2
        val top = y.roundToInt() - DotDigits.HEIGHT * Z / 2
        DotDigits.drawCentred(text, DotDigits.width(text) / 2, DotDigits.HEIGHT / 2) { px, py -> fill(left + px * Z, top + py * Z, color) }
    }

    /** A Z-square block with its top-left at (x, y). */
    private fun fill(x: Int, y: Int, color: Int) { for (a in 0 until Z) for (b in 0 until Z) set(x + a, y + b, color) }

    /** A Z-square block centred on (x, y): a pip that keeps its size on the finer grid. */
    private fun block(x: Float, y: Float, color: Int) = fill((x - Z / 2f).roundToInt(), (y - Z / 2f).roundToInt(), color)

    /** A point [r] cells from the centre, [degrees] clockwise from twelve. */
    private fun polar(cx: Float, cy: Float, r: Float, degrees: Float): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        return (cx + r * kotlin.math.sin(radians)).toFloat() to (cy - r * kotlin.math.cos(radians)).toFloat()
    }

    /** The dot's stand-in while charging: a 3×5 bolt, about the dot's size. */
    private fun bolt(cx: Float, cy: Float) {
        val left = cx.toInt() - Z * 3 / 2
        val top = cy.toInt() - Z * 5 / 2
        DotDigits.BOLT.forEachIndexed { row, bits ->
            for (col in 0 until 3) if (bits shr (2 - col) and 1 != 0) fill(left + col * Z, top + row * Z, palette[3])
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

    /** A one-cell ring; [density] below 1 thins it evenly (ordered dither), for a ripple fading out. */
    private fun ring(cx: Float, cy: Float, r: Float, color: Int, dashed: Boolean, density: Float = 1f) {
        val top = (cy - r - 1).toInt()
        val bottom = (cy + r + 1).toInt()
        val left = (cx - r - 1).toInt()
        val right = (cx + r + 1).toInt()
        for (y in top..bottom) for (x in left..right) {
            if (abs(hypot(x + 0.5f - cx, y + 0.5f - cy) - r) >= 0.55f) continue
            // Dashes are measured along the ring, so they stay even all the way round.
            if (dashed && kotlin.math.floor(kotlin.math.atan2(y + 0.5f - cy, x + 0.5f - cx) * r / Z).toInt() % 2 != 0) continue
            if (density < 1f && BAYER[(x and 3) + (y and 3) * 4] / 16f >= density) continue
            set(x, y, color)
        }
    }

    private fun set(x: Int, y: Int, color: Int) {
        if (x in 0 until gw && y in 0 until gh) px[y * gw + x] = color
    }

    /** App names sit where each ring crosses the top, cut into the ring like a pane title. */
    override fun drawOverlay(canvas: Canvas) {
        drawWords(canvas)
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

    /** How to search, small and half see-through at the bottom middle, above the navigation bar. */
    /** Off when the search bar is already on screen beside the dot. */
    var showHint = true
        set(value) { field = value; invalidate() }

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
                settingsHeld = false
                // A hold on the dot (or the readout in its place) brings up the rings; on the black, the settings.
                if (inCentre(event.x, event.y)) postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                else postDelayed(settingsHold, ViewConfiguration.getLongPressTimeout().toLong())
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
                    removeCallbacks(hold); removeCallbacks(settingsHold)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold); removeCallbacks(settingsHold)
                if (settingsHeld) settingsHeld = false
                else if (holding) {
                    val chosen = shown.getOrNull(selected)
                    endHold()
                    chosen?.open?.invoke()
                } else if (hypot(event.x - downX, event.y - downY) <= slop) {
                    tap(event.x, event.y, event.eventTime)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold); removeCallbacks(settingsHold)
                endHold()
            }
        }
        return true
    }

    /**
     * A tap on the black shows or hides the rings at once. On the dot, one tap waits out the double-tap window and then
     * plays or pauses (while there is music) or shows the rings; a second tap in time searches instead.
     */
    private fun tap(x: Float, y: Float, at: Long) {
        if (!inCentre(x, y)) { lastTapAt = 0L; toggleModules(); return }
        if (at - lastTapAt <= ViewConfiguration.getDoubleTapTimeout()) {
            lastTapAt = 0L
            removeCallbacks(toggle); removeCallbacks(playPause)
            onSearch()
        } else {
            lastTapAt = at
            postDelayed(if (music() != null) playPause else toggle, ViewConfiguration.getDoubleTapTimeout().toLong())
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
        if (visibility != VISIBLE) { removeCallbacks(hold); removeCallbacks(settingsHold); endHold() }
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
        music()?.let { info.addAction(AccessibilityNodeInfo.AccessibilityAction(ACTION_BASE + MAX_APPS + 1,
            context.getString(ohi.andre.consolelauncher.R.string.dot_home_now_playing, it.title))) }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val index = action - ACTION_BASE
        if (index == MAX_APPS) { toggleModules(); return true }
        if (index == MAX_APPS + 1) { onPlayPause(); return true }
        if (index in 0 until MAX_APPS) {
            rings().getOrNull(index)?.let { it.open(); return true }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private val ACTION_BASE = 0x00D07000
}
