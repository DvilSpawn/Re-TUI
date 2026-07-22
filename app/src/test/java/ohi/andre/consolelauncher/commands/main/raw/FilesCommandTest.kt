package ohi.andre.consolelauncher.commands.main.raw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilesCommandTest {
    @Test
    fun buildsExplicitSearchHandoff() {
        assertNull(files.parseSearch(""))
        assertEquals(files.SearchHandoff("pan", null), files.parseSearch("pan"))
        assertEquals(files.SearchHandoff("pan", ".png"), files.parseSearch("pan .png"))
        assertEquals(files.SearchHandoff("*", ".png"), files.parseSearch("* .png"))
    }
}
