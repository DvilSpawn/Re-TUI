package ohi.andre.consolelauncher.managers.widgets

import kotlin.math.max
import ohi.andre.consolelauncher.managers.termux.TerminalHomeLayout

internal data class WidgetRecord(
        val wid: String,
        val appWidgetId: Int,
        val provider: String,
        val col: Int,
        val row: Int,
        val colSpan: Int,
        val rowSpan: Int
    )

internal data class WidgetPosition(val col: Int, val row: Int)


internal enum class ResizeEdge {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }


internal object AndroidWidgetLayout {
    fun layout(records: List<WidgetRecord>, columns: Int): List<WidgetRecord> {
        val placed = mutableListOf<WidgetRecord>()
        val deferred = mutableListOf<WidgetRecord>()
        records.sortedWith(compareBy<WidgetRecord> { it.row }.thenBy { it.col }).forEach { record ->
            val bounded = normalizeRecord(record, columns)
            if (isOpenSlot(placed, bounded.col, bounded.row, bounded.colSpan, bounded.rowSpan)) {
                placed.add(bounded)
            } else deferred.add(bounded)
        }
        deferred.forEach { record ->
            val position = nextOpenPosition(placed, record.colSpan, record.rowSpan, columnCount = columns)
            placed.add(record.copy(col = position.col, row = position.row))
        }
        return placed.sortedWith(compareBy<WidgetRecord> { it.row }.thenBy { it.col })
    }

    fun place(locked: WidgetRecord, records: List<WidgetRecord>, columns: Int): List<WidgetRecord> {
        val placed = mutableListOf(normalizeRecord(locked, columns))
        val others = records.filter { it.appWidgetId != locked.appWidgetId }
            .map { normalizeRecord(it, columns) }
        // Reserve the dragged widget and all unaffected widgets before relocating collisions.
        val deferred = mutableListOf<WidgetRecord>()
        others.forEach { record ->
            if (isOpenSlot(placed, record.col, record.row, record.colSpan, record.rowSpan)) placed.add(record)
            else deferred.add(record)
        }
        deferred.forEach { record ->
            val position = nextOpenPosition(placed, record.colSpan, record.rowSpan, columnCount = columns)
            placed.add(record.copy(col = position.col, row = position.row))
        }
        return placed.sortedWith(compareBy<WidgetRecord> { it.row }.thenBy { it.col })
    }

    /**
     * Terminal-mode arrangement: the edited widget stays where it was put, widgets in its way are pushed straight
     * down in order, and with [settle] everything then floats back up. Null when the widget cannot fit there.
     */
    fun flow(locked: WidgetRecord, records: List<WidgetRecord>, columns: Int, settle: Boolean): List<WidgetRecord>? {
        fun tile(record: WidgetRecord) = TerminalHomeLayout.Tile(record.appWidgetId.toString(), record.col, record.row, record.colSpan, record.rowSpan)
        val engine = TerminalHomeLayout(columns) { true }
        val original = layout(records, columns)
        if (original.none { it.appWidgetId == locked.appWidgetId }) return null
        val moved = engine.reflow(tile(normalizeRecord(locked, columns)), original.map(::tile)) ?: return null
        val placement = (if (settle) engine.compact(moved) else moved).associateBy { it.id }
        return original.map { record ->
            placement.getValue(record.appWidgetId.toString()).let { record.copy(col = it.x, row = it.y, colSpan = it.width, rowSpan = it.height) }
        }
    }

    fun normalizeRecord(record: WidgetRecord, columnCount: Int): WidgetRecord {
        val colSpan = record.colSpan.coerceIn(1, columnCount)
        return record.copy(
            col = record.col.coerceIn(0, columnCount - colSpan),
            row = max(0, record.row),
            colSpan = colSpan,
            rowSpan = max(1, record.rowSpan)
        )
    }

    fun shrinkHorizontalEdge(
        record: WidgetRecord,
        edge: ResizeEdge,
        minimumColumns: Int,
        resizeStep: Int
    ): WidgetRecord {
        if (record.colSpan > minimumColumns) {
            val shrinkBy = minOf(resizeStep, record.colSpan - minimumColumns)
            val newColSpan = record.colSpan - shrinkBy
            return if (edge == ResizeEdge.LEFT) {
                record.copy(col = record.col + shrinkBy, colSpan = newColSpan)
            } else {
                record.copy(colSpan = newColSpan)
            }
        }
        return record
    }

