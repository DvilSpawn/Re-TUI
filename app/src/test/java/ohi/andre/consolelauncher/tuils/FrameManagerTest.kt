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

    @Test fun controlFrameContractsStayAvailableForPerElementImports() {
        assertEquals(
            listOf(
                "button", "button_pressed", "button_primary", "icon_button",
                "toggle_off", "toggle_on", "slider_track", "slider_progress", "slider_thumb"
            ),
            FrameTarget.entries.map { it.id }.filter {
                it.startsWith("button") || it == "icon_button" || it.startsWith("toggle_") || it.startsWith("slider_")
            }
        )
    }

    @Test fun rawPngDefaultsToAThreeByThreeNearestNeighborFrame() {
        val spec = FrameManager.defaultPngSpec(48, 48)
        assertEquals(16, spec.leftPx)
        assertEquals(16, spec.topPx)
        assertEquals(16, spec.rightPx)
        assertEquals(16, spec.bottomPx)
        assertEquals("tile", spec.topMode)
        assertEquals("stretch", spec.centerMode)
        assertEquals("nearest", spec.filtering)
        assertTrue(FrameManager.hasPngSignature(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        assertFalse(FrameManager.hasPngSignature(byteArrayOf(0x50, 0x4e, 0x47)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rawPngRejectsImagesThatCannotBeSlicedIntoTheContractGrid() {
        FrameManager.defaultPngSpec(50, 50)
    }

}
