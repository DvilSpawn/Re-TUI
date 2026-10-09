package ohi.andre.consolelauncher.help

import org.junit.Assert.*
import org.junit.Test

class HelpMarkupTest {
    @Test fun explicitBlocksDoNotInferEnglishProse() {
        val blocks = HelpMarkup.parse("## 見出し\n\nParagraph **bold** and `help`.\n\n- One\n2. Two\n\n```\nhelp apps\n```\n\n>! Warning\n\nExamples: is ordinary prose")
        assertEquals(listOf("heading", "paragraph", "bullet", "number", "code", "warning", "paragraph"), blocks.map { it.kind })
        assertEquals("help apps", blocks[4].text)
        assertEquals("Examples: is ordinary prose", blocks.last().text)
    }
    @Test fun legacyParagraphsAndUnclosedFenceRemainReadable() {
        assertEquals(listOf("First", "Second"), HelpMarkup.parse("First\n\nSecond").map { it.text })
        assertEquals("literal * and <xml>\nlast", HelpMarkup.parse("```\nliteral * and <xml>\nlast").single().text)
    }
}
