package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchProviderManagerTest {
    @Test fun pinnedAppRoundTrips() {
        val line = "gg=@com.google.android.googlequicksearchbox websearch:{raw} => https://www.google.com/search?q={query}"
        val provider = SearchProviderManager.parse(line)!!

        assertEquals("gg", provider.name)
        assertEquals("com.google.android.googlequicksearchbox", provider.app)
        assertEquals("websearch:{raw}", provider.template)
        assertEquals("https://www.google.com/search?q={query}", provider.fallbackTemplate)
        assertEquals(line, SearchProviderManager.serialize(provider))
    }

    @Test fun legacyLinesStillParseWithoutAnApp() {
        val provider = SearchProviderManager.parse("ps=market://search?q={query} => https://play.google.com/store/search?q={query}&c=apps")!!

        assertNull(provider.app)
        assertEquals("market://search?q={query}", provider.template)
    }

    @Test fun browserPinAndInvalidPackagesAreHandled() {
        assertEquals("browser", SearchProviderManager.parse("dd=@browser https://duckduckgo.com/?q={query}")!!.app)
        assertNull(SearchProviderManager.parse("dd=@not/a/package https://duckduckgo.com/?q={query}"))
        assertNull(SearchProviderManager.parse("dd=@com.example.app"))
    }

    @Test fun untouchedGoogleMovesToTheGoogleAppAndNewDefaultsAreAdded() {
        val legacy = mutableListOf(
            SearchProviderManager.Provider("gg", "https://www.google.com/search?q={query}", null),
            SearchProviderManager.Provider("yt", "https://www.youtube.com/results?search_query={query+}", null)
        )

        val upgraded = SearchProviderManager.upgradeDefaults(legacy)
        val google = upgraded.first { it.name == "gg" }

        assertEquals("com.google.android.googlequicksearchbox", google.app)
        assertEquals("https://www.google.com/search?q={query}", google.fallbackTemplate)
        assertEquals(listOf("gg", "yt", "mp", "wp", "rd", "gh", "tr", "ym"), upgraded.map { it.name })
    }

    @Test fun customisedProvidersSurviveTheUpgrade() {
        val custom = mutableListOf(
            SearchProviderManager.Provider("gg", "https://www.google.co.uk/search?q={query}", null),
            SearchProviderManager.Provider("mp", "https://www.openstreetmap.org/search?query={query}", null)
        )

        val upgraded = SearchProviderManager.upgradeDefaults(custom)

        assertEquals("https://www.google.co.uk/search?q={query}", upgraded.first { it.name == "gg" }.template)
        assertNull(upgraded.first { it.name == "gg" }.app)
        assertEquals("https://www.openstreetmap.org/search?query={query}", upgraded.first { it.name == "mp" }.template)
        assertEquals(1, upgraded.count { it.name == "mp" })
    }

    @Test fun googleStaysFirstInTheDefaults() {
        val defaults = SearchProviderManager.defaultProviders()

        assertEquals("gg", defaults.first().name)
        assertEquals(
            listOf("gg", "ps", "yt", "dd", "u", "mp", "wp", "rd", "gh", "tr", "ym"),
            defaults.map { it.name }
        )
        defaults.forEach { assertEquals(it.name, SearchProviderManager.parse(SearchProviderManager.serialize(it))!!.name) }
    }
}
