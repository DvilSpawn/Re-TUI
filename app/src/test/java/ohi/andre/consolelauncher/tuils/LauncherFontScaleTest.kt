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

    @Test
    fun supportsStagedPerSurfaceTypography() {
        assertEquals(20f, LauncherFontScale.effectiveSp(14, 6, true), 0f)
        assertEquals(14f, LauncherFontScale.effectiveSp(14, 6, false), 0f)
        assertEquals(8, LauncherFontScale.adjustedBaseSp(8, -1, 8, 64))
        assertEquals(64, LauncherFontScale.adjustedBaseSp(64, 1, 8, 64))
        assertEquals(15, LauncherFontScale.adjustedBaseSp(14, 1, 8, 64))
    }
}
