package ohi.andre.consolelauncher.tuils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBorderRuntimeTest {
    @Test fun disabledSurfaceNeverDrawsInAnyMode() {
        assertFalse(TerminalBorderRuntime.drawsBorder(false, false, false, false))
        assertFalse(TerminalBorderRuntime.drawsBorder(false, false, false, true))
        assertFalse(TerminalBorderRuntime.drawsBorder(false, false, true, true))
        assertTrue(TerminalBorderRuntime.drawsBorder(false, true, false, true))
        assertTrue(TerminalBorderRuntime.drawsBorder(false, true, true, false))
        assertFalse(TerminalBorderRuntime.drawsBorder(true, true, true, true))
    }

    @Test fun forcedTabBorderDrawsOutsideDashedAndCyberdeckModes() {
        assertFalse(TerminalBorderRuntime.drawsTabBorder(false, false, false, false, 255))
        assertTrue(TerminalBorderRuntime.drawsTabBorder(false, true, false, false, 255))
        assertFalse(TerminalBorderRuntime.drawsTabBorder(false, true, false, false, 0))
        assertFalse(TerminalBorderRuntime.drawsTabBorder(true, true, true, true, 255))
    }

    @Test fun importedFrameOnlyHonorsExplicitFrameSuppression() {
        assertFalse(TerminalBorderRuntime.drawsFrame(false, true))
        assertFalse(TerminalBorderRuntime.drawsFrame(true, false))
        assertTrue(TerminalBorderRuntime.drawsFrame(true, true))
    }
}
