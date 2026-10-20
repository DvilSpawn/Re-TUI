package ohi.andre.consolelauncher.navigation

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class InlineFrameSelectorTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } finally { bitmap.recycle() }
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun libraryIncludesUnusedImportsPackAssetsAndInvalidReferences() {
        check(context.packageName.endsWith(".fetchdebug"))
        val root = File(context.filesDir, "frame-selector-${UUID.randomUUID()}")
        val active = File(root, "active").apply { mkdirs() }
        File(active, "state.json").writeText("""{"schema":4,"applyToAll":false,"assignments":{},"packs":{},"activePackId":null}""")
        try {
            val directory = File(root, "draft")
            val session = FrameManager.createDraft(directory, active)
            val zeta = session.importFrame(FrameTarget.OUTPUT, "Zeta.png", ByteArrayInputStream(png()))
            val alpha = session.importFrame(FrameTarget.OUTPUT, "alpha.png", ByteArrayInputStream(png()))
            session.createPack("Saved frames")
            session.select(FrameTarget.OUTPUT, null)
            val missing = "1".repeat(64)
            val corrupt = "2".repeat(64)
            session.select(FrameTarget.INPUT, missing)
            File(directory, "library-$corrupt.retui-frame").writeText("broken bundle")
            File(directory, "not-a-frame.txt").writeText("ignored")

            val assets = session.assets()
            assertEquals(4, assets.size)
            assertEquals(listOf("alpha", "Zeta"), assets.filter { it.valid }.map { it.name })
            assertEquals(setOf(zeta.id, alpha.id), assets.filter { it.valid }.map { it.id }.toSet())
            assertEquals(setOf(missing, corrupt), assets.filterNot { it.valid }.map { it.id }.toSet())
            assertEquals(assets, FrameManager.reopenDraft(directory, active.canonicalPath).assets())
        } finally { root.deleteRecursively() }
    }

    @Test fun inlineSelectionIsPerSurfaceAndDoesNotPublishBeforeSave() {
        check(context.packageName.endsWith(".fetchdebug"))
        val activity = instrumentation.startActivitySync(Intent(context, ThemerActivity::class.java)
            .putExtra(ThemerActivity.EXTRA_SECTION, ThemerActivity.SECTION_FRAMES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ThemerActivity
        val hub = ViewModelProvider(activity)[ThemerActivity.HubState::class.java]
        val previous = hub.frameEditSession
        val active = File(Tuils.getFolder(), FrameManager.FRAME_FOLDER)
        val savedBefore = File(active, FrameManager.STATE_FILE).readBytes()
        val directory = File(context.filesDir, "frame-selector-ui-${UUID.randomUUID()}")
        val session = FrameManager.createDraft(directory, active)
        try {
            session.applyToAll = false
            val reusable = session.importFrame(FrameTarget.OUTPUT, "Reusable frame.png", ByteArrayInputStream(png()))
            val missing = "3".repeat(64)
            session.select(FrameTarget.INPUT, missing)
            instrumentation.runOnMainSync {
                hub.frameEditSession = session
                views(activity.window.decorView).filterIsInstance<RecyclerView>().single().adapter!!.notifyDataSetChanged()
            }
            instrumentation.waitForIdleSync()
            fun labels() = views(activity.window.decorView).filterIsInstance<TextView>()
            fun selector() = labels().first { it.text.startsWith("FRAME:") }
            fun click(view: View) {
                instrumentation.runOnMainSync {
                    assertTrue("Enabled control: ${(view as TextView).text}", view.isEnabled)
                    assertTrue("Clickable control: ${view.text}", view.isClickable)
                    view.performClick()
                }
                instrumentation.waitForIdleSync()
            }

            click(selector())
            instrumentation.runOnMainSync {
                assertTrue(selector().text.endsWith("▴"))
                assertFalse(labels().first { it.text.startsWith("⚠") }.isEnabled)
            }
            click(labels().single { it.text.toString() == reusable.name })
            assertEquals(reusable.id, session.selectedAssetId(FrameTarget.STATUS_GROUP))
            assertEquals(missing, session.selectedAssetId(FrameTarget.INPUT))
            instrumentation.runOnMainSync {
                assertTrue(selector().text.endsWith("▾"))
                assertFalse(labels().any { it.text.toString() == context.getString(R.string.frame_selector_default) })
            }
            click(selector())
            click(labels().single { it.text.toString() == context.getString(R.string.frame_selector_default) })
            assertNull(session.selectedAssetId(FrameTarget.STATUS_GROUP))
            click(selector())
            click(labels().filterIsInstance<CheckBox>().single {
                it.text.toString() == context.getString(R.string.themer_apply_one_frame_to_all_surfaces_aa400)
            })
            instrumentation.runOnMainSync {
                assertEquals(1, labels().count { it.text.startsWith("FRAME:") })
                assertTrue(selector().text.endsWith("▾"))
            }
            click(selector())
            instrumentation.runOnMainSync {
                selector().requestRectangleOnScreen(android.graphics.Rect(0, 0, selector().width, selector().height + 700), true)
            }
            instrumentation.waitForIdleSync()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "inline-frame-selector.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            click(labels().single { it.text.toString() == reusable.name })
            assertEquals(reusable.id, session.selectedAssetId(null))
            assertTrue(session.hasChanges())
            assertArrayEquals(savedBefore, File(active, FrameManager.STATE_FILE).readBytes())
        } finally {
            instrumentation.runOnMainSync { hub.frameEditSession = previous; activity.finish() }
            session.discard()
        }
    }
}
