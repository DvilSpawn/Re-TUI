package ohi.andre.consolelauncher.managers.mediaplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTest {
    @Test fun readsTimedLinesInOrderAndIgnoresTags() {
        val lines = Lyrics.parse("[ar:Someone]\n[00:12.50]second\n[00:01.5]first")
        assertEquals(listOf(LyricLine(1500, "first"), LyricLine(12500, "second")), lines)
    }

    @Test fun repeatsALineWithSeveralStamps() {
        assertEquals(listOf(LyricLine(1000, "x"), LyricLine(62000, "x")), Lyrics.parse("[00:01.00][01:02.00]x"))
    }

    @Test fun plainTextKeepsItsBreaksButNotTheEdges() {
        assertEquals(listOf(LyricLine(null, "a"), LyricLine(null, ""), LyricLine(null, "b")), Lyrics.parse("\na\n\nb\n"))
    }

    @Test fun appliesTheFilesOffset() {
        assertEquals(listOf(LyricLine(500, "x")), Lyrics.parse("[offset:+500]\n[00:01.00]x"))
        assertEquals(listOf(LyricLine(1250, "x")), Lyrics.parse("[00:01.00]x\n[offset:-250]"))
    }

    @Test fun anOffsetNeverGoesBeforeTheStart() {
        assertEquals(listOf(LyricLine(0, "x")), Lyrics.parse("[offset:5000]\n[00:01.00]x"))
    }

    @Test fun findsTheLineBeingSung() {
        val lines = Lyrics.parse("[00:01.00]a\n[00:05.00]b")
        assertEquals(-1, Lyrics.indexAt(lines, 500))
        assertEquals(0, Lyrics.indexAt(lines, 4999))
        assertEquals(1, Lyrics.indexAt(lines, 5000))
    }
}
