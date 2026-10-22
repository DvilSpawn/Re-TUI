package ohi.andre.consolelauncher.managers.suggestions

import ohi.andre.consolelauncher.managers.settings.LauncherMode
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchModeTest {
    @Test
    fun searchLayoutCanShowSelectedCategoryOrAllRows() {
        val groups = SearchModeResultRenderer.groupResults(listOf(
            result("App", SuggestionsManager.SearchResult.TYPE_APP),
            result("Command", SuggestionsManager.SearchResult.TYPE_COMMAND)
        ))
        assertEquals("true", Behavior.search_category_tabs.defaultValue())
        assertEquals(listOf(groups[1]), SearchModeResultRenderer.visibleGroups(groups, SuggestionsManager.SearchResult.TYPE_COMMAND, true))
        assertEquals(groups, SearchModeResultRenderer.visibleGroups(groups, SuggestionsManager.SearchResult.TYPE_COMMAND, false))
        assertEquals(emptyList<Any>(), SearchModeResultRenderer.visibleGroups(emptyList(), SuggestionsManager.SearchResult.TYPE_APP, false))
    }

    @Test
    fun emptyDefaultsRequireActiveInputButQueriesAndOutputRemainVisible() {
        assertEquals(false, SuggestionsManager.showSearchResults(false, false, false))
        assertEquals(true, SuggestionsManager.showSearchResults(true, false, false))
        assertEquals(true, SuggestionsManager.showSearchResults(false, true, false))
        assertEquals(true, SuggestionsManager.showSearchResults(false, false, true))
    }

    @Test
    fun launcherModeDefaultsToCompatibilityMode() {
        assertEquals("", Behavior.launcher_mode.defaultValue())
        assertEquals("false", Behavior.search_only_mode.defaultValue())
    }

    @Test
    fun directRankingPrefersExactThenPrefixThenWordsThenContains() {
        assertEquals(0, SuggestionsManager.directSearchRank("maps", "Maps"))
        assertEquals(1, SuggestionsManager.directSearchRank("map", "Maps"))
        assertEquals(2, SuggestionsManager.directSearchRank("set", "Behavior settings"))
        assertEquals(3, SuggestionsManager.directSearchRank("loud", "Weather · partly cloudy"))
        assertNull(SuggestionsManager.directSearchRank("clock", "Contacts"))
    }

    @Test
    fun mixedResultsDeduplicateAndUseSourceOrderForTies() {
        val candidates = listOf(
            result("Settings", SuggestionsManager.SearchResult.TYPE_NOTIFICATION),
            result("Settings", SuggestionsManager.SearchResult.TYPE_APP),
            result("Settings", SuggestionsManager.SearchResult.TYPE_APP)
        )

        val ranked = SuggestionsManager.rankSearchResults(
            "settings",
            candidates,
            4,
            0f,
            null,
            null
        )

        assertEquals(2, ranked.size)
        assertEquals(SuggestionsManager.SearchResult.TYPE_APP, ranked[0].type)
        assertEquals(SuggestionsManager.SearchResult.TYPE_NOTIFICATION, ranked[1].type)
    }

    @Test
    fun directMatchesDoNotPadUnrelatedCategoriesWithFuzzyResults() {
        val ranked = SuggestionsManager.rankSearchResults(
            "google",
            listOf(
                result("Google", SuggestionsManager.SearchResult.TYPE_APP),
                result("Moon Goddess", SuggestionsManager.SearchResult.TYPE_CONTACT),
                result("Auto show keyboard", SuggestionsManager.SearchResult.TYPE_COMMAND)
            ),
            4,
            0f,
            null,
            null
        )

        assertEquals(listOf("Google"), ranked.map { it.title })
    }

    @Test
    fun resultRowsUseLauncherCategoryOrderAndKeepRankedItemsTogether() {
        val grouped = SearchModeResultRenderer.groupResults(
            listOf(
                result("Run", SuggestionsManager.SearchResult.TYPE_COMMAND),
                result("Google", SuggestionsManager.SearchResult.TYPE_PROVIDER),
                result("Reddit", SuggestionsManager.SearchResult.TYPE_APP),
                result("Google", SuggestionsManager.SearchResult.TYPE_APP),
                result("Dvil", SuggestionsManager.SearchResult.TYPE_CONTACT)
            )
        )

        assertEquals(
            listOf(
                SuggestionsManager.SearchResult.TYPE_APP,
                SuggestionsManager.SearchResult.TYPE_CONTACT,
                SuggestionsManager.SearchResult.TYPE_COMMAND,
                SuggestionsManager.SearchResult.TYPE_PROVIDER
            ),
            grouped.map { it.first().type }
        )
        assertEquals(listOf("Reddit", "Google"), grouped.first().map { it.title })
    }

    @Test
    fun emptySearchDefaultsUseTabbedCategoryOrder() {
        val grouped = SearchModeResultRenderer.groupResults(
            listOf(
                result("alias", SuggestionsManager.SearchResult.TYPE_ALIAS),
                result("command", SuggestionsManager.SearchResult.TYPE_COMMAND),
                result("group", SuggestionsManager.SearchResult.TYPE_GROUP),
                result("app", SuggestionsManager.SearchResult.TYPE_APP)
            )
        )

        assertEquals(
            listOf(
                SuggestionsManager.SearchResult.TYPE_APP,
                SuggestionsManager.SearchResult.TYPE_ALIAS,
                SuggestionsManager.SearchResult.TYPE_COMMAND,
                SuggestionsManager.SearchResult.TYPE_GROUP
            ),
            grouped.map { it.first().type }
        )
    }

    @Test
    fun categoryTabsKeepPreviousSelectionWhenStillAvailable() {
        val grouped = SearchModeResultRenderer.groupResults(
            listOf(
                result("Reddit", SuggestionsManager.SearchResult.TYPE_APP),
                result("Dvil", SuggestionsManager.SearchResult.TYPE_CONTACT),
                result("Run", SuggestionsManager.SearchResult.TYPE_COMMAND)
            )
        )

        assertEquals(
            SuggestionsManager.SearchResult.TYPE_CONTACT,
            SearchModeResultRenderer.selectedCategoryType(SuggestionsManager.SearchResult.TYPE_CONTACT, grouped)
        )
        assertEquals(
            SuggestionsManager.SearchResult.TYPE_APP,
            SearchModeResultRenderer.selectedCategoryType(SuggestionsManager.SearchResult.TYPE_PROVIDER, grouped)
        )

        val emptyDefaultGroups = SearchModeResultRenderer.groupResults(
            listOf(
                result("app", SuggestionsManager.SearchResult.TYPE_APP),
                result("alias", SuggestionsManager.SearchResult.TYPE_ALIAS),
                result("command", SuggestionsManager.SearchResult.TYPE_COMMAND),
                result("group", SuggestionsManager.SearchResult.TYPE_GROUP)
            )
        )
        assertEquals(
            SuggestionsManager.SearchResult.TYPE_ALIAS,
            SearchModeResultRenderer.selectedCategoryType(SuggestionsManager.SearchResult.TYPE_ALIAS, emptyDefaultGroups)
        )
    }

    @Test
    fun webProviderLabelsUseReadableNames() {
        assertEquals("Google", SuggestionsManager.providerTitle("gg"))
        assertEquals("YouTube", SuggestionsManager.providerTitle("yt"))
        assertEquals("My wiki", SuggestionsManager.providerTitle("my_wiki"))
    }

    @Test
    fun commandSelectionsKeepTheirParameterContext() {
        assertEquals("preset" to "", SuggestionsManager.searchModeSuggestionParts("preset "))
        assertEquals("preset" to "-ap", SuggestionsManager.searchModeSuggestionParts("preset -ap"))
        assertEquals("preset -apply" to "", SuggestionsManager.searchModeSuggestionParts("preset -apply "))
        assertNull(SuggestionsManager.searchModeSuggestionParts("preset"))
    }

    @Test
    fun modeOnlyOffersTheNextSensibleLayout() {
        assertEquals("search", SuggestionsManager.modeTarget(LauncherMode.CLASSIC))
        assertEquals("classic", SuggestionsManager.modeTarget(LauncherMode.SEARCH))
    }

    @Test
    fun modeParameterSuggestionsIncludeEveryLauncherMode() {
        assertEquals(listOf("classic", "search", "terminal"), SuggestionsManager.modeTargets(""))
        assertEquals(listOf("terminal"), SuggestionsManager.modeTargets("ter"))
        assertEquals(emptyList<String>(), SuggestionsManager.modeTargets("f"))
    }

    private fun result(title: String, type: Int) = SuggestionsManager.SearchResult(
        title,
        "SOURCE",
        type,
        null,
        title
    )
}
