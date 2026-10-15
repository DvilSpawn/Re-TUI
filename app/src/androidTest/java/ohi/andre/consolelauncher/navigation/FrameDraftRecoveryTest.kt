package ohi.andre.consolelauncher.navigation

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.LocalizedStateException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class FrameDraftRecoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(run: (File, File) -> Unit) {
        check(context.packageName.endsWith(".palettedebug"))
        val root = File(context.filesDir, "frame-draft-test-${UUID.randomUUID()}").apply { mkdirs() }
        val active = File(root, "active").apply { mkdirs() }
        File(active, "state.json").writeText("""{"schema":4,"applyToAll":true,"assignments":{},"packs":{},"activePackId":null}""")
        try { run(active, File(root, "draft")) } finally { root.deleteRecursively() }
    }
    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally { bitmap.recycle() }
    }

    @Test fun reopenFromDiskRecoversImportedAssetPackAndUnsavedState() = fixture { active, directory ->
        val savedBefore = File(active, "state.json").readBytes()
        val token: String?
        val assetId: String
        run {
            val original = FrameManager.createDraft(directory, active)
            original.applyToAll = false
            assetId = original.importFrame(FrameTarget.OUTPUT, "Draft.png", ByteArrayInputStream(png())).id
            original.createPack("Recovered draft pack")
            token = original.draftToken
        }
        // Rebuild from bytes, without retaining the original session or a ViewModel.
        val recovered = FrameManager.reopenDraft(directory, active.canonicalPath)
        assertEquals(token, recovered.draftToken)
        assertEquals(assetId, recovered.selectedAssetId(FrameTarget.OUTPUT))
        assertFalse(recovered.applyToAll)
        assertTrue(recovered.hasChanges())
        assertEquals("Recovered draft pack", recovered.packs().single().name)
        assertEquals(recovered.packs().single().id, recovered.currentPackId())
        assertNotNull(recovered.assignedDetails(FrameTarget.OUTPUT))
        assertArrayEquals(savedBefore, File(active, "state.json").readBytes())
        recovered.discard()
        assertFalse(directory.exists())
    }

    @Test fun changedBaselineCannotOverwriteActiveFramesAndKeepsRecoverableDraft() = fixture { active, directory ->
        val original = FrameManager.createDraft(directory, active)
        original.applyToAll = false
        File(active, "state.json").writeText("""{"schema":4,"applyToAll":false,"assignments":{},"packs":{},"activePackId":null}""")
        val changed = File(active, "state.json").readBytes()
        val recovered = FrameManager.reopenDraft(directory, active.canonicalPath)
        try { recovered.save(); fail("A stale draft must not overwrite the active frames") }
        catch (_: LocalizedStateException) { }
        assertArrayEquals(changed, File(active, "state.json").readBytes())
        assertTrue(FrameManager.reopenDraft(directory, active.canonicalPath).hasChanges())
    }

    @Test fun sessionTokenAndConfigurationRootAreValidatedOnReopen() = fixture { active, directory ->
        FrameManager.createDraft(directory, active)
        assertTrue(runCatching { FrameManager.reopenDraft(directory, File(active, "foreign").canonicalPath) }.isFailure)
        File(directory, "session-token").writeText(UUID.randomUUID().toString())
        assertTrue(runCatching { FrameManager.reopenDraft(directory, active.canonicalPath) }.isFailure)
        assertTrue(File(directory, "checkpoint.json").exists())
    }
}
