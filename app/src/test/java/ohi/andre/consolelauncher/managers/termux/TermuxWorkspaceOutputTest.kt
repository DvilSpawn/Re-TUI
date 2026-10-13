package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.*
import org.junit.Test

class TermuxWorkspaceOutputTest {
    @Test fun allBytesAndSplitUtf8Survive() {
        val escaped = (0..255).joinToString("") { "\\" + it.toString(8).padStart(3, '0') }
        assertArrayEquals(ByteArray(256) { it.toByte() }, TermuxWorkspaceOutput.decode(escaped))
        val first = TermuxWorkspaceOutput.decode("\u00ce")
        val second = TermuxWorkspaceOutput.decode("\u00bb")
        assertEquals("λ", (first + second).toString(Charsets.UTF_8))
    }
    @Test fun malformedAndOversizedOutputIsRejected() {
        for (value in listOf("\\", "\\12", "\\xyz", "\\777", "x".repeat(65537))) {
            try { TermuxWorkspaceOutput.decode(value); fail("Accepted malformed output") }
            catch (expected: IllegalArgumentException) {}
        }
    }
}
