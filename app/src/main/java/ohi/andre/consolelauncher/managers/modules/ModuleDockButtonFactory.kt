package ohi.andre.consolelauncher.managers.modules

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Small UI helper for the home module dock buttons.
 *
 * UIManager owns module state, selection, and scrolling. This class only creates and
 * paints individual dock buttons so that the large manager does not carry all of the
 * button chrome details inline.
 */
object ModuleDockButtonFactory {
    fun create(
        context: Context,
        module: String?,
        spacingDp: Int,
        onClick: (String?) -> Unit,
        onTouchDown: () -> Unit
    ): TextView {
        val button=createPresentation(context,if("close"==module) "X" else ModuleManager.displayTitle(context,module).orEmpty(),
            "close"==module,spacingDp,false,HomeAppearance.saved())

        button.setOnClickListener { onClick(module) }
        button.setOnTouchListener { view: View, event: MotionEvent? ->
            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onTouchDown()
                    false
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    true
                }
                else -> false
            }
        }

        return button
    }

    internal fun createPresentation(context:Context,label:CharSequence,close:Boolean,spacingDp:Int,selected:Boolean,appearance:HomeAppearance):TextView = TextView(context).also { button ->
        button.text=label
        button.isSingleLine=true
        button.gravity=Gravity.CENTER
        val horizontal=Tuils.dpToPx(context,if(close) 14 else 16)
        val vertical=Tuils.dpToPx(context,7)
        button.setPadding(horizontal,vertical,horizontal,vertical)
        button.layoutParams=LinearLayout.LayoutParams(-2,-2).apply { rightMargin=Tuils.dpToPx(context,spacingDp.coerceAtLeast(0)) }
        style(context,button,selected,appearance.radius(Ui.module_corner_radius),appearance.bool(Ui.enable_dashed_border),appearance)
    }

    fun style(
        context: Context,
        button: TextView,
        selected: Boolean,
        cornerRadius: Int,
        dashedBorders: Boolean
    ) = style(context,button,selected,cornerRadius,dashedBorders,HomeAppearance.saved())

    internal fun styleDraft(button: TextView,appearance: HomeAppearance) {
        val selected=button.getTag(R.id.home_dock_binding) as? Boolean ?: button.isSelected
        style(button.context,button,selected,appearance.radius(Ui.module_corner_radius),appearance.bool(Ui.enable_dashed_border),appearance)
    }

    private fun style(
        context: Context,
        button: TextView,
        selected: Boolean,
        cornerRadius: Int,
        dashedBorders: Boolean,
        appearance: HomeAppearance?
    ) {
        val label = button.text.toString().removePrefix("✓ ")
        button.text = if (selected) "✓ $label" else label
        button.isSelected = selected
        button.setTag(R.id.home_dock_binding,selected)
        fun color(role: Theme)=appearance?.color(role) ?: ThemeColorResolver.color(role)
        val bg = color(
            if (selected) Theme.module_active_background_color else Theme.module_dock_background_color
        )
        val borderColor = color(
            if (selected) Theme.module_active_border_color else Theme.module_dock_border_color
        )
        val textColor = color(
            if (selected) Theme.module_active_text_color else Theme.module_dock_text_color
        )

        button.setTextColor(textColor)
        if (appearance!=null) {
            button.setTypeface(appearance.font(context),Typeface.BOLD)
            LauncherFontScale.setBaseSize(button,12f,appearance.number(Ui.font_size_offset).toInt())
            (button.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.rightMargin=Tuils.dpToPx(context,appearance.number(Ui.module_dock_spacing_dp).toInt().coerceAtLeast(0))
                button.layoutParams=it
            }
        }
        button.background = TerminalBorderRuntime.panelDrawablePx(
            context,
            bg,
            borderColor,
            1.2f,
            Tuils.dpToPx(context,cornerRadius).toFloat(),
            dashedBorders,
            target = FrameTarget.MODULE_DOCK,
            style = appearance?.borderStyle()?.let { it.copy(stroke=it.stroke*.8f) }
        )
    }
}
