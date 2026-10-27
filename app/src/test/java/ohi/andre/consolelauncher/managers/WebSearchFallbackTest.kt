package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchFallbackTest {
    private val executables = setOf("ls", "find", "cat", "ping")
    private fun search(input: String, shellWithoutPrefix: Boolean = false) =
        WebSearchFallback.looksLikeSearch(input, shellWithoutPrefix) { it in executables }

    @Test fun phrasesAreOfferedAsSearches() {
        assertTrue(search("how tall is everest"))
        assertTrue(search("nearest restaurant"))
        assertTrue(search("what's the weather tomorrow?"))
    }

    @Test fun singleWordsAndShellSyntaxAreNot() {
        assertFalse(search("everest"))
        assertFalse(search("ls | grep foo"))
        assertFalse(search("echo hi > out.txt"))
        assertFalse(search("cd /sdcard"))
    }

    @Test fun shellCommandsStayShellCommandsWhenNoPrefixIsNeeded() {
        assertFalse(search("find nearest cafe", shellWithoutPrefix = true))
        assertFalse(search("echo hello world", shellWithoutPrefix = true))
        assertTrue(search("nearest cafe", shellWithoutPrefix = true))
        // With the shell behind a prefix, nothing typed here can run as a shell command.
        assertTrue(search("find nearest cafe", shellWithoutPrefix = false))
    }

    @Test fun secondEnterConfirmsAndOtherInputCancels() {
        assertEquals(WebSearchFallback.Step.ASK, WebSearchFallback.next("how tall is everest", now = 1_000))
        assertEquals("how tall is everest", WebSearchFallback.pending)
        assertEquals(WebSearchFallback.Step.SEARCH, WebSearchFallback.next(" how tall is everest ", now = 2_000))
        assertNull(WebSearchFallback.pending)

        assertEquals(WebSearchFallback.Step.ASK, WebSearchFallback.next("nearest cafe", now = 3_000))
        WebSearchFallback.cancelUnless("apps -ls")
        assertNull(WebSearchFallback.pending)
        assertEquals(WebSearchFallback.Step.ASK, WebSearchFallback.next("nearest cafe", now = 4_000))
        WebSearchFallback.cancelUnless("nearest cafe")
        assertEquals(WebSearchFallback.Step.SEARCH, WebSearchFallback.next("nearest cafe", now = 5_000))
    }

    @Test fun anImmediateRepeatDoesNotConfirm() {
        assertEquals(WebSearchFallback.Step.ASK, WebSearchFallback.next("weather tomorrow", now = 10_000))
        assertEquals(WebSearchFallback.Step.IGNORE, WebSearchFallback.next("weather tomorrow", now = 10_100))
        assertEquals(WebSearchFallback.Step.SEARCH, WebSearchFallback.next("weather tomorrow", now = 11_000))
    }
}
