package ohi.andre.consolelauncher.managers.suggestions

import ohi.andre.consolelauncher.R
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.presentation.SearchResultPresentation

class SearchModeResultRenderer(
    private val pack: MainPack,
    private val container: LinearLayout,
    private val onClick: (SuggestionsManager.SearchResult) -> Unit
) {
    private val context = pack.context
    private val chipStyler = SuggestionChipStyler(pack)
    private val appearance=HomeAppearance.saved()
    private var rowState = emptyMap<Int, RowState>()
    private var latestResults = emptyList<SuggestionsManager.SearchResult>()
    private var activeCategoryType: Int? = null
    private var categoryStrip: HorizontalScrollView? = null
    private var categoryScrollX: Int = 0

    fun clear() {
        rowState = emptyMap()
        latestResults = emptyList()
        activeCategoryType = null
        categoryStrip = null
        categoryScrollX = 0
        container.removeAllViews()
    }

    fun render(results: List<SuggestionsManager.SearchResult>) {
        val previous = currentRowState()
        categoryScrollX = categoryStrip?.scrollX ?: categoryScrollX
        latestResults = results
        rowState = previous
        container.removeAllViews()
        val groups = groupResults(results)
        if (groups.isEmpty()) return

        val card = resultCard()
        val selectedType = selectedCategoryType(activeCategoryType, groups)
        activeCategoryType = selectedType
        val tabbed = XMLPrefsManager.getBoolean(Behavior.search_category_tabs)
        val visibleGroups = visibleGroups(groups, selectedType, tabbed)
        val nextState = previous.toMutableMap()
        for (group in visibleGroups) {
            val type = group.first().type
            if (!tabbed) card.addView(categoryHeader(categoryLabel(type), type))
            val signature = group.signature()
            val strip = resultStrip(group)
            val prior = previous[type]
            strip.post {
                if (prior?.signature == signature) strip.scrollTo(prior.scrollX, 0)
                else strip.fullScroll(View.FOCUS_LEFT)
            }
            nextState[type] = RowState(signature, 0, strip)
            card.addView(strip)
        }
        rowState = nextState
        categoryStrip = null
        if (tabbed) {
            val tabs = categoryTabs(groups, selectedType)
            categoryStrip = tabs
            tabs.post { tabs.scrollTo(categoryScrollX, 0) }
            card.addView(tabs)
        }
        container.addView(card, cardLayoutParams())
    }

    fun renderOutput(output: CharSequence) {
        clear()
        val resultCard = resultCard()
        resultCard.addView(categoryHeader(context.getString(R.string.search_category_output), SuggestionsManager.SearchResult.TYPE_COMMAND))
        val card = SearchResultPresentation.output(context,output.toString().trim(),appearance)
        card.setTextIsSelectable(true)
        resultCard.addView(card)
        container.addView(resultCard, cardLayoutParams())
    }

    private fun resultStrip(results: List<SuggestionsManager.SearchResult>) =
        SearchResultPresentation.strip(context,false).apply {
            results.forEach { SearchResultPresentation.addChip(this,resultChip(it)) }
        }

    private fun categoryTabs(groups: List<List<SuggestionsManager.SearchResult>>,selectedType: Int) =
        SearchResultPresentation.strip(context,true).apply {
            groups.forEach { group ->
                val type=group.first().type
                SearchResultPresentation.addChip(this,categoryChip(type,type==selectedType))
            }
        }

    private fun categoryChip(type: Int, selected: Boolean): TextView =
        SearchResultPresentation.chip(context,categoryLabel(type),chipStyler.binding(suggestionType(type),null),appearance,selected).apply {
            contentDescription=categoryLabel(type)
            setOnClickListener {
                categoryScrollX=categoryStrip?.scrollX ?: categoryScrollX
                activeCategoryType=type
                render(latestResults)
            }
        }

    private fun resultChip(result: SuggestionsManager.SearchResult): TextView =
        SearchResultPresentation.chip(context,result.title,chipStyler.binding(suggestionType(result.type),result.payload),appearance).apply {
            contentDescription=listOf(result.title,result.subtitle).filter { it.isNotBlank() }.joinToString(", ")
            setOnClickListener { onClick(result) }
        }

    private fun resultCard() = SearchResultPresentation.card(context,appearance)
    private fun cardLayoutParams() = SearchResultPresentation.cardLayoutParams(context)
    private fun categoryHeader(label: String,type: Int) = SearchResultPresentation.header(context,label,suggestionType(type),appearance)

    private fun categoryLabel(type: Int): String = when (type) {
        SuggestionsManager.SearchResult.TYPE_APP -> context.getString(R.string.search_category_apps)
        SuggestionsManager.SearchResult.TYPE_CONTACT -> context.getString(R.string.search_category_contacts)
        SuggestionsManager.SearchResult.TYPE_NOTIFICATION -> context.getString(R.string.search_category_notifications)
        SuggestionsManager.SearchResult.TYPE_ALIAS -> context.getString(R.string.search_category_aliases)
        SuggestionsManager.SearchResult.TYPE_COMMAND -> context.getString(R.string.search_category_commands)
        SuggestionsManager.SearchResult.TYPE_PARAMETER -> context.getString(R.string.search_category_params)
        SuggestionsManager.SearchResult.TYPE_GROUP -> context.getString(R.string.search_category_app_groups)
        SuggestionsManager.SearchResult.TYPE_PROVIDER -> context.getString(R.string.search_category_web)
        SuggestionsManager.SearchResult.TYPE_PERMISSION -> context.getString(R.string.search_category_enable_access)
        SuggestionsManager.SearchResult.TYPE_CONTACT_ACTION -> context.getString(R.string.search_category_contact_actions)
        else -> context.getString(R.string.search_category_results)
    }

    private fun suggestionType(type: Int): Int = when (type) {
        SuggestionsManager.SearchResult.TYPE_APP -> SuggestionsManager.Suggestion.TYPE_APP
        SuggestionsManager.SearchResult.TYPE_CONTACT -> SuggestionsManager.Suggestion.TYPE_CONTACT
        SuggestionsManager.SearchResult.TYPE_ALIAS -> SuggestionsManager.Suggestion.TYPE_ALIAS
        SuggestionsManager.SearchResult.TYPE_COMMAND -> SuggestionsManager.Suggestion.TYPE_COMMAND
        SuggestionsManager.SearchResult.TYPE_PARAMETER -> SuggestionsManager.Suggestion.TYPE_COMMAND
        SuggestionsManager.SearchResult.TYPE_PROVIDER -> SuggestionsManager.Suggestion.TYPE_COMMAND
        SuggestionsManager.SearchResult.TYPE_GROUP -> SuggestionsManager.Suggestion.TYPE_APPGP
        else -> SuggestionsManager.Suggestion.TYPE_PERMANENT
    }

    private fun currentRowState(): Map<Int, RowState> =
        rowState.mapValues { (_, state) -> state.copy(scrollX = state.view?.scrollX ?: state.scrollX, view = null) }

    private fun List<SuggestionsManager.SearchResult>.signature(): String =
        joinToString("\n") { "${it.title}|${it.subtitle}" }

    private data class RowState(
        val signature: String,
        val scrollX: Int,
        val view: HorizontalScrollView?
    )

    companion object {
        private val categoryOrder = intArrayOf(
            SuggestionsManager.SearchResult.TYPE_APP,
            SuggestionsManager.SearchResult.TYPE_CONTACT,
            SuggestionsManager.SearchResult.TYPE_NOTIFICATION,
            SuggestionsManager.SearchResult.TYPE_ALIAS,
            SuggestionsManager.SearchResult.TYPE_COMMAND,
            SuggestionsManager.SearchResult.TYPE_PARAMETER,
            SuggestionsManager.SearchResult.TYPE_GROUP,
            SuggestionsManager.SearchResult.TYPE_PROVIDER,
            SuggestionsManager.SearchResult.TYPE_PERMISSION,
            SuggestionsManager.SearchResult.TYPE_CONTACT_ACTION
        )

        internal fun groupResults(results: List<SuggestionsManager.SearchResult>): List<List<SuggestionsManager.SearchResult>> =
            categoryOrder.map { type -> results.filter { it.type == type } }.filter { it.isNotEmpty() }

        internal fun visibleGroups(
            groups: List<List<SuggestionsManager.SearchResult>>,
            selectedType: Int,
            tabbed: Boolean
        ): List<List<SuggestionsManager.SearchResult>> =
            if (tabbed) groups.filter { it.first().type == selectedType } else groups

        internal fun selectedCategoryType(
            previous: Int?,
            groups: List<List<SuggestionsManager.SearchResult>>
        ): Int {
            val available = groups.map { it.first().type }
            return if (previous != null && available.contains(previous)) previous else available.first()
        }
    }
}
