package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.*
import org.junit.Test

class TerminalWidgetCommandTest {
    private val old = "#!/data/data/com.termux/files/usr/bin/bash\n# Bundled terminal widget. Old renderer\necho old"
    private val fresh = "#!/data/data/com.termux/files/usr/bin/bash\n# Bundled terminal widget. Atomic renderer\nprintf '%s' new"
    @Test fun upgradePreservesModeAndQuotedLocation() {
        val command = TerminalWidgetCommand.create(old, "weather", "St. John's; echo untouched")
        assertEquals(TerminalWidgetCommand.create(fresh, "weather", "St. John's; echo untouched"), TerminalWidgetCommand.refreshed(command, fresh))
        assertNull(TerminalWidgetCommand.refreshed(TerminalWidgetCommand.create(fresh, "clock", ""), fresh))
    }
    @Test fun customCommandsAreNeverUpgraded() {
        assertNull(TerminalWidgetCommand.refreshed("bash -c 'echo hello'", fresh))
        assertNull(TerminalWidgetCommand.refreshed("htop", fresh))
    }
}
