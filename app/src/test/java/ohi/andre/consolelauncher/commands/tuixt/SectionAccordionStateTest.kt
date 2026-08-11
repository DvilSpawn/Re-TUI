package ohi.andre.consolelauncher.commands.tuixt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionAccordionStateTest {
    @Test fun startsClosedSwitchesAndCanCloseActiveSection() {
        val state = SectionAccordionState()
        assertTrue(state.collapsed("One"))
        state.toggle("One")
        assertFalse(state.collapsed("One"))
        state.toggle("Two")
        assertTrue(state.collapsed("One"))
        assertFalse(state.collapsed("Two"))
        state.toggle("Two")
        assertNull(state.active)
    }

    @Test fun searchRevealsAllThenRestoresThePreviousSection() {
        val state = SectionAccordionState("Two")
        state.search(true)
        assertFalse(state.collapsed("One"))
        state.search(false)
        assertTrue(state.collapsed("One"))
        assertFalse(state.collapsed("Two"))
    }
}
