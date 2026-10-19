package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

/** Native Duo controls without actions; live Home attaches its own listeners afterwards. */
internal object DuoControlsPresentation {
    data class Controls(val root: LinearLayout,val switch: TextView,val apps: ImageButton?,val widgets: ImageButton?)

    fun attach(pane: ViewGroup,side: String,appearance: HomeAppearance,imeBottom: Int): Controls {
        val context=pane.context
        fun dp(value: Int)=Tuils.dpToPx(context,value)
        val moveLeft=side=="right"
        val root=LinearLayout(context).apply {
            id=R.id.home_duo_controls;orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        }
        val border=appearance.color(Theme.terminal_border_color)
        val window=appearance.color(Theme.terminal_window_background_color)
        val fill=ThemeColorResolver.withMaxAlpha(if(Color.alpha(window)>0) window else appearance.color(Theme.background_color),224)
        fun background()=TerminalBorderRuntime.panelDrawablePx(context,fill,border,1.5f,
            dp(appearance.number(Ui.dashed_border_corner_radius).toInt().coerceIn(0,48)).coerceAtLeast(dp(6)).toFloat(),
            appearance.bool(Ui.enable_dashed_border),style=appearance.borderStyle())
        val switch=TextView(context).apply {
            text=if(moveLeft) "<<" else ">>";gravity=Gravity.CENTER
            contentDescription=context.getString(R.string.surface_move_re_t_ui_to_screen_eba98,if(moveLeft) "left" else "right")
            setTypeface(appearance.font(context),Typeface.BOLD)
            LauncherFontScale.setBaseSize(this,18f,appearance.number(Ui.font_size_offset).toInt())
            setTextColor(border);this.background=background()
        }
        root.addView(switch,LinearLayout.LayoutParams(dp(56),dp(48)))
        fun icon(resource: Int,description: Int)=ImageButton(context).apply {
            setImageResource(resource);contentDescription=context.getString(description)
            setColorFilter(border);this.background=background()
            root.addView(this,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginStart=dp(8) })
        }
        val apps=if(appearance.bool(Behavior.swipe_up_apps_drawer)) icon(R.drawable.ic_menu,R.string.cd_open_app_drawer) else null
        val widgets=if(appearance.bool(Behavior.show_android_widget_drawer_button)) icon(R.drawable.ic_toolbar_widgets_24,R.string.ui_base_view_android_widgets) else null
        val margin=dp(18)
        pane.addView(root,android.widget.FrameLayout.LayoutParams(-2,dp(48),Gravity.BOTTOM or (if(moveLeft) Gravity.START else Gravity.END)).apply {
            setMargins(margin,margin,margin,margin+imeBottom)
        })
        return Controls(root,switch,apps,widgets)
    }
}
