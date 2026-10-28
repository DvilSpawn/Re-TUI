/*
 * Re:TUI adaptation of MatrixRainView by Boy Offi9.
 * Source: https://github.com/boy-offi9-inc/matrix-rain-view (revision 744a8900e10d1ccfb2a57a8e1d634346d93119df)
 * Upstream license: MIT. See THIRD_PARTY_NOTICES.md.
 *
 * Changes: driven by the wallpaper service's frame loop instead of a ValueAnimator, colour styles and
 * glyph sets read from the wallpaper settings, the trail buffer can render below screen resolution,
 * XML attributes and the Compose wrapper removed.
 */
package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Matrix digital rain. Inside an activity (the picker preview) it runs its own frame loop; as a
 * wallpaper the service calls [advance] and draws it. Touch effects come through [handleTouch].
 */
class MatrixRainView(context: Context) : View(context) {

    enum class CharSet { KATAKANA, BINARY, ALNUM }

    data class Style(val name: String, val rain: Int, val head: Int, val backdrop: Int)

    companion object {
        const val SCENE = "matrix rain"
        const val FRAME_DELAY_MS = 33L

        val STYLES = listOf(
            Style("classic", Color.parseColor("#00FF41"), Color.parseColor("#D8FFE0"), Color.BLACK),
            Style("amber", Color.parseColor("#FFB000"), Color.parseColor("#FFF1C8"), Color.parseColor("#0A0600")),
            Style("ice", Color.parseColor("#58D8FF"), Color.parseColor("#E6FAFF"), Color.parseColor("#00060A")),
            Style("crimson", Color.parseColor("#FF2848"), Color.parseColor("#FFD8DE"), Color.parseColor("#0A0002")),
            Style("violet", Color.parseColor("#B070FF"), Color.parseColor("#F0E4FF"), Color.parseColor("#05000A")),
            Style("mono", Color.parseColor("#D8D8D8"), Color.WHITE, Color.BLACK),
        )
        val STYLE_NAMES = STYLES.map { it.name }

        /** A touch effect: streams bend around a finger. */
        const val TOUCH_AVOID = 1
        /** Glyphs near a finger mutate rapidly and brighten. */
        const val TOUCH_FLICKER = 2
        /** A quick tap sends an expanding ring that scatters and brightens glyphs. */
        const val TOUCH_SHOCKWAVE = 4
        /** Holding a finger down boosts the glitch effect. */
        const val TOUCH_GLITCH = 8

        private const val MAX_TOUCHES = 5
        private const val MAX_WAVES = 4
        private const val MAX_SLICES = 8
        private const val TAP_TIMEOUT_MS = 250L
        private const val WAVE_LIFETIME = 0.9f
    }

    // ---------------- Configuration ----------------

    var style: Style = STYLES[0]
        private set
    val styleName: String get() = style.name

    var charSet: CharSet = CharSet.KATAKANA
        private set

    /** Fall speed multiplier. 1.0 = default. */
    var speed: Float = 1f

    /** Column density multiplier, clamped to [0.1, 3.0]. */
    var density: Float = 1f
        set(value) {
            val clamped = value.coerceIn(0.1f, 3f)
            if (field == clamped) return
            field = clamped
            recalculateColumns()
        }

    /** Whether the head glyph of each column renders with a soft glow. */
    var glowEnabled: Boolean = true

    /** Trail fade strength, clamped to [4, 255]. Lower = longer-lingering trails. */
    var fadeStrength: Int = 32
        set(value) { field = value.coerceIn(4, 255) }

    /** Bitmask of the TOUCH_ flags; 0 turns touch handling off. */
    var touchEffects: Int = TOUCH_AVOID or TOUCH_FLICKER or TOUCH_SHOCKWAVE
        set(value) {
            if (field == value) return
            field = value
            if (value == 0) clearTouchState()
        }

