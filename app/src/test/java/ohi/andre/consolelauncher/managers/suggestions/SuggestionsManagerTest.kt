package ohi.andre.consolelauncher.managers.suggestions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsManagerTest {
    @Test
    fun moduleRootOptionMatchesDashlessFilter() {
        assertTrue(SuggestionsManager.moduleRootOptionMatches("-dock", "dock"))
        assertTrue(SuggestionsManager.moduleRootOptionMatches("-refresh", "ref"))
        assertTrue(SuggestionsManager.moduleRootOptionMatches("-refresh", "-r"))
        assertTrue(SuggestionsManager.moduleRootOptionMatches("-rm", "-r"))
        assertFalse(SuggestionsManager.moduleRootOptionMatches("-show", "dock"))
    }
}
