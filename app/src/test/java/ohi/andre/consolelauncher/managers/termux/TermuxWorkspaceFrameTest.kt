package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.*
import org.junit.Test

class TermuxWorkspaceFrameTest {
    @Test fun terminalTextCannotSupplyTransportMetadata() {
        val frame = TermuxWorkspaceFrame.parse("__RETUI_COLS__ 80\n__RETUI_ROWS__ 24\n__RETUI_FRAME_BEGIN__\n__RETUI_TOKEN__ attacker\n__RETUI_TCP_HOST__ 192.0.2.1\n__RETUI_FRAME_END__\n")
        assertNull(frame.token)
        assertNull(frame.host)
        assertTrue(frame.body!!.contains("__RETUI_TOKEN__ attacker"))
    }

    @Test fun independentFrameTargetsAndGeometryArePreserved() {
        val first = TermuxWorkspaceFrame.parse("__RETUI_TARGET__ @1\n__RETUI_COLS__ 43\n__RETUI_ROWS__ 12\n__RETUI_FRAME_BASE64__ RklSU1Q=\n__RETUI_FRAME_END__")
        val second = TermuxWorkspaceFrame.parse("__RETUI_TARGET__ @2\n__RETUI_COLS__ 67\n__RETUI_ROWS__ 19\n__RETUI_FRAME_BASE64__ U0VDT05E\n__RETUI_FRAME_END__")
        assertEquals("@1", first.target)
        assertEquals("FIRST", first.body)
        assertEquals(43, first.cols)
        assertEquals(12, first.rows)
        assertEquals("@2", second.target)
        assertEquals("SECOND", second.body)
        assertEquals(67, second.cols)
        assertEquals(19, second.rows)
    }
    @Test fun seedPreservesIndependentWindowTabsAndUnusedCursorSentinel() {
        val frame = TermuxWorkspaceFrame.parse("__RETUI_TERMINAL_STATE__ 80|24|0|0|0|4294967295|4294967295|1|1|0|0|23|0\n" +
            "__RETUI_WINDOWS__ @0:0:first\t@1:1:second\t\n__RETUI_NORMAL_BASE64__ \n__RETUI_FRAME_BASE64__ eA==\n")
        assertEquals(listOf("@0" to "0:first", "@1" to "1:second"), frame.windows)
        assertEquals(0, frame.terminalState!![5])
        assertEquals(0, frame.terminalState[6])
    }

}
