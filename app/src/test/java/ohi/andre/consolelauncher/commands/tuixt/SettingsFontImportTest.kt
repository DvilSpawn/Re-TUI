package ohi.andre.consolelauncher.commands.tuixt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SettingsFontImportTest {
    @Test fun acceptsAFontAtTheLimit() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val output = ByteArrayOutputStream()
        ThemerActivity.copyFontStream(ByteArrayInputStream(bytes), output, 4)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test fun rejectsOversizedFontBeforeWritingPastTheLimit() {
        val output = ByteArrayOutputStream()
        var rejected = false
        try {
            ThemerActivity.copyFontStream(ByteArrayInputStream(ByteArray(9)), output, 8)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertTrue(output.size() <= 8)
    }
}
