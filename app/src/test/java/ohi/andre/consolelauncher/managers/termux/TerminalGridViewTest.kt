package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalGridViewTest {
    @Test
    fun decLineDrawingConvertsBordersOnlyWhileShiftedOut() {
        assertEquals("┌──┐", "lqqk".map { decLineDrawingGlyph(it, true) }.joinToString(""))
        assertEquals("│  │", "x  x".map { decLineDrawingGlyph(it, true) }.joinToString(""))
        assertEquals("└──┘", "mqqj".map { decLineDrawingGlyph(it, true) }.joinToString(""))
        assertEquals('q', decLineDrawingGlyph('q', false))
        assertEquals('x', decLineDrawingGlyph('x', false))
    }
}
