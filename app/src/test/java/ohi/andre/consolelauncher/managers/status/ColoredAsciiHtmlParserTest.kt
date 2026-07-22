package ohi.andre.consolelauncher.managers.status

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColoredAsciiHtmlParserTest {
    @Test
    fun parsesPreformattedCharactersAndRgbColors() {
        val parsed = ColoredAsciiHtmlParser.parseOrNull(
            """<html><pre><span style="color:rgb(1, 2, 3)">A&amp;</span>
                |<span style="color:#abcdef">B</span><script>ignored</script></pre></html>""".trimMargin()
        )!!

        assertEquals("A&\nB", parsed.text)
        assertArrayEquals(
            intArrayOf(0xff010203.toInt(), 0xff010203.toInt(), 0, 0xffabcdef.toInt()),
            parsed.colors
        )
    }

    @Test
    fun rejectsDocumentsWithoutPreformattedAscii() {
        assertNull(ColoredAsciiHtmlParser.parseOrNull("<html><body>not ASCII</body></html>"))
    }
}
