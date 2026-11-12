package ohi.andre.consolelauncher.managers.suggestions

import android.content.Context
import it.andreuzzi.comparestring2.AlgMap.Alg
import it.andreuzzi.comparestring2.CompareObjects
import it.andreuzzi.comparestring2.algs.interfaces.Algorithm
import ohi.andre.consolelauncher.managers.settings.LauncherMode
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.SearchResult
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Suggestion
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceLauncherManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.LinkedHashMap
import java.util.Locale

/** Pure suggestion helpers, reached through [SuggestionsManager]'s companion. */
internal object SuggestionStatics {
    internal fun showSearchResults(inputActive: Boolean, hasQuery: Boolean, hasOutput: Boolean): Boolean =
        inputActive || hasQuery || hasOutput

    private val MEDIA_OPTIONS = arrayOf(
        "-v" to true,
        "-ls" to true,
        "-add" to true,
        "-scan" to true,
        "-rm" to false
    )

    /** `media` offers its options; `media -rm` offers the folders by name, since their numbers mean little. */
    internal fun mediaSuggestions(before: String, normalized: String, typed: String?, folders: List<String>): List<Suggestion> {
        val filter = typed?.trim { it <= ' ' }.orEmpty()
        return if (normalized == "media") {
            MEDIA_OPTIONS.filter { it.first.startsWith(filter, true) }
                .map { (option, exec) -> Suggestion(before, option, exec, Suggestion.TYPE_COMMAND) }
        } else {
            folders.withIndex().filter { it.value.contains(filter, true) }
                .map { (i, label) -> Suggestion(null, label, true, Suggestion.TYPE_PERMANENT, "media -rm ${i + 1}") }
        }
    }

    internal fun termuxLauncherSuggestions(
        launchers: List<TermuxWorkspaceLauncherManager.Launcher>,
        prefix: String? = null
    ): List<Suggestion> = launchers.filter {
        prefix == null || it.id.startsWith(prefix, true) || it.title.startsWith(prefix, true)
    }.map {
        Suggestion(null, it.title, true, Suggestion.TYPE_PERMANENT, "tmux launch ${it.id}")
    }

    internal fun moduleRootOptionMatches(option: String, typed: String?): Boolean {
        val filter = typed?.trim { it <= ' ' }?.lowercase(Locale.getDefault()).orEmpty()
        if (filter.isEmpty()) return true

        val normalizedOption = option.lowercase(Locale.getDefault())
        return normalizedOption.startsWith(filter) ||
            normalizedOption.removePrefix("-").startsWith(filter.removePrefix("-"))
    }

    internal fun normalizedHelpInput(input: String): String? {
        val normalized = input.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        return normalized.takeIf { it == "help" || it.startsWith("help ") }
    }

    internal fun helpTopics(
        input: String,
        resourceText: (Int) -> String = { "" }
    ): List<ohi.andre.consolelauncher.help.HelpTopic> {
        val normalized = normalizedHelpInput(input) ?: return emptyList()
        val query = normalized.substringAfter(' ', "").let {
            if (it == "-topic") "" else it.removePrefix("-topic ")
        }
        val topics = ohi.andre.consolelauncher.help.OfflineHelpCatalog.topics
        if (query.isEmpty()) return topics.filter { it.category == null }
        val terms = query.split(' ')
        fun matches(value: String): Boolean {
            val text = value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
            return terms.all(text::contains)
        }
        return topics.mapNotNull { topic ->
            val direct = topic.id + " " + topic.id.replace('-', ' ') + " " + resourceText(topic.title)
            when {
                matches(direct) -> topic to 0
                matches(direct + " " + resourceText(topic.body)) -> topic to 1
                else -> null
            }
        }.sortedBy { it.second }.map { it.first }
    }

    internal fun helpTopicSuggestions(context: Context, input: String): List<Suggestion> =
        helpTopicSuggestions(input) { context.getString(it) }

    internal fun helpTopicSuggestions(input: String, resourceText: (Int) -> String): List<Suggestion> =
        helpTopics(input, resourceText).map { topic ->
            Suggestion(null, resourceText(topic.title), true, Suggestion.TYPE_PERMANENT, "help -topic ${topic.id}")
        }

