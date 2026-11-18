package ohi.andre.consolelauncher.managers.reader

import ohi.andre.consolelauncher.mono.MonoLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class BookOrderTest {
    @Test
    fun comicPagesSortByNumberNotByLetter() {
        val names = listOf("vol1/page10.jpg", "vol1/page2.jpg", "vol1/page1.jpg", "vol2/page1.jpg")
        assertEquals(listOf("vol1/page1.jpg", "vol1/page2.jpg", "vol1/page10.jpg", "vol2/page1.jpg"), names.sortedWith(Comic::natural))
    }

    @Test
    fun bookKindsComeFromTheExtension() {
        assertEquals(Books.Format.EPUB, Books.formatOf("Moby Dick.EPUB"))
        assertEquals(Books.Format.MARKDOWN, Books.formatOf("notes.md"))
        assertEquals(Books.Format.COMIC, Books.formatOf("nemo.cbz"))
        assertEquals(null, Books.formatOf("song.mp3"))
    }

    @Test
    fun monoLayoutKnowsTheReadPanel() {
        assertEquals(listOf(MonoLayout.Kind.CLOCK, MonoLayout.Kind.READ), MonoLayout.parse("clock, read")[0].map { it.kind })
    }
}
