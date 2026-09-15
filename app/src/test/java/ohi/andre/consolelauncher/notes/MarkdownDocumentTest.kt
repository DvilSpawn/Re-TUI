package ohi.andre.consolelauncher.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownDocumentTest {
    @Test
    fun legacyBlocksBecomeOneOrderedMarkdownDocument() {
        val note = Note(
            title = "Trip",
            blocks = mutableListOf(
                Block.Text(text = "Before"),
                Block.Checklist(items = mutableListOf(Item(checked = true, text = "Tickets"))),
                Block.Image(file = "images/trip/photo.jpg", alt = "receipt"),
                Block.Text(text = "After")
            )
        )

        val markdown = MarkdownDocument.fromLegacy(note)

        assertTrue(markdown.startsWith("# Trip"))
        assertTrue(markdown.indexOf("Before") < markdown.indexOf("- [x] Tickets"))
        assertTrue(markdown.indexOf("Tickets") < markdown.indexOf("![receipt]"))
        assertTrue(markdown.indexOf("![receipt]") < markdown.indexOf("After"))
        assertEquals(listOf("images/trip/photo.jpg"), MarkdownDocument.imagePaths(markdown))
    }

    @Test
    fun titleAndReadableTextComeFromMarkdown() {
        val markdown = "# **Field Notes**\n\nHello **world**\n\n- [ ] Call home\n"

        assertEquals("Field Notes", MarkdownDocument.title(markdown))
        assertEquals("Hello world\n\nCall home", MarkdownDocument.plainText(markdown))
    }

    @Test
    fun emptyVisualMarkersCollapseToABlankLine() {
        listOf("# ", "### ", "- [ ] ", "- [x]", "- ", "* ", "+ ", "12. ", "> ").forEach {
            assertEquals("", MarkdownDocument.removeEmptyVisualMarker(it))
        }
        assertEquals("  ", MarkdownDocument.removeEmptyVisualMarker("  - [ ] "))
        assertEquals("- still here", MarkdownDocument.removeEmptyVisualMarker("- still here"))
    }

    @Test
    fun webLinksNormalizeAndReadAsLabels() {
        assertEquals("https://example.com/article", MarkdownDocument.normalizeWebUrl("example.com/article"))
        assertEquals(null, MarkdownDocument.normalizeWebUrl("file:///private/note"))
        assertEquals("Read this", MarkdownDocument.plainText("# Links\n\n[Read this](https://example.com/article)"))
    }
}
