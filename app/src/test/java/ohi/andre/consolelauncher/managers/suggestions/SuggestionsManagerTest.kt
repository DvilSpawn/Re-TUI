package ohi.andre.consolelauncher.managers.suggestions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.main.raw.preset

class SuggestionsManagerTest {
    @Test fun offlineHelpFiltersCatalogIdsWithoutChangingCommandRouting() {
        assertEquals(listOf("apps"), SuggestionsManager.helpTopics("help ").filter { it.id == "apps" }.map { it.id })
        assertTrue(SuggestionsManager.helpTopics("help -topic terminal").any { it.id == "terminal-basics" })
        val article = ohi.andre.consolelauncher.help.OfflineHelpCatalog.topics.first { it.category != null }
        assertTrue(SuggestionsManager.helpTopics("help ${article.id}").contains(article))
        assertTrue(SuggestionsManager.helpTopics("help unknown").isEmpty())
        assertFalse(SuggestionsManager.commandSuggestionExecutes("help", intArrayOf(CommandAbstraction.PLAIN_TEXT)))
    }

    @Test fun offlineHelpNormalizesCaseWhitespaceAndTopicPrefix() {
        val expected = SuggestionsManager.helpTopics("help -topic terminal basics")
        assertTrue(expected.any { it.id == "terminal-basics" })
        assertEquals(expected, SuggestionsManager.helpTopics("  HELP\t-TOPIC   TERMINAL\n BASICS  "))
        assertEquals(
            ohi.andre.consolelauncher.help.OfflineHelpCatalog.topics.filter { it.category == null },
            SuggestionsManager.helpTopics(" HELP ")
        )
        assertTrue(SuggestionsManager.helpTopics("files terminal").isEmpty())
        assertTrue(SuggestionsManager.helpTopics("helpful terminal").isEmpty())
    }

    @Test fun offlineHelpSearchesLiveLocalizedTitlesAndBodiesWithDirectMatchesFirst() {
        val topics = ohi.andre.consolelauncher.help.OfflineHelpCatalog.topics
        val bodyOnly = topics.first()
        val direct = topics.last()
        val resources = mapOf(
            bodyOnly.title to "Localized introduction",
            bodyOnly.body to "Lua\nbackup permissions Termux units theme",
            direct.title to "BACKUP   Lua",
            direct.body to "Other content"
        )
        val resolve: (Int) -> String = { resources[it].orEmpty() }
        assertEquals(listOf(direct, bodyOnly), SuggestionsManager.helpTopics("HELP lua\tBACKUP", resolve))
        listOf("permission", "Termux", "units", "theme").forEach { query ->
            assertTrue(SuggestionsManager.helpTopics("help $query", resolve).contains(bodyOnly))
        }
        assertEquals(listOf(bodyOnly), SuggestionsManager.helpTopics("help localized introduction", resolve))
        assertTrue(SuggestionsManager.helpTopics("help no-such-help-content", resolve).isEmpty())
    }

    @Test fun offlineHelpTopicLabelsDispatchCanonicalPayloadAndExecuteOnlyOnClick() {
        val roots = ohi.andre.consolelauncher.help.OfflineHelpCatalog.topics.filter { it.category == null }
        val suggestions = SuggestionsManager.helpTopicSuggestions("HELP") { "Localized title $it" }
        assertEquals(roots.size, suggestions.size)
        roots.zip(suggestions).forEach { (topic, suggestion) ->
            assertEquals("Localized title ${topic.title}", suggestion.text)
            assertEquals("help -topic ${topic.id}", suggestion.getText())
            assertEquals(suggestion.getText(), suggestion.`object`)
            assertEquals(SuggestionsManager.Suggestion.TYPE_PERMANENT, suggestion.type)
            assertTrue(suggestion.exec)
        }
        assertFalse(SuggestionsManager.commandSuggestionExecutes("help", intArrayOf(CommandAbstraction.PLAIN_TEXT)))
        assertFalse(SuggestionsManager.searchModeCommandSuggestionExecutes("help", intArrayOf(CommandAbstraction.PLAIN_TEXT)))
    }

    @Test fun offlineHelpRoutingRecognizesOnlyHelpAndNormalizesSearchInput() {
        assertEquals("help -topic lua backup", SuggestionsManager.normalizedHelpInput(" HELP\t-TOPIC Lua\n BACKUP "))
        assertEquals(null, SuggestionsManager.normalizedHelpInput("files help"))
        assertEquals(null, SuggestionsManager.normalizedHelpInput("helpful"))
        assertTrue(SuggestionsManager.helpTopicSuggestions("help no-such-help-content") { "" }.isEmpty())
    }

    @Test fun offlineHelpNormalizationDoesNotDependOnDeviceLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals("help -topic units", SuggestionsManager.normalizedHelpInput("HELP -TOPIC UNITS"))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test fun permanentSuggestionsStillUseLabelsWithoutReplacementPayload() {
        val suggestion = SuggestionsManager.Suggestion(null, "lua -apps", true,
            SuggestionsManager.Suggestion.TYPE_PERMANENT)
        assertEquals("lua -apps", suggestion.getText())
        suggestion.`object` = 3
        assertEquals("lua -apps", suggestion.getText())
    }

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

    @Test
    fun presetSuggestsMarketplaceRoute() {
        assertTrue(preset().params().contains("-market"))
        assertEquals("https://re-tui.pages.dev/marketplace", preset.MARKETPLACE_URL)
    }
}
