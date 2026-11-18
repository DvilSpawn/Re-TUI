package ohi.andre.consolelauncher.commands.main

import ohi.andre.consolelauncher.commands.main.raw.rejectedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ConfigRejectedValueTest {
    @Test fun acceptedValueHasNoMessage() {
        assertNull(rejectedValue { })
    }

    @Test fun rejectedValueAnswersWithTheSettingsMessage() {
        val message = "edge_sidebar_pill_color: Enter auto, #RRGGBB, or #AARRGGBB."
        assertEquals(message, rejectedValue { require(false) { message } })
    }

    @Test fun otherFailuresStillPropagate() {
        try {
            rejectedValue { throw IllegalStateException("bug") }
            fail("A crash was swallowed")
        } catch (expected: IllegalStateException) { }
    }
}
