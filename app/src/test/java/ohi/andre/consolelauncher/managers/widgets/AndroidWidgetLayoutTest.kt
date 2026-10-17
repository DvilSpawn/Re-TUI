package ohi.andre.consolelauncher.managers.widgets

import org.junit.Assert.*
import org.junit.Test

class AndroidWidgetLayoutTest {
    private fun widget(id: Int = 1, col: Int = 0, row: Int = 0, width: Int = 1, height: Int = 1) =
        WidgetRecord("0$id", id, "fixture/provider", col, row, width, height)

    @Test fun loneWidgetKeepsItsDraggedPositionAcrossRenders() {
        val moved = widget(col = 2, row = 5)
        val saved = AndroidWidgetLayout.place(moved, listOf(widget()), 4)
        assertEquals(listOf(moved), AndroidWidgetLayout.layout(saved, 4))
    }

    @Test fun draggingOnlyRelocatesCollisionsAndReservesUnaffectedWidgets() {
        val moved = widget(col = 1, row = 0)
        val collision = widget(2, col = 1)
        val untouched = widget(3)
        val result = AndroidWidgetLayout.place(moved, listOf(widget(), collision, untouched), 4)
        assertEquals(moved, result.first { it.appWidgetId == 1 })
        assertEquals(untouched, result.first { it.appWidgetId == 3 })
        assertEquals(2, result.first { it.appWidgetId == 2 }.col)
        assertEquals(result, AndroidWidgetLayout.layout(result, 4))
    }

    @Test fun narrowRenderingDoesNotRewriteTheSavedWideLayout() {
        val saved = listOf(widget(col = 6, row = 4, width = 2), widget(2, col = 1, row = 1))
        val narrow = AndroidWidgetLayout.layout(saved, 4)
        assertTrue(narrow.all { it.col >= 0 && it.col + it.colSpan <= 4 })
        assertEquals(6, saved.first().col)
        assertEquals(saved.sortedBy { it.row }, AndroidWidgetLayout.layout(saved, 8))
    }

    @Test fun shrinkingEachEdgeKeepsTheOppositeEdgeAnchored() {
        val record = widget(col = 1, row = 2, width = 3, height = 4)
        assertEquals(record.copy(colSpan = 2), AndroidWidgetLayout.shrinkHorizontalEdge(record, ResizeEdge.RIGHT, 1, 1))
        assertEquals(record.copy(col = 2, colSpan = 2), AndroidWidgetLayout.shrinkHorizontalEdge(record, ResizeEdge.LEFT, 1, 1))
        assertEquals(record.copy(rowSpan = 3), AndroidWidgetLayout.shrinkVerticalEdge(record, ResizeEdge.BOTTOM, 1, 1))
        assertEquals(record.copy(row = 3, rowSpan = 3), AndroidWidgetLayout.shrinkVerticalEdge(record, ResizeEdge.TOP, 1, 1))
    }

    @Test fun resizeStopsAtTheMinimumAndLayoutResolvesOverlaps() {
        val record = widget(width = 3, height = 4)
        assertEquals(2, AndroidWidgetLayout.shrinkHorizontalEdge(record, ResizeEdge.RIGHT, 2, 50).colSpan)
        assertEquals(2, AndroidWidgetLayout.shrinkVerticalEdge(record, ResizeEdge.BOTTOM, 2, 50).rowSpan)
        val result = AndroidWidgetLayout.layout(listOf(widget(), widget(2)), 4)
        assertEquals(listOf(0, 1), result.map { it.col })
    }
    @Test fun growthUsesTheDisplayedGridAndKeepsTheOppositeEdgeFixed() {
        val record = widget(col = 5, row = 3, width = 2, height = 2)
        assertEquals(record.copy(colSpan = 3), AndroidWidgetLayout.growHorizontalEdge(record, ResizeEdge.RIGHT, 4, 8, 3))
        assertEquals(record.copy(col = 3, colSpan = 4), AndroidWidgetLayout.growHorizontalEdge(record, ResizeEdge.LEFT, 4, 8, 10))
        assertEquals(record.copy(row = 0, rowSpan = 5), AndroidWidgetLayout.growVerticalEdge(record, ResizeEdge.TOP, 8, 10))
        assertEquals(record.copy(rowSpan = 8), AndroidWidgetLayout.growVerticalEdge(record, ResizeEdge.BOTTOM, 8, 10))
        val atEdge = record.copy(col = 6)
        assertEquals(atEdge, AndroidWidgetLayout.growHorizontalEdge(atEdge, ResizeEdge.RIGHT, 4, 8, 5))
    }

}
