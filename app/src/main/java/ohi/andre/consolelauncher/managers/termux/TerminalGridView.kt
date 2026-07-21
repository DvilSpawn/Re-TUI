package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
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
    private val normalGlyphWidths = HashMap<String, Float>()
    private val boldGlyphWidths = HashMap<String, Float>()

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
        reverseBackground = ColorUtils.blendARGB(background, foreground, 0.36f)
        clearCells()
        invalidate()
    }

    fun characterWidth(): Float {
        return max(1f, cellWidthPx)
    }

    fun lineHeight(): Int {
        return max(1, cellHeightPx)
    }

    fun setFrame(value: String?, frameCols: Int, frameRows: Int, cursorCol: Int?, cursorRow: Int?) {
        val cleanCols = max(1, frameCols)
        val cleanRows = max(1, frameRows)
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
                        writeCell(col, row, String(Character.toChars(codePoint)), style)
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
        val clean = index.coerceIn(0, 7)
        if (background) {
            val panel = ColorUtils.blendARGB(terminalBackground, terminalAccent, if (bright) 0.34f else 0.22f)
            val active = ColorUtils.blendARGB(terminalAccent, terminalForeground, if (bright) 0.30f else 0.12f)
            return when (clean) {
                0 -> terminalBackground
                1 -> ColorUtils.blendARGB(terminalBackground, Color.rgb(190, 62, 62), if (bright) 0.42f else 0.26f)
                2 -> ColorUtils.blendARGB(terminalBackground, terminalAccent, if (bright) 0.42f else 0.28f)
                3 -> ColorUtils.blendARGB(terminalBackground, terminalForeground, if (bright) 0.32f else 0.18f)
                4 -> panel
                5 -> ColorUtils.blendARGB(terminalBackground, terminalAccent, if (bright) 0.48f else 0.32f)
                6 -> active
                else -> ColorUtils.blendARGB(terminalBackground, terminalForeground, if (bright) 0.38f else 0.24f)
            }
        }
        return when (clean) {
            0 -> Color.BLACK
            1 -> ColorUtils.blendARGB(terminalForeground, Color.rgb(255, 70, 70), if (bright) 0.55f else 0.34f)
            2 -> ColorUtils.blendARGB(terminalAccent, terminalForeground, if (bright) 0.28f else 0.10f)
            3 -> ColorUtils.blendARGB(terminalForeground, terminalAccent, if (bright) 0.42f else 0.24f)
            4 -> ColorUtils.blendARGB(terminalForeground, terminalAccent, if (bright) 0.42f else 0.25f)
            5 -> ColorUtils.blendARGB(terminalAccent, Color.rgb(220, 150, 255), if (bright) 0.36f else 0.20f)
            6 -> ColorUtils.blendARGB(terminalAccent, terminalForeground, if (bright) 0.32f else 0.16f)
            else -> terminalForeground
        }
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
