package ohi.andre.consolelauncher.managers.suggestions

import ohi.andre.consolelauncher.managers.xml.options.Behavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchModeTest {
    @Test
    fun searchModeIsOptIn() {
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
    fun modeOnlyOffersTheOtherLayout() {
        assertEquals("search", SuggestionsManager.modeTarget(false))
        assertEquals("classic", SuggestionsManager.modeTarget(true))
    }

    private fun result(title: String, type: Int) = SuggestionsManager.SearchResult(
        title,
        "SOURCE",
        type,
        null,
        title
    )
}
