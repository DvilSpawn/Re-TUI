package ohi.andre.consolelauncher.tuils

import java.nio.file.Files
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameManagerTest {
    @Test fun globalModeIgnoresSurfaceAssignments() {
        assertNull(FrameManager.resolvedTarget(true, FrameTarget.OUTPUT))
        assertEquals(FrameTarget.OUTPUT, FrameManager.resolvedTarget(false, FrameTarget.OUTPUT))
    }

    @Test fun surfaceTargetsAndPortablePathsStayStable() {
        assertEquals(FrameTarget.STATUS_NOTES, FrameTarget.fromSurface(SurfaceBorder.NOTES))
        assertEquals("files", FrameTarget.FILES.id)
        assertTrue(FrameManager.isPortableEntry("frames/status_notes.retui-frame"))
        assertTrue(FrameManager.isPortableEntry("frames/library-${"a".repeat(64)}.retui-frame"))
        assertFalse(FrameManager.isPortableEntry("frames/library-short.retui-frame"))
        assertFalse(FrameManager.isPortableEntry("frames/nested/library-${"a".repeat(64)}.retui-frame"))
    }

    @Test fun spaceWithoutFrameStateStartsWithoutAssignments() {
        val state = FrameManager.stateForSpace(Files.createTempDirectory("retui-space").toFile())
        assertTrue(state.applyToAll)
        assertTrue(state.assignments.isEmpty())
    }
}
