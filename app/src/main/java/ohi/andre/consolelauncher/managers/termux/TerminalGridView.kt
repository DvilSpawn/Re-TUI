package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Rect
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class TerminalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isSubpixelText = false
    }
    private val backgroundPaint = Paint()
    private var normalTypeface: Typeface? = Typeface.MONOSPACE
    private var boldTypeface: Typeface? = Typeface.DEFAULT_BOLD
    private var terminalForeground = Color.WHITE
    private var terminalBackground = Color.BLACK
    private var terminalAccent = Color.WHITE
    private var reverseBackground = Color.DKGRAY
    private var cols = 80
    private var rows = 24
    private var cursorX: Int? = null
    private var cursorY: Int? = null
    private var cellWidthPx = 1f
    private var cellHeightPx = 1
    private var baselineOffsetPx = 1f
    private var cells = Array(cols * rows) { Cell() }
    private var frame: String? = null
    private var streamEmulator: com.termux.terminal.TerminalEmulator? = null
    private val normalGlyphWidths = HashMap<String, Float>()
    private val boldGlyphWidths = HashMap<String, Float>()

    /**
     * Where pasted text goes; set by the pane that owns the terminal. Without it the selection menu only copies.
     */
    var onPasteText: ((String) -> Unit)? = null
    private val selection = TextSelection()

    init {
        setWillNotDraw(false)
        setTerminalTextSizeSp(12f)
        updateThemeColors(Color.WHITE, Color.BLACK, Color.WHITE)
    }

    fun setTerminalTypeface(typeface: Typeface?) {
        normalTypeface = typeface ?: Typeface.MONOSPACE
        boldTypeface = Typeface.create(normalTypeface, Typeface.BOLD)
        textPaint.typeface = normalTypeface
        recalculateMetrics()
    }

    fun setTerminalTextSizeSp(sizeSp: Float) {
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp,
            resources.displayMetrics
        )
        recalculateMetrics()
    }

    fun updateThemeColors(foreground: Int, background: Int, accent: Int) {
        terminalForeground = foreground
        terminalBackground = background
        terminalAccent = accent
        reverseBackground = ThemeColorResolver.blendPreservingAlpha(background, foreground, 0.36f)
        clearCells()
        val engine = streamEmulator
        if (engine != null) setEmulatorFrame(engine) else { parseIntoGrid(frame); invalidate() }
    }

    fun characterWidth(): Float {
        return max(1f, cellWidthPx)
    }

    fun lineHeight(): Int {
        return max(1, cellHeightPx)
    }

    /** Copy the engine's cells into the existing renderer, without ANSI reparsing. */
    fun setEmulatorFrame(emulator: com.termux.terminal.TerminalEmulator) {
        streamEmulator = emulator
        frame = null
        val c = emulator.mColumns.coerceIn(1, 240)
        val r = emulator.mRows.coerceIn(1, 120)
        val sizeChanged = c != cols || r != rows
        if (sizeChanged) {
            cols = c; rows = r
            cells = Array(cols * rows) { Cell() }
            selection.clamp()
        } else clearCells()
        cursorX = if (emulator.isCursorEnabled) emulator.cursorCol else null
        cursorY = if (emulator.isCursorEnabled) emulator.cursorRow else null
        val screen = emulator.screen
        for (row in 0 until rows) {
            val source = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row))
            var offset = 0
            var col = 0
            var lastGlyphCol = -1
            while (col < cols && offset < source.spaceUsed) {
                val point = Character.codePointAt(source.mText, offset, source.spaceUsed)
                val count = Character.charCount(point)
                val width = com.termux.terminal.WcWidth.width(point)
                if (width > 0) {
                    val encoded = source.getStyle(col)
                    val effects = com.termux.terminal.TextStyle.decodeEffect(encoded)
                    val fore = com.termux.terminal.TextStyle.decodeForeColor(encoded)
                    val back = com.termux.terminal.TextStyle.decodeBackColor(encoded)
                    val style = Style().apply {
                        foreground = if (fore == 256) null else if (fore < 0) fore else if (fore < 16) ThemeColorResolver.ansi(fore % 8, fore >= 8) else emulator.mColors.mCurrentColors[fore]
                        background = if (back == 257) null else if (back < 0) back else if (back < 16) ThemeColorResolver.ansi(back % 8, back >= 8) else emulator.mColors.mCurrentColors[back]
                        bold = effects and com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BOLD != 0
                        reverse = (effects and com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0) xor emulator.isReverseVideo
                    }
                    writeCell(col, row, String(source.mText, offset, count), style)
                    if (width > 1 && col + 1 < cols) writeCell(col + 1, row, " ", style)
                    lastGlyphCol = col
                    col += width
                } else if (lastGlyphCol >= 0) {
                    // Keep combining characters on the previous glyph rather than a new cell.
                    cells[indexOf(lastGlyphCol, row)].text += String(source.mText, offset, count)
                }
                offset += count
            }
        }
        if (sizeChanged) requestLayoutSafely()
        invalidate()
    }

    fun setFrame(value: String?, frameCols: Int, frameRows: Int, cursorCol: Int?, cursorRow: Int?) {
        streamEmulator = null
        frame = value
        val cleanCols = frameCols.coerceIn(1, 240)
        val cleanRows = frameRows.coerceIn(1, 120)
        val sizeChanged = cleanCols != cols || cleanRows != rows
        if (sizeChanged) {
            cols = cleanCols
            rows = cleanRows
            cells = Array(cols * rows) { Cell() }
        } else {
            clearCells()
        }
        cursorX = cursorCol?.coerceIn(0, cols - 1)
        cursorY = cursorRow?.coerceIn(0, rows - 1)
        parseIntoGrid(value)
        if (sizeChanged) {
            requestLayoutSafely()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = max(1, ceil(cols * characterWidth()).toInt())
        val desiredHeight = max(1, rows * lineHeight())
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = characterWidth()
        val height = lineHeight().toFloat()
        val baseline = baselineOffsetPx
        for (row in 0 until rows) {
            val top = row * height
            for (col in 0 until cols) {
                val cell = cells[indexOf(col, row)]
                val left = col * width
                val isCursor = cursorX == col && cursorY == row
                val background = if (isCursor) terminalForeground else cell.background
                if (background != null) {
                    backgroundPaint.color = background
                    canvas.drawRect(left, top, left + width + 0.75f, top + height, backgroundPaint)
                }
            }
        }
        // Paint all backgrounds first so continuation cells cannot cover wide glyphs.
        for (row in 0 until rows) {
            val top = row * height
            for (col in 0 until cols) {
                val cell = cells[indexOf(col, row)]
                val left = col * width
                val isCursor = cursorX == col && cursorY == row
                if (cell.text.isNotEmpty() && cell.text != " ") {
                    textPaint.color = if (isCursor) terminalBackground else cell.foreground
                    textPaint.typeface = if (cell.bold) boldTypeface else normalTypeface
                    drawCellText(canvas, cell.text, left, top + baseline, width, cell.bold)
                } else if (isCursor) {
                    textPaint.color = terminalBackground
                    textPaint.typeface = normalTypeface
                    canvas.drawText(" ", left, top + baseline, textPaint)
                }
            }
        }
        selection.draw(canvas)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        selection.touch(event) || super.dispatchTouchEvent(event)

    /**
     * Long-press text selection, as in Termux: a long press selects the word under the finger and dragging
     * extends it; handles move either end; the floating toolbar copies, pastes or selects everything. While a
     * selection is shown it takes all touches on the terminal, and a tap elsewhere on it clears the selection.
     */
    private inner class TextSelection {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val handleRadius = 9f * resources.displayMetrics.density
        private val grabRadius = 28f * resources.displayMetrics.density
        private val highlight = Paint()
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var active = false
        /** A long press on empty space: no selection, just a menu to paste at the shell's cursor. */
        private var pasteOnly = false
        private var pressX = 0f; private var pressY = 0f
        private var startCol = 0; private var startRow = 0
        private var endCol = 0; private var endRow = 0
        private var dragging = 0 // 0 none, 1 start handle, 2 end handle
        private var downX = 0f; private var downY = 0f
        private var pending = false
        private var actionMode: ActionMode? = null
        private val longPress = Runnable { pending = false; begin(downX, downY) }

        fun touch(event: MotionEvent): Boolean {
            if (!active) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x; downY = event.y; pending = true
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    MotionEvent.ACTION_MOVE -> if (pending && (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop)) cancelPending()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelPending()
                }
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (pasteOnly) { clear(); return true }
                    dragging = when {
                        near(event, startCol, startRow, true) -> 1
                        near(event, endCol, endRow, false) -> 2
                        else -> 0
                    }
                    if (dragging == 0) { clear(); return true }
                    actionMode?.hide(0)
                }
                MotionEvent.ACTION_MOVE -> if (dragging != 0) moveHandle(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = 0
                    showMenu()
                }
            }
            return true
        }

        private fun cancelPending() { pending = false; removeCallbacks(longPress) }

        private fun cellAt(x: Float, y: Float): Pair<Int, Int> =
            (x / characterWidth()).toInt().coerceIn(0, cols - 1) to (y / lineHeight()).coerceIn(0f, (rows - 1).toFloat()).toInt().coerceIn(0, rows - 1)

        private fun blank(col: Int, row: Int) = cells[indexOf(col, row)].text.isBlank()

        private fun begin(x: Float, y: Float) {
            val (col, row) = cellAt(x, y)
            pressX = x; pressY = y
            // Pasting always types at the shell's cursor, so empty space only needs the menu.
            pasteOnly = blank(col, row) && onPasteText != null
            var first = col; var last = col
            if (!blank(col, row)) {
                while (first > 0 && !blank(first - 1, row)) first--
                while (last < cols - 1 && !blank(last + 1, row)) last++
            }
            startCol = first; startRow = row; endCol = last; endRow = row
            active = true; dragging = if (pasteOnly) 0 else 2
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
            // The pane's own gesture (tap to focus, swipe to switch) ends here.
            val now = android.os.SystemClock.uptimeMillis()
            MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, x, y, 0).also { super@TerminalGridView.dispatchTouchEvent(it); it.recycle() }
            invalidate()
        }

        private fun moveHandle(x: Float, y: Float) {
            // Handles sit below the text, so aim a line above the finger.
            val (col, row) = cellAt(x, y - lineHeight())
            if (dragging == 1) { startCol = col; startRow = row } else { endCol = col; endRow = row }
            if (startRow > endRow || (startRow == endRow && startCol > endCol)) {
                val c = startCol; val r = startRow
                startCol = endCol; startRow = endRow; endCol = c; endRow = r
                dragging = if (dragging == 1) 2 else 1
            }
            invalidate()
        }

        private fun handlePoint(col: Int, row: Int, start: Boolean): Pair<Float, Float> {
            val x = (if (start) col else col + 1) * characterWidth()
            return x to ((row + 1) * lineHeight() + handleRadius)
        }

        private fun near(event: MotionEvent, col: Int, row: Int, start: Boolean): Boolean {
            val (x, y) = handlePoint(col, row, start)
            return kotlin.math.hypot((event.x - x).toDouble(), (event.y - y).toDouble()) <= grabRadius
        }

        fun draw(canvas: Canvas) {
            if (!active || pasteOnly) return
            highlight.color = terminalAccent; highlight.alpha = 90
            val w = characterWidth(); val h = lineHeight().toFloat()
            for (row in startRow..endRow) {
                val from = if (row == startRow) startCol else 0
                val to = if (row == endRow) endCol else cols - 1
                canvas.drawRect(from * w, row * h, (to + 1) * w, (row + 1) * h, highlight)
            }
            handlePaint.color = terminalAccent
            for (start in listOf(true, false)) {
                val (x, y) = if (start) handlePoint(startCol, startRow, true) else handlePoint(endCol, endRow, false)
                canvas.drawCircle(x, y, handleRadius, handlePaint)
                canvas.drawRect(x - 1.5f, y - handleRadius - 2f, x + 1.5f, y, handlePaint)
            }
        }

        fun text(): String {
            streamEmulator?.let { return it.getSelectedText(startCol, startRow, endCol, endRow) }
            return (startRow..endRow).joinToString("\n") { row ->
                val from = if (row == startRow) startCol else 0
                val to = if (row == endRow) endCol else cols - 1
                (from..to).joinToString("") { cells[indexOf(it, row)].text }.trimEnd()
            }
        }

        private fun showMenu() {
            actionMode?.let { it.invalidateContentRect(); it.hide(0); return }
            actionMode = startActionMode(object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    if (!pasteOnly) menu.add(0, 1, 0, android.R.string.copy)
                    if (onPasteText != null) menu.add(0, 2, 1, android.R.string.paste)
                    menu.add(0, 3, 2, android.R.string.selectAll)
                    return true
                }
                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    when (item.itemId) {
                        1 -> { clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text())); clear() }
                        2 -> {
                            val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                            clear()
                            if (!pasted.isNullOrEmpty()) onPasteText?.invoke(pasted)
                        }
                        3 -> {
                            startCol = 0; startRow = 0; endCol = cols - 1; endRow = rows - 1
                            // Select all turns the paste menu into a full selection that can be copied.
                            if (pasteOnly) { pasteOnly = false; mode.menu.add(0, 1, 0, android.R.string.copy) }
                            invalidate(); mode.invalidateContentRect()
                        }
                    }
                    return true
                }
                override fun onDestroyActionMode(mode: ActionMode) {
                    if (actionMode === mode) { actionMode = null; if (active) clear() }
                }
                override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                    val w = characterWidth(); val h = lineHeight()
                    if (pasteOnly) { outRect.set(pressX.toInt(), pressY.toInt() - h, pressX.toInt() + 1, pressY.toInt()); return }
                    outRect.set(0, startRow * h, (cols * w).toInt(), (endRow + 1) * h)
                    if (startRow == endRow) outRect.set((startCol * w).toInt(), startRow * h, ((endCol + 1) * w).toInt(), (endRow + 1) * h)
                }
            }, ActionMode.TYPE_FLOATING)
        }

        fun clear() {
            if (!active) return
            active = false; pasteOnly = false; dragging = 0
            actionMode?.let { actionMode = null; it.finish() }
            parent?.requestDisallowInterceptTouchEvent(false)
            invalidate()
        }

        /** Keeps the selection inside the grid when the terminal changes size. */
        fun clamp() {
            if (!active) return
            startCol = startCol.coerceIn(0, cols - 1); endCol = endCol.coerceIn(0, cols - 1)
            startRow = startRow.coerceIn(0, rows - 1); endRow = endRow.coerceIn(0, rows - 1)
        }
    }

    private fun recalculateMetrics() {
        textPaint.typeface = normalTypeface
        normalGlyphWidths.clear()
        boldGlyphWidths.clear()
        cellWidthPx = max(1f, textPaint.measureText(CELL_WIDTH_SAMPLE) / CELL_WIDTH_SAMPLE.length)
        val metrics = textPaint.fontMetrics
        cellHeightPx = max(1, ceil(metrics.descent - metrics.ascent).toInt())
        baselineOffsetPx = -metrics.ascent
        requestLayoutSafely()
        invalidate()
    }

    private fun drawCellText(
        canvas: Canvas,
        text: String,
        left: Float,
        baseline: Float,
        cellWidth: Float,
        bold: Boolean
    ) {
        val widthCache = if (bold) boldGlyphWidths else normalGlyphWidths
        val glyphWidth = widthCache.getOrPut(text) { textPaint.measureText(text) }
        if (glyphWidth <= 0f) return

        val cellSpan = terminalCellWidth(text.codePointAt(0)).coerceAtLeast(1)
        val availableWidth = cellWidth * cellSpan
        val scaleX = min(1f, availableWidth / glyphWidth)
        val renderedWidth = glyphWidth * scaleX
        val drawLeft = left + (availableWidth - renderedWidth) / 2f
        if (scaleX < 1f) {
            canvas.save()
            canvas.translate(drawLeft, 0f)
            canvas.scale(scaleX, 1f)
            canvas.drawText(text, 0f, baseline, textPaint)
            canvas.restore()
        } else {
            canvas.drawText(text, drawLeft, baseline, textPaint)
        }
    }

    private fun requestLayoutSafely() {
        if (isInLayout) {
            post { requestLayout() }
        } else {
            requestLayout()
        }
    }

    private fun clearCells() {
        for (cell in cells) {
            cell.reset(terminalForeground)
        }
    }

    private fun parseIntoGrid(value: String?) {
        if (value.isNullOrEmpty()) {
            return
        }
        val style = Style()
        var row = 0
        var col = 0
        var index = 0
        var lineDrawing = false
        while (index < value.length && row < rows) {
            val ch = value[index]
            if (ch == '\u001B' && index + 1 < value.length && value[index + 1] == '[') {
                val end = findCsiEnd(value, index + 2)
                if (end > index) {
                    if (value[end] == 'm') {
                        updateStyle(style, value.substring(index + 2, end))
                    }
                    index = end + 1
                    continue
                }
            }
            when (ch) {
                '\u000E' -> {
                    lineDrawing = true
                    index++
                }
                '\u000F' -> {
                    lineDrawing = false
                    index++
                }
                '\r' -> {
                    index++
                }
                '\n' -> {
                    row++
                    col = 0
                    index++
                }
                '\t' -> {
                    val nextStop = ((col / TAB_WIDTH) + 1) * TAB_WIDTH
                    while (col < nextStop && col < cols && row < rows) {
                        writeCell(col, row, " ", style)
                        col++
                    }
                    index++
                }
                else -> {
                    val codePoint = value.codePointAt(index)
                    val charLength = Character.charCount(codePoint)
                    val cellSpan = terminalCellWidth(codePoint)
                    if (cellSpan > 0 && col < cols) {
                        val text = if (charLength == 1) decLineDrawingGlyph(ch, lineDrawing).toString()
                            else String(Character.toChars(codePoint))
                        writeCell(col, row, text, style)
                        if (cellSpan > 1 && col + 1 < cols) {
                            writeCell(col + 1, row, " ", style)
                        }
                        col += cellSpan
                    }
                    index += charLength
                }
            }
        }
    }

    private fun writeCell(col: Int, row: Int, text: String, style: Style) {
        if (col !in 0 until cols || row !in 0 until rows) {
            return
        }
        val cell = cells[indexOf(col, row)]
        val resolved = resolveStyle(style)
        cell.text = text
        cell.foreground = resolved.foreground
        cell.background = resolved.background
        cell.bold = style.bold
    }

    private fun resolveStyle(style: Style): ResolvedStyle {
        var foreground = style.foreground ?: terminalForeground
        var background = style.background
        if (style.reverse) {
            if (style.background != null) {
                val originalForeground = foreground
                foreground = style.background!!
                background = originalForeground
            } else {
                foreground = terminalBackground
                background = reverseBackground
            }
        }
        return ResolvedStyle(foreground, background)
    }

    private fun indexOf(col: Int, row: Int): Int {
        return row * cols + col
    }

    private fun findCsiEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length) {
            val code = text[index].code
            if (code in 0x40..0x7e) {
                return index
            }
            index++
        }
        return -1
    }

    private fun updateStyle(style: Style, params: String) {
        val codes = parseCodes(params)
        if (codes.isEmpty()) {
            style.reset()
            return
        }
        var index = 0
        while (index < codes.size) {
            when (val code = codes[index]) {
                0 -> style.reset()
                1 -> style.bold = true
                22 -> style.bold = false
                7 -> style.reverse = true
                27 -> style.reverse = false
                39 -> style.foreground = null
                49 -> style.background = null
                in 30..37 -> style.foreground = ansiColor(code - 30, false, false)
                in 90..97 -> style.foreground = ansiColor(code - 90, true, false)
                in 40..47 -> style.background = ansiColor(code - 40, false, true)
                in 100..107 -> style.background = ansiColor(code - 100, true, true)
                38, 48 -> {
                    val foreground = code == 38
                    val parsed = parseExtendedColor(codes, index + 1, !foreground)
                    if (parsed != null) {
                        if (foreground) {
                            style.foreground = parsed.first
                        } else {
                            style.background = parsed.first
                        }
                        index = parsed.second
                    }
                }
            }
            index++
        }
    }

    private fun parseCodes(params: String): List<Int> {
        if (params.isEmpty()) {
            return listOf(0)
        }
        val codes = ArrayList<Int>()
        val parts = params.replace(':', ';').split(";")
        for (part in parts) {
            codes.add(part.toIntOrNull() ?: 0)
        }
        return codes
    }

    private fun parseExtendedColor(codes: List<Int>, start: Int, background: Boolean): Pair<Int, Int>? {
        if (start >= codes.size) {
            return null
        }
        return when (codes[start]) {
            5 -> {
                if (start + 1 >= codes.size) null
                else Pair(ansi256Color(codes[start + 1], background), start + 1)
            }
            2 -> {
                if (start + 3 >= codes.size) null
                else Pair(
                    Color.rgb(
                        codes[start + 1].coerceIn(0, 255),
                        codes[start + 2].coerceIn(0, 255),
                        codes[start + 3].coerceIn(0, 255)
                    ),
                    start + 3
                )
            }
            else -> null
        }
    }

    private fun ansiColor(index: Int, bright: Boolean, background: Boolean): Int {
        return ThemeColorResolver.ansi(index, bright)
    }

    private fun ansi256Color(code: Int, background: Boolean): Int {
        val clean = code.coerceIn(0, 255)
        if (clean < 16) {
            return ansiColor(clean % 8, clean >= 8, background)
        }
        if (clean in 16..231) {
            val value = clean - 16
            val red = value / 36
            val green = (value / 6) % 6
            val blue = value % 6
            return Color.rgb(ansiCubeValue(red), ansiCubeValue(green), ansiCubeValue(blue))
        }
        val gray = 8 + (clean - 232) * 10
        return Color.rgb(gray, gray, gray)
    }

    private fun ansiCubeValue(value: Int): Int {
        return if (value <= 0) 0 else 55 + value * 40
    }

    private fun terminalCellWidth(codePoint: Int): Int {
        val type = Character.getType(codePoint)
        if (type == Character.NON_SPACING_MARK.toInt()
            || type == Character.ENCLOSING_MARK.toInt()
            || type == Character.COMBINING_SPACING_MARK.toInt()
        ) {
            return 0
        }
        return if (isWideCodePoint(codePoint)) 2 else 1
    }

    private fun isWideCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x1100..0x115f
                || codePoint in 0x2329..0x232a
                || codePoint in 0x2e80..0xa4cf
                || codePoint in 0xac00..0xd7a3
                || codePoint in 0xf900..0xfaff
                || codePoint in 0xfe10..0xfe19
                || codePoint in 0xfe30..0xfe6f
                || codePoint in 0xff00..0xff60
                || codePoint in 0xffe0..0xffe6
                || codePoint in 0x1f300..0x1faff
    }

    private class Cell {
        var text: String = " "
        var foreground: Int = Color.WHITE
        var background: Int? = null
        var bold: Boolean = false

        fun reset(defaultForeground: Int) {
            text = " "
            foreground = defaultForeground
            background = null
            bold = false
        }
    }

    private class Style {
        var foreground: Int? = null
        var background: Int? = null
        var bold: Boolean = false
        var reverse: Boolean = false

        fun reset() {
            foreground = null
            background = null
            bold = false
            reverse = false
        }
    }

    private class ResolvedStyle(
        val foreground: Int,
        val background: Int?
    )

    companion object {
        private const val TAB_WIDTH = 8
        private const val CELL_WIDTH_SAMPLE =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    }
}

internal fun decLineDrawingGlyph(char: Char, active: Boolean): Char {
    if (!active) return char
    return when (char) {
        '`' -> '◆'
        'a' -> '▒'
        'f' -> '°'
        'g' -> '±'
        'j' -> '┘'
        'k' -> '┐'
        'l' -> '┌'
        'm' -> '└'
        'n' -> '┼'
        'o' -> '⎺'
        'p' -> '⎻'
        'q' -> '─'
        'r' -> '⎼'
        's' -> '⎽'
        't' -> '├'
        'u' -> '┤'
        'v' -> '┴'
        'w' -> '┬'
        'x' -> '│'
        'y' -> '≤'
        'z' -> '≥'
        '{' -> 'π'
        '|' -> '≠'
        '}' -> '£'
        '~' -> '·'
        else -> char
    }
}
