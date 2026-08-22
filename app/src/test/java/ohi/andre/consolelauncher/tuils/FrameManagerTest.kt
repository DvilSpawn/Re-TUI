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

    @Test fun packsReplaceTheCompleteFrameSetup() {
        val output = "a".repeat(64)
        val button = "b".repeat(64)
        val first = FrameManager.FramePack(
            "c".repeat(64), "First", false, mapOf(FrameTarget.OUTPUT.id to output)
        )
        val state = FrameManager.FrameState(
            false,
            mutableMapOf(FrameTarget.BUTTON.id to button),
            mutableMapOf(first.id to first)
        )
        val session = FrameManager.EditSession(Files.createTempDirectory("retui-pack-apply").toFile(), state)

        session.applyPack(first.id)

        assertEquals(first.id, session.activePackId())
        assertEquals(output, session.selectedAssetId(FrameTarget.OUTPUT))
        assertNull(session.selectedAssetId(FrameTarget.BUTTON))
    }

    @Test fun createAndReplacePackCaptureTheStagedSetup() {
        val output = "a".repeat(64)
        val button = "b".repeat(64)
        val state = FrameManager.FrameState(false, mutableMapOf(FrameTarget.OUTPUT.id to output))
        val session = FrameManager.EditSession(Files.createTempDirectory("retui-pack-save").toFile(), state)

        val pack = session.createPack("  Sprout Lands  ")
        assertEquals("Sprout Lands", pack.name)
        assertEquals(output, pack.assignments[FrameTarget.OUTPUT.id])
        assertEquals(pack.id, session.activePackId())

        session.select(FrameTarget.BUTTON, button)
        assertNull(session.activePackId())
        val replaced = session.replacePack(pack.id)
        assertEquals(pack.id, replaced.id)
        assertEquals("Sprout Lands", replaced.name)
        assertEquals(button, replaced.assignments[FrameTarget.BUTTON.id])
        assertEquals(pack.id, session.activePackId())
    }

    @Test fun packNamesAreValidatedAndUniqueIgnoringCase() {
        val session = FrameManager.EditSession(
            Files.createTempDirectory("retui-pack-name").toFile(),
            FrameManager.FrameState(true, mutableMapOf())
        )
        session.createPack("Default")

        assertEquals("Pack name must be 1 to 80 characters.", session.packNameError(" "))
        assertEquals("A frame pack with that name already exists.", session.packNameError("default"))
        assertTrue(runCatching { session.createPack("DEFAULT") }.isFailure)
    }

    @Test fun deletingTheActivePackReturnsFramesToDefaults() {
        val asset = "a".repeat(64)
        val pack = FrameManager.FramePack(
            "b".repeat(64), "Active", false, mapOf(FrameTarget.OUTPUT.id to asset)
        )
        val session = FrameManager.EditSession(
            Files.createTempDirectory("retui-pack-delete").toFile(),
            FrameManager.FrameState(
                false,
                mutableMapOf(FrameTarget.OUTPUT.id to asset),
                mutableMapOf(pack.id to pack),
                pack.id
            )
        )

        assertTrue(session.deletePack(pack.id))
        assertTrue(session.applyToAll)
        assertNull(session.selectedAssetId(FrameTarget.OUTPUT))
        assertNull(session.activePackId())
    }

    @Test fun deletingAnInactivePackKeepsTheActiveSetup() {
        val asset = "a".repeat(64)
        val active = FrameManager.FramePack(
            "b".repeat(64), "Active", false, mapOf(FrameTarget.OUTPUT.id to asset)
        )
        val inactive = FrameManager.FramePack("c".repeat(64), "Inactive", true, emptyMap())
        val session = FrameManager.EditSession(
            Files.createTempDirectory("retui-pack-delete-inactive").toFile(),
            FrameManager.FrameState(
                false,
                mutableMapOf(FrameTarget.OUTPUT.id to asset),
                mutableMapOf(active.id to active, inactive.id to inactive),
                active.id
            )
        )

        assertFalse(session.deletePack(inactive.id))
        assertEquals(active.id, session.activePackId())
        assertEquals(asset, session.selectedAssetId(FrameTarget.OUTPUT))
    }

    @Test fun oldStateSchemasMigrateWithoutDroppingPacks() {
        val asset = "a".repeat(64)
        val packId = "b".repeat(64)
        val schema2 = frameStateRoot(
            """{"schema":2,"applyToAll":false,"assignments":{"output":"$asset"}}"""
        )
        assertTrue(FrameManager.stateForSpace(schema2).packs.isEmpty())

        val schema3 = frameStateRoot(
            """{"schema":3,"applyToAll":false,"assignments":{"output":"$asset"},"packs":{"$packId":{"name":"Legacy","roles":{"output":"$asset"}}}}"""
        )
        val migrated = FrameManager.stateForSpace(schema3)
        assertEquals("Legacy", migrated.packs[packId]?.name)
        assertEquals(packId, migrated.activePackId)

        val schema4 = frameStateRoot(
            """{"schema":4,"applyToAll":false,"assignments":{"output":"$asset"},"packs":{"$packId":{"name":"Current","applyToAll":false,"assignments":{"output":"$asset"}}},"activePackId":"$packId"}"""
        )
        assertEquals(packId, FrameManager.stateForSpace(schema4).activePackId)
    }

    @Test fun cleanupKeepsPackAssetsButDropsLooseUnassignedAssets() {
        val active = "a".repeat(64)
        val packed = "b".repeat(64)
        val loose = "c".repeat(64)
        val pack = FrameManager.FramePack(
            "d".repeat(64), "Pack", false, mapOf(FrameTarget.BUTTON.id to packed)
        )
        val state = FrameManager.FrameState(
            false,
            mutableMapOf(FrameTarget.OUTPUT.id to active),
            mutableMapOf(pack.id to pack)
        )

        val retained = FrameManager.referencedAssetIds(state)
        assertEquals(setOf(active, packed), retained)
        assertFalse(loose in retained)
    }

    private fun frameStateRoot(state: String) = Files.createTempDirectory("retui-frame-state").toFile().also {
        val frames = it.resolve(FrameManager.FRAME_FOLDER)
        assertTrue(frames.mkdirs())
        frames.resolve(FrameManager.STATE_FILE).writeText(state)
    }

}
