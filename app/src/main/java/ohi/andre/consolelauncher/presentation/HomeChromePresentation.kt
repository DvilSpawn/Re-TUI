package ohi.andre.consolelauncher.presentation

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.TextShadows
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.CyberpunkBackdropDrawable
import ohi.andre.consolelauncher.tuils.OutlineTextView
import ohi.andre.consolelauncher.tuils.Tuils

/** Home and Settings use these same chrome rules with their own immutable appearance values. */
internal object HomeChromePresentation {
    private data class Panel(val surface: SurfaceBorder,val fill: Theme,val margins: Ui,val notch: Boolean)

    fun styleSurface(view: View,appearance: HomeAppearance,saved: HomeAppearance?=null): Boolean {
        val panel=when(view.id) {
            R.id.terminal_output_border -> Panel(SurfaceBorder.OUTPUT,Theme.output_background_color,Ui.output_field_margins,true)
            R.id.input_group -> Panel(SurfaceBorder.INPUT,Theme.input_background_color,Ui.input_area_margins,false)
            R.id.tools_view -> Panel(SurfaceBorder.TOOLBAR,Theme.toolbar_background_color,Ui.toolbar_margins,false)
            R.id.suggestions_container -> Panel(SurfaceBorder.SUGGESTIONS,Theme.suggestions_background_color,Ui.suggestions_area_margin,true)
            R.id.module_suggestions_container -> Panel(SurfaceBorder.SUGGESTIONS,Theme.chip_background_color,Ui.suggestions_area_margin,true)
            else -> return false
        }
        try {
            if(saved==null || appearance.changed(panel.margins,saved)) margins(view,appearance,panel.margins)
            val radius=if(panel.surface==SurfaceBorder.OUTPUT) appearance.radius(Ui.output_corner_radius)
                else appearance.number(Ui.dashed_border_corner_radius).toInt().coerceIn(0,48)
            HomeAppearance.surface(view.context,view,appearance.color(panel.fill),
                SurfaceBorder.resolveColor(appearance.raw(panel.surface.color),appearance.color(Theme.terminal_border_color)),
                Tuils.dpToPx(view.context,radius).toFloat(),appearance.bool(Ui.enable_dashed_border),panel.notch,
                panel.surface,true,true,appearance)
        } catch (error: Exception) { Tuils.toFile(error);Tuils.log(error) }
        return true
    }

    /** Typography shared by the live terminal and passive preview; never changes text or focus. */
    fun styleTerminalText(view: TextView, appearance: HomeAppearance, input: Boolean) {
        view.typeface=appearance.font(view.context)
        ohi.andre.consolelauncher.tuils.LauncherFontScale.setBaseSize(view,appearance.number(Ui.input_output_size,12f),appearance.number(Ui.font_size_offset).toInt())
        if(input) {
            view.setTextColor(appearance.color(Theme.input_text_color))
            view.setHintTextColor(appearance.color(Theme.field_hint_color))
            styleInputText(view,appearance)
        } else styleOutputText(view,appearance)
        if(view is OutlineTextView) view.redrawPassesOverride=appearance.number(Ui.text_redraw_times,1f).toInt().coerceIn(1,8)
    }

    fun styleToolbarButton(view: ImageView,appearance: HomeAppearance) {
        val context=view.context
        view.background=ohi.andre.consolelauncher.tuils.TerminalBorderRuntime.customFrame(context,
            android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),ohi.andre.consolelauncher.tuils.FrameTarget.TOOLBAR)
            ?: if(appearance.bool(Behavior.enable_cyberdeck_mode)) ohi.andre.consolelauncher.tuils.CyberpunkIconFrameDrawable(
                ohi.andre.consolelauncher.managers.settings.ThemeColorResolver.withMaxAlpha(appearance.color(Theme.terminal_border_color),230),
                Tuils.dpToPx(context,1.6f),Tuils.dpToPx(context,9f),Tuils.dpToPx(context,9f)) else null
    }

    fun styleInputText(view: TextView,appearance: HomeAppearance,saved: HomeAppearance?=null) {
        if(saved==null || appearance.changed(Ui.input_field_margins,saved)) margins(view,appearance,Ui.input_field_margins)
        shadow(view,appearance,Theme.input_text_shadow_color)
    }

    fun styleOutputText(view: TextView,appearance: HomeAppearance) {
        view.setBackgroundColor(Color.TRANSPARENT)
        view.setTextColor(appearance.color(Theme.output_text_color))
        view.setHintTextColor(appearance.color(Theme.session_info_text_color))
        shadow(view,appearance,Theme.output_text_shadow_color)
    }

    fun styleToolbarIcon(view: ImageView,appearance: HomeAppearance): Boolean {
        val role=when {
            view.id==R.id.submit_tv -> Theme.enter_icon_color
            view.id in intArrayOf(R.id.back_view,R.id.next_view,R.id.paste_view,R.id.delete_view,
                R.id.app_drawer_view,R.id.android_widget_drawer_view,R.id.termux_workspace_view) -> Theme.toolbar_icon_color
            (view.parent as? View)?.id==R.id.tools_view -> Theme.toolbar_icon_color
            else -> return false
        }
        view.setColorFilter(appearance.color(role),PorterDuff.Mode.SRC_IN)
        return true
    }

    fun styleRoot(view: View,appearance: HomeAppearance,canApplyTheme: Boolean=true) {
        view.setTag(R.id.home_root_binding,canApplyTheme)
        val bg=appearance.color(if(appearance.bool(Ui.system_wallpaper) && canApplyTheme) Theme.wallpaper_overlay_color else Theme.background_color)
        if(appearance.bool(Behavior.enable_cyberdeck_mode)) view.background=CyberpunkBackdropDrawable(bg,
            appearance.color(Theme.terminal_border_color),appearance.color(Theme.device_text_color))
        else view.setBackgroundColor(bg)
    }

    private fun margins(view: View,appearance: HomeAppearance,key: Ui) {
        val parts=appearance.raw(key).orEmpty().replace(Regex("[\\[\\]\\s]"),"").split(',')
        val spaces=IntArray(4) { parts.getOrNull(it)?.toIntOrNull() ?: 0 }
        view.setPadding(spaces[2],spaces[3],spaces[2],spaces[3])
        when(val params=view.layoutParams) {
            is RelativeLayout.LayoutParams -> { params.setMargins(spaces[0],spaces[1],spaces[0],spaces[1]);view.layoutParams=params }
            is LinearLayout.LayoutParams -> { params.setMargins(spaces[0],spaces[1],spaces[0],spaces[1]);view.layoutParams=params }
        }
    }

    private fun shadow(view: TextView,appearance: HomeAppearance,role: Theme) {
        val color=appearance.color(role)
        if(Color.alpha(color)==0) { view.setShadowLayer(0f,0f,0f,Color.TRANSPARENT);view.tag=null }
        else {
            val params=TextShadows.parameters(appearance.raw(Ui.shadow_params))
            view.setShadowLayer(params[2],params[0].toInt().toFloat(),params[1].toInt().toFloat(),color)
            view.tag=OutlineTextView.SHADOW_TAG
        }
    }
}
