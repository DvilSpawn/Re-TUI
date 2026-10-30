package ohi.andre.consolelauncher.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTrayFitTest {
    private val header = 40
    private val minimum = TerminalTrayPresentation.minimumOpen(60, 30)

    @Test
    fun openPaneKeepsThreeLinesWhenTheTopSectionLeavesNoRoom() {
        assertEquals(150, minimum)
        // Toggled pane, opened: asks for 400 but only 10px are left below the status rows.
        assertEquals(minimum, TerminalTrayPresentation.fitRoom(400, 400, 10, header, false, minimum))
        // Native pane that fills the room it gets.
        assertEquals(minimum, TerminalTrayPresentation.fitRoom(300, 0, 0, header, true, minimum))
    }

    @Test
    fun closedPaneStaysAHeaderAndRoomyLayoutsAreUnchanged() {
        assertEquals(header, TerminalTrayPresentation.fitRoom(header, 0, 0, header, false, minimum))
        assertEquals(400, TerminalTrayPresentation.fitRoom(400, 400, 900, header, false, minimum))
        assertEquals(500, TerminalTrayPresentation.fitRoom(300, 500, 900, header, true, minimum))
        // Room unknown: the request stands.
        assertEquals(400, TerminalTrayPresentation.fitRoom(400, 400, -1, header, false, minimum))
    }
}
