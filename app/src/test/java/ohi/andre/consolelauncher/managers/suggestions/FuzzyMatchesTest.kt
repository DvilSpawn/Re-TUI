package ohi.andre.consolelauncher.managers.suggestions

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FuzzyMatchesTest {
    @Test fun returnsTheMatchesOfASuccessfulSearch() {
        assertArrayEquals(arrayOf("a", "b"), fuzzyMatchesOrNone { arrayOf("a", "b") })
    }

    @Test fun aBrokenSortGivesNoMatches() {
        val matches = fuzzyMatchesOrNone<String?> {
            throw IllegalArgumentException("Comparison method violates its general contract!")
        }
        assertEquals(0, matches.size)
    }

    @Test fun everySuggestionSearchIsGuarded() {
        val call = Regex("""Compare(Objects|Strings)\.topMatchesWithDeadline""")
        listOf("SuggestionsManager.kt", "SuggestionStatics.kt", "SuggestionFileProvider.kt").forEach { name ->
            val source = SourceFiles.kotlin(name).readText()
            val unguarded = call.findAll(source).count {
                !source.substring(0, it.range.first).endsWith("fuzzyMatchesOrNone { ")
            }
            assertEquals("$name has an unguarded fuzzy search", 0, unguarded)
        }
    }
}
