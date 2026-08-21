package ohi.andre.consolelauncher.managers.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidWidgetSizingTest {
    @Test fun providerMetadataChoosesAndBoundsTheGridSpan() {
        val github = calculateWidgetSpanBounds(
            WidgetSizeSpec(
                minWidth = 324,
                minHeight = 108,
                minResizeWidth = 324,
                minResizeHeight = 108,
                maxResizeWidth = 1296,
                maxResizeHeight = 324,
                targetColumns = 0,
                targetRows = 0,
                horizontalResizable = true,
                verticalResizable = true
            ),
            cellSize = 200,
            cellMargin = 10,
            maxColumns = 4,
            fallbackColumns = 1,
            fallbackRows = 1,
            maxRows = 120
        )

        assertEquals(WidgetSpan(2, 1), github.initial)
        assertEquals(4, github.maxColumns)
        assertEquals(120, github.maxRows)
        assertEquals(WidgetSpan(4, 2), github.constrain(4, 2))
    }

    @Test fun targetCellsWinAndFixedAxesStayFixed() {
        val fixed = calculateWidgetSpanBounds(
            WidgetSizeSpec(
                minWidth = 100,
                minHeight = 100,
                minResizeWidth = 0,
                minResizeHeight = 0,
                maxResizeWidth = 0,
                maxResizeHeight = 0,
                targetColumns = 3,
                targetRows = 2,
                horizontalResizable = false,
                verticalResizable = false
            ),
            cellSize = 100,
            cellMargin = 0,
            maxColumns = 4,
            fallbackColumns = 1,
            fallbackRows = 1,
            maxRows = 120
        )

        assertEquals(WidgetSpan(3, 2), fixed.initial)
        assertEquals(WidgetSpan(3, 2), fixed.constrain(1, 20))
    }

    @Test fun missingMetadataUsesTheFallbackWithoutForcingTwoByTwo() {
        val fallback = calculateWidgetSpanBounds(
            WidgetSizeSpec(0, 0, 0, 0, 0, 0, 0, 0, true, true),
            cellSize = 100,
            cellMargin = 0,
            maxColumns = 4,
            fallbackColumns = 1,
            fallbackRows = 1,
            maxRows = 120
        )

        assertEquals(WidgetSpan(1, 1), fallback.initial)
    }

    @Test fun independentlyResizableAxesIgnoreProviderMaximums() {
        val listWidget = calculateWidgetSpanBounds(
            WidgetSizeSpec(
                minWidth = 200,
                minHeight = 200,
                minResizeWidth = 100,
                minResizeHeight = 100,
                maxResizeWidth = 400,
                maxResizeHeight = 600,
                targetColumns = 0,
                targetRows = 0,
                horizontalResizable = false,
                verticalResizable = true
            ),
            cellSize = 100,
            cellMargin = 0,
            maxColumns = 4,
            fallbackColumns = 1,
            fallbackRows = 1,
            maxRows = 120
        )

        assertEquals(2, listWidget.minColumns)
        assertEquals(2, listWidget.maxColumns)
        assertEquals(1, listWidget.minRows)
        assertEquals(120, listWidget.maxRows)
        assertEquals(WidgetSpan(2, 20), listWidget.constrain(4, 20))
    }
}
