package ohi.andre.consolelauncher.tuils

import org.junit.Assert.assertEquals
import org.junit.Test

class NineSliceFrameDrawableTest {
    @Test fun opposingBordersShrinkProportionallyWhenTheTargetIsTooSmall() {
        val fitted = NineSliceFrameDrawable.fitBorders(30f, 20f, 40f)
        assertEquals(10f, fitted.first, 0.001f)
        assertEquals(20f, fitted.second, 0.001f)
    }
}
