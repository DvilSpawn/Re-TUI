package ohi.andre.consolelauncher.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteModelTest {
    @Test
    fun normalizeTagsTrimsHashesAndDedupes() {
        val note = Note().apply {
            tags.addAll(listOf(" #Work ", "work", "##home", " ", "#Ideas"))
        }
        note.normalizeTags()
        assertEquals(listOf("Work", "home", "Ideas"), note.tags.toList())
    }

    @Test
    fun pinnedAloneIsMeaningful() {
        assertTrue(Note(pinned = true).hasMeaningfulContent())
        assertFalse(Note().hasMeaningfulContent())
        assertTrue(Note().apply { tags.add("x") }.hasMeaningfulContent())
    }

    @Test
    fun checkCountsAcrossMarkdownTaskLists() {
        val note = Note(markdown = "# List\n\n- [x] a\n- [ ] b\n\n- [X] c\n")
        assertEquals(2 to 3, note.checkCounts())
    }

    @Test
    fun duplicateTitleLookupIsTrimmedCaseInsensitiveAndReturnsOriginal() {
        val original = Note(createdAt = 1, title = " Field Notes ")
        val later = Note(createdAt = 2, title = "field notes")

        assertTrue(listOf(later, original).findTitleDuplicate("FIELD NOTES") === original)
    }
}
