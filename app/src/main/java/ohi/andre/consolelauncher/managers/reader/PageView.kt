package ohi.andre.consolelauncher.managers.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.TextPaint
import android.text.TextUtils
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.view.ActionMode
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.widget.OverScroller
import android.widget.Toast
import java.text.BreakIterator
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One page of a book on e-ink paper: a running head, the page, and a footer with the place in the book. Turning
 * leaves the last pages faintly behind the new one, the way an e-ink screen ghosts between full refreshes. In scroll
 * mode the book is one long column instead. A long press selects a word, for copying or handing to another app.
 */
@SuppressLint("ViewConstructor")
class PageView(context: Context, private val chrome: Typeface) : View(context) {
    /** What a page shows: lines of a laid-out chapter, or a picture. */
    sealed class Page {
        class Text(val layout: Layout, val top: Int, val bottom: Int) : Page()
        class Picture(val bitmap: Bitmap) : Page()
    }

    /**
     * The book as one long column, for scrolling: chapters or pages one after another at the page's width. A piece is
     * text when it has a layout, or a picture once its bitmap is ready.
     */
    interface Strip {
        val count: Int
        fun height(index: Int): Int
        fun layout(index: Int): Layout?
        fun bitmap(index: Int): Bitmap?
    }

    /** Pinned passages: the characters pinned in a layout, and pinning or unpinning a selection there. */
    interface Pins {
        fun ranges(layout: Layout): List<IntRange>
        fun toggle(layout: Layout, start: Int, end: Int, text: String)
    }

    var pins: Pins? = null

    /** Taps on the left edge, the middle and the right edge of the page: -1, 0 and 1. */
    var onTap: (Int) -> Unit = {}
    /** A tap on the footer. */
    var onFooter: () -> Unit = {}
        /** A sideways swipe: 1 for a swipe to the left, -1 to the right. */
    var onSwipe: (Int) -> Unit = {}
    /** Scrolling moved to [offset] pixels into piece [piece]; [settled] once the finger is up and the fling done. */
    var onScrolled: (piece: Int, offset: Int, settled: Boolean) -> Unit = { _, _, _ -> }
    /** A picture page was zoomed in or out; the reader may draw it again sharper. */
    var onZoom: (Float) -> Unit = {}

    var head = ""
    var chapter = ""
    var footLeft = ""
    var footRight = ""
    var progress = 0f

    private var paper = Color.WHITE
    private var ink = Color.BLACK
    private var dim = Color.GRAY
    private var page: Page? = null
    private val ghosts = ArrayList<Pair<Page, Float>>()
    private var ghostStrength = 0f

    /** A full refresh every this many turns; 0 never. */
    var refreshEvery = 0
    private var turns = 0
    /** While refreshing: when it began, and the page that replaces the old one halfway through. */
    private var refreshStart = -1L
    private var pending: Page? = null
    private val washPaint = Paint()
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    // Scrolling: the strip, and how far into which piece the top of the page is.
    private var strip: Strip? = null
    private var piece = 0
    private var offset = 0f
    private val scroller = OverScroller(context)
    private var flingY = 0

    // Selected text: the layout it is in, its ends, and the handles last drawn, in view coordinates.
    private var selLayout: Layout? = null
    private var selStart = 0
    private var selEnd = 0
    private val selPath = Path()
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startHandle = android.graphics.PointF(-1f, -1f)
    private val endHandle = android.graphics.PointF(-1f, -1f)
    private var dragging = 0
    private var actionMode: ActionMode? = null
    val scrolling: Boolean get() = strip != null

