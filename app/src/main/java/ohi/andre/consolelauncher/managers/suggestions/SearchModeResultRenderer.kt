package ohi.andre.consolelauncher.managers.suggestions

import ohi.andre.consolelauncher.R
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

class SearchModeResultRenderer(
    private val pack: MainPack,
    private val container: LinearLayout,
    private val chipFactory: (Context) -> TextView,
    private val onClick: (SuggestionsManager.SearchResult) -> Unit
) {
    private val context = pack.context
    private val chipStyler = SuggestionChipStyler(pack)
    private var rowState = emptyMap<Int, RowState>()

    fun clear() {
        rowState = emptyMap()
        container.removeAllViews()
    }

    fun render(results: List<SuggestionsManager.SearchResult>) {
        val previous = rowState.mapValues { (_, state) -> state.copy(scrollX = state.view.scrollX) }
        rowState = emptyMap()
        container.removeAllViews()
        if (results.isEmpty()) return

        val card = resultCard()
        val nextState = LinkedHashMap<Int, RowState>()
        for (category in groupResults(results)) {
            val type = category.first().type
            card.addView(categoryHeader(categoryLabel(type), type))
            val signature = category.joinToString("\n") { "${it.title}|${it.subtitle}" }
            val strip = resultStrip(category)
            val prior = previous[type]
            strip.post {
                if (prior?.signature == signature) strip.scrollTo(prior.scrollX, 0)
                else strip.fullScroll(View.FOCUS_LEFT)
            }
            nextState[type] = RowState(signature, 0, strip)
            card.addView(strip)
        }
        rowState = nextState
        container.addView(card, cardLayoutParams())
    }

    fun renderOutput(output: CharSequence) {
        clear()
        val resultCard = resultCard()
        resultCard.addView(categoryHeader(context.getString(R.string.search_category_output), SuggestionsManager.SearchResult.TYPE_COMMAND))
        val card = TextView(context)
        card.typeface = Tuils.getTypeface(context)
        card.textSize = XMLPrefsManager.getInt(Ui.input_output_size).toFloat()
        card.setTextColor(XMLPrefsManager.getColor(Theme.output_text_color))
        card.gravity = Gravity.START
        card.setPadding(dp(12), dp(10), dp(12), dp(10))
        card.minHeight = dp(48)
        card.maxHeight = dp(240)
        card.setTextIsSelectable(true)
        card.text = output.toString().trim()
        resultCard.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        container.addView(resultCard, cardLayoutParams())
    }

    private fun resultStrip(results: List<SuggestionsManager.SearchResult>) =
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (result in results) {
                row.addView(resultChip(result), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(3), 0, dp(3), dp(4)) })
            }
            addView(row, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

    private fun resultChip(result: SuggestionsManager.SearchResult): TextView =
        chipFactory(context).apply {
            text = result.title
            contentDescription = listOf(result.title, result.subtitle)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            setOnClickListener { onClick(result) }
            chipStyler.apply(this, suggestionType(result.type), result.payload)
        }

    private fun resultCard() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(7), dp(5), dp(7), dp(7))
        background = TerminalBorderRuntime.panelDrawable(
            context,
            XMLPrefsManager.getColor(Theme.suggestions_background_color),
            AppearanceSettings.surfaceBorderColor(SurfaceBorder.SUGGESTIONS),
            1f,
            AppearanceSettings.moduleCornerRadius(),
            AppearanceSettings.dashedBorders(),
            target = FrameTarget.SUGGESTIONS
        )
    }

    private fun cardLayoutParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) }

    private fun categoryHeader(label: String, type: Int) = TextView(context).apply {
        text = label
        setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        textSize = (XMLPrefsManager.getInt(Suggestions.suggestions_size) * 0.72f).coerceAtLeast(9f)
        setTextColor(chipStyler.suggestionTextColor(suggestionType(type)))
        setPadding(dp(3), dp(4), dp(3), dp(2))
    }

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

    private fun dp(value: Int): Int = Tuils.dpToPx(context, value)

    private data class RowState(
        val signature: String,
        val scrollX: Int,
        val view: HorizontalScrollView
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
    }
}
