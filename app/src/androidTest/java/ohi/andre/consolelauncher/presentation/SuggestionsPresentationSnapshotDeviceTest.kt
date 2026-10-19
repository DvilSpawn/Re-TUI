package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SuggestionsPresentationSnapshotDeviceTest {
    @Test fun freezesContentAndOverridesWithoutCopyingActions() = onMain {
        val appearance=HomeAppearance(emptyMap())
        val source=scene()
        val original=SpannableString("PRIVATE APP").apply {
            setSpan(object: ClickableSpan() {
                override fun onClick(widget: View) { error("Source action executed") }
                override fun updateDrawState(ds: TextPaint) { ds.color=Color.CYAN }
            },0,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val binding=SuggestionChipStyler.Binding(SuggestionsManager.Suggestion.TYPE_APP,Color.RED,Color.YELLOW)
        val chip=SuggestionChipStyler.createView(source.context,appearance).apply {
            text=original
            setTag(R.id.suggestion_id,Any())
            setOnClickListener { error("Source click executed") }
            SuggestionChipStyler.styleDraft(this,binding,appearance)
        }
        source.findViewById<LinearLayout>(R.id.suggestions_group).addView(chip,SuggestionChipStyler.layoutParams(appearance))
        val card=SearchResultPresentation.card(source.context,appearance)
        val strip=SearchResultPresentation.strip(source.context,true)
        SearchResultPresentation.addChip(strip,SearchResultPresentation.chip(source.context,"APPS",binding,appearance,true))
        card.addView(strip)
        source.findViewById<LinearLayout>(R.id.search_mode_results_group).addView(card,SearchResultPresentation.cardLayoutParams(source.context))
        val frozen=SuggestionsPresentationSnapshot.capture(source)
        chip.text="CHANGED"
        val target=scene()
        frozen.bind(target,appearance)
        val restored=target.findViewById<LinearLayout>(R.id.suggestions_group).getChildAt(0) as TextView
        assertEquals("PRIVATE APP",restored.text.toString())
        assertEquals(Color.YELLOW,restored.currentTextColor)
        assertEquals(binding,restored.getTag(R.id.home_suggestion_binding))
        assertNull(restored.getTag(R.id.suggestion_id))
        assertFalse(restored.hasOnClickListeners());assertFalse(restored.isClickable);assertFalse(restored.isLongClickable)
        assertFalse(restored.isFocusable);assertFalse(restored.isTextSelectable)
        assertTrue((restored.text as Spanned).getSpans(0,restored.length(),ClickableSpan::class.java).isEmpty())
        val rebuiltCard=target.findViewById<LinearLayout>(R.id.search_mode_results_group).getChildAt(0) as LinearLayout
        val rebuiltStrip=rebuiltCard.getChildAt(0) as HorizontalScrollView
        val category=(rebuiltStrip.getChildAt(0) as LinearLayout).getChildAt(0) as TextView
        assertEquals(1f,category.alpha,0f)
        assertEquals(android.graphics.Typeface.BOLD,category.typeface.style)
        assertFalse(category.hasOnClickListeners())
    }

    @Test fun restoresVisibleModuleSuggestionStripWithoutCommands() = onMain {
        val appearance=HomeAppearance(emptyMap())
        val source=scene()
        val sourceStrip=source.findViewById<HorizontalScrollView>(R.id.module_suggestions_container)
        sourceStrip.visibility=View.VISIBLE
        source.findViewById<LinearLayout>(R.id.module_suggestions_group).addView(
            ModuleSuggestionPresentation.create(source.context,"NEXT",appearance).apply {
                setOnClickListener { error("Source command executed") }
            },ModuleSuggestionPresentation.layoutParams(appearance))
        val target=scene()
        target.findViewById<View>(R.id.module_suggestions_container).visibility=View.GONE
        SuggestionsPresentationSnapshot.capture(source).bind(target,appearance)
        assertEquals(View.VISIBLE,target.findViewById<View>(R.id.module_suggestions_container).visibility)
        val restored=target.findViewById<LinearLayout>(R.id.module_suggestions_group).getChildAt(0) as TextView
        assertEquals("NEXT",restored.text.toString())
        assertFalse(restored.hasOnClickListeners());assertFalse(restored.isClickable)
    }

    @Test fun preservesTrailingSpaceGeometryAndHiddenSearchContent() = onMain {
        val appearance=HomeAppearance(emptyMap())
        val source=scene()
        val group=source.findViewById<LinearLayout>(R.id.suggestions_group)
        (group.parent as LinearLayout).addView(SuggestionChipStyler.createTrailingSpace(source.context,appearance))
        source.findViewById<View>(R.id.search_mode_results_group).visibility=View.INVISIBLE
        layout(source)
        val sourceStrip=source.findViewById<HorizontalScrollView>(R.id.suggestions_container)
        val target=scene()
        val frozen=SuggestionsPresentationSnapshot.capture(source)
        repeat(5) { frozen.bind(target,appearance) }
        layout(target)
        val targetGroup=target.findViewById<LinearLayout>(R.id.suggestions_group)
        val targetStrip=target.findViewById<HorizontalScrollView>(R.id.suggestions_container)
        assertEquals(2,(targetGroup.parent as LinearLayout).childCount)
        assertEquals(sourceStrip.getChildAt(0).measuredWidth,targetStrip.getChildAt(0).measuredWidth)
        assertEquals(sourceStrip.getChildAt(0).measuredHeight,targetStrip.getChildAt(0).measuredHeight)
        assertEquals(View.INVISIBLE,target.findViewById<View>(R.id.search_mode_results_group).visibility)
    }

    @Test fun retainsCapturedThenUserScrollAcrossRapidDraftRebinds() = onMain {
        val appearance=HomeAppearance(emptyMap())
        val source=scene()
        val group=source.findViewById<LinearLayout>(R.id.suggestions_group)
        repeat(15) { index ->
            val chip=SuggestionChipStyler.createView(source.context,appearance).apply {
                text="Long application $index"
                SuggestionChipStyler.styleDraft(this,SuggestionChipStyler.Binding(SuggestionsManager.Suggestion.TYPE_APP),appearance)
            }
            group.addView(chip,SuggestionChipStyler.layoutParams(appearance))
        }
        layout(source)
        source.findViewById<HorizontalScrollView>(R.id.suggestions_container).scrollTo(135,0)
        val frozen=SuggestionsPresentationSnapshot.capture(source)
        val target=scene()
        repeat(5) { frozen.bind(target,appearance) }
        layout(target);frozen.restoreScroll(target)
        val strip=target.findViewById<HorizontalScrollView>(R.id.suggestions_container)
        assertEquals(135,strip.scrollX)
        strip.scrollTo(221,0)
        repeat(5) { frozen.bind(target,appearance) }
        layout(target);frozen.restoreScroll(target)
        assertEquals(221,strip.scrollX)
    }

    private fun scene(): LinearLayout {
        val context=ApplicationProvider.getApplicationContext<Context>()
        return LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            fun chips(scrollId: Int,groupId: Int) = HorizontalScrollView(context).apply {
                id=scrollId
                val group=LinearLayout(context).apply { id=groupId;orientation=LinearLayout.HORIZONTAL }
                val content=if(scrollId==R.id.suggestions_container) LinearLayout(context).apply { addView(group) } else group
                addView(content,ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            addView(chips(R.id.suggestions_container,R.id.suggestions_group),LinearLayout.LayoutParams(-1,100))
            addView(chips(R.id.module_suggestions_container,R.id.module_suggestions_group),LinearLayout.LayoutParams(-1,100))
            addView(ScrollView(context).apply {
                id=R.id.search_mode_results_scroll
                addView(LinearLayout(context).apply { id=R.id.search_mode_results_group;orientation=LinearLayout.VERTICAL })
            },LinearLayout.LayoutParams(-1,400))
        }
    }

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY))
        view.layout(0,0,480,600)
    }
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
