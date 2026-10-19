package ohi.andre.consolelauncher.presentation

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.TextShadows
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.*

/** Binds only text and chrome. Module providers and action listeners stay with Home. */
internal object ModuleContentPresentation {
    internal data class Binding(val size:Ui?,val fixedSize:Float,val themeFont:Boolean,val textStyle:Int,
                                val role:Theme?,val explicitColor:Int,val background:Int,val target:FrameTarget,
                                val explicitBorder:Int=0,val followsPanelBorder:Boolean=true)
    private const val TEXT=0
    private const val ACTION=1
    private const val CLOSE=2
    private const val ROW=3

    fun body(view:TextView,appearance:HomeAppearance,themeFont:Boolean) = bind(view,
        Binding(Ui.module_body_text_size,0f,themeFont,view.typeface?.style?:Typeface.NORMAL,
            Theme.module_panel_text_color,view.currentTextColor,TEXT,FrameTarget.MODULES),appearance)

    fun musicText(view:TextView,appearance:HomeAppearance) = bind(view,
        Binding(Ui.module_body_text_size,0f,true,view.typeface?.style?:Typeface.NORMAL,
            Theme.module_panel_text_color,view.currentTextColor,TEXT,FrameTarget.MUSIC),appearance)

    fun action(view:TextView,appearance:HomeAppearance,target:FrameTarget=FrameTarget.MODULES,music:Boolean=false) = bind(view,
        Binding(if(music) Ui.module_body_text_size else null,12f,true,if(music) Typeface.NORMAL else Typeface.BOLD,
            Theme.module_panel_text_color,view.currentTextColor,ACTION,target),appearance)

    fun close(view:TextView,appearance:HomeAppearance,target:FrameTarget) = bind(view,
        Binding(Ui.module_header_text_size,0f,true,view.typeface?.style?:Typeface.NORMAL,
            Theme.module_panel_text_color.takeIf { view.currentTextColor==appearance.color(it) },view.currentTextColor,CLOSE,target),appearance)

    fun notificationRow(view:TextView,appearance:HomeAppearance,textColor:Int,borderColor:Int,rowBackground:Boolean) = bind(view,
        Binding(Ui.module_body_text_size,0f,true,Typeface.NORMAL,
            Theme.module_panel_text_color.takeIf { appearance.color(it)==textColor },textColor,if(rowBackground) ROW else TEXT,
            FrameTarget.NOTIFICATIONS,borderColor,borderColor==appearance.color(Theme.module_panel_border_color)),appearance)

    internal fun bind(view:TextView,binding:Binding,appearance:HomeAppearance) {
        ModuleBodyPresentation.registerBody(view)
        view.setTag(R.id.home_module_content_binding,binding)
        view.typeface=Typeface.create(if(binding.themeFont) appearance.font(view.context) else Typeface.MONOSPACE,when(binding.textStyle) {
            Typeface.BOLD -> Typeface.BOLD
            Typeface.ITALIC -> Typeface.ITALIC
            Typeface.BOLD_ITALIC -> Typeface.BOLD_ITALIC
            else -> Typeface.NORMAL
        })
        view.setTextColor(binding.role?.let(appearance::color)?:binding.explicitColor)
        val base=binding.size?.let { appearance.number(it,12f).toInt().coerceIn(8,32).toFloat() }?:binding.fixedSize
        val scaled=LauncherFontScale.scaledSp(base,appearance.number(Ui.font_size_offset).toInt())
        view.textSize=scaled
        view.setTag(R.id.retui_font_base_size_sp,base)
        view.setTag(R.id.retui_font_scaled_size_sp,scaled)
        val shadow=appearance.color(Theme.module_text_shadow_color)
        if(Color.alpha(shadow)==0) view.setShadowLayer(0f,0f,0f,Color.TRANSPARENT)
        else {
            val params=TextShadows.parameters(appearance.raw(Ui.shadow_params))
            view.setShadowLayer(params[2],params[0],params[1],shadow)
        }
        when(binding.background) {
            ACTION -> view.background=TerminalBorderRuntime.panelDrawablePx(view.context,
                appearance.color(Theme.module_action_background_color),appearance.color(Theme.module_action_border_color),1.2f,
                Tuils.dpToPx(view.context,appearance.radius(Ui.module_corner_radius)).toFloat(),appearance.bool(Ui.enable_dashed_border),
                target=binding.target,style=appearance.borderStyle())
            ROW -> view.background=TerminalBorderRuntime.panelDrawablePx(view.context,
                appearance.color(Theme.widget_row_background_color),
                ThemeColorResolver.withMaxAlpha(if(binding.followsPanelBorder) appearance.color(Theme.module_panel_border_color) else binding.explicitBorder,140),
                1.2f,Tuils.dpToPx(view.context,appearance.radius(Ui.module_corner_radius)).toFloat(),appearance.bool(Ui.enable_dashed_border),
                target=binding.target,style=appearance.borderStyle())
            CLOSE -> view.background=TerminalBorderRuntime.tabDrawable(view.context,
                appearance.color(Theme.module_header_control_background_color),appearance.color(Theme.terminal_header_border_color),
                target=binding.target,style=appearance.borderStyle(),draftDashed=appearance.bool(Ui.enable_dashed_border))
        }
    }

    fun styleDraft(view:TextView,appearance:HomeAppearance,saved:HomeAppearance):Boolean {
        val binding=view.getTag(R.id.home_module_content_binding) as? Binding?:return false
        bind(view,binding,appearance)
        if(view.text is Spanned && (binding.role?.let { appearance.changed(it,saved) }==true || binding.size?.let { appearance.changed(it,saved) }==true)) {
            val text=SpannableString(view.text)
            binding.role?.takeIf { appearance.changed(it,saved) }?.let { role ->
                text.getSpans(0,text.length,ForegroundColorSpan::class.java).filter { it.foregroundColor==saved.color(role) }.forEach { span ->
                    val start=text.getSpanStart(span);val end=text.getSpanEnd(span);val flags=text.getSpanFlags(span)
                    text.removeSpan(span);text.setSpan(ForegroundColorSpan(appearance.color(role)),start,end,flags)
                }
            }
            binding.size?.takeIf { appearance.changed(it,saved) }?.let { key ->
                val sourceSize=Tuils.convertSpToPixels(saved.number(key,12f),view.context)
                val draftSize=Tuils.convertSpToPixels(appearance.number(key,12f),view.context)
                text.getSpans(0,text.length,AbsoluteSizeSpan::class.java).filter { !it.dip && it.size==sourceSize }.forEach { span ->
                    val start=text.getSpanStart(span);val end=text.getSpanEnd(span);val flags=text.getSpanFlags(span)
                    text.removeSpan(span);text.setSpan(AbsoluteSizeSpan(draftSize),start,end,flags)
                }
            }
            view.text=text
        }
        return true
    }
}
