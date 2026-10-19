package ohi.andre.consolelauncher.tuils

import org.junit.Assert.*
import org.junit.Test

class AlphabetHeadersTest {
    @Test fun headingsContinueThroughTheEntireAlphabet() {
        val names = ('A'..'Z').map { "  ${it}pp" }
        val lines = names.map { it as String? }.toMutableList()
        Tuils.insertHeaders(lines, false)
        assertEquals(('A'..'Z').flatMap { listOf(it.toString(), "  ${it}pp") }, lines)
    }
    @Test fun duplicateInitialsAndBlankLabelsDoNotLoseEntriesOrCrash() {
        val lines = mutableListOf<String?>("", "  ", null, " alpha", "Another", "beta", "Zulu")
        Tuils.insertHeaders(lines, true)
        assertEquals(listOf("", "  ", null, "\nA\n", " alpha", "Another", "\nB\n", "beta", "\nZ\n", "Zulu"), lines)
    }
}
