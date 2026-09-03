package ohi.andre.consolelauncher.managers.suggestions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import ohi.andre.consolelauncher.commands.CommandAbstraction

class SuggestionsManagerTest {
    @Test
    fun moduleRootOptionMatchesDashlessFilter() {
        assertTrue(SuggestionsManager.moduleRootOptionMatches("-dock", "dock"))
        assertTrue(SuggestionsManager.moduleRootOptionMatches("-refresh", "ref"))
        assertTrue(SuggestionsManager.moduleRootOptionMatches("-refresh", "-r"))
        assertTrue(SuggestionsManager.moduleRootOptionMatches("-rm", "-r"))
        assertFalse(SuggestionsManager.moduleRootOptionMatches("-show", "dock"))
    }

    @Test
    fun paneRootSuggestionsExecuteFromSearchDespitePlainTextArgs() {
        listOf("calc", "podcast", "termux", "tmux").forEach { command ->
            assertTrue(
                SuggestionsManager.searchModeCommandSuggestionExecutes(
                    command,
                    intArrayOf(CommandAbstraction.PLAIN_TEXT)
                )
            )
        }
        assertFalse(
            SuggestionsManager.commandSuggestionExecutes(
                "calc",
                intArrayOf(CommandAbstraction.PLAIN_TEXT)
            )
        )
        assertFalse(
            SuggestionsManager.searchModeCommandSuggestionExecutes(
                "shell",
                intArrayOf(CommandAbstraction.PLAIN_TEXT)
            )
        )
    }

    @Test
    fun intentSuggestionsUseReadableLabelsAndDispatchCommands() {
        assertEquals(
            listOf("intent -view", "intent -activity", "intent -broadcast", "intent -uri"),
            SuggestionsManager.INTENT_ROOT_ACTIONS.map { it.second }
        )
        assertEquals(
            listOf("Action (-a)", "Data URI (-d)", "MIME type (-t)", "Package (-p)", "Component (-n)"),
            SuggestionsManager.INTENT_PARAMETERS.map { it.second }
        )
        assertFalse(SuggestionsManager.INTENT_ROOT_ACTIONS.any { it.second.contains("check") })
    }

    @Test
    fun multiWordContactSuggestionReplacesPartialName() {
        assertEquals("call", SuggestionsManager.Suggestion.contactCommandPrefix("call mako", "Mako Harish"))
        assertEquals("cntcts -rm", SuggestionsManager.Suggestion.contactCommandPrefix("cntcts -rm mako", "Mako Harish"))
    }
}
