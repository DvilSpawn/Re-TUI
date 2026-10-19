package ohi.andre.consolelauncher.tuils

import org.junit.Assert.*
import org.junit.Test

class ToolbarGestureDirectionTest {
    @Test fun directionsUseDominantAxisAndDeadZone() {
        assertNull(ToolbarGestureDirection.fromDrag(23f, -23f, 24f))
        assertEquals(ToolbarGestureDirection.UP, ToolbarGestureDirection.fromDrag(5f, -40f, 24f))
        assertEquals(ToolbarGestureDirection.DOWN, ToolbarGestureDirection.fromDrag(-5f, 40f, 24f))
        assertEquals(ToolbarGestureDirection.LEFT, ToolbarGestureDirection.fromDrag(-40f, 5f, 24f))
        assertEquals(ToolbarGestureDirection.RIGHT, ToolbarGestureDirection.fromDrag(40f, -5f, 24f))
        assertNull(ToolbarGestureDirection.fromDrag(0f, 0f, 24f))
    }
}