    internal fun commandSuggestionExecutes(commandName: String?, argTypes: IntArray?): Boolean {
        // podcast and settings take optional arguments; on their own they open their screen.
        return argTypes == null || argTypes.isEmpty() ||
            "podcast".equals(commandName, ignoreCase = true) || "settings".equals(commandName, ignoreCase = true)
    }

    internal fun searchModeCommandSuggestionExecutes(commandName: String?, argTypes: IntArray?): Boolean {
        if (commandSuggestionExecutes(commandName, argTypes)) return true
        return when (commandName?.lowercase(Locale.ROOT)) {
            "calc", "termux", "tmux" -> true
            else -> false
        }
    }

    internal fun modeTarget(currentMode: String): String = LauncherMode.nextTarget(currentMode)

    internal fun modeTargets(filter: String?): List<String> {
        val normalized = filter?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return LauncherMode.values.filter { normalized.isEmpty() || it.startsWith(normalized) }
    }

    internal fun searchModeSuggestionParts(input: String): Pair<String, String>? {
        val current = input.trimStart()
        val lastSpace = current.lastIndexOf(Tuils.SPACE)
        if (lastSpace < 0) return null
        return current.substring(0, lastSpace) to current.substring(lastSpace + 1)
    }

    internal fun directSearchRank(query: String, value: String): Int? {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) return null
        val haystack = value.lowercase(Locale.getDefault())
        if (haystack == needle) return 0
        if (haystack.startsWith(needle)) return 1
        if (haystack.split(Regex("[\\s._-]+")).any { it.startsWith(needle) }) return 2
        if (haystack.contains(needle)) return 3
        return null
    }

    internal fun providerTitle(name: String): String = when (name.lowercase(Locale.getDefault())) {
        "gg" -> "Google"
        "yt" -> "YouTube"
        "dd" -> "DuckDuckGo"
        "ps" -> "Play Store"
        "mp" -> "Maps"
        "wp" -> "Wikipedia"
        "rd" -> "Reddit"
        "gh" -> "GitHub"
        "tr" -> "Translate"
        "ym" -> "YouTube Music"
        "u" -> "Open URL"
        else -> name.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    internal fun rankSearchResults(
        query: String,
        candidates: List<SearchResult>,
        perCategory: Int,
        deadline: Float,
        algorithm: Algorithm?,
        algorithmType: Alg?
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val unique = LinkedHashMap<String, SearchResult>()
        for (candidate in candidates) {
            val key = candidate.type.toString() + "|" + candidate.title.lowercase(Locale.getDefault()) +
                "|" + candidate.subtitle.lowercase(Locale.getDefault())
            if (!unique.containsKey(key)) unique[key] = candidate
        }
        val categories = unique.values.groupBy { it.type }.toSortedMap()
        val directResults = categories.mapValues { (_, category) ->
            category.mapNotNull { candidate ->
                directSearchRank(query, candidate.getString())?.let { candidate.copy(rank = it) }
            }.sortedWith(
                compareBy<SearchResult> { it.rank }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            ).take(perCategory)
        }
        val ranked = directResults.values.flatten().toMutableList()
        if (ranked.isEmpty() && query.trim().length >= 2) {
            for ((_, category) in categories) {
            val fuzzy = fuzzyMatchesOrNone { CompareObjects.topMatchesWithDeadline<SearchResult?>(
                SearchResult::class.java,
                query,
                    category.size,
                    category.toMutableList(),
                    perCategory,
                deadline,
                arrayOf(" ", "_", "-", "."),
                algorithm,
                algorithmType
            ) }
            fuzzy.filterNotNull().forEachIndexed { index, result ->
                ranked.add(result.copy(rank = 4 + index))
            }
            }
        }
        return ranked.sortedWith(
            compareBy<SearchResult> { it.rank }
                .thenBy { it.type }
                .thenBy { it.title.lowercase(Locale.getDefault()) }
        )
    }
}
