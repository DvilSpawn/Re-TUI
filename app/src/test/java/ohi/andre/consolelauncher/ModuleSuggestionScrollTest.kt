package ohi.andre.consolelauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleSuggestionScrollTest {
    @Test
    fun preservesOnlyTheCurrentModulesScrollPosition() {
        assertEquals(180, moduleSuggestionScrollTarget("clock", "clock", 180, 0))
        assertEquals(180, moduleSuggestionScrollTarget("clock", "clock", 0, 180))
        assertEquals(0, moduleSuggestionScrollTarget("weather", "clock", 180, 180))
    }
}