    private val density = resources.displayMetrics.density
    private val chromePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = chrome; textSize = 10 * resources.displayMetrics.scaledDensity }
    private val linePaint = Paint()
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pinDots = android.graphics.DashPathEffect(floatArrayOf(0.1f * density, 3.5f * density), 0f)
    private val pinFill = Paint(Paint.ANTI_ALIAS_FLAG)
    /** A push pin on a 12 by 14 grid: head, collar, point. */
    private val pinGlyph = Path().apply {
        moveTo(3f, 0f); lineTo(9f, 0f); lineTo(9f, 2f); lineTo(8f, 2f); lineTo(8f, 6f); lineTo(11f, 9f); lineTo(11f, 10f)
        lineTo(7f, 10f); lineTo(7f, 14f); lineTo(5f, 14f); lineTo(5f, 10f); lineTo(1f, 10f); lineTo(1f, 9f); lineTo(4f, 6f)
        lineTo(4f, 2f); lineTo(3f, 2f); close()
    }
    private val grainPaint = Paint()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val grain: Bitmap = Bitmap.createBitmap(GRAIN, GRAIN, Bitmap.Config.ARGB_8888).apply {
        val random = Random(7)
        val pixels = IntArray(GRAIN * GRAIN) {
            when (random.nextInt(9)) { 0 -> Color.BLACK; 1 -> Color.WHITE; else -> Color.TRANSPARENT }
        }
        setPixels(pixels, 0, GRAIN, 0, 0, GRAIN, GRAIN)
    }

    /** Where the page itself goes, inside the margins, under the head and over the footer. */
    val content = RectF()
    var marginPx = 0
        set(value) { field = value; measureContent() }

    fun colours(paper: Int, ink: Int, grainAlpha: Int, ghost: Float) {
        this.paper = paper
        this.ink = ink
        dim = ColorUtils.blendARGB(paper, ink, 0.6f)
        grainPaint.shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        grainPaint.alpha = grainAlpha
        ghostStrength = ghost
        if (ghost == 0f) ghosts.clear()
        invalidate()
    }

    /** Shows [next]; with [turned], the page it replaces stays behind as a ghost. */
    fun show(next: Page?, turned: Boolean) {
        strip = null
        scroller.forceFinished(true)
        clearSelection()
        finishRefresh()
        val old = page
        if (turned && old != null && refreshEvery > 0 && ++turns % refreshEvery == 0) {
            // A full refresh: the old page washes out, the new one comes in under it, and no ghost is left.
            zoom = 1f; panX = 0f; panY = 0f
            pending = next
            refreshStart = SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
            return
        }
        if (turned && old != null && ghostStrength > 0f) {
            for (i in ghosts.indices) ghosts[i] = ghosts[i].first to ghosts[i].second * FADE
            ghosts.removeAll { it.second < MIN_GHOST }
            ghosts.add(0, old to ghostStrength)
            while (ghosts.size > MAX_GHOSTS) ghosts.removeAt(ghosts.lastIndex)
        }
        if (turned) { zoom = 1f; panX = 0f; panY = 0f }
        page = next
        invalidate()
    }

    /** A sharper copy of the picture now showing, with the zoom kept. */
    fun sharpen(bitmap: Bitmap) {
        if (page is Page.Picture) { page = Page.Picture(bitmap); invalidate() }
    }

    fun clearGhosts() { ghosts.clear(); invalidate() }

    /** Shows the book as a scrolling column, starting [offset] pixels into piece [piece]. */
    fun showStrip(next: Strip, piece: Int, offset: Float) {
        finishRefresh()
        clearSelection()
        scroller.forceFinished(true)
        page = null
        ghosts.clear()
        strip = next
        this.piece = piece.coerceIn(0, max(0, next.count - 1))
        this.offset = offset
        settle()
        invalidate()
    }

    /** Moves the column by a screen, less a line or so to keep the reader's place. */
    fun scrollPage(direction: Int) {
        if (strip == null) return
        scroller.forceFinished(true)
        scrollBy(direction * content.height() * PAGE_SCROLL)
        scrollSettled()
    }

    private fun scrollSettled() {
        onScrolled(piece, offset.toInt(), true)
        val layout = selLayout ?: return
        // The toolbar comes back over the selection where it now is, or goes with it once scrolled away.
        val origin = originOf(layout)
        val shown = origin != null && origin + layout.getLineTop(layout.getLineForOffset(min(selStart, selEnd))) < content.bottom &&
            origin + layout.getLineBottom(layout.getLineForOffset(max(selStart, selEnd))) > content.top
        if (!shown) clearSelection() else actionMode?.let { it.invalidateContentRect(); it.hide(0) }
    }

    private fun scrollBy(dy: Float) {
        if (selecting) actionMode?.hide(60_000L)
        offset += dy
        settle()
        invalidate()
    }

    /** Carries the offset across piece boundaries, and keeps the column from running off either end. */
    private fun settle() {
        val s = strip ?: return
        if (s.count == 0) { piece = 0; offset = 0f; return }
        while (offset < 0 && piece > 0) { piece--; offset += s.height(piece) }
        while (piece < s.count - 1 && offset >= s.height(piece)) { offset -= s.height(piece); piece++ }
        if (offset < 0) offset = 0f
        var below = s.height(piece) - offset
        var i = piece + 1
        while (below < content.height() && i < s.count) { below += s.height(i); i++ }
        if (below < content.height()) {
            offset -= content.height() - below
            while (offset < 0 && piece > 0) { piece--; offset += s.height(piece) }
            if (offset < 0) offset = 0f
        }
    }

    override fun computeScroll() {
        if (strip == null || !scroller.computeScrollOffset()) return
        val y = scroller.currY
        scrollBy((y - flingY).toFloat())
        flingY = y
        if (scroller.isFinished) scrollSettled() else { onScrolled(piece, offset.toInt(), false); postInvalidateOnAnimation() }
    }

    // ---- selection ----

    val selecting: Boolean get() = selLayout != null

    fun clearSelection() {
        if (selLayout == null) return
        selLayout = null
        dragging = 0
        actionMode?.finish()
        actionMode = null
        invalidate()
    }

    /** Where the top of [layout] is on screen, if it is showing. */
    private fun originOf(layout: Layout): Float? {
        (page as? Page.Text)?.let { return if (it.layout === layout) content.top - layout.getLineTop(it.top) else null }
        val s = strip ?: return null
        var y = content.top - offset
        var i = piece
        while (i < s.count && y < content.bottom) {
            if (s.layout(i) === layout) return y
            y += s.height(i); i++
        }
        return null
    }

    /** The text under a point: its layout and the character there. */
    private fun textAt(x: Float, y: Float): Pair<Layout, Int>? {
        if (y < content.top || y > content.bottom) return null
        val text = page as? Page.Text
        if (text != null) {
            val origin = content.top - text.layout.getLineTop(text.top)
            val line = text.layout.getLineForVertical((y - origin).toInt()).coerceIn(text.top, max(text.top, text.bottom - 1))
            return text.layout to text.layout.getOffsetForHorizontal(line, x - content.left)
        }
        val s = strip ?: return null
        var top = content.top - offset
        var i = piece
        while (i < s.count && top < content.bottom) {
            val h = s.height(i)
            if (y < top + h) {
                val layout = s.layout(i) ?: return null
                val line = layout.getLineForVertical((y - top).toInt())
                return layout to layout.getOffsetForHorizontal(line, x - content.left)
            }
            top += h; i++
        }
        return null
    }

    /** Selects the word at a point and offers what can be done with it. */
    private fun selectWordAt(x: Float, y: Float): Boolean {
        val (layout, at) = textAt(x, y) ?: return false
        val text = layout.text
        val from = max(0, at - WORD_WINDOW)
        val to = min(text.length, at + WORD_WINDOW)
        val words = BreakIterator.getWordInstance().apply { setText(text.subSequence(from, to).toString()) }
        val local = (at - from).coerceIn(0, to - from)
        var end = words.following(local.coerceAtMost(max(0, to - from - 1))).takeIf { it != BreakIterator.DONE } ?: (to - from)
        var start = words.preceding(min(end, local + 1)).takeIf { it != BreakIterator.DONE } ?: 0
        if (start >= end) return false
        // A press on the space between words takes the word before it.
        if (text.subSequence(from + start, from + end).isBlank()) {
            end = start
            start = words.preceding(end).takeIf { it != BreakIterator.DONE } ?: return false
        }
        // The old toolbar goes first: closing it would otherwise take the new selection with it.
        actionMode?.finish()
        actionMode = null
        selLayout = layout
        selStart = from + start
        selEnd = from + end
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
        actionMode = startActionMode(selectionActions, ActionMode.TYPE_FLOATING)
        return true
    }

    /** Let go, the handles settle on whole words, as text fields do. */
    private fun snapToWords() {
        val text = selLayout?.text ?: return
        val from = max(0, selStart - WORD_WINDOW)
        val to = min(text.length, selEnd + WORD_WINDOW)
        val words = BreakIterator.getWordInstance().apply { setText(text.subSequence(from, to).toString()) }
        val a = selStart - from
        val b = selEnd - from
        if (!words.isBoundary(a)) selStart = from + words.preceding(a).coerceAtLeast(0)
        if (!words.isBoundary(b)) selEnd = from + words.following(b).let { if (it == BreakIterator.DONE) to - from else it }
    }

    private fun selectedText(): String = selLayout?.text?.subSequence(min(selStart, selEnd), max(selStart, selEnd))?.toString().orEmpty()

    /** Whether the selection touches a pinned passage, so the toolbar offers to unpin it. */
    private fun selectionPinned(): Boolean {
        val layout = selLayout ?: return false
        val a = min(selStart, selEnd)
        val b = max(selStart, selEnd)
        return pins?.ranges(layout)?.any { it.first < b && it.last >= a } == true
    }

    /** Copy, share, pin, and every app that offers to work on selected text, Translate among them. */
    private val selectionActions = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = true

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            menu.add(Menu.NONE, COPY, 0, android.R.string.copy)
            menu.add(Menu.NONE, SHARE, 1, context.getString(ohi.andre.consolelauncher.R.string.reader_share))
            if (pins != null) menu.add(Menu.NONE, PIN, 2, context.getString(
                if (selectionPinned()) ohi.andre.consolelauncher.R.string.reader_unpin else ohi.andre.consolelauncher.R.string.reader_pin))
            val pm = context.packageManager
            val ask = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            runCatching { pm.queryIntentActivities(ask, 0) }.getOrDefault(emptyList()).forEachIndexed { i, info ->
                menu.add(Menu.NONE, APPS + i, 3 + i, info.loadLabel(pm)).intent = Intent(ask)
                    .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText())
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            }
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val text = selectedText()
            when (item.itemId) {
                COPY -> {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(head, text))
                    // Android 13 and later show their own note of what was copied.
                    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, ohi.andre.consolelauncher.R.string.reader_copied, Toast.LENGTH_SHORT).show()
                }
                SHARE -> runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }
                PIN -> selLayout?.let { pins?.toggle(it, min(selStart, selEnd), max(selStart, selEnd), text) }
                else -> item.intent?.let { runCatching { context.startActivity(it.putExtra(Intent.EXTRA_PROCESS_TEXT, text)) } }
            }
            clearSelection()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            if (actionMode !== mode) return
            actionMode = null
            // Dismissed from outside, by back or a tap elsewhere: the selection goes with it, unless a handle is held.
            if (dragging == 0) { selLayout = null; invalidate() }
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            val layout = selLayout ?: return
            val origin = originOf(layout) ?: content.top
            val first = layout.getLineForOffset(min(selStart, selEnd))
            val last = layout.getLineForOffset(max(selStart, max(selEnd - 1, 0)))
            outRect.set(content.left.toInt(), (origin + layout.getLineTop(first)).toInt().coerceAtLeast(0),
                content.right.toInt(), (origin + layout.getLineBottom(last)).toInt().coerceAtMost(height))
        }
    }

    /** The selection under the text, and its two handles. */
    private fun drawSelection(canvas: Canvas, layout: Layout, originX: Float, originY: Float) {
        if (layout !== selLayout) return
        val a = min(selStart, selEnd)
        val b = max(selStart, selEnd)
        val radius = HANDLE_DP * density
        handlePaint.color = ink
        fun handle(at: Int, end: Boolean, out: android.graphics.PointF) {
            var line = layout.getLineForOffset(at)
            var x = layout.getPrimaryHorizontal(at)
            if (end && at > a && at == layout.getLineStart(line) && line > 0) { line--; x = layout.getLineRight(line) }
            val cx = originX + x
            val top = originY + layout.getLineBottom(line)
            canvas.drawRect(cx - density / 2, top - density, cx + density / 2, top + radius, handlePaint)
            canvas.drawCircle(cx, top + radius * 1.6f, radius, handlePaint)
            out.set(cx, top + radius * 1.6f)
        }
        handle(a, false, startHandle)
        handle(b, true, endHandle)
    }

    /**
     * Pinned passages, dotted under their lines, with a pin in the margin by the first line of each. Drawn with the
     * canvas at the top left of [layout]; lines outside [first] until [last] are left out.
     */
    private fun drawPins(canvas: Canvas, layout: Layout, first: Int = 0, last: Int = layout.lineCount) {
        val ranges = pins?.ranges(layout).orEmpty()
        if (ranges.isEmpty()) return
        pinPaint.color = ink
        pinPaint.strokeWidth = max(1f, 1.5f * density)
        pinPaint.pathEffect = pinDots
        val length = layout.text.length
        ranges.forEach { range ->
            val a = range.first.coerceIn(0, length)
            val b = (range.last + 1).coerceIn(0, length)
            if (a >= b) return@forEach
            val top = layout.getLineForOffset(a)
            val bottom = layout.getLineForOffset(b)
            for (line in max(top, first)..min(bottom, last - 1)) {
                val x0 = if (line == top) layout.getPrimaryHorizontal(a) else layout.getLineLeft(line)
                val x1 = if (line == bottom) layout.getPrimaryHorizontal(b) else layout.getLineRight(line)
                if (x1 - x0 < 1f) continue
                val y = layout.getLineBaseline(line) + max(2f * density, layout.getLineDescent(line) * 0.55f)
                canvas.drawLine(x0, y, x1, y, pinPaint)
            }
            if (top in first until last && content.left >= PIN_ROOM_DP * density) {
                val s = PIN_SIZE_DP * density / 14f
                canvas.save()
                canvas.translate(-(PIN_ROOM_DP - 2) * density, layout.getLineTop(top) + (layout.getLineBottom(top) - layout.getLineTop(top) - 14 * s) / 2)
                canvas.scale(s, s)
                pinFill.color = ink
                canvas.drawPath(pinGlyph, pinFill)
                canvas.restore()
            }
        }
    }

    private fun selectionHighlight(layout: Layout): Path? {
        if (layout !== selLayout) return null
        selPath.reset()
        layout.getSelectionPath(min(selStart, selEnd), max(selStart, selEnd), selPath)
        selPaint.color = ColorUtils.blendARGB(paper, ink, 0.28f)
        return selPath
    }

    private fun finishRefresh() {
        if (refreshStart < 0) return
        if (pending != null) { page = pending; ghosts.clear() }
        pending = null
        refreshStart = -1L
        invalidate()
    }

    /**
     * The refresh as a wash over the whole screen: light grey rising over the old page, the new page swapped in
     * under it, a brief darker pulse, then clearing — an e-ink panel's full refresh at about five frames a second.
     */
    private fun drawRefresh(canvas: Canvas) {
        if (refreshStart < 0) return
        val t = SystemClock.uptimeMillis() - refreshStart
        // The simulator's light and dark greys over a white page, taken as steps from this paper toward its ink.
        val light = ColorUtils.blendARGB(paper, ink, WASH_DEPTH)
        val dark = ColorUtils.blendARGB(paper, ink, PULSE_DEPTH)
        fun ease(x: Float) = 1f - (1f - x.coerceIn(0f, 1f)).let { it * it }
        val (colour, alpha) = when {
            t < WASH_MS -> light to 0.8f * ease(t / WASH_MS.toFloat())
            t < WASH_MS + PULSE_MS -> dark to 0.8f + (0.3f - 0.8f) * ease((t - WASH_MS) / PULSE_MS.toFloat())
            t < WASH_MS + PULSE_MS + CLEAR_MS -> light to 0.3f * (1f - ease((t - WASH_MS - PULSE_MS) / CLEAR_MS.toFloat()))
            else -> { refreshStart = -1L; return }
        }
        washPaint.color = colour
        washPaint.alpha = (255 * alpha).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), washPaint)
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = measureContent()

    private fun measureContent() {
        val headRoom = HEAD_DP * density
        val footRoom = FOOT_DP * density
        content.set(marginPx.toFloat(), paddingTop + headRoom, (width - marginPx).toFloat(), height - paddingBottom - footRoom)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(paper)
        if (grainPaint.alpha > 0) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), grainPaint)
        ghosts.forEach { (ghost, strength) -> drawPage(canvas, ghost, strength) }
        // Halfway through a refresh the new page is swapped in under the wash.
        if (refreshStart >= 0 && pending != null && SystemClock.uptimeMillis() - refreshStart >= WASH_MS) {
            page = pending; pending = null; ghosts.clear()
        }
        page?.let { drawPage(canvas, it, 1f) }
        drawStrip(canvas)
        drawChrome(canvas)
        drawRefresh(canvas)
    }

    private fun drawStrip(canvas: Canvas) {
        val s = strip ?: return
        canvas.save()
        canvas.clipRect(0f, content.top, width.toFloat(), content.bottom)
        var y = content.top - offset
        var i = piece
        while (i < s.count && y < content.bottom) {
            val h = s.height(i)
            val layout = s.layout(i)
            if (layout != null) {
                canvas.save()
                canvas.translate(content.left, y)
                layout.paint.color = ink
                selectionHighlight(layout)?.let { canvas.drawPath(it, selPaint) }
                drawPins(canvas, layout)
                layout.draw(canvas)
                canvas.restore()
                drawSelection(canvas, layout, content.left, y)
            } else {
                s.bitmap(i)?.let { b ->
                    bitmapPaint.alpha = 255
                    canvas.drawBitmap(b, null, RectF(content.left, y, content.right, y + b.height * content.width() / b.width), bitmapPaint)
                }
            }
            y += h; i++
        }
        canvas.restore()
    }

    private fun drawPage(canvas: Canvas, page: Page, strength: Float) {
        val alpha = (255 * strength).toInt().coerceIn(0, 255)
        when (page) {
            is Page.Text -> {
                val top = page.layout.getLineTop(page.top)
                val bottom = page.layout.getLineTop(page.bottom)
                canvas.save()
                canvas.translate(content.left, content.top)
                // The left margin stays open for the pins beside pinned lines.
                canvas.clipRect(-content.left, 0f, content.width(), (bottom - top).toFloat())
                canvas.translate(0f, -top.toFloat())
                page.layout.paint.color = ink
                if (alpha < 255) canvas.saveLayerAlpha(-content.left, top.toFloat(), content.width(), bottom.toFloat(), alpha)
                if (alpha == 255) selectionHighlight(page.layout)?.let { canvas.drawPath(it, selPaint) }
                drawPins(canvas, page.layout, page.top, page.bottom)
                page.layout.draw(canvas)
                if (alpha < 255) canvas.restore()
                canvas.restore()
                if (alpha == 255) {
                    canvas.save()
                    canvas.clipRect(0f, content.top, width.toFloat(), height.toFloat())
                    drawSelection(canvas, page.layout, content.left, content.top - top)
                    canvas.restore()
                }
            }
            is Page.Picture -> {
                val b = page.bitmap
                val fit = min(content.width() / b.width, content.height() / b.height) * zoom
                val w = b.width * fit
                val h = b.height * fit
                val left = content.centerX() - w / 2 + panX
                val top = content.top + if (zoom > 1f) (content.height() - h) / 2 + panY else 0f
                canvas.save()
                canvas.clipRect(0f, content.top, width.toFloat(), content.bottom)
                bitmapPaint.alpha = alpha
                canvas.drawBitmap(b, null, RectF(left, top, left + w, top + h), bitmapPaint)
                canvas.restore()
            }
        }
    }

    private fun drawChrome(canvas: Canvas) {
        chromePaint.color = dim
        val left = max(content.left, 14 * density)
        val right = width - left
        val headY = paddingTop + HEAD_DP * density / 2
        val half = (right - left) / 2 - 8 * density
        canvas.drawText(TextUtils.ellipsize(head.uppercase(), chromePaint, half, TextUtils.TruncateAt.END).toString(), left, headY, chromePaint)
        val chapterText = TextUtils.ellipsize(chapter.uppercase(), chromePaint, half, TextUtils.TruncateAt.END).toString()
        canvas.drawText(chapterText, right - chromePaint.measureText(chapterText), headY, chromePaint)

        val footY = height - paddingBottom - FOOT_DP * density / 2 + chromePaint.textSize / 3
        canvas.drawText(footLeft, left, footY, chromePaint)
        val rightWidth = chromePaint.measureText(footRight)
        canvas.drawText(footRight, right - rightWidth, footY, chromePaint)
        val barLeft = left + chromePaint.measureText(footLeft) + 10 * density
        val barRight = right - rightWidth - 10 * density
        if (barRight > barLeft) {
            val y = footY - chromePaint.textSize / 3
            val thick = max(1f, 2 * density)
            linePaint.color = ColorUtils.blendARGB(paper, ink, 0.18f)
            canvas.drawRect(barLeft, y, barRight, y + thick, linePaint)
            linePaint.color = ColorUtils.blendARGB(paper, ink, 0.85f)
            canvas.drawRect(barLeft, y, barLeft + (barRight - barLeft) * progress.coerceIn(0f, 1f), y + thick, linePaint)
        }
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            if (!scroller.isFinished) { scroller.forceFinished(true); scrollSettled() }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (selecting) { clearSelection(); return true }
            if (page !is Page.Picture) tap(e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (zoom <= 1f) selectWordAt(e.x, e.y)
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (page is Page.Picture) tap(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (page !is Page.Picture) return false
            if (zoom > 1f) { zoom = 1f; panX = 0f; panY = 0f } else {
                zoom = ZOOM
                panX = (content.centerX() - e.x) * (ZOOM - 1)
                panY = (content.centerY() - e.y) * (ZOOM - 1)
                clampPan()
            }
            onZoom(zoom)
            invalidate()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (strip != null) { scrollBy(dy); onScrolled(piece, offset.toInt(), false); return true }
            if (zoom <= 1f) return false
            panX -= dx
            panY -= dy
            clampPan()
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (strip != null) {
                flingY = 0
                scroller.fling(0, 0, 0, -vy.toInt(), 0, 0, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2)
                postInvalidateOnAnimation()
                return true
            }
            if (zoom > 1f || abs(vx) < abs(vy) * 1.5f || abs(vx) < FLING * density) return false
            onSwipe(if (vx < 0) 1 else -1)
            return true
        }
    })

    /** The outer quarters turn; the wide middle opens the menu. */
    private fun tap(x: Float, y: Float) {
        if (y > height - paddingBottom - FOOT_DP * density) { onFooter(); return }
        val edge = width * EDGE
        onTap(when { x < edge -> -1; x > width - edge -> 1; else -> 0 })
    }

    private fun clampPan() {
        val b = (page as? Page.Picture)?.bitmap ?: return
        val fit = min(content.width() / b.width, content.height() / b.height) * zoom
        val spareX = max(0f, (b.width * fit - content.width()) / 2)
        val spareY = max(0f, (b.height * fit - content.height()) / 2)
        panX = panX.coerceIn(-spareX, spareX)
        panY = panY.coerceIn(-spareY, spareY)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (selecting && dragHandle(event)) return true
        val handled = gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && strip != null && scroller.isFinished) scrollSettled()
        return handled
    }

    /** A selection handle follows the finger, aimed a little above it so the words stay in sight. */
    private fun dragHandle(event: MotionEvent): Boolean {
        val reach = HANDLE_REACH_DP * density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = when {
                    near(event, startHandle, reach) -> 1
                    near(event, endHandle, reach) -> 2
                    else -> 0
                }
                if (dragging != 0) actionMode?.hide(60_000L)
                return dragging != 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging == 0) return false
                val layout = selLayout ?: return true
                val (under, at) = textAt(event.x, event.y - HANDLE_LIFT_DP * density) ?: return true
                if (under !== layout) return true
                if (dragging == 1) selStart = at else selEnd = at
                if (selStart == selEnd) { if (dragging == 1) selStart = max(0, selEnd - 1) else selEnd = min(layout.text.length, selStart + 1) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging == 0) return false
                dragging = 0
                if (selStart > selEnd) { val t = selStart; selStart = selEnd; selEnd = t }
                snapToWords()
                actionMode?.let { it.invalidate(); it.invalidateContentRect(); it.hide(0) }
                return true
            }
        }
        return false
    }

    private fun near(event: MotionEvent, point: android.graphics.PointF, reach: Float) =
        point.x >= 0 && abs(event.x - point.x) < reach && abs(event.y - point.y) < reach

    companion object {
        const val HEAD_DP = 34
        const val FOOT_DP = 30
        const val ZOOM = 2.2f
        private const val GRAIN = 96
        private const val FADE = 0.45f
        private const val MIN_GHOST = 0.02f
        private const val MAX_GHOSTS = 3
        private const val FLING = 600
        /** How much of the width each turning edge takes. */
        private const val EDGE = 0.25f
        /** A tap or key in scrolling moves this much of a screen. */
        private const val PAGE_SCROLL = 0.9f
        private const val WORD_WINDOW = 80
        private const val HANDLE_DP = 7
        private const val HANDLE_REACH_DP = 28
        private const val HANDLE_LIFT_DP = 24
        private const val COPY = 1
        private const val SHARE = 2
        private const val PIN = 3
        private const val APPS = 100
        /** The pin beside a pinned line, and the margin it needs to be drawn. */
        private const val PIN_SIZE_DP = 10
        private const val PIN_ROOM_DP = 13
        /** The refresh at five frames a second: two frames of wash, one of pulse, one to clear. */
        private const val WASH_MS = 400L
        private const val PULSE_MS = 200L
        private const val CLEAR_MS = 200L
        private const val WASH_DEPTH = 0.22f
        private const val PULSE_DEPTH = 0.42f
    }
}
