package ohi.andre.consolelauncher

import android.view.View
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

    @Test
    fun clampsOutputAutoHideDuration() {
        assertEquals(1_000L, UIManager.outputAutoHideDelayMs(0))
        assertEquals(10_000L, UIManager.outputAutoHideDelayMs(10))
        assertEquals(3_600_000L, UIManager.outputAutoHideDelayMs(9_999))
    }

    @Test
    fun hidesOnlyTheOutputSurfaceWhenAutoHideIsCollapsed() {
        assertEquals(View.INVISIBLE, UIManager.outputPaneVisibility(true, false))
        assertEquals(View.VISIBLE, UIManager.outputPaneVisibility(true, true))
        assertEquals(View.VISIBLE, UIManager.outputPaneVisibility(false, false))
    }

    @Test
    fun collapsesTrayToTheMeasuredHeaderEdge() {
        assertEquals(24, UIManager.headerOnlyTerminalTrayHeight(24, 32))
        assertEquals(32, UIManager.headerOnlyTerminalTrayHeight(0, 32))
    }
}
