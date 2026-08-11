package ohi.andre.consolelauncher.tuils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBorderRuntimeTest {
    @Test fun disabledSurfaceNeverDrawsInAnyMode() {
        assertFalse(TerminalBorderRuntime.drawsBorder(false, false, false))
        assertFalse(TerminalBorderRuntime.drawsBorder(false, false, true))
        assertFalse(TerminalBorderRuntime.drawsBorder(false, true, true))
        assertTrue(TerminalBorderRuntime.drawsBorder(true, false, true))
        assertTrue(TerminalBorderRuntime.drawsBorder(true, true, false))
    }

    @Test fun forcedTabBorderDrawsOutsideDashedAndCyberdeckModes() {
        assertFalse(TerminalBorderRuntime.drawsTabBorder(false, false, false, 255))
        assertTrue(TerminalBorderRuntime.drawsTabBorder(true, false, false, 255))
        assertFalse(TerminalBorderRuntime.drawsTabBorder(true, false, false, 0))
    }
}
