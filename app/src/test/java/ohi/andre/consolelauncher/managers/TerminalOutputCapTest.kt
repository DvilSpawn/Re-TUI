package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalOutputCapTest {
    @Test fun keepsOnlyTheLastLines() {
        val text = "\none\ntwo\nthree"
        assertEquals("two\nthree", text.substring(TerminalManager.overflowStart(text, 2)))
    }

    @Test fun shortOutputIsNotTrimmed() {
        assertEquals(0, TerminalManager.overflowStart("\none\ntwo", 2))
        assertEquals(0, TerminalManager.overflowStart("", TerminalManager.OUTPUT_LINE_CAP))
    }

    @Test fun unlimitedOutputIsCapped() {
        val text = buildString { repeat(TerminalManager.OUTPUT_LINE_CAP + 50) { append("\nline ").append(it) } }
        val kept = text.substring(TerminalManager.overflowStart(text, TerminalManager.OUTPUT_LINE_CAP))
        assertEquals(TerminalManager.OUTPUT_LINE_CAP, kept.split('\n').size)
        assertEquals("line 50", kept.substringBefore('\n'))
    }
}
