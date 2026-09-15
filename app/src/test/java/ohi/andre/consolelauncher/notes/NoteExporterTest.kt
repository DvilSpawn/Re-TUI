package ohi.andre.consolelauncher.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteExporterTest {
    @Test
    fun markdownPreservesMetadataAndBlockOrder() {
        val image = Block.Image(file = "images/photo.png", alt = "receipt")
        val note = Note(
            title = "Trip",
            pinned = true,
            tags = mutableListOf("travel", "todo"),
            location = Place(1.25, 2.5, "Station"),
            blocks = mutableListOf(
                Block.Text(text = "Before"),
                Block.Checklist(items = mutableListOf(Item(checked = true, text = "Tickets"))),
                image,
                Block.Text(text = "After")
            )
        )

        val markdown = NoteExporter.markdown(note) { "images/photo.png" }

        assertTrue(markdown.contains("Pinned: yes"))
        assertTrue(markdown.contains("Tags: #travel, #todo"))
        assertTrue(markdown.contains("Location: Station (1.25, 2.5)"))
        assertTrue(markdown.contains("- [x] Tickets"))
        assertTrue(markdown.contains("![receipt](images/photo.png)"))
        assertTrue(markdown.indexOf("Before") < markdown.indexOf("Tickets"))
        assertTrue(markdown.indexOf("Tickets") < markdown.indexOf("![receipt]"))
        assertTrue(markdown.indexOf("![receipt]") < markdown.indexOf("After"))
    }

    @Test
    fun exportNamesCannotEscapeTheirDirectory() {
        assertEquals("private_notes", NoteExporter.safeName("../../private notes"))
        assertEquals("नोट्स_日本語_ملاحظات", NoteExporter.safeName("नोट्स 日本語 ملاحظات"))
    }
}
