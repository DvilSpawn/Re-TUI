package ohi.andre.consolelauncher.commands.tuixt

import java.util.Locale

/**
 * Search across every settings page at once. Entries are settings (name, plain description, page and section) and
 * hub destinations (Fonts, ASCII settings, Backup…); every word of the query must appear somewhere in an entry, and
 * hits in the name rank above hits in the description.
 */
internal object SettingsSearch {
    class Entry(
        val title: String,
        /** Where it lives, e.g. "Behavior › behavior.xml › Weather". */
        val place: String,
        val description: String,
        /** Extra words that should find it, such as the raw setting name. */
        val keywords: String,
        val target: Any
    ) {
        // Normalised once, so a keystroke only compares strings.
        internal val name = normalize("$title $keywords")
        internal val rest = normalize("$place $description")
        internal val nameWords = name.split(' ')
        internal val restWords = rest.split(' ')
    }

    private const val LIMIT = 80

    fun search(entries: List<Entry>, query: String): List<Entry> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        val phrase = words.joinToString(" ")
        return entries.mapNotNull { entry ->
            var score = 0
            for (word in words) {
                score += when {
                    entry.nameWords.any { it.startsWith(word) } -> 4
                    entry.name.contains(word) -> 3
                    entry.restWords.any { it.startsWith(word) } -> 2
                    entry.rest.contains(word) -> 1
                    else -> return@mapNotNull null
                }
            }
            // The whole phrase in the name, like "time format", beats scattered words.
            if (words.size > 1 && entry.name.contains(phrase)) score += 3
            entry to score
        }.sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.title.length })
            .take(LIMIT).map { it.first }
    }

    // Setting names are snake_case; "status_time_format" should answer "time format" and "status time".
    private val SYMBOLS = Regex("[^\\p{L}\\p{N} ]")
    private val SPACES = Regex("\\s+")

    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace('_', ' ').replace('-', ' ').replace(SYMBOLS, " ").replace(SPACES, " ").trim()

    private fun words(query: String) = normalize(query).split(' ').filter { it.isNotEmpty() }
}
