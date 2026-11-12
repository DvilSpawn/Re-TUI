package ohi.andre.consolelauncher.managers.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherModeTest {
    @Test
    fun supportedModesIncludeTerminal() {
        assertEquals(listOf("classic", "search", "terminal", "dot", "mono"), LauncherMode.values)
    }

    @Test
    fun resolvesDefaultToClassic() {
        assertEquals(LauncherMode.CLASSIC, LauncherMode.resolve("", false))
        assertEquals(LauncherMode.CLASSIC, LauncherMode.resolve(null, false))
    }

    @Test
    fun resolvesLegacySearchOnlyWhenModeIsEmpty() {
        assertEquals(LauncherMode.SEARCH, LauncherMode.resolve("", true))
        assertEquals(LauncherMode.SEARCH, LauncherMode.resolve(null, true))
    }

    @Test
    fun explicitClassicOverridesLegacySearchOnly() {
        assertEquals(LauncherMode.CLASSIC, LauncherMode.resolve("classic", true))
    }

    @Test
    fun invalidModeFallsBackToClassic() {
        assertEquals(LauncherMode.CLASSIC, LauncherMode.resolve("desktop", true))
        assertNull(LauncherMode.normalize("desktop"))
    }

    @Test
    fun nextTargetReturnsSearchOnlyFromClassic() {
        assertEquals(LauncherMode.SEARCH, LauncherMode.nextTarget(LauncherMode.CLASSIC))
        assertEquals(LauncherMode.CLASSIC, LauncherMode.nextTarget(LauncherMode.SEARCH))
    }

    /** What every mode answers to every predicate. A mode added to [LauncherMode.values] must get a row here. */
    private data class Row(
        val search: Boolean,
        val usesSearch: Boolean,
        val terminal: Boolean,
        val dot: Boolean,
        val mono: Boolean,
        val managesOwnKeyboard: Boolean
    )

    private val table = mapOf(
        LauncherMode.CLASSIC to Row(search = false, usesSearch = false, terminal = false, dot = false, mono = false, managesOwnKeyboard = false),
        LauncherMode.SEARCH to Row(search = true, usesSearch = true, terminal = false, dot = false, mono = false, managesOwnKeyboard = false),
        LauncherMode.TERMINAL to Row(search = false, usesSearch = true, terminal = true, dot = false, mono = false, managesOwnKeyboard = true),
        LauncherMode.DOT to Row(search = false, usesSearch = true, terminal = false, dot = true, mono = false, managesOwnKeyboard = true),
        LauncherMode.MONO to Row(search = false, usesSearch = false, terminal = false, dot = false, mono = true, managesOwnKeyboard = true)
    )

    @Test
    fun everyModeHasARowInThePredicateTable() {
        assertEquals("Fill in the table for each LauncherMode value", LauncherMode.values.toSet(), table.keys)
    }

    @Test
    fun predicatesMatchTheTableForEveryMode() {
        for (mode in LauncherMode.values) {
            val row = table.getValue(mode)
            assertEquals("$mode isSearch", row.search, LauncherMode.isSearch(mode))
            assertEquals("$mode usesSearch", row.usesSearch, LauncherMode.usesSearch(mode))
            assertEquals("$mode isTerminal", row.terminal, LauncherMode.isTerminal(mode))
            assertEquals("$mode isDot", row.dot, LauncherMode.isDot(mode))
            assertEquals("$mode isMono", row.mono, LauncherMode.isMono(mode))
            assertEquals("$mode managesOwnKeyboard", row.managesOwnKeyboard, LauncherMode.managesOwnKeyboard(mode))
        }
    }

    @Test
    fun onlyHomesWithTheirOwnKeyboardGestureManageTheKeyboard() {
        assertEquals(
            setOf(LauncherMode.TERMINAL, LauncherMode.DOT, LauncherMode.MONO),
            LauncherMode.values.filter { LauncherMode.managesOwnKeyboard(it) }.toSet()
        )
    }
}
