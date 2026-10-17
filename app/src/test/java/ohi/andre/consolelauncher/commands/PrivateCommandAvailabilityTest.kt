package ohi.andre.consolelauncher.commands

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import org.junit.Assert.*
import org.junit.Test

class PrivateCommandAvailabilityTest {
    @Test fun privateCommandIsOptInAndDiscoveryTracksTheSavedPreference() {
        val setting = Behavior.enable_private_command
        assertEquals("false", setting.defaultValue())
        val values = XMLPrefsManager.XMLPrefsRoot.BEHAVIOR.getValues()!!
        val original = values[setting]
        try {
            values.add(setting.label(), "false")
            assertTrue(CommandTuils.isHiddenCommandName("private"))
            assertTrue(CommandTuils.isHiddenCommandName("PRIVATE"))
            assertFalse(CommandTuils.isHiddenCommandName("apps"))
            values.add(setting.label(), "true")
            assertFalse(CommandTuils.isHiddenCommandName("private"))
            values.add(setting.label(), "false")
            assertTrue(CommandTuils.isHiddenCommandName("private"))
        } finally {
            if (original == null) values.list.removeAll { it.key == setting.label() }
            else values.add(original)
        }
    }
}