    /** Glitch intensity, 0 (off) to 1. Produces short bursts of horizontal slice shifts. */
    var glitchIntensity: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f) }

    /** The trail buffer's size relative to the view; below 1 trades sharpness for less work per frame. */
    var bufferScale: Float = 0.5f
        set(value) {
            val clamped = value.coerceIn(0.25f, 1f)
            if (field == clamped) return
            field = clamped
            recalculateColumns()
        }

    private val glyphSizeDp = 15f

    // ---------------- Internal state ----------------

    private var glyphPool: CharArray = katakanaPool()
    private var textSizePx = 0f
    private var bufferWidth = 0
    private var bufferHeight = 0

    private var columnCount = 0
    private var columnY = FloatArray(0)          // head row position, fractional for smooth motion
    private var columnSpeed = FloatArray(0)      // per-column speed variance
    private var columnLength = IntArray(0)       // trail length in rows, per column
    private var columnGlyphs: Array<CharArray> = arrayOf()
    private var rowHeight = 0f
    private var colStep = 0f                     // actual spacing: width / columnCount
    private var rows = 0

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val fadePaint = Paint()
    private val backdropPaint = Paint()
    private val echoPaint = Paint().apply { alpha = 90 }
    private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var lastFrameMs = 0L
    private var running = false
    private val frameLoop = object : Runnable {
        override fun run() {
            if (!running) return
            advance()
            invalidate()
            postDelayed(this, FRAME_DELAY_MS)
        }
    }

    // Offscreen buffer that persists between frames; a wallpaper or hardware canvas does not.
    private var trailBitmap: Bitmap? = null
    private var trailCanvas: Canvas? = null

    // Touch state: fixed-size slots, so tracking fingers never allocates. Positions are in buffer pixels.
    private val touchId = IntArray(MAX_TOUCHES)
    private val touchX = FloatArray(MAX_TOUCHES)
    private val touchY = FloatArray(MAX_TOUCHES)
    private val touchAmount = FloatArray(MAX_TOUCHES)   // eased 0..1, fades in on press and out on release
    private val touchHeld = BooleanArray(MAX_TOUCHES)
    private val touchUsed = BooleanArray(MAX_TOUCHES)
    private var activeTouches = 0
    private var touchGlitchBoost = 0f

    private val waveX = FloatArray(MAX_WAVES)
    private val waveY = FloatArray(MAX_WAVES)
    private val waveAge = FloatArray(MAX_WAVES)
    private val waveUsed = BooleanArray(MAX_WAVES)
    private var activeWaves = 0

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f

    private var fxDx = 0f
    private var fxDy = 0f
    private var fxBoost = 0f

    private var glitchBurstLeft = 0f
    private var glitchReshuffleIn = 0f
    private val sliceTop = IntArray(MAX_SLICES)
    private val sliceBottom = IntArray(MAX_SLICES)
    private val sliceShift = IntArray(MAX_SLICES)
    private var sliceCount = 0
    private val srcRect = Rect()
    private val dstRect = Rect()

    init { loadSettings() }

    fun loadSettings() {
        setStyle(RetuiWallpaperSettings.matrixStyle(context))
        setCharSet(RetuiWallpaperSettings.matrixGlyphs(context))
    }

    fun setStyle(name: String) {
        style = STYLES.firstOrNull { it.name == name } ?: STYLES[0]
        headPaint.color = style.head
        fadePaint.color = style.backdrop
        backdropPaint.color = style.backdrop
        trailBitmap?.eraseColor(style.backdrop)
        invalidate()
    }

    fun setCharSet(name: String) {
        val next = CharSet.values().firstOrNull { it.name.equals(name, true) } ?: CharSet.KATAKANA
        if (next == charSet && glyphPool.isNotEmpty()) return
        charSet = next
        glyphPool = when (next) {
            CharSet.KATAKANA -> katakanaPool()
            CharSet.BINARY -> charArrayOf('0', '1')
            CharSet.ALNUM -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray()
        }
        recalculateColumns()
    }

    /** Katakana, then binary, then letters and digits. */
    fun nextCharSet(): String {
        val values = CharSet.values()
        setCharSet(values[(charSet.ordinal + 1) % values.size].name)
        return charSet.name
    }

    @RequiresApi(27)
    fun wallpaperColors(): WallpaperColors =
        WallpaperColors(Color.valueOf(style.rain), Color.valueOf(style.head), Color.valueOf(style.backdrop))

    // ---------------- Glyph pools ----------------

    private fun katakanaPool(): CharArray = (0x30A0..0x30FF).map { it.toChar() }.toCharArray()

    private fun randomGlyph(): Char = glyphPool[Random.nextInt(glyphPool.size)]

    // ---------------- Layout ----------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateColumns()
    }

    private fun ensureTrailBuffer() {
        if (bufferWidth == 0 || bufferHeight == 0) return
        val existing = trailBitmap
        if (existing == null || existing.width != bufferWidth || existing.height != bufferHeight) {
            existing?.recycle()
            val bitmap = Bitmap.createBitmap(bufferWidth, bufferHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(style.backdrop)
            trailBitmap = bitmap
            trailCanvas = Canvas(bitmap)
        }
    }

    private fun recalculateColumns() {
        if (width == 0 || height == 0) return
        bufferWidth = (width * bufferScale).toInt().coerceAtLeast(1)
        bufferHeight = (height * bufferScale).toInt().coerceAtLeast(1)
        ensureTrailBuffer()

        textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, glyphSizeDp, resources.displayMetrics) * bufferScale
        headPaint.textSize = textSizePx
        trailPaint.textSize = textSizePx
        val colWidth = textSizePx * 0.85f
        rowHeight = textSizePx * 1.1f
        val baseColumns = (bufferWidth / colWidth).toInt().coerceAtLeast(1)
        columnCount = (baseColumns * density).toInt().coerceIn(1, baseColumns * 3)
        colStep = bufferWidth.toFloat() / columnCount
        rows = (bufferHeight / rowHeight).toInt().coerceAtLeast(1)

        columnY = FloatArray(columnCount) { Random.nextInt(-rows, rows).toFloat() }
        columnSpeed = FloatArray(columnCount) { 0.6f + Random.nextFloat() * 0.8f }
        columnLength = IntArray(columnCount) { Random.nextInt(8, 24) }
        columnGlyphs = Array(columnCount) { CharArray(rows) { randomGlyph() } }
    }

    // ---------------- Touch ----------------

    private fun has(flag: Int): Boolean = (touchEffects and flag) != 0
    private fun touchRadius() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 96f, resources.displayMetrics) * bufferScale

    fun handleTouch(event: MotionEvent) {
        if (touchEffects == 0) return
        val s = bufferScale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = event.eventTime
                downX = event.x
                downY = event.y
                pressPointer(event.getPointerId(0), event.x * s, event.y * s)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                pressPointer(event.getPointerId(i), event.getX(i) * s, event.getY(i) * s)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) movePointer(event.getPointerId(i), event.getX(i) * s, event.getY(i) * s)
            }
            MotionEvent.ACTION_POINTER_UP -> releasePointer(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_UP -> {
                releasePointer(event.getPointerId(event.actionIndex))
                if (has(TOUCH_SHOCKWAVE) && isTap(event)) spawnWave(event.x * s, event.y * s)
            }
            MotionEvent.ACTION_CANCEL -> releaseAllPointers()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (touchEffects == 0) return super.onTouchEvent(event)
        handleTouch(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun isTap(event: MotionEvent): Boolean {
        val dx = event.x - downX
        val dy = event.y - downY
        return event.eventTime - downTime < TAP_TIMEOUT_MS && dx * dx + dy * dy < touchSlop.toFloat() * touchSlop
    }

    private fun findSlot(id: Int): Int {
        for (i in 0 until MAX_TOUCHES) if (touchUsed[i] && touchId[i] == id) return i
        return -1
    }

    private fun pressPointer(id: Int, x: Float, y: Float) {
        var slot = findSlot(id)
        if (slot < 0) {
            for (i in 0 until MAX_TOUCHES) if (!touchUsed[i]) { slot = i; break }
        }
        if (slot < 0) {
            // All slots busy: reuse the faintest released one, if any.
            var faintest = 2f
            for (i in 0 until MAX_TOUCHES) {
                if (!touchHeld[i] && touchAmount[i] < faintest) { faintest = touchAmount[i]; slot = i }
            }
        }
        if (slot < 0) return
        if (!touchUsed[slot]) touchAmount[slot] = 0f
        touchUsed[slot] = true
        touchHeld[slot] = true
        touchId[slot] = id
        touchX[slot] = x
        touchY[slot] = y
        recountTouches()
    }

    private fun movePointer(id: Int, x: Float, y: Float) {
        val slot = findSlot(id)
        if (slot < 0) return
        touchX[slot] = x
        touchY[slot] = y
    }

    private fun releasePointer(id: Int) {
        val slot = findSlot(id)
        if (slot >= 0) touchHeld[slot] = false
    }

    private fun releaseAllPointers() {
        for (i in 0 until MAX_TOUCHES) touchHeld[i] = false
    }

    private fun recountTouches() {
        var count = 0
        for (i in 0 until MAX_TOUCHES) if (touchUsed[i]) count++
        activeTouches = count
    }

    private fun clearTouchState() {
        for (i in 0 until MAX_TOUCHES) {
            touchUsed[i] = false
            touchHeld[i] = false
            touchAmount[i] = 0f
        }
        for (i in 0 until MAX_WAVES) waveUsed[i] = false
        activeTouches = 0
        activeWaves = 0
        touchGlitchBoost = 0f
    }

    private fun spawnWave(x: Float, y: Float) {
        var slot = -1
        var oldest = -1f
        for (i in 0 until MAX_WAVES) {
            if (!waveUsed[i]) { slot = i; break }
            if (waveAge[i] > oldest) { oldest = waveAge[i]; slot = i }
        }
        waveUsed[slot] = true
        waveX[slot] = x
        waveY[slot] = y
        waveAge[slot] = 0f
        var count = 0
        for (i in 0 until MAX_WAVES) if (waveUsed[i]) count++
        activeWaves = count
    }

    /** Eases fingers in and out and ages shockwaves. */
    private fun updateInteraction(delta: Float) {
        var strongest = 0f
        if (activeTouches > 0) {
            for (i in 0 until MAX_TOUCHES) {
                if (!touchUsed[i]) continue
                touchAmount[i] = if (touchHeld[i]) (touchAmount[i] + delta * 8f).coerceAtMost(1f) else touchAmount[i] - delta * 5f
                if (!touchHeld[i] && touchAmount[i] <= 0f) {
                    touchUsed[i] = false
                    touchAmount[i] = 0f
                } else {
                    strongest = max(strongest, touchAmount[i])
                }
            }
            recountTouches()
        }
        if (activeWaves > 0) {
            var count = 0
            for (i in 0 until MAX_WAVES) {
                if (!waveUsed[i]) continue
                waveAge[i] += delta
                if (waveAge[i] >= WAVE_LIFETIME) waveUsed[i] = false else count++
            }
            activeWaves = count
        }
        touchGlitchBoost = if (has(TOUCH_GLITCH)) strongest else 0f
    }

    /** How much a glyph centred at ([cx], [cy]) is pushed and brightened. Writes [fxDx], [fxDy], [fxBoost]. */
    private fun computeFx(cx: Float, cy: Float) {
        fxDx = 0f
        fxDy = 0f
        fxBoost = 0f
        val r = touchRadius()
        val r2 = r * r

        if (activeTouches > 0) {
            val avoid = has(TOUCH_AVOID)
            val flicker = has(TOUCH_FLICKER)
            if (avoid || flicker) {
                for (i in 0 until MAX_TOUCHES) {
                    if (!touchUsed[i]) continue
                    val dx = cx - touchX[i]
                    val dy = cy - touchY[i]
                    val d2 = dx * dx + dy * dy
                    if (d2 >= r2) continue
                    val amount = touchAmount[i]
                    if (avoid) {
                        // Radial "parting": distance d maps to d + hole * (1 - d/r)^2, which stays
                        // monotonic for hole <= r/2, so glyphs bunch up at the edge but never cross.
                        val d = sqrt(d2)
                        val t = 1f - d / r
                        val push = 0.5f * r * amount * t * t
                        if (d > 0.001f) {
                            fxDx += dx / d * push
                            fxDy += dy / d * push
                        } else {
                            fxDx += push
                        }
                    }
                    if (flicker) {
                        val w = 1f - d2 / r2
                        fxBoost = max(fxBoost, w * w * amount)
                    }
                }
            }
        }

        if (activeWaves > 0 && has(TOUCH_SHOCKWAVE)) {
            val waveSpeed = r * 3f
            val band = r * 0.35f
            for (i in 0 until MAX_WAVES) {
                if (!waveUsed[i]) continue
                val dx = cx - waveX[i]
                val dy = cy - waveY[i]
                val d = sqrt(dx * dx + dy * dy)
                val off = (d - waveAge[i] * waveSpeed) / band
                if (off <= -1f || off >= 1f) continue
                val shape = 1f - off * off
                val w = shape * shape * (1f - waveAge[i] / WAVE_LIFETIME)
                val push = w * textSizePx * 1.2f
                if (d > 0.001f) {
                    fxDx += dx / d * push
                    fxDy += dy / d * push
                }
                fxBoost = max(fxBoost, w)
            }
        }
    }

    /** Makes glyphs near each finger mutate rapidly. */
    private fun flickerNearTouches() {
        if (!has(TOUCH_FLICKER) || activeTouches == 0 || columnCount == 0 || rows == 0) return
        val r = touchRadius()
        val r2 = r * r
        val glyphHalf = textSizePx * 0.3f
        for (i in 0 until MAX_TOUCHES) {
            if (!touchUsed[i]) continue
            val amount = touchAmount[i]
            val c0 = ((touchX[i] - r) / colStep).toInt().coerceIn(0, columnCount - 1)
            val c1 = ((touchX[i] + r) / colStep).toInt().coerceIn(0, columnCount - 1)
            val r0 = ((touchY[i] - r) / rowHeight).toInt().coerceIn(0, rows - 1)
            val r1 = ((touchY[i] + r) / rowHeight).toInt().coerceIn(0, rows - 1)
            for (c in c0..c1) {
                val glyphs = columnGlyphs[c]
                val dx = c * colStep + glyphHalf - touchX[i]
                for (row in r0..r1) {
                    if (row >= glyphs.size) break
                    val dy = (row + 0.5f) * rowHeight - touchY[i]
                    val d2 = dx * dx + dy * dy
                    if (d2 >= r2) continue
                    val w = 1f - d2 / r2
                    if (Random.nextFloat() < 0.5f * w * w * amount) glyphs[row] = randomGlyph()
                }
            }
        }
    }

    // ---------------- Glitch ----------------

    private fun updateGlitch(delta: Float) {
        val intensity = (glitchIntensity + touchGlitchBoost * 0.7f).coerceAtMost(1f)
        if (intensity <= 0.001f) {
            glitchBurstLeft = 0f
            sliceCount = 0
            return
        }
        if (glitchBurstLeft > 0f) {
            glitchBurstLeft -= delta
            glitchReshuffleIn -= delta
            if (glitchBurstLeft <= 0f) {
                sliceCount = 0
            } else if (glitchReshuffleIn <= 0f) {
                buildSlices(intensity)
                glitchReshuffleIn = 0.04f
            }
        } else if (Random.nextFloat() < intensity * 3f * delta) {
            glitchBurstLeft = 0.08f + Random.nextFloat() * 0.2f * (0.5f + intensity)
            buildSlices(intensity)
            glitchReshuffleIn = 0.04f
        }
    }

    /** Picks non-overlapping bands (top to bottom) and a sideways shift for each, in buffer pixels. */
    private fun buildSlices(intensity: Float) {
        val w = bufferWidth
        val h = bufferHeight
        if (w <= 0 || h <= 0) {
            sliceCount = 0
            return
        }
        val wanted = (2 + intensity * 5f).toInt().coerceAtMost(MAX_SLICES)
        val maxShift = (w * (0.02f + 0.13f * intensity)).toInt().coerceAtLeast(1)
        var y = Random.nextInt(0, (h / 4).coerceAtLeast(1))
        var n = 0
        while (n < wanted && y < h) {
            val sliceHeight = (h * (0.02f + Random.nextFloat() * 0.12f)).toInt().coerceAtLeast(2)
            val bottom = (y + sliceHeight).coerceAtMost(h)
            sliceTop[n] = y
            sliceBottom[n] = bottom
            sliceShift[n] = Random.nextInt(-maxShift, maxShift + 1)
            n++
            y = bottom + Random.nextInt(h / 20 + 1, h / 4 + 2)
        }
        sliceCount = n
    }

    private fun drawBand(canvas: Canvas, bitmap: Bitmap, top: Int, bottom: Int, shift: Int, paint: Paint) {
        val k = 1f / bufferScale
        srcRect.set(0, top, bitmap.width, bottom)
        dstRect.set(((shift) * k).toInt(), (top * k).toInt(), ((bitmap.width + shift) * k).toInt(), (bottom * k).toInt())
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
    }

    private fun blit(canvas: Canvas, bitmap: Bitmap) {
        val h = bitmap.height
        if (sliceCount == 0) {
            drawBand(canvas, bitmap, 0, h, 0, blitPaint)
            return
        }
        val k = 1f / bufferScale
        var cursor = 0
        for (i in 0 until sliceCount) {
            val top = sliceTop[i].coerceIn(cursor, h)
            val bottom = sliceBottom[i].coerceIn(top, h)
            if (top > cursor) drawBand(canvas, bitmap, cursor, top, 0, blitPaint)
            if (bottom > top) {
                // The shift exposes a strip at one edge; keep it the backdrop colour.
                canvas.drawRect(0f, top * k, width.toFloat(), bottom * k, backdropPaint)
                drawBand(canvas, bitmap, top, bottom, sliceShift[i], blitPaint)
                drawBand(canvas, bitmap, top, bottom, -sliceShift[i] / 2, echoPaint)
            }
            cursor = bottom
        }
        if (cursor < h) drawBand(canvas, bitmap, cursor, h, 0, blitPaint)
    }

    // ---------------- Animation ----------------

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); updateAnimation() }

    private fun updateAnimation() {
        running = isAttachedToWindow && windowVisibility == VISIBLE && hasWindowFocus()
        removeCallbacks(frameLoop)
        resetClock()
        if (running) post(frameLoop)
    }

    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }

    fun release() {
        running = false
        removeCallbacks(frameLoop)
        clearTouchState()
        glitchBurstLeft = 0f
        sliceCount = 0
        trailBitmap?.recycle()
        trailBitmap = null
        trailCanvas = null
    }

    /** Forget the last frame time, so time spent hidden is not replayed as one long step. */
    fun resetClock() { lastFrameMs = 0L }

    fun advance(nowMs: Long = SystemClock.uptimeMillis()) {
        if (trailBitmap == null) recalculateColumns()
        val delta = if (lastFrameMs == 0L) 0f else ((nowMs - lastFrameMs) / 1000f).coerceAtMost(0.1f)
        lastFrameMs = nowMs

        updateInteraction(delta)
        updateGlitch(delta)

        if (rows > 0) {
            for (c in 0 until columnCount) {
                columnY[c] += delta * 12f * speed * columnSpeed[c]

                if (columnY[c] - columnLength[c] > rows) {
                    val resetSpread = (rows / 2).coerceAtLeast(1)
                    columnY[c] = Random.nextInt(-resetSpread, 0).toFloat()
                    columnSpeed[c] = 0.6f + Random.nextFloat() * 0.8f
                    columnLength[c] = Random.nextInt(8, 24)
                }

                // Random glyph swaps give the flicker.
                if (Random.nextFloat() < 0.05f) {
                    val glyphs = columnGlyphs[c]
                    if (glyphs.isNotEmpty()) glyphs[Random.nextInt(glyphs.size)] = randomGlyph()
                }
            }
            flickerNearTouches()
        }
    }

    // ---------------- Drawing ----------------

    override fun onDraw(canvas: Canvas) {
        val buffer = trailCanvas ?: return
        val bitmap = trailBitmap ?: return

        // Paint the translucent backdrop over the previous frame instead of clearing it; that is
        // what makes the trails fade.
        fadePaint.alpha = fadeStrength
        buffer.drawRect(0f, 0f, bufferWidth.toFloat(), bufferHeight.toFloat(), fadePaint)

        val interactive = activeTouches > 0 || activeWaves > 0
        val glyphHalf = textSizePx * 0.3f

        for (c in 0 until columnCount) {
            val headRow = columnY[c].toInt()
            val baseX = c * colStep
            val glyphs = columnGlyphs[c]
            val trailLen = columnLength[c]

            for (i in 0..trailLen) {
                val row = headRow - i
                if (row < 0 || row >= rows || row >= glyphs.size) continue
                val baseY = row * rowHeight + rowHeight

                var x = baseX
                var y = baseY
                var boost = 0f
                if (interactive) {
                    computeFx(baseX + glyphHalf, baseY - rowHeight * 0.4f)
                    x += fxDx
                    y += fxDy
                    boost = fxBoost
                }

                if (i == 0) {
                    headPaint.alpha = 255
                    if (glowEnabled) headPaint.setShadowLayer(8f * bufferScale, 0f, 0f, style.rain) else headPaint.clearShadowLayer()
                    buffer.drawText(glyphs, row, 1, x, y, headPaint)
                } else {
                    val fade = (1f - i.toFloat() / trailLen).coerceIn(0f, 1f)
                    trailPaint.color = style.rain
                    trailPaint.alpha = (fade * 200 + boost * 120).toInt().coerceAtMost(255)
                    buffer.drawText(glyphs, row, 1, x, y, trailPaint)
                }
            }
        }

        blit(canvas, bitmap)
    }
}
