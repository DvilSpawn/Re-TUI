package ohi.andre.consolelauncher.tuils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class NineSliceFrameDrawableTest {
    @Test fun destinationBoundariesSnapToWholePixels() {
        assertArrayEquals(
            floatArrayOf(3f, 14f, 85f, 104f),
            NineSliceFrameDrawable.destinationBoundaries(3, 104, 10.6f, 19.4f),
            0f
        )
    }

    @Test fun allBordersUseTheMostConstrainedAxis() {
        assertEquals(
            0.5f,
            NineSliceFrameDrawable.fitScale(200f, 50f, 40f, 50f, 40f, 50f),
            0.001f
        )
        assertEquals(
            0.25f,
            NineSliceFrameDrawable.fitScale(20f, 200f, 40f, 50f, 40f, 50f),
            0.001f
        )
        assertEquals(
            1f,
            NineSliceFrameDrawable.fitScale(100f, 100f, 40f, 40f, 40f, 40f),
            0.001f
        )
    }

    @Test fun intrinsicSizePreservesTallSliderThumbAspectRatio() {
        assertEquals(43 to 72, NineSliceFrameDrawable.intrinsicDimensions(18, 30, 72f))
        assertEquals(72 to 43, NineSliceFrameDrawable.intrinsicDimensions(30, 18, 72f))
    }
}
