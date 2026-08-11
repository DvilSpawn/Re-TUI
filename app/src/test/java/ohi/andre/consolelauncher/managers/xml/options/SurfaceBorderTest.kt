package ohi.andre.consolelauncher.managers.xml.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceBorderTest {
    @Test fun manualColorOverridesInheritedAndAutoFallsBack() {
        val inherited = 0xFF123456.toInt()
        assertEquals(0xFFABCDEF.toInt(), SurfaceBorder.resolveColor("#ABCDEF", inherited))
        assertEquals(0x80112233.toInt(), SurfaceBorder.resolveColor("#80112233", inherited))
        assertEquals(inherited, SurfaceBorder.resolveColor("auto", inherited))
        assertEquals(inherited, SurfaceBorder.resolveColor("bad", inherited))
    }

    @Test fun masterAndSurfaceFlagsBothGateTheBorder() {
        assertTrue(SurfaceBorder.isEnabled(true, true))
        assertFalse(SurfaceBorder.isEnabled(false, true))
        assertFalse(SurfaceBorder.isEnabled(true, false))
    }
}
