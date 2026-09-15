package ohi.andre.consolelauncher.notes

import ohi.andre.consolelauncher.R
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LauncherNotesIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun interruptedWritesRecoverIndexAndMarkdownBackups() {
        val root = File(context.cacheDir, "notes-recovery-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
            val store = NoteStore(isolated)
            val library = NoteLibrary()
            val note = Note(title = "Recovery", markdown = "# Recovery\n\nPreserved text")
            library.notes.add(note)
            store.save(library)
            val index = File(root, "remember-notes.xml")
            val markdown = File(root, note.file!!)
            check(index.renameTo(File(index.path + ".bak")))
            check(markdown.renameTo(File(markdown.path + ".bak")))
            assertTrue(store.hasDatabase())
            val restored = store.load().notes.single()
            assertEquals(note.id, restored.id)
            assertEquals(note.markdown, restored.markdown)
            assertTrue(index.isFile)
            assertTrue(markdown.isFile)
        } finally { root.deleteRecursively() }
    }

    @Test fun migrationAndPrivateFilesPreserveDataAndLocks() {
        val root = File(context.cacheDir, "notes-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val legacy = File(root, "legacy.xml")
            val xml = "<NOTES><note creationTime=\"100\" value=\"First line&#10;Second line\" lock=\"true\"/><note creationTime=\"100\" value=\"First line&#10;Second line\" lock=\"false\"/></NOTES>"
            legacy.writeText(xml)
            val library = LauncherNotes.importLegacyFile(legacy)
            assertEquals(xml, legacy.readText())
            assertEquals(2, library.notes.size)
            assertNotEquals(library.notes[0].id, library.notes[1].id)
            assertEquals(library.notes[0].id, LauncherNotes.importLegacyFile(legacy).notes[0].id)
            assertEquals("First line\nSecond line", library.notes[0].markdown)
            val isolated = object : ContextWrapper(context) { override fun getFilesDir() = root }
            val store = NoteStore(isolated)
            store.save(library)
            val loaded = store.load()
            assertTrue(loaded.notes[0].locked)
            assertEquals("First line\nSecond line", File(root, loaded.notes[0].file!!).readText())
            assertEquals(2, File(root, "notes-library").walk().count { it.extension == "md" })
            try { store.delete(loaded.notes[0], loaded); fail("Locked note deleted") } catch (_: IllegalStateException) { }
            assertEquals(2, store.load().notes.size)
            val index = File(root, "remember-notes.xml")
            index.writeText("broken")
            try { store.load(); fail("Corrupt index treated as empty") } catch (_: NoteStore.ReadException) { }
            assertEquals("broken", index.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun notesTaskSurvivesLauncherReloadAndRecreation() {
        val launcher = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        val store = NoteStore(context)
        val library = store.load()
        val note = Note(title = "Integration check", markdown = "# Integration check\n\nOriginal", folderId = library.ensureDefaultFolder().id)
        library.notes.add(note)
        store.save(library)
        var siblingId: String? = null
        var activity: RememberActivity? = null
        try {
            val monitor = instrumentation.addMonitor(RememberActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { LauncherNotes.open(launcher, note.id) }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000) as RememberActivity
            instrumentation.removeMonitor(monitor)
            val notes = activity
            instrumentation.waitForIdleSync()
            assertNotEquals(launcher.taskId, notes.taskId)
            val task = notes.taskId
            instrumentation.runOnMainSync {
                val editor = findEditor(notes.window.decorView)!!
                editor.setText("# Integration check\n\nEdited full file\n- [ ] Task\n\n日本語 हिन्दी العربية café")
                editor.setSelection(editor.length())
            }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(1000) // Wait for the activity transition before capturing the rendered editor.
            File(context.cacheDir, "notes-editor-check.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            instrumentation.runOnMainSync {
                notes.showNewNotePrompt(RetuiTheme.receive(notes, notes.intent), emptyList(), emptyList(), null,
                    { fail("Unexpected open") }, { _, _ -> fail("Unexpected create") })
            }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(1000)
            File(context.cacheDir, "notes-new-dialog-check.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            // Switching to Home persists the edit; reloading Home must not finish the Notes task.
            instrumentation.runOnMainSync { launcher.startActivity(Intent(launcher, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { launcher.reload() }
            instrumentation.waitForIdleSync()
            assertFalse(notes.isFinishing)
            assertEquals(task, notes.taskId)
            assertTrue(store.load().notes.first { it.id == note.id }.markdown.contains("Edited full file"))
            val changed = store.load()
            val sibling = Note(title = "Added while Notes was paused", markdown = "Another full file")
            siblingId = sibling.id
            changed.notes.add(sibling)
            store.save(changed)
            instrumentation.runOnMainSync { LauncherNotes.open(context, note.id) }
            instrumentation.waitForIdleSync()
            assertEquals(task, notes.taskId)
            val recreated = instrumentation.addMonitor(RememberActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { notes.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(recreated, 10000) as RememberActivity
            instrumentation.removeMonitor(recreated)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(findEditor(activity!!.window.decorView)!!.markdown().contains("Edited full file"))
                LauncherNotes.open(context)
            }
            instrumentation.waitForIdleSync()
            awaitUi { findEditor(activity!!.window.decorView) == null }
            assertTrue(store.load().notes.any { it.id == siblingId })
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
            val current = store.load()
            current.notes.filter { it.id == note.id || it.id == siblingId }.toList().forEach { store.delete(it, current) }
        }
    }

    @Test fun paneFileTapDoesNotAlsoOpenLibrary() {
        val launcher = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        var fileClicks = 0
        var libraryClicks = 0
        lateinit var pane: android.widget.TextView
        instrumentation.runOnMainSync {
            pane = android.widget.TextView(launcher)
            val text = android.text.SpannableString("File title")
            text.setSpan(object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) { fileClicks++ }
            }, 0, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            pane.text = text
            pane.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            NotesPaneLinks.bind(pane)
            pane.setOnClickListener { libraryClicks++ }
            launcher.addContentView(pane, ViewGroup.LayoutParams(600, 200))
        }
        instrumentation.waitForIdleSync()
        fun tap(x: Float, y: Float) {
            instrumentation.runOnMainSync {
                val now = android.os.SystemClock.uptimeMillis()
                val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
                val up = android.view.MotionEvent.obtain(now, now + 50, android.view.MotionEvent.ACTION_UP, x, y, 0)
                pane.dispatchTouchEvent(down); pane.dispatchTouchEvent(up)
                down.recycle(); up.recycle()
            }
            instrumentation.waitForIdleSync()
        }
        try {
            tap(10f, 10f)
            assertEquals(1, fileClicks)
            assertEquals(0, libraryClicks)
            tap(500f, 150f)
            assertEquals(1, fileClicks)
            assertEquals(1, libraryClicks)
            instrumentation.runOnMainSync {
                val now = android.os.SystemClock.uptimeMillis()
                listOf(Triple(android.view.MotionEvent.ACTION_DOWN, 10f, 100f),
                    Triple(android.view.MotionEvent.ACTION_MOVE, 10f, 10f),
                    Triple(android.view.MotionEvent.ACTION_UP, 10f, 10f)).forEach { (action, x, y) ->
                    val event = android.view.MotionEvent.obtain(now, now + 50, action, x, y, 0)
                    pane.dispatchTouchEvent(event)
                    event.recycle()
                }
            }
            instrumentation.waitForIdleSync()
            assertEquals(1, fileClicks)
            assertEquals(1, libraryClicks)
        } finally {
            instrumentation.runOnMainSync { (pane.parent as ViewGroup).removeView(pane) }
        }
    }

    @Test fun transparentLibraryAndDocumentReplaceTheSamePane() {
        val store = NoteStore(context)
        val library = store.load()
        val fixture = Note(title = "Pane replacement check", markdown = "# Document only\n\nThe library must be gone.")
        library.notes.add(fixture)
        store.save(library)
        val preferences = context.getSharedPreferences("remember-theme", 0)
        val saved = preferences.all.toMap()
        var activity: RememberActivity? = null
        try {
            activity = instrumentation.startActivitySync(Intent(context, RememberActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("panel_bg", android.graphics.Color.TRANSPARENT)
                .putExtra("input_bg", android.graphics.Color.TRANSPARENT)
                .putExtra("panel_text", android.graphics.Color.GREEN)
                .putExtra("input_text", android.graphics.Color.RED)) as RememberActivity
            val notes = activity
            instrumentation.waitForIdleSync()
            lateinit var pane: ViewGroup
            lateinit var oldLibrary: View
            instrumentation.runOnMainSync {
                pane = notes.window.decorView.findViewWithTag("retui_panel")
                oldLibrary = pane.getChildAt(0)
                clickText(pane, context.getString(R.string.notes_rememberactivity_unfiled_c1aca) + "/")
                clickText(pane, fixture.title)
                assertSame(pane, notes.window.decorView.findViewWithTag("retui_panel"))
                assertNull(oldLibrary.parent)
                assertNull(findText(pane, context.getString(R.string.notes_rememberactivity_last_modified_622cd)))
                assertNull(findEditor(pane)!!.background)
                assertEquals(android.graphics.Color.GREEN, findEditor(pane)!!.currentTextColor)
            }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(250)
            File(context.cacheDir, "notes-transparent-transition.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            repeat(3) {
                instrumentation.runOnMainSync {
                    notes.onBackPressedDispatcher.onBackPressed()
                    assertSame(pane, notes.window.decorView.findViewWithTag("retui_panel"))
                    assertNull(findEditor(pane))
                    assertNotNull(findText(pane, context.getString(R.string.notes_rememberactivity_last_modified_622cd)))
                    clickText(pane, fixture.title)
                    assertNull(findText(pane, context.getString(R.string.notes_rememberactivity_last_modified_622cd)))
                    assertNull(findEditor(pane)!!.background)
                }
                instrumentation.waitForIdleSync()
            }
        } finally {
            instrumentation.runOnMainSync { activity?.finishAndRemoveTask() }
            instrumentation.waitForIdleSync()
            val current = store.load()
            current.notes.firstOrNull { it.id == fixture.id }?.let { store.delete(it, current) }
            preferences.edit().clear().apply {
                saved.forEach { (key, value) -> when (value) {
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                } }
            }.commit()
        }
    }

    private fun findText(view: View, text: String): View? {
        if (view is android.widget.TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findText(view.getChildAt(index), text)?.let { return it }
        return null
    }

    private fun clickText(root: View, text: String) {
        var target = checkNotNull(findText(root, text)) { "Missing $text" }
        while (!target.isClickable) target = target.parent as View
        target.performClick()
    }

    private fun awaitUi(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            android.os.SystemClock.sleep(25)
        }
        fail("Expected UI state was not reached")
    }

    private fun findEditor(view: View): MarkdownEditText? {
        if (view is MarkdownEditText) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findEditor(view.getChildAt(index))?.let { return it }
        return null
    }
}
