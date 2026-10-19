package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.TextView
import ohi.andre.consolelauncher.managers.settings.TextShadows
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.*

/** Module command labels only: providers and command listeners never enter this factory. */
internal object ModuleSuggestionPresentation {
    fun create(context: Context,label: CharSequence,appearance: HomeAppearance) = TextView(context).apply {
        text=ThemeColorSpan.refresh(label,appearance::color)
        gravity=Gravity.CENTER
        setLines(1);maxLines=1
        ellipsize=TextUtils.TruncateAt.END
        minWidth=Tuils.dpToPx(context,56)
        val spaces=SuggestionChipStyler.spacing(appearance)
        setPadding(spaces[2],spaces[3],spaces[2],spaces[3])
        setTypeface(appearance.font(context),Typeface.BOLD)
        setTextColor(appearance.color(Theme.module_panel_text_color))
        LauncherFontScale.setBaseSize(this,appearance.number(Suggestions.suggestions_size),appearance.number(Ui.font_size_offset).toInt())
        val shadow=appearance.color(Theme.suggestions_text_shadow_color)
        if(Color.alpha(shadow)==0) setShadowLayer(0f,0f,0f,Color.TRANSPARENT)
        else {
            val params=TextShadows.parameters(appearance.raw(Ui.shadow_params))
            setShadowLayer(params[2],params[0],params[1],shadow)
        }
        background=TerminalBorderRuntime.tabDrawable(context,appearance.color(Theme.terminal_header_background_color),
            appearance.color(Theme.terminal_header_border_color),target=FrameTarget.SUGGESTIONS,
            style=appearance.borderStyle(),draftDashed=appearance.bool(Ui.enable_dashed_border))
    }

    fun layoutParams(appearance: HomeAppearance) = SuggestionChipStyler.layoutParams(appearance)
}
