package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.TextShadows
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.ThemeColorSpan
import ohi.andre.consolelauncher.tuils.Tuils

/** Passive Search components shared by Home and its frozen presentation. Actions belong to Home. */
internal object SearchResultPresentation {
    enum class Role { CARD, HEADER, OUTPUT, RESULT_STRIP, CATEGORY_STRIP, CATEGORY_CHIP }
    data class Binding(val role: Role, val suggestionType: Int=0, val selected: Boolean=false)

    fun card(context: Context, appearance: HomeAppearance) = LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL
        styleDraft(this,Binding(Role.CARD),appearance)
    }

    fun cardLayoutParams(context: Context) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        val margin=Tuils.dpToPx(context,8);setMargins(margin,margin,margin,margin)
    }

    fun header(context: Context,text: CharSequence,type: Int,appearance: HomeAppearance) = TextView(context).apply {
        this.text=ThemeColorSpan.refresh(text,appearance::color)
        styleDraft(this,Binding(Role.HEADER,type),appearance)
    }

    fun output(context: Context,text: CharSequence,appearance: HomeAppearance) = TextView(context).apply {
        this.text=ThemeColorSpan.refresh(text,appearance::color)
        styleDraft(this,Binding(Role.OUTPUT),appearance)
        layoutParams=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun strip(context: Context,categories: Boolean) = HorizontalScrollView(context).apply {
        setTag(R.id.home_search_binding,Binding(if(categories) Role.CATEGORY_STRIP else Role.RESULT_STRIP))
        isHorizontalScrollBarEnabled=false
        isFillViewport=false
        addView(LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun chip(context: Context,text: CharSequence,binding: SuggestionChipStyler.Binding,
             appearance: HomeAppearance,selected: Boolean?=null): TextView =
        SuggestionChipStyler.createView(context,appearance).apply {
            this.text=ThemeColorSpan.refresh(text,appearance::color)
            SuggestionChipStyler.styleDraft(this,binding,appearance)
            if(selected!=null) styleDraft(this,Binding(Role.CATEGORY_CHIP,binding.type,selected),appearance)
        }

    fun addChip(strip: HorizontalScrollView,chip: TextView) {
        val categories=(strip.getTag(R.id.home_search_binding) as? Binding)?.role==Role.CATEGORY_STRIP
        fun dp(value: Int)=Tuils.dpToPx(strip.context,value)
        (strip.getChildAt(0) as LinearLayout).addView(chip,LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(3),if(categories) dp(6) else 0,dp(3),if(categories) 0 else dp(4))
        })
    }

    fun styleDraft(view: View,binding: Binding,appearance: HomeAppearance,saved: HomeAppearance?=null) {
        view.setTag(R.id.home_search_binding,binding)
        fun dp(value: Int)=Tuils.dpToPx(view.context,value)
        when(binding.role) {
            Role.CARD -> {
                view.setPadding(dp(7),dp(5),dp(7),dp(7))
                view.background=TerminalBorderRuntime.panelDrawablePx(view.context,
                    appearance.color(Theme.suggestions_background_color),
                    SurfaceBorder.resolveColor(appearance.raw(SurfaceBorder.SUGGESTIONS.color),appearance.color(Theme.terminal_border_color)),
                    1f,dp(appearance.radius(Ui.module_corner_radius)).toFloat(),appearance.bool(Ui.enable_dashed_border),
                    target=FrameTarget.SUGGESTIONS,style=appearance.borderStyle().let { it.copy(stroke=it.stroke*(1f/1.5f)) })
            }
            Role.HEADER -> if(view is TextView) {
                view.setTypeface(appearance.font(view.context),Typeface.BOLD)
                LauncherFontScale.setBaseSize(view,(appearance.number(Suggestions.suggestions_size)*.72f).coerceAtLeast(9f),appearance.number(Ui.font_size_offset).toInt())
                view.setTextColor(SuggestionChipStyler.textColor(binding.suggestionType,appearance))
                view.setPadding(dp(3),dp(4),dp(3),dp(2))
                val color=appearance.color(Theme.suggestions_text_shadow_color)
                if(Color.alpha(color)==0) view.setShadowLayer(0f,0f,0f,Color.TRANSPARENT)
                else {
                    val params=TextShadows.parameters(appearance.raw(Ui.shadow_params))
                    view.setShadowLayer(params[2],params[0],params[1],color)
                }
            }
            Role.OUTPUT -> if(view is TextView) {
                view.typeface=appearance.font(view.context)
                LauncherFontScale.setBaseSize(view,appearance.number(Ui.input_output_size),appearance.number(Ui.font_size_offset).toInt())
                view.setTextColor(appearance.color(Theme.output_text_color))
                view.gravity=Gravity.START
                view.setPadding(dp(12),dp(10),dp(12),dp(10))
                view.minHeight=dp(48);view.maxHeight=dp(240)
            }
            Role.CATEGORY_CHIP -> if(view is TextView) {
                view.setTypeface(appearance.font(view.context),if(binding.selected) Typeface.BOLD else Typeface.NORMAL)
                view.alpha=if(binding.selected) 1f else .62f
                view.minWidth=dp(52)
                view.gravity=Gravity.CENTER
            }
            Role.RESULT_STRIP,Role.CATEGORY_STRIP -> Unit
        }
    }
}
