package ohi.andre.consolelauncher.tuils

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherFontScaleTest {
    @Test
    fun addsOffsetWithoutChangingTheBaseSize() {
        assertEquals(9f, LauncherFontScale.scaledSp(14f, -5), 0f)
        assertEquals(17f, LauncherFontScale.scaledSp(14f, 3), 0f)
        assertEquals(6f, LauncherFontScale.scaledSp(14f, -99), 0f)
    }
}
