package ohi.andre.consolelauncher.commands

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CommandGroupLocaleTest {
    @Test
    fun reflectionFallbackFindsAsciiCommandsInTurkish() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val group = CommandGroup("ohi.andre.consolelauncher.commands.main.raw")
            assertEquals("time", group.getCommandByName("T-I_M_E")?.javaClass?.simpleName)
        } finally {
            Locale.setDefault(original)
        }
    }
}