    fun shrinkVerticalEdge(
        record: WidgetRecord,
        edge: ResizeEdge,
        minimumRows: Int,
        resizeStep: Int
    ): WidgetRecord {
        if (record.rowSpan > minimumRows) {
            val shrinkBy = minOf(resizeStep, record.rowSpan - minimumRows)
            val newRowSpan = record.rowSpan - shrinkBy
            return if (edge == ResizeEdge.TOP) {
                record.copy(row = record.row + shrinkBy, rowSpan = newRowSpan)
            } else {
                record.copy(rowSpan = newRowSpan)
            }
        }
        return record
    }

    fun growHorizontalEdge(record: WidgetRecord, edge: ResizeEdge, maximumColumns: Int,
        gridColumns: Int, step: Int): WidgetRecord {
        val available = if (edge == ResizeEdge.LEFT) record.col else gridColumns - record.col - record.colSpan
        val growBy = minOf(step, maximumColumns - record.colSpan, available).coerceAtLeast(0)
        return record.copy(col = record.col - if (edge == ResizeEdge.LEFT) growBy else 0,
            colSpan = record.colSpan + growBy)
    }

    fun growVerticalEdge(record: WidgetRecord, edge: ResizeEdge, maximumRows: Int, step: Int): WidgetRecord {
        val available = if (edge == ResizeEdge.TOP) record.row else step
        val growBy = minOf(step, maximumRows - record.rowSpan, available).coerceAtLeast(0)
        return record.copy(row = record.row - if (edge == ResizeEdge.TOP) growBy else 0,
            rowSpan = record.rowSpan + growBy)
    }

    fun nextOpenPosition(
        records: List<WidgetRecord>,
        colSpan: Int,
        rowSpan: Int,
        startRow: Int = 0,
        columnCount: Int
    ): WidgetPosition {
        val boundedColSpan = colSpan.coerceIn(1, columnCount)
        for (row in max(0, startRow)..120) {
            for (col in 0..(columnCount - boundedColSpan)) {
                if (isOpenSlot(records, col, row, boundedColSpan, rowSpan)) {
                    return WidgetPosition(col, row)
                }
            }
        }
        val row = records.maxOfOrNull { it.row + it.rowSpan } ?: 0
        return WidgetPosition(0, row)
    }

    private fun isOpenSlot(
        records: List<WidgetRecord>,
        col: Int,
        row: Int,
        colSpan: Int,
        rowSpan: Int
    ): Boolean {
        val right = col + colSpan
        val bottom = row + rowSpan
        for (record in records) {
            val recordRight = record.col + record.colSpan
            val recordBottom = record.row + record.rowSpan
            val intersects = col < recordRight
                && right > record.col
                && row < recordBottom
                && bottom > record.row
            if (intersects) {
                return false
            }
        }
        return true
    }

}

internal data class WidgetResizeRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

internal fun previewWidgetCorner(start: WidgetResizeRect, left: Boolean, top: Boolean, dx: Float, dy: Float,
    minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int, gridWidth: Int, margin: Int): WidgetResizeRect {
    val x = kotlin.math.round(dx).toInt()
    val y = kotlin.math.round(dy).toInt()
    val newLeft = if (left && minWidth != maxWidth)
        (start.left + x).coerceIn(maxOf(margin, start.right - maxWidth), start.right - minWidth) else start.left
    val newRight = if (!left && minWidth != maxWidth)
        (start.right + x).coerceIn(start.left + minWidth, minOf(gridWidth - margin, start.left + maxWidth)) else start.right
    val newTop = if (top && minHeight != maxHeight)
        (start.top + y).coerceIn(maxOf(margin, start.bottom - maxHeight), start.bottom - minHeight) else start.top
    val newBottom = if (!top && minHeight != maxHeight)
        (start.bottom + y).coerceIn(start.top + minHeight, start.top + maxHeight) else start.bottom
    return WidgetResizeRect(newLeft, newTop, newRight, newBottom)
}
