package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class AsciiArtTextView : AppCompatTextView {
    private val asciiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lines: List<String> = emptyList()
    private var lineColors: List<IntArray> = emptyList()
    private var textColor = currentTextColor
    private var viewportRowsSetting = DEFAULT_VIEWPORT_ROWS
    private var paneHeightRowsSetting = DEFAULT_PANE_ROWS
    private var contentBounds = ContentBounds.EMPTY
    private var frameHash = 1
    private val bitmapCache = LinkedHashMap<String, Bitmap>(16, 0.75f, true)
    private var bitmapCacheBytes = 0

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        isSingleLine = false
        ellipsize = null
        text = ""
    }

    fun setAsciiFrame(
        text: String?,
        color: Int,
        viewportRows: Int,
        paneHeightRows: Int,
        colors: IntArray? = null
    ) {
        val oldLineCount = lines.size
        val oldBoundsWidth = contentBounds.width()
        val oldViewportRowsSetting = viewportRowsSetting
        val oldPaneHeightRowsSetting = paneHeightRowsSetting

        val normalized = text.orEmpty()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val parsed = normalized.split('\n').toMutableList()
        if (parsed.size > 1 && parsed.last().isEmpty()) {
            parsed.removeAt(parsed.size - 1)
        }

        lines = if (parsed.isEmpty()) listOf("") else parsed
        lineColors = splitColors(lines, colors)
        textColor = color
        viewportRowsSetting = if (viewportRows > 0) viewportRows else DEFAULT_VIEWPORT_ROWS
        paneHeightRowsSetting = if (paneHeightRows > 0) paneHeightRows else DEFAULT_PANE_ROWS
        contentBounds = ContentBounds.from(lines)
        frameHash = 31 * lines.hashCode() + (colors?.contentHashCode() ?: 0)
        if (oldLineCount != lines.size ||
            oldBoundsWidth != contentBounds.width() ||
            oldViewportRowsSetting != viewportRowsSetting ||
            oldPaneHeightRowsSetting != paneHeightRowsSetting
        ) {
            requestLayout()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        configurePaint(baseTextSizePx())
        val availableWidth = max(1, MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
        val desiredHeight = if (isAutoFitMode()) {
            val targetHeight = ceil(lineHeightPx() * paneHeightRowsSetting).toInt()
            val scale = fitScale(availableWidth, targetHeight)
            paddingTop + paddingBottom +
                    max(1, ceil(lineHeightPx() * contentBounds.height() * scale + FIT_INSET_PX * 2).toInt())
        } else {
            val sourceRows = viewportRows(availableWidth)
            val paneRows = min(sourceRows, paneHeightRowsSetting)
            paddingTop + paddingBottom + ceil(lineHeightPx() * paneRows).toInt()
        }
        val desiredWidth = suggestedMinimumWidth
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (lines.isEmpty()) {
            return
        }

        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) {
            return
        }

        val bitmap = bitmapForCurrentFrame(availableWidth, availableHeight)
        canvas.drawBitmap(bitmap, paddingLeft.toFloat(), paddingTop.toFloat(), null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            clearBitmapCache()
        }
    }

    override fun onDetachedFromWindow() {
        clearBitmapCache()
        super.onDetachedFromWindow()
    }

    private fun bitmapForCurrentFrame(availableWidth: Int, availableHeight: Int): Bitmap {
        val cacheKey = frameCacheKey(availableWidth, availableHeight)
        val cached = bitmapCache[cacheKey]
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val bitmap = rasterizeCurrentFrame(availableWidth, availableHeight)
        cacheBitmap(cacheKey, bitmap)
        return bitmap
    }

    private fun rasterizeCurrentFrame(availableWidth: Int, availableHeight: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(
            max(1, availableWidth),
            max(1, availableHeight),
            Bitmap.Config.ARGB_8888
        )
        val bitmapCanvas = Canvas(bitmap)
        bitmap.eraseColor(Color.TRANSPARENT)

        if (isAutoFitMode()) {
            drawFittedFrame(bitmapCanvas, availableWidth, availableHeight)
            return bitmap
        }

        configurePaint(scaledTextSizePx(availableWidth, availableHeight))
        val cellWidth = cellWidthPx()
        val lineHeight = lineHeightPx()
        val requestedRows = viewportRows(availableWidth)
        val visibleCols = max(1, floor(availableWidth / cellWidth).toInt())
        val visibleRows = min(requestedRows, max(1, floor(availableHeight / lineHeight).toInt()))
        val columnWindow = viewportColumnWindow(visibleCols)
        val startRow = viewportStartRow(visibleRows)

        val metrics = asciiPaint.fontMetrics
        var baseline = -metrics.ascent
        var top = 0f
        for (row in 0 until visibleRows) {
            val lineIndex = startRow + row
            if (lineIndex >= lines.size) {
                break
            }

            val line = lines[lineIndex]
            if (columnWindow.start < line.length) {
                val end = min(line.length, columnWindow.end)
                for (col in columnWindow.start until end) {
                    val ch = line[col]
                    if (!ch.isWhitespace()) {
                        asciiPaint.color = cellColor(lineIndex, col)
                        drawCell(
                            bitmapCanvas,
                            ch,
                            (col - columnWindow.start + columnWindow.offset) * cellWidth,
                            top,
                            baseline,
                            cellWidth,
                            lineHeight
                        )
                    }
                }
            }
            baseline += lineHeight
            top += lineHeight
        }

        return bitmap
    }

    private fun drawFittedFrame(canvas: Canvas, availableWidth: Int, availableHeight: Int) {
        if (!contentBounds.hasContent) {
            return
        }

        configurePaint(baseTextSizePx())
        val cellWidth = cellWidthPx()
        val lineHeight = lineHeightPx()
        val scale = fitScale(availableWidth, availableHeight)
        val contentWidth = cellWidth * contentBounds.width() * scale
        val contentHeight = lineHeight * contentBounds.height() * scale
        val offsetX = horizontalOffset(availableWidth.toFloat(), contentWidth)
        val offsetY = max(0f, (availableHeight - contentHeight) / 2f)

        if (cellWidth * scale < MIN_FIT_GLYPH_PX || lineHeight * scale < MIN_FIT_GLYPH_PX) {
            drawFittedCellFrame(canvas, cellWidth, lineHeight, scale, offsetX, offsetY)
            return
        }

        val metrics = asciiPaint.fontMetrics
        var baseline = -metrics.ascent
        var top = 0f
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        for (row in contentBounds.top..contentBounds.bottom) {
            val line = lines[row]
            if (contentBounds.left < line.length) {
                val end = min(line.length, contentBounds.right + 1)
                for (col in contentBounds.left until end) {
                    val ch = line[col]
                    if (!ch.isWhitespace()) {
                        asciiPaint.color = cellColor(row, col)
                        drawCell(
                            canvas,
                            ch,
                            (col - contentBounds.left) * cellWidth,
                            top,
                            baseline,
                            cellWidth,
                            lineHeight
                        )
                    }
                }
            }
            baseline += lineHeight
            top += lineHeight
        }
        canvas.restore()
    }

    private fun drawFittedCellFrame(
        canvas: Canvas,
        cellWidth: Float,
        lineHeight: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ) {
        for (row in contentBounds.top..contentBounds.bottom) {
            val line = lines[row]
            if (contentBounds.left < line.length) {
                val end = min(line.length, contentBounds.right + 1)
                for (col in contentBounds.left until end) {
                    val ch = line[col]
                    if (!ch.isWhitespace()) {
                        asciiPaint.color = cellColor(row, col)
                        drawScaledCell(
                            canvas,
                            ch,
                            offsetX + (col - contentBounds.left) * cellWidth * scale,
                            offsetY + (row - contentBounds.top) * lineHeight * scale,
                            cellWidth * scale,
                            lineHeight * scale
                        )
                    }
                }
            }
        }
    }

    private fun drawScaledCell(
        canvas: Canvas,
        ch: Char,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val oldAlpha = asciiPaint.alpha
        asciiPaint.alpha = shadeAlpha(ch)
        canvas.drawRect(
            left,
            top,
            max(left + 1f, left + ceil(width.toDouble()).toFloat()),
            max(top + 1f, top + ceil(height.toDouble()).toFloat()),
            asciiPaint
        )
        asciiPaint.alpha = oldAlpha
    }

    private fun configurePaint(textSizePx: Float) {
        asciiPaint.color = textColor
        asciiPaint.typeface = Typeface.MONOSPACE
        asciiPaint.textSize = textSizePx
        asciiPaint.isAntiAlias = false
    }

    private fun cellColor(row: Int, col: Int): Int =
        lineColors.getOrNull(row)?.getOrNull(col)?.takeIf { it != 0 } ?: textColor

    private fun splitColors(lines: List<String>, colors: IntArray?): List<IntArray> {
        if (colors == null) return emptyList()
        var offset = 0
        return lines.map { line ->
            IntArray(line.length) { index -> colors.getOrElse(offset + index) { 0 } }.also {
                offset += line.length + 1
            }
        }
    }

    private fun drawCell(
        canvas: Canvas,
        ch: Char,
        left: Float,
        top: Float,
        baseline: Float,
        cellWidth: Float,
        lineHeight: Float
    ) {
        when (ch) {
            '\u2588' -> drawShadeCell(canvas, left, top, cellWidth, lineHeight, 255)
            '\u2593' -> drawShadeCell(canvas, left, top, cellWidth, lineHeight, 205)
            '\u2592' -> drawShadeCell(canvas, left, top, cellWidth, lineHeight, 145)
            '\u2591' -> drawShadeCell(canvas, left, top, cellWidth, lineHeight, 85)
            else -> canvas.drawText(ch.toString(), left, baseline, asciiPaint)
        }
    }

    private fun drawShadeCell(
        canvas: Canvas,
        left: Float,
        top: Float,
        cellWidth: Float,
        lineHeight: Float,
        alpha: Int
    ) {
        val oldAlpha = asciiPaint.alpha
        asciiPaint.alpha = alpha
        canvas.drawRect(
            left,
            top,
            left + ceil(cellWidth.toDouble()).toFloat(),
            top + ceil(lineHeight.toDouble()).toFloat(),
            asciiPaint
        )
        asciiPaint.alpha = oldAlpha
    }

    private fun shadeAlpha(ch: Char): Int {
        return when (ch) {
            '\u2593' -> 205
            '\u2592' -> 145
            '\u2591' -> 85
            else -> 255
        }
    }

    private fun frameCacheKey(availableWidth: Int, availableHeight: Int): String {
        return frameHash.toString() + ":" +
                lines.size + ":" +
                textColor + ":" +
                viewportRowsSetting + ":" +
                paneHeightRowsSetting + ":" +
                gravity + ":" +
                layoutDirection + ":" +
                availableWidth + ":" +
                availableHeight
    }

    private fun cacheBitmap(key: String, bitmap: Bitmap) {
        val previous = bitmapCache.remove(key)
        if (previous != null && !previous.isRecycled) {
            bitmapCacheBytes -= previous.byteCount
            previous.recycle()
        }

        bitmapCache[key] = bitmap
        bitmapCacheBytes += bitmap.byteCount
        trimBitmapCache()
    }

    private fun trimBitmapCache() {
        val iterator = bitmapCache.entries.iterator()
        while ((bitmapCache.size > MAX_BITMAP_CACHE_FRAMES ||
                    bitmapCacheBytes > MAX_BITMAP_CACHE_BYTES) &&
            bitmapCache.size > 1 &&
            iterator.hasNext()
        ) {
            val entry = iterator.next()
            val bitmap = entry.value
            bitmapCacheBytes -= bitmap.byteCount
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            iterator.remove()
        }
    }

    private fun clearBitmapCache() {
        for (bitmap in bitmapCache.values) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        bitmapCache.clear()
        bitmapCacheBytes = 0
    }

    private fun scaledTextSizePx(availableWidth: Int, availableHeight: Int): Float {
        configurePaint(baseTextSizePx())
        val sourceRows = viewportRows(availableWidth)
        val baseLineHeight = lineHeightPx()
        val targetLineHeight = availableHeight.toFloat() / max(1, sourceRows)
        val scale = min(1f, targetLineHeight / baseLineHeight)
        return max(minTextSizePx(), baseTextSizePx() * scale)
    }

    private fun fitScale(availableWidth: Int, availableHeight: Int): Float {
        val fitWidth = max(1f, availableWidth - FIT_INSET_PX * 2)
        val fitHeight = max(1f, availableHeight - FIT_INSET_PX * 2)
        val widthScale = fitWidth / max(1f, contentBounds.width() * cellWidthPx())
        val heightScale = fitHeight / max(1f, contentBounds.height() * lineHeightPx())
        return min(1f, min(widthScale, heightScale))
    }

    private fun viewportRows(availableWidth: Int): Int {
        val contentRows = max(1, lines.size)
        if (viewportRowsSetting > 0) {
            return max(1, min(contentRows, viewportRowsSetting))
        }

        configurePaint(baseTextSizePx())
        val visibleColsAtBaseSize = max(1, floor(availableWidth / cellWidthPx()).toInt())
        val contentCols = max(1, contentBounds.width())
        val rowsForWidth = if (contentCols > visibleColsAtBaseSize) {
            ceil(DEFAULT_PANE_ROWS * (contentCols.toFloat() / visibleColsAtBaseSize.toFloat())).toInt()
        } else {
            DEFAULT_PANE_ROWS
        }
        val rowsForFrame = if (contentCols > visibleColsAtBaseSize) {
            min(contentRows, MAX_AUTO_VIEWPORT_ROWS)
        } else {
            DEFAULT_PANE_ROWS
        }
        return max(1, min(contentRows, max(DEFAULT_PANE_ROWS, max(rowsForWidth, rowsForFrame))))
    }

    private fun viewportStartRow(visibleRows: Int): Int {
        if (lines.size <= visibleRows) {
            return 0
        }

        val center = if (contentBounds.hasContent) {
            (contentBounds.top + contentBounds.bottom) / 2
        } else {
            lines.size / 2
        }
        return clamp(center - visibleRows / 2, 0, lines.size - visibleRows)
    }

    private fun viewportColumnWindow(visibleCols: Int): ColumnWindow {
        if (contentBounds.hasContent) {
            val contentWidth = contentBounds.width()
            if (contentWidth <= visibleCols) {
                return ColumnWindow(
                    contentBounds.left,
                    contentBounds.right + 1,
                    horizontalOffsetColumns(visibleCols, contentWidth)
                )
            }

            val start = when (horizontalGravity()) {
                Gravity.RIGHT -> contentBounds.right - visibleCols + 1
                Gravity.CENTER_HORIZONTAL -> contentBounds.left + (contentWidth - visibleCols) / 2
                else -> contentBounds.left
            }
            val clamped = clamp(start, contentBounds.left, contentBounds.right - visibleCols + 1)
            return ColumnWindow(clamped, clamped + visibleCols, 0)
        }

        val maxColumns = lines.maxOfOrNull { it.length } ?: 0
        if (maxColumns <= visibleCols) {
            return ColumnWindow(0, maxColumns, horizontalOffsetColumns(visibleCols, maxColumns))
        }

        val start = when (horizontalGravity()) {
            Gravity.RIGHT -> maxColumns - visibleCols
            Gravity.CENTER_HORIZONTAL -> (maxColumns - visibleCols) / 2
            else -> 0
        }
        return ColumnWindow(clamp(start, 0, maxColumns - visibleCols), clamp(start, 0, maxColumns - visibleCols) + visibleCols, 0)
    }

    private fun horizontalOffset(availableWidth: Float, contentWidth: Float): Float {
        val free = max(0f, availableWidth - contentWidth)
        return when (horizontalGravity()) {
            Gravity.RIGHT -> free
            Gravity.CENTER_HORIZONTAL -> free / 2f
            else -> 0f
        }
    }

    private fun horizontalOffsetColumns(visibleCols: Int, contentCols: Int): Int {
        val free = max(0, visibleCols - contentCols)
        return when (horizontalGravity()) {
            Gravity.RIGHT -> free
            Gravity.CENTER_HORIZONTAL -> free / 2
            else -> 0
        }
    }

    private fun horizontalGravity(): Int {
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection)
        return absoluteGravity and Gravity.HORIZONTAL_GRAVITY_MASK
    }

    private fun cellWidthPx(): Float {
        return max(1f, asciiPaint.measureText("M"))
    }

    private fun lineHeightPx(): Float {
        val metrics = asciiPaint.fontMetrics
        return max(1f, metrics.descent - metrics.ascent)
    }

    private fun baseTextSizePx(): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            VIEWPORT_TEXT_SIZE_SP,
            resources.displayMetrics
        )
    }

    private fun minTextSizePx(): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            MIN_VIEWPORT_TEXT_SIZE_SP,
            resources.displayMetrics
        )
    }

    private fun isAutoFitMode(): Boolean {
        return viewportRowsSetting <= 0
    }

    private fun clamp(value: Int, minValue: Int, maxValue: Int): Int {
        return max(minValue, min(value, maxValue))
    }

    private data class ContentBounds(
        val hasContent: Boolean,
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int
    ) {
        fun width(): Int {
            return if (hasContent) right - left + 1 else 1
        }

        fun height(): Int {
            return if (hasContent) bottom - top + 1 else 1
        }

        companion object {
            val EMPTY = ContentBounds(false, 0, 0, 0, 0)

            fun from(lines: List<String>): ContentBounds {
                var left = Int.MAX_VALUE
                var right = -1
                var top = Int.MAX_VALUE
                var bottom = -1

                for (row in lines.indices) {
                    val line = lines[row]
                    for (col in line.indices) {
                        if (!line[col].isWhitespace()) {
                            left = min(left, col)
                            right = max(right, col)
                            top = min(top, row)
                            bottom = max(bottom, row)
                        }
                    }
                }

                if (left == Int.MAX_VALUE) {
                    return EMPTY
                }
                return ContentBounds(true, left, right, top, bottom)
            }
        }
    }

    private data class ColumnWindow(val start: Int, val end: Int, val offset: Int)

    companion object {
        private const val VIEWPORT_TEXT_SIZE_SP = 12f
        private const val MIN_VIEWPORT_TEXT_SIZE_SP = 2.5f
        private const val FIT_INSET_PX = 4f
        private const val MIN_FIT_GLYPH_PX = 4f
        private const val DEFAULT_PANE_ROWS = 10
        private const val DEFAULT_VIEWPORT_ROWS = -1
        private const val MAX_AUTO_VIEWPORT_ROWS = 48
        private const val MAX_BITMAP_CACHE_FRAMES = 32
        private const val MAX_BITMAP_CACHE_BYTES = 24 * 1024 * 1024
    }
}
