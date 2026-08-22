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

    @Test fun namedPackFilesMapOnlyToKnownSurfaces() {
        assertEquals("global", FrameManager.namedPackAssignment("global.png"))
        FrameTarget.entries.forEach {
            assertEquals(it.id, FrameManager.namedPackAssignment(it.fileName))
        }
        assertEquals("suggestions", FrameManager.namedPackAssignment("suggestion_chip.png"))
        assertNull(FrameManager.namedPackAssignment("suggestions.png"))
        assertNull(FrameManager.namedPackAssignment("panel.png"))
        assertNull(FrameManager.namedPackAssignment("Output.png"))
        assertNull(FrameManager.namedPackAssignment("nested/output.png"))
    }

    @Test fun macFolderMetadataDoesNotBlockPackImports() {
        assertTrue(FrameManager.isIgnoredPackEntry(".DS_Store"))
        assertTrue(FrameManager.isIgnoredPackEntry("._panel.png"))
        assertFalse(FrameManager.isIgnoredPackEntry("manifest.json"))
    }

    @Test fun canonicalFramesUseEqualThirdsAndRepeatTheirEdges() {
        val panel = FrameManager.defaultFrameSpec("settings", 144, 144)
        assertEquals(48, panel.leftPx)
        assertEquals(48, panel.topPx)
        assertEquals(8f, panel.leftDp)
        assertEquals("tile", panel.topMode)
        assertEquals("tile", panel.leftMode)
        assertEquals("stretch", panel.centerMode)
        assertEquals("nearest", panel.filtering)

        assertTrue(runCatching { FrameManager.defaultFrameSpec("output", 106, 122) }.isFailure)
        assertTrue(runCatching { FrameManager.defaultFrameSpec("output", 32, 32) }.isFailure)
    }

    @Test fun applyingPackReplacesLegacyGlobalFrameWithMappedRoles() {
        val packId = "a".repeat(64)
        val panelId = "b".repeat(64)
        val buttonId = "c".repeat(64)
        val pack = FrameManager.FramePack(
            packId,
            "Test Pack",
            mapOf(FrameTarget.SETTINGS.id to panelId, FrameTarget.BUTTON.id to buttonId)
        )
        val state = FrameManager.FrameState(
            true,
            mutableMapOf("global" to "d".repeat(64)),
            mutableMapOf(packId to pack)
        )
        val session = FrameManager.EditSession(Files.createTempDirectory("retui-pack").toFile(), state)

        session.applyPack(packId)

        assertFalse(session.applyToAll)
        assertNull(session.selectedAssetId(null))
        assertEquals(panelId, session.selectedAssetId(FrameTarget.SETTINGS))
        assertEquals(buttonId, session.selectedAssetId(FrameTarget.BUTTON))
    }

    @Test fun deletingPackClearsUniqueSelectionsButKeepsSharedOnes() {
        val firstId = "a".repeat(64)
        val secondId = "b".repeat(64)
        val uniqueId = "c".repeat(64)
        val sharedId = "d".repeat(64)
        val state = FrameManager.FrameState(
            false,
            mutableMapOf(FrameTarget.OUTPUT.id to uniqueId, FrameTarget.BUTTON.id to sharedId),
            mutableMapOf(
                firstId to FrameManager.FramePack(firstId, "First", mapOf("output" to uniqueId, "button" to sharedId)),
                secondId to FrameManager.FramePack(secondId, "Second", mapOf("button" to sharedId))
            )
        )
        val session = FrameManager.EditSession(Files.createTempDirectory("retui-delete-pack").toFile(), state)

        session.deletePack(firstId)

        assertEquals(listOf("Second"), session.packs().map { it.name })
        assertNull(session.selectedAssetId(FrameTarget.OUTPUT))
        assertEquals(sharedId, session.selectedAssetId(FrameTarget.BUTTON))
    }
}
