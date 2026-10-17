package ohi.andre.consolelauncher.managers.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetResizePreviewTest {
    private val start = WidgetResizeRect(104, 204, 396, 496)
    private fun preview(left: Boolean, top: Boolean, dx: Float, dy: Float,
        minW: Int = 92, maxW: Int = 392, minH: Int = 92, maxH: Int = 592) =
        previewWidgetCorner(start, left, top, dx, dy, minW, maxW, minH, maxH, 504, 4)

    @Test fun partialCellDragChangesActualPixelBounds() {
        assertEquals(WidgetResizeRect(104, 204, 369, 533), preview(false, false, -27f, 37f))
    }
    @Test fun eachCornerKeepsTheOppositeEdgesAnchored() {
        assertEquals(WidgetResizeRect(121, 227, 396, 496), preview(true, true, 17f, 23f))
        assertEquals(WidgetResizeRect(121, 204, 396, 519), preview(true, false, 17f, 23f))
        assertEquals(WidgetResizeRect(104, 227, 413, 496), preview(false, true, 17f, 23f))
    }
    @Test fun providerMinimumsAndBoardEdgesLimitPreview() {
        assertEquals(WidgetResizeRect(104, 204, 196, 296), preview(false, false, -900f, -900f))
        assertEquals(WidgetResizeRect(4, 4, 396, 496), preview(true, true, -900f, -900f))
        assertEquals(WidgetResizeRect(104, 204, 496, 796), preview(false, false, 900f, 900f))
    }
    @Test fun fixedAxisDoesNotResizeWhileTheOtherAxisFollowsTheDrag() {
        assertEquals(WidgetResizeRect(104, 204, 396, 523), preview(false, false, 37f, 27f, 292, 292))
    }
}
