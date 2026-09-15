package ohi.andre.consolelauncher.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionFormatTest {
    private var sequence = 0
    private fun encrypt(value: String) = RedactionFormat.encoded(value.length, "payload${sequence++}")

    @Test
    fun automaticRedactionKeepsTitleMundaneWordsAndExistingTokens() {
        val existing = RedactionFormat.encoded(8, "existing")
        val result = RedactionFormat.automatic(
            "# Redaction Test\n\nAlice carried the launch code to Hangar 7.\n$existing",
            ::encrypt
        )

        assertTrue(result.startsWith("# Redaction Test"))
        assertTrue(result.contains("the"))
        assertTrue(result.contains("to"))
        assertTrue(result.contains(existing))
        assertFalse(result.contains("Alice"))
        assertFalse(result.contains("launch"))
        assertTrue(RedactionFormat.contains(result))
        assertTrue(RedactionFormat.hide(result).contains("[REDACTED]"))
    }

    @Test
    fun manualRedactionPreservesLineBreaksAndBlankLines() {
        assertEquals(
            "{{remember-redact:v1:5:payload0}}\n\n{{remember-redact:v1:5:payload1}}",
            RedactionFormat.manual("alpha\n\nomega", ::encrypt)
        )
    }
}
