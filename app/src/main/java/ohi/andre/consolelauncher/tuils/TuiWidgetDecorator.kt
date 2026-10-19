package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.R

object TuiWidgetDecorator {
    @JvmStatic
    fun decorateWidget(widgetRoot: View?, borderViewId: Int, labelViewId: Int) {
        if(widgetRoot==null) return
        val appearance=HomeAppearance.saved()
        decorate(widgetRoot,Binding(borderViewId,labelViewId,0,FrameTarget.MUSIC,
            appearance.color(Theme.module_panel_border_color),appearance.color(Theme.module_panel_text_color),true,true),appearance)
    }

    @JvmStatic
    fun decorateWidget(widgetRoot: View?, borderViewId: Int, labelViewId: Int, borderColor: Int, textColor: Int) {
        decorateWidget(widgetRoot, borderViewId, labelViewId, 0, borderColor, textColor, FrameTarget.MODULES)
    }

    @JvmStatic
    fun decorateWidget(
        widgetRoot: View?,
        borderViewId: Int,
        labelViewId: Int,
        closeViewId: Int,
        borderColor: Int,
        textColor: Int,
        target: FrameTarget = FrameTarget.MODULES
    ) {
        if(widgetRoot==null) return
        val appearance=HomeAppearance.saved()
        val binding=Binding(borderViewId,labelViewId,closeViewId,target,borderColor,textColor,
            borderColor==appearance.color(Theme.module_panel_border_color),textColor==appearance.color(Theme.module_panel_text_color))
        decorate(widgetRoot,binding,appearance)
    }

    internal data class Binding(val border:Int,val label:Int,val close:Int,val target:FrameTarget,
                                val borderColor:Int,val textColor:Int,val followsPanelBorder:Boolean,val followsPanelText:Boolean)
    internal fun decorateDraft(root:View,binding:Binding,appearance:HomeAppearance) = decorate(root,binding,appearance)

    private fun decorate(widgetRoot:View,binding:Binding,appearance:HomeAppearance) {
        widgetRoot.setTag(R.id.home_widget_binding,binding)
        val borderColor=if(binding.followsPanelBorder) appearance.color(Theme.module_panel_border_color) else binding.borderColor
        val textColor=if(binding.followsPanelText) appearance.color(Theme.module_panel_text_color) else binding.textColor
        val context = widgetRoot.context
        val widgetBgColor = appearance.color(Theme.module_panel_background_color)
        val labelMaskColor = appearance.color(Theme.module_header_background_color)
        val useDashed = appearance.bool(Ui.enable_dashed_border)

        val borderView = widgetRoot.findViewById<View>(binding.border)
        if (borderView != null) {
            borderView.background = TerminalBorderRuntime.panelDrawablePx(
                context,
                widgetBgColor,
                borderColor,
                1.5f,
                Tuils.dpToPx(context,appearance.radius(Ui.module_corner_radius)).toFloat(),
                useDashed,
                target = binding.target, style=appearance.borderStyle()
            )
        }

        val widgetLabel = widgetRoot.findViewById<TextView>(binding.label)
        if (widgetLabel != null) {
            widgetLabel.setTextColor(textColor)
            val shadow=appearance.color(Theme.module_text_shadow_color)
            if(android.graphics.Color.alpha(shadow)==0) widgetLabel.setShadowLayer(0f,0f,0f,android.graphics.Color.TRANSPARENT)
            else {
                val params=ohi.andre.consolelauncher.managers.settings.TextShadows.parameters(appearance.raw(Ui.shadow_params))
                widgetLabel.setShadowLayer(params[2],params[0],params[1],shadow)
            }
            widgetLabel.setTypeface(appearance.font(context), Typeface.BOLD)
            val baseSize=appearance.number(Ui.module_header_text_size,12f).toInt().coerceIn(8,32).toFloat()
            val size=LauncherFontScale.scaledSp(baseSize,appearance.number(Ui.font_size_offset).toInt())
            widgetLabel.textSize=size
            widgetLabel.setTag(R.id.retui_font_base_size_sp,baseSize)
            widgetLabel.setTag(R.id.retui_font_scaled_size_sp,size)
            widgetLabel.background=TerminalBorderRuntime.tabDrawable(context,labelMaskColor,
                appearance.color(Theme.terminal_header_border_color),target=binding.target,style=appearance.borderStyle(),draftDashed=useDashed)
        }

        if (borderView != null) {
            val closeView = if (binding.close == 0) null else widgetRoot.findViewById<View?>(binding.close)
            TerminalBorderRuntime.bindPresentation(borderView, widgetLabel, closeView)
        }
    }

    @JvmStatic
    fun getRowBackground(context: Context): Drawable =
        getRowBackground(context, AppearanceSettings.notificationWidgetBorderColor(), FrameTarget.NOTIFICATIONS)

    @JvmStatic
    fun getRowBackground(
        context: Context,
        borderColor: Int,
        target: FrameTarget = FrameTarget.NOTIFICATIONS
    ): Drawable {
        val rowBackground = ThemeColorResolver.color(Theme.widget_row_background_color)
        val strokeColor = ThemeColorResolver.withMaxAlpha(borderColor, 140)

        return TerminalBorderRuntime.panelDrawable(
            context,
            rowBackground,
            strokeColor,
            1.2f,
            AppearanceSettings.moduleCornerRadius(),
            AppearanceSettings.dashedBorders(),
            target = target
        )
    }

    @JvmStatic
    fun panelDrawable(
        context: Context,
        fillColor: Int,
        borderColor: Int,
        strokeDp: Float,
        radiusDp: Int,
        dashed: Boolean,
        target: FrameTarget = FrameTarget.MODULES
    ): Drawable = TerminalBorderRuntime.panelDrawable(
        context,
        fillColor,
        borderColor,
        strokeDp,
        radiusDp,
        dashed,
        target = target
    )

}
