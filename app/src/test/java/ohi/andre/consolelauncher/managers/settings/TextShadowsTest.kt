package ohi.andre.consolelauncher.managers.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TextShadowsTest {
    @Test fun parsesSharedOffsetsAndSafelyFallsBackForInvalidValues() {
        assertArrayEquals(floatArrayOf(-2f, 3f, 4.5f), TextShadows.parameters("-2,3,4.5"), 0f)
        assertArrayEquals(floatArrayOf(2f, 2f, 0.2f), TextShadows.parameters("NaN,bad,-1"), 0f)
        assertArrayEquals(floatArrayOf(0f, 2f, 0.2f), TextShadows.parameters("0"), 0f)
    }
}
