package ohi.andre.consolelauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class UIManagerImeSizingTest {
    @Test
    fun keepsPersistentInputAboveImeByShrinkingOutputFirst() {
        assertEquals(400, UIManager.constrainTerminalOutputHeight(400, 1000, 0, 40, 180, false))
        assertEquals(400, UIManager.constrainTerminalOutputHeight(400, 1000, 300, 40, 180, true))
        assertEquals(280, UIManager.constrainTerminalOutputHeight(400, 800, 300, 40, 180, true))
        assertEquals(0, UIManager.constrainTerminalOutputHeight(400, 400, 300, 40, 180, true))
    }
}
