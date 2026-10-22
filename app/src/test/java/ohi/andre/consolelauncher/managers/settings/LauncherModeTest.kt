package ohi.andre.consolelauncher.managers.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherModeTest {
    @Test
    fun supportedModesIncludeTerminal() {
        assertEquals(listOf("classic", "search", "terminal"), LauncherMode.values)
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
}
