package ohi.andre.consolelauncher.presentation

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.tuils.ThemeColorSpan

/** Frozen native suggestion content. No result payload, command, app, View or callback is retained. */
internal class SuggestionsPresentationSnapshot private constructor(
    private val classic: List<Chip>,
    private val trailingSpace: Boolean,
    private val moduleLabels: List<CharSequence>,
    private val moduleVisibility: Int,
    private val cards: List<List<Row>>,
    private val searchVisibility: Int,
    private val sourceScroll: ScrollPositions
) {
    private data class Chip(val text: CharSequence,val binding: SuggestionChipStyler.Binding,val selected: Boolean?=null)
    private sealed interface Row {
        data class Header(val text: CharSequence,val type: Int): Row
        data class Output(val text: CharSequence): Row
        data class Strip(val categories: Boolean,val chips: List<Chip>): Row
    }
    private data class ScrollPositions(val classic: Int,val module: Int,val search: Int,val strips: List<Int>)
    private class BoundState(val scroll: ScrollPositions,var pending: Boolean=true)

    /** Rebuild only known components. Later draft edits retain inspection scroll positions. */
    fun bind(root: ViewGroup,appearance: HomeAppearance) {
        val old=root.getTag(R.id.home_search_binding) as? BoundState
        val scroll=if(old==null) sourceScroll else if(old.pending) old.scroll else positions(root)
        val state=BoundState(scroll)
        root.setTag(R.id.home_search_binding,state)
        root.findViewById<LinearLayout>(R.id.suggestions_group)?.let { group ->
            group.removeAllViews()
            classic.forEach { chip ->
                val view=SuggestionChipStyler.createView(group.context,appearance)
                view.text=ThemeColorSpan.refresh(chip.text,appearance::color)
                SuggestionChipStyler.styleDraft(view,chip.binding,appearance)
                group.addView(passive(view),SuggestionChipStyler.layoutParams(appearance))
            }
            (group.parent as? LinearLayout)?.let { outer ->
                for(index in outer.childCount-1 downTo 0) {
                    val child=outer.getChildAt(index)
                    if(child.getTag(R.id.home_suggestion_binding)===SuggestionChipStyler.TrailingSpace) outer.removeViewAt(index)
                }
                if(trailingSpace) outer.addView(SuggestionChipStyler.createTrailingSpace(group.context,appearance))
            }
        }
        root.findViewById<LinearLayout>(R.id.module_suggestions_group)?.let { group ->
            group.removeAllViews()
            moduleLabels.forEach { label ->
                group.addView(passive(ModuleSuggestionPresentation.create(group.context,label,appearance)),
                    ModuleSuggestionPresentation.layoutParams(appearance))
            }
        }
        root.findViewById<View>(R.id.module_suggestions_container)?.visibility=
            if(appearance.bool(Suggestions.show_suggestions)) moduleVisibility else View.GONE
        root.findViewById<LinearLayout>(R.id.search_mode_results_group)?.let { group ->
            group.visibility=searchVisibility
            group.removeAllViews()
            cards.forEach { rows ->
                val card=SearchResultPresentation.card(group.context,appearance)
                rows.forEach { row ->
                    val view=when(row) {
                        is Row.Header -> SearchResultPresentation.header(group.context,row.text,row.type,appearance)
                        is Row.Output -> SearchResultPresentation.output(group.context,row.text,appearance)
                        is Row.Strip -> SearchResultPresentation.strip(group.context,row.categories).apply {
                            row.chips.forEach { chip -> SearchResultPresentation.addChip(this,
                                passive(SearchResultPresentation.chip(context,chip.text,chip.binding,appearance,chip.selected))) }
                        }
                    }
                    card.addView(view)
                }
                group.addView(card,SearchResultPresentation.cardLayoutParams(group.context))
            }
        }
    }

    /** Call after Android lays out the rebuilt scene; also works for detached bitmap inspection. */
    fun restoreScroll(root: ViewGroup) {
        val state=root.getTag(R.id.home_search_binding) as? BoundState ?: return
        if(!state.pending) return
        val scroll=state.scroll
        root.findViewById<HorizontalScrollView>(R.id.suggestions_container)?.scrollTo(scroll.classic,0)
        root.findViewById<HorizontalScrollView>(R.id.module_suggestions_container)?.scrollTo(scroll.module,0)
        root.findViewById<ScrollView>(R.id.search_mode_results_scroll)?.scrollTo(0,scroll.search)
        strips(root).forEachIndexed { index,strip -> strip.scrollTo(scroll.strips.getOrElse(index){0},0) }
        state.pending=false
    }

    companion object {
        fun capture(root: ViewGroup): SuggestionsPresentationSnapshot {
            val classic=root.findViewById<LinearLayout>(R.id.suggestions_group)?.let { group ->
                (0 until group.childCount).mapNotNull { chip(group.getChildAt(it)) }
            }.orEmpty()
            val outer=root.findViewById<LinearLayout>(R.id.suggestions_group)?.parent as? LinearLayout
            val trailingSpace=outer!=null && (0 until outer.childCount).any {
                outer.getChildAt(it).getTag(R.id.home_suggestion_binding)===SuggestionChipStyler.TrailingSpace
            }
            val module=root.findViewById<LinearLayout>(R.id.module_suggestions_group)?.let { group ->
                (0 until group.childCount).mapNotNull { (group.getChildAt(it) as? TextView)?.let { view ->
                    ReadOnlyStyledText.freeze(view.text,view.paint)
                } }
            }.orEmpty()
            val cards=root.findViewById<LinearLayout>(R.id.search_mode_results_group)?.let { group ->
                (0 until group.childCount).mapNotNull { index ->
                    val card=group.getChildAt(index) as? LinearLayout ?: return@mapNotNull null
                    if((card.getTag(R.id.home_search_binding) as? SearchResultPresentation.Binding)?.role!=SearchResultPresentation.Role.CARD)
                        return@mapNotNull null
                    (0 until card.childCount).mapNotNull { row(card.getChildAt(it)) }
                }
            }.orEmpty()
            return SuggestionsPresentationSnapshot(classic,trailingSpace,module,
                root.findViewById<View>(R.id.module_suggestions_container)?.visibility ?: View.GONE,cards,
                root.findViewById<View>(R.id.search_mode_results_group)?.visibility ?: View.VISIBLE,positions(root))
        }

        private fun chip(view: View): Chip? {
            val text=view as? TextView ?: return null
            val binding=text.getTag(R.id.home_suggestion_binding) as? SuggestionChipStyler.Binding ?: return null
            val search=text.getTag(R.id.home_search_binding) as? SearchResultPresentation.Binding
            return Chip(ReadOnlyStyledText.freeze(text.text,text.paint),binding,
                search?.takeIf { it.role==SearchResultPresentation.Role.CATEGORY_CHIP }?.selected)
        }

        private fun row(view: View): Row? {
            val binding=view.getTag(R.id.home_search_binding) as? SearchResultPresentation.Binding ?: return null
            return when(binding.role) {
                SearchResultPresentation.Role.HEADER -> (view as? TextView)?.let {
                    Row.Header(ReadOnlyStyledText.freeze(it.text,it.paint),binding.suggestionType)
                }
                SearchResultPresentation.Role.OUTPUT -> (view as? TextView)?.let {
                    Row.Output(ReadOnlyStyledText.freeze(it.text,it.paint))
                }
                SearchResultPresentation.Role.RESULT_STRIP,SearchResultPresentation.Role.CATEGORY_STRIP -> {
                    val group=(view as? HorizontalScrollView)?.getChildAt(0) as? LinearLayout ?: return null
                    Row.Strip(binding.role==SearchResultPresentation.Role.CATEGORY_STRIP,
                        (0 until group.childCount).mapNotNull { chip(group.getChildAt(it)) })
                }
                else -> null
            }
        }

        private fun strips(root: ViewGroup): List<HorizontalScrollView> {
            val group=root.findViewById<LinearLayout>(R.id.search_mode_results_group) ?: return emptyList()
            return (0 until group.childCount).flatMap { index ->
                val card=group.getChildAt(index) as? LinearLayout ?: return@flatMap emptyList()
                (0 until card.childCount).mapNotNull { card.getChildAt(it) as? HorizontalScrollView }
            }
        }

        private fun positions(root: ViewGroup) = ScrollPositions(
            root.findViewById<HorizontalScrollView>(R.id.suggestions_container)?.scrollX ?: 0,
            root.findViewById<HorizontalScrollView>(R.id.module_suggestions_container)?.scrollX ?: 0,
            root.findViewById<ScrollView>(R.id.search_mode_results_scroll)?.scrollY ?: 0,
            strips(root).map { it.scrollX })

        private fun <T:TextView> passive(view: T): T = view.apply {
            isClickable=false;isLongClickable=false;isFocusable=false
            setTextIsSelectable(false)
        }
    }
}
