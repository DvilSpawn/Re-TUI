package ohi.andre.consolelauncher.notes

import ohi.andre.consolelauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderLibraryTest {
    @Test
    fun legacyNotesMigrateIntoLowercaseNotesFolderInMemory() {
        val legacy = mutableListOf(Note(title = "one"), Note(title = "two"))

        val library = NoteLibrary.fromLegacy(legacy)

        assertEquals(listOf("notes"), library.folders.map { it.name })
        assertTrue(library.notes.all { it.folderId == DEFAULT_NOTES_FOLDER_ID })
    }

    @Test
    fun deletingFolderMovesNotesToUnfiledWithoutChangingTheirContent() {
        val folder = NoteFolder(name = "Work")
        val image = Block.Image(file = "images/kept.png")
        val note = Note(title = "Keep me", folderId = folder.id, blocks = mutableListOf(image))
        val library = NoteLibrary(mutableListOf(folder), mutableListOf(note))

        library.deleteFolder(folder.id)

        assertTrue(library.folders.isEmpty())
        assertNull(note.folderId)
        assertEquals("Keep me", note.title)
        assertEquals("images/kept.png", (note.blocks.single() as Block.Image).file)
    }

    @Test
    fun folderNamesAreTrimmedByCallerAndValidatedCaseInsensitively() {
        val folders = listOf(NoteFolder(name = "Personal"))

        assertEquals(R.string.notes_folder_duplicate, FolderRules.error(" personal ", folders))
        assertEquals(R.string.notes_folder_invalid, FolderRules.error("work/home", folders))
        assertEquals(R.string.notes_folder_invalid, FolderRules.error("work\u0000home", folders))
        assertEquals(R.string.notes_folder_required, FolderRules.error("   ", folders))
        assertNull(FolderRules.error("Projects", folders))
    }
}
